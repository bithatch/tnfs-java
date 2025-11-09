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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;

import org.junit.jupiter.api.Test;

import uk.co.bithatch.tnfs.client.TNFSClient;
import uk.co.bithatch.tnfs.client.TNFSClient.Builder;
import uk.co.bithatch.tnfs.client.TNFSMount;

public abstract class AbstractIntegrationTests {
	
	interface TestTask {
		void run(TNFSClient clnt, ITNFSServer svr) throws Exception;
	}
	
	interface TestMountTask {
		void run(TNFSMount mount, TNFSClient clnt, ITNFSServer svr) throws Exception;
	}
	
	static {
//		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "TRACE");
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
			mnt.newFile("newdir_a/file1_a");
			mnt.newFile("newdir_a/file2_a");
			mnt.newFile("newdir_a/file3_a");
			
			var items  = mnt.list().toList();
			
			assertEquals(7, items.size());			
			assertTrue(items.contains("file1"));			
			assertTrue(items.contains("file2"));			
			assertTrue(items.contains("file3"));			
			assertTrue(items.contains("file4"));			
			assertTrue(items.contains("file5"));			
			assertTrue(items.contains("file6"));

			items  = mnt.list("/newdir_a").toList();
			assertEquals(3, items.size());		
			assertTrue(items.contains("file1_a"));			
			assertTrue(items.contains("file2_a"));			
			assertTrue(items.contains("file3_a"));
		});
	}

	protected Builder createClientBuilder(ITNFSServer svr) {
		return new TNFSClient.Builder().
				withHost(InetAddress.getLoopbackAddress()).
				withPort(svr.port()).
				withoutTimeout();
	}

	protected TNFSJServerBuilder createServerBuilder() {
		return new TNFSJServerBuilder().
				withHost(InetAddress.getLoopbackAddress());
	}

	private TNFSClient createClient(ITNFSServer svr) throws IOException {
		return createClientBuilder(svr).
				build();
	}

	private void runMountTest(TestMountTask task) throws Exception {
		runTest((clnt, svr) -> {
			try(var mnt = clnt.mount("/").build()) {
				task.run(mnt, clnt, svr);
			}
		});
	}
	
	private void runTest(TestTask task) throws Exception {

		try(var svr = createServer()) {
			try(var clnt = createClient(svr)) {
				task.run(clnt, svr);
			}
		}
	}

	private ITNFSServer createServer() {
		ITNFSServer svr = createServerBuilder().
				build();
		Thread t = new Thread(svr, "TNFSServer");
		t.start();
		return svr;
	}
}
