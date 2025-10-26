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
import java.util.Optional;
import java.util.stream.Stream;

import uk.co.bithatch.tnfs.client.AbstractTNFSMount.ReadDirIterator;
import uk.co.bithatch.tnfs.client.TNFSClientExtension.AbstractTNFSClientExtension;
import uk.co.bithatch.tnfs.lib.Message;
import uk.co.bithatch.tnfs.lib.extensions.Extensions;

public class Mounts extends AbstractTNFSClientExtension {

	public Stream<String> mounts() throws IOException {
		return ReadDirIterator.stream(
				client,
				0, 
				client.sendMessage(Extensions.MOUNTS, Message.of(client.nextSeq(), 0, Extensions.MOUNTS, new Extensions.Mounts())), 
				Optional.empty()
			);
	}
}
