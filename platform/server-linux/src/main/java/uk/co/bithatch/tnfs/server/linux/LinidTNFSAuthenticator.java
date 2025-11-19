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
package uk.co.bithatch.tnfs.server.linux;

import java.security.Principal;
import java.util.Optional;

import uk.co.bithatch.linid.Id;
import uk.co.bithatch.linid.IdDb;
import uk.co.bithatch.linid.Linid;
import uk.co.bithatch.linid.PasswordChangeRequiredException;
import uk.co.bithatch.tnfs.lib.TNFSFileAccess;
import uk.co.bithatch.tnfs.server.TNFSAuthenticator;

public class LinidTNFSAuthenticator implements TNFSAuthenticator {
	
	private final IdDb database = Linid.get();

	@Override
	public Optional<Principal> authenticate(TNFSFileAccess fs, Optional<String> username, Optional<char[]> password) {
		if(username.isPresent()) {
			var uname = username.get();
			var princ = locateUser(uname);
			if(princ.isPresent() && password.isPresent()) {
				var usr = princ.get();
				try {
					usr.verify(password.get());
					return Optional.of(usr);
				}
				catch(PasswordChangeRequiredException pcre) {
					return Optional.of(usr);
				}
				catch(Exception zpe) {
					// Failure
				}
			}
		}
		return Optional.empty();
	}

	private Optional<Id> locateUser(String uname) {
		return database.idByName(uname);
	}

}
