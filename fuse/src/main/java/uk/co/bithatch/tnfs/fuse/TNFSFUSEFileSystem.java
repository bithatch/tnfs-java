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
package uk.co.bithatch.tnfs.fuse;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.cryptomator.jfuse.api.DirFiller;
import org.cryptomator.jfuse.api.Errno;
import org.cryptomator.jfuse.api.FileInfo;
import org.cryptomator.jfuse.api.FuseConfig;
import org.cryptomator.jfuse.api.FuseConnInfo;
import org.cryptomator.jfuse.api.FuseOperations;
import org.cryptomator.jfuse.api.Stat;
import org.cryptomator.jfuse.api.Statvfs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.bithatch.tnfs.client.TNFSMount;
import uk.co.bithatch.tnfs.lib.Command.StatResult;
import uk.co.bithatch.tnfs.lib.DirOptionFlag;
import uk.co.bithatch.tnfs.lib.DirSortFlag;
import uk.co.bithatch.tnfs.lib.ModeFlag;
import uk.co.bithatch.tnfs.lib.OpenFlag;
import uk.co.bithatch.tnfs.lib.TNFSDirectory;

/**
 * A {@link FuseOperations} for accessing <em>TNFS</em> via a {@link TNFSMount}.
 */
public class TNFSFUSEFileSystem implements FuseOperations {
	private static final Logger LOG = LoggerFactory.getLogger(TNFSFUSEFileSystem.class);
	private final AtomicLong dirHandleGen = new AtomicLong(1L);
	private final Errno errno;
	private final AtomicLong fileHandleGen = new AtomicLong(1L);
	private final TNFSMount mount;
	private final Map<Long, TNFSDirectory> openDirs = new ConcurrentHashMap<>();
	private final Map<Long, SeekableByteChannel> openFiles = new ConcurrentHashMap<>();
	
	private final static int RENAME_EXCHANGE = 0;
	private final static int RENAME_NOREPLACE = 1;

	public TNFSFUSEFileSystem(TNFSMount mount, Errno errno) {
		this.errno = errno;
		this.mount = mount;
	}

	@Override
	public int create(String path, int mode, FileInfo fi) {
		LOG.trace("create {}", path);
		return ioCall(() -> {
			var fc = mount.open(path, ModeFlag.decode(mode), OpenFlag.decodeOptions(fi.getOpenFlags().toArray(new OpenOption[0])));
			var fh = fileHandleGen.incrementAndGet();
			fi.setFh(fh);
			openFiles.put(fh, fc);
			return 0;
		});
	}

	@Override
	public void destroy() {
		LOG.info("destroy()");
		try {
			mount.close();
		} catch (IOException e) {
			LOG.error("Failed to close cleanly.", e);
		}
	}

	@Override
	public Errno errno() {
		return errno;
	}

	@Override
	public int getattr(String path, Stat stat, FileInfo fi) {
		if(LOG.isDebugEnabled()) {
			LOG.debug("getattr() {}", path);
		}
		return ioCall(() -> {
			setStat(stat, mount.stat(path));
			return 0;
		});
	}

	@Override
	public void init(FuseConnInfo conn, FuseConfig cfg) {
		if(LOG.isDebugEnabled()) {
			LOG.info("init() {}.{}", conn.protoMajor(), conn.protoMinor());
		}
	}

	@Override
	public int mkdir(String path, int mode) {
		if(LOG.isDebugEnabled()) {
			LOG.debug("mkdir {}", path);
		}
		return ioCall(() -> {
			mount.mkdir(path);
			mount.chmod(path, ModeFlag.decode(mode));
			return 0;
		});
	}

	@Override
	public int open(String path, FileInfo fi) {
		LOG.trace("open {}", path);
		return ioCall(() -> {
			var fc = mount.open(path, OpenFlag.decodeOptions(fi.getOpenFlags().toArray(new OpenOption[0])));
			var fh = fileHandleGen.incrementAndGet();
			fi.setFh(fh);
			openFiles.put(fh, fc);
			return 0;
		});
	}

	@Override
	public int opendir(String path, FileInfo fi) {
		if(LOG.isDebugEnabled()) {
			LOG.debug("opendir {}", path);
		}
		return ioCall(() -> {
			var fc = mount.directory(
					0, 
					path, 
					"", 
					new DirOptionFlag[] {
						DirOptionFlag.NO_SKIPHIDDEN, 
						DirOptionFlag.NO_SKIPSPECIAL, 
						DirOptionFlag.NO_FOLDERSFIRST
					},
					new DirSortFlag[] {
						DirSortFlag.NONE
					});
			var fh = dirHandleGen.incrementAndGet();
			fi.setFh(fh);
			openDirs.put(fh, fc);
			return 0;
		});
	}

	@Override
	public int read(String path, ByteBuffer buf, long size, long offset, FileInfo fi) {
		if(LOG.isTraceEnabled()) {
			LOG.trace("read {} at pos {}", path, offset);
		}
		var fc = openFiles.get(fi.getFh());
		if (fc == null) {
			return -errno.ebadf();
		}

		return ioCall(() -> {
			var read = 0;
			var toRead = (int) Math.min(size, buf.limit());
			while (read < toRead) {
				fc.position(offset + read);
				int r = fc.read(buf);
				if (r == -1) {
					LOG.trace("Reached EOF");
					break;
				}
				read += r;
			}
			return read;
		});
	}

