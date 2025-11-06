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

import static uk.co.bithatch.tnfs.lib.Util.dirname;
import static uk.co.bithatch.tnfs.lib.Util.normalPath;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Paths;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.bithatch.tnfs.lib.Command.Entry;
import uk.co.bithatch.tnfs.lib.Command.StatResult;
import uk.co.bithatch.tnfs.lib.DirEntryFlag;
import uk.co.bithatch.tnfs.lib.ModeFlag;
import uk.co.bithatch.tnfs.lib.OpenFlag;
import uk.co.bithatch.tnfs.lib.ResultCode;
import uk.co.bithatch.tnfs.lib.TNFSDirectory;
import uk.co.bithatch.tnfs.lib.Util;

public class TNFSInMemoryFileSystem implements TNFSFileSystem {
	private final static Logger LOG = LoggerFactory.getLogger(TNFSInMemoryFileSystem.class);

	private static final int BUFFER_BLOCK_SIZE = 256;


	private record MemoryFile(
			String path, 
			ByteBuffer output,
			List<ModeFlag> mode,
			FileTime created, 
			FileTime modified, 
			FileTime accessed) {

		MemoryFile(String path, 
				ByteBuffer output,
				List<ModeFlag> mode) {
			this(path, output, mode, FileTime.from(Instant.now()), FileTime.from(Instant.now()), FileTime.from(Instant.now()));
		}
		
		MemoryFile(String path) {
			this(path, ByteBuffer.allocate(0), Arrays.asList(ModeFlag.ALL_FLAGS_DIR));
		}
		
	}

	private final String mountPath;
	private final  Map<String, MemoryFile> fs = new ConcurrentHashMap<>();
	private boolean readOnly;
	private final long size;


	public TNFSInMemoryFileSystem(String mountPath) {
		this(mountPath, 20 * 1024 * 1024); // 20MiB
	}

