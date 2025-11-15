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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.jline.builtins.Completers;
import org.jline.builtins.Styles;
import org.jline.console.impl.SystemRegistryImpl;
import org.jline.keymap.KeyMap;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.Reference;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.StyleResolver;
import org.jline.widget.TailTipWidgets;

import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.shell.jline3.PicocliCommands;
import picocli.shell.jline3.PicocliCommands.PicocliCommandsFactory;
import uk.co.bithatch.tnfs.cli.commands.Bye;
import uk.co.bithatch.tnfs.cli.commands.Cd;
import uk.co.bithatch.tnfs.cli.commands.ChkSum;
import uk.co.bithatch.tnfs.cli.commands.Cp;
import uk.co.bithatch.tnfs.cli.commands.Df;
import uk.co.bithatch.tnfs.cli.commands.Get;
import uk.co.bithatch.tnfs.cli.commands.Help;
import uk.co.bithatch.tnfs.cli.commands.Lcd;
import uk.co.bithatch.tnfs.cli.commands.Lpwd;
import uk.co.bithatch.tnfs.cli.commands.Ls;
import uk.co.bithatch.tnfs.cli.commands.MGet;
import uk.co.bithatch.tnfs.cli.commands.MPut;
import uk.co.bithatch.tnfs.cli.commands.Mkdir;
import uk.co.bithatch.tnfs.cli.commands.Mount;
import uk.co.bithatch.tnfs.cli.commands.Mounts;
import uk.co.bithatch.tnfs.cli.commands.MsgSize;
import uk.co.bithatch.tnfs.cli.commands.Mv;
import uk.co.bithatch.tnfs.cli.commands.Put;
import uk.co.bithatch.tnfs.cli.commands.Pwd;
import uk.co.bithatch.tnfs.cli.commands.Rm;
import uk.co.bithatch.tnfs.cli.commands.Rmdir;
import uk.co.bithatch.tnfs.cli.commands.Stat;
import uk.co.bithatch.tnfs.cli.commands.TNFSTPCommand;
import uk.co.bithatch.tnfs.client.TNFSClient;
import uk.co.bithatch.tnfs.client.TNFSMount;
import uk.co.bithatch.tnfs.lib.Command.Entry;
import uk.co.bithatch.tnfs.lib.DirEntryFlag;
import uk.co.bithatch.tnfs.lib.Util;

/**
 * SFTP-like interface client for accessing TNFS resources.
 */
@Command(name = "tnfstp", mixinStandardHelpOptions = true, description = "Trivial Network File System Transfer Program.", subcommands = {
		Ls.class, Cd.class, Pwd.class, Lcd.class, Lpwd.class, Mkdir.class, Rmdir.class, Mv.class, Rm.class,
		Get.class, MGet.class, Put.class, MPut.class, Bye.class, Df.class, Cp.class, ChkSum.class, Mounts.class, Mount.class, 
		MsgSize.class, Stat.class, Help.class })
public final class TNFSTP extends AbstractTNFSFilesCommand implements Callable<Integer>, TNFSContainer {

	public enum FilenameCompletionMode {
		DIRECTORIES_REMOTE, DIRECTORIES_REMOTE_THEN_LOCAL, DIRECTORIES_LOCAL, DIRECTORIES_LOCAL_THEN_REMOTE, 
		REMOTE, REMOTE_THEN_LOCAL, LOCAL, LOCAL_THEN_REMOTE, NONE
	}
	
	/**
	 * Entry point.
	 * 
	 * @param args command line arguments
	 * @throws Exception on error
	 */
	public static void main(String[] args) throws Exception {
		var client = new TNFSTP();
        System.exit(new CommandLine(client).setExecutionExceptionHandler(new ExceptionHandler(client)).execute(args));
	}

