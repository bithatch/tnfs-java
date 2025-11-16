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

import java.net.InetAddress;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import uk.co.bithatch.tnfs.lib.Protocol;
import uk.co.bithatch.tnfs.lib.ResultCode;
import uk.co.bithatch.tnfs.lib.TNFSException;

public class AuthenticatedUDPIntegrationTests extends UDPIntegrationTests {
	
	private static String username;
	private static char[] password;

	@BeforeAll
	public static void setupUserAndPassword() {
		username = UUID.randomUUID().toString();
		password = UUID.randomUUID().toString().toCharArray();
	}
	
	@Test
	public void testIncorrectUsername() throws Exception {
		runTest((clnt, svr) -> {
			var exception = Assertions.assertThrows(TNFSException.class, () -> {
				clnt.mount("/").
				withUsername(username + "XXXXXXXX").
				withPassword(password).build();				
			});
			Assertions.assertEquals(ResultCode.PERM, exception.code());
		});

	}
	
	@Test
	public void testIncorrectPassword() throws Exception {
		runTest((clnt, svr) -> {
			var exception = Assertions.assertThrows(TNFSException.class, () -> {
				clnt.mount("/").
				withUsername(username).
				withPassword((new String(password) + "XXXXXXXXX").toCharArray()).build();				
			});
			Assertions.assertEquals(ResultCode.PERM, exception.code());
		});

	}

	@Override
	protected void runMountTest(TestMountTask task) throws Exception {
		runTest((clnt, svr) -> {

			var mntBldr = clnt.mount("/").
					withUsername(username).
					withPassword(password);	
			
			try(var mnt = mntBldr.build()) {
				task.run(mnt, clnt, svr);
			}
		});
	}

	@Override
	protected TNFSJServerBuilder createServerBuilder() {
		return new TNFSJServerBuilder().
				withAuthenticatedFileMounts(username, password).
				withProtocol(Protocol.UDP).
				withHost(InetAddress.getLoopbackAddress());
	}

}
