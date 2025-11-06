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

import static uk.co.bithatch.tnfs.nio.TNFSFileSystem.toAbsolutePathString;
import static uk.co.bithatch.tnfs.nio.TNFSFileSystemProvider.translateException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.ClosedDirectoryStreamException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class TNFSDirectoryStream implements DirectoryStream<Path> {
	private final DirectoryStream.Filter<? super Path> filter;
	private volatile Iterator<Path> iterator;
	private volatile boolean open = true;
	private final Path path;

	TNFSDirectoryStream(TNFSPath tnfsPath, DirectoryStream.Filter<? super Path> filter) throws IOException {
		this.path = tnfsPath.normalize();
		this.filter = filter;
		if (Files.exists(path)) {
			if (!Files.isDirectory(path)) {
				throw new NotDirectoryException(tnfsPath.toString());
			}
		} else
			throw new NoSuchFileException(tnfsPath.toString());
	}

	@Override
	public synchronized void close() throws IOException {
		open = false;
	}

	@Override
	public synchronized Iterator<Path> iterator() {
		if (!open)
			throw new ClosedDirectoryStreamException();
		if (iterator != null)
			throw new IllegalStateException();
		try {
			var fsPath = (TNFSPath) path;
			try {
				var fs = fsPath.getFileSystem();
				var pstr = toAbsolutePathString(path);
				var mount = fs.getMount();
				var it = mount.list(pstr).iterator();

				iterator = new Iterator<>() {

					Path next = null;

					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}

					@Override
					public boolean hasNext() {
						if (!open)
							return false;
						checkNext();
						return next != null;
					}

					@Override
					public Path next() {
						if (!open)
							throw new NoSuchElementException();
						try {
							checkNext();
							if (next == null) {
								throw new NoSuchElementException();
							}
							return next;
						} finally {
							next = null;
						}
					}

					private void checkNext() {
						if (next == null) {
							while (true) {
								var hasNext = it.hasNext();
								if (hasNext) {
									var nextFile = it.next();
									/* TODO: check this will never actual happen */
									/*if (nextFile.getFilename().equals(".") || nextFile.getFilename().equals(".."))
										continue; */
									var p = path.resolve(nextFile);
									try {
										if (filter == null || filter.accept(p)) {
											next = p;
											return;
										}
									} catch (IOException ioe) {
										throw new UncheckedIOException(ioe);
									}
								} else
									return;
							}
						}
					}

				};
			} catch (Exception e) {
				throw translateException(e);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return iterator;
	}

}
