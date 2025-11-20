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
package uk.co.bithatch.tnfs.it;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.Arrays;
import java.util.Optional;

import uk.co.bithatch.tnfs.lib.Io;
import uk.co.bithatch.tnfs.lib.Protocol;
import uk.co.bithatch.tnfs.lib.TNFSFileAccess;
import uk.co.bithatch.tnfs.server.DefaultInMemoryFileSystemService;
import uk.co.bithatch.tnfs.server.TNFSAccessCheck;
import uk.co.bithatch.tnfs.server.TNFSAuthenticator;
import uk.co.bithatch.tnfs.server.TNFSMounts;
import uk.co.bithatch.tnfs.server.TNFSServer;

/**
 * A TNFSJ implementation of a {@link AbstractTestTNFSServerBuilder}
 */
public class TNFSJServerBuilder extends AbstractTestTNFSServerBuilder {
	private final TNFSServer.Builder builder = new TNFSServer.Builder();
	private Path tempMountDir;
	
	{
		builder.withRandomPort();
		builder.withFileSystemFactory(new DefaultInMemoryFileSystemService());
	}

	private class TNFSJServer implements ITNFSServer {
		
		private final TNFSServer<?> tnfsjServer;
		private final Path tempMountDir;
		

		TNFSJServer(Path tempMountDir, TNFSServer<?> tnfsjServer) {
			this.tnfsjServer = tnfsjServer;
			this.tempMountDir = tempMountDir;
		}

		@Override
		public void close() {
			try {
				tnfsjServer.close();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			} finally {
				if(tempMountDir != null) {
					Io.deleteDir(tempMountDir);
				}
			}
		}

		@Override
		public void run() {
			tnfsjServer.run();			
		}

		@Override
		public int port() {
			return tnfsjServer.port();
		}

	}
	
	public TNFSJServerBuilder withHost(InetAddress address) {
		builder.withHost(address);
		return this;
	}
	
	public TNFSJServerBuilder withProtocol(Protocol protocol) {
		builder.withProtocol(protocol);
		return this;
	}
	
	public TNFSJServerBuilder withSize(int size) {
		builder.withSize(size);
		return this;
	}
	
	public TNFSJServerBuilder withAuthenticatedFileMounts(String username, char[] password) {
		var mounts = new TNFSMounts();
		try {
			tempMountDir = Files.createTempDirectory("tnfs");
			mounts.mount("/", tempMountDir, new TNFSAuthenticator() {
				@Override
				public Optional<Principal> authenticate(TNFSFileAccess fs, Optional<String> ausername, Optional<char[]> apassword) {
					return ausername.isPresent() && ausername.get().equals(username) &&
							apassword.isPresent() && Arrays.equals(apassword.get(), password) ?
						Optional.of(new Principal() {
							@Override
							public String getName() {
								return username;
							}
						}) : Optional.empty();
				}
			}, TNFSAccessCheck.AUTHENTICATED_READ_WRITE);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		builder.withFileSystemFactory(mounts);
		return this;
	}
	
	public TNFSJServerBuilder withFileMounts() {
		var mounts = new TNFSMounts();
		try {
			tempMountDir = Files.createTempDirectory("tnfs");
			mounts.mount("/", tempMountDir);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		builder.withFileSystemFactory(mounts);
		return this;
	}
	
	public TNFSJServerBuilder withClientSize(int csize) {
		builder.withSessionDecorator(s -> {
			s.size(csize);
		});
		return this;
	}
	
	public ITNFSServer build() {
		try {
			return new TNFSJServer(tempMountDir, builder.build());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
