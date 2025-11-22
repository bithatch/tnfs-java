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
package uk.co.bithatch.tnfs.mountlib;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.jini.Data.UpdateType;
import com.sshtools.jini.INI.Section;
import com.sshtools.jini.config.Monitor;

import uk.co.bithatch.tnfs.client.TNFSClient;
import uk.co.bithatch.tnfs.client.TNFSMount;
import uk.co.bithatch.tnfs.client.extensions.SecureMount;
import uk.co.bithatch.tnfs.daemonlib.MDNS;
import uk.co.bithatch.tnfs.lib.Message;
import uk.co.bithatch.tnfs.lib.Net;
import uk.co.bithatch.tnfs.lib.Protocol;
import uk.co.bithatch.tnfs.lib.TNFS;
import uk.co.bithatch.tnfs.lib.extensions.Extensions;

public class MountManager implements Closeable {
	
	public enum MountSource {
		MDNS, CONFIGURATION
	}
	
	public final static class Builder {
		private final MountConfiguration configuration;
		private Optional<MDNS> mdns = Optional.empty();
		private Optional<ExecutorService> executor  = Optional.empty();
		
		public Builder(MountConfiguration configuration) {
			this.configuration = configuration;
		}
		
		public MountManager build() {
			return new MountManager(this);
		}
		
		public Builder withExecutor(ExecutorService executor) {
			this.executor = Optional.of(executor);
			return this;
		}
		
		public Builder withMDNS(MDNS mdns) {
			this.mdns = Optional.of(mdns);
			return this;
		}
	}
	
	public final static class Mountable implements Closeable {
		private final MountableKey key;
		private final  String name;
		private final MountSource mountSource;
		private final Optional<Section> configuration;
		private Optional<TNFSMount> mount = Optional.empty();
		private Optional<Exception> error = Optional.empty();
		
		public Mountable(MountableKey key, MountSource mountSource, String name, Optional<Section> configuration) {
			super();
			this.configuration = configuration;
			this.mountSource = mountSource;
			this.key = key;
			this.name = name;
		}
		
		@Override
		public void close() throws IOException {
			error = Optional.empty();
			this.mount.get().close();
			this.mount = Optional.empty();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Mountable other = (Mountable) obj;
			return Objects.equals(key, other.key);
		}

		public Exception error() {
			return error.orElseThrow(() -> new IllegalStateException("There is no error."));
		}
		
		private void error(Exception error) {

			if(LOG.isDebugEnabled())
				LOG.error(MessageFormat.format("Failed to mount TNFS resource {0} @ {1}.", key.hostname(), key.path()), error);
			else
				LOG.error(MessageFormat.format("Failed to mount TNFS resource {0} @ {1}. {2}", key.hostname(), key.path(), error.getMessage()));
			
			this.error = Optional.of(error);
		}

		public Optional<Exception> errorOr() {
			return error;
		}

		@Override
		public int hashCode() {
			return Objects.hash(key);
		}
		
		public boolean isError() {
			return error.isPresent();
		}
		
		public boolean isMounted() {
			return mount.isPresent();
		}
		
		public MountSource source() {
			return mountSource;
		}
		
		public Optional<Section> configuration() {
			return configuration;
		}

		public MountableKey key() {
			return key;
		}
		
		public TNFSMount mount() {
			return mount.orElseThrow(() -> new IllegalStateException("Not mounted."));
		}
		
		private void mount(TNFSMount mount) {
			this.mount =Optional.of(mount);
			error = Optional.empty();
			LOG.info("{} now mounted.", key.id());
		}
		
		public Optional<TNFSMount> mountOr() {
			return mount;
		}
		
		public String name() {
			return name;
		}
		
		
	}
	
	public record MountableKey(String id, String hostname, String path, int port, Protocol protocol) {
		public MountableKey withId(String id) {
			return new MountableKey(id, hostname, path, port, protocol);
		}
		
		public MountableKey withPath(String path) {
			return new MountableKey(id, hostname, path, port, protocol);
		}
		
