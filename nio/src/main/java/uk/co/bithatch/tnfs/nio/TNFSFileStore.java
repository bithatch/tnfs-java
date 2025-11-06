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
package uk.co.bithatch.tnfs.nio;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;

import uk.co.bithatch.tnfs.client.TNFSMount;

final class TNFSFileStore extends FileStore {
	private final String path;
	private final TNFSMount mount;
	
	TNFSFileStore(TNFSMount mount, String path) {
		this.path = path;
		this.mount = mount;
	}

	@Override
	public String type() {
		return "remote";
	}

	@Override
	public boolean supportsFileAttributeView(String name) {
		return name.equals("basic") || name.equals("posix");
	}

	@Override
	public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
		return type.equals(BasicFileAttributeView.class) || type.equals(TNFSFileAttributeViews.class) || type.equals(PosixFileAttributeView.class);
	}

	@Override
	public String name() {
		/* TODO  extension ? */
		return String.valueOf("vol-" + Integer.toUnsignedLong(path.hashCode()));
	}

	@Override
	public long getBlockSize() throws IOException {
		/* TODO  extension ? */
		return 0;
	}

	@Override
	public boolean isReadOnly() {
		/* TODO  extension ? */
		return false;
	}

	@Override
	public long getUsableSpace() throws IOException {
		return mount.free();
	}

	@Override
	public long getUnallocatedSpace() throws IOException {
		return getUsableSpace();
	}

	@Override
	public long getTotalSpace() throws IOException {
		return mount.size();
	}

	@Override
	public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
		return null;
	}

	@Override
	public Object getAttribute(String attribute) throws IOException {
		return null;
	}


}