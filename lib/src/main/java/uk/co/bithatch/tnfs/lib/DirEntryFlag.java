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
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

public enum DirEntryFlag implements BitSet {
	DIR, HIDDEN, SPECIAL;
	
	public static boolean isDirectory(DirEntryFlag... flags) {
		return Arrays.asList(flags).contains(DIR);
	}
	
	public static boolean isFile(DirEntryFlag... flags) {
		var flgs = Arrays.asList(flags);
		return !flgs.contains(DIR) && !flgs.contains(SPECIAL);
	}
	
	public static boolean isHidden(DirEntryFlag... flags) {
		return Arrays.asList(flags).contains(HIDDEN);
	}
	
	public static boolean isSpecial(DirEntryFlag... flags) {
		return Arrays.asList(flags).contains(SPECIAL);
	}

	static int encode(DirEntryFlag... types) {
		return BitSet.encode(Arrays.asList(types));
	}

	static DirEntryFlag[] decode(int val) {
		return BitSet.decode(val, DirEntryFlag::values, new DirEntryFlag[0]);
	}

	static DirEntryFlag[] forPath(Path path) {
		var l = new ArrayList<DirEntryFlag>();
		var linkOpts = Files.exists(path) ? new LinkOption[0] : new LinkOption[] { LinkOption.NOFOLLOW_LINKS };
		var fname = path.getFileName().toString();
		
		if(fname.equals(".") || 
		   fname.equals("..") ||
		   ( !Files.isDirectory(path, linkOpts) &&
		     !Files.isRegularFile(path, linkOpts) &&
		     !Files.isSymbolicLink(path) ) ) {
			l.add(SPECIAL);
		}

		if(Files.isDirectory(path, linkOpts)) {
			l.add(DIR);
		}
		
		try {
			if(!fname.equals(".") && 
			   !fname.equals("..")  && 
			   Files.isHidden(path)) {
				l.add(HIDDEN);
			}
		} catch(IOException ioe ){
			//
		}
			
		return l.toArray(new DirEntryFlag[0]);
	}

}