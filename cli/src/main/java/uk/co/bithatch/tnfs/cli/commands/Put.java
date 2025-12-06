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

import java.util.Optional;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import uk.co.bithatch.tnfs.cli.FileTransfer;
import uk.co.bithatch.tnfs.cli.TNFSTP.FilenameCompletionMode;
import uk.co.bithatch.tnfs.lib.Util;

/**
 * Put file command.
 */
@Command(name = "put", aliases = { "upload"}, mixinStandardHelpOptions = true, description = "Upload local file.")
public class Put extends TNFSTPCommand implements Callable<Integer> {

	@Option(names = { "-f", "--force" }, description = "Force overwriting existing local files.")
	private boolean force;

	@Option(names = { "-g", "--no-progress-bar" }, description = "No progress bar.")
	private boolean noProgress = false;

	@Parameters(index = "0", arity = "1", description = "File to upload.")
	private String path;

	@Parameters(index = "1", arity = "0..1", description = "Optional destination filename.")
	private Optional<String> destination;

	public Put() {
		super(FilenameCompletionMode.LOCAL_THEN_REMOTE);
	}

	@Override
	protected Integer onCall() throws Exception {
		
		var container = getContainer();
		var mount = container.getMount();
		
		var ftransfer = new FileTransfer(
				mount.client().bufferPool(), 
				force, 
				!noProgress, 
				false, 
				container.getSeparator(),
				container.getTerminal());
		
		var local = expandLocal(path);
		var resolved = local.isAbsolute() 
				? local 
				: container.getLcwd().resolve(local);
		
		var remoteFile = destination.map(this::expandRemote).orElseGet(() -> Util.concatenatePaths(container.getCwd(), resolved.getFileName().toString(), container.getSeparator()));

		ftransfer.localToRemote(mount, resolved, container.localToNativePath(remoteFile));
		
		System.out.println("Uploaded " + resolved + " to " + remoteFile);
		
		return 0;
	}
}