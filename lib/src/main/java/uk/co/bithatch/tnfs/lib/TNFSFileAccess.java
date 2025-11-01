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
package uk.co.bithatch.tnfs.lib;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import uk.co.bithatch.tnfs.lib.Command.Entry;
import uk.co.bithatch.tnfs.lib.Command.StatResult;

/**
 * The API specification for interacting with a single <em>Mount</em>. This
 * interface is used on both the client and server.
 * <p>
 * On the client, a ready-made implementation of it (<code>TNFSMount</code>) is
 * internally constructed and provided to user code to query and manipulate the
 * remote file system.
 * <p>
 * On the server, an implementation must be provided by the developer, and that
 * is called when remote file system requests are received. The
 * <code>server</code> module in the <em>TNFS Java</em> stack provides a default
 * implementation that accesses the local file system.
 */
public interface TNFSFileAccess extends Closeable {

	/**
	 * The virtual path this mount is mounted as.
	 * 
	 * @return mount path
	 */
	String mountPath();

	/**
	 * Remove a directory. Will fail if directory contains any files.
	 * 
	 * @param path path of directory to remove
	 * @throws IOException on error
	 */
	void rmdir(String path) throws IOException;

	/**
	 * Change file modes.
	 * 
	 * @param path  path of file
	 * @param modes modes
	 * @throws IOException on error
	 */
	void chmod(String path, ModeFlag... modes) throws IOException;

	/**
	 * Read information about file at a path.
	 * 
	 * @param path path of file
	 * @throws IOException on error
	 */
	StatResult stat(String path) throws IOException;

	/**
	 * Create a directory.
	 * 
	 * @param path path of directory to create
	 * @throws IOException on error
	 */
	void mkdir(String path) throws IOException;

	/**
	 * Unlink (remove) a file.
	 * 
	 * @param path path of file to remove
	 * @throws IOException on error
	 */
	void unlink(String path) throws IOException;

	/**
	 * Open a file channel
	 * 
	 * @param path
	 * @return file channel
	 */
	SeekableByteChannel open(String path, ModeFlag[] mode, OpenFlag... flags) throws IOException;

	/**
	 * Obtain a handle to the mounts root directory. From the handle object
	 * returned, entries may be read, and the position of the next read may be
	 * queries and changed.
	 * 
	 * @param path        absolute path or path relative to the root of the mount
	 * @return directory handle
	 * @throws IOException on any error
	 */
	default TNFSDirectory directory() throws IOException {
		return directory("");
	}

	/**
	 * Obtain a handle to a directory at given a path. From the handle object
	 * returned, entries may be read, and the position of the next read may be
	 * queries and changed.
	 * 
	 * @param path        absolute path or path relative to the root of the mount
	 * @return directory handle
	 * @throws IOException on any error
	 */
	default TNFSDirectory directory(String path) throws IOException {
		return directory(path, "");
	}

	/**
	 * Obtain a handle to a directory at given a path. From the handle object
	 * returned, entries may be read, and the position of the next read may be
	 * queries and changed.
	 * 
	 * @param path        absolute path or path relative to the root of the mount
	 * @param wildcard    wildcard pattern entries must match, or empty string to
	 *                    match call
	 * @return directory handle
	 * @throws IOException on any error
	 */
	TNFSDirectory directory(String path, String wildcard) throws IOException;

