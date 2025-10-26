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
package uk.co.bithatch.tnfs.client;

import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import uk.co.bithatch.tnfs.lib.ModeFlag;
import uk.co.bithatch.tnfs.lib.OpenFlag;

public class Test {

	public static void main(String[] args) throws Exception {
		try(var clnt = new TNFSClient.Builder().
//				withTcp().
				build()) {
			

			
			try(var mnt = clnt.mount().build()) {
				
				var free = mnt.free();
				var sz = mnt.size();
				var used = sz - free;
				System.out.println("Free: " + free + " K");
				System.out.println("Size: " + sz + " K");
				System.out.println("Used: " + used + " K");

				/* Simple list */
//				try(var dirstr = mnt.list()) {
//					dirstr.forEach(d -> {
//						System.out.println(d);
//					});
//				}
				
				try(var dirstr = mnt.entries()) {
					dirstr.stream().forEach(d -> {
						System.out.println(d);
					});
				}
			}
		}
	}

	public static void Xmain(String[] args) throws Exception {
		try(var clnt = new TNFSClient.Builder().
				withHostname("terra").
//				withTcp().
				build()) {
			try(var mnt = clnt.mount().build()) {
				
				/* Full list */
				try(var dirstr = mnt.directory("roms/zxspectrum/Games/a")) {
					dirstr.stream().forEach(d -> {
						System.out.println(d);
					});
				}

				/* Simple list */
				try(var dirstr = mnt.list("roms")) {
					dirstr.forEach(d -> {
						System.out.println(d);
					});
				}

				System.out.println("G1: " + mnt.stat("roms/zxspectrum/Games/a/Azzurro 8-Bit Jam (2011)(RELEVO Videogames)(48K-128K)(ES)(en).tzx"));
//				
				/* Get file */
				try(var o = Files.newByteChannel(Paths.get("test.tzx"), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
					try(var f = mnt.open("roms/zxspectrum/Games/a/Azzurro 8-Bit Jam (2011)(RELEVO Videogames)(48K-128K)(ES)(en).tzx")) {
						var buf = ByteBuffer.allocate(65536);
						while( f.read(buf) != -1) {
							buf.flip();
							o.write(buf);
							buf.clear();
						}
					}
				}
				
				
				/* Upload file */
				try(var f = Files.newByteChannel(Paths.get("test.tzx"), StandardOpenOption.READ)) {
					try(var o = mnt.open("roms/zxspectrum/test.tzx", OpenFlag.WRITE, OpenFlag.TRUNCATE, OpenFlag.CREATE)) {
						var buf = ByteBuffer.allocate(256);
						while( f.read(buf) != -1) {
							buf.flip();
							o.write(buf);
							buf.clear();
						}
					}
				}
				

				System.out.println("G2: " + mnt.stat("roms/zxspectrum/test.tzx"));
				
				mnt.unlink("roms/zxspectrum/test.tzx");
				mnt.mkdir("roms/zxspectrum/test.dir");
				mnt.chmod("roms/zxspectrum/test.dir", ModeFlag.ALL_FLAGS);
				System.out.println("G3: " + mnt.stat("roms/zxspectrum/test.dir"));
				mnt.rmdir("roms/zxspectrum/test.dir");
				try {
					System.out.println("G3: " + mnt.stat("roms/zxspectrum/test.dir"));
					throw new IllegalStateException("Should have failed.");
				}
				catch(FileNotFoundException fnfe) {
					// expected
				}
				
			}
		}
	}
}
