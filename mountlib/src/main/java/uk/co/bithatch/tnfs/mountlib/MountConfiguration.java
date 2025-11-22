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
package uk.co.bithatch.tnfs.mountlib;


import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.jini.INI;
import com.sshtools.jini.INI.Section;
import com.sshtools.jini.INIReader;
import com.sshtools.jini.config.INISet;
import com.sshtools.jini.config.INISet.CreateDefaultsMode;
import com.sshtools.jini.config.INISet.Scope;
import com.sshtools.jini.config.Monitor;

public final class MountConfiguration {
	static Logger LOG = LoggerFactory.getLogger(MountConfiguration.class);

	private final INISet iniSet;
	private final INI ini;

	private Section mountConfiguration;

	public MountConfiguration(String appName, String configName, Monitor monitor, Optional<Path> configDir, Optional<Path> userConfigDir) {
		var bldr =  new INISet.Builder(configName).
				withApp(appName).
				withReaderFactory(() ->
					new INIReader.Builder().
						withInterpolator((data, k) -> {
							if(k.startsWith("env:")) {
								var vk = k.substring(4);
								var idx = vk.indexOf(":-");
								String defaultVal = null;
								if(idx != -1) {
									defaultVal = vk.substring(idx + 2);
									vk = vk.substring(0, idx);
								}
								var val = System.getenv(vk);
								if(val == null) {
									return defaultVal;
								}
								else {
									return val;
								}
							}
							else
								return null;
						})
				).
				withSystemPropertyOverrides(true).
				withCreateDefaults(CreateDefaultsMode.valueOf(System.getProperty("tnfsjd-web.create-defaults-mode", "NONE"))).
				withSchema(MountConfiguration.class);
		

		
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
		

		if(!scopes.isEmpty())
			bldr.withScopes(scopes.toArray(new Scope[0]));
		
		if(monitor != null)
			bldr.withMonitor(monitor);
		
		iniSet = bldr.build();

		ini = iniSet.document();
		mountConfiguration = ini.obtainSection(MountConstants.MOUNTS_SECTION);

		iniSet.scopes().forEach(sc -> {
			LOG.info("Configuration for {} in {}",  sc, iniSet.appPathForScope(sc));
		});
	}
	
	public INI document() {
		return ini;
	}
	
	public Section mountConfiguration() {
		return mountConfiguration;
	}
	
	public Set<Section> mounts() {
		return Set.of(ini.allSectionsOr(MountConstants.MOUNT_SECTION).orElse(new Section[0]));
	}

}
