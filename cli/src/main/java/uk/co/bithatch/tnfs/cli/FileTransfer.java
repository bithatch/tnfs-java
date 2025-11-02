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
package uk.co.bithatch.tnfs.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;

import org.jline.utils.OSUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import uk.co.bithatch.tnfs.client.TNFSFile;
import uk.co.bithatch.tnfs.client.TNFSMount;
import uk.co.bithatch.tnfs.lib.OpenFlag;
import uk.co.bithatch.tnfs.lib.Util;

public final class FileTransfer {
	private final static Logger LOG = LoggerFactory.getLogger(FileTransfer.class);

	private final boolean force;
	private final boolean progress;
	private final boolean recursive;
	private final String sep;

	public FileTransfer(boolean force, boolean progress, boolean recursive, String sep) {
		super();
		this.force = force;
		this.progress = progress;
		this.recursive = recursive;
		this.sep = sep;
	}

	public void localToRemote(TNFSMount mount, Path localFile, String file) throws IOException {

		LOG.info("Local {} to remote {} on {}", localFile, file, mount.mountPath());
		if (Files.isDirectory(localFile)) {
			if (!recursive) {
				throw new IllegalArgumentException("Source is a directory. Use -r to recursively copy.");
			}

			Files.walkFileTree(localFile, new SimpleFileVisitor<>() {

				private String currentPath = Util.dirname(file);

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					currentPath = Util.concatenatePaths(currentPath, dir.getFileName().toString(), sep);
					return super.preVisitDirectory(dir, attrs);
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					mount.mkdir(currentPath);
					copyLocalToRemoteFile(mount, file, Util.concatenatePaths(currentPath, file.getFileName().toString(), currentPath), Files.size(file));
					return super.visitFile(file, attrs);
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					currentPath = Util.dirname(currentPath);
					return super.postVisitDirectory(dir, exc);
				}

			});
		} else {
			copyLocalToRemoteFile(mount, localFile, file, Files.size(localFile));
		}
	}

	public void remoteToLocal(TNFSMount mount, String file, Path localFile) throws IOException {
		
		LOG.info("Remote {} on {} to local {}", file, mount.mountPath(), localFile);

		var remote = mount.stat(file);

		if (remote.isDirectory()) {
			if (!recursive) {
				throw new IllegalArgumentException("Source is a directory. Use -r to recursively copy.");
			}

			mount.visit(file, new SimpleFileVisitor<>() {

				private Path currentPath = localFile.getParent();

				@Override
				public FileVisitResult preVisitDirectory(TNFSFile dir, BasicFileAttributes attrs) throws IOException {
					currentPath = currentPath.resolve(dir.entry().name());
					return super.preVisitDirectory(dir, attrs);
				}

				@Override
				public FileVisitResult visitFile(TNFSFile file, BasicFileAttributes attrs) throws IOException {
					Files.createDirectories(currentPath);
					copyRemoteToLocalFile(mount, file.path(), currentPath.resolve(file.entry().name()), file.entry().size());
					return super.visitFile(file, attrs);
				}

				@Override
				public FileVisitResult postVisitDirectory(TNFSFile dir, IOException exc) throws IOException {
					currentPath = currentPath.getParent();
					return super.postVisitDirectory(dir, exc);
				}

			});
		} else {
			copyRemoteToLocalFile(mount, file, localFile, remote.size());
		}
	}

	private void copyLocalToRemoteFile(TNFSMount mount, Path localFile, String remote, long size) throws IOException {
		String txt = localFile.getFileName().toString();
		if (progress) {
			try (var pb = createProgressBar(size, txt)) {
				localToRemote(mount, localFile, remote, pb);
			}
		} else {
			localToRemote(mount, localFile, remote, null);
		}
	}

	private void copyRemoteToLocalFile(TNFSMount mount, String remote, Path localFile, long size) throws IOException {
		String txt = Util.basename(remote);
		if (progress) {
			try (var pb = createProgressBar(size, txt)) {
				remoteToLocal(mount, remote, localFile, pb);
			}
		} else {
			remoteToLocal(mount, remote, localFile, null);
		}
	}

	private void remoteToLocal(TNFSMount mount, String remote, Path localFile, ProgressBar pb) throws IOException {
		try (var o = Files.newByteChannel(localFile, getLocalWriteOpenFlags())) {
			try (var f = mount.open(remote)) {
				copyStreams(mount, pb, o, f);
			}
		}
	}

	private void localToRemote(TNFSMount mount, Path localFile, String remote, ProgressBar pb) throws IOException {
		try (var o = Files.newByteChannel(localFile, StandardOpenOption.READ)) {
			try (var f = mount.open(remote, getRemoteWriteOpenFlags())) {
				copyStreams(mount, pb, o, f);
			}
		}
	}

	private void copyStreams(TNFSMount mount, ProgressBar pb, SeekableByteChannel o, SeekableByteChannel f)
			throws IOException {
		var buf = ByteBuffer.allocate(mount.client().messageSize());
		var rd = 0;
		while ((rd = f.read(buf)) != -1) {
			buf.flip();
			o.write(buf);
			buf.clear();
			if (pb != null)
				pb.stepBy(rd);
		}
	}

	private ProgressBar createProgressBar(long size, String txt) {
		var bldr = new ProgressBarBuilder().setTaskName(txt).setInitialMax(size);
		if(OSUtils.IS_WINDOWS) {
			bldr.setStyle(ProgressBarStyle.UNICODE_BLOCK);
		}
		return bldr.build();
	}

	private OpenOption[] getLocalWriteOpenFlags() {
		if (force)
			return new OpenOption[] { StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.CREATE };
		else
			return new OpenOption[] { StandardOpenOption.WRITE, StandardOpenOption.CREATE };
	}
	
	private OpenFlag[] getRemoteWriteOpenFlags() {
		if (force)
			return new OpenFlag[] { OpenFlag.WRITE, OpenFlag.TRUNCATE,
					OpenFlag.CREATE };
		else
			return new OpenFlag[] { OpenFlag.WRITE, OpenFlag.CREATE };
	}
}