		public MountableKey withProtocol(Protocol protocol) {
			return new MountableKey(id, hostname, path, port, protocol);
		}
		
		public MountableKey withPort(int port) {
			return new MountableKey(id, hostname, path, port, protocol);
		}
		
		public MountableKey withHostname(String hostname) {
			return new MountableKey(id, hostname, path, port, protocol);
		}
	}
	
	public interface MountListener {
		default void mountAdded(Mountable mountable) {}
		default void mounted(Mountable mountable) {}
		default void mountFailed(Mountable mountable, Exception error) {}
		default void mountRemoved(Mountable mountable) {}
		default void unmounted(Mountable mountable) {}
	}
	
	private class MountServiceListener implements ServiceListener {


		@Override
		public void serviceAdded(ServiceEvent event) {
			LOG.trace("Service added: {}", event);
		}

		@Override
		public void serviceRemoved(ServiceEvent event) {

			LOG.trace("Service removed: {}", event);
			for(var k : mounts.keySet()) {
				if(event.getInfo().getKey().equals(k.id())) {
					executor.submit(() -> {
						unmountAndRemove(k);
					});
				}
			}
			
		}

		@Override
		public void serviceResolved(ServiceEvent event) {
			LOG.trace("Service resolved: {}", event);
			Protocol protocol = protocolFromSrvType(event.getType());

				for(var addr : event.getInfo().getHostAddresses()) {
					LOG.trace("  Address: {}", addr);
					for(var path : parseArray(event.getInfo().getPropertyString("path"))) {
						
						LOG.trace("     Path: {}", path);
						
						var mntabl = new MountableKey(event.getInfo().getKey(), addr, path, event.getInfo().getPort(), protocol);
						if(!mounts.containsKey(mntabl)) {
							executor.submit(() -> {
								try {
									configureFromMDNS(mntabl, event.getName());
								}
								catch(IOException ioe) {
									if(LOG.isDebugEnabled())
										LOG.warn("Failed to mount.", ioe);
									else
										LOG.warn("Failed to mount. {}", ioe.getMessage());
								}
							});
						}
						
					}
				}
		}
	}
	
	static {
//		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "TRACE");
	}
	
	private final static Logger LOG = LoggerFactory.getLogger(MountManager.class);
	public static void main(String[] args) throws Exception {
		try(var monitor = new Monitor()) {
			
			var cfg = new MountConfiguration("mountlib-test", "mountlib-test", monitor, Optional.empty(), Optional.empty());
			
			try(var mm = new MountManager.Builder(cfg).
					withMDNS(new MDNS(InetAddress.getLocalHost())).
					build()) {
				Thread.sleep(Duration.ofHours(1).toMillis());
			}
		}
	}
	private final static String[] parseArray(String arrStr) {
		if(arrStr.startsWith("[") && arrStr.endsWith("]")) {
			return Arrays.asList(arrStr.substring(1, arrStr.length() - 1).split(",")).stream().map(String::trim).toList().toArray(new String[0]);
		}
		else
			throw new IllegalArgumentException("Not array string.");
	}
	private final MountConfiguration configuration;
	private final Optional<MDNS> mdns;

	private final List<MountListener> listeners = new CopyOnWriteArrayList<>();
	private final Map<MountableKey, Mountable> mounts = new ConcurrentHashMap<>();

	private boolean createdExecutor;
	private ExecutorService executor;
	private ServiceListener serviceListener;

