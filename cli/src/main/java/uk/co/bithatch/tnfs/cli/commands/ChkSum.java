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

import static uk.co.bithatch.tnfs.lib.Util.relativizePath;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import uk.co.bithatch.tnfs.cli.TNFSTP.FilenameCompletionMode;
import uk.co.bithatch.tnfs.client.extensions.Sum;
import uk.co.bithatch.tnfs.lib.extensions.Extensions.Checksum;

/**
 * Checksum (requires extended server).
 */
@Command(name = "sum", aliases = { "checksum", "chk", "chksum" }, mixinStandardHelpOptions = true, description = "Show checksum for file.")
public class ChkSum extends TNFSTPCommand implements Callable<Integer> {
	
	@Option(names = "-t", description = "Checksum type. Defaults to CRC32")
	private Checksum type = Checksum.CRC32;

	@Parameters(index = "0", arity = "1", description = "File to sum.")
	private String file;
	
	public ChkSum() {
		super(FilenameCompletionMode.REMOTE);
	}

	@Override
	protected Integer onCall() throws Exception {
		var container = getContainer();
		var sum = container.getMount().extension(Sum.class);
		file = relativizePath(container.getCwd(), file, container.getSeparator());
		System.out.format("%s %s%n", sum.sum(type, container.localToNativePath(file)), file);
		return 0;
	}
}