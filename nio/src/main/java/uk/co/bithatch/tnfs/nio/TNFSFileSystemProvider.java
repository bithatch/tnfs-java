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

import static uk.co.bithatch.tnfs.lib.Util.emptyOptionalIfBlank;
import static uk.co.bithatch.tnfs.nio.TNFSFileSystem.toAbsolutePathString;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import uk.co.bithatch.tnfs.client.TNFSClient;
import uk.co.bithatch.tnfs.client.TNFSMount;
import uk.co.bithatch.tnfs.lib.OpenFlag;
import uk.co.bithatch.tnfs.lib.TNFS;

public class TNFSFileSystemProvider extends FileSystemProvider {

	public final static String TNFS_MOUNT = "tnfs-mount";
	public final static String TNFS_CLIENT = "tnfs-client";
	public final static String USERNAME = "username";
	public final static String PASSWORD = "password";
	public final static String HOSTNAME = "hostname";
	public final static String PORT = "port";
	public final static String MOUNT_PATH = "mount-path";
	public final static String TNFS_CLOSE_ON_FS_CLOSE = "tnfs-close-on-fs-close";

	protected static final long TRANSFER_SIZE = 8192;

	static IOException translateException(Exception e) {
		if(e instanceof IOException ioe) {
			return ioe;
		}
		else if(e instanceof UncheckedIOException uoe) {
			return uoe.getCause();
		}
		else if(e instanceof RuntimeException re) {
			throw re;
		}
		else 
			return new IOException(e.getMessage(), e);
	}

	private final Map<URI, TNFSFileSystem> filesystems = Collections.synchronizedMap(new HashMap<>());

	@Override
	public void checkAccess(Path path, AccessMode... modes) throws IOException {
		var modeList = Arrays.asList(modes);
		if (modeList.contains(AccessMode.EXECUTE))
			throw new AccessDeniedException("Cannot execute files on this file system.");

		var tnfsPath = (TNFSPath) path;
		try {
			var fs = tnfsPath.getFileSystem();
			var pstr = toAbsolutePathString(path);
			fs.getMount().stat(pstr);
			/*
			 * Just assume we can read and write. TNFS itself provides no way to test if the
			 * currently authenticated user can read or write.
			 * 
			 * We could potentially try opening the file for reading and check for any
			 * error, but writing is more risky.
			 * 
			 * If we had access to a shell we could use 'test' unix shell built-in command.
			 */
		} catch(Exception e) {
			throw translateException(e);
		}

	}

	@Override
	public void copy(Path source, Path target, CopyOption... options) throws IOException {
//		var sourceTnfsPath = (TNFSPath) source;
//		var optionsList = Set.of(options);
//		try {
//			var fs = sourceTnfsPath.getFileSystem();
//			var sourcePath = toAbsolutePathString(source);
//			var targetPath = toAbsolutePathString(target);
//			var tnfs = fs.getMount();			
//			var replaceExisting = optionsList.contains(StandardCopyOption.REPLACE_EXISTING);
//			
//			if(Files.isDirectory(source)) {
//				/* If the file is a directory then an empty directory is
//			     * created in the target location (entries in the directory are not
//			     * copied). This method can be used with the {@link #walkFileTree
//			     * walkFileTree} method to copy a directory and all entries in the directory,
//			     * or an entire <i>file-tree</i> where required.
//			     */
//				if(Files.exists(target)) {
//					boolean hasContents;
//					try(var str = newDirectoryStream(target, null)) {
//						hasContents = str.iterator().hasNext();
//					}
//					if(hasContents && replaceExisting) {
//						/* 
//						 * Replace an existing file. A non-empty directory cannot be
//					     * replaced. 
//						 */
//						throw new DirectoryNotEmptyException(target.toString());
//					}
//				}
//				else {
//					createDirectory(target);
//				}
//			}
//	
//			try {
//				tnfs.copyRemoteFile(sourcePath, targetPath, replaceExisting);
//			} catch (TnfsStatusException se) {
//				if (se.getStatus() == TnfsStatusException.SSH_FX_OP_UNSUPPORTED) {
//					if (!replaceExisting && Files.exists(target))
//						throw new FileAlreadyExistsException(targetPath);
//					try (var in = tnfs.getInputStream(sourcePath)) {
//						try (var out = tnfs.getOutputStream(targetPath)) {
//							in.transferTo(out);
//						}
//					}
//				} else
//					throw se;
//			}
//
//			if (optionsList.contains(StandardCopyOption.COPY_ATTRIBUTES)) {
//				var stat = tnfs.stat(sourcePath);
//				var otherStat = TnfsFileAttributesBuilder.create().withFileAttributes(tnfs.stat(targetPath));
//				otherStat.withPermissions(stat.permissions());
//				otherStat.withUidOrUsername(stat.bestUsernameOr());
//				otherStat.withGidOrGroup(stat.bestGroupOr());
//				tnfs.getSubsystemChannel().setAttributes(targetPath, otherStat.build());
//			}
//
//		} catch (Exception e) {
//			throw translateException(e);
//		}
		throw new UnsupportedOperationException();

	}

