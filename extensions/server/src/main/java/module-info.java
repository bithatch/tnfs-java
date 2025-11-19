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
import uk.co.bithatch.tnfs.server.TNFSMessageHandler;
import uk.co.bithatch.tnfs.server.extensions.CopyHandler;
import uk.co.bithatch.tnfs.server.extensions.MountsHandler;
import uk.co.bithatch.tnfs.server.extensions.PktSzHandler;
import uk.co.bithatch.tnfs.server.extensions.SecureMountHandler;
import uk.co.bithatch.tnfs.server.extensions.SumHandler;

module uk.co.bithatch.tnfs.server.extensions {
	exports uk.co.bithatch.tnfs.server.extensions; 
	requires transitive uk.co.bithatch.tnfs.lib.extensions;
	requires transitive uk.co.bithatch.tnfs.server;
	requires transitive org.slf4j;
	provides TNFSMessageHandler with SumHandler, CopyHandler, MountsHandler,
									 PktSzHandler, SecureMountHandler;
}