	/**
	 * Parse a space separated string into a list, treating portions quotes with
	 * single quotes as a single element. Single quotes themselves and spaces can be
	 * escaped with a backslash.
	 * 
	 * @param command command to parse
	 * @return parsed command
	 */
	public static List<String> parseQuotedString(String command, boolean windowsParsing) {
		var args = new ArrayList<String>();
		var escaped = false;
		var quoted = false;
		var word = new StringBuilder();
		for (int i = 0; i < command.length(); i++) {
			char c = command.charAt(i);
			if (c == '"' && !escaped) {
				if (quoted) {
					quoted = false;
				} else {
					quoted = true;
				}
			} else if (((c == '\\' && !windowsParsing) || (c == '^' && windowsParsing) )  && !escaped) {
				escaped = true;
			} else if (c == ' ' && !escaped && !quoted) {
				if (word.length() > 0) {
					args.add(word.toString().trim());
					word.setLength(0);
					;
				}
			} else {
				word.append(c);
				escaped = false;
			}
		}
		if (word.length() > 0)
			args.add(word.toString().trim());
		return args;
	}

	public final static class CompletionMode {

		@Option(names = { "-nc", "--no-completion" }, description = "Turn off filename completion entirely.")
		public boolean noCompletion;

		@Option(names = { "-nr", "--no-remote-completion" }, description = "Turn off remote  filename completion.")
		public boolean noRemoteCompletion;
	}
	
	@ArgGroup(multiplicity = "0..1")
	private CompletionMode completion;

	@Parameters(index = "0", description = "Destination.", paramLabel = "Remote TNFS URI.")
	protected String destination;

	@Spec 
	private CommandSpec spec;

	private String cwd;
	private boolean exitWhenDone;
	private Path lcwd = Paths.get(System.getProperty("user.dir"));
	private TNFSMount mount;
	private TNFSClient client;
	private TNFSURI uri;


//	@Override
//	public FileTransferHost getTransferHost() {
//		return this;
//	}

	@Override
	public Integer call() throws Exception {
		cwd = String.valueOf(getSeparator());
		start();
		return 0;
	}

	@Override
	public char getSeparator() {
		return getSeparator(false);
	}

	@Override
	public TNFSMount getMount() {
		return mount;
	}

	@Override
	public String getCwd() {
		return cwd;
	}

	@Override
	public Path getLcwd() {
		return lcwd;
	}

	@Override
	public Terminal getTerminal() {
		return terminal;
	}

	@Override
	public void setCwd(String cwd) {
		this.cwd = cwd;
	}

	@Override
	public void setLcwd(Path lcwd) {
		this.lcwd = lcwd;
	}

	@Override
	public CommandSpec getSpec() {
		return spec;
	}

	@Override
	public TNFSURI getURI() {
		return uri;
	}

	@Override
	public TNFSClient getClient() {
		return client;
	}

	@Override
	public TNFSMount mount(TNFSURI uri) throws IOException {
		if(client == null)
			throw new IOException("Not connected.");
		if(!uri.isSameServer(this.uri)) {
			throw new IllegalArgumentException("URI is not for the same server. Close client and reconnect with this URI first.");
		}
		var oldMount = mount;
		mount = doMount(uri, client);
		this.uri = uri; 
		cwd = String.valueOf(getSeparator());
		if(oldMount != null) {
			oldMount.close();
		}
		return mount;
	}

	@Override
	public TNFSClient connect(TNFSURI uri) throws IOException {
		var oldClient = client;
		client = doConnect(uri);
		this.uri = uri;
		cwd = String.valueOf(getSeparator());
		if(oldClient != null) {
			mount = null;
			oldClient.close();
		}
		return client;
	}

	@Override
	public boolean isMounted() {
		return mount != null;
	}

	@Override
	public void startIfNotStarted() throws Exception {
		if(uri == null) {
			setupLogging();
			connectAndMount();
		}
	}

	@Override
	public String nativeToLocalPath(String cwd) {
		/* TODO what if path has escaped forward slash? */
		if(isWindowsParsing()) {
			return cwd.replace("/", String.valueOf(getSeparator(false)));
		}
		return cwd;
	}
	
	@Override
	public String localToNativePath(String cwd) {
		/* TODO what if path has escaped forward slash? */
		if(isWindowsParsing())
			return cwd.replace("\\", "/");
		return cwd;
	}

	@Override
	public void unmount() throws IOException {
		if(mount == null) {
			throw new IllegalStateException("Not mounted.");
		}
		else {
			try {
				mount.close();
			}
			finally {
				mount = null;
			}
		}
	}

