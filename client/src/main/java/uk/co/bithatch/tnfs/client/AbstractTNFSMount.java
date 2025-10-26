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
package uk.co.bithatch.tnfs.client;

import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import uk.co.bithatch.tnfs.lib.Command;
import uk.co.bithatch.tnfs.lib.Command.Entry;
import uk.co.bithatch.tnfs.lib.Command.HandleResult;
import uk.co.bithatch.tnfs.lib.Command.SeekType;
import uk.co.bithatch.tnfs.lib.Command.StatResult;
import uk.co.bithatch.tnfs.lib.DirEntryFlag;
import uk.co.bithatch.tnfs.lib.DirOptionFlag;
import uk.co.bithatch.tnfs.lib.DirSortFlag;
import uk.co.bithatch.tnfs.lib.Message;
import uk.co.bithatch.tnfs.lib.ModeFlag;
import uk.co.bithatch.tnfs.lib.OpenFlag;
import uk.co.bithatch.tnfs.lib.TNFSDirectory;
import uk.co.bithatch.tnfs.lib.Util;

public abstract class AbstractTNFSMount implements TNFSMount {


	public static final class ReadDirIterator implements Iterator<String> {
		
		public static final Stream<String> stream(TNFSClient client, int sessionId, HandleResult dir, Optional<String> path) {
			return StreamSupport
	        .stream(Spliterators.spliteratorUnknownSize(new ReadDirIterator(client, sessionId, dir, path), Spliterator.ORDERED), false)
	        .onClose(() -> {
	            try {
	        		client.send(Command.CLOSEDIR, Message.of(client.nextSeq(), sessionId, Command.CLOSEDIR, new Command.CloseHandle(dir.handle())), path);
	            } catch (IOException e) {
	                throw new UncheckedIOException(e);
	            }
	        });	
		}
		
		private final HandleResult dir;
		private final Optional<String> path;
		String next;
		private final TNFSClient client;
		private final int sessionId;

		private ReadDirIterator(TNFSClient client, int sessionId, HandleResult dir, Optional<String> path) {
			this.dir = dir;
			this.path = path;
			this.client = client;
			this.sessionId = sessionId;
		}

		private void checkNext() {
			if(next == null) {
				try {
					next = client.send(
							Command.READDIR, 
							Message.of(client.nextSeq(), sessionId, Command.READDIR, 
								new Command.ReadDir(dir.handle())), path).result().entry();
				}
				catch(EOFException eof) {
					//
				}
				catch(IOException ioe) {
					throw new UncheckedIOException(ioe);
				}
			}
		}

		@Override
		public boolean hasNext() {
			checkNext();
			return next != null;
		}

		@Override
		public String next() {
			try {
				checkNext();
				if(next ==  null) {
					throw new NoSuchElementException();
				}
				return next;
			}
			finally {
				next = null;
			}
		}
	}

	public static abstract class AbstractBuilder<BLDR extends AbstractBuilder<BLDR>> {

		final String path;
		private final TNFSClient client;
		private int maxEntries = 0;
		private Optional<String> username = Optional.empty();
		private Optional<char[]> password = Optional.empty();

		/**
		 * Construct a new mount builder.
		 *
		 * @param path path
		 * @param client client
		 * @return this for chaining
		 */
		protected AbstractBuilder(String path, TNFSClient client) {
			this.path = path;
			this.client = client;
		}
		/**
		 * If authentication is required, the username.
		 *
		 * @param username user name
		 * @return this for chaining
		 */
		@SuppressWarnings("unchecked")
		public BLDR withUsername(String username) {
			this.username = Optional.of(username);
			return (BLDR)this;
		}

		/**
		 * If authentication is required, the password.
		 *
		 * @param password password
		 * @return this for chaining
		 */
		public BLDR withPassword(String password) {
			return withPassword(new String(password));
		}

		/**
		 * If authentication is required, the password.
		 *
		 * @param password password
		 * @return this for chaining
		 */
		@SuppressWarnings("unchecked")
		public BLDR withPassword(char[] password) {
			this.password = Optional.of(password);
			return (BLDR)this;
		}

