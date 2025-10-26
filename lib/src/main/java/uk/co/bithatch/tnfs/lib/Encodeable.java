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

import java.lang.reflect.Array;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

public interface Encodeable {
	
	public final static class Default {
		final static Charset ENCODING;
		
		static {
			ENCODING = Charset.forName(System.getProperty("tnfs.stringEncoding", "UTF-8"));
		}	
	}
	
	ByteBuffer encode(ByteBuffer buf);
	
	static String cString(ByteBuffer data) {
		var nextString = data.slice();
		int i;
		for (i = 0; data.hasRemaining() && data.get() != 0x00; i++) {
		}
		nextString.limit(i);
		return Default.ENCODING.decode(nextString).toString();
	}

	static String paddedCString(ByteBuffer data, int len) {
		var nextString = data.slice();
		int i;
		for (i = 0; data.get() != 0x00; i++) {
		}
		nextString.limit(i);
		var str = Default.ENCODING.decode(nextString).toString();
		for (; i < len - 1; i++) {
			data.get();
		}
		return str;
	}
	
	static ByteBuffer paddedCString(String str, ByteBuffer buf, int len) {
		var bytes = str.getBytes(Default.ENCODING);
		if(bytes.length > len - 1) {
			buf.put(bytes, 0, len);
			buf.put((byte)0);
		}
		else {
			buf.put(bytes, 0, bytes.length);
			for(int i = 0 ; i < len - bytes.length; i++)
				buf.put((byte)0);
		}
		return buf;
	}

	static ByteBuffer cString(String str, ByteBuffer buf) {
		buf.put(str.getBytes(Default.ENCODING));
		buf.put((byte)0);
		return buf;
	}
	
	static ByteBuffer shortLPByteBuffer(ByteBuffer buf) {
		var bbuf = ByteBuffer.allocateDirect(buf.getShort());
		bbuf.put(buf);
		bbuf.flip();
		return bbuf;
	}

	static void shortLPByteBuffer(ByteBuffer data, ByteBuffer buf) {
		buf.putShort((short)data.remaining());
		buf.put(data);
		if(data.remaining() > 0)
			throw new IllegalArgumentException("Target buffer not large enough.");
	}

	static ByteBuffer lpIntArray(int[] arr, ByteBuffer buf) {
		buf.put((byte) arr.length);
		for (var a : arr)
			buf.putInt(a);
		return buf;
	}
	
	static ByteBuffer lpIntShortArray(int[] arr, ByteBuffer buf) {
		buf.put((byte) arr.length);
		for (var a : arr)
			buf.putShort((short) a);
		return buf;
	}
	
	static ByteBuffer intShortArray(int[] arr, ByteBuffer buf) {
		for (var a : arr)
			buf.putShort((short) a);
		return buf;
	}
	
	static byte[] byteArray(ByteBuffer buf) {
		var a = new byte[Byte.toUnsignedInt(buf.get())];
		buf.get(a);
		return a;
	}
	
	static void byteArray(ByteBuffer buf, byte[] arr) {
		buf.put((byte)arr.length);
		buf.put(arr, 0, arr.length);
	}

	static int[] intShortArray(ByteBuffer buf) {
		var b = new int[256];
		int i;
		for (i = 0; i < b.length && buf.hasRemaining(); i++) {
			b[i] = Short.toUnsignedInt(buf.getShort());
		}
		var n = new int[i];
		System.arraycopy(b, 0, n, 0, i);
		return n;
	}

	static int[] lpIntShortArray(ByteBuffer buf) {
		var l = Byte.toUnsignedInt(buf.get());
		var b = new int[l];
		for (int i = 0; i < l; i++) {
			b[i] = Short.toUnsignedInt(buf.getShort());
		}
		return b;
	}

	static ByteBuffer lpIntByteArray(int[] arr, ByteBuffer buf) {
		buf.put((byte) arr.length);
		for (var a : arr)
			buf.put((byte) a);
		return buf;
	}

	static int[] lpIntByteArray(ByteBuffer buf) {
		var l = Byte.toUnsignedInt(buf.get());
		var b = new int[l];
		for (int i = 0; i < l; i++) {
			b[i] = Byte.toUnsignedInt(buf.get());
		}
		return b;
	}

	static ByteBuffer paddedIntByteArray(int[] arr, ByteBuffer buf, int len) {
		if(arr.length > len)
			throw new BufferOverflowException();
		for(int i = 0 ; i < len; i++) {
			if(i < arr.length)
				buf.put((byte) arr[i]);
			else
				buf.put((byte)0);
		}
		return buf;
	}


	static int[] paddedIntByteArray(ByteBuffer buf, int size) {
		var b = new int[size];
		for (int i = 0; i < size; i++) {
			b[i] = Byte.toUnsignedInt(buf.get());
		}
		return b;
	}

	static int[] lpIntArray(ByteBuffer buf) {
		var l = Byte.toUnsignedInt(buf.get());
		var b = new int[l];
		for (int i = 0; i < l; i++) {
			b[i] = buf.getInt();
		}
		return b;
	}

	
	static String[] emptyTerminatedStringArray(ByteBuffer data) {
		var l = new ArrayList<String>();
		while(true) {
			var str = cString(data);
			if(str.equals(""))
				break;
			else
				l.add(str);
		}
		return l.toArray(new String[0]);
	}
	
	static void emptyTerminatedStringArray(String[] arr, ByteBuffer data) {
		for(var str : arr)
			cString(str, data);
	}

	static <T> T[] lpEncodeableArray(ByteBuffer buf, Class<T> type, Function<ByteBuffer, T> converter) {
		var c = Byte.toUnsignedInt(buf.get());
		@SuppressWarnings("unchecked")
		var t = (T[])Array.newInstance(type, c);
		for(int i = 0 ; i < c; i++) {
			t[i] = (T)converter.apply(buf);
		}
		return t;
	}
	
	static <T> void lpEncodeableArray(ByteBuffer buf, T[] arr, BiConsumer<T, ByteBuffer> converter) {
		buf.put((byte)arr.length);
		for(var part : arr) {
			converter.accept(part, buf);
		}
	}

	static void lpEncodeableArray(ByteBuffer buf, Encodeable[] parts) {
		buf.put((byte)parts.length);
		for(var part : parts) {
			part.encode(buf);
		}
	}

	static Optional<String> emptyIfBlank(String str) {
		return str == null || str.length() == 0 ? Optional.empty() : Optional.of(str);
	}
}