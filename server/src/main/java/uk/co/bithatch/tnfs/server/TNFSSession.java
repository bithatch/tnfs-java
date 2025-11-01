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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import uk.co.bithatch.tnfs.lib.TNFSFileAccess;
import uk.co.bithatch.tnfs.lib.Version;

public final class TNFSSession implements Closeable {
//	private final static Logger LOG = LoggerFactory.getLogger(TNFSSession.class);

	private static final int MAX_HANDLES = 256;

	private final TNFSFileAccess mount;
	private final int id;
	
	final Map<Integer, AbstractDirHandle<?>> dirHandles = new ConcurrentHashMap<>();
	final Map<Integer, FileHandle> fileHandles = new ConcurrentHashMap<>();
	
	private byte dirHandle = 1;
	private byte fileHandle = 1;
	
	private final TNFSServer<?> server;
	private final Version version;

	private final Principal principal;
	private final Map<String, Object> state = new ConcurrentHashMap<>();
	
	private boolean authenticated;
	
	TNFSSession(TNFSUserMount userMount, int id, TNFSServer<?> server, Version version) {
		this.mount = userMount.fileSystem();
		this.principal = userMount.user();
		this.id = id;
		this.server = server;
		this.version = version;
	}
	
	public boolean authenticated() {
		return authenticated;
	}
	
	public void authenticate() {
		this.authenticated = true;
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
	
	public TNFSFileAccess mount() {
		return mount;
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
