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
package uk.co.bithatch.tnfs.cli;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * SCP-like command for TNFS
 */
@Command(name = "tnfscp", mixinStandardHelpOptions = true, description = "Trivial Network File System Copy.")
public class TNFSCP extends AbstractTNFSFilesCommand implements Callable<Integer> {
	
	/**
	 * Entry point.
	 *
	 * @param args command line arguments
	 * @throws Exception on error
	 */
	public static void main(String[] args) throws Exception {
		TNFSCP cli = new TNFSCP();
        System.exit(new CommandLine(cli).setExecutionExceptionHandler(new ExceptionHandler(cli)).execute(args));
	}
	
	@Option(names = { "-p",
			"--preserve-attributes" }, description = "Preserves modification times, access times, and modes from the original file..")
	private boolean preserveAttributes;
	
	@Option(names = {"-r", "--recursive"}, description = "Recursively copy directories.")
	private boolean recursive;
	
	@Option(names = {"-f", "--force"}, description = "Force overwriting existing local files.")
	private boolean force;
	
	@Option(names = {"-g", "--no-progress-bar"}, description = "No progress bar.")
	private boolean noProgress = false;
	
	@Parameters(index = "0", arity = "1..", description = "The source file(s) or directory, either a local path or a TFNS Path URI.")
	private List<String> sources;

	@Parameters(index = "1", description = "The target file or directory, either a local path or a TFNS Path URI.")
	private String target;
	
	@Spec 
	private CommandSpec spec;

	@Override
	public Integer call() throws Exception {
		start();
		return 0;
	}

	@Override
	protected CommandSpec getSpec() {
		return spec;
	}

	@Override
	protected void onStart() throws IOException {
		if(sources.size() == 1) {
			oneSource(sources.get(0), false);
		}
		else {
			for(var src : sources) {
				oneSource(src, true);
			}
		}
	}

	private void oneSource(String source, boolean multiple) throws IOException {
		if (isRemotePath(source)) {
			if (isRemotePath(target)) {
				remoteToRemote(source, multiple);
			} else {
				remoteToLocal(source, multiple);
			}
		} else {
			if (isRemotePath(target)) {
				localToRemote(source, multiple);
			} else {
				throw new IllegalArgumentException("Local to local copy not supported, use your OS tools!");
			}
		}
	}

	File checkPath(String path) throws FileNotFoundException {
		var file = new File(path);
		if (!file.exists()) {
			throw new FileNotFoundException(path);
		}
		return file;
	}

	void localToRemote(String source, boolean multiple) throws IOException {
		var uri = TNFSURI.parsePath(target); 
		var mount = doConnectAndMount(uri);
		var local = Paths.get(source);
		
		new FileTransfer(force, !noProgress, recursive, getSeparator(isWindowsParsing())).
			localToRemote(mount, local, uri.path().orElse("/"));
	}

	void remoteToLocal(String source, boolean multiple) throws IOException {
		var uri = TNFSURI.parsePath(source); 
		var mount = doConnectAndMount(uri);
		var local = Paths.get(target);
		
		new FileTransfer(force, !noProgress, recursive, getSeparator(isWindowsParsing())).
			remoteToLocal(mount, uri.path().orElse("/"), local);
	}

	void remoteToRemote(String source, boolean multiple) throws IOException {
		throw new UnsupportedOperationException("TODO");
	}
}
