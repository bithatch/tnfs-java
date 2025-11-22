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

import java.util.Optional;

import org.slf4j.event.Level;

import com.sshtools.jajafx.JajaApp;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Option;
import uk.co.bithatch.nativeimage.annotations.Bundle;
import uk.co.bithatch.nativeimage.annotations.Resource;

@Bundle
@Resource(siblings = true)
@Command(name = "tnfs-drive", mixinStandardHelpOptions = true, description = "TNFS File System Mounter.", versionProvider = Drive.Version.class)
public class Drive extends JajaApp<DriveApp, DriveAppWindow> {

	@Option(names = { "-L", "--log-level" }, description = "Logging level.")
	private Optional<Level> logLevel;
 
	public final static class FUSEMOUNTBuilder extends JajaAppBuilder<Drive, FUSEMOUNTBuilder, DriveApp> {

		public static FUSEMOUNTBuilder create() {
			return new FUSEMOUNTBuilder();
		}

		private FUSEMOUNTBuilder() {
		}

		@Override
		public Drive build() {
			return new Drive(this);
		}
	}

	public final static class Version implements IVersionProvider {

		@Override
		public String[] getVersion() throws Exception {
			return version();
		}

	}

	public static void main(String[] args) {
		var bldr = FUSEMOUNTBuilder.create().withInceptionYear(2025).withApp(DriveApp.class)
				.withAppResources(DriveApp.RESOURCES);
		System.exit(new CommandLine(bldr.build()).execute(args));
	}

	Drive(FUSEMOUNTBuilder builder) {
		super(builder);
	}

	@Override
	protected void initCall() throws Exception {
		logLevel.ifPresent(l -> { 
			System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", l.name()); 
		});
	}

	public static String[] version() {
		return new String[] { uk.co.bithatch.tnfs.lib.Version.getVersion("uk.co.bithatch", "tnfs-java-fusemount") };
	}

}