	protected void onStart() throws IOException {
		connectAndMount();
		startConsole();
	}

	private void connectAndMount() throws IOException {
		uri = TNFSURI.parse(destination);
		client = doConnect(uri);
		mount = doMount(uri, client);
	}

	private void startConsole() throws IOException {
		try {
			/* Setup */
			var iconsole = new InteractiveConsole(this);
			var factory = new PicocliCommandsFactory();
			var cmdline = new CommandLine(iconsole, factory);
			var subs = cmdline.getSubcommands();
			
			configureCommandLine(cmdline);
			
			/* Custom commands */
			var picocliCommands = new PicocliCommands(cmdline) {
			    @Override
				public Object invoke(CommandSession session, String command, Object... args) throws Exception {
			        List<String> arguments = new ArrayList<>();
			        arguments.add( command );
			        arguments.addAll( Arrays.stream( args ).map( Object::toString ).toList() );
			        return cmdline.execute( arguments.toArray( new String[0] ) );
			    }
			};

			/* Parser and system registry */
			var parser = new DefaultParser();  /* TODO windows / unix path parsing mode */
			if(isWindowsParsing()) {
				parser.setEscapeChars(new char[] {'^'});
			}
			var systemRegistry = new SystemRegistryImpl(parser, terminal, this::getLcwd, null);
			systemRegistry.setCommandRegistries(picocliCommands);
			
			/* Add completers etc */
			configureSystemRegistry(systemRegistry, picocliCommands, subs);

			var bldr = LineReaderBuilder.builder().terminal(terminal).
					completer(systemRegistry.completer()).
					parser(parser).
	                option(LineReader.Option.DISABLE_EVENT_EXPANSION, true).
					variable(LineReader.LIST_MAX, 50);
			bldr.option(LineReader.Option.USE_FORWARD_SLASH, !isWindowsParsing());
			
			var reader = bldr.
					build();
			factory.setTerminal(terminal);
			
			/* Widgets */
			var widgets = new TailTipWidgets(reader, 
					systemRegistry::commandDescription, 5,
					TailTipWidgets.TipType.COMPLETER);
			widgets.enable();
			
			/* Keymap */
			var keyMap = reader.getKeyMaps().get("main");
			keyMap.bind(new Reference("tailtip-toggle"), KeyMap.alt("s"));
			
			/* Welcome text */
			cwd = String.valueOf(getSeparator());
			var trm = terminal.writer();
			trm.println(String.format("Connected to %s @ %s over %s", mount.client().address(), mount.mountPath().equals("") ? "default mount" : mount.mountPath(), mount.client().protocol()));
			trm.flush();
			
			/* Input loop */
			do {
				try {
					systemRegistry.cleanUp();
					var cmd = reader.readLine("tnfs> ");
					if (cmd != null && cmd.length() > 0) {
						var newargs = parseQuotedString(cmd, isWindowsParsing());
						newargs.removeIf(item -> item == null || "".equals(item));
						var args = newargs.toArray(new String[0]);
						if (args.length > 0) {
							spawn(args, iconsole);
						}
					}

				} catch(UserInterruptException | EndOfFileException ee) {
					exitWhenDone = true;
				} 
			} while (!exitWhenDone);
		} catch(RuntimeException re) {
			throw re;
		} catch (Exception e1) {
			throw new IllegalStateException("Failed to open interactive shell.", e1);
		} finally {
			mount.close();
		}
	}

