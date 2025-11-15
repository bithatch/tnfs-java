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

import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;

public enum ModeFlag implements BitSet {
	
	IXOTH, IWOTH, IROTH, IXGRP, IWGRP, IRGRP, IXUSR, IWUSR, IRUSR, IRWXU, ISVTX, ISGID, ISUID, IFIFO, IFCHR, IFDIR, IFBLK, IFREG, IFLNK, IFSOCK, IFMT;
	
	public static ModeFlag[] DEFAULT_FLAGS = {
		IRUSR, IXUSR, IRGRP, IXGRP
	};
	
	public static ModeFlag[] DEFAULT_WRITABLE_FLAGS = {
		IRUSR, IWUSR, IXUSR, IRGRP, IWGRP, IXGRP
	};
	
	public static ModeFlag[] ALL_FLAGS = {
		IRUSR, IWUSR, IXUSR,
		IRGRP, IWGRP, IXGRP,
		IROTH, IWOTH, IXOTH,
	};
	
	public static ModeFlag[] ALL_FLAGS_DIR = {
			IFDIR,
			IRUSR, IWUSR, IXUSR,
			IRGRP, IWGRP, IXGRP,
			IROTH, IWOTH, IXOTH,
		};
	
	public static ModeFlag[] USER_EXECUTABLE = {
		IRUSR, IXUSR,
	};
	
	public static ModeFlag[] USER_WRITABLE = {
		IRUSR, IWUSR,
	};
	
	public static ModeFlag[] ALL_USER = {
		IRUSR, IWUSR, IXUSR
	};
	
	public static ModeFlag[] ALL_EXECUTABLE = {
		IRUSR, IXUSR,
		IRGRP, IXGRP,
		IROTH, IXOTH,
	};
	
	public static ModeFlag[] forAttributes(PosixFileAttributes attrs) {
		var l = new ArrayList<ModeFlag>();
		forBasicType(attrs, l);
		attrs.permissions().forEach(p -> l.add(ModeFlag.forPermission(p)));
		return l.toArray(new ModeFlag[0]);
	}
	
	public static ModeFlag forPermission(PosixFilePermission p) {
		switch(p) {
		case OWNER_READ:
			return IRUSR;
		case OWNER_WRITE:
			return IWUSR;
		case OWNER_EXECUTE:
			return IXUSR;
		case GROUP_READ:
			return IRGRP;
		case GROUP_WRITE:
			return IWGRP;
		case GROUP_EXECUTE:
			return IXGRP;
		case OTHERS_READ:
			return IROTH;
		case OTHERS_WRITE:
			return IWOTH;
		case OTHERS_EXECUTE:
			return IXOTH;
		default:
			throw new IllegalArgumentException();
		}
	}
	
	public static PosixFilePermission toPermission(ModeFlag p) {
		switch(p) {
		case IRUSR:
			return OWNER_READ;
		case IWUSR:
			return PosixFilePermission.OWNER_WRITE;
		case IXUSR:
			return PosixFilePermission.OWNER_EXECUTE;
		case IRGRP:
			return PosixFilePermission.GROUP_READ;
		case IWGRP:
			return PosixFilePermission.GROUP_WRITE;
		case IXGRP:
			return PosixFilePermission.GROUP_EXECUTE;
		case IROTH:
			return PosixFilePermission.OTHERS_READ;
		case IWOTH:
			return PosixFilePermission.OTHERS_WRITE;
		case IXOTH:
			return PosixFilePermission.OTHERS_EXECUTE;
		default:
			throw new IllegalArgumentException();
		}
	}


	public static ModeFlag[] forAttributes(DosFileAttributes attrs) {
		return forAttributes(attrs, null);
	}

	public static ModeFlag[] forAttributes(DosFileAttributes attrs, Path file) {
		var l = new ArrayList<ModeFlag>();
		forBasicType(attrs, l);
		if(!forLegacyFile(file, l)) {
			if(attrs.isReadOnly()) {
				l.addAll(Arrays.asList(ALL_EXECUTABLE));
			}
			else {
				l.addAll(Arrays.asList(ALL_FLAGS));
			}
		}
		return l.toArray(new ModeFlag[0]);
	}
	