	public TNFSInMemoryFileSystem(String mountPath, long size) {
		this.mountPath = mountPath;
		this.size = size;
		try {
			mkdir("/");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void close() throws IOException {
		fs.clear();
	}

	@Override
	public String mountPath() {
		return mountPath;
	}

	@Override
	public void rmdir(String path) throws IOException {

		if(LOG.isDebugEnabled()) {
			LOG.debug("Remove directory {}", path);
		}
		
		checkReadOnly();
		var p = Util.normalPath(path, '/');
		synchronized(fs) {
			var s = fs.get(p);
			if(s != null) {
				if(s.mode.contains(ModeFlag.IFDIR)) {
					for(var k : fs.keySet()) {
						if(k.startsWith(p + "/")) {
							throw new DirectoryNotEmptyException(p);
						}
					}
					fs.remove(p);	
				}
				fs.remove(p);
			}
			else {
				throw new NoSuchFileException(path);
			}
			
		}
		
	}

	@Override
	public void chmod(String path, ModeFlag... modes) throws IOException {

		if(LOG.isDebugEnabled()) {
			LOG.debug("Chmod file or directory {} to {}", path, String.join(", ", Arrays.asList(modes).stream().map(ModeFlag::name).toList()));
		}
		
		checkReadOnly();
		stat(path);
	}

	@Override
	public StatResult stat(String path) throws IOException {
		var p = Util.normalPath(path, '/');
		var f = fs.get(p);
		if(f == null) {
			throw new NoSuchFileException(path);
		}
		return new StatResult(
				ResultCode.SUCCESS, 
				f.mode().toArray(new ModeFlag[0]), 
				0, 
				0, 
				f.output.limit(), 
				f.accessed(), 
				f.modified(), 
				f.created(), 
				"", 
				"");
	}

	@Override
	public void mkdir(String path) throws IOException {

		if(LOG.isDebugEnabled()) {
			LOG.debug("Create directory {}", path);
		}
		
		checkReadOnly();
		var p = Util.normalPath(path, '/');
		synchronized(fs) {
			if(fs.containsKey(p)) {
				throw new FileAlreadyExistsException(path);
			}
			fs.put(p, new MemoryFile(path));
		}
	}

	@Override
	public void unlink(String path) throws IOException {
		
		if(LOG.isDebugEnabled()) {
			LOG.debug("Remove file {}", path);
		}
		
		checkReadOnly();
		var p = Util.normalPath(path, '/');
		synchronized(fs) {
			var f = fs.get(p);
			if(f == null) {
				throw new NoSuchFileException(path);
			}
			else {
				if(f.mode().contains(ModeFlag.IFDIR)) {
					throw new IOException("Is a directory.");
				}
				else {
					fs.remove(p);
				}
			}
		}
		
	}

	@Override
	public SeekableByteChannel open(String path, ModeFlag[] mode, OpenFlag... flags) throws IOException {
		var flgs = Arrays.asList(flags);
		
		if(LOG.isDebugEnabled()) {
			LOG.debug("Open in memory file at {}. Modes {}. Flags {}.", path,
					String.join(", ", Arrays.asList(mode).stream().map(ModeFlag::name).toList()),
					String.join(", ", Arrays.asList(flags).stream().map(OpenFlag::name).toList())
					);
		}
		
		if(readOnly && (flgs.contains(OpenFlag.CREATE) || flgs.contains(OpenFlag.WRITE) || flgs.contains(OpenFlag.APPEND) || flgs.contains(OpenFlag.TRUNCATE))) {
			throw new ReadOnlyFileSystemException();
		}

		var p = Util.normalPath(path, '/');
		var f = fs.get(p);
		
		var create = flgs.contains(OpenFlag.CREATE);
		var excl = create && flgs.contains(OpenFlag.EXCLUSIVE);
		
		if(f == null) {
			if(create) {
				f = new MemoryFile(p, ByteBuffer.allocate(0), Arrays.asList(ModeFlag.ALL_FLAGS));
			}
			else {
				throw new NoSuchFileException(p);
			}
		}
		else if(excl) {
			throw new FileAlreadyExistsException(path);
		}

		var read = flgs.isEmpty() || flgs.contains(OpenFlag.READ);
		var write = flgs.contains(OpenFlag.WRITE);
		
//		If the file is opened for WRITE access then bytes will be written to the end of the file rather than the beginning.
//		If the file is opened for write access by other programs, then it is file system specific if writing to the end of the file is atomic.
		var append = write && flgs.contains(OpenFlag.APPEND);
		
		//	If the file already exists and it is opened for WRITE access, then its 
		//	length is truncated to 0. This option is ignored if the file is opened only for READ access.
		var truncate = write && !read && flgs.contains(OpenFlag.TRUNCATE); 
		
		if(write && ( !append || truncate)) {
			f = new MemoryFile(p, ByteBuffer.allocate(0), f.mode);
			fs.put(p, f);
		}
		
		var ff = f;

		return new SeekableByteChannel() {
			
			boolean open = true;
			ByteBuffer buf = ff.output.slice();
			
			@Override
			public boolean isOpen() {
				return open;
			}
			
			@Override
			public void close() throws IOException {
				checkClosed();
				open = false;
				buf.flip();
				if(LOG.isDebugEnabled()) {
					LOG.debug("Closing file handle {} for {} at {} bytes", hashCode(), path, buf.limit());
				}
				fs.put(p, new MemoryFile(path, buf, ff.mode, ff.created(),FileTime.from(Instant.now()), FileTime.from(Instant.now())));
			}
			
			@Override
			public int write(ByteBuffer src) throws IOException {

				if(LOG.isDebugEnabled()) {
					LOG.debug("Write handle {} at {},  {} bytes to handle {} for {}", hashCode(), path, src.remaining(), hashCode(), path);
				}
				
				checkClosed();
				checkWrite();
				var wrtn = 0;
				while(src.hasRemaining()) {
					var bufrem = buf.remaining();
					var srcrem = src.remaining();
					var wrt = Math.min(bufrem, srcrem);
					if(LOG.isTraceEnabled()) {
						LOG.trace("Smaller of buffer rem {} and source rem {} is {}", bufrem, srcrem, wrt );
					}
					if(wrt > 0) {
						buf.put(buf.position(), src, src.position(), wrt);
						buf.position(buf.position() + wrt);
						src.position(src.position() + wrt);
						wrtn += wrt;
						if(LOG.isTraceEnabled()) {
							LOG.trace("Buffered {} bytes, buffer rem is now {} and src rem is now {}", wrt, buf.remaining(), src.remaining());
						}
					}
					if(!buf.hasRemaining() && src.hasRemaining()) {
						if(buf.limit() < buf.capacity()) {
							buf.limit(Math.min(buf.capacity(), buf.limit() + src.remaining()));
							if(LOG.isTraceEnabled()) {
								LOG.trace("Some space left in buffer, increasing limit to {} and write next segment", buf.limit());
							}
						}
						else {
							var nbuf = ByteBuffer.allocate(((buf.capacity() / BUFFER_BLOCK_SIZE) * BUFFER_BLOCK_SIZE ) + BUFFER_BLOCK_SIZE);
							nbuf.put(0, buf, 0, buf.capacity());
							nbuf.position(buf.capacity());
	
	
							buf = nbuf;
	
							if(LOG.isTraceEnabled()) {
								LOG.trace("Buffer not big enough, enlarged to {}, limited to {}, at position {}", buf.capacity(), buf.limit(), buf.position());
							}
							
							updateUnderlyingFile();
						}
					}
				}

				if(LOG.isTraceEnabled()) {
					LOG.trace("Written handle {} at {},  {} bytes to handle {} for {}", hashCode(), path, wrtn, hashCode(), path);
				}
				
				return wrtn;
			}
			
			@Override
			public SeekableByteChannel truncate(long size) throws IOException {
				if(LOG.isDebugEnabled()) {
					LOG.debug("Truncate file handle {} for {} to {}", hashCode(), path, size);
				}
				checkClosed();
				checkWrite();
				if(size < 0)
					throw new IllegalArgumentException("Negative size.");
				var wasPos = buf.position();
				if(size < buf.limit()) {
					buf = buf.slice(0, (int)size);
				}
				buf.position(Math.min(buf.limit(), wasPos));
				return this;
			}
			
			@Override
			public long size() throws IOException {
				return buf.limit();
			}
			
			@Override
			public int read(ByteBuffer dst) throws IOException {
				checkClosed();
				if(!read)
					throw new NonReadableChannelException();
				if(!buf.hasRemaining()) {
					return -1;
				}
				var r = 0;
				while(dst.hasRemaining() && buf.hasRemaining()) {
					dst.put(buf.get());
					r++;
				}
				return r;
			}
			
			@Override
			public SeekableByteChannel position(long newPosition) throws IOException {
				if(LOG.isDebugEnabled()) {
					LOG.debug("Position file handle {} for {} to {}", hashCode(), path, newPosition);
				}
				checkClosed();
				buf.position((int)newPosition);
				return this;
			}
			
			@Override
			public long position() throws IOException {
				checkClosed();
				return buf.position();
			}
			
			private void checkClosed() throws IOException {
				if(!open)
					throw new ClosedChannelException();
			}
			
			private void checkWrite() throws IOException {
				if(!write)
					throw new NonWritableChannelException();
			}
			
			private void updateUnderlyingFile() {
				if(LOG.isDebugEnabled()) {
					LOG.debug("Updating underlying file {} of {}, limited to {}, at position {}", path, buf.capacity(), buf.limit(), buf.position());
				}
				
				var newbuf = buf.slice();
				fs.put(p, new MemoryFile(path, newbuf, ff.mode, ff.created(),FileTime.from(Instant.now()), FileTime.from(Instant.now())));
				
				
			}
		};
	}

	@Override
	public Stream<String> list(String path) throws IOException {
		var p = Util.normalPath(path, '/');
		var stat = stat(path);
		if(Arrays.asList(stat.mode()).contains(ModeFlag.IFDIR)) {
			if(p.equals("/")) {
				return fs.values().stream().
						filter(f -> !f.path.equals("/") && f.path.startsWith("/") && f.path.indexOf('/', 1) == -1).
						map(f -> f.path.substring(1));
			}
			return fs.values().stream().
					filter(f ->  f.path.startsWith(p + "/")).
					map(f -> f.path.substring(p.length() + 1)).
					filter(s -> s.indexOf('/') == -1
			);
		}
		else {
			throw new NotDirectoryException(path);
		}
		
		
	}

	@Override
	public long free() throws IOException {
		var total = 0l;
		for(var v : fs.values()) {
			total += v.output.limit();
		}
		return size - total;
	}

	@Override
	public long size() throws IOException {
		return size;
	}

	@Override
	public TNFSDirectory directory(String path, String wildcard) throws IOException {
		

		return new InMemoryDirectory(wildcard, path);
	}

	@Override
	public void rename(String path, String targetPath) throws IOException {

		if(LOG.isDebugEnabled()) {
			LOG.debug("Rename {} to {}", path, targetPath);
		}
		
		checkReadOnly();

		var p = normalPath(path, '/');
		var tp = normalPath(targetPath, '/');
		
		var pd = dirname(p);
		var tdd = dirname(tp);

		synchronized (fs) {
			var pf = fs.get(p);
			if (pf == null) {
				throw new NoSuchFileException(path);
			}
			var pfIsDir = pf.mode.contains(ModeFlag.IFDIR);
			var tf = fs.get(tp);

			if (Objects.equals(pd, tdd)) {
				/* Rename */
				if (tf == null) {
					fs.remove(p);
					fs.put(tp, pf);
				} else {
					throw new FileAlreadyExistsException(targetPath);
				}
			} else {
				var cp = tdd + "/" + Util.basename(p);
				
				/* Move */
				if(tf == null) {
					throw new NoSuchFileException(path);
				}
				else if(tf.mode.contains(ModeFlag.IFDIR)) {
					var cf = fs.get(cp);
					var cfIsDir = cf.mode.contains(ModeFlag.IFDIR);
					
					if(cf == null || (!cfIsDir && !pfIsDir)) {
						/* File doesn't already exists both are files, overwrite */
						fs.remove(p);
						fs.put(cp, pf);
					}
					else {
						throw new FileAlreadyExistsException(cp);
					}
				}
				else {
					/* Already exists as file, source must be file */
					if(pfIsDir) {
						throw new FileAlreadyExistsException(path);
					}
					else {
						fs.remove(p);
						fs.put(cp, pf);
					}
				}
			}
		}
		
	}

	@Override
	public boolean readOnly() {
		return readOnly;
	}

	public void readOnly(boolean readOnly) {
		this.readOnly = readOnly;
	}
	
	private void checkReadOnly() {
		if(readOnly) {
			throw new ReadOnlyFileSystemException();
		}
	}
	
	
	private final class InMemoryDirectory implements TNFSDirectory {
		private final String wildcard;
		private final String path;
		private Stream<Entry> str;
		private long position;
		
		private InMemoryDirectory(String wildcard, String path) throws IOException {
			this.wildcard = wildcard;
			this.path = path;
			makeStream();
		}

		@Override
		public void close() throws IOException {
			str.close();
		}

		@Override
		public long tell() throws IOException {
			return position;
		}

		@Override
		public Stream<Entry> stream() {
			return str;
		}

		@Override
		public void seek(long position) throws IOException {
			makeStream();
			str = str.skip(position);
			this.position = position;
		}

		private void makeStream() throws IOException {

			if (wildcard.isBlank()) {
				str = list(path).map(p -> {
					return toEntry(path, p);
				});
			} else {
				var root = Paths.get(System.getProperty("user.home"));
				var pattern = root.getFileSystem().getPathMatcher("glob:" + wildcard);
				str = list(path).filter(p -> {
					return pattern.matches(Paths.get(p));
				}).map(p -> {
					return toEntry(path, p);
				});

				throw new UnsupportedOperationException("Wildcards not supported");
			}

			str = str.peek(ent -> position++);
		}

		private Entry toEntry(String path, String p) {
			var f = fs.get(path.equals("/") ? "/" + p : path + "/" + p);
			if(f == null) {
				return new Entry(
						new DirEntryFlag[0], 
						0, 
						FileTime.from(0, TimeUnit.MILLISECONDS), 
						FileTime.from(0, TimeUnit.MILLISECONDS), 
						p);
			}
			else {
				return new Entry(
						ModeFlag.toDirEntryFlags(p, f.mode().toArray(new ModeFlag[0])), 
						f.output.limit(), 
						f.modified(), 
						f.created(), 
						p);
			}
		}
	}
}
