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
import uk.co.bithatch.tnfs.lib.Command.Read;
import uk.co.bithatch.tnfs.lib.Command.ReadResult;
import uk.co.bithatch.tnfs.lib.Command.Result;
import uk.co.bithatch.tnfs.lib.Message;
import uk.co.bithatch.tnfs.lib.ResultCode;
import uk.co.bithatch.tnfs.lib.TNFSException;
import uk.co.bithatch.tnfs.server.TNFSMessageHandler;
import uk.co.bithatch.tnfs.server.Tasks;

public class ReadHandler implements TNFSMessageHandler {
	public final static Logger LOG = LoggerFactory.getLogger(ReadHandler.class);


	@Override
	public Result handle(Message message, HandlerContext context) {

		return Tasks.ioCall(() -> {
			Read read = message.payload();

			if(LOG.isDebugEnabled()) {
				LOG.debug("{}. Handle: {} [{}]. Size: {}", 
						Command.READ.name(), 
						read.handle(),
					String.format("%04x", read.handle()),
					read.size()
				);
			}
			
			var dh = context.fileHandles().get(read.handle());
			if(dh == null) {
				throw new TNFSException(ResultCode.BADF);
			}
			else {
				dh.buffer().clear();
				var maxBytes = context.session().server().messageSize() - Message.HEADER_SIZE - 3;
				dh.buffer().limit(Math.min(read.size(), maxBytes));
				var rd = dh.channel().read(dh.buffer());
				if(rd == -1) {
					return new Command.HeaderOnlyResult(ResultCode.EOF);
				}
				else {
					dh.buffer().flip();
					return new ReadResult(ResultCode.SUCCESS, rd, dh.buffer());
				}
			}
		});
	}

	@Override
	public Command<?, ?> command() {
		return Command.READ;
	}
	
}