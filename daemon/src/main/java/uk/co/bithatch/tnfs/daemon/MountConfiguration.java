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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.jini.config.Monitor;

import uk.co.bithatch.tnfs.server.TNFSInMemoryFileSystem;
import uk.co.bithatch.tnfs.server.TNFSMounts;

public class MountConfiguration extends AbstractConfiguration {
	private final static class LazyLog {
		private static Logger LOG = LoggerFactory.getLogger(MountConfiguration.class);
	}
	
	private final TNFSMounts mounts;

	private final Authentication auth;

	public MountConfiguration(Monitor monitor, Configuration configuration, Optional<Path> configurationDir, Optional<Path> userConfigDir) {
		super(MountConfiguration.class, "mounts", Optional.of(monitor), configurationDir, userConfigDir);
		
		mounts = new TNFSMounts();
		
		auth = new Authentication(Optional.of(monitor), configurationDir, userConfigDir);
		
		document().allSectionsOr(Constants.MOUNT_KEY).ifPresentOrElse(mntsec-> {
			Arrays.asList(mntsec).forEach(sec -> {
				
				var authTypes = Arrays.asList(sec.getAllEnumOr(AuthenticationType.class, Constants.AUTHENTICATION_KEY).orElse(new AuthenticationType[0]));
				var path = sec.get(Constants.PATH_KEY);
				var local = Paths.get(sec.get(Constants.LOCAL_KEY));
				var rdOnly = sec.getBoolean(Constants.READ_ONLY_KEY);
				
				try {
					if(authTypes.isEmpty()) {
						
						if(rdOnly)
							LazyLog.LOG.info("Anonymous mounting {} to {} as read only", local, path);
						else
							LazyLog.LOG.info("Anonymous mounting {} to {}", local, path);
						
						mounts.mount(path, local, rdOnly);
					}
					else {
						
						if(rdOnly)
							LazyLog.LOG.info("Mounting {} to {} (using {} for auth) as read only", local, path, String.join(", ", authTypes.stream().map(AuthenticationType::name).toList()));
						else
							LazyLog.LOG.info("Mounting {} to {} (using {} for auth)", local, path, String.join(", ", authTypes.stream().map(AuthenticationType::name).toList()));
						
						mounts.mount(path, local, auth, rdOnly);
					}
				}
				catch(IOException ioe) {
					throw new UncheckedIOException(ioe);
				}
			});;
		}, () -> {
			LazyLog.LOG.info("Mounting / to default demonstration in-memory file system. Add your own mount to override this behaviour.");
			mounts.mount("/", new TNFSInMemoryFileSystem("/"));
		});
	}
	
	public TNFSMounts mounts() {
		return mounts;
	}
	
	public Authentication authentication() {
		return auth;
	}
}
