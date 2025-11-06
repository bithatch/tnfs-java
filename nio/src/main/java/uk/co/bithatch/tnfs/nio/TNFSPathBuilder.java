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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import uk.co.bithatch.tnfs.lib.TNFS;
import uk.co.bithatch.tnfs.lib.Util;

public  final class TNFSPathBuilder {

	private Optional<String> username = Optional.empty();
	private Optional<char[]> password = Optional.empty();
	private Optional<String> host = Optional.empty();
	private Optional<Integer> port = Optional.empty();
	private Optional<String> path = Optional.empty();
	
	public static TNFSPathBuilder create() {
		return new TNFSPathBuilder();
	}
	
	private TNFSPathBuilder() {
	}

	public final TNFSPathBuilder withUsername(String username) {
		return withUsername(Util.emptyOptionalIfBlank(username));
	}

	public final TNFSPathBuilder withUsername(Optional<String> username) {
		this.username = username;
		return this;
	}

	public final TNFSPathBuilder withPassword(String password) {
		return withPasswordCharacters(password.toCharArray());
	}

	public final TNFSPathBuilder withPassword(Optional<String> password) {
		return withPasswordCharacters(password.map(p -> p.toCharArray()));
	}

	public final TNFSPathBuilder withPasswordCharacters(char[] password) {
		return withPasswordCharacters(Util.emptyOptionalIfBlank(password));
	}

	public final TNFSPathBuilder withPasswordCharacters(Optional<char[]> password) {
		this.password = password;
		return this;
	}

	public final TNFSPathBuilder withPath(String path) {
		this.path = Optional.ofNullable(path);
		return this;
	}

	public final TNFSPathBuilder withHost(String host) {
		this.host = Optional.of(host);
		return this;
	}

	public final TNFSPathBuilder withPort(int port) {
		this.port = Optional.of(port);
		return this;
	}

	public URI build() {
		var uriStr = new StringBuilder("tnfs://");
		username.ifPresent(un -> {
			uriStr.append(encodeUserInfo( username.orElse("guest")));
			password.ifPresent(p -> {
				uriStr.append(":");
				uriStr.append(encodeUserInfo( new String(p)));
			});
			uriStr.append("@");
		});
		uriStr.append(host.orElse("localhost"));
		port.ifPresent(p -> {
			if(p != 22) {
				uriStr.append(":");
				uriStr.append(p);
			}
		});
		uriStr.append("/" + path.orElse(""));
		return URI.create(uriStr.toString());

	}

	public static String encodeUserInfo(String userinfo) {
		try {
			return new URI("tnfs", userinfo, "localhost", TNFS.DEFAULT_PORT, null, null, null).getRawUserInfo();
		} catch (URISyntaxException e) {
			/* NOTE: Will NEVER be thrown, and can't be tested for coverage :( */
			throw new IllegalArgumentException(e);
		}
	}
}
