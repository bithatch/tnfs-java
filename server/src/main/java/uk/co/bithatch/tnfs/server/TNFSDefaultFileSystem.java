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

import java.io.File;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.bithatch.tnfs.lib.Command.Entry;
import uk.co.bithatch.tnfs.lib.Command.StatResult;
import uk.co.bithatch.tnfs.lib.ModeFlag;
import uk.co.bithatch.tnfs.lib.OpenFlag;
import uk.co.bithatch.tnfs.lib.ResultCode;
import uk.co.bithatch.tnfs.lib.TNFSDirectory;

/**
 * Default file system implementation based on {@link Path}, i.e. any supported
 * NIO VFS provider.
 * <p>
 * Note, TNFS does not support symbolic links, so these will be presented as special
 * files that cannot be traversed. This is to prevent symbolic links being followed
 * when deleting a file system contents.
 */
public class TNFSDefaultFileSystem extends AbstractTNFSFileSystem {
	private final static Logger LOG = LoggerFactory.getLogger(TNFSDefaultFileSystem.class);
	
	private final String mountPath;
	private final Path root;

	public TNFSDefaultFileSystem(Path root, String mountPath, TNFSAccessCheck accessCheck) throws IOException {
		super(accessCheck);
		if(!Files.exists(root))
			throw new NoSuchFileException(root.toString());
		if(!Files.isDirectory(root))
			throw new NotDirectoryException(root.toString());
		this.mountPath = mountPath;
		this.root = root;
	}

	@Override
	protected void onChmod(String path, ModeFlag... modes) throws IOException {
		
		var resolved = resolve(path); 
		checkFileSymbolicLink(resolved, path);
		checkDescendant(resolved, path);

		var perms = Set.of(ModeFlag.permissions(modes));
		try {
			Files.setPosixFilePermissions(resolved, perms);
		}
		catch(UnsupportedOperationException uoe) {
			var readableOthers =  
					perms.contains(PosixFilePermission.GROUP_READ) ||
					perms.contains(PosixFilePermission.OTHERS_READ);
			var readable = perms.contains(PosixFilePermission.OWNER_READ) || readableOthers;

			
			var writableOthers =  
					perms.contains(PosixFilePermission.GROUP_WRITE) ||
					perms.contains(PosixFilePermission.OTHERS_WRITE);
			var writable = perms.contains(PosixFilePermission.OWNER_WRITE) || writableOthers;
			
			var executableOthers =  
					perms.contains(PosixFilePermission.GROUP_EXECUTE) ||
					perms.contains(PosixFilePermission.OTHERS_EXECUTE);
			var executable = perms.contains(PosixFilePermission.OWNER_EXECUTE) || executableOthers;
			
			resolved.toFile().setReadable(readable, readableOthers);
			resolved.toFile().setExecutable(executable, executableOthers);
			resolved.toFile().setWritable(writable, writableOthers);
		}
	}

	@Override
	public void close() throws IOException {
	}

	@Override
	protected TNFSDirectory onDirectory(String path, String wildcard) throws IOException {
		
		var resolved = resolve(path);
		
		if(LOG.isDebugEnabled()) {
			LOG.debug("Listing entries for `{}` (resolved at `{}`), wildcard pattern of `{}`", path, resolved, wildcard);
		}
		if(isSymbolicLink(resolved)) {
			throw new NotDirectoryException(path);
		}
		checkDescendant(resolved, path);
		
		Stream<Entry> str;
		if(wildcard.isBlank()) {
			str = list(resolved).map(p -> {
				return toEntry(resolved, p);
			});
		}
		else {
			var pattern = root.getFileSystem().getPathMatcher("glob:" + wildcard);
			str = list(resolved).
				filter(p -> {
					return pattern.matches(p.getFileName());
				}).
				map(p -> {
					return toEntry(resolved, p);
				});
		}
		

		return new TNFSDirectory() {
			
			@Override
			public void close() throws IOException {
				str.close();
			}
			
			@Override
			public long tell() throws IOException {
				throw new UnsupportedOperationException();
			}
			
			@Override
			public Stream<Entry> stream() {
				return str;
			}
			
			@Override
			public void seek(long position) throws IOException {
				throw new UnsupportedOperationException();
			}
		};
	}

	@Override
	protected long onFree() throws IOException {		
		return Math.min(Files.getFileStore(root).getUsableSpace() / 1024l, (long)Integer.MAX_VALUE * 2l);
	}

	@Override
	protected Stream<String> onList(String path) throws IOException {
		
		var rpath = resolve(path);
		if(isSymbolicLink(rpath)) {
			throw new NotDirectoryException(path);
		}
		checkDescendant(rpath, path);
		return Files.list(rpath).map(p -> p.getFileName().toString()).sorted();
	}

	@Override
	protected void onMkdir(String path) throws IOException {
		
		var rpath = resolve(path);
		checkFileSymbolicLink(rpath, path);
		checkDescendant(rpath, path);
		Files.createDirectory(rpath);
	}

	@Override
	public String mountPath() {
		return mountPath;
	}

