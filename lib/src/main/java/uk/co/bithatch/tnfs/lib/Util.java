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

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class Util {
	
	private final static class Defaults {
		private final static SecureRandom RANDOM = new SecureRandom();
	}

	private static final long MS_IN_HOUR = TimeUnit.HOURS.toMillis(1);
	private static final long MS_IN_MINUTE = TimeUnit.MINUTES.toMillis(1);


	/**
	 * {@link ByteBuffer#slice()} does not preserve byte order which
	 * we general need to do. 
	 * 
	 * @param buffer original buffer
	 * @param offset offset
	 * @param length length
	 * @return sliced buffer with same byte order
	 */
	public static ByteBuffer sliceAndOrder(ByteBuffer buffer, int offset, int length) {
		var slice = buffer.slice(offset, length);
		slice.order(buffer.order());
		return slice;
	}

	/**
	 * {@link ByteBuffer#slice()} does not preserve byte order which
	 * we general need to do. 
	 * 
	 * @param buffer original buffer
	 * @return sliced buffer with same byte order
	 */
	public static ByteBuffer sliceAndOrder(ByteBuffer buffer) {
		var slice = buffer.slice();
		slice.order(buffer.order());
		return slice;
	}

	public static Optional<String> emptyOptionalIfBlank(String str) {
		return "".equals(str) ? Optional.empty() : Optional.ofNullable(str);
	}

	public static Optional<char[]> emptyOptionalIfBlank(char[] str) {
		return str != null && str.length == 0 ? Optional.empty() : Optional.ofNullable(str);
	}
	
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

	/**
	 * Normalizes a Unix-style path string using pure string handling.
	 * Rules:
	 *  - Collapse multiple '/'.
	 *  - Remove "." segments (including leading "./" and trailing "/.").
	 *  - Resolve ".." by removing the previous segment; never ascends above "/" for absolute paths.
	 *  - For absolute inputs, result is absolute; for relative, result is relative (no leading "./").
	 *  - Trailing slashes are removed unless the result is root ("/").
	 *
	 * Examples:
	 *   "/di1/dir2/./dir3"        -> "/di1/dir2/dir3"
	 *   "./dir/./"                -> "dir"
	 *   "/dir1/dir2/../dir3"      -> "/dir1/dir3"
	 *   "/.."                     -> "/"
	 *   "/dir1/dir2/../../../../../" -> "/"
	 *   
	 *   @param path path to normalise
	 *   @param sep separator
	 */
	public static String normalUnixPath(String path) {
		return normalPath(path, '/');
	}

	/**
	 * Normalizes a Unix-style or Windows path string using pure string handling.
	 * Rules:
	 *  - Collapse multiple '/'.
	 *  - Remove "." segments (including leading "./" and trailing "/.").
	 *  - Resolve ".." by removing the previous segment; never ascends above "/" for absolute paths.
	 *  - For absolute inputs, result is absolute; for relative, result is relative (no leading "./").
	 *  - Trailing slashes are removed unless the result is root ("/").
	 *
	 * Examples:
	 *   "/di1/dir2/./dir3"        -> "/di1/dir2/dir3"
	 *   "./dir/./"                -> "dir"
	 *   "/dir1/dir2/../dir3"      -> "/dir1/dir3"
	 *   "/.."                     -> "/"
	 *   "/dir1/dir2/../../../../../" -> "/"
	 *   
	 *   @param path path to normalise
	 *   @param sep separator
	 */
	public static String normalPath(String path, char sep) {
	    if (path == null || path.isEmpty()) return "";

	    final boolean absolute = path.charAt(0) == sep;

	    // Split by '/', handling consecutive slashes via empty segments
	    // Iterate and use a small deque-like buffer implemented with an array list.
	    java.util.ArrayList<String> stack = new java.util.ArrayList<>();

	    int i = 0, n = path.length();
	    while (i < n) {
	        // Skip consecutive '/'
	        while (i < n && path.charAt(i) == sep) i++;
	        if (i >= n) break;

	        // Read next segment [i, j)
	        int j = i;
	        while (j < n && path.charAt(j) != sep) j++;
	        String segment = path.substring(i, j);
	        i = j;

	        if (segment.equals("") || segment.equals(".")) {
	            // ignore
	            continue;
	        } else if (segment.equals("..")) {
	            if (!stack.isEmpty()) {
	                // Pop last normal segment if possible
	                String last = stack.get(stack.size() - 1);
	                if (!last.equals("..")) {
	                    stack.remove(stack.size() - 1);
	                } else {
	                    // previous was ".." (only happens for relative paths); keep another ".."
	                    if (!absolute) stack.add("..");
	                }
	            } else {
	                // Nothing to pop
	                if (!absolute) stack.add(".."); // keep leading ".." for relative paths
	                // If absolute, ignore attempts to go above root
	            }
	        } else {
	            // normal path component
	            stack.add(segment);
	        }
	    }

	    // Build result
	    if (absolute) {
	        if (stack.isEmpty()) return String.valueOf(sep);
	        StringBuilder sb = new StringBuilder(path.length());
	        for (int k = 0; k < stack.size(); k++) {
	            sb.append(sep).append(stack.get(k));
	        }
	        return sb.toString();
	    } else {
	        if (stack.isEmpty()) return ""; // no leading "./" retained
	        String first = stack.get(0);
	        int totalLen = Math.max(0, stack.size() - 1); // for slashes
	        for (String s : stack) totalLen += s.length();
	        StringBuilder sb = new StringBuilder(totalLen);
	        sb.append(first);
	        for (int k = 1; k < stack.size(); k++) {
	            sb.append(sep).append(stack.get(k));
	        }
	        return sb.toString();
	    }
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
		return basename(path, '/');
	}

	/**
	 * Get the file portion of a path, i.e the part after the last separator. If the
	 * provided path ends with a separator, this is stripped first.
	 * 
	 * @param path path
	 * @param sep separator
	 * @return file basename
	 */
	public static String basename(String path, char sep) {
		if (path.equals(String.valueOf(sep))) {
			return path;
		}
		while (path.endsWith(String.valueOf(sep))) {
			path = path.substring(0, path.length() - 1);
		}
		int idx = path.lastIndexOf(sep);
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
		return dirname(path, '/');
	}
	
	/**
	 * Get the file parent of a path, i.e the part before the last separator. If the
	 * provided path ends with a separator, this is stripped first.
	 * 
	 * @param path path
	 * @param sep separator
	 * @return file dirname
	 */
	public static String dirname(String path, char sep) {
		if (path.equals(String.valueOf(sep))) {
			return path;
		}
		while (path.endsWith(String.valueOf(sep))) {
			path = path.substring(0, path.length() - 1);
		}
		int idx = path.lastIndexOf(sep);
		return idx == -1 ? path : path.substring(0, idx);
	}

	/**
	 * Resolve paths
	 * 
	 * @param path     first path portion
	 * @param filename path to resolve
	 * @return resolved path
	 */
	public static String resolvePaths(String path, String filename, char sep) {
		if(filename.startsWith("/"))
			return filename;
		else
			return concatenatePaths(path, filename, sep);
	}

	/**
	 * Concatenate two paths, removing any additional leading/trailing slashes.
	 * 
	 * @param path     first path portion
	 * @param filename path to append
	 * @return concatenated path
	 */
	public static String concatenatePaths(String path, String filename, char sep) {
		if (filename.startsWith("." + sep))
			filename = filename.substring(2);
		while (path.endsWith(String.valueOf(sep))) {
			path = path.substring(0, path.length() - 1);
		}
		return path + sep + filename;
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

	public static String absolutePath(String cwd, String newCwd, char sep) {
		if(!newCwd.equals(String.valueOf(sep)) && newCwd.endsWith(String.valueOf(sep))) {
			newCwd = newCwd.substring(0, newCwd.length() - 1);
		}
		if (!newCwd.startsWith(String.valueOf(sep))) {
			newCwd = normalPath(concatenatePaths(cwd, newCwd, sep), sep);
		}
		return newCwd;
	}

	public static byte[] concatenate(byte[] a1, byte[] a2) {
		var b = new byte[a1.length + a2.length];
		System.arraycopy(a1, 0, b, 0, a1.length);
		System.arraycopy(a2, 0, b, a1.length, a2.length);
		return b;
	}


	public  static String[] splitPath(char sep, String path) {
		return ( path.startsWith(String.valueOf(sep)) ? path.substring(1) : path ).split(Pattern.quote(String.valueOf(sep)));
	}

	public  static String[] processHome(char sep, String[] pathParts) {
		return processHome(sep, pathParts,System.getProperty("user.home", String.valueOf(sep)));
	}

	public  static String[] processHome(char sep, String[] pathParts, String homeDir) {
		var l = new ArrayList<>(Arrays.asList(pathParts));
		for(var p : pathParts) {
			if(p.equals("~")) {
				l.clear();
				l.addAll(Arrays.asList(splitPath(sep, homeDir)));
			}
			else {
				l.add(p);
			}
		}
		return l.toArray(new String[0]);
	}
}
