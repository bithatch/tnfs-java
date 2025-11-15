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
package uk.co.bithatch.tnfs.cli;

import java.io.IOException;
import java.nio.file.Path;

import org.jline.terminal.Terminal;

import picocli.CommandLine.Model.CommandSpec;
import uk.co.bithatch.tnfs.client.TNFSClient;
import uk.co.bithatch.tnfs.client.TNFSMount;

public interface TNFSContainer {
	
	/**
	 * Gets the current URI.
	 *
	 * @return the mount
	 */
	TNFSURI getURI();
	
	/**
	 * Gets the client.
	 *
	 * @return the mount
	 */
	TNFSClient getClient();
	
	/**
	 * Gets the mount.
	 *
	 * @return the mount
	 */
	TNFSMount getMount();

	/**
	 * Gets the cwd.
	 *
	 * @return the cwd
	 */
	String getCwd();

	/**
	 * Sets the cwd.
	 *
	 * @param path the new cwd
	 */
	void setCwd(String path);
	
	/**
	 * Gets the terminal.
	 *
	 * @return the terminal
	 */
	Terminal getTerminal();
	
	/**
	 * Sets the lcwd.
	 *
	 * @param lcwd the new lcwd
	 */
	void setLcwd(Path lcwd);

	/**
	 * Gets the lcwd.
	 *
	 * @return the lcwd
	 */
	Path getLcwd();

	/**
	 * Gets the command line spec.
	 *
	 * @return the spec
	 */
	CommandSpec getSpec();

	/**
	 * Get path separator in use.
	 * 
	 * @return separator
	 */
	char getSeparator();

	/**
	 * Connect to the URI.
	 * 
	 * @param uri uri
	 * @return mount
	 */
	TNFSClient connect(TNFSURI uri) throws IOException;

	/**
	 * Mount the URI.
	 * 
	 * @param uri uri
	 * @return mount
	 */
	TNFSMount mount(TNFSURI uri) throws IOException;
	
	/**
	 * Connect using default details if not connected.
	 * 
	 * @throws Exception
	 */
	void startIfNotStarted() throws  Exception;

	/**
	 * Translate the path separators from the TNFS forward slashes to whatever
	 * is automatically detected for the platform (or chosen through configuration).
	 * 
	 * @param path
	 * @return translated path
	 */
	String nativeToLocalPath(String path);

	/**
	 * Translate the path separators from those automatically detected for the platform 
	 * (or chosen through configuration) to TNFS forward slashes.
	 * 
	 * @param path
	 * @return translated path
	 */
	String localToNativePath(String path);

	/**
	 * Unmount.
	 * 
	 * @throws IOException on error
	 */
	void unmount() throws IOException;

	/**
	 * Get if mounted.
	 * 
	 * @return mounted
	 */
	boolean isMounted();
}