	@Override
	protected SeekableByteChannel onOpen(String path, ModeFlag[] mode, List<OpenFlag> flgs) throws IOException {
		
		var rpath = resolve(path);
		checkFileSymbolicLink(rpath, path);
		checkDescendant(rpath, path);
		var oflgs = OpenFlag.encodeOptions(flgs.toArray(new OpenFlag[0]));
		var chnl = Files.newByteChannel(rpath, oflgs);
		
		if(flgs.contains(OpenFlag.CREATE)) {
			/* TODO umask? */
			chmod(path, mode);
		}
		return chnl;
	}

	@Override
	protected void onRename(String path, String targetPath) throws IOException {
		var rpath1 = resolve(path);
		checkFileSymbolicLink(rpath1, path);
		checkDescendant(rpath1, path);
		
		var rpath2 = resolve(targetPath);
		checkFileSymbolicLink(rpath2, targetPath);
		checkDescendant(rpath2, targetPath);
		
		Files.move(rpath1, rpath2);
	}

	@Override
	protected void onRmdir(String path) throws IOException {

		var dpath = resolve(path);
		checkFileSymbolicLink(dpath, path);
		checkDescendant(dpath, path);
		if(dpath.toAbsolutePath().toString().equals(root.toAbsolutePath().toString()))
			throw new AccessDeniedException(path);
		else
			Files.delete(dpath);
	}
	
	@Override
	protected long onSize() throws IOException {
		
		return Math.min(Files.getFileStore(root).getTotalSpace() / 1024l, (long)Integer.MAX_VALUE * 2l);
	}

	@Override
	protected StatResult onStat(String path) throws IOException {
		
		var p = resolve(path);
		checkFileSymbolicLink(p, path);
		checkDescendant(p, path);
		var basicAttrView = Files.getFileAttributeView(p, BasicFileAttributeView.class);
		var posixAttrView = Files.getFileAttributeView(p, PosixFileAttributeView.class);
		var dosAttrView = Files.getFileAttributeView(p, DosFileAttributeView.class);
		
		if(posixAttrView == null) {
			if(dosAttrView == null) {
				var basicAttrs = basicAttrView.readAttributes();
				return new StatResult(
						ResultCode.SUCCESS, 
						ModeFlag.forAttributes(basicAttrs, p),
						0,
						0,
						basicAttrs.size(), 
						basicAttrs.lastAccessTime(), 
						basicAttrs.lastModifiedTime(), 
						basicAttrs.creationTime(), 
						"", 
						"");
			}
			else {
				var dosAttrs = dosAttrView.readAttributes();
				return new StatResult(
						ResultCode.SUCCESS, 
						ModeFlag.forAttributes(dosAttrs, p),
						0,
						0,
						dosAttrs.size(), 
						dosAttrs.lastAccessTime(), 
						dosAttrs.lastModifiedTime(), 
						dosAttrs.creationTime(), 
						"", 
						"");
			}
		}
		else {
			var posixAttrs = posixAttrView.readAttributes();
			var owner = posixAttrs.owner();
			var group = posixAttrs.group();
			return new StatResult(
					ResultCode.SUCCESS, 
					ModeFlag.forAttributes(posixAttrs),
					owner.hashCode(),
					group.hashCode(),
					posixAttrs.size(), 
					posixAttrs.lastAccessTime(), 
					posixAttrs.lastModifiedTime(), 
					posixAttrs.creationTime(), 
					owner.getName(), 
					group.getName());
		}
	}

	@Override
	protected void onUnlink(String path) throws IOException {
		
		var rpath = resolve(path);
		checkFileSymbolicLink(rpath, path);
		checkDescendant(rpath, path);
		Files.delete(rpath);
	}

	private Stream<Path> list(Path path) throws IOException {
		if(path.getParent() == null) {
			return Stream.concat(Stream.of(path), Files.list(path));
		}
		else {
			return Stream.concat(Stream.of(path, path.getParent()), Files.list(path));
		}
	}

	private Path resolve(String path) {
		path = path.replace('/', File.separatorChar);
		while(path.startsWith(File.separator)) {
			path = path.substring(1);
		}
		if(path.equals("")) {
			return root;
		}
		else
			return root.resolve(path);
		
	}
	
	private Entry toEntry(Path resolved, Path p) {
		if(p.toString().equals(resolved.toString())) {
			return Entry.forCwd(p);
		}
		else if(resolved.getParent() != null && p.toString().equals(resolved.getParent().toString())) {
			return Entry.forParent(p);
		}
		else {
			return Entry.forPath(p);
		}
	}

	private void checkFileSymbolicLink(Path path, String strpath) throws IOException {
		if(isSymbolicLink(path.getParent())) {
			throw new NoSuchFileException(strpath);
		}
	}

	private void checkDescendant(Path path, String strpath) throws IOException {
		var thisPath = path.toAbsolutePath();
		var rootPath = root.toAbsolutePath();
		if(!thisPath.startsWith(rootPath)) {
			throw new AccessDeniedException(strpath);
		}
	}

	private boolean isSymbolicLink(Path path) {
		while(path != null && !path.toUri().equals(root.toUri())) {
			if(Files.isSymbolicLink(path)) {
				return true;
			}	
			path = path.getParent();
		}
		return false;
	}

}
