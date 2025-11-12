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
package uk.co.bithatch.tnfs.web;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.json.JSONPropertyIgnore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.bithatch.tnfs.client.TNFSMount;
import uk.co.bithatch.tnfs.lib.Command.Entry;
import uk.co.bithatch.tnfs.lib.Command.StatResult;
import uk.co.bithatch.tnfs.lib.DirEntryFlag;
import uk.co.bithatch.tnfs.lib.DirOptionFlag;
import uk.co.bithatch.tnfs.lib.DirSortFlag;
import uk.co.bithatch.tnfs.lib.OpenFlag;
import uk.co.bithatch.tnfs.lib.TNFS;
import uk.co.bithatch.tnfs.lib.Util;
import uk.co.bithatch.tnfs.web.elfinder.core.Target;
import uk.co.bithatch.tnfs.web.elfinder.core.Volume;
import uk.co.bithatch.tnfs.web.elfinder.support.content.detect.DefaultFileTypeDetector;
import uk.co.bithatch.tnfs.web.elfinder.support.content.detect.Detector;

public class TNFSMountVolume implements Volume {
	
	private final static class Lazy {
		static Logger LOG = LoggerFactory.getLogger(TNFSMountVolume.class);
	}
	
	interface TNFSTarget extends Target {
		String getPath();

		long getSize() throws IOException;
		
		Target getParent();

		long getLastModified() throws IOException;

		boolean isFolder();
		
		boolean exists();
		
	}

	private abstract static class AbstractTNFSTarget implements TNFSTarget {

		protected final TNFSMountVolume volume;
		protected final String path;
		protected final Supplier<Target> parent;
		
		protected AbstractTNFSTarget(TNFSMountVolume volume, String path) {
			this(volume, null, path);
		}
		
		protected AbstractTNFSTarget(TNFSMountVolume volume, Supplier<Target> parent, String path) {
			this.volume = volume;
			this.path = path;
			this.parent = parent;
			if(path.equals("/") && parent != null) {
				throw new IllegalArgumentException("Root path cannot have a parent");
			}
		}

		@Override
		public final int hashCode() {
			return Objects.hash(path, volume);
		}

		@Override
		public final boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (obj instanceof TNFSTarget other) {
				return Objects.equals(path, other.getPath()) && Objects.equals(volume, other.getVolume());
			}
			else {
				return false;
			}
		}

		@Override
		public final Volume getVolume() {
			return volume;
		}

		@Override
		public final String getPath() {
			return path;
		}

		@Override
	    @JSONPropertyIgnore
		public final Target getParent() {
			return parent.get();
		}

