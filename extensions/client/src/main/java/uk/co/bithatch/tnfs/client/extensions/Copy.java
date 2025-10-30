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
package uk.co.bithatch.tnfs.client.extensions;

import java.io.IOException;

import uk.co.bithatch.tnfs.client.TNFSMountExtension.AbstractTNFSMountExtension;
import uk.co.bithatch.tnfs.lib.Message;
import uk.co.bithatch.tnfs.lib.extensions.CopyFlag;
import uk.co.bithatch.tnfs.lib.extensions.Extensions;

public class Copy extends AbstractTNFSMountExtension {

	public void copy(String path, String targetPath) throws IOException {
		mount.client().sendMessage(Extensions.COPY, Message.of(mount.sessionId(), Extensions.COPY, new Extensions.Copy(path, targetPath, new CopyFlag[0])), path);
	}
}
