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
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

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
import uk.co.bithatch.tnfs.daemonlib.MDNS;
import uk.co.bithatch.tnfs.lib.AppLogLevel;
import uk.co.bithatch.tnfs.lib.Net;
import uk.co.bithatch.tnfs.lib.Protocol;
import uk.co.bithatch.tnfs.server.TNFSMounts;
import uk.co.bithatch.tnfs.server.TNFSServer;

/**
 * A TNFS server.
 */
@Command(name = "tnfsjd", description = "TNFS Daemon.", mixinStandardHelpOptions = true)
public class TNFSDaemon implements Callable<Integer>, ExceptionHandlerHost {

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

    @Option(names = { "--hash" }, description = "The default hashing algorithm when using extended authentication.")
    private String hash = "MD5";

    @Option(names = { "--iterations" }, description = "Default iteration count for password hashes when using extended authentication.")
    private int iterations = 4096;

    @Option(names = { "-L", "--log-level" }, paramLabel = "LEVEL", description = "Logging level for trouble-shooting.")
    private Optional<AppLogLevel> level;

    @Option(names = { "-F", "--log-file" }, paramLabel = "LOGFILE", description = "Path to the log file.")
    private Optional<Path> logFile;
    
    @Spec
    private CommandSpec spec;

    @Option(names = { "-C", "--configuration" }, description = "Locate of system configurationDir. By defafult, will either be the systems default global configuration directory or a user configuration directory.")
    private Optional<Path> configurationDir;
    
    @Option(names = { "-O", "--override-configuration" }, description = "Location of user override configuration. By default, will be a configuration directory in the users home directory or the users home directory.")
    private Optional<Path> userConfiguration;

    @Option(names = { "-D", "--sysprop" }, description = "Set a system property.")
    private List<String> systemProperties;
    
	@Option(names = { "-X", "--verbose-exceptions" }, description = "Show verbose exception traces on errors.")
    private boolean verboseExceptions;

    private InetAddress actualAddress;
    private Configuration configuration;
    private Logger log;
	private MountConfiguration mountConfiguration;
    
	private final AtomicBoolean deregistered = new AtomicBoolean();

	private TNFSServer<?> udpSrvr;
	private TNFSServer<?> tcpSrvr;

	private MDNS jmdns;

	private Thread shutdownHook;

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
    		
	    	/* Logging. Must happen before ANY loggers are created  */
			System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", level.orElse(AppLogLevel.WARN).name());
			System.setProperty("org.slf4j.simpleLogger.logFile", logFile.map(Path::toString).orElse("System.out"));
			
			log = LoggerFactory.getLogger(TNFSDaemon.class);
    		
    		configuration = new Configuration(monitor, configurationDir, userConfiguration);

    		mountConfiguration = new MountConfiguration(monitor, configuration, configurationDir, userConfiguration);
    		var tnfsMounts = mountConfiguration.mounts();
    		mountConfiguration.addListener(() -> {
    			if(jmdns != null) {
    				deregisterMdns();

    				try {
	    				try {
							Thread.sleep(2000);
						} catch (InterruptedException e) {
							throw new IllegalStateException(e);
						}
	    				
	    				var protocols = getProtocols();
	    				if(protocols.isEmpty() || protocols.size() == Protocol.values().length) {
	    					
							registerMDNS(tnfsMounts, Protocol.TCP);
							registerMDNS(tnfsMounts, Protocol.UDP);
	    						
	    				}
	    				else {
	    					if(protocols.get(0) == Protocol.TCP) {
	    						registerMDNS(tnfsMounts, Protocol.TCP);
	    					}
	    					else if(protocols.get(0) == Protocol.UDP) {
	    						registerMDNS(tnfsMounts, Protocol.UDP);
	    					}
	    					else {
	    						throw new IllegalStateException();
	    					}
	    				}
    				}
    				finally {
    					deregistered.set(false);
    				}
    			}
    		});
    		
    		configuration.document().onValueUpdate(vu -> {
    			if(udpSrvr != null) {
    				try {
						udpSrvr.close();
					} catch (IOException e) {
					}
    			}
    			
    			if(tcpSrvr != null) {
    				try {
    					tcpSrvr.close();
					} catch (IOException e) {
					}
    			}
    		});
    		
