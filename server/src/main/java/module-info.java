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
import uk.co.bithatch.tnfs.server.handlers.ChmodHandler;
import uk.co.bithatch.tnfs.server.handlers.CloseDirHandler;
import uk.co.bithatch.tnfs.server.handlers.CloseHandleHandler;
import uk.co.bithatch.tnfs.server.handlers.FreeHandler;
import uk.co.bithatch.tnfs.server.handlers.LSeekHandler;
import uk.co.bithatch.tnfs.server.handlers.MkdirHandler;
import uk.co.bithatch.tnfs.server.handlers.MountHandler;
import uk.co.bithatch.tnfs.server.handlers.OpenDirHandler;
import uk.co.bithatch.tnfs.server.handlers.OpenDirXHandler;
import uk.co.bithatch.tnfs.server.handlers.OpenHandler;
import uk.co.bithatch.tnfs.server.handlers.ReadDirHandler;
import uk.co.bithatch.tnfs.server.handlers.ReadDirXHandler;
import uk.co.bithatch.tnfs.server.handlers.ReadHandler;
import uk.co.bithatch.tnfs.server.handlers.RenameHandler;
import uk.co.bithatch.tnfs.server.handlers.RmdirHandler;
import uk.co.bithatch.tnfs.server.handlers.SizeHandler;
import uk.co.bithatch.tnfs.server.handlers.StatHandler;
import uk.co.bithatch.tnfs.server.handlers.UmountHandler;
import uk.co.bithatch.tnfs.server.handlers.UnlinkHandler;
import uk.co.bithatch.tnfs.server.handlers.WriteHandler;

module uk.co.bithatch.tnfs.server {
	exports uk.co.bithatch.tnfs.server; 
	requires transitive uk.co.bithatch.tnfs.lib;
	requires transitive org.slf4j;
	uses TNFSMessageHandler;
	provides TNFSMessageHandler with CloseHandleHandler, ChmodHandler, UmountHandler,
									 CloseDirHandler, RenameHandler, MkdirHandler,
									 RmdirHandler, FreeHandler, SizeHandler,
									 UnlinkHandler, LSeekHandler, StatHandler,
									 WriteHandler, ReadHandler, OpenHandler,
									 ReadDirHandler, ReadDirXHandler, OpenDirXHandler,
									 OpenDirHandler, MountHandler;
}