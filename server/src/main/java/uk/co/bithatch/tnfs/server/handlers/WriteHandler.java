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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.bithatch.tnfs.lib.Command;
import uk.co.bithatch.tnfs.lib.Command.Result;
import uk.co.bithatch.tnfs.lib.Command.Write;
import uk.co.bithatch.tnfs.lib.Command.WriteResult;
import uk.co.bithatch.tnfs.lib.Message;
import uk.co.bithatch.tnfs.lib.ResultCode;
import uk.co.bithatch.tnfs.lib.TNFSException;
import uk.co.bithatch.tnfs.server.TNFSMessageHandler;
import uk.co.bithatch.tnfs.server.Tasks;

public class WriteHandler implements TNFSMessageHandler {
	public final static Logger LOG = LoggerFactory.getLogger(WriteHandler.class);

	@Override
	public Result handle(Message message, HandlerContext context) {
		return Tasks.ioCall(() -> {
			Write write = message.payload();

			if(LOG.isDebugEnabled()) {
				LOG.debug("{}. Handle: {} [{}]. Bytes: {}", 
					Command.WRITE.name(), 
					write.handle(),
					String.format("%04x", write.handle()),
					write.data().remaining()
				);
			}
			
			var dh = context.fileHandles().get(write.handle());
			if(dh == null) {
				throw new TNFSException(ResultCode.BADF);
			}
			else {
				var wrtn = dh.channel().write(write.data());
				return new WriteResult(ResultCode.SUCCESS, wrtn);
			}
		});
	}

	@Override
	public Command<?, ?> command() {
		return Command.WRITE;
	}
	
}