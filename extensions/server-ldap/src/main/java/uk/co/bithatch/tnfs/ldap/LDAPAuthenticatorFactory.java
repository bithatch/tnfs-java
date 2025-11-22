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
package uk.co.bithatch.tnfs.ldap;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;

import uk.co.bithatch.tnfs.server.TNFSAuthenticator;
import uk.co.bithatch.tnfs.server.TNFSAuthenticatorFactory;

public class LDAPAuthenticatorFactory implements TNFSAuthenticatorFactory {

	private TNFSAuthenticatorContext context;
	private List<LDAPAuthenticatorConfigurer> configurers;
	
	public LDAPAuthenticatorFactory() {

		configurers = ServiceLoader.load(LDAPAuthenticatorConfigurer.class).stream().map(Provider::get).toList();
	}

	@Override
	public String id() {
		return "ldap";
	}

	@Override
	public boolean valid() {
		return configurers.stream().filter(c -> c.valid(context)).findFirst().isPresent();
	}

	@Override
	public void init(TNFSAuthenticatorContext context) {
		this.context = context;
	}

	@Override
	public Optional<TNFSAuthenticator> createAuthenticator(String path) {
		var cfg = new LDAPAuthenticator.Builder();
		return configurers.stream().peek(p -> p.configure(context, cfg)).findFirst().map(bldr -> {
					return cfg.build();
				});
	}

}
