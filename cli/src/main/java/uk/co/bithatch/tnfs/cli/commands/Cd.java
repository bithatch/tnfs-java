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
package uk.co.bithatch.tnfs.cli.commands;

import java.nio.file.NotDirectoryException;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import uk.co.bithatch.tnfs.cli.TNFSTP.FilenameCompletionMode;
import uk.co.bithatch.tnfs.lib.Util;

/**
 * Change directory.
 */
@Command(name = "cd", aliases = { "chdir"}, mixinStandardHelpOptions = true, description = "Change remote directory.")
public class Cd extends TNFSTPCommand implements Callable<Integer> {

    @Parameters(index = "0", arity = "0..1", description = "Directory to change to.")
	private String directory;
    
	public Cd() {
		super(FilenameCompletionMode.DIRECTORIES_REMOTE);
	}

	@Override
	protected Integer onCall() throws Exception {
		var container = getContainer();
		var mount = container.getMount();

		if (directory != null && directory.length() > 0) {
			directory = Util.relativizePath(container.getCwd(), directory);
			var file = mount.stat(directory);
			if (file.isDirectory()) {
				container.setCwd(directory);
			} else {
				throw new NotDirectoryException(directory);
			}
		} else {
			container.setCwd(mount.mountPath());
		}
		return 0;
	}
}