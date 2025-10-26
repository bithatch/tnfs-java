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

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.Callable;

import me.tongfei.progressbar.ProgressBar;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import uk.co.bithatch.tnfs.cli.TNFSTP.FilenameCompletionMode;
import uk.co.bithatch.tnfs.lib.OpenFlag;
import uk.co.bithatch.tnfs.lib.Util;

/**
 * Put file command.
 */
@Command(name = "put", aliases = { "upload"}, mixinStandardHelpOptions = true, description = "Upload local file.")
public class Put extends TNFSTPCommand implements Callable<Integer> {

	@Parameters(index = "0", arity = "1", description = "File to store.")
	private String file;

	@Parameters(index = "1", arity = "0..1", description = "Destination.")
	private Optional<String> destination;

	public Put() {
		super(FilenameCompletionMode.LOCAL_THEN_REMOTE);
	}

	@Override
	protected Integer onCall() throws Exception {
		var container = getContainer();
		var mnt = container.getMount();
		var path =  container.getLcwd().resolve(file);

		try(var f = Files.newByteChannel(path, StandardOpenOption.READ)) {
			var base = Util.basename(file);
    		var target = Util.relativizePath(container.getCwd(), base);

//			var transfers = getContainer().getTransferHost();

    		try (ProgressBar pb = new ProgressBar(base, Files.size(path))) {
//			transfers.startedTransfer(path.toString(), target, Files.size(path));
    		
				try(var o = mnt.open(target, OpenFlag.WRITE, OpenFlag.TRUNCATE, OpenFlag.CREATE)) {
					var buf = ByteBuffer.allocate(mnt.client().messageSize());
					var rd = 0;
					while( ( rd = f.read(buf) ) != -1) {
						buf.flip();
						o.write(buf);
						buf.clear();
						
						pb.stepBy(rd);
	
//						transfers.transferProgress(path.toString(), target, rd);
					}
				}
//				finally {
//					transfers.finishedTransfer(path.toString(), target);
//				}
    		}
		}

		return 0;
	}
}