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
package uk.co.bithatch.tnfs.lib;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.ArrayList;

public class Interrupt {
	
	@FunctionalInterface
	public interface IoTask {
		void run() throws IOException;
	}
	
	@FunctionalInterface
	public interface IoCall<T> {
		T call() throws IOException;
	}

	public static void ioInterrupt(IoTask task, Duration timeout) throws IOException {
		var exc = new ArrayList<Exception>();
		var t = new Thread(() -> {
			try {
				task.run();
			} catch (Exception e) {
				exc.add(e);
			}
		}, "InterruptableTask");
		try {
			t.start();
			t.join(timeout.toMillis());
			if(exc.isEmpty())
				return;
			else {
				var ex = exc.get(0);
				if(ex instanceof IOException ioe) {
					throw ioe;
				}
				else if(ex instanceof RuntimeException re) {
					throw re;
				}
				else {
					throw new IllegalStateException("Unexpected exception.", ex);
				}
			}
		} catch (InterruptedException e) {
		}
		t.interrupt();
		throw new InterruptedIOException();
	}
	
	public static <T> T ioCall(IoCall<T> task, Duration timeout) throws IOException {
		var exc = new ArrayList<Exception>();
		var rslt = new ArrayList<T>();
		var t = new Thread(() -> {
			try {
				rslt.add(task.call());
			} catch (Exception e) {
				exc.add(e);
			}
		}, "InterruptableTask");
		try {
			t.start();
			t.join(timeout.toMillis());
			if(exc.isEmpty())
				return rslt.get(0);
			else {
				var ex = exc.get(0);
				if(ex instanceof IOException ioe) {
					throw ioe;
				}
				else if(ex instanceof RuntimeException re) {
					throw re;
				}
				else {
					throw new IllegalStateException("Unexpected exception.", ex);
				}
			}
		} catch (InterruptedException e) {
		}
		t.interrupt();
		throw new InterruptedIOException();
	}
}
