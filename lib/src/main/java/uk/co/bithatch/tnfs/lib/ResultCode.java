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

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public enum ResultCode {
	/**
	 * Success
	 */
	SUCCESS(0x00),
	/**
	 * Operation not permitted
	 */
	PERM(0x01),
	/**
	 * No such file or directory
	 */
	NOENT(0x02),
	/**
	 * I/O error
	 */
	IO(0x03),
	/**
	 * No such device or address
	 */
	NXIO(0x05),
	/**
	 * Argument list too long
	 */
	TOOBIG(0x05),
	/**
	 * Bad file number
	 */
	BADF(0x06),
	/**
	 * Try again
	 */
	AGAIN(0x07),
	/**
	 * Out of memory
	 */
	NOMEM(0x08),
	/**
	 * Permission denied
	 */
	ACCESS(0x09),
	/**
	 * Device or resource busy
	 */
	BUSY(0x0A),
	/**
	 * File exists
	 */
	EXIST(0x0B),
	/**
	 * Is not a directory
	 */
	NOTDIR(0x0C),
	/**
	 * Is a directory
	 */
	ISDIR(0x0D),
	/**
	 * Invalid argument
	 */
	INVAL(0x0E),
	/**
	 * File table overflow
	 */
	NFILE(0x0F),
	/**
	 * Too many open files
	 */
	MFILE(0x10),
	/**
	 * File too large
	 */
	FBIG(0x11),
	/**
	 * No space left on device
	 */
	NOSPC(0x12),
	/**
	 * Attempt to seek on a FIFO or pipe
	 */
	SPIPE(0x13),
	/**
	 * Read Only Filesystem
	 */
	ROFS(0x14),
	/**
	 * Filename too long
	 */
	NAMETOOLONG(0x15),
	/**
	 * Function not implemented
	 */
	NOSYS(0x16),
	/**
	 * Directory not empty
	 */
	NOTEMPTY(0x17),
	/**
	 * Too many symbolic links encountered
	 */
	LOOP(0x18),
	/**
	 * No data available
	 */
	NODATA(0x19),
	/**
	 * Out of streams resources
	 */
	NOSTR(0x1a),
	/**
	 * Protocol error
	 */
	PROTO(0x1b),
	/**
	 * File descriptor in bad state
	 */
	BADFD(0x1c),
	/**
	 * Too many users
	 */
	USERS(0x1d),
	/**
	 * No buffer space available
	 */
	NOBUFS(0x1e),
	/**
	 * Operation already in progress
	 */
	ALREADY(0x1f),
	/**
	 * Stale TNFS handle
	 */
	STALE(0x20),
	/**
	 * End of file
	 */
	EOF(0x21),
	/**
	 * Invalid TNFS handle
	 */
	INVALID(0xff);

	private final static Map<Integer, ResultCode> codes = new ConcurrentHashMap<>();

	static {
		Arrays.asList(values()).forEach(v -> codes.put(v.value(), v));
	}

	private int value;

	ResultCode(int value) {
		this.value = value;
	}

	public boolean isOk() {
		return this == SUCCESS;
	}

	public boolean isError() {
		return this != SUCCESS;
	}

	public final static ResultCode fromValue(int value) {
		var rc = codes.get(value);
		if (rc == null)
			throw new IllegalArgumentException("Unknown result code.");
		return rc;
	}

	public int value() {
		return value;
	}
}