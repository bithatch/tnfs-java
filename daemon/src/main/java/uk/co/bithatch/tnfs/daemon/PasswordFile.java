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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;
import java.util.stream.Stream;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Very simple passwd-like database backed by a .properties file.
 *
 * File format:
 *   username = algorithm:iterations:base64(salt):base64(hash)
 *
 * Uses PBKDF2WithHmacSHA256 via standard JCE.
 */
public class PasswordFile {

    private static final String DEFAULT_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int DEFAULT_ITERATIONS = 120_000;
    private static final int KEY_LENGTH_BITS = 256;   // length of derived key
    private static final int SALT_LENGTH_BYTES = 16;  // 128-bit salt

    private final Path file;
    private final SecureRandom random = new SecureRandom();
	
    public PasswordFile(Path file) {
        this.file = file;
    }

    // ---------- Public API ----------


	/**
	 * Get all of the known usernames.
	 * 
	 * @return usernames
	 */
	public Stream<String> users() {
		return loadPasswords().keySet().stream().map(s -> (String)s);
	}

    /**
     * Get if a user exists.
     * 
     * @param username username
     * @return exists
     */
    public boolean exists(String username) {
    	return loadPasswords().containsKey(username);
    }
    

    /**
     * Adds a new user. Fails if the user already exists.
     * 
     * @param username username
     * @param password password
     */
    public synchronized void add(String username, char[] password) {
    	var props = loadPasswords();
        if (props.containsKey(username)) {
            throw new IllegalArgumentException("User already exists: " + username);
        }
        var stored = hashPasswordForStorage(password);
        props.setProperty(username, stored);
        save(props);
    }

    /**
     * Removes an existing user. Does nothing if the user does not exist.
     * 
     * @param username username
     */
    public synchronized void remove(String username) throws IOException {
    	var props = loadPasswords();
        if (props.remove(username) != null) {
            save(props);
        }
        else {
            throw new IllegalArgumentException("User does not exist: " + username);
        }
    }

    /**
     * Changes the password of an existing user.
     * 
     * @param username username
     * @param newPassword new password
     */
    public synchronized void password(String username, char[] newPassword)
            throws IOException, GeneralSecurityException {

    	var props = loadPasswords();
        if (!props.containsKey(username)) {
            throw new IllegalArgumentException("User does not exist: " + username);
        }
        var stored = hashPasswordForStorage(newPassword);
        props.setProperty(username, stored);
        save(props);
    }

    /**
     * Verifies that the given password matches the stored hash for the user.
     *
     * @return true if the user exists and the password is correct; false otherwise.
     */
    public synchronized boolean verifyUser(String username, char[] password) {

    	var props = loadPasswords();
        var stored = props.getProperty(username);
        if (stored == null) {
            return false; // user not found
        }

       var parts = stored.split(":");
        if (parts.length != 4) {
            // malformed entry
            return false;
        }

        var algorithm = parts[0];
        int iterations;
        try {
            iterations = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }

        var salt = Base64.getDecoder().decode(parts[2]);
        var expectedHash = Base64.getDecoder().decode(parts[3]);

        try {
	        var actualHash = pbkdf2(
	                password,
	                salt,
	                iterations,
	                expectedHash.length * 8,
	                algorithm
	        );
	
	        return constantTimeEquals(expectedHash, actualHash);
        }
        catch(GeneralSecurityException gse) {
        	throw new IllegalStateException("Failed to hash.", gse);
        }
    }

    // ---------- Internal helpers ----------

    private synchronized void save(Properties props) {
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "Simple passwd DB");
        }
        catch(IOException ioe) {
        	throw new UncheckedIOException(ioe);
        }		
        
		var file = this.file.toFile();
        file.setReadable(false, false);
        file.setWritable(false, false);
        file.setExecutable(false, false);
        file.setReadable(true, true);
        file.setWritable(true, true);
    }

    private String hashPasswordForStorage(char[] password) {
        var salt = new byte[SALT_LENGTH_BYTES];
        random.nextBytes(salt);
        
        try {

	        var hash = pbkdf2(
	                password,
	                salt,
	                DEFAULT_ITERATIONS,
	                KEY_LENGTH_BITS,
	                DEFAULT_ALGORITHM
	        );
	
	        return DEFAULT_ALGORITHM + ":" +
	               DEFAULT_ITERATIONS + ":" +
	               Base64.getEncoder().encodeToString(salt) + ":" +
	               Base64.getEncoder().encodeToString(hash);
        }
        catch(GeneralSecurityException gse) {
        	throw new IllegalStateException("Failed to hash.", gse);
        }
    }

    private static byte[] pbkdf2(char[] password,
                                 byte[] salt,
                                 int iterations,
                                 int keyLengthBits,
                                 String algorithm) throws GeneralSecurityException {
        var spec = new PBEKeySpec(password, salt, iterations, keyLengthBits);
        try {
            var skf = SecretKeyFactory.getInstance(algorithm);
            return skf.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * Constant-time comparison to avoid timing attacks.
     */
    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

	private Properties loadPasswords() {
	    var props = new Properties();
		if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            }
            catch(IOException ioe) {
            	throw new UncheckedIOException(ioe);
            }
        }
		return props;
	}
}
