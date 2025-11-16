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
package uk.co.bithatch.tnfs.it;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.bithatch.tnfs.client.TNFSClient;
import uk.co.bithatch.tnfs.client.TNFSClient.Builder;
import uk.co.bithatch.tnfs.client.TNFSMount;
import uk.co.bithatch.tnfs.lib.OpenFlag;
import uk.co.bithatch.tnfs.lib.ResultCode;
import uk.co.bithatch.tnfs.lib.TNFS;
import uk.co.bithatch.tnfs.lib.TNFSException;

public abstract class AbstractIntegrationTests {
	protected static String username;
	protected static char[] password;
	
	@BeforeAll
	public static void setupUserAndPassword() {
		username = null;
		password = null;
	}
	
	final static Logger LOG = LoggerFactory.getLogger(AbstractIntegrationTests.class);
	
	interface TestTask {
		void run(TNFSClient clnt, ITNFSServer svr) throws Exception;
	}
	
	interface TestMountTask {
		void run(TNFSMount mount, TNFSClient clnt, ITNFSServer svr) throws Exception;
	}
	
	static {
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "TRACE");
		
		Thread.setDefaultUncaughtExceptionHandler((t,e) -> {
			LOG.error("Uncaught error in thread.", e);
		});
	}
	
	@Test
	public void testIncorrectUsername() throws Exception {
		
		assumeTrue(username != null);
		
		runTest((clnt, svr) -> {
			var exception = Assertions.assertThrows(TNFSException.class, () -> {
				clnt.mount("/").
				withUsername(username + "XXXXXXXX").
				withPassword(password).build();				
			});
			Assertions.assertEquals(ResultCode.PERM, exception.code());
		});

	}
	
	@Test
	public void testIncorrectPassword() throws Exception {
		assumeTrue(username != null);
		
		runTest((clnt, svr) -> {
			var exception = Assertions.assertThrows(TNFSException.class, () -> {
				clnt.mount("/").
				withUsername(username).
				withPassword((new String(password) + "XXXXXXXXX").toCharArray()).build();				
			});
			Assertions.assertEquals(ResultCode.PERM, exception.code());
		});

	}

	@Test
	public void testDirectories() throws Exception {
		runMountTest((mnt, clnt, svr) -> {
			mnt.mkdir("mydir");
			assertTrue(mnt.exists("mydir"));
			
			var stat = mnt.stat("mydir");
			assertTrue(stat.isDirectory());
			
			mnt.rmdir("mydir");
			assertFalse(mnt.exists("mydir"));
		});
	}

	@Test
	public void testDirectoriesWithSlashes() throws Exception {
		runMountTest((mnt, clnt, svr) -> {
			mnt.mkdir("/mydir");
			assertTrue(mnt.exists("/mydir"));
			
			var stat = mnt.stat("mydir/");
			assertTrue(stat.isDirectory());
			
			mnt.rmdir("/mydir");
			assertFalse(mnt.exists("/mydir/"));
		});
	}

	@Test
	public void testDirectoryExistsAsFile() throws Exception {
		runMountTest((mnt, clnt, svr) -> {
			mnt.newFile("myfile");
			assertThrows(FileAlreadyExistsException.class, () -> {
				mnt.mkdir("myfile");
			});
		});
	}

	@Test
	public void testRemoveNonEmptyDirectory() throws Exception {
		runMountTest((mnt, clnt, svr) -> {
			mnt.mkdir("mydir");
			mnt.newFile("mydir/myfile");
			assertThrows(DirectoryNotEmptyException.class, () -> {
				mnt.rmdir("mydir");
			});
			
			mnt.deleteRecursively("mydir");
			assertFalse(mnt.exists("mydir/myfile"));
			assertFalse(mnt.exists("mydir"));
		});
	}

	@Test
	public void testRoot() throws Exception {
		runMountTest((mnt, clnt, svr) -> {
			assertThrows(AccessDeniedException.class, () -> {
				mnt.rmdir("/");
			});
		});
	}

	@Test
	public void testList() throws Exception {
		runMountTest((mnt, clnt, svr) -> {
			mnt.newFile("file1");
			mnt.newFile("file2");
			mnt.newFile("file3");
			mnt.newFile("file4");
			mnt.newFile("file5");
			mnt.newFile("file6");
			mnt.mkdir("newdir_a");
			mnt.mkdir("newdir_b");
			mnt.newFile("newdir_a/file1_a");
			mnt.newFile("newdir_a/file2_a");
			mnt.newFile("newdir_a/file3_a");
			
			var items  = mnt.list().toList();
			
			assertEquals(8, items.size());			
			assertTrue(items.contains("file1"));			
			assertTrue(items.contains("file2"));			
			assertTrue(items.contains("file3"));			
			assertTrue(items.contains("file4"));			
			assertTrue(items.contains("file5"));			
			assertTrue(items.contains("file6"));			
			assertTrue(items.contains("newdir_a"));			
			assertTrue(items.contains("newdir_b"));

			items  = mnt.list("/newdir_a").toList();
			assertEquals(3, items.size());		
			assertTrue(items.contains("file1_a"));			
			assertTrue(items.contains("file2_a"));			
			assertTrue(items.contains("file3_a"));

			items  = mnt.list("/newdir_b").toList();
			assertEquals(0, items.size());

		});
	}

	@Test
	public void testDirectoryList() throws Exception {
		runMountTest((mnt, clnt, svr) -> {
			mnt.newFile("file1");
			mnt.newFile("file2");
			mnt.newFile("file3");
			mnt.newFile("file4");
			mnt.newFile("file5");
			mnt.newFile("file6");
			mnt.mkdir("newdir_a");
			mnt.mkdir("newdir_b");
			mnt.newFile("newdir_a/file1_a");
			mnt.newFile("newdir_a/file2_a");
			mnt.newFile("newdir_a/file3_a");
			
			try(var dir = mnt.directory()) {
				var items = dir.stream().toList();
				assertEquals(8, items.size());			
				var names = items.stream().map(i -> i.name()).toList();
				assertTrue(names.contains("file1"));			
				assertTrue(names.contains("file2"));			
				assertTrue(names.contains("file3"));			
				assertTrue(names.contains("file4"));			
				assertTrue(names.contains("file5"));			
				assertTrue(names.contains("file6"));			
				assertTrue(names.contains("newdir_a"));			
				assertTrue(names.contains("newdir_b"));
			}
			
			try(var dir = mnt.directory("/newdir_a")) {
				var items = dir.stream().toList();
				var names = items.stream().map(i -> i.name()).toList();
				assertEquals(3, items.size());		
				assertTrue(names.contains("file1_a"));			
				assertTrue(names.contains("file2_a"));			
				assertTrue(names.contains("file3_a"));
			}
			
			try(var dir = mnt.directory("/newdir_b")) {
				var items = dir.stream().toList();
				assertEquals(0, items.size());
			}
			
			try(var dir = mnt.directory("/newdir_b/")) {
				var items = dir.stream().toList();
				assertEquals(0, items.size());
			}

		});
	}

	@Test
	public void createNewFileThenWriteToIt() throws Exception {

		runMountTest((mnt, clnt, svr) -> {
			mnt.newFile("abc");
			try(var out = mnt.open("abc", OpenFlag.WRITE)) {
				out.write(ByteBuffer.wrap("Test123".getBytes()));
			}
		});
	}

	@Test
	public void testPutAndGetFile() throws Exception {
		runMountTest((mnt, clnt, svr) -> {
			testPutAndGetFile(mnt, clnt, svr, "file1", 16);
			testPutAndGetFile(mnt, clnt, svr, "file1", 255);
			testPutAndGetFile(mnt, clnt, svr, "file1", 256);
			testPutAndGetFile(mnt, clnt, svr, "file1", TNFS.DEFAULT_UDP_MESSAGE_SIZE);
			testPutAndGetFile(mnt, clnt, svr, "file1", 1024);
			testPutAndGetFile(mnt, clnt, svr, "file1", 1024 * 10);
			testPutAndGetFile(mnt, clnt, svr, "file1", 1024 * 1024);
			testPutAndGetFile(mnt, clnt, svr, "file1", 1024 * 1024 * 6);
	});
	}

	@Test
	public void testPutAndGetFileThreads() throws Exception {
		var l = new ArrayList<Thread>();
		var e = new ArrayList<Throwable>();
		runMountTest((mnt, clnt, svr) -> {
			for(int i = 0 ; i < 10 ; i++) {
				var fi = i;
				var t = new Thread(() -> {
					try {
						testPutAndGetFile(mnt, clnt, svr, "file1_" + fi, 16);
						testPutAndGetFile(mnt, clnt, svr, "file1_" + fi, 255);
						testPutAndGetFile(mnt, clnt, svr, "file1_" + fi, 256);
						testPutAndGetFile(mnt, clnt, svr, "file1_" + fi, TNFS.DEFAULT_UDP_MESSAGE_SIZE);
						testPutAndGetFile(mnt, clnt, svr, "file1_" + fi, 1024);
						testPutAndGetFile(mnt, clnt, svr, "file1_" + fi, 1024 * 10);
						testPutAndGetFile(mnt, clnt, svr, "file1_" + fi, 1024 * 1024);
						testPutAndGetFile(mnt, clnt, svr, "file1_" + fi, 1024 * 1024 * 2);
					}
					catch(Throwable ex) {
						ex.printStackTrace();
						e.add(ex);
					}
					
				}, "testPutAndGetFileThreads-" + i);
				l.add(t);
				t.start();
			}
			
			for(var t : l) {
				t.join(1000 * 60 * 5);
			}
			
		});
		Assertions.assertEquals(0, e.size());
	}

	protected void testPutAndGetFile(TNFSMount mnt, TNFSClient clnt, ITNFSServer svr, String filename, long size) throws Exception {
		LOG.info("Test put then get of {} bytes", size);
		var buf = ByteBuffer.allocateDirect(clnt.size());
		var wrtn = 0l;
		try(var rc = new RandomReadableChannel(clnt.size(), size, false)) {
			try(var fc = mnt.open(filename, OpenFlag.CREATE, OpenFlag.WRITE)) {
				while(rc.read(buf) != -1) {
					buf.flip();
					while(buf.hasRemaining())
						wrtn += fc.write(buf);
					buf.clear();
				}
			}	
			
			try(var dc = new DigestReadableChannel(mnt.open(filename, OpenFlag.READ))) {
				while(dc.read(buf) != -1) {
					buf.clear();
				}
				
				assertArrayEquals(rc.digest(), dc.digest());
			}	
		}
		
		Assertions.assertEquals(size, wrtn);
	}

	protected Builder createClientBuilder(ITNFSServer svr) {
		return new TNFSClient.Builder().
				withHost(InetAddress.getLoopbackAddress()).
				withPort(svr.port()).
				withoutTimeout();
	}

	protected TNFSJServerBuilder createServerBuilder() {
		return new TNFSJServerBuilder().
				withFileMounts().
				withHost(InetAddress.getLoopbackAddress());
	}
	
	protected final void runTest(TestTask task) throws Exception {
		var started = System.currentTimeMillis();

		try {
			doRunTest(task);
			
		}
		finally {
			var ended = System.currentTimeMillis();
			var took =  ended - started;
			LOG.info("Test took: {}ms",  took);
		}
	}

	protected void doRunTest(TestTask task) throws Exception, IOException {
		try(var svr = createServer(createServerBuilder())) {
			try(var clnt = createClientBuilder(svr).
					build()) {
				task.run(clnt, svr);
			}
		}
	}

	protected void runMountTest(TestMountTask task) throws Exception {
		runTest((clnt, svr) -> {
			try(var mnt = clnt.mount("/").build()) {
				task.run(mnt, clnt, svr);
			}
		});
	}

	protected ITNFSServer createServer(TNFSJServerBuilder bldr) {
		ITNFSServer svr = bldr.
				build();
		Thread t = new Thread(svr, "TNFSServer");
		t.start();
		return svr;
	}
}
