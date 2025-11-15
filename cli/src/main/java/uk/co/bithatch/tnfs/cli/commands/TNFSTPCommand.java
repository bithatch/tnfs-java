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
package uk.co.bithatch.tnfs.cli.commands;

import static uk.co.bithatch.tnfs.lib.Util.absolutePath;
import static uk.co.bithatch.tnfs.lib.Util.concatenatePaths;
import static uk.co.bithatch.tnfs.lib.Util.normalPath;
import static uk.co.bithatch.tnfs.lib.Util.processHome;
import static uk.co.bithatch.tnfs.lib.Util.splitPath;

import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;
import uk.co.bithatch.tnfs.cli.TNFSContainer;
import uk.co.bithatch.tnfs.cli.TNFSTP.FilenameCompletionMode;
import uk.co.bithatch.tnfs.lib.Version;

/**
 * Abstract TNFSTP command.
 */
public abstract class TNFSTPCommand implements IVersionProvider, Callable<Integer> {
	
	public interface FileOp extends VisitOp<String> {
	}
	
	public interface PathOp extends VisitOp<Path> {
	}
	
	public interface VisitOp<T> {
		void op(T path) throws Exception;
	}
	
	@Spec
	private CommandSpec spec;
	
	private final FilenameCompletionMode completionMode;
	
	protected TNFSTPCommand(FilenameCompletionMode completionMode) {
		this.completionMode = completionMode;
	}

	@Override
	public final Integer call() throws Exception {
		getContainer().startIfNotStarted();
		return onCall();
	}
	
	protected Path expandLocal(String path) {
		var l = new ArrayList<Path>();
		expandLocalAndDo(p -> {
			l.add(p);
		}, false, path);
		if(l.isEmpty()) {
			return Paths.get(path);
		}
		else {
			return l.get(0);
		}
	}

	protected void expandLocalAndDo(PathOp op, boolean multiple, String... paths) {
		expandLocalAndDo(op, multiple, Arrays.asList(paths));
	}
	
	protected void expandLocalAndDo(PathOp op, boolean multiple, List<String> paths) {

		var cwd = getContainer().getCwd();
		var sep = getContainer().getSeparator();
		for(var opath : paths) {
			
			var matched = false;
			var path = normalPath(opath, sep);
			if(path.startsWith("..")) {
				path = absolutePath(cwd, path, sep);
			}
			
			if(path.equals(String.valueOf(sep))) {
				doOp(op, Paths.get(String.valueOf(sep)));
				matched = true;
			}
			else {
				
				var absolute = path.startsWith(String.valueOf(sep));
				var root = absolute ? String.valueOf(sep) : cwd;
				var resolved = root;
				var pathParts = splitPath(sep, path);
				var pathCount = pathParts.length;
				
				for(int i = 0 ; i < pathCount; i++) {
					var pathPart = pathParts[i];
					var matches = 0;
					var matcher = FileSystems.getDefault().getPathMatcher("glob:" + pathPart);
					
					var matchedName = pathPart;
					try {
						for(var it = Files.list(Paths.get(resolved)).iterator(); it.hasNext(); ) {
							var file = it.next();
							if(matcher.matches(file)) {
								matches++;
								matchedName = file.getFileName().toString();
								if(i == pathCount -1) {
									doOp(op, file.toAbsolutePath());
									matched = true;
									if(!multiple) {
										break;
									}
								}
							}
						}
					}
					catch(IOException ioe) {
						throw new UncheckedIOException(ioe);
					}
					
					if(matches == 0 || (matched && !multiple)) {
						break;
					}
					
					resolved = concatenatePaths(resolved, matchedName, sep);
				}
			}
			if(!matched) {
				doOp(op, Paths.get(opath));
				matched = true;
			}
		}
	}
	
	protected String expandRemote(String path) {
		var l = new ArrayList<String>();
		expandRemoteAndDo(p -> {
			l.add(p);
		}, false, path);
		if(l.isEmpty()) {
			return path;
		}
		else {
			return l.get(0);
		}
	}

	protected void expandRemoteAndDo(FileOp op, boolean multiple, String... paths) {
		expandRemoteAndDo(op, multiple, Arrays.asList(paths));
	}
	
	protected void expandRemoteAndDo(FileOp op, boolean multiple, List<String> paths) {

		var cwd = getContainer().getCwd();
		var sep = getContainer().getSeparator();
		for(var opath : paths) {
			
			var matched = false;

			var path = normalPath(opath, sep);
			if(path.startsWith("..")) {
				path = absolutePath(cwd, path, sep);
			}
			
			if(path.equals(String.valueOf(sep))) {
				doOp(op, String.valueOf(sep));
				matched = true;
			}
			else {
				
				var absolute = path.startsWith(String.valueOf(sep));
				var root = absolute ? String.valueOf(sep) : cwd;
				var resolved = root;
				var pathParts = processHome(sep, splitPath(sep, path));
				var pathCount = pathParts.length;
				
				for(int i = 0 ; i < pathCount; i++) {
					var pathPart = pathParts[i];
					var matches = 0;
					var matcher = FileSystems.getDefault().getPathMatcher("glob:" + pathPart);
					
					var matchedName = pathPart;
					try {
						for(var it = getContainer().getMount().list(getContainer().localToNativePath(resolved)).iterator(); it.hasNext(); ) {
							var filename = it.next();
							var fullPath = resolved;
							if(filename.equals(".") || filename.equals("..")) {
								continue;
							}
							if(filename.equals(pathPart) || matcher.matches(Path.of(filename))) {
								fullPath = concatenatePaths(fullPath, filename, sep);
								matches++;
								matchedName = filename;
								if(i == pathCount -1) {
									doOp(op, fullPath);
									matched = true;
									if(!multiple) {
										break;
									}
								}
							}
						}
					}
					catch(IOException ioe) {
						throw new UncheckedIOException(ioe);
					}
					
					if(matches == 0 || (matched && !multiple)) {
						break;
					}
					
					resolved = concatenatePaths(resolved, matchedName, sep);
				}
			}
			
			if(!matched) {
				doOp(op, opath);
				matched = true;
			}
		}
	}

	protected <T> void doOp(VisitOp<T> op, T fullPath) {
		try {
			op.op(fullPath);
		} catch(EOFException ee) {
		} catch (IOException ioe) {
			throw new UncheckedIOException(ioe);
		} catch (RuntimeException re) {
			throw re;
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	protected abstract Integer onCall() throws Exception;

	@SuppressWarnings("unchecked")
	<C extends TNFSContainer> C getContainer() {
		return (C) spec.parent().userObject();
	}

	@Override
	public String[] getVersion() throws Exception {
		return Version.getVersion("uk.co.bithatch", "tnfs-java-cli").split("\\.");
	}

	public FilenameCompletionMode completionMode() {
		return completionMode;
	}
}