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

import java.io.IOException;
import java.nio.file.Path;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.bithatch.tnfs.lib.TNFSFileAccess;

public class TNFSMounts implements TNFSFileSystemService {
	private final static Logger LOG = LoggerFactory.getLogger(TNFSMounts.class);

	public static Principal simpleUser(String username) {
		return new Principal() {
			@Override
			public String getName() {
				return username;
			}
		};
	}
	
	public static final String GUEST_NAME = "<guest>";
	public static final Principal GUEST = TNFSMounts.simpleUser(GUEST_NAME);
	
	@FunctionalInterface
	public interface TNFSAuthenticator {
		Optional<Principal> authenticate(TNFSFileAccess fs, Optional<String> username, Optional<char[]> password);
	}
	
	public record TNFSMountRef(TNFSFileAccess fs, Optional<TNFSAuthenticator> auth) {} 

	private Map<String, TNFSMountRef> mounts = Collections.synchronizedMap(new LinkedHashMap<>());

	public TNFSMounts mount(String path, Path root) throws IOException {
		return mount(path, root, false);
	}
	public TNFSMounts mount(String path, Path root, boolean readOnly) throws IOException {
		return mount(path, new TNFSDefaultFileSystem(root, path, readOnly));
	}

	public TNFSMounts mount(String path, Path root, TNFSAuthenticator authenticator) throws IOException {
		return mount(path, root, authenticator, false);		
	}
	
	public TNFSMounts mount(String path, Path root, TNFSAuthenticator authenticator, boolean readOnly) throws IOException {
		return mount(path, new TNFSDefaultFileSystem(root, path, readOnly), authenticator);
	}

	public TNFSMounts mount(String path, TNFSFileAccess mount) {
		return mount(path, mount, Optional.empty());
	}

	public TNFSMounts mount(String path, TNFSFileAccess mount, TNFSAuthenticator authenticator) {
		return mount(path, mount, Optional.of(authenticator));
	}
	
	private TNFSMounts mount(String path, TNFSFileAccess mount, Optional<TNFSAuthenticator> authenticator) {
		synchronized(mounts) {
			if(mounts.containsKey(path))
				throw new IllegalArgumentException("Already mounted to " + path);
			mounts.put(path, new TNFSMountRef(mount, authenticator));
			return this;
		}
	}

	@Override
	public Collection<TNFSMountRef> mounts() {
		return mounts.values();
	}
	@Override
	public TNFSUserMount createMount(String path, Optional<? extends Principal> user) {
		var ref = mountDetails(path);
		if(LOG.isDebugEnabled()) {
			user.ifPresentOrElse(
				un -> LOG.debug("Request to mount {} as {}", path, un.getName()), 
				() -> LOG.debug("Request to mount {} as guest", path));
			;
		}
		
		return new TNFSUserMount(ref.fs, user.map(p -> (Principal)p).orElse(GUEST));
	}
	
	@Override
	public TNFSMountRef mountDetails(String path) {
		if(path.equals("")) {
			if(mounts.isEmpty()) 
				throw new IllegalArgumentException(path);
			
			return mounts.values().iterator().next();
		}
		var mnt = mounts.get(path);
		if(mnt == null)
			throw new IllegalArgumentException(path);
		return mnt;
	}
}
