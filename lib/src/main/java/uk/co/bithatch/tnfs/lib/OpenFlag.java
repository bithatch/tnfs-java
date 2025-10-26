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

import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/**
 * TODO replace with StandardOpenOption
 */
public enum OpenFlag implements BitSet {
	READ, WRITE, APPEND, CREATE, TRUNCATE, EXCLUSIVE;

	@Override
	public int value() {
		switch (this) {
		case READ:
			return 0x01;
		case WRITE:
			return 0x02;
		case APPEND:
			return 0x08;
		case CREATE:
			return 0x0100;
		case TRUNCATE:
			return 0x0200;
		case EXCLUSIVE:
			return 0x0400;
		default:
			throw new IllegalArgumentException();
		}
	}

	public OpenOption toOption() {
		switch (this) {
		case READ:
			return StandardOpenOption.READ;
		case WRITE:
			return StandardOpenOption.WRITE;
		case APPEND:
			return StandardOpenOption.APPEND;
		case CREATE:
			return StandardOpenOption.CREATE;
		case TRUNCATE:
			return StandardOpenOption.TRUNCATE_EXISTING;
		case EXCLUSIVE:
			return StandardOpenOption.CREATE_NEW;
		default:
			throw new IllegalArgumentException();
		}
	}

	public static OpenFlag[] decodeOptions(OpenOption... options) {
		return Arrays.asList(options).stream().map(OpenFlag::fromOption).toList().toArray(new OpenFlag[0]);
	}

	public static OpenFlag fromOption(OpenOption option) {
		if (option instanceof StandardOpenOption sopt) {
			switch (sopt) {
			case READ:
				return OpenFlag.READ;
			case WRITE:
				return OpenFlag.WRITE;
			case APPEND:
				return OpenFlag.APPEND;
			case CREATE:
				return OpenFlag.CREATE;
			case TRUNCATE_EXISTING:
				return OpenFlag.TRUNCATE;
			case CREATE_NEW:
				return OpenFlag.EXCLUSIVE;
			default:
				throw new IllegalArgumentException();
			}
		} else {
			throw new IllegalArgumentException();
		}
	}

	public static OpenOption[] encodeOptions(OpenFlag... flags) {
		return Arrays.asList(flags).stream().map(OpenFlag::toOption).toList().toArray(new OpenOption[0]);
	}

	public static int encode(OpenFlag... types) {
		return BitSet.encode(Arrays.asList(types));
	}

	public static OpenFlag[] decode(int val) {
		return BitSet.decode(val, OpenFlag::values, new OpenFlag[0]);
	}
}