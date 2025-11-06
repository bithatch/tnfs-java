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

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import uk.co.bithatch.tnfs.client.TNFSClient;
import uk.co.bithatch.tnfs.client.TNFSMount;
import uk.co.bithatch.tnfs.lib.TNFS;

/**
 * Convenience methods to create new TNFS {@link FileSystem} instances directly,
 * rather through resolution of {@link Path} through that standard libraries.
 * <p>
 * This allows you to easily do things such as re-use existing {@link TNFSMount} instances
 * as more.
 *
 */
public class TNFSFileSystems {
	
	private TNFSFileSystems() {
	}

	/**
	 * Create a new file system given an existing {@link TNFSClient} and using
	 * the default mount as the root of the file system.
	 * 
	 * @param tnfsClient tnfsClient instance
	 * @return file system
	 * @throws IOException if file system cannot be created
	 */
	public static FileSystem newFileSystem(TNFSClient tnfsClient) throws IOException {
		return newFileSystem(tnfsClient.mount().build());
	}

	/**
	 * Create a new file system given an existing {@link TNFSClient} and using
	 * a specific directory as the root 
	 * of the file system.
	 * 
	 * @param tnfsClient tnfsClient instance
	 * @param path path
	 * @return file system
	 * @throws IOException if file system cannot be created
	 */
	public static FileSystem newFileSystem(TNFSClient tnfsClient, Path path) throws IOException {
		return newFileSystem(tnfsClient.mount().build(), path);
	}

	/**
	 * Create a new file system given an existing {@link TNFSMount} and using
	 * the root of the mount as the root 
	 * of the file system.
	 * 
	 * @param tnfs tnfs instance
	 * @return file system
	 * @throws IOException if file system cannot be created
	 */
	public static FileSystem newFileSystem(TNFSMount tnfs) throws IOException {
		return newFileSystem(tnfs, Paths.get(tnfs.mountPath()));
	}

	/**
	 * Create a new file system given an existing {@link TNFSMount} and using
	 * a specified remote directory as the root of the file system. 
	 * 
	 * @param tnfs tnfs instance
	 * @param path path of remote root.
	 * @return file system
	 * @throws IOException if file system cannot be created
	 */
	public static FileSystem newFileSystem(TNFSMount tnfs, Path path) throws IOException {
		return newFileSystem(tnfs, path.toString());
	}

	/**
	 * Create a new file system given an existing {@link TNFSMount} and using
	 * a specified remote directory as the root of the file system. 
	 * 
	 * @param tnfs tnfs instance
	 * @param path path of remote root.
	 * @return file system
	 * @throws IOException if file system cannot be created
	 */
	public static FileSystem newFileSystem(TNFSMount tnfs, String path) throws IOException {
		var uri = URI.create(String.format(
				"tnfs://%s%s%s%s", tnfs.username().map(s -> "@" + s).orElse(""), 
				formatHostnameAndPort(
							tnfs.client().address().getHostName(), 
							tnfs.client().address().getPort()), path.equals("") ? "" : "/", path ));
		
		return new TNFSFileSystemProvider().newFileSystem(uri, Map.of(TNFSFileSystemProvider.TNFS_MOUNT, tnfs));
	}

	/**
	 * Create a new file system given all configuration via the environment {@link Map}.
	 * 
	 * @param environment configuration of file system
	 * @return file system
	 * @throws IOException if file system cannot be created
	 */
	public static FileSystem newFileSystem(Map<String, ?> environment) throws IOException {
		return new TNFSFileSystemProvider().newFileSystem(URI.create("tnfs:////"), environment);
	}
	
	public static String formatHostnameAndPort(String hostname, int port) {
		return port == TNFS.DEFAULT_PORT ? hostname : hostname + ":" + port;
	}
}
