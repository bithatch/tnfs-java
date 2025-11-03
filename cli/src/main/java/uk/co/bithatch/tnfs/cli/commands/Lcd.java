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

import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import uk.co.bithatch.tnfs.cli.TNFSTP.FilenameCompletionMode;

/**
 * Local change directory.
 */
@Command(name = "lcd", aliases = { "lchdir"}, mixinStandardHelpOptions = true, description = "Change local directory.")
public class Lcd extends TNFSTPCommand implements Callable<Integer> {

	@Parameters(index = "0", arity = "0..1", description = "Directory to change to.")
	private Optional<Path> directory;
	
	public Lcd() {
		super(FilenameCompletionMode.DIRECTORIES_LOCAL);
	}

	@Override
	protected Integer onCall() throws Exception {
		var container = getContainer();
		var userhome = Paths.get(System.getProperty("user.home"));
		var resolved = directory.map(d -> {
			if(d.getName(0).getFileName().toString().equals("~")) {
				if(d.getNameCount() == 1) {
					return userhome;
				}
				else {
					return userhome.resolve(d.subpath(1, d.getNameCount()));	
				}
			}
			else {
				return d;
			}
		}).orElse(userhome);
		
		if (!resolved.isAbsolute())	
			resolved = container.getLcwd().resolve(resolved);
		
		if (Files.isDirectory(resolved))
			container.setLcwd(resolved.normalize());
		else
			throw new NotDirectoryException(resolved.toString());
		return 0;
	}
}