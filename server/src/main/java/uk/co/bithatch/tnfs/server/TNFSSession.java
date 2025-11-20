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
package uk.co.bithatch.tnfs.server;

import java.io.Closeable;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.bithatch.tnfs.lib.Protocol;
import uk.co.bithatch.tnfs.lib.TNFS;
import uk.co.bithatch.tnfs.lib.TNFSFileAccess;
import uk.co.bithatch.tnfs.lib.Version;

public final class TNFSSession implements Closeable {
	
	public enum Flag {
		ENCRYPTED
	}
	
	private final static Logger LOG = LoggerFactory.getLogger(TNFSSession.class);
	

	private static final int MAX_HANDLES = 256;
	
	private final static ThreadLocal<TNFSSession> current = new ThreadLocal<>();
	
	@FunctionalInterface
	public interface IORunnable {
		void run() throws IOException;
	}

	public static boolean isActive() {
		return current.get() != null;
	}
	
	public static void runAs(TNFSSession session, IORunnable r) throws IOException {
		var was = current.get();
		current.set(session);
		try {
			r.run();
		}
		finally {
			if(was == null)
				current.remove();
			else
				current.set(was);
		}
	}
	
	public static TNFSSession get() {
		var s = current.get();
		if(s == null)
			throw new IllegalStateException("Not in session context.");
		else
			return s;
	}

	private final int id;
	
	final Map<Integer, AbstractDirHandle<?>> dirHandles = new ConcurrentHashMap<>();
	final Map<Integer, FileHandle> fileHandles = new ConcurrentHashMap<>();
	
	private byte dirHandle = 1;
	private byte fileHandle = 1;
	
	private final TNFSServer<?> server;
	private final Version version;

	private final Map<String, Object> state = new ConcurrentHashMap<>();
	private final List<TNFSServerPacketProcessor> inProcessors = new ArrayList<>();
	private final List<TNFSServerPacketProcessor> outProcessors = new ArrayList<>();
	
	private int size;
	private TNFSFileSystem mount;
	private Principal principal;
	private Set<Flag> flags;
	
	TNFSSession(int id, TNFSServer<?> server, Version version, Flag... flags) {
		this.id = id;
		this.flags = Set.of(flags);
		this.server = server;
		this.version = version;
		this.size = server.protocol() == Protocol.TCP ? TNFS.DEFAULT_TCP_MESSAGE_SIZE : TNFS.DEFAULT_UDP_MESSAGE_SIZE;
	}
	
	public Set<Flag> flags() {
		return flags;
	}
	
	public List<TNFSServerPacketProcessor> inProcessors() {
		return inProcessors;
	}
	
	public List<TNFSServerPacketProcessor> outProcessors() {
		return outProcessors;
	}
	
	public boolean authenticated() {
		return principal != null;
	}
	
	public boolean guest() {
		return authenticated() && principal.equals(TNFSMounts.GUEST);
	}
	
	public Map<String, Object> state() {
		return state;
	}
	
	public TNFSServer<?> server() {
		return server;
	}

	public Version version() {
		return version;
	}
	
	public Principal user() {
		return principal;
	}
	
	public int id() {
		return id;
	}

	public void mount(TNFSUserMount userMount) throws IOException {
		this.mount = userMount.fileSystem();
		this.principal = userMount.user();
		
		runAs(this, () -> {
			mount.checkAccess();
		});
	}

	public boolean mounted() {
		return mount != null;
	}
	
	public TNFSFileAccess mount() {
		if(mount == null) {
			throw new IllegalStateException("Session has no mount.");
		}
		return mount;
	}
	
	public int size() {
		return size;
	}

	public void size(int size) {
		if(size < 16 || size > 65535)
			throw new IllegalArgumentException();
		
		
		var newSize = Math.min(size, server.size());
		if(newSize != this.size) {
			LOG.info("Client changed packet size from {} to {} (originally requested {})", this.size, newSize, size);
			this.size = size;
		}
	}

	@Override
	public void close() throws IOException {
		try {
			server.close(this);
		}
		finally {
			mount.close();
		}
	}

	int nextDirHandle() {
		if(dirHandles.size() >= MAX_HANDLES) {
			throw new IllegalStateException("Exhausted dir handles.");
		}
		var id = 0;
		do {
			id = Byte.toUnsignedInt(dirHandle++);
		} while(dirHandles.containsKey(id));
		return id;
	}

	int nextFileHandle() {
		if(fileHandles.size() >= MAX_HANDLES) {
			throw new IllegalStateException("Exhausted file handles.");
		}
		var id = 0;
		do {
			id = Byte.toUnsignedInt(fileHandle++);
		} while(fileHandles.containsKey(id));
		return id;
	}
}
