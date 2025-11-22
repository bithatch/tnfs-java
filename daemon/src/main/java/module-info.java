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

import uk.co.bithatch.tnfs.daemon.LDAPConfigurer;
import uk.co.bithatch.tnfs.ldap.LDAPAuthenticatorConfigurer;
import uk.co.bithatch.tnfs.server.TNFSAuthenticatorFactory;

open module uk.co.bithatch.tnfs.daemon {
	requires info.picocli;
	requires transitive uk.co.bithatch.tnfs.server;
	requires transitive uk.co.bithatch.tnfs.server.extensions;
	requires transitive uk.co.bithatch.tnfs.ldap;
	requires transitive uk.co.bithatch.tnfs.daemonlib;
	requires org.slf4j.simple;
	requires org.slf4j;
	requires com.sshtools.porter;
	requires com.sshtools.jini.config;
	
	uses TNFSAuthenticatorFactory;
	provides LDAPAuthenticatorConfigurer with LDAPConfigurer;
}