	private MountManager(Builder bldr) {
		this.configuration = bldr.configuration;
		this.mdns = bldr.mdns;
		
		createdExecutor = bldr.executor.isEmpty();
		executor = bldr.executor.orElseGet(() -> Executors.newSingleThreadExecutor());

		/**
		 * TODO A Jini bug. The parent provided in value update
		 * events (key asnd section) is not the schema facade, its the original
		 * INI document or section.
		 */
		
		configuration.document().onSectionUpdate(su -> {
			LOG.info("SU: " + su.section().key());
			executor.submit(() -> {
				var tempSection = su.section();
				if(tempSection.key().equals(MountConstants.MOUNT_SECTION)) {
					var name = tempSection.get(MountConstants.NAME_KEY);
					var mountSection = Arrays.asList(configuration.document().allSections(MountConstants.MOUNT_SECTION)).
							stream().filter(s -> s.get(MountConstants.NAME_KEY).equals(name)).findFirst().get();
//					var mountSection = tempSection;
					
					var key = mountableKey(mountSection);
					LOG.info("SUK " + key);
					if(su.type() == UpdateType.REMOVE) {
						if(mounts.containsKey(key)) {
							LOG.info("Removing mount `{}`", key.id);
							unmountAndRemove(key);
						}
					}
					else if(su.type() == UpdateType.ADD) {
						if(mounts.containsKey(key)) {
							LOG.warn("A new mount `{}` was added in configuration but it already existss!", key.id());
						}
						else {
							LOG.info("Creating new mount `{}`", key.id);
							configureFromConfig(mountSection, key);
						}
					}
					else {
						LOG.info("Section `{}` changed, ignoring", key.id);
					}
				}
			});
		});
		
		configuration.document().onValueUpdate(vu -> {
			LOG.info("VU: " + vu);
			executor.submit(() -> {
				/* Handle updated mounts. Will unmount, then remount */
				try {
					var tempMountSection = (Section)vu.parent();
					if(tempMountSection.key().equals(MountConstants.MOUNT_SECTION)) {
						var name = tempMountSection.get(MountConstants.NAME_KEY);
						var mountSection = Arrays.asList(configuration.document().allSections(MountConstants.MOUNT_SECTION)).stream().filter(s -> s.get(MountConstants.NAME_KEY).equals(name)).findFirst().get();
//						var mountSection = tempMountSection;
						
						var key = mountableKey(mountSection);
						
						MountableKey oldKey = key;
	
						if(vu.key().equals(MountConstants.NAME_KEY) && vu.oldValues() != null) {
							oldKey = key.withId(vu.oldValues()[0]);
						}
						else if(vu.key().equals(MountConstants.PATH_KEY) && vu.oldValues() != null) {
							oldKey = key.withPath(vu.oldValues()[0]);
						}
						else if(vu.key().equals(MountConstants.PROTOCOL_KEY) && vu.oldValues() != null) {
							oldKey = key.withProtocol(Protocol.valueOf(vu.oldValues()[0]));
						}
						else if(vu.key().equals(MountConstants.PORT_KEY) && vu.oldValues() != null) {
							oldKey = key.withPort(Integer.parseInt(vu.oldValues()[0]));
						}
						else if(vu.key().equals(MountConstants.HOSTNAME_KEY) && vu.oldValues() != null) {
							oldKey = key.withHostname(vu.oldValues()[0]);
						}
						
						if(mounts.containsKey(oldKey)) {
							LOG.info("Updating mount `{}`", key.id());
							unmountAndRemove(oldKey);
						}
						else {
							/* Unknown  mount */
							return;
						}
						
						configureFromConfig(mountSection, key);
					}
				}
				catch(Exception e) {
					LOG.error("Failed to update mounts.", e);
				}
			});
		});
		var mountSections = configuration.mounts();
		mountSections.forEach(mnt -> {
			configureFromConfig(mnt, mountableKey(mnt));
		});
		
		if(configuration.mountConfiguration().getBoolean(MountConstants.DISCOVER_KEY)) {
			mdns.ifPresentOrElse(md -> {
				executor.submit(() -> {
					discoverFromMDNS(MDNS.MDNS_TNFS_TCP_LOCAL);
					discoverFromMDNS(MDNS.MDNS_TNFS_UDP_LOCAL);
				});
			}
			, () -> {
				LOG.warn("mDNS discovery was enabled in configuration, but no MDNS instance supplied. Will be ignored.");
			});
		}
		
		mdns.ifPresent(md -> md.addListener(serviceListener = new MountServiceListener()));
	}

