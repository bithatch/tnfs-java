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
package uk.co.bithatch.tnfs.server;

import java.io.Closeable;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.stream.Stream;

public class AbstractDirHandle<ENT> implements Closeable, Iterator<ENT> {
	
	protected Spliterator<ENT> it;
	protected Stream<ENT> str;
	private ENT next;
	
	AbstractDirHandle(Stream<ENT> stream) {
		it = stream.spliterator();
		str = stream;
	}

	@Override
	public final void close() {
		str.close();
	}

	@Override
	public final boolean hasNext() {
		checkNext();
		return next != null;
	}

	public void setNext(ENT next) {
		this.next = next;
	}

	@Override
	public final ENT next() {
		try {
			checkNext();
			if(next == null)
				throw new NoSuchElementException();
			
			return next;
		}
		finally {
			next = null;
		}
	}
	
	private void checkNext() {
		if(next == null) {
			it.tryAdvance(v -> next = v);
		}
	}
}