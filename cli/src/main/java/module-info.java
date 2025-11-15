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
open module uk.co.bithatch.tnfs.cli {
	
	exports uk.co.bithatch.tnfs.cli;
	
	requires transitive uk.co.bithatch.tnfs.client;
	requires transitive uk.co.bithatch.tnfs.client.extensions;
	requires transitive uk.co.bithatch.tnfs.fuse;
	requires transitive org.jline.builtins;
	requires transitive org.jline.console;
	requires transitive org.jline.reader;
	requires transitive org.jline.terminal;

	requires transitive info.picocli;
	requires transitive info.picocli.shell.jline3;
	
	requires org.slf4j;
	
	requires org.cryptomator.jfuse.api;
	requires me.tongfei.progressbar; 
}