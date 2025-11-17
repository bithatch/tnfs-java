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
package uk.co.bithatch.tnfs.server.extensions;

import static uk.co.bithatch.tnfs.server.Tasks.ioCall;

import java.util.Arrays;

import com.ongres.scram.common.ScramMechanism;

import uk.co.bithatch.tnfs.lib.Command;
import uk.co.bithatch.tnfs.lib.Command.Result;
import uk.co.bithatch.tnfs.lib.Message;
import uk.co.bithatch.tnfs.lib.ResultCode;
import uk.co.bithatch.tnfs.lib.extensions.Extensions;
import uk.co.bithatch.tnfs.lib.extensions.Extensions.ServerCapsResult;
import uk.co.bithatch.tnfs.lib.extensions.Extensions.ServerFirst;
import uk.co.bithatch.tnfs.server.TNFSMessageHandler;

public class ServerCapsHandler implements TNFSMessageHandler {

	public final static ScramMechanism[] MECHS = new ScramMechanism[] { ScramMechanism.SCRAM_SHA_1};
//	public final static ScramMechanism[] MECHS = Arrays.asList(ScramMechanism.values()).stream().filter(f->!f.isPlus()).toList().toArray(new ScramMechanism[0]);
//	public final static ScramMechanism[] MECHS = ScramMechanism.values();
	
	@Override
	public Result handle(Message message, HandlerContext context) {
		var res = ioCall(() -> {
			return new ServerCapsResult(
					ResultCode.SUCCESS,
					context.server().serverKey(),
					Arrays.asList(MECHS).stream().map(ScramMechanism::name).toList().toArray(new String[0])
					);
		}
		, code -> new ServerFirst(code));
		return res;
	}

	@Override
	public Command<?, ?> command() {
		return Extensions.SRVRCAPS;
	}

	@Override
	public boolean needsSession() {
		return false;
	}
}
