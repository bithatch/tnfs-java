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
package uk.co.bithatch.tnfs.it;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.security.NoSuchAlgorithmException;

public class DigestReadableChannel extends AbstractDigestReadableChannel {

	private final ReadableByteChannel delegate;

	DigestReadableChannel(ReadableByteChannel delegate)
			throws NoSuchAlgorithmException {
		this.delegate = delegate;
	}

	@Override
	public int read(ByteBuffer dst) throws IOException {
		checkClosed();
		
		var start  = dst.position();
		var read = delegate.read(dst);
		if(read == -1)
			return -1;

		dst.position(start);
		dst.limit(start + read);
		digest.update(dst);
		
		dst.position(start);
		dst.limit(start + read);
		
		return read;
	}

	@Override
	protected void onClose() throws IOException {
		delegate.close();
	}

}