	@Override
	public int readdir(String path, DirFiller filler, long offset, FileInfo fi, int flags) {
		if(LOG.isTraceEnabled()) {
			LOG.trace("readdir {} ({} [])", path, fi.getFh(), String.format("%04x", fi.getFh()));
		}
		return ioCall(() -> {
			openDirs.get(fi.getFh()).stream().forEach(entry -> {
				try {
					filler.fill(entry.name(), st -> {;
						try {
							setStat(st, mount.stat(path));
						} catch (IOException e) {
							throw new UncheckedIOException(e);
						}
					});
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
			return 0;
		});
	}

	@Override
	public int release(String path, FileInfo fi) {
		if(LOG.isDebugEnabled()) {
			LOG.debug("release {}", path);
		}
		var fc = openFiles.remove(fi.getFh());
		if (fc == null) {
			return -errno.ebadf();
		}

		return ioCall(() -> {
			fc.close();
			return 0;
		});
	}

	@Override
	public int releasedir(String path, FileInfo fi) {
		if(LOG.isDebugEnabled()) {
			LOG.debug("releasedir {}", path);
		}
		return ioCall(() -> {
			var fh = openDirs.remove(fi.getFh());
			fh.close();
			return 0;
		});
	}

	@Override
	public int rename(String oldpath, String newpath, int flags) {
		if(LOG.isDebugEnabled()) {
			LOG.debug("rename {} {}", oldpath, newpath);
		}
		return ioCall(() -> {
			if((flags & RENAME_NOREPLACE) == 0 && mount.exists(newpath)) {
				mount.unlink(newpath);
			}
			mount.rename(oldpath, newpath);
			return 0;
		});
	}

	@Override
	public int rmdir(String path) {
		if(LOG.isDebugEnabled()) {
			LOG.debug("rmdir {}", path);
		}
		return ioCall(() -> {
			mount.rmdir(path);
			return 0;
		});
	}

	@Override
	public int statfs(String path, Statvfs statvfs) {
		if(LOG.isDebugEnabled()) {
			LOG.debug("statfs() {}", path);
		}
		return ioCall(() -> {
			var free = mount.free();
			var total = mount.size();
			statvfs.setNameMax(255);
			statvfs.setBsize(1024);
			statvfs.setBlocks(total);
			statvfs.setBfree(free);
			statvfs.setBavail(free);
			return 0;
		});
	}

	@Override
	public Set<Operation> supportedOperations() {
		return EnumSet.of(
		/*
		 * For libfuse 3 to ensure that the readdir operation runs in readdirplus mode,
		 * you have to add FuseOperations.Operation.INIT to the set returend by
		 * FuseOperations::supportedOperations method to the supported operations. An
		 * implementation of init is not necessary.
		 */
//			Operation.INIT, 
			Operation.DESTROY, 
			Operation.GET_ATTR, 
			Operation.OPEN, 
			Operation.OPEN_DIR,  
			Operation.RELEASE,
			Operation.MKDIR, 
			Operation.CHMOD,
			Operation.RMDIR, 
			Operation.RENAME,  
			Operation.READ,  
			Operation.WRITE,
			Operation.UNLINK, 
			Operation.RELEASE_DIR,
			Operation.READ_DIR,
			Operation.STATFS,
			Operation.CREATE
		);
	}

	@Override
	public int unlink(String path) {
		if(LOG.isDebugEnabled()) {
			LOG.debug("unlink {}", path);
		}
		return ioCall(() -> {
			mount.unlink(path);
			return 0;
		});
	}
	
	@Override
	public int write(String path, ByteBuffer buf, long count, long offset, FileInfo fi) {
		LOG.trace("write {} at pos {}", path, offset);
		var fc = openFiles.get(fi.getFh());
		if (fc == null) {
			return -errno.ebadf();
		}

		return ioCall(() -> {
			var written = 0;
			var toWrite = (int) Math.min(count, buf.limit());
			while (written < toWrite) {
				fc.position(offset + written);
				written += fc.write(buf);
			}
			return written;
		});
	}

	private int ioCall(Callable<Integer> task) {
		try {
			return task.call();
		} catch (IllegalArgumentException e) {
			LOG.debug("EINVAL. ", e);
			return -errno.einval();
		} catch (UnsupportedOperationException e) {
			if(LOG.isDebugEnabled())
				LOG.error("ENOSYS. ", e);
			else
				LOG.error("ENOSYS. {}", e.getMessage() == null ? "No message supplied." : e.getMessage());
			return -errno.enosys();
		} catch (FileAlreadyExistsException e) {
			LOG.debug("EEXIST. ", e);
			return -errno.eexist();
		} catch (NotDirectoryException e) {
			LOG.debug("ENOTDIR. ", e);
			return -errno.enotdir();
		} catch (FileNotFoundException | NoSuchFileException e) {
			if(LOG.isDebugEnabled()) {
				if(LOG.isTraceEnabled()) {
					LOG.error("ENOENT. ", e);
				}
				else {
					LOG.error("ENOENT. {}", e.getMessage());
				}
			}
			return -errno.enoent();
		} catch (UncheckedIOException | IOException e) {
			LOG.debug("EIO. ", e);
			return -errno.eio();
		} catch (Exception e) {
			return -errno.eio();
		}
	}

	private void setStat(Stat stat, StatResult fstat) {
		stat.setPermissions(Set.of(ModeFlag.permissions(fstat.mode())));
		stat.setGid(fstat.gid());
		stat.setUid(fstat.uid());
		stat.setSize(fstat.size());

		if (fstat.isDirectory()) {
			stat.setModeBits(Stat.S_IFDIR);
			stat.setNLink((short) 2); // quick and dirty implementation. should really be 2 + subdir count
		} else {
			stat.setNLink((short) 1);
			stat.setModeBits(Stat.S_IFREG);
		}
		stat.aTime().set(fstat.atime().toInstant());
		stat.mTime().set(fstat.mtime().toInstant());
		stat.cTime().set(fstat.ctime().toInstant());
	}
}
