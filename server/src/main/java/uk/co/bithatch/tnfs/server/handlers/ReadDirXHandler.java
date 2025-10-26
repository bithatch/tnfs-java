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
package uk.co.bithatch.tnfs.server.handlers;

import java.io.EOFException;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.bithatch.tnfs.lib.Command;
import uk.co.bithatch.tnfs.lib.Command.Entry;
import uk.co.bithatch.tnfs.lib.Command.ReadDirX;
import uk.co.bithatch.tnfs.lib.Command.Result;
import uk.co.bithatch.tnfs.lib.DirStatusFlag;
import uk.co.bithatch.tnfs.lib.Message;
import uk.co.bithatch.tnfs.lib.ResultCode;
import uk.co.bithatch.tnfs.lib.TNFSException;
import uk.co.bithatch.tnfs.server.DirXHandle;
import uk.co.bithatch.tnfs.server.TNFSMessageHandler;
import uk.co.bithatch.tnfs.server.Tasks;

public class ReadDirXHandler implements TNFSMessageHandler {
	public final static Logger LOG = LoggerFactory.getLogger(ReadDirXHandler.class);

	@Override
	public Result handle(Message message, HandlerContext context) {

		return Tasks.ioCall(() -> {
			ReadDirX dirx = message.payload();

			if(LOG.isDebugEnabled()) {
				LOG.debug("{}. Path: {} [{}]. Entries: {}", 
					Command.READDIRX.name(), 
					dirx.handle(),
					String.format("%04x", dirx.handle()),
					dirx.entries()
				);
			}
			
			var dh = (DirXHandle)context.dirHandles().get(dirx.handle());
			if(dh == null) {
				throw new TNFSException(ResultCode.BADF);
			}
			else {
				if(dh.hasNext()) {
					var l = new ArrayList<Entry>();
					var sz = context.session().server().messageSize() - 5 - Message.HEADER_SIZE;

					while(dh.hasNext() && (dirx.entries() == 0 || l.size() < dirx.entries())) {
						var entry = dh.next();
						sz -= entry.encodedSize();
						if(sz < 0) {
							dh.setNext(entry);
							break;
						}
						else
							l.add(entry);
					}

					return new Command.ReadDirXResult(
							ResultCode.SUCCESS, 
						l.size(), 
						dh.hasNext() ? 0 : DirStatusFlag.DIREOF.value(), 
						0 /*TODO*/, 
						l.toArray(new Entry[0])
					);
				}
				else
					throw new EOFException();
			}
		});
	}

	@Override
	public Command<?, ?> command() {
		return Command.READDIRX;
	}
	
}