	/**
	 * Obtain a handle to a directory at given a path. From the handle object
	 * returned, entries may be read, and the position of the next read may be
	 * queries and changed.
	 * 
	 * @param maxResults  maximum number of results, or zero for all results
	 * @param path        absolute path or path relative to the root of the mount
	 * @param wildcard    wildcard pattern entries must match, or empty string to
	 *                    match call
	 * @param dirOptions  directory options
	 * @param sortOptions sort options
	 * @return directory handle
	 * @throws IOException on any error
	 */
	default TNFSDirectory directory(int maxResults, String path, String wildcard, DirOptionFlag[] dirOptions,
			DirSortFlag[] sortOptions) throws IOException {
		/* TODO filtering, sorting etc */
		var dirOpts = Arrays.asList(dirOptions);
		var sortOpts = Arrays.asList(sortOptions);
		var dir = directory(path, wildcard);
		var stream = dir.stream();

		/* Filter */
		stream = stream.filter(entry -> {
			var flgs = Arrays.asList(entry.flags());

			if (flgs.contains(DirEntryFlag.HIDDEN)) {

				/* Maybe skip hidden */
				if (flgs.contains(DirEntryFlag.HIDDEN) && !dirOpts.contains(DirOptionFlag.NO_SKIPHIDDEN)) {
					return false;
				}	
			}
			else {

				/* Maybe skip directory */
				if (wildcard.length() > 0 && flgs.contains(DirEntryFlag.DIR)
						&& !dirOpts.contains(DirOptionFlag.DIR_PATTERN)) {
					/*
					 * Is a wildcard match that is a directory, but directories should not be
					 * matched according to dir option flags
					 */
					return false;
				}
				
				/* Maybe skip special */
				if (flgs.contains(DirEntryFlag.SPECIAL) && !dirOpts.contains(DirOptionFlag.NO_SKIPSPECIAL)) {
					return false;
				}	
			}

			return true;
		});
		
		/* Limit what remains */
		if (maxResults > 0) {
			stream = stream.limit(maxResults);
		}

		/* Finally sort */
		if (!sortOpts.contains(DirSortFlag.NONE)) {
			stream = stream.sorted((e1, e2) -> {
				var r = compare(dirOpts, sortOpts, e1, e2);
				if (sortOpts.contains(DirSortFlag.DESCENDING))
					r *= -1;
				return r;
			});
		}

		var fStream = stream;
		return new TNFSDirectory() {

			@Override
			public void close() throws IOException {
				fStream.close();
			}

			@Override
			public Stream<Entry> stream() {
				return fStream;
			}

			@Override
			public void seek(long position) throws IOException {
				dir.seek(position);
			}

			@Override
			public long tell() throws IOException {
				return dir.tell();
			}
			
		};
	}

	static int compare(List<DirOptionFlag> dirOpts, List<DirSortFlag> sortOpts, Entry e1, Entry e2) {
		var flgs1 = Arrays.asList(e1.flags());
		var flgs2 = Arrays.asList(e2.flags());

		if (!dirOpts.contains(DirOptionFlag.NO_FOLDERSFIRST)) {
			var v = Boolean.compare(flgs1.contains(DirEntryFlag.DIR), flgs2.contains(DirEntryFlag.DIR)) * -1;
			if (v != 0) {
				return v;
			}
		}
		if (sortOpts.contains(DirSortFlag.SIZE)) {
			return Long.compare(e1.size(), e2.size());
		} else if (sortOpts.contains(DirSortFlag.MODIFIED)) {
			return Long.compare(e1.mtime().toMillis(), e2.mtime().toMillis());
		} else if (sortOpts.contains(DirSortFlag.CASE)) {
			return e1.name().compareTo(e2.name());
		} else {
			return e1.name().toLowerCase().compareTo(e2.name().toLowerCase());
		}
	}

	/**
	 * List all the names of entries given a path. All paths (including absolute
	 * paths) are relative to the root of the mount.
	 * 
	 * @param path path
	 * @return stream of entry names
	 * @throws IOException on any error
	 */
	Stream<String> list(String path) throws IOException;

	/**
	 * Rename or move a file.
	 * 
	 * @param path       path
	 * @param targetPath targetPath
	 * @throws IOException on any error
	 */
	void rename(String path, String targetPath) throws IOException;

	/**
	 * Get the amount of free space in KiB on the file system that contains the
	 * mount. Note that although the return type is a <code>long</code>, the maximum
	 * value returned must fit in an <code>int</code>, ie. be an unsigned integer of
	 * 4 bytes with a value of <code> 2 * Integer.MAX_VALUE</code>.
	 * 
	 * @return free space in K
	 * @throws IOException on any error
	 */
	long free() throws IOException;

	/**
	 * Get the amount of total space in KiB on the file system that contains the
	 * mount. Note that although the return type is a <code>long</code>, the maximum
	 * value returned must fit in an <code>int</code>, ie. be an unsigned integer of
	 * 4 bytes with a value of <code> 2 * Integer.MAX_VALUE</code>.
	 * 
	 * @return total space in K
	 * @throws IOException on any error
	 */
	long size() throws IOException;

}