	private void configureSystemRegistry(SystemRegistryImpl systemRegistry, PicocliCommands picocliCommands,
			Map<String, CommandLine> subs) {
		if(completion == null || !completion.noCompletion) {

			RemoteFileNameCompleter remote;
			RemoteFileNameCompleter remoteDirs;
			
			if(completion != null && completion.noRemoteCompletion) {
				remote = null;
				remoteDirs = null;
			}
			else {
				remote = new RemoteFileNameCompleter();
				remoteDirs = new RemoteFileNameCompleter() {
		
					@Override
					protected boolean accept(Entry path) {
						return super.accept(path) && DirEntryFlag.isDirectory(path.flags());
					}
					
				};
			}
			
			/* Local files or directories */
			var local = new Completers.FileNameCompleter() {
				@Override
				protected Path getUserDir() {
					return getLcwd();
				}
	
				@Override
		        protected String getSeparator(boolean useForwardSlash) {
		            return useForwardSlash ? "/" : ( isWindowsParsing() ? "\\" : "/" );
		        }
			};
			
			/* Local directories */
			var localDirs = new Completers.FileNameCompleter() {
				@Override
				protected Path getUserDir() {
					return getLcwd();
				}
	
				@Override
				protected boolean accept(Path path) {
					return super.accept(path) && Files.isDirectory(path);
				}
	
				@Override
		        protected String getSeparator(boolean useForwardSlash) {
		            return useForwardSlash ? "/" : ( isWindowsParsing() ? "\\" : "/" );
		        }
			};
			
			systemRegistry.addCompleter(new Completer() {
				@Override
				public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
					var cmd = line.words().get(0);
					var cli  = subs.get(cmd);
					if(cli != null && cli.getCommand() instanceof TNFSTPCommand sub) {
						var mode = sub.completionMode();
						switch(mode) {
						case LOCAL:
						case LOCAL_THEN_REMOTE: /* TODO remove when below is supported */
							local.complete(reader, line, candidates);
							break;
						case DIRECTORIES_LOCAL:
						case DIRECTORIES_LOCAL_THEN_REMOTE: /* TODO remove when below is supported */
							localDirs.complete(reader, line, candidates);
							break;
						case REMOTE:
						case REMOTE_THEN_LOCAL:  /* TODO remove when below is supported */
							if(remote != null) {
								remote.complete(reader, line, candidates);
							}
							break;
						case DIRECTORIES_REMOTE:
						case DIRECTORIES_REMOTE_THEN_LOCAL:  /* TODO remove when below is supported */
							if(remote != null) {
								remoteDirs.complete(reader, line, candidates);
							}
							break;
	//					case LOCAL_THEN_REMOTE:
							// TODO base on index
	//						local.complete(reader, line, candidates);
	//						remote.complete(reader, line, candidates);
	//						break;
	//					case REMOTE_THEN_LOCAL:
							// TODO base on index
	//						remote.complete(reader, line, candidates);
	//						local.complete(reader, line, candidates);
	//						break;
						default:
							break;
						}
					}
				}
			});
		}
	}

	private void spawn(String[] args, Object cmd) throws IOException, InterruptedException {
		if(args[0].startsWith("!")) {
			try {
				args[0] = args[0].substring(1);
				if(Util.isWindows()) {
					args = new String[] {
						"cmd.exe",
						"/c",
						String.join(" ", Arrays.asList(args).stream().
								map(s -> "\"" + s + "\"").
								toList().
								toArray(new String[0]))
					};
				}
//				args[0] = Util.findCommand(args[0]).toAbsolutePath().toString();
				var pb = new ProcessBuilder(Arrays.asList(args));
				pb.directory(getLcwd().toFile());
				pb.redirectErrorStream(true);
				Process p = pb.start();
				try {
					var thread = new Thread() {
						public void run() {
							var b = new byte[256];
							var in = terminal.input();
							var out = p.getOutputStream();
							int r;
							try {
								while( ( r = in.read(b)) != -1) {
									out.write(b, 0, r);
									out.flush();
								}
							}
							catch(Exception ioe) {
							}
						}
					};
					thread.start();
					try {
						p.getInputStream().transferTo(System.out);
					}
					finally {
						thread.interrupt();
					}
				}
				finally {
					if(p.waitFor() != 0) {
						error(String.format("%s exited with error code %d.%n", args[0], p.exitValue()));
					}
				}
			}
			catch(Exception ioe) { 
				error(String.format("Command %s failed. %s%n", args[0], ioe.getMessage()));
			}
		}
		else {
			var cl = new CommandLine(cmd);
			configureCommandLine(cl);
			cl.execute(args);
		}
	}
	

	private void configureCommandLine(CommandLine cl) {
		cl.setTrimQuotes(true);
		cl.setUnmatchedArgumentsAllowed(true);
		cl.setUnmatchedOptionsAllowedAsOptionParameters(true);
		cl.setUnmatchedOptionsArePositionalParams(true);
		cl.setExecutionExceptionHandler(new ExceptionHandler(this));
	}
	
	class RemoteFileNameCompleter implements org.jline.reader.Completer {

        @Override
		public void complete(LineReader reader, ParsedLine commandLine, List<Candidate> candidates) {
            assert commandLine != null;
            assert candidates != null;

            String buffer = commandLine.word().substring(0, commandLine.wordCursor());

            String current;
            String curBuf;
            char sep = getSeparator(reader.isSet(LineReader.Option.USE_FORWARD_SLASH));
            int lastSep = buffer.lastIndexOf(sep);
            try {
                if (lastSep >= 0) {
                    curBuf = buffer.substring(0, lastSep + 1);
                    current = Util.resolvePaths(getCwd(), curBuf, getSeparator());
                } else {
                    curBuf = "";
                    current = getCwd();
                }
                var resolver = Styles.lsStyle();
                
                try(var dir = mount.directory(localToNativePath(current))) {
                	var str = dir.stream();
                	str.filter(this::accept).
                	forEach(p -> {

	                	var value = curBuf + p.name();
	                    if (DirEntryFlag.isDirectory(p.flags())) {
	                    	var candidate = value;
	                    	if(reader.isSet(LineReader.Option.AUTO_PARAM_SLASH) && !candidate.endsWith(String.valueOf(sep))) {
	                    		candidate += sep;
	                    	}
	                    	
	                        candidates.add(new Candidate(
	                                candidate,
	                                getDisplay(terminal, p, resolver, 0, sep, true),
	                                null,
	                                null,
	                                reader.isSet(LineReader.Option.AUTO_REMOVE_SLASH) ? String.valueOf(sep) : null,
	                                null,
	                                false));
	                    } else {
	                        candidates.add(new Candidate(
	                                value,
	                                getDisplay(terminal, p, resolver, 0, sep, true),
	                                null,
	                                null,
	                                null,
	                                null,
	                                true));
	                    }
	                });
                }
                
            } catch (Exception e) {
            	if(log.isDebugEnabled())
            		log.error("Failed remote completion.", e);
            }
        }

        protected boolean accept(Entry path) {
            return !path.name().startsWith(".");
        }
    }

    public static String getDisplay(Terminal terminal, Entry p, StyleResolver resolver, int width, char separator, boolean nameTailIndicator) {
    	
        var sb = new AttributedStringBuilder();
        var name = p.name();
        var isDir = DirEntryFlag.isDirectory(p.flags());
        var isSpecial = DirEntryFlag.isSpecial(p.flags());
        var isHidden = DirEntryFlag.isHidden(p.flags());
        var idx = name.lastIndexOf(".");
        var type = idx != -1 ? ".*" + name.substring(idx) : null;
        
        var displayName = name;
        
        var availableWidth = width == 0 
        		? 0 
        		: ( nameTailIndicator & ( isHidden || isDir ) 
        			? width  - ( nameTailIndicator ? 0 : - 1 ) 
        			: width );
        
        var tail = new StringBuffer();
        if(availableWidth > 0) {
        	if(displayName.length() > availableWidth) {
        		displayName = Util.trimOrPad(displayName, availableWidth);
        	}
        	else if(displayName.length() < availableWidth) {
        		var cnt = availableWidth - displayName.length();
        		for(var i = 0 ; i < cnt; i++) {
        			tail.append(' ');
        		}
        	}
        }
        
        if (isHidden) {
            sb.styled(resolver.resolve(".ln"), displayName);
            if(nameTailIndicator)
            	sb.append("@");
            sb.append(tail);
        } else if (isDir) {
            sb.styled(resolver.resolve(".di"), displayName);
            if(nameTailIndicator)
            	sb.append(separator);
            sb.append(tail);
        } else if (type != null && resolver.resolve(type).getStyle() != 0) {
            sb.styled(resolver.resolve(type), displayName).append(tail);
        } else if (isSpecial) {
            sb.styled(resolver.resolve(".fi"), displayName).append(tail);
        } else {
            sb.append(displayName).append(tail);
        }
        
        return sb.toAnsi(terminal);
    }
}
