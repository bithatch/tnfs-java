/*
 * Copyright © 2025 Bithatch (bithatch@bithatch.co.uk)
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
package uk.co.bithatch.tnfs.server.extensions;

import static uk.co.bithatch.tnfs.server.Tasks.ioCall;

import java.util.Optional;

import com.ongres.scram.common.ClientFirstMessage;
import com.ongres.scram.common.ScramFunctions;
import com.ongres.scram.common.ServerFirstMessage;

import uk.co.bithatch.tnfs.lib.Command;
import uk.co.bithatch.tnfs.lib.Command.Result;
import uk.co.bithatch.tnfs.lib.Message;
import uk.co.bithatch.tnfs.lib.ResultCode;
import uk.co.bithatch.tnfs.lib.TNFSException;
import uk.co.bithatch.tnfs.lib.extensions.Crypto;
import uk.co.bithatch.tnfs.lib.extensions.Extensions;
import uk.co.bithatch.tnfs.lib.extensions.Extensions.ClientFirst;
import uk.co.bithatch.tnfs.lib.extensions.Extensions.ServerFirst;
import uk.co.bithatch.tnfs.server.TNFSMessageHandler;

public class ScramClientFirstHandler implements TNFSMessageHandler {
	
	static final String SERVER_FIRST = "serverFirst";
	static final String CLIENT_FIRST = "clientFirst";
	static final String PRINCIPAL = "principal";
	static final String CLIENT_NONCE = "clientNonce";

	@Override
	public Result handle(Message message, HandlerContext context) {
		ClientFirst mountmsg = message.payload();
		
		var res = ioCall(() -> {
			
			var fact = context.server().fileSystemService();
			var ref = fact.mountDetails(mountmsg.path());

			/* Parse clients SCRAM message */
			var cfirst = ClientFirstMessage.parseFrom(mountmsg.clientFirstMessage());
			
			/* Locate user */
			var principal = ref.auth().map(auth -> {
				if(auth instanceof ScramTNFSAuthenticator rfc5802auth) {
					return rfc5802auth.identify(ref.fs(), cfirst.getUsername());
				}
				else {
					throw new UnsupportedOperationException("CLNTFRST must be used with " + ScramTNFSAuthenticator.class.getSimpleName());
				}
			}
			).orElse(Optional.empty());
			
			/* TODO should we fake a user if it doesn't exist?
			 */
			
			if(!principal.isPresent())
				throw new TNFSException(ResultCode.PERM, "Authentication failed for " + mountmsg.normalizedPath());
			
			var usr = principal.get();
			var serverNonce = ScramFunctions.nonce(Crypto.NONCE_SIZE, Crypto.random());
			
			/* Create the mount to get the new session, the session
			 * will not actually be usable until authentication complete
			 */
			var userMount = fact.createMount(mountmsg.normalizedPath(), principal);
			var session = context.newSession(userMount, mountmsg.version());
			
			/* Store some stuff in the session for use by the remainder of the
			 * authentication handshake
			 */
			var state = session.state();			
			state.put(CLIENT_FIRST, cfirst);
			state.put(PRINCIPAL, usr);
			state.put(CLIENT_NONCE, cfirst.getClientNonce());
			
			/* Build reply */
			var sfirst = new ServerFirstMessage(
				cfirst.getClientNonce(),
				serverNonce,
				usr.getSalt(),
				usr.getIterationCount()
			);			
			state.put(SERVER_FIRST, sfirst);
			
			return new  ServerFirst(
					ResultCode.SUCCESS,
					sfirst.toString()
					);
		}
		, code -> new ServerFirst(code));
		return res;
	}

	@Override
	public Command<?, ?> command() {
		return Extensions.CLNTFRST;
	}

	@Override
	public boolean needsSession() {
		return false;
	}

}
