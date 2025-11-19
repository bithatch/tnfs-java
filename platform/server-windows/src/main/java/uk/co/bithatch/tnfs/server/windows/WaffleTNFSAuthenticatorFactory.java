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
package uk.co.bithatch.tnfs.server.windows;

import java.util.Optional;

import uk.co.bithatch.tnfs.server.AuthenticationType;
import uk.co.bithatch.tnfs.server.TNFSAuthenticator;
import uk.co.bithatch.tnfs.server.TNFSAuthenticatorFactory;
import waffle.windows.auth.impl.WindowsAuthProviderImpl;

public class WaffleTNFSAuthenticatorFactory implements TNFSAuthenticatorFactory {
	
	private final WindowsAuthProviderImpl waffle;

	public WaffleTNFSAuthenticatorFactory() {
		waffle = new WindowsAuthProviderImpl();
	}

	@Override
	public String name() {
		return "Windows System Users";
	}

	@Override
	public Optional<TNFSAuthenticator> createAuthenticator(String path, AuthenticationType... authTypes) {
		return Optional.of(new WaffleTNFSAuthenticator(waffle));
	}

	@Override
	public String id() {
		return "system";
	}

}
