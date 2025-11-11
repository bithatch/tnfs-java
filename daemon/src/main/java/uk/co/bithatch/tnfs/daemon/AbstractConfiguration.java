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
package uk.co.bithatch.tnfs.daemon;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.jini.INI;
import com.sshtools.jini.config.INISet;
import com.sshtools.jini.config.INISet.CreateDefaultsMode;
import com.sshtools.jini.config.INISet.Scope;
import com.sshtools.jini.config.Monitor;

public abstract class AbstractConfiguration {
	private static Logger LOG = LoggerFactory.getLogger(Configuration.class);
	
	private final INI ini;
	
	protected final INISet iniSet;

	protected AbstractConfiguration(Class<?> schema, String cfgName, 
			Optional<Monitor> monitor, 
			Optional<Path> configDir, 
			Optional<Path> userConfigDir) {
		var bldr =  new INISet.Builder(cfgName).
				withApp("tnfsjd").
				withCreateDefaults(CreateDefaultsMode.valueOf(System.getProperty(cfgName + ".create-defaults-mode", "NONE"))).
				withSchema(schema);
		
		var scopes = new ArrayList<Scope>();
		
		configDir.ifPresent(dir -> {
			try {
				if(!Files.exists(dir))
					throw new NoSuchFileException(dir.toString());
				if(!Files.isDirectory(dir))
					throw new NotDirectoryException(dir.toString());
				scopes.add(Scope.GLOBAL);
				bldr.withPath(Scope.GLOBAL, dir);
			}
			catch(IOException ioe) {
				throw new UncheckedIOException(ioe);
			}
		});
		
		userConfigDir.ifPresent(dir -> {
			try {
				if(!Files.exists(dir))
					throw new NoSuchFileException(dir.toString());
				if(!Files.isDirectory(dir))
					throw new NotDirectoryException(dir.toString());
				bldr.withPath(Scope.USER, dir);
				scopes.add(Scope.USER);
			}
			catch(IOException ioe) {
				throw new UncheckedIOException(ioe);
			}
		});
		

		if(!scopes.isEmpty()) {
			bldr.withScopes(scopes.toArray(new Scope[0]));
			bldr.withWriteScope(scopes.get(0));
		}
		
		monitor.ifPresent(bldr::withMonitor);
		
		iniSet = bldr.build();

		ini = iniSet.document();

		iniSet.scopes().forEach(sc -> {
			LOG.info("Configuration for {} {} in {}", cfgName, sc, iniSet.appPathForScope(sc));
		});
	}
	
	public final INI document() {
		return ini;
	}
}
