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

import static uk.co.bithatch.tnfs.lib.Util.absolutePath;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import uk.co.bithatch.tnfs.cli.TNFSTP.FilenameCompletionMode;

/**
 * Stat file.
 */
@Command(name = "stat", aliases = { "st"}, mixinStandardHelpOptions = true, description = "Stat file.")
public class Stat extends TNFSTPCommand implements Callable<Integer> {

    @Parameters(index = "0", arity = "1..", description = "File to stat.")
	private List<String> files;
    
	public Stat() {
		super(FilenameCompletionMode.REMOTE);
	}

	@Override
	protected Integer onCall() throws Exception {
		var container = getContainer();
		var mount = container.getMount();

		expandRemoteAndDo(file -> {
			
			file = absolutePath(container.getCwd(), file, container.getSeparator());
			
			var stat = mount.stat(container.localToNativePath(file));
			var wtr = getContainer().getTerminal().writer();
			
			wtr.println(String.format("%s %7d %7d %10d %10d %10d \"%s\" \"%s\"", stat.isDirectory() ? "d" : "-", stat.uid(), stat.gid(), stat.atime().to(TimeUnit.SECONDS), stat.ctime().to(TimeUnit.SECONDS), stat.mtime().to(TimeUnit.SECONDS), stat.uidString(), stat.gidString()));
			
		}, true, files);
		return 0;
	}
}