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
import java.nio.file.Path;
import java.nio.file.attribute.UserPrincipal;
import java.security.Principal;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

import com.sshtools.jini.config.Monitor;

import uk.co.bithatch.tnfs.lib.TNFSFileAccess;
import uk.co.bithatch.tnfs.server.AuthenticationType;
import uk.co.bithatch.tnfs.server.TNFSAuthenticator;
import uk.co.bithatch.tnfs.server.TNFSAuthenticatorFactory;

public class DefaultAuthentication extends AbstractConfiguration implements TNFSAuthenticator, TNFSAuthenticatorFactory {

	private final class ExtendedAuthUser implements UserPrincipal {
		private final String username;
		private final char[] password;

		private ExtendedAuthUser(String username, char[] password) {
			this.username = username;
			this.password = password;
		}

		@Override
		public String getName() {
			return username;
		}

	}

	public DefaultAuthentication(Optional<Path> configurationDir, Optional<Path> userConfigurationDir) {
		this(Optional.empty(), configurationDir, userConfigurationDir);
	}
	
	public DefaultAuthentication(Optional<Monitor> monitor, Optional<Path> configurationDir, Optional<Path> userConfigurationDir) {
		super(DefaultAuthentication.class, "authentication", monitor, configurationDir, userConfigurationDir);
	}

	@Override
	public String name() {
		return "Default";
	}

	@Override
	public Optional<Principal> authenticate(TNFSFileAccess fs, Optional<String> username, Optional<char[]> password) {
		if(username.isPresent()) {
			var uname = username.get();
			var princ = locateUser(uname);
			if(princ.isPresent() && password.isPresent()) {
				var usr = princ.get();
				var pw = password.get();
				if(Arrays.equals(((ExtendedAuthUser)usr).password, pw)) {
					return princ.map(u -> (Principal)u);
				}
			}
		}
		return Optional.empty();
	}

	private Optional<Principal> locateUser(String username) {
		if(Files.exists(resolvePasswdFile())) {
			var props = loadUsers();
			var rec = props.getProperty(username);
			if(rec != null && !rec.equals("")) {
				return Optional.of(new ExtendedAuthUser(username, rec.toCharArray()));
			}
		}
		return Optional.empty();
	}

	public void remove(String username) {
		var users = loadUsers();
		var old = (String)users.remove(username);
		if(old == null) 
			throw new IllegalArgumentException("User does not exist.");
		saveUsers(users);
	}
	
	public void password(String username, char[] password) {
		var users = loadUsers();
		if(!users.containsKey(username))
			throw new IllegalArgumentException("User does not exists.");

		savePassword(username, password, users);
	}
	
	public void add(String username, char[] password) {
		var users = loadUsers();
		if(users.containsKey(username))
			throw new IllegalArgumentException("User already exists.");
		
		savePassword(username, password, users);
		
	}

	public Stream<Principal> users() {
		if(Files.exists(resolvePasswdFile())) {
			return loadUsers().entrySet().
					stream().
					map(ent -> new ExtendedAuthUser((String)ent.getKey(), ((String)ent.getValue()).toCharArray()));
		}
		else
			return Stream.empty();
	}


	private void savePassword(String username, char[] password, Properties users) {
		users.put(username, new String(password));
		saveUsers(users);
	}

	private void saveUsers(Properties users) {
		var passwdFile = resolvePasswdFile();
		try(var in = Files.newBufferedWriter(passwdFile)) {
			users.store(in, "TNFS Extended User Accounts");
		}
		catch(IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
		
		var file = passwdFile.toFile();
        file.setReadable(false, false);
        file.setWritable(false, false);
        file.setExecutable(false, false);
        file.setReadable(true, true);
        file.setWritable(true, true);
	}
	
	private Properties loadUsers() {
		var props = new Properties();
		var pwfile = resolvePasswdFile();
		if(Files.exists(pwfile)) {
			try(var in = Files.newBufferedReader(pwfile)) {
				props.load(in);
			}
			catch(IOException ioe) {
				throw new UncheckedIOException(ioe);
			}
		}
		return props;
	}

	private Path resolvePasswdFile() {
		return resolveDir()
				.resolve("passwd.properties");
	}

	private Path resolveDir() {
		return iniSet.appPathForScope(iniSet.writeScope()
				.orElseThrow(() -> new IllegalStateException("No writable configuration directory found.")));
	}

	@Override
	public Optional<TNFSAuthenticator> createAuthenticator(String path, AuthenticationType... authTypes) {
		return Optional.of(this);
	}

	@Override
	public int priority() {
		return Integer.MIN_VALUE;
	}
}