	public void addListener(MountListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void close() {
		if(createdExecutor) {
			executor.shutdown();
		}
		mdns.ifPresent(md -> md.removeListener(serviceListener));
	}
	
	private void configureFromConfig(Section mnt, MountableKey mntblKey) {

			LOG.info("Configuration mount {}, name = {}", mntblKey, mntblKey.id());

			var mntbl = new Mountable(mntblKey, MountSource.CONFIGURATION, mntblKey.id(), Optional.of(mnt));
			mounts.put(mntblKey, mntbl);
			
			LOG.info("Configuration added {}, name = {}", mntblKey, mntblKey.id());

			listeners.forEach(l -> l.mountAdded(mntbl));
			
			if(mnt.getBoolean(MountConstants.AUTOMOUNT)) {
				LOG.info("Automount is ON, mounting ..");
				mountFromConfig(mntbl);
			}

	}
	
	private void mountFromConfig(Mountable mntbl) {
		try {
			var mntblKey = mntbl.key();
			var clnt = new TNFSClient.Builder().
					withAddress(mntblKey.hostname(), mntblKey.port()).
					build();
			var mnt = mntbl.configuration().get();
			var secure = mnt.getBoolean(MountConstants.SECURE_KEY);
			
			TNFSMount tnfsmnt;
			if (secure) {
				var bldr = clnt.extension(SecureMount.class).mount(mntblKey.path());
				mnt.getOr(MountConstants.USERNAME_KEY).ifPresent(un -> bldr.withUsername(un));
				mnt.getOr(MountConstants.PASSWORD_KEY).ifPresent(un -> bldr.withPassword(un));
				tnfsmnt = bldr.build();
			} else {
				var bldr = clnt.mount(mntblKey.path());
				mnt.getOr(MountConstants.USERNAME_KEY).ifPresent(un -> bldr.withUsername(un));
				mnt.getOr(MountConstants.PASSWORD_KEY).ifPresent(un -> bldr.withPassword(un));
				tnfsmnt = bldr.build();
			}
			
			mntbl.mount(tnfsmnt);

			mnt.getIntOr(MountConstants.PACKET_SIZE).ifPresentOrElse(ps -> {
				if(ps > 0) {
					try {
						clnt.size(clnt.sendMessage(tnfsmnt, Extensions.PKTSZ, Message.of(tnfsmnt.sessionId(),
								Extensions.PKTSZ, new Extensions.PktSize(TNFS.LARGE_MESSAGE_SIZE))).size());
					} catch(UnsupportedOperationException uoe) {
						LOG.debug("Server does not support PKTSZ.", uoe);
					} catch (Exception e) {
						LOG.error("Failed to set custom packet, ignoring.", e);
					}	
				}
			}, () -> {
				try {
					clnt.size(clnt.sendMessage(tnfsmnt, Extensions.PKTSZ, Message.of(tnfsmnt.sessionId(),
							Extensions.PKTSZ, new Extensions.PktSize(TNFS.LARGE_MESSAGE_SIZE))).size());
				} catch(UnsupportedOperationException uoe) {
					LOG.debug("Server does not support PKTSZ.", uoe);
				} catch (Exception e) {
					LOG.error("Failed to set large packet size.", e);
				}	
			});
			
			
			listeners.forEach(l -> l.mounted(mntbl));
		} catch (Exception ioe) {
			mntbl.error(ioe);
			listeners.forEach(l -> l.mountFailed(mntbl, ioe));
		}
	}

	private void configureFromMDNS(MountableKey mntblKey, String name) throws IOException {
		
		LOG.info("mDNS mount {}, name = {}", mntblKey, name);

		var mntbl = new Mountable(mntblKey, MountSource.MDNS, name, Optional.empty());
		
		mounts.put(mntblKey, mntbl);
		listeners.forEach(l -> l.mountAdded(mntbl));

		LOG.info("mDNS added {}, name = {}", mntblKey, name);
		
		if(configuration.mountConfiguration().getBoolean(MountConstants.AUTOMOUNT_DISCOVERED)) {
			LOG.info("Automounting  ..");
			mountFromMDNS(mntbl);
		}
	}
	public void mountFromMDNS(Mountable mntbl) {
		try {
			var mntblKey = mntbl.key();
			var bldr = new TNFSClient.Builder().
					withHostname(mntblKey.hostname()).
					withProtocol(mntblKey.protocol()).
					withPort(mntblKey.port());
			
			var clnt = bldr.
					build();
			
			var tnfsmnt = clnt.extension(SecureMount.class).
					mount(mntblKey.path()).build();
			
			mntbl.mount(tnfsmnt);
			
			listeners.forEach(l -> l.mounted(mntbl));
		} catch (Exception e) {
			mntbl.error(e);
			listeners.forEach(l -> l.mountFailed(mntbl, e));
		}
	}
	
	private void discoverFromMDNS(String type) {
		LOG.info("Discover from {}", type);
		for(var srv : mdns.get().list(type)) {
			try {
				for(var addr : srv.getHostAddresses()) {
					for(var path : parseArray(srv.getPropertyString("path"))) {
						var mntabl = new MountableKey(srv.getKey(), addr, path, srv.getPort(), protocolFromSrvType(type));
						configureFromMDNS(mntabl, srv.getName());
					}
				}
			}
			catch(IOException ioe) {
				LOG.warn("Failed to connect to mDNS advertised server {}", srv);
			}
		}
	}
	
	private MountableKey mountableKey(Section mnt) {

		var hostname = Net.tryAddress(mnt.get(MountConstants.HOSTNAME_KEY));
		var port = mnt.getInt(MountConstants.PORT_KEY);
		var path = mnt.get(MountConstants.PATH_KEY);
		var protocol = mnt.getEnum(Protocol.class, MountConstants.PROTOCOL_KEY);
		var name = mnt.getOr(MountConstants.NAME_KEY).orElseGet(() -> {
			if (port != TNFS.DEFAULT_PORT) {
				return "{hostname}:{port}{path}";
			}
			else {
				return "{hostname}:{path}";
			}
		}).replace("{hostname}", hostname)
			.replace("{port}", String.valueOf(port))
			.replace("{path}", String.valueOf(path));
		
		return new MountableKey(name, hostname, path, port, protocol);
	}
	
	public void mount(Mountable mount) {
		switch(mount.source()) {
		case CONFIGURATION:
			mountFromConfig(mount);
			break;
		case MDNS:
			mountFromMDNS(mount);
			break;
		default:
			throw new IllegalArgumentException();
		}
	}
	
	public void unmount(Mountable mnt) {
		try {
			mnt.close();
		} catch (IOException ioe) {
			LOG.trace("Failed to cleanup unmount, maybe server is now down.", ioe);
		} finally {
			listeners.forEach(l -> l.unmounted(mnt));
		}
	}
	
	public List<Mountable> mounts() {
		return mounts.values().stream().toList();
	}

	private Protocol protocolFromSrvType(String srvType) {
		Protocol protocol;
		if(srvType.equals(MDNS.MDNS_TNFS_TCP_LOCAL)) {
			protocol = Protocol.TCP;
		}
		else if(srvType.equals(MDNS.MDNS_TNFS_UDP_LOCAL)) {
			protocol = Protocol.UDP;
		}
		else {
			throw new IllegalStateException("Unexpected service type. " + srvType);
		}
		return protocol;
	}
	
	public void removeListener(MountListener listener) {
		this.listeners.remove(listener);
	}
	
	private void unmountAndRemove(MountableKey key) {
		LOG.info("Unmount " + key);
		var mnt = mounts.remove(key);
		try {
			if(mnt.isMounted()) {
				unmount(mnt);
			}
		}
		finally {
			listeners.forEach(l -> l.mountRemoved(mnt));
		}
	}
}
