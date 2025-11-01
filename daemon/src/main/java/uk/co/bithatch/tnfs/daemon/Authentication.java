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
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ongres.scram.common.ScramFunctions;
import com.ongres.scram.common.ScramMechanism;
import com.ongres.scram.common.StringPreparation;
import com.sshtools.jini.config.Monitor;

import uk.co.bithatch.tnfs.lib.TNFSFileAccess;
import uk.co.bithatch.tnfs.lib.Util;
import uk.co.bithatch.tnfs.lib.extensions.Crypto;
import uk.co.bithatch.tnfs.server.extensions.ScramPrincipal;
import uk.co.bithatch.tnfs.server.extensions.ScramTNFSAuthenticator;

public class Authentication extends AbstractConfiguration implements ScramTNFSAuthenticator {

	private static Logger LOG = LoggerFactory.getLogger(Authentication.class);
	
	private final class ExtendedAuthUser implements ScramPrincipal {
		private final String username;
		private final String[] arr;

		private ExtendedAuthUser(String username, String record) {
			this.username = username;
			this.arr = record.split(":");
		}

		@Override
		public String getName() {
			return username;
		}

		@Override
		public String getSalt() {
			return arr[2];
		}

		@Override
		public String getStoredKey() {
			return arr[3];
		}

		@Override
		public int getIterationCount() {
			return Integer.parseInt(arr[1]);
		}

		@Override
		public ScramMechanism getMechanism() {
			return ScramMechanism.valueOf(arr[0]);
		}
	}

	public Authentication(Optional<Path> configurationDir, Optional<Path> userConfigurationDir) {
		this(Optional.empty(), configurationDir, userConfigurationDir);
	}
	
	public Authentication(Optional<Monitor> monitor, Optional<Path> configurationDir, Optional<Path> userConfigurationDir) {
		super(Authentication.class, "authentication", monitor, configurationDir, userConfigurationDir);
		
		var srvkey = resolveServerKey();
		if(!Files.exists(srvkey)) {
			try(var out = new PrintWriter(Files.newBufferedWriter(srvkey), true)) {
				out.println(Base64.getEncoder().encodeToString(Util.genRandom(Crypto.SERVER_KEY_SIZE)));
			}
			catch(IOException ioe) {
				throw new UncheckedIOException(ioe);
			}
			LOG.info("Generated server key to {}.", srvkey);
		}
	}

	@Override
	public Optional<ScramPrincipal> identify(TNFSFileAccess fs, String username) {
		return locateUser(username);
	}

	@Override
	public Optional<Principal> authenticate(TNFSFileAccess fs, Optional<String> username, Optional<char[]> password) {
		if(username.isPresent()) {
			var uname = username.get();
			var princ = locateUser(uname);
			if(princ.isPresent() && password.isPresent()) {
				var usr = princ.get();
				var pw = password.get();
				var saltedPw = ScramFunctions.saltedPassword(usr.getMechanism(), StringPreparation.NO_PREPARATION, pw, Base64.getDecoder().decode(usr.getSalt()), usr.getIterationCount());
				if(Arrays.equals(Base64.getDecoder().decode(usr.getStoredKey()), saltedPw)) {
					return princ.map(u -> (Principal)u);
				}
			}
		}
		return Optional.empty();
	}

	private Optional<ScramPrincipal> locateUser(String username) {
		if(Files.exists(resolvePasswdFile())) {
			var props = loadUsers();
			var rec = props.getProperty(username);
			if(rec != null && !rec.equals("")) {
				return Optional.of(new ExtendedAuthUser(username, rec));
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

		var rec = users.getProperty(username);
		var arr = rec.split(":");
		var mech = ScramMechanism.valueOf(arr[0]);
		var iterations = Integer.valueOf(arr[1]);
		savePassword(username, password, users, mech, iterations);
		
	}
	
	public void add(String username, ScramMechanism mech, int iterations, char[] password) {
		var users = loadUsers();
		if(users.containsKey(username))
			throw new IllegalArgumentException("User already exists.");
		
		savePassword(username, password, users, mech, iterations);
		
	}

	@Override
	public byte[] serverKey() {
		var keypath = resolveServerKey();
		try(var in = Files.newBufferedReader(keypath)) {
			return Base64.getDecoder().decode(in.readLine());	
		}
		catch(IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}

	public Stream<ScramPrincipal> users() {
		if(Files.exists(resolvePasswdFile())) {
			return loadUsers().entrySet().
					stream().
					map(ent -> new ExtendedAuthUser((String)ent.getKey(), ((String)ent.getValue())));
		}
		else
			return Stream.empty();
	}

	private Path resolveServerKey() {
		return resolveDir().resolve(document().section(Constants.AUTHENTICATION_SECTION).get(Constants.KEY_PATH_KEY));
	}

	private void savePassword(String username, char[] password, Properties users, ScramMechanism mech,
			Integer iterations) {
		var salt = ScramFunctions.salt(Crypto.SALT_SIZE, Crypto.random());
		var saltedPw = ScramFunctions.saltedPassword(mech, StringPreparation.NO_PREPARATION, password, salt, iterations);
		var storedKey = ScramFunctions.storedKey(mech, saltedPw);
		
		users.put(username, 
			String.format("%s:%d:%s:%s", 
				mech.name(), 
				iterations,
				Base64.getEncoder().encodeToString(salt),
				Base64.getEncoder().encodeToString(storedKey)
			)
		);
		
		saveUsers(users);
	}

	private void saveUsers(Properties users) {
		try(var in = Files.newBufferedWriter(resolvePasswdFile())) {
			users.store(in, "TNFS Extended User Accounts");
		}
		catch(IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
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
}
