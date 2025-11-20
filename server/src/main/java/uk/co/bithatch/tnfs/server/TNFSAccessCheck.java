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

import java.nio.file.AccessDeniedException;
import java.nio.file.ReadOnlyFileSystemException;
import java.util.Arrays;

public interface TNFSAccessCheck {
	
	public final static TNFSAccessCheck DENY_ACCESS = new TNFSAccessCheck() {
		@Override
		public void check(TNFSFileSystem fs, String path, Operation... required)
				throws ReadOnlyFileSystemException, AccessDeniedException {
			throw new AccessDeniedException(path);
		}
	};

	public final static TNFSAccessCheck READ_WRITE = new TNFSAccessCheck() {
		@Override
		public void check(TNFSFileSystem fs, String path, Operation... required)
				throws ReadOnlyFileSystemException, AccessDeniedException {
		}
	};

	public final static TNFSAccessCheck READ_ONLY = new TNFSAccessCheck() {
		@Override
		public void check(TNFSFileSystem fs, String path, Operation... required)
				throws ReadOnlyFileSystemException, AccessDeniedException {
			if (Arrays.asList(required).contains(Operation.WRITE)) {
				throw new AccessDeniedException(path);
			}
		}
	};

	public final static TNFSAccessCheck AUTHENTICATED_READ_WRITE = new TNFSAccessCheck() {
		@Override
		public void check(TNFSFileSystem fs, String path, Operation... required)
				throws ReadOnlyFileSystemException, AccessDeniedException {
			if ( ( !TNFSSession.isActive() || !TNFSSession.get().authenticated() || TNFSSession.get().guest())) {
				throw new AccessDeniedException(path);
			}
		}
	};

	public enum Operation {
		READ, WRITE
	}

	void check(TNFSFileSystem fs, String path, Operation... required) throws ReadOnlyFileSystemException, AccessDeniedException;
}
