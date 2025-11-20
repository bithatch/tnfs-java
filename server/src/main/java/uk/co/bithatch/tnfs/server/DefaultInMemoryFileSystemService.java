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

import java.security.Principal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import uk.co.bithatch.tnfs.server.TNFSMounts.TNFSMountRef;

public final class DefaultInMemoryFileSystemService implements TNFSFileSystemService {
	
	private TNFSInMemoryFileSystem defaultMount = new TNFSInMemoryFileSystem("/", TNFSAccessCheck.READ_WRITE);
	private TNFSMountRef ref = new TNFSMountRef(defaultMount, Optional.empty());

	@Override
	public TNFSUserMount createMount(String path, Optional<Principal> user) {
		if(path.equals("/")) {
			return new TNFSUserMount(defaultMount, user.orElse(TNFSMounts.GUEST));
		} else {
			throw new IllegalArgumentException("No such mount.");
		}
	}

	@Override
	public TNFSMountRef mountDetails(String path) {
		if(path.equals("/")) {
			return ref;
		} else {
			throw new IllegalArgumentException("No such mount.");
		}
	}

	@Override
	public Collection<TNFSMountRef> mounts() {
		return Arrays.asList(ref);
	}
}