		@Override
		public final String toString() {
			return getPath();
		}
	}
	
	private final static class StatTNFSTarget extends AbstractTNFSTarget {
		
		
		private StatResult stat;
		private Boolean dir;
		private Boolean exists;
		
		private StatTNFSTarget(TNFSMountVolume volume, String path) {
			super(volume, path);
		}
		
		private StatTNFSTarget(TNFSMountVolume volume, Supplier<Target> parent, String path) {
			super(volume, parent, path);
		}
		
		private StatTNFSTarget(TNFSMountVolume volume, TNFSTarget parent, String path) {
			super(volume, () -> parent, path);
		}

		@Override
		public long getSize() throws IOException {
			return stat().size();
		}

		@Override
		public long getLastModified() throws IOException {
			return stat().mtime().toMillis();
		}

		@Override
		public boolean isFolder() {
			if(dir == null) {
				try {
					stat();
				}
				catch(IOException ioe) {
				}
			}
			return dir == null ? false : dir;
		}

		@Override
		public boolean exists() {
			if(exists == null) {
				try {
					stat();
				} catch (IOException e) { 
				}
			}
			return exists == null ? false : exists;
		}
		
		private StatResult stat() throws IOException {
			if(stat == null) {
				try {
					stat = volume.mount.stat(path);
					exists = true;
					dir = stat.isDirectory();
				}
				catch(FileNotFoundException nfe) {
					exists = false;
					dir = false;
					throw nfe;
				}
			}
			return stat;
		}
	}
	
	private final static class EntryTNFSTarget extends AbstractTNFSTarget {
		
		private final Entry entry;
		
		private EntryTNFSTarget(TNFSMountVolume volume, TNFSTarget parent, Entry entry) {
			super(volume, () -> parent, Util.concatenatePaths(parent.getPath(), entry.name(), TNFS.UNIX_SEPARATOR));
			this.entry = entry;
		}

		@Override
		public long getSize() throws IOException {
			return entry.size();
		}

		@Override
		public long getLastModified() throws IOException {
			return entry.mtime().toMillis();
		}

		@Override
		public boolean isFolder() {
			return DirEntryFlag.isDirectory(entry.flags());
		}

		@Override
		public boolean exists() {
			return true;
		}
	}
	
	private final TNFSMount mount;
	private final Detector<String> detector;
	private final String alias;
	private final StatTNFSTarget root;

	public  TNFSMountVolume(TNFSMount mount, String alias) {
		this.mount = mount;
		this.alias = alias;
        this.detector = new DefaultFileTypeDetector();
        this.root = new StatTNFSTarget(this, "/");
	}

	@Override
	public void createFile(Target target) throws IOException {
		if(Lazy.LOG.isDebugEnabled()) {
			Lazy.LOG.debug("Create new file {}", target);
		}
		mount.open(((TNFSTarget)target).getPath(), OpenFlag.WRITE, OpenFlag.EXCLUSIVE, OpenFlag.CREATE).close();
	}

	@Override
	public void createFolder(Target target) throws IOException {
		if(Lazy.LOG.isDebugEnabled()) {
			Lazy.LOG.debug("Create new folder {}", target);
		}
		mount.mkdir(((TNFSTarget)target).getPath());
	}

	@Override
	public void deleteFile(Target target) throws IOException {
		if(Lazy.LOG.isDebugEnabled()) {
			Lazy.LOG.debug("Delete file {}", target);
		}
		mount.unlink(((TNFSTarget)target).getPath());
	}

	@Override
	public void deleteFolder(Target target) throws IOException {
		if(Lazy.LOG.isDebugEnabled()) {
			Lazy.LOG.debug("Delete folder {}", target);
		}
		mount.rmdir(((TNFSTarget)target).getPath());
	}

	@Override
	public boolean exists(Target target) {
		return ((TNFSTarget)target).exists();
	}

	@Override
	public Target fromPath(String relativePath) {

        String rootDir = getRoot().toString();

        String path;
        if (relativePath.startsWith(rootDir)) {
            path = relativePath;
        } else {
            path = Util.concatenatePaths(rootDir, relativePath, TNFS.UNIX_SEPARATOR);
        }
        
        if(path.equals("/"))
        	return new StatTNFSTarget(this, path);
        else {
			return new StatTNFSTarget(this, () -> {
				var ppath = Util.dirname(path);
				return fromPath(ppath);
			}, path);
        }
	}

	@Override
	public long getLastModified(Target target) throws IOException {
		return ((TNFSTarget)target).getLastModified();
	}

	@Override
	public String getMimeType(Target target) throws IOException {
		if(isFolder(target)) {
			return "directory";
		}
		return detector.detect(((TNFSTarget)target).getPath());
	}

	@Override
	public String getAlias() {
		return alias;
	}

	@Override
	public String getName(Target target) {
		return Util.basename(((TNFSTarget)target).getPath());
	}

	@Override
	public Target getParent(Target target) {
		return ((TNFSTarget)target).getParent();
	}

	@Override
	public String getPath(Target target) throws IOException {
		return ((TNFSTarget)target).getPath();
	}

	@Override
	public Target getRoot() {
		return root;
	}

	@Override
	public long getSize(Target target) throws IOException {
		return ((TNFSTarget)target).getSize();
	}

	@Override
	public boolean hasChildFolder(Target target) throws IOException {
		try {
			return Arrays.asList(listChildren(target)).stream().filter(t -> isFolder(t)).findFirst().isPresent();
		}
		catch(NotDirectoryException | AccessDeniedException nde) {
			return false;
		}
	}

	@Override
	public boolean isFolder(Target target) {
		return ((TNFSTarget)target).isFolder();
	}

	@Override
	public boolean isRoot(Target target) throws IOException {
		return target.equals(root);
	}

	@Override
	public Target[] listChildren(Target target) throws IOException {
		try(var dir = mount.directory(0, ((TNFSTarget)target).getPath(), "", new DirOptionFlag[] { DirOptionFlag.NO_SKIPHIDDEN }, new DirSortFlag[] {})) {
			return dir.stream().
					filter(f -> !f.name().equals(".") && !f.name().equals("..")).
					map(f -> new EntryTNFSTarget(this, (TNFSTarget)target, f)).toList().toArray(new Target[0]);	
		}
	}

	@Override
	public InputStream openInputStream(Target target) throws IOException {
		if(Lazy.LOG.isDebugEnabled()) {
			Lazy.LOG.debug("Open input stream {}", target);
		}
		return Channels.newInputStream(mount.open(((TNFSTarget)target).getPath(), OpenFlag.READ));
	}

	@Override
	public OutputStream openOutputStream(Target target) throws IOException {
		if(Lazy.LOG.isDebugEnabled()) {
			Lazy.LOG.debug("Open output stream {}", target);
		}
		return Channels.newOutputStream(mount.open(((TNFSTarget)target).getPath(), OpenFlag.WRITE, OpenFlag.TRUNCATE));
	}

	@Override
	public SeekableByteChannel openChannel(Target target, OpenOption... options) throws IOException {
		return mount.open(((TNFSTarget)target).getPath(), OpenFlag.decodeOptions(options));
	}

	@Override
	public void rename(Target origin, Target destination) throws IOException {
		if(Lazy.LOG.isDebugEnabled()) {
			Lazy.LOG.debug("Rename {} to {}", origin, destination);
		}
		mount.rename(((TNFSTarget)origin).getPath(), ((TNFSTarget)destination).getPath());
	}

	@Override
	public List<Target> search(String target) throws IOException {
		throw new UnsupportedOperationException();
	}

}
