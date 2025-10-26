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
package uk.co.bithatch.tnfs.lib;

import java.io.Closeable;
import java.io.IOException;
import java.util.stream.Stream;

import uk.co.bithatch.tnfs.lib.Command.Entry;

public interface TNFSDirectory extends Closeable {

	/**
	 * Get the stream of entries;
	 * 
	 * @return stream
	 */
	Stream<Entry> stream();
	
	/**
	 * Set the position within the currently active directory listing, i.e. while
	 * processing the stream returned by {@link TNFSMount#entries()}.
	 * 
	 * @param position position in directory listing
	 * @throws IOException
	 * @throws IllegalStateException if current thread is not streaming a directory
	 * @throws IOException           on any error
	 */
	void seek(long position) throws IOException;

	/**
	 * Get the current position within the currently active directory listing, i.e.
	 * while processing the stream returned by {@link TNFSMount#entries()}.
	 * 
	 * @return position in directory listing
	 * @throws IOException
	 * @throws IllegalStateException if current thread is not streaming a directory
	 * @throws IOException           on any error
	 */
	long tell() throws IOException;
}
