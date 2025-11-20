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
package uk.co.bithatch.tnfs.daemon;

import java.nio.file.attribute.UserPrincipal;
import java.security.Principal;
import java.util.Optional;

import uk.co.bithatch.tnfs.lib.TNFSFileAccess;
import uk.co.bithatch.tnfs.server.TNFSAuthenticator;
import uk.co.bithatch.tnfs.server.TNFSAuthenticatorFactory;

public class PasswordFileAuthenticator  implements TNFSAuthenticator, TNFSAuthenticatorFactory {

	private PasswordFile passwd;
	
	public PasswordFileAuthenticator(Authentication authConfig) {
		passwd = new PasswordFile(authConfig.passwdFile());
	}

	@Override
	public String name() {
		return "Default";
	}

	@Override
	public Optional<Principal> authenticate(TNFSFileAccess fs, Optional<String> username, Optional<char[]> password) {
		if(username.isPresent()) {
			var uname = username.get();
			if(passwd.verifyUser(uname, password.get())) {
				return Optional.of(new UserPrincipal() {
					
					@Override
					public String getName() {
						return uname;
					}
				});
			}
		}
		return Optional.empty();
	}

	@Override
	public Optional<TNFSAuthenticator> createAuthenticator(String path) {
		return Optional.of(this);
	}

	@Override
	public int priority() {
		return Integer.MIN_VALUE;
	}

	@Override
	public String id() {
		return "password-file";
	}
}
