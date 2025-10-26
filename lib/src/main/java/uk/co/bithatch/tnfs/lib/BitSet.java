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

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Supplier;

public interface BitSet {
	
	default int value() {
		return 1 << ordinal();
	}
	
	String name();
	
	int ordinal();
	
	default boolean isUnset(long val) {
		return ( val & value() ) == 0;
	}
	
	default boolean isSet(long val) {
		return ( val & value() ) != 0;
	}
	
	default long unset(long val) {
		return val & ~value();
	}
	
	default long set(long val) {
		return val | value();
	}

	default String id() {
		return name().replace('_', '-').toLowerCase();
	}

	public static <E extends BitSet> E[] decode(int val, Supplier<E[]> values, E[] type) {
		var l = new ArrayList<E>();
		for (var t : values.get()) {
			if ((val & t.value()) != 0) {
				l.add(t);
			}
		}
		return l.toArray(type);
	}

	public static int encode(Collection<? extends BitSet> types) {
		var v = 0;
		for (var t : types) {
			v |= t.value();
		}
		return v;
	}
}
