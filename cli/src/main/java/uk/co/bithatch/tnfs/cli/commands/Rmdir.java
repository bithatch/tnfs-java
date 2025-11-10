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
import picocli.CommandLine.Parameters;
import uk.co.bithatch.tnfs.cli.TNFSTP.FilenameCompletionMode;

/**
 * Remove directory command.
 */
@Command(name = "rmdir", aliases = { "rd"}, mixinStandardHelpOptions = true, description = "Remove directory.")
public class Rmdir extends TNFSTPCommand implements Callable<Integer> {

	@Parameters(index = "0", arity = "1", description = "Directory to remove.")
	private String directory;
	
	public Rmdir() {
		super(FilenameCompletionMode.DIRECTORIES_REMOTE);
	}

	@Override
	protected Integer onCall() throws Exception {
		var container = getContainer();
		var sftp = container.getMount();
		directory = relativizePath(container.getCwd(), directory, container.getSeparator());
		sftp.rmdir(container.localToNativePath(directory));
		return 0;
	}
}