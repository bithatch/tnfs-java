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

import static uk.co.bithatch.tnfs.lib.Io.prompt;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.Callable;

import com.ongres.scram.common.ScramMechanism;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import uk.co.bithatch.tnfs.daemon.ExceptionHandler.ExceptionHandlerHost;
import uk.co.bithatch.tnfs.lib.AppLogLevel;
import uk.co.bithatch.tnfs.lib.extensions.Crypto;

@Command(name = "tnfs-user", description = "Manage TNFS users.", mixinStandardHelpOptions = true,
	subcommands = { TNFSUser.AddUser.class, TNFSUser.RemoveUser.class, TNFSUser.Password.class, TNFSUser.List.class })
public class TNFSUser implements Callable<Integer>, ExceptionHandlerHost {


    public static void main(String[] args) throws Exception {
        var cmd = new TNFSUser();
        System.exit(new CommandLine(cmd).setExecutionExceptionHandler(new ExceptionHandler(cmd)).execute(args));
    }

    @Option(names = { "-L", "--log-level" }, paramLabel = "LEVEL", description = "Logging level for trouble-shooting.")
    private Optional<AppLogLevel> level;
    
    @Option(names = { "-C", "--configuration" }, description = "Location of system configuration. By default, will either be the systems default global configuration directory or a user configuration directory.")
    private Optional<Path> configuration;
    
    @Option(names = { "-O", "--override-configuration" }, description = "Location of user override configuration. By default, will be a configuration directory in the users home directory or the users home directory.")
    private Optional<Path> userConfiguration;

    @Option(names = { "-X", "--verbose-exceptions" }, description = "Show verbose exception traces on errors.")
    private boolean verboseExceptions;
    
    @Spec
    private CommandSpec spec;

	private Authentication authenticator;
    

    @Override
    public Integer call() throws Exception {
		throw new IllegalStateException("Choose a sub-command (see --help).");
    }


	@Override
	public CommandSpec spec() {
		return spec;
	}


	@Override
	public boolean verboseExceptions() {
		return false;
	}
	
	public Authentication authenticator() {
		if(authenticator == null) {
			
	    	/* Logging */
			System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", level.orElse(AppLogLevel.WARN).name());
			
			var gconfig = configuration;
			
			if(configuration.isEmpty() && userConfiguration.isEmpty() && System.getProperty("java.home") == null) {
				var prchndl = ProcessHandle.current();
				var cmdor = prchndl.info().command();
				if(cmdor.isPresent()) {
					gconfig = Optional.of(Paths.get(cmdor.get()).getParent().resolve("etc"));
				}
			}
			
			authenticator = new Authentication(gconfig, userConfiguration);
		}
		return authenticator;
	}
	
	@Command
	protected static abstract class UserCommand  implements Callable<Integer> {

		@Spec
		private CommandSpec spec;
		
		protected TNFSUser parent() {
			return (TNFSUser)spec.parent().userObject();
		}
	}

	@Command(name = "add", aliases = { "create", "new" }, description = "Add a new user.", mixinStandardHelpOptions = true)	
	public final static class AddUser extends UserCommand {
		
		@Option(names = { "-m" ,"--mech", "--mechanism"}, description = "The scram mechansimm to use.")
		private ScramMechanism mech = Crypto.DEFAULT_MECHANISM;
		
		@Parameters(arity = "1", description = "The username to use for the new user.")
		private String username;
		

		@Override
		public Integer call() throws Exception {
			var auth = parent().authenticator();
			for(int i = 0 ; i < 3 ; i++) {
				var pw = prompt(true, "New Password: ");
				if(pw.equals("")) {
					throw new IllegalStateException("Aborted.");
				}
				var confirmPw = prompt(true, "Confirm Password: ");
				if(pw.equals(confirmPw)) {
					auth.add(username, mech, Crypto.DEFAULT_ITERATIONS, pw.toCharArray());
					return 0;
				}
			}
			throw new IllegalStateException("Too many attempts.");
		}
		
	}

	@Command(name = "remove", aliases = { "rm", "delete", "del" }, description = "Remove an existing user.", mixinStandardHelpOptions = true)	
	public final static class RemoveUser extends UserCommand {
		
		@Option(names = { "-y" ,"--yes"}, description = "Do not ask for confirmation, just remove it.")
		private boolean yes;

		
		@Parameters(arity = "1", description = "The username to remove.")
		private String username;

		@Override
		public Integer call() throws Exception {
			if(!yes) {
				System.out.format("Are you sure you wish to remove the user `%s`%n", username);
				var res = prompt(false, "[Y]es/[N]o: ").toLowerCase();
				if(res.equals("y") || res.equals("yes")) {
					yes = true;
				}
			}
			
			if(yes) {
				parent().authenticator().remove(username);
				return 0;
			}
			else {
				throw new IllegalStateException("Aborted.");
			}
			
		}
		
	}

	@Command(name = "password", aliases = { "passwd", "pwd", "pass" }, description = "Set a users password.", mixinStandardHelpOptions = true)	
	public final static class Password extends UserCommand {
		
		@Parameters(arity = "1", description = "The username to set password for.")
		private String username;

		@Override
		public Integer call() throws Exception {

			var auth = parent().authenticator();
			for(int i = 0 ; i < 3 ; i++) {
				var pw = prompt(true, "New Password: ");
				if(pw.equals("")) {
					throw new IllegalStateException("Aborted.");
				}
				var confirmPw = prompt(true, "Confirm Password: ");
				if(pw.equals(confirmPw)) {
					auth.password(username, pw.toCharArray());
					return 0;
				}
			}
			throw new IllegalStateException("Too many attempts.");
			
		}
		
	}

	@Command(name = "list", aliases = { "ls", "users" }, description = "List all users.", mixinStandardHelpOptions = true)	
	public final static class List extends UserCommand {
		
		@Parameters(arity = "1", description = "The username to set password for.")
		private String username;

		@Override
		public Integer call() throws Exception {
			var auth = parent().authenticator();
			auth.users().forEach(u -> System.out.println(u.getName()));
			return 0;
		}
		
	}
}
