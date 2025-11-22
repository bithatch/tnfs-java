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

import uk.co.bithatch.tnfs.ldap.LDAPAuthenticator.Builder;
import uk.co.bithatch.tnfs.ldap.LDAPAuthenticatorConfigurer;
import uk.co.bithatch.tnfs.server.TNFSAuthenticatorFactory.TNFSAuthenticatorContext;

public class LDAPConfigurer implements LDAPAuthenticatorConfigurer {

	@Override
	public void configure(TNFSAuthenticatorContext context, Builder builder) {
		var authConfig = ((MountConfiguration)context).authConfig();
		var ldap = authConfig.ldap();
		
		builder.withHost(ldap.get(Constants.HOSTNAME_KEY));
		builder.withSsl(ldap.getBoolean(Constants.SSL_KEY));
		var ignoreSslErrors = ldap.getBoolean(Constants.IGNORE_SSL_ERRORS_KEY);
		System.setProperty("com.sun.jndi.ldap.object.disableEndpointIdentification", String.valueOf(ignoreSslErrors));
		builder.withIgnoreSslErrors(ignoreSslErrors);
		builder.withBaseDn(ldap.get(Constants.BASE_DN_KEY));
		builder.withUserSearchFilter(ldap.get(Constants.USER_SEARCH_FILTER_KEY));
		ldap.getOr(Constants.BIND_DN_KEY).ifPresent(builder::withBindDn);
		ldap.getOr(Constants.BIND_DN_PASSWORD_KEY).ifPresent(builder::withBindPassword);
		ldap.getIntOr(Constants.PORT_KEY).ifPresent(builder::withPort);
	}

	@Override
	public boolean valid(TNFSAuthenticatorContext context) {
		var authConfig = ((MountConfiguration)context).authConfig();
		return authConfig.ldap().getBooleanOr(Constants.HOSTNAME_KEY).isPresent();
	}

}
