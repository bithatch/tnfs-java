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

import static java.nio.file.Files.isDirectory;
import static uk.co.bithatch.tnfs.lib.Util.absolutePath;
import static uk.co.bithatch.tnfs.lib.Util.basename;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import uk.co.bithatch.tnfs.cli.FileTransfer;
import uk.co.bithatch.tnfs.cli.TNFSTP.FilenameCompletionMode;

/**
 * Get command.
 */
@Command(name = "get", aliases = { "download" }, mixinStandardHelpOptions = true, description = "Download remote file.")
public class Get extends TNFSTPCommand implements Callable<Integer> {

	@Option(names = { "-r", "--recursive" }, description = "Recursively copy directories.")
	private boolean recursive;

	@Option(names = { "-f", "--force" }, description = "Force overwriting existing local files.")
	private boolean force;

	@Option(names = { "-g", "--no-progress-bar" }, description = "No progress bar.")
	private boolean noProgress = false;

	@Parameters(index = "0", arity = "1..", description = "Remote files or directories to retrieve.")
	private List<String> files;

	@Option(names = {"-d", "--destination" }, description = "Path to download file to.")
	private Optional<Path> destination;

	public Get() {
		super(FilenameCompletionMode.REMOTE);
	}

	@Override
	protected Integer onCall() throws Exception {
		var container = getContainer();
		var mount = container.getMount();
		var localDest = destination.map(d -> container.getLcwd().resolve(d)).orElseGet(container::getLcwd);
		var ftransfer = new FileTransfer(
				mount.client().bufferPool(), 
				force, 
				!noProgress, 
				recursive, 
				container.getSeparator());
		
		expandRemoteAndDo(file -> {
			file = absolutePath(container.getCwd(), file, container.getSeparator());

			var localFile = isDirectory(localDest) 
					? localDest.resolve(basename(file)) 
					: localDest;

			ftransfer.remoteToLocal(mount, container.localToNativePath(file), localFile);
			
			System.out.println("Downloaded " + file + " to " + localFile);
			
		}, true, files);
		

		return 0;
	}
}