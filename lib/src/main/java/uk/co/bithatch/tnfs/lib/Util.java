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

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

public class Util {
	
	private final static class Defaults {
		private final static SecureRandom RANDOM = new SecureRandom();
	}

	private static final long MS_IN_HOUR = TimeUnit.HOURS.toMillis(1);
	private static final long MS_IN_MINUTE = TimeUnit.MINUTES.toMillis(1);
	
	public static String toHexString(byte[] data) {
		var sb = new StringBuilder();
		for(var b : data) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}
	
	public static byte[] genRandom(int bytes) {
		var b = new byte[bytes];
		Defaults.RANDOM.nextBytes(b);
		return b;
	}

	public static String normalPath(String path) {
		var idx = 0;
		var eidx = 0;
		while(true) {
			idx = path.indexOf("//");
			if(idx == -1) {
				idx = path.indexOf("../");
				if(idx == -1 && path.endsWith("..")) {
					idx = path.length() - 2;
				}
				
				if(idx == -1) {
					break;
				}
	
				eidx = path.lastIndexOf('/', idx - 2);
				if(eidx == -1) {
					break;
				}
				
				path = path.substring(0, eidx) + path.substring(idx + 2);
			}
			else {
				path = path.substring(0, idx) + path.substring(idx + 2);
			}
		}
		
		if(path.equals(""))
			path = "/";
		return path;
	}

	public static String formatSize(long bytes) {
		var sizeSoFar = String.valueOf(bytes);
		var size = bytes;
		if (size > 9999) {
			size = size / 1024;
			sizeSoFar = size + "KB";
			if (size > 9999) {
				size = size / 1024;
				sizeSoFar = size + "MB";
				if (size > 9999) {
					size = size / 1024;
					sizeSoFar = size + "GB";
				}
			}
		}
		return sizeSoFar;
	}

	public static String formatSpeed(long bytesPerSecond) {
		var speedText = String.valueOf(bytesPerSecond) + "B/s";
		if (bytesPerSecond > 9999) {
			bytesPerSecond = bytesPerSecond / 1024;
			speedText = bytesPerSecond + "KB/s";
			if (bytesPerSecond > 9999) {
				bytesPerSecond = bytesPerSecond / 1024;
				speedText = bytesPerSecond + "MB/s";
				if (bytesPerSecond > 9999) {
					bytesPerSecond = bytesPerSecond / 1024;
					speedText = bytesPerSecond + "GB/s";
				}
			}
		}
		return speedText;
	}

	public static String formatTime(long ms) {
		long hrs = ms / MS_IN_HOUR;
		long mins = (ms % MS_IN_HOUR) / MS_IN_MINUTE;
		long secs = (ms - (hrs * MS_IN_HOUR + (mins * MS_IN_MINUTE))) / 1000;

		if(hrs > 99999) {
			return String.format("%08d ETA", hrs);
		}
		else if(hrs > 99) {
			return String.format("%4d:%02dm ETA", hrs, mins);
		}
		else if(hrs > 0) {
			return String.format("%02d:%02d:%02d ETA", hrs, mins, secs);
		}
		else {
			return String.format("   %02d:%02d ETA", mins, secs);
		}
	}

	/**
	 * Get the file portion of a path, i.e the part after the last /. If the
	 * provided path ends with a /, this is stripped first.
	 * 
	 * @param path path
	 * @return file basename
	 */
	public static String basename(String path) {
		if (path.equals("/")) {
			return path;
		}
		while (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		int idx = path.lastIndexOf("/");
		return idx == -1 ? path : path.substring(idx + 1);
	}
	/**
	 * Get the file parent of a path, i.e the part before the last /. If the
	 * provided path ends with a /, this is stripped first.
	 * 
	 * @param path path
	 * @return file dirname
	 */
	public static String dirname(String path) {
		if (path.equals("/")) {
			return path;
		}
		while (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		int idx = path.lastIndexOf("/");
		return idx == -1 ? path : path.substring(0, idx);
	}

	/**
	 * Resolve paths
	 * 
	 * @param path     first path portion
	 * @param filename path to resolve
	 * @return resolved path
	 */
	public static String resolvePaths(String path, String filename) {
		if(filename.startsWith("/"))
			return filename;
		else
			return concatenatePaths(path, filename);
	}

	/**
	 * Concatenate two paths, removing any additional leading/trailing slashes.
	 * 
	 * @param path     first path portion
	 * @param filename path to append
	 * @return concatenated path
	 */
	public static String concatenatePaths(String path, String filename) {
		if (filename.startsWith("./"))
			filename = filename.substring(2);
		while (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		return path + "/" + filename;
	}

	/**
	 * Trim or pad to length.
	 * 
	 * @param str string
	 * @param width width
	 * @return trimmed or padded string
	 */
	public static String trimOrPad(String str, int width) {
		while(str.length() < width) {
			str = str + " ";
		}
		while(str.length() > width) {
			str = str.substring(str.length() - 1);
		}
		return str;
	}

	public static String relativizePath(String cwd, String newCwd) {
		if(!newCwd.equals("/") && newCwd.endsWith("/")) {
			newCwd = newCwd.substring(0, newCwd.length() - 1);
		}
		if (!newCwd.startsWith("/")) {
			newCwd = normalPath(concatenatePaths(cwd, newCwd));
		}
		return newCwd;
	}

	public static byte[] concatenate(byte[] a1, byte[] a2) {
		var b = new byte[a1.length + a2.length];
		System.arraycopy(a1, 0, b, 0, a1.length);
		System.arraycopy(a2, 0, b, a1.length, a2.length);
		return b;
	}
}
