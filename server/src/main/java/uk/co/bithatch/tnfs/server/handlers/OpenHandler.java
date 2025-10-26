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

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.bithatch.tnfs.lib.Command;
import uk.co.bithatch.tnfs.lib.Command.Open;
import uk.co.bithatch.tnfs.lib.Command.Result;
import uk.co.bithatch.tnfs.lib.Message;
import uk.co.bithatch.tnfs.lib.ResultCode;
import uk.co.bithatch.tnfs.server.FileHandle;
import uk.co.bithatch.tnfs.server.TNFSMessageHandler;
import uk.co.bithatch.tnfs.server.Tasks;

public class OpenHandler implements TNFSMessageHandler {
	public final static Logger LOG = LoggerFactory.getLogger(OpenHandler.class);

	@Override
	public Result handle(Message message, HandlerContext context) {

		return Tasks.ioCall(() -> {
			
			Open open = message.payload();
			
			if(LOG.isDebugEnabled()) {
				LOG.debug("{}. Path: {} Mode: {} Flags: {}",
						Command.OPEN.name(),
						open.path(), 
						Arrays.asList(open.mode()), Arrays.asList(open.flags()));
			}
			
			var fh = context.session().mount().open(
					open.path(),
					open.mode(),
					open.flags()
					);
			
			int key;
			synchronized(context.fileHandles()) {
				key = context.nextFileHandle();
				context.fileHandles().put(key, new FileHandle(fh, ByteBuffer.allocate(context.session().server().messageSize())));
			}
			
			LOG.info("Opened file {}, handle {}", open.path(), key);
			return new Command.HandleResult(ResultCode.SUCCESS, key);
		});
	}

	@Override
	public Command<?, ?> command() {
		return Command.OPEN;
	}
	
}