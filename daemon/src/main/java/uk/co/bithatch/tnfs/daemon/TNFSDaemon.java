/*
 * Copyright © 2025 Bithatch (bithatch@bithatch.co.uk)
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
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jmdns.JmmDNS;
import javax.jmdns.ServiceInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.jini.config.Monitor;
import com.sshtools.porter.UPnP;
import com.sshtools.porter.UPnP.Gateway;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import uk.co.bithatch.tnfs.daemon.ExceptionHandler.ExceptionHandlerHost;
import uk.co.bithatch.tnfs.lib.Protocol;
import uk.co.bithatch.tnfs.server.TNFSMounts;
import uk.co.bithatch.tnfs.server.TNFSServer;

/**
 * A TNFS server.
 */
@Command(name = "tnfsd", description = "TNFS Daemon.", mixinStandardHelpOptions = true)
public class TNFSDaemon implements Callable<Integer>, ExceptionHandlerHost {

    public enum Level {
        DEBUG, ERROR, INFO, TRACE, WARN,
    }
    
    final static PrintStream out = System.out;

    public static void main(String[] args) throws Exception {
        var cmd = new TNFSDaemon();
        System.exit(new CommandLine(cmd).setExecutionExceptionHandler(new ExceptionHandler(cmd)).execute(args));
    }
    
    static void logCommandLine(String... args) {
        var largs = new ArrayList<>(Arrays.asList(args));
        var path = Paths.get(largs.get(0));
        if(path.isAbsolute()) {
            largs.set(0, path.getFileName().toString());
        }
        System.out.format("[#] %s%n", String.join(" ", largs));
    }

    @Option(names = { "-C", "--configuration" }, description = "Locate of configurationDir. By defafult, will either be the systems default global configuration directory or a user configuration directory.")
    private Optional<Path> configurationDir;

    @Option(names = { "--hash" }, description = "The default hashing algorithm when using extended authentication.")
    private String hash = "MD5";

    @Option(names = { "--iterations" }, description = "Default iteration count for password hashes when using extended authentication.")
    private int iterations = 4096;

    @Option(names = { "-L", "--log-level" }, paramLabel = "LEVEL", description = "Logging level for trouble-shooting.")
    private Optional<Level> level;
    
    @Spec
    private CommandSpec spec;

    @Option(names = { "-D", "--sysprop" }, description = "Set a system property.")
    private List<String> systemProperties;
    
	@Option(names = { "-X", "--verbose-exceptions" }, description = "Show verbose exception traces on errors.")
    private boolean verboseExceptions;

    private InetAddress actualAddress;
    private Configuration configuration;
    private Logger log;
	private MountConfiguration mountConfiguration;

