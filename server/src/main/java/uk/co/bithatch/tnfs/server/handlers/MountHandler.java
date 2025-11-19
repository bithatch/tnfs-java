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
package uk.co.bithatch.tnfs.server.handlers;

import static uk.co.bithatch.tnfs.server.Tasks.ioCall;

import java.nio.file.AccessDeniedException;
import java.util.Optional;

import uk.co.bithatch.tnfs.lib.Command;
import uk.co.bithatch.tnfs.lib.Command.Mount;
import uk.co.bithatch.tnfs.lib.Command.MountResult;
import uk.co.bithatch.tnfs.lib.Command.Result;
import uk.co.bithatch.tnfs.lib.Message;
import uk.co.bithatch.tnfs.lib.ResultCode;
import uk.co.bithatch.tnfs.server.TNFSMessageHandler;

public class MountHandler implements TNFSMessageHandler {

	@Override
	public Result handle(Message message, HandlerContext context) {
		Mount mountmsg = message.payload();
		
		var res = ioCall(() -> {
			
			var fact = context.server().fileSystemService();
			var ref = fact.mountDetails(mountmsg.path());
			
			/* Locate user */
			
			var principal = ref.auth().map(auth -> 
				auth.authenticate(ref.fs(), mountmsg.userId(), mountmsg.password())
			).orElse(Optional.empty());
			
			if(ref.auth().isPresent() && !mountmsg.userId().isPresent())
				throw new AccessDeniedException("Authentication required for " + mountmsg.normalizedPath());
			else if(ref.auth().isPresent() && !principal.isPresent())
				throw new AccessDeniedException("Authentication failed for " + mountmsg.normalizedPath());
			
			/* Create mount */
			var userMount = fact.createMount(mountmsg.path(), principal);
			
			/* Create and immediately authenticate the session */
			var session = context.session();
			if(session == null) {
				session = context.newSession(mountmsg.version());
			}
			session.mount(userMount);
			
			return new MountResult(ResultCode.SUCCESS,  context.server().retryTime());
		}
		, code -> new MountResult(code));
		return res;
	}

	@Override
	public Command<?, ?> command() {
		return Command.MOUNT;
	}

	@Override
	public boolean needsSession() {
		return false;
	}

}
