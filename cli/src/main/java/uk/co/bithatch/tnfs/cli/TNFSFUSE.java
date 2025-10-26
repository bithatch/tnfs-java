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
package uk.co.bithatch.tnfs.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

import org.cryptomator.jfuse.api.Fuse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import uk.co.bithatch.tnfs.fuse.TNFSFUSEFileSystem;

/**
 * Mount TNFS resources to the local file system using FUSE.
 */
@Command(name = "tnfs-fuse", mixinStandardHelpOptions = true, description = "Mount TNFS resources to the local file system using FUSE.")
public class TNFSFUSE extends AbstractTNFSFilesCommand implements Callable<Integer> {

	private Logger log;
	
	/**
	 * Entry point.
	 * 
	 * @param args command line arguments
	 * @throws Exception on error
	 */
	public static void main(String[] args) throws Exception {
		var client = new TNFSFUSE();
        System.exit(new CommandLine(client).setExecutionExceptionHandler(new ExceptionHandler(client)).execute(args));
	}

	@Option(names = { "-c", "--create" }, description = "Create the mount point folder if it does not exist.")
	private boolean create;

	@Parameters(arity = "1", index = "0", description = "URI of TNFS resource to mount.")
	protected String remotePath;

	@Parameters(arity = "1", index = "1", description = "The local path to mount the TNFS resource to.")
	protected Path mountPoint;

	@Spec 
	private CommandSpec spec;

	@Override
	public Integer call() throws Exception {
		start();
		return 0;
	}

	@Override
	protected void onStart() throws Exception {
		log = LoggerFactory.getLogger(TNFSFUSE.class);
		if(create && !Files.exists(mountPoint)) {
			Files.createDirectories(mountPoint);
		}
		
		var uri = TNFSURI.parse(remotePath);
		var mount = doMount(uri, doConnect(uri));
		var builder = Fuse.builder();
		var fuseOps = new TNFSFUSEFileSystem(mount, builder.errno());
		try (var fuse = builder.build(fuseOps)) {
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				log.info("Shutting down.");
				try {
					fuse.close();
				} catch (TimeoutException e) {
				}
			}));
			
			log.info("Mounting {} at {}...", remotePath, mountPoint);
			fuse.mount(getSpec().name(), mountPoint, "-s");
			log.info("Mounted {} at {}...", remotePath, mountPoint);
			while(true) {
				Thread.sleep(Integer.MAX_VALUE);
			}
		} 
	}

	@Override
	public CommandSpec getSpec() {
		return spec;
	}
}