	@Override
    public Integer call() throws Exception {

		
    	try(var monitor = new Monitor()) {
			
	    	/* System properties */
	        if(systemProperties != null) {
		        for(var str: systemProperties) {
		        	var idx = str.indexOf('=');
		        	if(idx == -1) {
		        		System.setProperty(str, "true");
		        	}
		        	else {
		        		System.setProperty(str.substring(0, idx), str.substring(idx + 1));
		        	}
		        }
	        }
    		
    		configuration = new Configuration(monitor, configurationDir);
    		
	    	/* Logging */
			System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", level.orElse(Level.WARN).name());
			log = LoggerFactory.getLogger(TNFSDaemon.class);
    		
    		mountConfiguration = new MountConfiguration(monitor, configuration, configurationDir);
    		var tnfsMounts = mountConfiguration.mounts();
    	
	    	
	        /* Get actual address to listen on */
	    	actualAddress = configuration.server().getOr(Constants.ADDRESS_KEY).map(t -> {
				try {
					return InetAddress.getByName(t);
				} catch (UnknownHostException e) {
					throw new UncheckedIOException(e);
				}
			}).orElseGet(InetAddress::getLoopbackAddress);
	    	
	    	if(configuration.server().getBoolean(Constants.UPNP_KEY) && actualAddress.isLoopbackAddress()) {
	    		throw new IllegalStateException("Cannot map port using UPnP if only listening to loopback address. Use --addresss argument to specify address to bind to.");
	    	}

	    	/* Announce to everyone else we exist */
			if(configuration.server().getBoolean(Constants.ANNOUNCE_KEY)) {

				JmmDNS jmdns =JmmDNS.Factory.getInstance();
	            
	            var deregistered = new AtomicBoolean();
	            try {
		            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
	            		jmdns.unregisterAllServices();
	            		deregistered.set(true);
		            }, "MDNSDeregister"));
	
					runServer(tnfsMounts, Optional.of(jmdns));
					
	            }
	            finally {
	            	if(!deregistered.get()) {
	            		jmdns.unregisterAllServices();
	            		deregistered.set(true);
	            	}
	            }
			}
			else {
				runServer(tnfsMounts, Optional.empty());	            
			}
			
	        return 0;
		
    	}
    }

	@Override
	public CommandSpec spec() {
		return spec;
	}
	
	@Override
	public boolean verboseExceptions() {
        return verboseExceptions;
    }

	private InetAddress getPublicAddress() {
    	if(actualAddress.isAnyLocalAddress())
			try {
				return InetAddress.getLocalHost();
			} catch (UnknownHostException e) {
				throw new IllegalStateException("Cannot get localhost, but the server is listening on a wildcard address. Will not be able to announce to mDNS.", e);
			}
		else
    		return actualAddress;
    }

	private List<String> mountNammes(TNFSMounts mounts) {
		return mounts.mounts().stream().map(mnt -> mnt.fs().mountPath()).toList();
	}

	private String normalize(InetAddress addr) {
		var hostaddr = addr.getHostAddress();
		if(hostaddr.equals("0.0.0.0")) {
			try {
				addr = InetAddress.getLocalHost();
			} catch (UnknownHostException e) {
				return "localhost";
			}
			hostaddr = addr.getHostAddress();
		}
		var hostname = addr.getHostName();
		if(hostname.equals(hostaddr)) {
			return hostaddr.replace('.', '_').replace(':', '_');
		}
		else {
			var idx = hostname.indexOf('.');
			return idx == -1 ? hostname : hostname.substring(0, idx);
		}
	}
	
	private void registerMDNS(TNFSMounts tnfsMounts, Optional<JmmDNS> mDNS, Protocol protocol) {
		mDNS.ifPresent(m -> {
			try {
				var txtname = configuration.server().get(Constants.NAME_KEY).
					replace("{hostname}", 
						normalize(getPublicAddress())).
					replace("{protocol}", protocol.name());
				log.info("Registering mDNS for {} as {}", protocol, txtname);
				m.registerService(ServiceInfo.create(
						String.format("_tnfs._%s.local.", protocol.name().toLowerCase()), 
						txtname, 
						configuration.server().getInt(Constants.PORT_KEY), 
						String.join(";", String.format("path=%s", mountNammes(tnfsMounts)))));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	private void runServer(TNFSMounts tnfsMounts, Optional<Gateway> gateway, Optional<JmmDNS> mDNS) throws IOException, InterruptedException {
		
		var port = configuration.server().getInt(Constants.PORT_KEY);
		var protocols = Arrays.asList(configuration.server().getAllEnum(Protocol.class, Constants.PROTOCOLS_KEY));
		
		/* Servers */		
		if(protocols.isEmpty() || protocols.size() == Protocol.values().length) {
			

			/* Both */
			try(var udpSrvr = new TNFSServer.Builder().
					withFileSystemFactory(tnfsMounts).
					withPort(port).
					withServerKey(mountConfiguration.authentication()::serverKey).
					withHost(actualAddress).
					build();
					
				var tcpSrvr = new TNFSServer.Builder().
						withFileSystemFactory(tnfsMounts).
						withPort(port).
						withHost(actualAddress).
						withServerKey(mountConfiguration.authentication()::serverKey).
						withTcp().
						build()
			) {
				

				gateway.ifPresent(gw -> {  
					try {
						gw.map(port, UPnP.Protocol.TCP);
					}
					finally {
						gw.map(port, UPnP.Protocol.UDP);
					}
				});
				registerMDNS(tnfsMounts, mDNS, Protocol.TCP);
				registerMDNS(tnfsMounts, mDNS, Protocol.UDP);
				
				var t1 = new Thread(udpSrvr::run, "UDPTNFS");
				t1.start();

				var t2 = new Thread(tcpSrvr::run, "TCPTNFS");
				t2.start();
				
				t1.join();
				t2.join();
			}
		}
		else {
			if(protocols.get(0) == Protocol.TCP) {

				gateway.ifPresent(gw -> gw.map(port, UPnP.Protocol.TCP));
				registerMDNS(tnfsMounts, mDNS, Protocol.TCP);
				
				/* TCP */
				try(var tcpSrvr = new TNFSServer.Builder().
							withFileSystemFactory(tnfsMounts).
							withPort(port).
							withHost(actualAddress).
							withServerKey(mountConfiguration.authentication()::serverKey).
							withTcp().
							build()
				) {
					tcpSrvr.run();
				}
			}
			else if(protocols.get(0) == Protocol.TCP) {

				gateway.ifPresent(gw -> gw.map(port, UPnP.Protocol.UDP));
				registerMDNS(tnfsMounts, mDNS, Protocol.UDP);
				
				/* UDP */
				try(var udpSrvr = new TNFSServer.Builder().
							withFileSystemFactory(tnfsMounts).
							withPort(port).
							withServerKey(mountConfiguration.authentication()::serverKey).
							withHost(actualAddress).
							build()
				) {
					udpSrvr.run();
				}
			}
			else {
				throw new IllegalStateException();
			}
		}
	}

	private void runServer(TNFSMounts tnfsMounts, Optional<JmmDNS> mDNS) throws IOException, InterruptedException {
		if(configuration.server().getBoolean(Constants.UPNP_KEY)) {
			runServer(tnfsMounts, UPnP.gateway(), mDNS);
		}
		else {
			runServer(tnfsMounts, Optional.empty(), mDNS);
		}
	}
}
