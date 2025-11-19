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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import uk.co.bithatch.tnfs.client.TNFSClient;
import uk.co.bithatch.tnfs.client.TNFSMount;
import uk.co.bithatch.tnfs.client.extensions.SecureMount;
import uk.co.bithatch.tnfs.lib.Message;
import uk.co.bithatch.tnfs.lib.Protocol;
import uk.co.bithatch.tnfs.lib.TNFS;
import uk.co.bithatch.tnfs.lib.extensions.Extensions;

/**
 * Abstract command.
 */
public abstract class AbstractTNFSCommand {

	protected final static int MAX_AUTH_ATTEMPTS = Integer.parseInt(System.getProperty("tnfs.maxAuthAttempts", "3"));

	@Option(names = { "-v", "--verbosity" }, description = "Log verbosity. Multiple -v options increase the verbosity.  The  maximum is 4.")
	protected boolean[] verbosity;

	@Option(names = { "-q", "--quiet" }, description = "No output.")
	private boolean quiet;

	@Option(names = { "-t", "--tcp" }, description = "Use TCP instead of UDP. You can also select tcp by using the scheme 'tnfst' in the URI, e.g. tnfst://myserver/path/to/file")
	private boolean tcp;

	@Option(names = { "-R", "--reply-timeout" }, description = "Reply timeout in seconds. A value of zero, means no timeout.")
	private int replyTimeout = TNFS.DEFAULT_REPLY_TIMEOUT_SECONDS;

    @Option(names = { "-X", "--verbose-exceptions" }, description = "Show verbose exception traces on errors.")
    private boolean verboseExceptions;
	
	@Option(names = { "-P", "--port" }, description = "Port number on which the server is listening. You can also select the port in the URI, e.g. tnfs://myserver:12345/mypath")
	private Optional<Integer> port;
	
	@Option(names = { "-B",
			"--batch" }, description = "Selects batch mode (prevents asking for passwords or passphrases).")
	private boolean batch;
	
	@Option(names = { "-S",
			"--secure" }, description = "Use the Secure Mount .")
	private boolean secure;

	@Option(names = { "--paths" }, description = "Whether or not to use WINDOWS styles paths and escaping, or UNIX style. When AUTO, defaults to automatic based on operating system.")
	public PathsMode paths = PathsMode.AUTO;

	@Option(names = { "-M", "--packet-size" }, description = "Maximum size of each message packet. Requires server support for PKTSZ extension.")
	private Optional<Integer> size;
	
	protected Logger log;

	/**
	 * Start the command.
	 * 
	 * @throws SshException
	 * @throws IOException
	 */
	public final void start() throws Exception {
		setupLogging();
		onStart();
	}

	protected void setupLogging() {
		Level level;
		switch (verbosity == null ? 0 : verbosity.length) {
		case 0:
			level = quiet ? Level.ERROR : Level.WARN;
			break;
		case 1:
			level = Level.INFO;
			break;
		case 2:
			level = Level.DEBUG;
			break;
		default:
			level = Level.TRACE;
			break;
		}
        
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", level.name());
		log = LoggerFactory.getLogger(AbstractTNFSCommand.class);
	}

	protected final void error(String text) {
		error(text, null);
	}
	
	protected final void error(String text, Throwable exception) {
		if(!isQuiet()) {
			if(!text.trim().endsWith(".")) {
				text += ". ";
			}
			if(exception == null || exception.getMessage() == null) {
				System.out.format("%s: %s", getSpec().name(), text);
			}
			else {
				System.out.format("%s: %s %s", getSpec().name(), text, exception.getMessage());
			}
			if(exception != null && isVerboseExceptions()) {
				exception.printStackTrace();
			}
		}
	}

	protected final boolean isBatchMode() {
		return batch;
	}

	protected final boolean isQuiet() {
		return quiet;
	}

	protected final boolean isVerboseExceptions() {
		return verboseExceptions;
	}

	protected abstract void onStart() throws Exception;

	protected abstract CommandSpec getSpec();
	
