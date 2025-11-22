/*
 * Copyright © 2025 Bithatch (brett@bithatch.co.uk)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the “Software”), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package uk.co.bithatch.tnfs.daemon;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ReadOnlyFileSystemException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.jini.INI.Section;
import com.sshtools.jini.config.Monitor;

import uk.co.bithatch.tnfs.lib.OpenFlag;
import uk.co.bithatch.tnfs.server.TNFSAccessCheck;
import uk.co.bithatch.tnfs.server.TNFSAccessCheck.Operation;
import uk.co.bithatch.tnfs.server.TNFSAuthenticatorFactory;
import uk.co.bithatch.tnfs.server.TNFSAuthenticatorFactory.TNFSAuthenticatorContext;
import uk.co.bithatch.tnfs.server.TNFSFileSystem;
import uk.co.bithatch.tnfs.server.TNFSInMemoryFileSystem;
import uk.co.bithatch.tnfs.server.TNFSMounts;
import uk.co.bithatch.tnfs.server.TNFSSession;
import uk.co.bithatch.tnfs.server.TNFSSession.Flag;

public class MountConfiguration extends AbstractConfiguration implements TNFSAuthenticatorContext {
	private final static class LazyLog {
		private static Logger LOG = LoggerFactory.getLogger(MountConfiguration.class);
	}
	
	public interface Listener {
		void run();
	}
	
	private final TNFSMounts mounts;
	private final List<TNFSAuthenticatorFactory> allAuthFactories;
	private boolean demo;
	private final List<Listener> listeners = new ArrayList<>();
	private final Authentication authConfig;
	private final Section mountsConfig;

	public MountConfiguration(Monitor monitor, Configuration configuration, Optional<Path> configurationDir, Optional<Path> userConfigDir) {
		super(MountConfiguration.class, "mounts", Optional.of(monitor), configurationDir, userConfigDir);
		
		mounts = new TNFSMounts();

		mountsConfig = configuration.mounts();
		authConfig = new Authentication(Optional.of(monitor), configurationDir, userConfigDir);
		
		allAuthFactories = Stream.concat(
				Stream.of(new PasswordFileAuthenticator(authConfig)), 
				ServiceLoader.load(TNFSAuthenticatorFactory.class).
				stream().
				map(Provider::get)
		).sorted().toList();
		
		allAuthFactories.forEach(af -> af.init(this));
		
		document().onValueUpdate(vu -> {
			configChanged();
		});
		
		document().onSectionUpdate(su -> {
			configChanged();
		});
		
		
		LazyLog.LOG.info("Available authentication modules:");
		for(var authFactory : allAuthFactories) {
			LazyLog.LOG.info("  {} [{}]", authFactory.name(), authFactory.id());
		}
		LazyLog.LOG.info("Active authentication modules:");
		for(var authFactory : activeFactories()) {
			LazyLog.LOG.info("  {} [{}]", authFactory.name(), authFactory.id());
		}
		
		remountAll();
	}
	
	public Authentication authConfig() {
		return authConfig;
	}

	public void addListener(Listener listener) {
		this.listeners.add(listener);
	}
	
	public void removeListener(Listener listener) {
		this.listeners.remove(listener);
	}
	
	private List<TNFSAuthenticatorFactory> activeFactories() {
		var disabledAuthenticators = Arrays.asList(authConfig.document().getAllElse(Constants.DISABLE_AUTHENTICATOR_KEY));
		return allAuthFactories.stream().
				filter(p -> p.valid()).
				filter(p -> !disabledAuthenticators.contains(p.id())).
				toList();
	}

	protected void configChanged() {
		mounts.unmountAll();
		remountAll();
		listeners.forEach(Listener::run);
	}

	protected void remountAll() {
		document().allSectionsOr(Constants.MOUNT_KEY).ifPresentOrElse(mntsec-> {
			Arrays.asList(mntsec).forEach(sec -> {
				
				var path = sec.get(Constants.PATH_KEY);
				var local = Paths.get(sec.get(Constants.LOCAL_KEY));
				
				try {
					LazyLog.LOG.info("Mounting {} to {}", local, path);
					
					 
					for(var authFactory : activeFactories()) {
						var res = authFactory.createAuthenticator(path);
						if(res.isPresent()) {
							LazyLog.LOG.info("Using {} for authenticator", authFactory.name());
							mounts.mount(path, local, res.get(), (mnt, fp, ops) -> checkAccess(sec, mnt, fp, ops));
							return;
						}
					}
					
					throw new AccessDeniedException("No authenticators");
				}
				catch(IOException ioe) {
					throw new UncheckedIOException(ioe);
				}
			});;
		}, () -> {
			LazyLog.LOG.info("Mounting / to default demonstration in-memory file system. Add your own mount to override this behaviour.");
			demo = true;
			var memFs = new TNFSInMemoryFileSystem("/", TNFSAccessCheck.READ_WRITE);
			mounts.mount("/", memFs);
			try(var in = getClass().getResourceAsStream("readme.txt")) {
				try(var out = Channels.newOutputStream(memFs.open("/readme.txt", OpenFlag.WRITE, OpenFlag.CREATE))) {
					in.transferTo(out);
				}
			}
			catch(IOException ioe) {
				throw new UncheckedIOException(ioe);
			}
		});
	}
	
	private void checkAccess(Section mountConfig, TNFSFileSystem fs, String path, Operation... ops) throws ReadOnlyFileSystemException, AccessDeniedException {
		var session = TNFSSession.get();
		var user = session.user();
		var authenticated = session.authenticated();
		var opList = Arrays.asList(ops);
		var guest = session.guest();
		var authenticatedUser = authenticated && !guest;
		
		/* Get  configuration for the mount */
		var mountAllowRead = mountConfig.getAllElse(Constants.ALLOW_READ_KEY);
		var mountAllowWrite= mountConfig.getAllElse(Constants.ALLOW_WRITE_KEY);
		var mountDenyRead = mountConfig.getAllElse(Constants.DENY_READ_KEY);
		var mountDenyWrite= mountConfig.getAllElse(Constants.DENY_WRITE_KEY);

		/* Get global configuration */
		var globalAllowRead = mountsConfig.getAllElse(Constants.ALLOW_READ_KEY);
		var globalAllowWrite= mountsConfig.getAllElse(Constants.ALLOW_WRITE_KEY);
		var globalDenyRead = mountsConfig.getAllElse(Constants.DENY_READ_KEY);
		var globalDenyWrite= mountsConfig.getAllElse(Constants.DENY_WRITE_KEY);

		/* Resolve the actual lists we will use */
		var allowRead = Arrays.asList(mountAllowRead.length == 0 ? globalAllowRead : mountAllowRead);
		var allowWrite = Arrays.asList(mountAllowWrite.length == 0 ? globalAllowWrite : mountAllowWrite);
		var denyRead = Stream.concat(Arrays.asList(globalDenyRead).stream(), Arrays.asList(mountDenyRead).stream()).toList();
		var denyWrite = Stream.concat(Arrays.asList(globalDenyWrite).stream(), Arrays.asList(mountDenyWrite).stream()).toList();
				
		/* Evaluate each required operation. If any fails, access is denied */
		for(var op : ops) {
			switch(op) {
			case READ:
				if(!allowRead.isEmpty()) {
					if(authenticatedUser && allowRead.contains(user.getName())) {
						LazyLog.LOG.debug("Potentially allow READ access to {} because {} was explicitly allowed", path, user.getName());
					}
					else if(authenticatedUser && allowRead.contains("?")) {
						LazyLog.LOG.debug("Potentially allow READ access to {} authenticated users are allowed (`?`)", path);
					}
					else if(authenticatedUser && session.flags().contains(Flag.ENCRYPTED) && allowRead.contains("!")) {
						LazyLog.LOG.debug("Potentially allow READ access to {} authenticated+encrypted users are allowed (`!`)", path);
					}
					else if(allowRead.contains("*")) {
						LazyLog.LOG.debug("Potentially allow READ access to {} all users are allowed (`*`)", path);
					}
					else {
						LazyLog.LOG.debug("Denied READ access to {} because {} matched no allow rules", path, user.getName());
						throw new AccessDeniedException(path);
					}
				}
				
				if(authenticatedUser && denyRead.contains(user.getName())) {
					LazyLog.LOG.debug("Denied READ access to {} because {} was explicitly denied", path, user.getName());
					throw new AccessDeniedException(path);
				}
				break;
			case WRITE:

				if(allowWrite.isEmpty() || allowWrite.contains("@")) {
					LazyLog.LOG.debug("Denied WRITE access to {} by {} there are no users allowed to write", path, user.getName());
					throw new AccessDeniedException(path);
				}
				else {
					if(authenticatedUser && allowWrite.contains(user.getName())) {
						LazyLog.LOG.debug("Potentially allow WRITE access to {} because {} was explicitly allowed", path, user.getName());
					}
					else if(authenticatedUser && allowWrite.contains("?")) {
						LazyLog.LOG.debug("Potentially allow WRITE access to {} authenticated users are allowed (`?`)", path);
					}
					else if(authenticatedUser && session.flags().contains(Flag.ENCRYPTED) && allowWrite.contains("!")) {
						LazyLog.LOG.debug("Potentially allow READ access to {} authenticated+encrypted users are allowed (`!`)", path);
					}
					else if(allowWrite.contains("*")) {
						LazyLog.LOG.debug("Potentially allow WRITE access to {} all users are allowed (`*`)", path);
					}
					else if(allowWrite.contains("*")) {
						LazyLog.LOG.debug("Potentially allow WRITE access to {} all users are allowed (`*`)", path);
					}
					else {
						LazyLog.LOG.debug("Denied WRITE access to {} because {} matched no allow rules", path, user.getName());
						throw new AccessDeniedException(path);
					}
				}
				
				if(authenticatedUser && denyWrite.contains(user.getName())) {
					LazyLog.LOG.debug("Denied WRITE access to {} because {} was explicitly denied", path, user.getName());
					throw new AccessDeniedException(path);
				}
				break;
			default:
				throw new UnsupportedOperationException();
			}
		}
		

		LazyLog.LOG.debug("Allowed {} to {} as {}",  String.join(", ", opList.stream().map(Operation::name).toList()), path, user == null ? "anonymous" : user.getName());
	}
	
	public boolean isDemo() {
		return demo;
	}
	
	public TNFSMounts mounts() {
		return mounts;
	}
}
