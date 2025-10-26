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

import java.io.File;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Arrays;
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
import uk.co.bithatch.tnfs.lib.TNFSFileSystem;

public class TNFSDefaultFileSystem implements TNFSFileSystem {
	private final static Logger LOG = LoggerFactory.getLogger(TNFSDefaultFileSystem.class);
	
	private final String mountPath;
	private final Path root;

	public TNFSDefaultFileSystem(Path root, String mountPath) throws IOException {
		if(!Files.exists(root))
			throw new NoSuchFileException(root.toString());
		if(!Files.isDirectory(root))
			throw new NotDirectoryException(root.toString());
		this.mountPath = mountPath;
		this.root = root;
	}

	@Override
	public void chmod(String path, ModeFlag... modes) throws IOException {
		Files.setPosixFilePermissions(resolve(path), Set.of(ModeFlag.permissions(modes)));
	}

	@Override
	public void close() throws IOException {
	}

	@Override
	public TNFSDirectory directory(String path, String wildcard) throws IOException {
		var resolved = resolve(path);
		LOG.info("Listing entries for `{}` (resolved at `{}`), wildcard pattern of `{}`", path, resolved, wildcard);
		
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
	public long free() throws IOException {
		return Math.min(Files.getFileStore(root).getUsableSpace() / 1024l, (long)Integer.MAX_VALUE * 2l);
	}

	@Override
	public Stream<String> list(String path) throws IOException {
		return Files.list(resolve(path)).map(p -> p.getFileName().toString()).sorted();
	}

	@Override
	public void mkdir(String path) throws IOException {
		Files.createDirectory(resolve(path));
	}

	@Override
	public String mountPath() {
		return mountPath;
	}

	@Override
	public SeekableByteChannel open(String path, ModeFlag[] mode, OpenFlag... flags) throws IOException {
		SeekableByteChannel chnl = Files.newByteChannel(resolve(path), OpenFlag.encodeOptions(flags));
		if(Arrays.asList(flags).contains(OpenFlag.CREATE)) {
			/* TODO umask? */
			chmod(path, mode);
		}
		return chnl;
	}

	@Override
	public void rename(String path, String targetPath) throws IOException {
		Files.move(resolve(path), resolve(targetPath));
	}

	@Override
	public void rmdir(String path) throws IOException {
		Files.delete(resolve(path));
	}
	
	@Override
	public long size() throws IOException {
		return Math.min(Files.getFileStore(root).getTotalSpace() / 1024l, (long)Integer.MAX_VALUE * 2l);
	}

	@Override
	public StatResult stat(String path) throws IOException {
		var p = resolve(path);
		var basicAttrView = Files.getFileAttributeView(p, BasicFileAttributeView.class);
		var posixAttrView = Files.getFileAttributeView(p, PosixFileAttributeView.class);
		var dosAttrView = Files.getFileAttributeView(p, DosFileAttributeView.class);
		
		if(posixAttrView == null) {
			if(dosAttrView == null) {
				var basicAttrs = basicAttrView.readAttributes();
				return new StatResult(
						ResultCode.SUCCESS, 
						ModeFlag.forAttributes(basicAttrs),
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
						ModeFlag.forAttributes(dosAttrs),
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
	public void unlink(String path) throws IOException {
		Files.delete(resolve(path));
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

}