	@Override
	public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
		var tnfsPath = (TNFSPath) dir;
		try {
			var fs = tnfsPath.getFileSystem();
			fs.getMount().mkdir(toAbsolutePathString(dir));
		} catch (Exception e) {
			throw translateException(e);
		}
	}

	@Override
	public void delete(Path path) throws IOException {
		var tnfsPath = (TNFSPath) path;
		if (path.isAbsolute() && path.toString().equals("/"))
			throw new IOException("Cannot delete root path.");
		try {
			var fs = tnfsPath.getFileSystem();
			fs.getMount().unlink(toAbsolutePathString(tnfsPath));
		} catch (Exception e) {
			throw translateException(e);
		}
	}

	@Override
	public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
		return (V)TNFSFileAttributeViews.get((TNFSPath) path, type);
	}

	@Override
	public FileStore getFileStore(Path path) throws IOException {
		var tnfsPath = (TNFSPath) path;
		var fs = tnfsPath.getFileSystem();
		return new TNFSFileStore(fs.getMount(), toAbsolutePathString(tnfsPath));
	}

	@Override
	public FileSystem getFileSystem(URI uri) {
		synchronized (filesystems) {
			var fs = filesystems.get(uri);
			if (fs == null)
				throw new FileSystemNotFoundException(
						MessageFormat.format("Cannot find TNFS file system for {0}", uri));
			return fs;
		}
	}

	@Override
	public Path getPath(URI uri) {
		if ("tnfs".equals(uri.getScheme())) {
			synchronized (filesystems) {
				for(var fs : filesystems.entrySet()) {
					if( isCompatibleUri(uri, fs.getKey())) {
						return fs.getValue().getPath(uri.getPath());
					}
				}
				try {
					return newFileSystem(uri, Collections.emptyMap()).getPath("/");
				} catch (IOException e) {
					throw new FileSystemNotFoundException(MessageFormat.format("File system {0} not found.", uri));
				}
			}
		}
		throw new UnsupportedOperationException();
	}

	protected boolean isCompatibleUri(URI uri, URI other) {
		var path1 = other.getPath();
		var compatiblePath = ( path1 == null && uri.getPath() == null ) || ( path1 != null && uri.getPath() != null && uri.getPath().startsWith(path1));
		var compatibleHost = Objects.equals(uri.getHost(), other.getHost());
		var compatiblePort = Objects.equals(uri.getPort(), other.getPort());
		return compatiblePath && compatibleHost && compatiblePort;
	}

	@Override
	public String getScheme() {
		return "tnfs";
	}

	@Override
	public boolean isHidden(Path path) throws IOException {
		return path.getFileName().toString().startsWith(".");
	}

	@Override
	public boolean isSameFile(Path path1, Path path2) throws IOException {
		if(path1 instanceof TNFSPath && path2 instanceof TNFSPath && Files.exists(path1) && Files.exists(path2)) {
			var full1 = ((TNFSPath)path1).toAbsolutePath();
			var full2 = path2.toAbsolutePath();
			return full1.equals(full2);
		}
		else
			return false;
	}

	@Override
	public void move(Path source, Path target, CopyOption... options) throws IOException {
		var sourceTnfsPath = (TNFSPath) source;
		var optionsList = Set.of(options);
		try {
			var fs = sourceTnfsPath.getFileSystem();
			var sourcePath = toAbsolutePathString(source);
			var targetPath = toAbsolutePathString(target);
			var replaceExisting = optionsList.contains(StandardCopyOption.REPLACE_EXISTING);
			var mount = fs.getMount();

			if (replaceExisting && Files.exists(target))
				Files.delete(target);
			mount.rename(sourcePath, targetPath);
			
		} catch (Exception e) {
			throw translateException(e);
		}

	}

	@Override
	public AsynchronousFileChannel newAsynchronousFileChannel(Path path, Set<? extends OpenOption> options,
			ExecutorService exec, FileAttribute<?>... attrs) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
			throws IOException {
		return newFileChannel(path, options, attrs);
	}

	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException {
		return new TNFSDirectoryStream((TNFSPath) dir, filter);
	}

	@Override
	public FileChannel newFileChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
			throws IOException {

		var tnfsPath = (TNFSPath) path;
		try {
			var fs = tnfsPath.getFileSystem();
			var pstr = toAbsolutePathString(path);

			var flags = OpenFlag.decodeOptions(options.toArray(new OpenOption[0]));

			var deleteOnClose = options.contains(StandardOpenOption.DELETE_ON_CLOSE);
			var handle = fs.getMount().open(pstr, flags);

			return new TNFSFileChannel(deleteOnClose, path, handle);

		} catch (Exception e) {
			throw translateException(e);
		}

	}

	@Override
	public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
		synchronized (filesystems) {
			var pathStr = (String) env.get(MOUNT_PATH);
			if(pathStr != null) {
				uri = uri.resolve(pathStr);
			}
			
			if (filesystems.containsKey(uri))
				throw new FileSystemAlreadyExistsException();
			var tnfsMount = (TNFSMount) env.get(TNFS_MOUNT);
			var closeOnFsClose = (Boolean) env.get(TNFS_CLOSE_ON_FS_CLOSE);

			if (tnfsMount == null) {

				if (closeOnFsClose == null)
					closeOnFsClose = true;

				var tnfsClient = (TNFSClient) env.get(TNFS_CLIENT);

				if (tnfsClient == null) {
					String hostname = uri.getHost();
					Integer port = uri.getPort();

					if (env.containsKey(HOSTNAME))
						hostname = (String) env.get(HOSTNAME);
					if (env.containsKey(PORT))
						port = (Integer) env.get(PORT);

					if (port == -1)
						port = TNFS.DEFAULT_PORT;

					var bldr = new TNFSClient.Builder().withPort(port);

					if(hostname != null)
						bldr.withHostname(hostname);
						
					tnfsClient  = bldr.build();
				}

				String username = null;
				String password = null;
				var userInfo = uri.getRawUserInfo();
				if (userInfo != null) {
					var idx = userInfo.indexOf(':');
					if (idx == -1) {
						username = uri.getUserInfo();
					} else {
						username = decodeUserInfo(userInfo.substring(0, idx));
						password = decodeUserInfo(userInfo.substring(idx + 1));
					}
				}

				if (env.containsKey(USERNAME))
					username = (String) env.get(USERNAME);
				if (env.containsKey(PASSWORD))
					password = (String) env.get(PASSWORD);
				
				var tnfsMountBldr = tnfsClient.mount(uri.getPath());
				if(username != null) {
					tnfsMountBldr.withUsername(username);
					if(password != null) {
						tnfsMountBldr.withPassword(password);
					}
				}
						
				tnfsMount = tnfsMountBldr.build();
			} else {
				if (closeOnFsClose == null)
					closeOnFsClose = true;
			}
			var vfs = new TNFSFileSystem(tnfsMount, this, emptyOptionalIfBlank(uriToRootPath(uri)), closeOnFsClose, uri);
			filesystems.put(uri, vfs);
			return vfs;
		}
	}

	protected String uriToRootPath(URI uri) {
		var path = uri.getPath();
		if(path.equals("///")) 
			return "/";
		else if(path.equals("//"))
			return "";
		else if(path.startsWith("//"))
			return path.substring(1);
		else
			return path;
	}

	static String decodeUserInfo(String userinfo) {
		return URI.create("tnfs://" + userinfo + "@localhost").getUserInfo();
	}

	@Override
	public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
			throws IOException {
		var tnfsPath = (TNFSPath) path;
		return TNFSFileAttributeViews.getAttributes(tnfsPath, type);
	}

	@Override
	public Map<String, Object> readAttributes(Path path, String attribute, LinkOption... options) throws IOException {
		return ((TNFSPath) path).readAttributes(attribute, options);
	}

	@Override
	public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
		((TNFSPath) path).setAttribute(attribute, value, options);
	}

	void remove(URI path) {
		filesystems.remove(path);
	}


}
