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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
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

import uk.co.bithatch.tnfs.lib.Command.Entry;
import uk.co.bithatch.tnfs.lib.Command.StatResult;
import uk.co.bithatch.tnfs.lib.DirEntryFlag;
import uk.co.bithatch.tnfs.lib.ModeFlag;
import uk.co.bithatch.tnfs.lib.OpenFlag;
import uk.co.bithatch.tnfs.lib.ResultCode;
import uk.co.bithatch.tnfs.lib.TNFSDirectory;
import uk.co.bithatch.tnfs.lib.Util;

public class TNFSInMemoryFileSystem implements TNFSFileSystem {

	private record MemoryFile(
			String path, 
			ByteArrayOutputStream output,
			List<ModeFlag> mode,
			FileTime created, 
			FileTime modified, 
			FileTime accessed) {
		
		MemoryFile(String path) {
			this(path, new ByteArrayOutputStream(), Arrays.asList(ModeFlag.ALL_FLAGS_DIR), FileTime.from(Instant.now()), FileTime.from(Instant.now()), FileTime.from(Instant.now()));
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
				f.output.size(), 
				f.accessed(), 
				f.modified(), 
				f.created(), 
				"", 
				"");
	}

	@Override
	public void mkdir(String path) throws IOException {
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
		// TODO Auto-generated method stub
		return null;
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
			total += v.output.size();
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

//			if(wildcard.isBlank()) {
				str = list(path).map(p -> {
					return toEntry(path, p);
				});
//			}
//			else {
//					var pattern = root.getFileSystem().getPathMatcher("glob:" + wildcard);
//					str = list(path).
//						filter(p -> {
//							return pattern.matches(p.getFileName());
//						}).
//						map(p -> {
//							return toEntry(resolved, p);
//						});
				
//				throw new UnsupportedOperationException("Wildcards not supported");
//			}
			
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
						f.output.size(), 
						f.modified(), 
						f.created(), 
						p);
			}
		}
	}
}
