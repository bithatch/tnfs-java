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

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.BufferUnderflowException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.ReadOnlyFileSystemException;
import java.util.concurrent.Callable;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.bithatch.tnfs.lib.Command.HeaderOnlyResult;
import uk.co.bithatch.tnfs.lib.Command.Result;
import uk.co.bithatch.tnfs.lib.ResultCode;
import uk.co.bithatch.tnfs.lib.TNFSException;

public class Tasks {

	private final static Logger LOG = LoggerFactory.getLogger(Tasks.class);


	public static <T extends Result> T ioCall(Callable<T> task) {
		return ioCall(task, (code) -> new HeaderOnlyResult(code));
	}

	@SuppressWarnings("unchecked")
	public static <T extends Result> T ioCall(Callable<T> task, Function<ResultCode, Result> func) {
		try {
			return task.call();
		}  catch (EOFException e) {
			LOG.trace("EOF. ", e);
			return (T)func.apply(ResultCode.EOF);
		} catch (OutOfMemoryError e) {
			LOG.error("ENOMEM. ", e);
			return (T)func.apply(ResultCode.NOMEM);
		} catch (BufferUnderflowException e) {
			LOG.error("ENOBUFS. ", e);
			return (T)func.apply(ResultCode.NOMEM);
		} catch (IllegalArgumentException e) {
			LOG.error("EINVAL. ", e);
			return (T)func.apply(ResultCode.INVAL);
		} catch (ReadOnlyFileSystemException e) {
			LOG.error("EROFS. ", e);
			return (T)func.apply(ResultCode.ROFS);
		} catch (UnsupportedOperationException e) {
			LOG.error("ENOSYS. ", e);
			return (T)func.apply(ResultCode.NOSYS);
		} catch (FileAlreadyExistsException e) {
			LOG.error("EEXIST. ", e);
			return (T)func.apply(ResultCode.EXIST);
		} catch (NotDirectoryException e) {
			LOG.error("ENOTDIR. ", e);
			return (T)func.apply(ResultCode.NOTDIR);
		} catch (FileSystemLoopException e) {
			LOG.error("ELOOP. ", e);
			return (T)func.apply(ResultCode.LOOP);
		} catch (AccessDeniedException e) {
			LOG.error("ENOTDIR. ", e);
			return (T)func.apply(ResultCode.NOTDIR);
		} catch (FileNotFoundException | NoSuchFileException e) {
			if(LOG.isTraceEnabled())
				LOG.debug("ENOENT. ", e);
			else
				LOG.debug("ENOENT. {}", e.getMessage());
			return (T)func.apply(ResultCode.NOENT);
		} catch (UncheckedIOException | IOException e) {
			LOG.error("EIO. ", e);
			return (T)func.apply(ResultCode.IO);
		}  catch (TNFSException tnfse) {
			LOG.error("TNFSE. " + tnfse.code(), tnfse);
			return (T)func.apply(tnfse.code());
		} catch (Exception e) {
			throw new IllegalStateException("Unhandled exception.", e);
		}
	}
}
