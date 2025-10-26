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

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import uk.co.bithatch.tnfs.cli.TNFSTP.FilenameCompletionMode;
import uk.co.bithatch.tnfs.cli.TNFSURI;

/**
 * Change directory.
 */
@Command(name = "mount", aliases = { "mnt", "connect", "open" }, mixinStandardHelpOptions = true, description = "Mount another path or server.")
public class Mount extends TNFSTPCommand implements Callable<Integer> {
	
	@Option(names = {"-f", "--unmount-first"}, description = "Usually, the current mount will only be unmounted if the new mount is successful. To forcibly unmount first, use this option (e.g. if a server only allows one active mount per user). Doesn't apply if the new mount is on a different server.")
	private boolean unmountFirst;

    @Parameters(index = "0", arity = "0..1", paramLabel = "TNFS_URI_OR_PATH", description = "TNFS uri to connect to, or just a path to connect to a different mount on the same server.")
	private String destination;
    
	public Mount() {
		super(FilenameCompletionMode.DIRECTORIES_REMOTE);
	}

	@Override
	protected Integer onCall() throws Exception {
		var container = getContainer();
		var oldUri = container.getURI();
		var newUri = TNFSURI.parse(oldUri, destination);
		
		if(!newUri.isSameServer(oldUri)) {
			try {
				container.getClient().close();
			}
			catch(Exception e) {
				var cmdline = getContainer().getSpec().commandLine(); 
				cmdline.getExecutionExceptionHandler().handleExecutionException(e, cmdline, null);
			}

			container.connect(newUri);
		}

		if(unmountFirst) {
			try {
				getContainer().getMount().close();
			}
			catch(Exception e) {
				var cmdline = getContainer().getSpec().commandLine(); 
				cmdline.getExecutionExceptionHandler().handleExecutionException(e, cmdline, null);
			}
		}
		
		container.mount(newUri);
		
		return 0;
	}
}