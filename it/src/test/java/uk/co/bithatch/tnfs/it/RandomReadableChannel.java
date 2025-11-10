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
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class RandomReadableChannel extends AbstractDigestReadableChannel {

	private final int maximumBlockSize;
	private final SecureRandom r = new SecureRandom();
	private final boolean randomBlock;

	private long totalDataAmount;

	RandomReadableChannel(int maximumBlockSize, long totalDataAmount, boolean randomBlock)
			throws NoSuchAlgorithmException {
		this.maximumBlockSize = maximumBlockSize;
		this.totalDataAmount = totalDataAmount;
		this.randomBlock = randomBlock;
	}

	@Override
	public int read(ByteBuffer dst) throws IOException {
		checkClosed();
		if (totalDataAmount == 0) {
			return -1;
		}
		var max = Math.min(dst.remaining(), maximumBlockSize);
		if (totalDataAmount < max) {
			max = (int) totalDataAmount;
		}
		var s = max;

		if (randomBlock) {
			s = r.nextInt(max);
			if (s == 0) {
				s = max;
			}
		}
		var b = new byte[s];
		r.nextBytes(b);

		digest.update(b);
		
		dst.put(b);
		totalDataAmount -= b.length;
		return b.length;
	}

}