	protected final TNFSMount doConnectAndMount(TNFSURI uri) throws IOException {
		return doMount(uri, doConnect(uri));
	}

	protected final TNFSClient doConnect(TNFSURI uri) throws IOException {
		log.info("Connecting to {}", uri);

		var bldr = new TNFSClient.Builder();
		if(tcp || uri.protocol() == Protocol.TCP)
			bldr.withTcp();
		bldr.withHostname(uri.hostname());
		bldr.withPort(port.or(() -> uri.port()).orElse(TNFS.DEFAULT_PORT));
		if(replyTimeout == 0)
			bldr.withoutTimeout();
		else
			bldr.withTimeout(Duration.ofSeconds(replyTimeout));
		
		return bldr.build();
	}

	protected final TNFSMount doMount(TNFSURI uri, TNFSClient client) throws IOException {
		var username = uri.username().orElse("");
		for(var i = 0 ; i <= MAX_AUTH_ATTEMPTS ; i++) {
			
			try {
				var mntPath = uri.mount().orElse("");
				
				if(secure) {

					var mntBldr = client.extension(SecureMount.class).mount(mntPath);
					if(username.equals("")) {
						username = promptForUsername();
					}
					
					if(!username.equals("")) {
						mntBldr.withUsername(username);
						if(uri.password().isPresent() && i == 0) {
							mntBldr.withPassword(uri.password().get());	
						}
						else {
							mntBldr.withPassword(promptForPassword());
						}
					}

					return setupMount(client, mntBldr.build());
				}
				else {
				
					var mntBldr = client.mount(mntPath);
					if(username.equals("") && i > 0) {
						username = promptForUsername();
					}
					
					if(!username.equals("")) {
						mntBldr.withUsername(username);
						if(uri.password().isPresent() && i == 0) {
							mntBldr.withPassword(uri.password().get());	
						}
						else {
							mntBldr.withPassword(promptForPassword());
						}
					}

					return setupMount(client, mntBldr.build());
				}
			}
			catch(AccessDeniedException ade) { 
				username = "";
			}
		}
		throw new IllegalStateException("Too many authentication attempts.");
	}

	protected TNFSMount setupMount(TNFSClient client, TNFSMount mnt) throws IOException {
		if(size.isPresent()) {
			try {
				var res = client.sendMessage(mnt, Extensions.PKTSZ, Message.of(mnt.sessionId(), Extensions.PKTSZ, new Extensions.PktSize(size.get())));
				client.size(res.size());
			}
			catch(Exception e) {
				error("Failed to set packet size.", e);
			}
		}
		
		
		return mnt;
	}
	
	protected char[] promptForPassword() {
		return prompt(true, "Password:").toCharArray();
	}
	
	protected String promptForUsername() {
		return prompt(false, "Username:");
	}
	
	protected String prompt(String message, Object... args) {
		return prompt(false, message, args);
	}
	
	protected String prompt(boolean password, String message, Object... args) {
		if(batch)
			throw new IllegalStateException("In batch mode, input cannot be prompted for, but input was required.");
		var rdr = System.console();
		if(rdr == null) {
			try {
				System.out.print(MessageFormat.format(message, args));
				var line = new BufferedReader(new InputStreamReader(System.in)).readLine();
				if(line == null)
					throw new IllegalStateException("Password required, but no standard input.");
				return line == null ? "" : line;
			}
			catch(IOException ioe) {
				throw new UncheckedIOException(ioe);
			}
		}
		else {
			if(password) {
				var chs = rdr.readPassword(MessageFormat.format(message, args));
				if(chs == null)
					throw new IllegalStateException("Aborted.");
				return chs == null ? "" : new String(chs);
			}
			else {
				var chs = rdr.readLine(MessageFormat.format(message, args));
				if(chs == null)
					throw new IllegalStateException("Aborted.");
				return chs == null ? "" : new String(chs);
			}
		}
	}

	final boolean isRemotePath(String path) {
		try {
			TNFSURI.parsePath(path);
			return true;
		}
		catch(IllegalArgumentException iae) {
			return false;
		}
	}
}