		/**
		 * Maximum number of entries that should be returned per directory
		 * listing read ({@link Command.ReadDirX}). A value of <code>zero</code>
		 * indicates as many as can be fit inside a single message.
		 *
		 * @param maxEntries entries
		 * @return this for chaining
		 */
		@SuppressWarnings("unchecked")
		public final BLDR withMaxEntries(int maxEntries) {
			this.maxEntries = maxEntries;
			return (BLDR)this;
		}
	}

	protected final int maxEntries;
	protected final TNFSClient client;
	protected final String path;
	protected Optional<String> username;
	protected Optional<char[]> password;
	
	private final Map<Class<? extends TNFSMountExtension>, TNFSMountExtension> extensions;

	protected AbstractTNFSMount(AbstractBuilder<?> bldr) throws  IOException {
		
		client = bldr.client;
		path = bldr.path;
		maxEntries = bldr.maxEntries;
		username = bldr.username;
		password = bldr.password;

		extensions = ServiceLoader.load(TNFSMountExtension.class).stream().map(p -> p.get()).peek(ext -> {
			ext.init(this);
		}).collect(Collectors.toMap(TNFSMountExtension::getClass, Function.identity()));
	}

	public abstract int sessionId();

	@Override
	public long free() throws IOException {
		return client.sendMessage(Command.FREE, Message.of(client.nextSeq(), sessionId(), Command.FREE, new Command.Free()), mountPath()).free();
	}


	@Override
	public long size() throws IOException {
		return client.sendMessage(Command.SIZE, Message.of(client.nextSeq(), sessionId(), Command.SIZE, new Command.Size()), mountPath()).size();
	}

	@Override
	public Stream<String> list(String path) throws IOException {
		return ReadDirIterator.stream(
			client,
			sessionId(),
			client.sendMessage(Command.OPENDIR, Message.of(client.nextSeq(), sessionId(), Command.OPENDIR, new Command.OpenDir(path)), path), 
			Optional.of(path)
		);
	}

	/**
	 * List all the entries given a path. All paths (including absolute paths) are relative to the root of the mount.
	 *
	 * @param maxResults maximum number of results, or zero for all results
	 * @param path path
	 * @param wildcard wildcard entries must match
	 * @param dirOptions directory options
	 * @param sortOptions sort options
	 * @return stream of entries
	 * @throws IOException on any error
	 */
	@Override
	public TNFSDirectory directory(int maxResults, String path, String wildcard, DirOptionFlag[] dirOptions, DirSortFlag[] sortOptions) throws IOException {
		var dir = client.sendMessage(Command.OPENDIRX, Message.of(client.nextSeq(), sessionId(), Command.OPENDIRX, new Command.OpenDirX(dirOptions, sortOptions, maxResults, wildcard, path)), path);
		var it = new Iterator<Entry>() {

			Iterator<Entry> next;
			AtomicInteger entries = new  AtomicInteger(dir.entries());

			private void checkNext() {
				if(next != null && !next.hasNext()) {
					next = null;
				}
				if(entries.get() == 0) {
					return;
				}

				if(next == null) {
					try {
						var readDirXReply = client.sendMessage(Command.READDIRX, Message.of(client.nextSeq(), sessionId(), Command.READDIRX, new Command.ReadDirX(dir.handle(), maxEntries)), path);
						next = Arrays.asList(readDirXReply.entries()).iterator();
					}
					catch(EOFException eof) {
						//
					}
					catch(IOException ioe) {
						throw new UncheckedIOException(ioe);
					}
				}
			}

			@Override
			public boolean hasNext() {
				checkNext();
				return next != null && next.hasNext();
			}

			@Override
			public Entry next() {
				checkNext();
				if(next == null) {
					throw new NoSuchElementException();
				}
				try {
					return next.next();
				}
				finally {
					entries.addAndGet(-1);
				}
			}

		};

		var str = StreamSupport
	        .stream(Spliterators.spliterator(it, dir.entries(), Spliterator.ORDERED), false)
	        .onClose(() -> {
	            try {
	        		client.sendMessage(Command.CLOSEDIR, Message.of(client.nextSeq(), sessionId(), Command.CLOSEDIR, new Command.CloseHandle(dir.handle())), path);
	            } catch (IOException e) {
	                throw new UncheckedIOException(e);
	            }
	        });

		return new TNFSDirectory() {

			@Override
			public void close() throws IOException {
				str.close();
			}

			@Override
			public Stream<Entry> stream() {
				return str;
			}

			@Override
			public long tell() throws IOException {
				return client.sendMessage(Command.TELLDIR, Message.of(client.nextSeq(), sessionId(), Command.TELLDIR, new Command.TellDir(dir.handle()))).position();
			}

			@Override
			public void seek(long position) throws IOException {
				client.sendMessage(Command.SEEKDIR, Message.of(client.nextSeq(), sessionId(), Command.SEEKDIR, new Command.SeekDir(dir.handle(), position)));
			}

		};
	}

