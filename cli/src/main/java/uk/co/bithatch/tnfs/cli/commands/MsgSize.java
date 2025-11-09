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
package uk.co.bithatch.tnfs.cli.commands;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import uk.co.bithatch.tnfs.cli.TNFSTP.FilenameCompletionMode;
import uk.co.bithatch.tnfs.client.extensions.PktSz;

/**
 * Checksum (requires extended server).
 */
@Command(name = "msgsize", aliases = { "message-size","packet-size","pktsz" }, mixinStandardHelpOptions = true, description = "Change the maximum packet size to a number of bytes greater than 15 and less than 65536. Will return actual size agreed by server.")
public class MsgSize extends TNFSTPCommand implements Callable<Integer> {
	
	@Parameters(index = "0", arity = "1", description = "Size in bytes to set as packet maximum.")
	private int size;
	
	public MsgSize() {
		super(FilenameCompletionMode.REMOTE);
	}

	@Override
	protected Integer onCall() throws Exception {
		var container = getContainer();
		System.out.format("%d%n", container.getMount().extension(PktSz.class).packetSize(size).size());
		return 0;
	}
}