    		while(true) {
    			startServer(tnfsMounts, monitor);
    		}
		
    	}
    }

	private void startServer(TNFSMounts tnfsMounts, Monitor monitor) throws IOException, InterruptedException {
  	
		
		/* Get actual address to listen on */
		actualAddress = getActualAddress();
		
		log.info("Will listen on {}", actualAddress);
		
		if(configuration.server().getBoolean(Constants.UPNP_KEY) && actualAddress.isLoopbackAddress()) {
			throw new IllegalStateException("Cannot map port using UPnP if only listening to loopback address. Use --addresss argument to specify address to bind to.");
		}

		/* Announce to everyone else we exist */
		if(configuration.mdns().getBoolean(Constants.ANNOUNCE_KEY)) {

			jmdns = new MDNS(configuration.mdns().getOr(Constants.ADDRESS_KEY).map(t ->Net.parseAddress(t, t)).
					orElse(actualAddress));
		    try {
		        shutdownHook = new Thread(() -> deregisterMdns(), "MDNSDeregister");
				Runtime.getRuntime().addShutdownHook(shutdownHook);
				runServer(tnfsMounts);
		    }
		    finally {
		    	deregisterMdns();
		    }
		}
		else {
			if(shutdownHook != null) {
				Runtime.getRuntime().removeShutdownHook(shutdownHook);
				shutdownHook = null;
			}
			jmdns = null;
			runServer(tnfsMounts, Optional.empty());	            
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

	private InetAddress getActualAddress() {
		return configuration.server().getOr(Constants.ADDRESS_KEY).map(t ->Net.parseAddress(t, t)).
				orElseGet(() -> {
					if(mountConfiguration.isDemo()) {
						log.warn("Listening on LAN address because demonstration mount is active. When you define your own mounts, you will also need to configure the listening address.");
						return Net.getIpAddress();
					}
					else {
						return InetAddress.getLoopbackAddress();
					}
				});
	}

	private void deregisterMdns() {
		if(!deregistered.get()) {
			log.info("De-registering mDNS");
			jmdns.unregisterAllServices();
			deregistered.set(true);
		}
	}

	private InetAddress getPublicAddress() {
    	if(actualAddress.isAnyLocalAddress())
			try {
				return Net.getIpAddress();
			} catch (UncheckedIOException e) {
				throw new IllegalStateException("Cannot get localhost, but the server is listening on a wildcard address. Will not be able to announce to mDNS.", e);
			}
		else
    		return actualAddress;
    }

	private List<String> mountNames(TNFSMounts mounts) {
		return mounts.mounts().stream().map(mnt -> mnt.fs().mountPath()).toList();
	}

	private String normalize(InetAddress addr) {
		var hostaddr = addr.getHostAddress();
		if(hostaddr.equals("0.0.0.0")) {
			try {
				addr = Net.getIpAddress();
			} catch (UncheckedIOException e) {
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
	
	private void registerMDNS(TNFSMounts tnfsMounts, Protocol protocol) {
		try {
			var txtname = configuration.server().get(Constants.NAME_KEY).
				replace("{hostname}", 
					normalize(getPublicAddress())).
				replace("{protocol}", protocol.name());
			log.info("Registering mDNS for {} as {}", protocol, txtname);
			ServiceInfo srv = ServiceInfo.create(
					String.format("_tnfs._%s.local.", protocol.name().toLowerCase()), 
					txtname, 
					configuration.server().getInt(Constants.PORT_KEY), 
					String.join(";", String.format("path=%s", mountNames(tnfsMounts))));
			
			jmdns.registerService(srv);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void runServer(TNFSMounts tnfsMounts, Optional<Gateway> gateway) throws IOException, InterruptedException {
		
		var port = configuration.server().getInt(Constants.PORT_KEY);
		var protocols = getProtocols();
		
		/* Servers */		
		if(protocols.isEmpty() || protocols.size() == Protocol.values().length) {
			

			/* Both */
			try(var udpSrvr = new TNFSServer.Builder().
					withFileSystemFactory(tnfsMounts).
					withPort(port).
					withHost(actualAddress).
					build();
					
				var tcpSrvr = new TNFSServer.Builder().
						withFileSystemFactory(tnfsMounts).
						withPort(port).
						withHost(actualAddress).
						withTcp().
						build()
			) {

				this.tcpSrvr = tcpSrvr;
				this.udpSrvr = udpSrvr;

				gateway.ifPresent(gw -> {  
					try {
						gw.map(port, UPnP.Protocol.TCP);
					}
					finally {
						gw.map(port, UPnP.Protocol.UDP);
					}
				});
				
				if(jmdns != null) {
					registerMDNS(tnfsMounts, Protocol.TCP);
					registerMDNS(tnfsMounts, Protocol.UDP);
				}
				
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
				if(jmdns != null) {
					registerMDNS(tnfsMounts, Protocol.TCP);
				}
				
				/* TCP */
				try(var tcpSrvr = new TNFSServer.Builder().
							withFileSystemFactory(tnfsMounts).
							withPort(port).
							withHost(actualAddress).
							withTcp().
							build()
				) {
					this.tcpSrvr = tcpSrvr;
					tcpSrvr.run();
				}
			}
			else if(protocols.get(0) == Protocol.UDP) {

				gateway.ifPresent(gw -> gw.map(port, UPnP.Protocol.UDP));
				if(jmdns != null) {
					registerMDNS(tnfsMounts, Protocol.UDP);
				}
				
				/* UDP */
				try(var udpSrvr = new TNFSServer.Builder().
							withFileSystemFactory(tnfsMounts).
							withPort(port).
							withHost(actualAddress).
							build()
				) {
					this.udpSrvr = udpSrvr;
					udpSrvr.run();
				}
			}
			else {
				throw new IllegalStateException();
			}
		}
	}

	private List<Protocol> getProtocols() {
		return Arrays.asList(configuration.server().getAllEnum(Protocol.class, Constants.PROTOCOLS_KEY));
	}

	private void runServer(TNFSMounts tnfsMounts) throws IOException, InterruptedException {
		if(configuration.server().getBoolean(Constants.UPNP_KEY)) {
			runServer(tnfsMounts, UPnP.gateway());
		}
		else {
			runServer(tnfsMounts, Optional.empty());
		}
	}
}