	@Override
	public SeekableByteChannel open(String path, ModeFlag[] mode, OpenFlag... flags) throws IOException {
		var fh = client.sendMessage(Command.OPEN, Message.of(client.nextSeq(), sessionId(), Command.OPEN, new Command.Open(flags, mode, path)), path);
		return new SeekableByteChannel() {

			boolean open = true;
			long position;

			@Override
			public int read(ByteBuffer dst) throws IOException {
				try {
					var max = client.payloadSize - 3;
					if(dst.remaining() < max) {
						max = dst.remaining();
					}
					var rd = client.sendMessage(Command.READ, Message.of(client.nextSeq(), sessionId(), Command.READ, new Command.Read(fh.handle(), max)), path);
					dst.put(rd.data());
					var r = rd.read();
					position += r;
					return r;
				}
				catch(EOFException eofe) {
					return -1;
				}
			}

			@Override
			public boolean isOpen() {
				return open;
			}

			@Override
			public void close() throws IOException {
				if(open) {
					client.sendMessage(Command.CLOSE, Message.of(client.nextSeq(), sessionId(), Command.CLOSE, new Command.CloseHandle(fh.handle())), path);
					open = false;
				}
			}

			@Override
			public int write(ByteBuffer src) throws IOException {
				var max = client.payloadSize - 3;
				if(src.remaining() > max) {
					throw new IllegalArgumentException("Source buffer must provider fewer than " + max + " bytes");
				}
				var w = client.sendMessage(Command.WRITE, Message.of(client.nextSeq(), sessionId(), Command.WRITE, new Command.Write(fh.handle(), src)), path).written();
				position += w;
				return w;
			}

			@Override
			public long position() throws IOException {
				return position;
			}

			@Override
			public SeekableByteChannel position(long newPosition) throws IOException {
				client.sendMessage(Command.LSEEK, Message.of(client.nextSeq(), sessionId(), Command.LSEEK, new Command.LSeek(fh.handle(), SeekType.SEEK_SET, newPosition)), path);
				position = newPosition;
				return this;
			}

			@Override
			public long size() throws IOException {
				return stat(path).size();
			}

			@Override
			public SeekableByteChannel truncate(long size) throws IOException {
				throw new UnsupportedOperationException();
			}

		};
	}

	@Override
	public void unlink(String path) throws IOException {
		client.sendMessage(Command.UNLINK, Message.of(client.nextSeq(), sessionId(), Command.UNLINK, new Command.Unlink(path)), path);
	}

	@Override
	public void rename(String path, String targetPath) throws IOException {
		client.sendMessage(Command.RENAME, Message.of(client.nextSeq(), sessionId(), Command.RENAME, new Command.Rename(path, targetPath)), path);
	}

	@Override
	public void mkdir(String path) throws IOException {
		client.sendMessage(Command.MKDIR, Message.of(client.nextSeq(), sessionId(), Command.MKDIR, new Command.MkDir(path)), path);
	}

	@Override
	public StatResult stat(String path) throws IOException {
		return client.sendMessage(Command.STAT, Message.of(client.nextSeq(), sessionId(), Command.STAT, new Command.Stat(path)), path);
	}

	@Override
	public void chmod(String path, ModeFlag... modes) throws IOException {
		client.sendMessage(Command.CHMOD, Message.of(client.nextSeq(), sessionId(), Command.CHMOD, new Command.Chmod(modes, path)), path);
	}

	@Override
	public void rmdir(String path) throws IOException {
		client.sendMessage(Command.RMDIR, Message.of(client.nextSeq(), sessionId(), Command.RMDIR, new Command.RmDir(path)), path);
	}

