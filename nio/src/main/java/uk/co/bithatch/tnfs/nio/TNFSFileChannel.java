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
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;


public final class TNFSFileChannel extends FileChannel {
	private final boolean deleteOnClose;
	private final Path path;
	private final SeekableByteChannel handle;

	TNFSFileChannel(boolean deleteOnClose, Path path, SeekableByteChannel handle) {
		this.deleteOnClose = deleteOnClose;
		this.path = path;
		this.handle = handle;
	}

	@Override
	public void force(boolean metaData) throws IOException {
		// Noop?
	}

	@Override
	public FileLock lock(long position, long size, boolean shared) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public long position() throws IOException {
		return handle.position();
	}

	@Override
	public FileChannel position(long newPosition) throws IOException {
		handle.position(newPosition);
		return this;
	}

	@Override
	public int read(ByteBuffer dst) throws IOException {
		try {
			return handle.read(dst);
		} catch (Exception e) {
			throw TNFSFileSystemProvider.translateException(e);
		}
	}

	@Override
	public int read(ByteBuffer dst, long position) throws IOException {
		position(position);
		return read(dst);
	}

	@Override
	public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
		long t = 0;
		try {
			for (var dst : dsts) {
				
				var waspos = dst.position();
				var waslimit = dst.limit();
				
				dst.position(dst.position() + offset);
				dst.limit(Math.min(dst.capacity(), dst.position() + length));
				
				try {
					var r = handle.read(dst);
					if(r == -1) {
						break;
					}
					t += r;
				}
				finally {
					dst.limit(waslimit);
					dst.position(waspos);
				}
			}
			return t;
		} catch (Exception e) {
			throw TNFSFileSystemProvider.translateException(e);
		}
	}

	@Override
	public long size() throws IOException {
		return Files.size(path);
	}

	@Override
	public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
		// Untrusted target: Use a newly-erased buffer
		int c = (int) Math.min(count, TNFSFileSystemProvider.TRANSFER_SIZE);
		ByteBuffer bb = ByteBuffer.allocate(c);
		long tw = 0; // Total bytes written
		long pos = position;
		try {
			while (tw < count) {
				bb.limit((int) Math.min((count - tw), TNFSFileSystemProvider.TRANSFER_SIZE));
				// ## Bug: Will block reading src if this channel
				// ## is asynchronously closed
				int nr = src.read(bb);
				if (nr <= 0)
					break;
				bb.flip();
				int nw = write(bb, pos);
				tw += nw;
				if (nw != nr)
					break;
				pos += nw;
				bb.clear();
			}
			return tw;
		} catch (IOException x) {
			if (tw > 0)
				return tw;
			throw x;
		}
	}

	@Override
	public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
		// Untrusted target: Use a newly-erased buffer
		int c = (int) Math.min(count, TNFSFileSystemProvider.TRANSFER_SIZE);
		ByteBuffer bb = ByteBuffer.allocate(c);
		long tw = 0; // Total bytes written
		long pos = position;
		try {
			while (tw < count) {
				bb.limit((int) Math.min(count - tw, TNFSFileSystemProvider.TRANSFER_SIZE));
				int nr = read(bb, pos);
				if (nr <= 0)
					break;
				bb.flip();
				// ## Bug: Will block writing target if this channel
				// ## is asynchronously closed
				int nw = target.write(bb);
				tw += nw;
				if (nw != nr)
					break;
				pos += nw;
				bb.clear();
			}
			return tw;
		} catch (IOException x) {
			if (tw > 0)
				return tw;
			throw x;
		}
	}

	@Override
	public FileChannel truncate(long size) throws IOException {
		try {
			handle.truncate(size);
			return this;
		} catch (Exception e) {
			throw TNFSFileSystemProvider.translateException(e);
		}
	}

	@Override
	public FileLock tryLock(long position, long size, boolean shared) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public int write(ByteBuffer src) throws IOException {
		try {
			return handle.write(src);
		} catch (Exception e) {
			throw TNFSFileSystemProvider.translateException(e);
		}
	}

	@Override
	public int write(ByteBuffer src, long position) throws IOException {
		position(position);
		return write(src);
	}

	@Override
	public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
		try {
			long t = 0;

			for (var src : srcs) {
				
				var waspos = src.position();
				var waslimit = src.limit();
				
				src.position(src.position() + offset);
				src.limit(Math.min(src.capacity(), src.position() + length));
				
				try {
					t += handle.write(src);
				}
				finally {
					src.limit(waslimit);
					src.position(waspos);
				}
			}
			return t;
		} catch (Exception e) {
			throw TNFSFileSystemProvider.translateException(e);
		}
	}

	@Override
	protected void implCloseChannel() throws IOException {
		try {
			handle.close();
		} finally {
			if (deleteOnClose)
				Files.delete(path);
		}
	}

}