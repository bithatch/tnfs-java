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
package uk.co.bithatch.tnfs.drive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

import org.cryptomator.jfuse.api.Fuse;
import org.cryptomator.jfuse.api.FuseBuilder;
import org.cryptomator.jfuse.api.FuseMountFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.bithatch.tnfs.fuse.TNFSFUSEFileSystem;
import uk.co.bithatch.tnfs.mountlib.MountManager;
import uk.co.bithatch.tnfs.mountlib.MountManager.MountListener;
import uk.co.bithatch.tnfs.mountlib.MountManager.Mountable;
import uk.co.bithatch.tnfs.mountlib.MountManager.MountableKey;

public class LocalFileSystemManager implements MountListener {
	private final static Logger LOG = LoggerFactory.getLogger(LocalFileSystemManager.class);
	
	private record ActiveMount(TNFSFUSEFileSystem fs, Fuse fuse, Thread shutdownHook, Path mountPoint) {}

	private final FuseBuilder builder;
	private final Configuration configuration;
	private final Map<MountableKey, ActiveMount> mounts = new ConcurrentHashMap<>();
	private final ExecutorService executor;

	public LocalFileSystemManager(ExecutorService executor, MountManager mountManager, Configuration configuration, Optional<Path> libpath) {
		this.configuration = configuration;
		this.executor = executor;
		
		builder = Fuse.builder();
		libpath.ifPresentOrElse(p -> builder.setLibraryPath(p.toString()), () -> {
			if(System.getProperty("os.name", "").toLowerCase().contains("linux")) {
				if(System.getProperty("os.arch", "amd64").equals("amd64")) {
					var path = Paths.get("/lib/x86_64-linux-gnu/libfuse3.so.3");
					if(Files.exists(path)) {
						builder.setLibraryPath(path.toString());
					}
				}	
			}
		});
		
		executor.submit(() -> {
			mountManager.mounts().stream().filter(Mountable::isMounted).forEach(this::mountLocalDrive);
		});
		mountManager.addListener(this);
	}

	public Path mountPount(MountableKey key) {
		var mp = mounts.get(key);
		if(mp == null)
			throw new IllegalArgumentException("No mount with key " + key);
		return mp.mountPoint;
	}

	@Override
	public void mounted(Mountable mountable) {
		executor.submit(() -> mountLocalDrive(mountable));
	}

	@Override
	public void unmounted(Mountable mountable) {
		executor.submit(() -> unmountLocalDrive(mountable));
	}

	private void unmountLocalDrive(Mountable mountable) {
		var activeMount = mounts.remove(mountable.key());
		try {
			LOG.info("Locally unmounting {} from {}...", mountable.key(), activeMount.mountPoint());
			activeMount.fuse().close();
			LOG.info("Locally unmounted {} from {}...", mountable.key(), activeMount.mountPoint());
		} catch (TimeoutException e) {
			LOG.error("Failed to unmount from local drive.", e);
		} finally {
			Runtime.getRuntime().addShutdownHook(activeMount.shutdownHook());
		}
	}
	
	private void mountLocalDrive(Mountable mountable) {
		var mount = mountable.mount();
		var fuseOps = new TNFSFUSEFileSystem(mount, builder.errno());
		var fuse = builder.build(fuseOps);
		var mountsDir = Paths.get(Configuration.decodeMountPath(configuration.document().get(Constants.MOUNT_PATH)));
		var mountPoint = mountsDir.resolve(mountable.name());
				
		LOG.info("Locally Mounting {} at {}...", mountable.key(), mountPoint);
		try {
			Files.createDirectories(mountPoint);

			fuse.mount(mountable.name(), mountPoint, "-s");
			LOG.info("Locally Mounted {} at {}...", mountable.key(), mountPoint);
			var shutdownHook = new Thread(() -> {
				LOG.info("Shutting down.");
				try {
					fuse.close();
				} catch (TimeoutException e) {
				}
			});
			
			Runtime.getRuntime().addShutdownHook(shutdownHook);
			
			mounts.put(mountable.key(), new ActiveMount(fuseOps, fuse, shutdownHook, mountPoint));
		} catch (IllegalArgumentException | FuseMountFailedException | IOException e) {
			try {
				fuse.close();
			} catch (TimeoutException e1) {
			}
			LOG.error("Failed to mount to local drive.", e);
		}
	}
}
