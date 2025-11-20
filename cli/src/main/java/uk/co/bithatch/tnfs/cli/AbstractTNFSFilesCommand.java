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

import java.text.MessageFormat;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import picocli.CommandLine.Option;
import uk.co.bithatch.tnfs.lib.Util;

public abstract class AbstractTNFSFilesCommand extends AbstractTNFSCommand {
	
	protected Terminal terminal;
	protected LineReader reader;

	@Option(names = { "--no-terminal" }, description = "Disable terminal.", hidden = true)
	private boolean noTerminal;

	/**
	 * Constructor.
	 */
	public AbstractTNFSFilesCommand() {
		try {
			terminal = TerminalBuilder.builder().
					system(true).
//					dumb(true).
					build();
			
			reader = LineReaderBuilder.builder().terminal(terminal).build();
		} catch (Exception e) {
			if(verbosity != null && verbosity.length > 1)
				error("Failed.", e);
			terminal = null;
		}

	}
    
	@Override
	protected String prompt(boolean password, String message, Object... args) {
		if(noTerminal || terminal == null) {
			return super.prompt(password, message, args);
		}
		else {
			if(password) {
				return reader.readLine(MessageFormat.format(message, args), '*');
			}
			else {
				return reader.readLine(MessageFormat.format(message, args));
			}
		}
	}

	 @Override
	protected void insecureWarning() {
		var trm = terminal.writer();
        var sb = new AttributedStringBuilder();
        sb.style(AttributedStyle.BOLD);
        sb.style(new AttributedStyle().foreground(AttributedStyle.RED));
        sb.append("This connection is insecure, your username and password will be transmitted without protection");
        sb.style(AttributedStyle.DEFAULT);
        sb.println(terminal);
        sb = new AttributedStringBuilder();
        sb.style(new AttributedStyle().faint());
        sb.append("Use the `-S` or `--secure` arguments if the server supports TNFSJ extensions.");
        sb.style(AttributedStyle.DEFAULT);
        sb.println(terminal);
		trm.flush();
	}

	 protected char getSeparator(boolean useForwardSlash) {
        return useForwardSlash ? '/' : ( isWindowsParsing() ? '\\' : '/' );
    }
	
	protected boolean isWindowsParsing() {
		if(paths == PathsMode.UNIX)
			return false;
		else if(paths == PathsMode.WINDOWS)
			return true;
		else
			return Util.isWindows();
	}
}
