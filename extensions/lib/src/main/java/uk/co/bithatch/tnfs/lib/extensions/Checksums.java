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
package uk.co.bithatch.tnfs.lib.extensions;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

import uk.co.bithatch.tnfs.lib.ModeFlag;
import uk.co.bithatch.tnfs.lib.OpenFlag;
import uk.co.bithatch.tnfs.lib.TNFSFileSystem;
import uk.co.bithatch.tnfs.lib.Util;
import uk.co.bithatch.tnfs.lib.extensions.Extensions.Checksum;

public class Checksums {

	public static String sum(TNFSFileSystem mnt, String path, Checksum type) throws IOException, NoSuchAlgorithmException {
		String checksum;
		switch (type) {
		case CRC32:
			try (var in = new CheckedInputStream(
					Channels.newInputStream(mnt.open(path, new ModeFlag[0], OpenFlag.READ)),
					new CRC32())) {
				in.transferTo(OutputStream.nullOutputStream());
				checksum = String.format("%04x", in.getChecksum().getValue());
			}
			break;
		default:
			var md = MessageDigest.getInstance(type.name());
			try (var in = new DigestInputStream(Channels.newInputStream(
					mnt.open(path, new ModeFlag[0], OpenFlag.READ)), md)) {
				in.transferTo(OutputStream.nullOutputStream());
				checksum = Util.toHexString(md.digest());
			}
			break;
		}
		return checksum;
	}
}
