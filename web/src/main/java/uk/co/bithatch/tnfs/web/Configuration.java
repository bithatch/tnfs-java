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
package uk.co.bithatch.tnfs.web;


import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.jini.INI;
import com.sshtools.jini.INI.Section;
import com.sshtools.jini.config.INISet;
import com.sshtools.jini.config.INISet.CreateDefaultsMode;
import com.sshtools.jini.config.INISet.Scope;
import com.sshtools.jini.config.Monitor;

public final class Configuration {
	static Logger LOG = LoggerFactory.getLogger(Configuration.class);

	private final INISet iniSet;
	private final INI ini;

	public Configuration(Monitor monitor) {
		var bldr =  new INISet.Builder("tnfs-web").
				withApp("tnfs-web").
				withCreateDefaults(CreateDefaultsMode.valueOf(System.getProperty("tnfs-web.create-defauls-mode", "NONE"))).
				withSchema(Configuration.class);
		
		var config = System.getProperty("tnfs-web.configuration", 
			Boolean.getBoolean("tnfs-web.dev") ? "etc" : ""
		);
		if(!config.equals("")) {
			try {
				var dir = Paths.get(config);
				if(!Files.exists(dir))
					throw new NoSuchFileException(config);
				if(!Files.isDirectory(dir))
					throw new NotDirectoryException(config);
				bldr.withScopes(Scope.USER);
				bldr.withPath(Scope.USER, dir);
			}
			catch(IOException ioe) {
				throw new UncheckedIOException(ioe);
			}
		}
		
		if(monitor != null)
			bldr.withMonitor(monitor);
		
		iniSet = bldr.build();

		ini = iniSet.document();

		iniSet.scopes().forEach(sc -> {
			LOG.info("Configuration for {} in {}",  sc, iniSet.appPathForScope(sc));
		});
	}
	
	public INI document() {
		return ini;
	}
	
	public Section mountConfiguration() {
		return ini.obtainSection(Constants.MOUNTS_SECTION);
	}
	
	public Set<Section> mounts() {
		return Set.of(ini.allSectionsOr(Constants.MOUNT_SECTION).orElse(new Section[0]));
	}

	public Section server() {
		return ini.section(Constants.SERVER_SECTION);
	}

	public Section http() {
		return ini.section(Constants.HTTP_SECTION);
	}

	public Section https() {
		return ini.section(Constants.HTTPS_SECTION);
	}

	public Section tuning() {
		return ini.obtainSection(Constants.TUNING_SECTION);
	}

	public Section ncsa() {
		return ini.obtainSection(Constants.NCSA_SECTION);
	}

}
