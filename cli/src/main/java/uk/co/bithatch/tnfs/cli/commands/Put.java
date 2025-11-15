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
import static uk.co.bithatch.tnfs.lib.Util.concatenatePaths;

import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import uk.co.bithatch.tnfs.cli.FileTransfer;
import uk.co.bithatch.tnfs.cli.TNFSTP.FilenameCompletionMode;

/**
 * Put file command.
 */
@Command(name = "put", aliases = { "upload"}, mixinStandardHelpOptions = true, description = "Upload local file.")
public class Put extends TNFSTPCommand implements Callable<Integer> {

	@Option(names = { "-r", "--recursive" }, description = "Recursively copy directories.")
	private boolean recursive;

	@Option(names = { "-f", "--force" }, description = "Force overwriting existing local files.")
	private boolean force;

	@Option(names = { "-g", "--no-progress-bar" }, description = "No progress bar.")
	private boolean noProgress = false;

	@Parameters(index = "0", arity = "1..", description = "Files to store.")
	private List<String> files;

	@Option(names = {"-d", "--destination" }, description = "Path to upload file to.")
	private Optional<String> destination;

	public Put() {
		super(FilenameCompletionMode.LOCAL_THEN_REMOTE);
	}

	@Override
	protected Integer onCall() throws Exception {
		
		var container = getContainer();
		var mount = container.getMount();
		
		var remoteDest = destination.map(d ->
			container.localToNativePath(absolutePath(container.getCwd(), d, container.getSeparator()))
		).orElseGet(container::getCwd);
		
		var ftransfer = new FileTransfer(
				mount.client().bufferPool(), 
				force, 
				!noProgress, 
				recursive, 
				container.getSeparator());
		
		expandLocalAndDo(file -> {

			var remoteFile = Files.isDirectory(file) 
					? concatenatePaths(remoteDest, file.getFileName().toString(), '/') 
					: remoteDest;

			ftransfer.localToRemote(mount, file, remoteFile);
			
			System.out.println("Uploaded " + file + " to " + remoteFile);
			
		}, true, files);

		return 0;
	}
}