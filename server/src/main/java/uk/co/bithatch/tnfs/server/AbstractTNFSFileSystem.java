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
package uk.co.bithatch.tnfs.server;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import uk.co.bithatch.tnfs.lib.Command.StatResult;
import uk.co.bithatch.tnfs.lib.ModeFlag;
import uk.co.bithatch.tnfs.lib.OpenFlag;
import uk.co.bithatch.tnfs.lib.TNFSDirectory;
import uk.co.bithatch.tnfs.server.TNFSAccessCheck.Operation;

public abstract class AbstractTNFSFileSystem implements TNFSFileSystem {
	
	private final TNFSAccessCheck accessCheck;

	public AbstractTNFSFileSystem(TNFSAccessCheck accessCheck) {
		this.accessCheck = accessCheck;
	}

	@Override
	public final void chmod(String path, ModeFlag... modes) throws IOException {
		
		accessCheck.check(this, path, Operation.READ, Operation.WRITE);
		onChmod(path, modes);
	}

	protected abstract void onChmod(String path, ModeFlag... modes) throws IOException;
		
	@Override
	public final TNFSDirectory directory(String path, String wildcard) throws IOException {
		accessCheck.check(this, path, Operation.READ);
		return onDirectory(path, wildcard);
	}

	protected abstract TNFSDirectory onDirectory(String path, String wildcard) throws IOException;
	
	@Override
	public final long free() throws IOException {		
		accessCheck.check(this, "/", Operation.READ);
		return onFree();
	}

	protected abstract long onFree() throws IOException;

	@Override
	public Stream<String> list(String path) throws IOException {
		accessCheck.check(this, path, Operation.READ);
		return onList(path);
	}

	protected abstract Stream<String> onList(String path) throws IOException;

	@Override
	public final void mkdir(String path) throws IOException {

		accessCheck.check(this, path, Operation.READ, Operation.WRITE);
		onMkdir(path);
	}

	protected abstract void onMkdir(String path) throws IOException;

	@Override
	public final SeekableByteChannel open(String path, ModeFlag[] mode, OpenFlag... flags) throws IOException {
		var flgs = Arrays.asList(flags);
		if(flgs.contains(OpenFlag.CREATE) || flgs.contains(OpenFlag.WRITE) || flgs.contains(OpenFlag.APPEND) || flgs.contains(OpenFlag.TRUNCATE)) {
			accessCheck.check(this, path, Operation.WRITE);
		}
		if(flgs.contains(OpenFlag.READ) || flgs.isEmpty()) {
			accessCheck.check(this, path, Operation.READ);
		}
		return onOpen(path, mode, flgs);
	}
	
	protected abstract SeekableByteChannel onOpen(String path, ModeFlag[] mode, List<OpenFlag> flgs) throws IOException;

	@Override
	public final void rename(String path, String targetPath) throws IOException {
		accessCheck.check(this, path, Operation.READ, Operation.WRITE);
		onRename(path, targetPath);
	}

	protected abstract void onRename(String path, String targetPath) throws IOException;

	@Override
	public final void rmdir(String path) throws IOException {
		accessCheck.check(this, path, Operation.READ, Operation.WRITE);
		onRmdir(path);
	}

	protected abstract void onRmdir(String path) throws IOException;
	
	@Override
	public final long size() throws IOException {
		
		accessCheck.check(this, "/", Operation.READ);
		return onSize();
	}

	protected abstract long onSize() throws IOException ;

	@Override
	public final StatResult stat(String path) throws IOException {
		accessCheck.check(this, path, Operation.READ);
		return onStat(path);
	}

	protected abstract StatResult onStat(String path) throws IOException;

	@Override
	public final void unlink(String path) throws IOException {
		
		accessCheck.check(this, path, Operation.READ, Operation.WRITE);
		onUnlink(path);
	}

	protected abstract void onUnlink(String path) throws IOException;

	@Override
	public final void checkAccess() throws AccessDeniedException {
		accessCheck.check(this, "/", Operation.READ);
		onCheckAccess();
	}

	protected void onCheckAccess() throws AccessDeniedException {
	}
}
