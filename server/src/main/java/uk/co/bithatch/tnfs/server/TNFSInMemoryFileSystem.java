/*
 * Copyright © 2025 Bithatch (bithatch@bithatch.co.uk)
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
import java.nio.channels.SeekableByteChannel;
import java.util.stream.Stream;

import uk.co.bithatch.tnfs.lib.Command.StatResult;
import uk.co.bithatch.tnfs.lib.ModeFlag;
import uk.co.bithatch.tnfs.lib.OpenFlag;
import uk.co.bithatch.tnfs.lib.TNFSDirectory;
import uk.co.bithatch.tnfs.lib.TNFSFileSystem;

public class TNFSInMemoryFileSystem implements TNFSFileSystem {

	private final String mountPath;

	public TNFSInMemoryFileSystem(String mountPath) {
		this.mountPath = mountPath;
	}

	@Override
	public void close() throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String mountPath() {
		return mountPath;
	}

	@Override
	public void rmdir(String path) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void chmod(String path, ModeFlag... modes) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public StatResult stat(String path) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void mkdir(String path) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void unlink(String path) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public SeekableByteChannel open(String path, ModeFlag[] mode, OpenFlag... flags) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Stream<String> list(String path) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long free() throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long size() throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public TNFSDirectory directory(String path, String wildcard) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void rename(String path, String targetPath) throws IOException {
		// TODO Auto-generated method stub
		
	}

}