	@Override
	public final void close() throws IOException {
		beforeClose();
		for(var x : extensions.values()) {
			x.close();
		}
		client.sendMessage(Command.UMOUNT, Message.of(client.nextSeq(), sessionId(), Command.UMOUNT, new Command.HeaderOnly()));
		onClose();
	}
	
	protected void beforeClose() throws IOException {
	}
	
	protected void onClose() throws IOException {
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public final <EXT extends TNFSMountExtension> EXT extension(Class<EXT> extension) {
		var ext = extensions.get(extension);
		if(ext == null)
			throw new IllegalArgumentException("No such extension.");
		return (EXT)ext;
	}

	/**
	 * Recursively visit all all entries at the specified path.
	 *
	 * @param path
	 * @param visitor
	 * @return
	 * @throws IOException
	 */
	@Override
	public FileVisitResult visit(String path, FileVisitor<TNFSFile> visitor) throws IOException {
		var attrs = stat(path);
		var name = Util.basename(path);
		var file = new TNFSFile(path, attrs.toEntry(name));
		if (attrs.isDirectory()) {
			var preVisitResult = visitor.preVisitDirectory(file, fileToBasicAttributes(file));
			try {
				if (preVisitResult != FileVisitResult.CONTINUE) {
					return preVisitResult;
				}
				try(var dir = directory(0, path, "", new DirOptionFlag[] { DirOptionFlag.NO_SKIPHIDDEN }, new DirSortFlag[] { })) {
					var str = dir.stream();
					var it =str.iterator();
					while(it.hasNext()) {
						var child = it.next();
						if (DirEntryFlag.isFile(child.flags())) {
							var childFile = new TNFSFile(Util.concatenatePaths(path, child.name()), child);
							var fileVisitResult = visitor.visitFile(childFile, fileToBasicAttributes(childFile));
							if (fileVisitResult != FileVisitResult.CONTINUE
									&& fileVisitResult != FileVisitResult.SKIP_SUBTREE) {
								return fileVisitResult;
							}
						} else if (DirEntryFlag.isDirectory(child.flags()) && !child.name().equals(".")
								&& !child.name().equals("..")) {
							switch (visit(Util.concatenatePaths(path, child.name()), visitor)) {
							case SKIP_SIBLINGS:
								break;
							case TERMINATE:
								return FileVisitResult.TERMINATE;
							default:
								continue;
							}
						}
					}
				}

				var postVisitResult = visitor.postVisitDirectory(file, null);
				if (postVisitResult != FileVisitResult.CONTINUE && postVisitResult != FileVisitResult.SKIP_SUBTREE) {
					return postVisitResult;
				}
			} catch (IOException ioe) {
				ioe.printStackTrace();
				var postVisitResult = visitor.postVisitDirectory(file, ioe);
				if (postVisitResult != FileVisitResult.CONTINUE && postVisitResult != FileVisitResult.SKIP_SUBTREE) {
					return postVisitResult;
				}
			}
		} else {
			var fileVisitResult = visitor.visitFile(file, fileToBasicAttributes(file));
			if (fileVisitResult != FileVisitResult.CONTINUE && fileVisitResult != FileVisitResult.SKIP_SUBTREE) {
				return fileVisitResult;
			}
		}
		return FileVisitResult.CONTINUE;
	}

	@Override
	public final String mountPath() {
		return path;
	}

	@Override
	public final TNFSClient client() {
		return client;
	}

	private BasicFileAttributes fileToBasicAttributes(TNFSFile file) {
		return new BasicFileAttributes() {
			@Override
			public FileTime creationTime() {
				return file.entry().ctime();
			}

			@Override
			public Object fileKey() {
				return file;
			}

			@Override
			public boolean isDirectory() {
				return DirEntryFlag.isDirectory(file.entry().flags());
			}

			@Override
			public boolean isOther() {
				return false;
			}

			@Override
			public boolean isRegularFile() {
				return !isDirectory();
			}

			@Override
			public boolean isSymbolicLink() {
				return false;
			}

			@Override
			public FileTime lastAccessTime() {
				return file.entry().mtime();
			}

			@Override
			public FileTime lastModifiedTime() {
				return file.entry().mtime();
			}

			@Override
			public long size() {
				return file.entry().size();
			}
		};
	}
}
