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

import java.nio.file.Path;
import java.util.Optional;

import com.sshtools.jini.config.Monitor;

public class Authentication extends AbstractConfiguration {

	public Authentication(Optional<Path> configurationDir, Optional<Path> userConfigurationDir) {
		this(Optional.empty(), configurationDir, userConfigurationDir);
	}
	
	public Authentication(Optional<Monitor> monitor, Optional<Path> configurationDir, Optional<Path> userConfigurationDir) {
		super(Authentication.class, "authentication", monitor, configurationDir, userConfigurationDir);
	}

	public Path passwdFile() {
		return iniSet.appPathForScope(iniSet.writeScope()
				.orElseThrow(() -> new IllegalStateException("No writable configuration directory found."))).resolve("password.properties");
	}
}