	public static ModeFlag[] forAttributes(BasicFileAttributes attrs) {
		return forAttributes(attrs, null);
	}
	
	public static ModeFlag[] forAttributes(BasicFileAttributes attrs, Path file) {
		var l = new ArrayList<ModeFlag>();
		forLegacyFile(file, l);
		forBasicType(attrs, l);
		return l.toArray(new ModeFlag[0]);
	}

	protected static boolean forLegacyFile(Path file, ArrayList<ModeFlag> l) {
		if(file != null) {
			try {
				var legacyFile = file.toFile();
				if(legacyFile.canRead()) {
					l.add(ModeFlag.IRUSR);
					l.add(ModeFlag.IRGRP);
					l.add(ModeFlag.IROTH);
				}
				if(legacyFile.canWrite()) {
					l.add(ModeFlag.IWUSR);
					l.add(ModeFlag.IWGRP);
					l.add(ModeFlag.IWOTH);
				}
				if(legacyFile.canExecute()) {
					l.add(ModeFlag.IXUSR);
					l.add(ModeFlag.IXGRP);
					l.add(ModeFlag.IXOTH);
				}
				return true;
			}
			catch(Exception e) {
				
			}
			
		}
		return false;
	}

	private static void forBasicType(BasicFileAttributes attrs, ArrayList<ModeFlag> l) {
		if(attrs.isDirectory())
			l.add(ModeFlag.IFDIR);
		else if(attrs.isSymbolicLink())
			l.add(ModeFlag.IFLNK);
		else if(attrs.isRegularFile())
			l.add(ModeFlag.IFREG);
	}
	
	@Override
	public int value() {
		switch(this) {
		case IFMT:
			return 0170000;
		case IFSOCK:
			return 0140000;
		case IFLNK:
			return 0120000;
		case IFREG:
			return 0100000;
		case IFBLK:
			return 0060000;
		case IFDIR:
			return 0040000;
		case IFCHR:
			return 0020000;
		case IFIFO:
			return 0010000;
		case ISUID:
			return 0004000;
		case ISGID:
			return 0002000;
		case ISVTX:
			return 0001000;
		case IRWXU:
			return 00700;
		case IRUSR:
			return 00400;
		case IWUSR:
			return 00200;
		case IXUSR:
			return 00100;
		case IRGRP:
			return 00040;
		case IWGRP:
			return 00020;
		case IXGRP:
			return 00010;
		case IROTH:
			return 00004;
		case IWOTH:
			return 00002;
		case IXOTH:
			return 00001;
		default:
			throw new IllegalArgumentException();
		}
	}

	public static int encode(ModeFlag... types) {
		return BitSet.encode(Arrays.asList(types));
	}

	public static PosixFilePermission[] permissions(ModeFlag... types) {
		var l = new ArrayList<PosixFilePermission>();
		for(var m : types) {
			try {
				l.add(toPermission(m));
			}
			catch(IllegalArgumentException iae) {
			}
		}
		return l.toArray(new PosixFilePermission[0]);
	}

	public static ModeFlag[] decode(int val) {
		return BitSet.decode(val, ModeFlag::values, new ModeFlag[0]);
	}

	public static DirEntryFlag[] toDirEntryFlags(String name, ModeFlag... mode) {
		var l = new ArrayList<DirEntryFlag>();
		if(name.equals(".") || name.equals("..")) {
			l.add(DirEntryFlag.SPECIAL);
		}
		var modes = Arrays.asList(mode);
		if(modes.contains(IFDIR))
			l.add(DirEntryFlag.DIR);
		try {
			if(Files.isHidden(Paths.get(name))) {
				l.add(DirEntryFlag.HIDDEN);
			}
		}
		catch(IOException ioe) {}
		return l.toArray(new DirEntryFlag[0]);
	}
}