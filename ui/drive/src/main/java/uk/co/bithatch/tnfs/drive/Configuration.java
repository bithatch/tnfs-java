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
package uk.co.bithatch.tnfs.drive;

import java.io.File;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.jini.INI;
import com.sshtools.jini.config.INISet;
import com.sshtools.jini.config.INISet.Scope;
import com.sshtools.jini.config.Monitor;
import com.sshtools.jini.schema.INISchema;

public final class Configuration {
	
	public static final String APPNAME = "drive";

	static Logger LOG = LoggerFactory.getLogger(Configuration.class);

	private final INISet iniSet;
	private final INI ini;

	Configuration(Monitor monitor) {

		var builder = new INISet.Builder(APPNAME).
				withApp(APPNAME).
				withMonitor(monitor);
		
		iniSet = builder.
				withSchema(Configuration.class, "Configuration.schema.ini").build();

		ini = iniSet.document();
	}
	
	public INI document() {
		return ini;
	}
	
	public String mountPath() {
		var pathStr = ini.get(Constants.MOUNT_PATH);
		return decodeMountPath(pathStr);
	}

	public static String decodeMountPath(String pathStr) {
		if(pathStr.startsWith("~/") || pathStr.startsWith("~\\"))
			return System.getProperty("user.home") + File.separator + pathStr.substring(2).replace('/', File.separatorChar).replace('\\', File.separatorChar);
		else
			return pathStr;
	}

	public static String encodeMountPath(String pathStr) {
		return pathStr.replace(System.getProperty("user.home"), "~").replace('\\', '/');
	}
	
	public INISchema schema() {
		return iniSet.schema();
	}

	public Path dir() {
		return iniSet.appPathForScope(Scope.USER);
	}

}
