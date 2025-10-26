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

import java.util.concurrent.Callable;

import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;
import uk.co.bithatch.tnfs.cli.TNFSContainer;
import uk.co.bithatch.tnfs.cli.TNFSTP.FilenameCompletionMode;
import uk.co.bithatch.tnfs.lib.Version;

/**
 * Abstract TNFSTP command.
 */
public abstract class TNFSTPCommand implements IVersionProvider, Callable<Integer> {
	
	public interface FileOp {
		void op(String path) throws Exception;
	}
	
	@Spec
	private CommandSpec spec;
	
	private final FilenameCompletionMode completionMode;
	
	protected TNFSTPCommand(FilenameCompletionMode completionMode) {
		this.completionMode = completionMode;
	}

	@Override
	public final Integer call() throws Exception {
		getContainer().startIfNotStarted();
		return onCall();
	}

	protected abstract Integer onCall() throws Exception;

	@SuppressWarnings("unchecked")
	<C extends TNFSContainer> C getContainer() {
		return (C) spec.parent().userObject();
	}

	@Override
	public String[] getVersion() throws Exception {
		return Version.getVersion("uk.co.bithatch", "tnfs-java-cli").split("\\.");
	}

	public FilenameCompletionMode completionMode() {
		return completionMode;
	}
}