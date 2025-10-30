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

import java.io.IOException;
import java.nio.file.Path;

import org.jline.terminal.Terminal;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import uk.co.bithatch.tnfs.cli.commands.Bye;
import uk.co.bithatch.tnfs.cli.commands.Cd;
import uk.co.bithatch.tnfs.cli.commands.ChkSum;
import uk.co.bithatch.tnfs.cli.commands.Cp;
import uk.co.bithatch.tnfs.cli.commands.Df;
import uk.co.bithatch.tnfs.cli.commands.Get;
import uk.co.bithatch.tnfs.cli.commands.Help;
import uk.co.bithatch.tnfs.cli.commands.Lcd;
import uk.co.bithatch.tnfs.cli.commands.Lpwd;
import uk.co.bithatch.tnfs.cli.commands.Ls;
import uk.co.bithatch.tnfs.cli.commands.Mkdir;
import uk.co.bithatch.tnfs.cli.commands.Mount;
import uk.co.bithatch.tnfs.cli.commands.Mounts;
import uk.co.bithatch.tnfs.cli.commands.Mv;
import uk.co.bithatch.tnfs.cli.commands.Put;
import uk.co.bithatch.tnfs.cli.commands.Pwd;
import uk.co.bithatch.tnfs.cli.commands.Rm;
import uk.co.bithatch.tnfs.cli.commands.Rmdir;
import uk.co.bithatch.tnfs.cli.commands.Stat;
import uk.co.bithatch.tnfs.client.TNFSClient;
import uk.co.bithatch.tnfs.client.TNFSMount;

@Command(name = "tnfsfp-interactive", mixinStandardHelpOptions = false, description = "Interactive shell.", subcommands = {
		Ls.class, Cd.class, Pwd.class, Lcd.class, Lpwd.class, Mkdir.class, Rmdir.class, Mv.class, Rm.class,
		Get.class, Put.class, Bye.class, Df.class, Cp.class, ChkSum.class, Mounts.class, Mount.class, 
		Stat.class, Help.class })
public class InteractiveConsole implements Runnable, TNFSContainer {
	private final TNFSTP cntr;

	public InteractiveConsole(TNFSTP cntr) {
		this.cntr = cntr;
	}

	@Override
	public TNFSMount getMount() {
		return this.cntr.getMount();
	}

	@Override
	public String getCwd() {
		return this.cntr.getCwd();
	}

	@Override
	public Path getLcwd() {
		return this.cntr.getLcwd();
	}

	@Override
	public Terminal getTerminal() {
		return this.cntr.getTerminal();
	}

	@Override
	public void run() {
		throw new ParameterException(this.cntr.getSpec().commandLine(), "Missing required subcommand");
	}

	@Override
	public void setCwd(String cwd) {
		this.cntr.setCwd(cwd);
	}

	@Override
	public void setLcwd(Path lcwd) {
		this.cntr.setLcwd(lcwd);
	}

	@Override
	public CommandSpec getSpec() {
		return this.cntr.getSpec();
	}

	@Override
	public String getSeparator() {
		return this.cntr.getSeparator();
	}

	@Override
	public TNFSMount mount(TNFSURI uri) throws IOException {
		return this.cntr.mount(uri);
	}

	@Override
	public TNFSURI getURI() {
		return this.cntr.getURI();
	}

	@Override
	public TNFSClient getClient() {
		return this.cntr.getClient();
	}

	@Override
	public TNFSClient connect(TNFSURI uri) throws IOException {
		return this.cntr.connect(uri);
	}

	@Override
	public void startIfNotStarted() throws Exception {
		this.cntr.startIfNotStarted();
	}

	@Override
	public String translatePath(String cwd) {
		return cntr.translatePath(cwd);
	}
}