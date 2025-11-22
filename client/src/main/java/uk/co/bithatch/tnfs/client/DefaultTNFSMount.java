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
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.Set;

import uk.co.bithatch.tnfs.lib.Command;
import uk.co.bithatch.tnfs.lib.Command.MountResult;
import uk.co.bithatch.tnfs.lib.Message;
import uk.co.bithatch.tnfs.lib.Version;

public final class DefaultTNFSMount extends AbstractTNFSMount {

	public static class Builder extends AbstractBuilder<Builder> {
		

		/**
		 * Construct a new mount builder.
		 *
		 * @param path path
		 * @param client client
		 * @return this for chaining
		 */
		Builder(String path, TNFSClient client) {
			super(path, client);
		}

		/**
		 * Create the client from this builders configuration.
		 *
		 * @return client
		 */
		public TNFSMount build() throws IOException {
			return new DefaultTNFSMount(this);
		}
	}

	private final int sessionId;
	private final Version serverVersion;
	private final boolean authenticated;

	private DefaultTNFSMount(Builder bldr) throws IOException {
		super(bldr); 
		try {
			authenticated = username.isPresent();
			var rep = client.send(null, Command.MOUNT, Message.of(Command.MOUNT, new Command.Mount(bldr.path, username, password)));
			MountResult res = rep.result();
			sessionId = rep.message().connectionId();
			serverVersion = res.version();
		}
		catch(SocketTimeoutException ste) {
			throw new IOException("No TNFS service responded to request.");
		}
	}
	
	@Override
	public Set<Flag> flags() {
		if(authenticated) {
			return Set.of(Flag.AUTHENTICATED);
		}
		else {
			return Collections.emptySet();
		}
	}

	@Override
	public int sessionId() {
		return sessionId;
	}

	@Override
	public Version serverVersion() {
		return serverVersion;
	}

}
