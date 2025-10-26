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
package uk.co.bithatch.tnfs.server;

import java.util.ArrayList;
import java.util.stream.Stream;

import uk.co.bithatch.tnfs.lib.Command.Entry;

public class DirXHandle extends AbstractDirHandle<Entry> {
	
	public DirXHandle(Stream<Entry> stream) {
		super(stream);
		
		/* TODO Unfortunately, must know size for OpenDirX. Double
		 * check this with other implementations, to see if its acceptable
		 * to return zero (configurable?). Probably a better way to do this anyway */
		if(size() == -1) {
			var l = new ArrayList<Entry>();
			while(hasNext())
				l.add(next());
			stream = l.stream();
			it = stream.spliterator();
			str = stream;
		}
	}

	public int size() {
		return (int)it.getExactSizeIfKnown();
	}
}