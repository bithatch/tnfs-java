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

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import uk.co.bithatch.tnfs.client.TNFSMount;
import uk.co.bithatch.tnfs.lib.Command.StatResult;
import uk.co.bithatch.tnfs.lib.DirEntryFlag;
import uk.co.bithatch.tnfs.lib.ModeFlag;

public class TNFSFileAttributeViews {

	private TNFSFileAttributeViews() {
	}

	static Set<String> viewNames() {
		return Set.of("basic");
	}

	@SuppressWarnings("unchecked")
	static <V extends FileAttributeView> V get(TNFSPath path, Class<V> type) {
		if (type == null)
			throw new NullPointerException();
		else if (type == BasicFileAttributeView.class)
			return (V) new BasicTNFSFileAttributesView(path);
		return null;
	}

	@SuppressWarnings("unchecked")
	static <V extends BasicFileAttributes> V getAttributes(TNFSPath path, Class<V> type) throws IOException {
		if (type == null)
			throw new NullPointerException();
		else if (type == BasicFileAttributes.class)
			return (V) new BasicTNFSFileAttributesView(path).readAttributes();
		else
			return null;
	}

	@SuppressWarnings("unchecked")
	static <V extends TNFSFileAttributeView> V get(TNFSPath path, String type) {
		if (type.equals("basic"))
			return (V) new BasicTNFSFileAttributesView(path);
		else
			return null;
	}

	public static class BasicTNFSFileAttributes implements BasicFileAttributes {

		protected final StatResult attrs;
		protected final TNFSPath path;

		BasicTNFSFileAttributes(TNFSPath path, StatResult e) {
			this.attrs = e;
			this.path = path;
		}

		@Override
		public FileTime creationTime() {
			return attrs.ctime();
		}

		@Override
		public Object fileKey() {
			/* TODO */
			return null;
		}

		@Override
		public boolean isDirectory() {
			return Arrays.asList(attrs.mode()).contains(ModeFlag.IFDIR);
		}

		@Override
		public boolean isOther() {
			return Arrays.asList(ModeFlag.toDirEntryFlags(path.getFileName().toString(), attrs.mode()))
					.contains(DirEntryFlag.SPECIAL);
		}

		@Override
		public boolean isRegularFile() {
			return !isOther() && !isDirectory();
		}

		@Override
		public boolean isSymbolicLink() {
			return false;
		}

		@Override
		public FileTime lastAccessTime() {
			return attrs.mtime();
		}

		@Override
		public FileTime lastModifiedTime() {
			return attrs.mtime();
		}

		@Override
		public long size() {
			return attrs.size();
		}

	}

	public interface TNFSFileAttributeView extends BasicFileAttributeView {

		void setAttribute(String attribute, Object value) throws IOException;

		Map<String, Object> readAttributes(String attributes) throws IOException;
	}

	public static class BasicTNFSFileAttributesView implements TNFSFileAttributeView {

		static enum BasicAttribute {
			creationTime, fileKey, isDirectory, isOther, isRegularFile, isSymbolicLink, lastAccessTime,
			lastModifiedTime, size
		};

		protected final TNFSPath path;

		private BasicTNFSFileAttributesView(TNFSPath path) {
			this.path = path;
		}

		@Override
		public String name() {
			return "basic";
		}

		@Override
		public BasicTNFSFileAttributes readAttributes() throws IOException {
			var pathStr = toAbsolutePathString(path);
			try {
				return new BasicTNFSFileAttributes(path, getMount().stat(pathStr));
			} catch (Exception e) {
				throw TNFSFileSystemProvider.translateException(e);
			}
		}

		@Override
		public final void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime)
				throws IOException {
			throw new UnsupportedOperationException();
		}

		protected Object attribute(BasicAttribute id, BasicTNFSFileAttributes attributes) {
			switch (id) {
			case size:
				return attributes.size();
			case creationTime:
				return attributes.creationTime();
			case lastAccessTime:
				return attributes.lastAccessTime();
			case lastModifiedTime:
				return attributes.lastModifiedTime();
			case isDirectory:
				return attributes.isDirectory();
			case isRegularFile:
				return attributes.isRegularFile();
			case isSymbolicLink:
				return attributes.isSymbolicLink();
			case isOther:
				return attributes.isOther();
			case fileKey:
			default: /* Done like this for coverage */
				return attributes.fileKey();
			}
		}

		protected final TNFSFileSystem getFileSystem() {
			return path.getFileSystem();
		}

		protected final TNFSMount getMount() {
			return getFileSystem().getMount();
		}

		@Override
		public Map<String, Object> readAttributes(String attributes) throws IOException {
			var zfas = readAttributes();
			var map = new LinkedHashMap<String, Object>();
			if ("*".equals(attributes)) {
				for (var id : BasicAttribute.values()) {
					map.put(id.name(), attribute(id, zfas));
				}
			} else {
				var as = attributes.split(",");
				for (var a : as) {
					try {
						map.put(a, attribute(BasicAttribute.valueOf(a), zfas));
					} catch (IllegalArgumentException x) {
					}
				}
			}
			return map;
		}

		@Override
		public void setAttribute(String attribute, Object value) throws IOException {
			try {
				var attr = BasicAttribute.valueOf(attribute);
				switch (attr) {
				case lastModifiedTime:
					setTimes((FileTime) value, null, null);
					break;
				case lastAccessTime:
					setTimes(null, (FileTime) value, null);
					break;
				case creationTime:
					setTimes(null, null, (FileTime) value);
					break;
				default:
					break;
				}
				return;
			} catch (IllegalArgumentException x) {
			}
			throw new UnsupportedOperationException("'" + attribute + "' is unknown or read-only attribute");
		}
	}

}
