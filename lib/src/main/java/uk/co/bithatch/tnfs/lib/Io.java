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
package uk.co.bithatch.tnfs.lib;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.text.MessageFormat;

/**
 * General I/O utilities.
 */
public final class Io {


	/**
	 * Prompt for a response from standard input, optionally masking (when supported). 
	 * 
	 * @param password mask input
	 * @param message message format
	 * @param args arguments
	 * @return password or empty string
	 */
	public static String prompt(boolean password, String message, Object... args) {
		var rdr = System.console();
		if(rdr == null) {
			try {
				System.out.print(MessageFormat.format(message, args));
				var line = new BufferedReader(new InputStreamReader(System.in)).readLine();
				if(line == null)
					throw new IllegalStateException("Password required, but no standard input.");
				return line == null ? "" : line;
			}
			catch(IOException ioe) {
				throw new UncheckedIOException(ioe);
			}
		}
		else {
			if(password) {
				var chs = rdr.readPassword(MessageFormat.format(message, args));
				if(chs == null)
					throw new IllegalStateException("Aborted.");
				return chs == null ? "" : new String(chs);
			}
			else {
				var chs = rdr.readLine(MessageFormat.format(message, args));
				if(chs == null)
					throw new IllegalStateException("Aborted.");
				return chs == null ? "" : new String(chs);
			}
		}
	}
}
