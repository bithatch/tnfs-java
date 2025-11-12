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
package uk.co.bithatch.tnfs.client;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.NoSuchFileException;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import uk.co.bithatch.tnfs.lib.DirOptionFlag;
import uk.co.bithatch.tnfs.lib.DirSortFlag;
import uk.co.bithatch.tnfs.lib.ModeFlag;
import uk.co.bithatch.tnfs.lib.OpenFlag;
import uk.co.bithatch.tnfs.lib.TNFSDirectory;
import uk.co.bithatch.tnfs.lib.TNFSFileAccess;

public interface TNFSMount extends TNFSFileAccess {

	/**
	 * List all the names of entries in the root of this mount. This is a convenience method
	 * for {@link #list(String)} with a value of <code>/</code>.
	 *
	 * @return stream of entry names
	 * @throws IOException on any error
	 */
	default Stream<String> list() throws IOException {
		return list("/");
	}

	/**
	 * List all the entries in the root of this mount. This is a convenience method
	 * for {@link #directory(String)} with a value of <code>/</code>.
	 *
	 * @return stream of entry names
	 * @throws IOException on any error
	 */
	default TNFSDirectory entries() throws IOException {
		return directory("/");
	}

	/**
	 * List all the entries given a path. All paths (including absolute paths) are relative to the root of the mount.
	 *
	 * @param path path
	 * @return stream of entries
	 * @throws IOException on any error
	 */
	default TNFSDirectory directory(String path) throws IOException {
		return directory(path, "");
	}

	default TNFSDirectory directory(String path, String wildcard) throws IOException {
		return directory(path, wildcard, new DirOptionFlag[0]);
	}

	/**
	 * List all the entries given a path. All paths (including absolute paths) are relative to the root of the mount.
	 *
	 * @param path path
	 * @param dirOptions directory options.
	 * @return stream of entries
	 * @throws IOException on any error
	 */
	default TNFSDirectory entries(String path, DirOptionFlag... dirOptions) throws IOException {
		return directory(path, "", dirOptions);
	}

	/**
	 * List all the entries given a path. All paths (including absolute paths) are relative to the root of the mount.
	 * @param path path
	 * @param wildcard wildcard entries must match
	 * @param dirOptions directory options.
	 *
	 * @return stream of entries
	 * @throws IOException on any error
	 */
	default TNFSDirectory directory(String path, String wildcard, DirOptionFlag... dirOptions) throws IOException {
		return directory(0, path, wildcard, dirOptions, new DirSortFlag[0]);
	}

	/**
	 * Open a file channel
	 *
	 * @param path
	 * @return file channel
	 * @throws IOException on error
	 */
	default SeekableByteChannel open(String path, OpenFlag... flags) throws IOException {
		var flgs = Arrays.asList(flags);
		return open(
			path, 
			flgs.contains(OpenFlag.CREATE) 
				? ModeFlag.DEFAULT_WRITABLE_FLAGS 
				: ModeFlag.DEFAULT_FLAGS, 
			flags
		);
	}

	<EXT extends TNFSMountExtension> EXT extension(Class<EXT> extension);

	/**
	 * Recursively visit all all entries at the specified path.
	 *
	 * @param path path to visit
	 * @param visitor visitor
	 * @return result
	 * @throws IOException on I/O error
	 */
	FileVisitResult visit(String path, FileVisitor<TNFSFile> visitor) throws IOException;

	TNFSClient client();
	
	/**
	 * Get the username this mount is authenticated as.
	 * 
	 * @return username
	 */
	Optional<String> username();

	/**
	 * Create a new file, throwing an exception if it already exists. The file handle
	 * is immediately closed if successful.
	 * 
	 * @param path
	 * @throws IOException on I/O error
	 */
	default void newFile(String path) throws IOException {
		open(path, OpenFlag.CREATE, OpenFlag.EXCLUSIVE).close();;
	}
	
	/**
	 * Recursively delete this folder (or file). Obviously, use with caution! TNFS
	 * does not support symbolic links, so they will not be followed.
	 * 
	 * @param path path to delete
	 * @throws IOException on I/O error
	 */
	default void deleteRecursively(String path) throws IOException {
		visit(path, new SimpleFileVisitor<TNFSFile>() {
			@Override
			public FileVisitResult preVisitDirectory(TNFSFile dir, BasicFileAttributes attrs) throws IOException {
				if (attrs.isSymbolicLink()) {
					return FileVisitResult.SKIP_SUBTREE;
				}
				return super.preVisitDirectory(dir, attrs);
			}

			@Override
			public FileVisitResult visitFile(TNFSFile file, BasicFileAttributes attrs) throws IOException {
				unlink(file.path());
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(TNFSFile dir, IOException exc) throws IOException {
				rmdir(dir.path());
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/**
	 * Convenience method to test if a file exists.
	 * 
	 * @param path path 
	 * @return exists
	 * @throws IOException on I/O error
	 */
	default boolean exists(String path) throws IOException {
		try {
			stat(path);
			return true;
		}
		catch(NoSuchFileException nsfe) {
			return false;
		}
	}
}
