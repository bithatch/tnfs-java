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
package uk.co.bithatch.tnfs.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jmdns.ServiceInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import com.sshtools.jini.config.Monitor;
import com.sshtools.porter.UPnP;
import com.sshtools.porter.UPnP.Gateway;
import com.sshtools.porter.UPnP.Protocol;
import com.sshtools.uhttpd.UHTTPD;
import com.sshtools.uhttpd.UHTTPD.FormData;
import com.sshtools.uhttpd.UHTTPD.NCSALoggerBuilder;
import com.sshtools.uhttpd.UHTTPD.Status;
import com.sshtools.uhttpd.UHTTPD.Transaction;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import uk.co.bithatch.tnfs.client.TNFSClient;
import uk.co.bithatch.tnfs.daemonlib.MDNS;
import uk.co.bithatch.tnfs.lib.AppLogLevel;
import uk.co.bithatch.tnfs.lib.Net;
import uk.co.bithatch.tnfs.lib.TNFS;
import uk.co.bithatch.tnfs.lib.TNFSException;
import uk.co.bithatch.tnfs.web.ExceptionHandler.ExceptionHandlerHost;
import uk.co.bithatch.tnfs.web.elfinder.ElFinderConstants;
import uk.co.bithatch.tnfs.web.elfinder.core.ElfinderContext;
import uk.co.bithatch.tnfs.web.elfinder.service.ElfinderStorageFactory;
import uk.co.bithatch.tnfs.web.elfinder.service.impl.DefaultElfinderStorage;
import uk.co.bithatch.tnfs.web.elfinder.service.impl.DefaultElfinderStorageFactory;
import uk.co.bithatch.tnfs.web.elfinder.service.impl.DefaultVolumeRef;

/**
 * Simple web front-end to TNFS resources.
 */
@Command(name = "tnfsjd-web", description = "TNFS Web File Manager.", mixinStandardHelpOptions = true)
public final class TNFSWeb implements Callable<Integer>, ExceptionHandlerHost {

    @Option(names = { "-L", "--log-level" }, paramLabel = "LEVEL", description = "Logging level for trouble-shooting.")
    private Optional<AppLogLevel> level;
    
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

	/**
	 * Entry point.
	 * 
	 * @param args command line arguments
	 * @throws Exception on error
	 */
	public static void main(String[] args) throws Exception {
        var cmd = new TNFSWeb();
        System.exit(new CommandLine(cmd).setExecutionExceptionHandler(new ExceptionHandler(cmd)).execute(args));
	}

	private ServiceCommandFactory commandFactory;
	private DefaultElfinderStorageFactory storageFactory;
	private Configuration configuration;
	private Optional<MDNS> mdns;
	private Logger log;

	public TNFSWeb() {

	}

	@Override
	public Integer call() throws Exception {

		var monitor = new Monitor();
		try {
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
    		
	    	/* Logging */
			System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", level.orElse(AppLogLevel.WARN).name());
			log = LoggerFactory.getLogger(TNFSWeb.class);
	        
			configuration = new Configuration(monitor, configurationDir, userConfiguration);
	
			log = LoggerFactory.getLogger(TNFSWeb.class);
	
			commandFactory = new ServiceCommandFactory();
			storageFactory = new DefaultElfinderStorageFactory();
	
			var bldr = new DefaultElfinderStorage.Builder();
			var mountCount = new AtomicInteger();
	
			/* Mounts from config files */
			
			configuration.mounts().forEach(mnt -> {
				var hostname = mnt.get(Constants.HOSTNAME_KEY);
				var port = mnt.getInt(Constants.PORT_KEY);
				var path = mnt.get(Constants.PATH_KEY);
				var name = mnt.getOr(Constants.NAME_KEY).orElseGet(() -> {
					var b = new StringBuilder(hostname);
					if (port != TNFS.DEFAULT_PORT) {
						b.append(":");
						b.append(port);
					}
					b.append(path);
					return b.toString();
				});
	
				try {
					var clnt = new TNFSClient.Builder().withAddress(hostname, port).build();
					var tnfsmnt = clnt.mount(path).build();
					var vol = new TNFSMountVolume(tnfsmnt, name);
					var ref = new DefaultVolumeRef( String.valueOf(Integer.toUnsignedLong(name.hashCode())), vol);
					
					bldr.addVolumes(ref);
					log.info("Mounted {} [{}] to {} @ {}", name, ref.getId(), path, hostname);
					mountCount.addAndGet(1);
	
				} catch (IOException ioe) {
					if(log.isDebugEnabled())
						log.error(MessageFormat.format("Failed to mount TNFS resource {0} @ {1}.", hostname, path), ioe);
					else
						log.error(MessageFormat.format("Failed to mount TNFS resource {0} @ {1}. {2}", hostname, path, ioe.getMessage()));
				}
			});
			
			/* Setup mDNS if needed */
			
			if(configuration.mountConfiguration().getBoolean(Constants.DISCOVER_KEY) ||
			   configuration.mdns().getBoolean(Constants.ANNOUNCE_KEY)) {
				try {
					
					var mdnsAddress = configuration.mdns().getOr(Constants.ADDRESS_KEY).map(a -> Net.parseAddress(a, "localhost")).orElseGet(() -> {
						var http = configuration.http();
						var https = configuration.https();
						var httpOn = http.getInt(Constants.PORT_KEY, 0) > 0;
						var httpsOn = https.getInt(Constants.PORT_KEY, 0) > 0;
						var httpAddr =  http.get(Constants.ADDRESS_KEY, "127.0.0.1");
						var httpsAddr =  https.get(Constants.ADDRESS_KEY, "127.0.0.1");
						
						if(httpOn  && httpsOn && !httpAddr.equals(httpsAddr)) {
							return Net.parseAddress("*");
						}
						else {
							if(httpsOn) {
								return Net.parseAddress(httpsAddr);
							}
							else if(httpOn) {
								return Net.parseAddress(httpAddr);
							}
							else {
								return null;
							}
						}	
					});
					mdns = Optional.of(new MDNS(mdnsAddress));
					
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
			else {
				mdns = Optional.empty();
			}
			
			try {
	
				/* Discover other mounts */
				
				if(configuration.mountConfiguration().getBoolean(Constants.DISCOVER_KEY)) {
					for(var srv : mdns.get().list("_tnfs._tcp.local.")) {
						try {
							for(var addr : srv.getHostAddresses()) {
		
								var clnt = new TNFSClient.Builder().
										withHostname(addr).
										withPort(srv.getPort()).
										build();
								
								for(var path : parseArray(srv.getPropertyString("path"))) {
									try {
										var name = path + " on " + addr;
										log.info("Mounting {} to {} @ {}", name, addr, path);
										var tnfsmnt = clnt.mount(path).build();
										var vol = new TNFSMountVolume(tnfsmnt, srv.getName());
										bldr.addVolumes(new DefaultVolumeRef(name, vol));
										log.info("Mounted {} to {} @ {}", name, addr, path);
										mountCount.addAndGet(1);	
									}
									catch(IOException | TNFSException ioe) {
										log.warn("Failed to connect to mDNS advertised mount {} on {}", path, addr);
									}
								}
									
							}
						}
						catch(IOException ioe) {
							log.warn("Failed to connect to mDNS advertised server {}", srv);
						}
					}
				}
		
				if (mountCount.get() == 0)
					throw new IllegalStateException("No valid mounts defined in configuration.");
		
				var storage = bldr.build();
		
				storageFactory.setElfinderStorage(storage);
			}
			catch(RuntimeException re) {
				mdns.ifPresent(dns -> { 
					dns.unregisterAllServices();
					try {
						dns.close();
					} catch (IOException e) {
					}
				});
				throw re;
			}
		}
		catch(RuntimeException re) {
			try {
				monitor.close();
			} catch (IOException e) {
			}
			throw re;
		}
		
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();

		if (configuration.mdns().getBoolean(Constants.ANNOUNCE_KEY)) {

			var deregistered = new AtomicBoolean();
			try {
				Runtime.getRuntime().addShutdownHook(new Thread(() -> {
					mdns.get().unregisterAllServices();
					deregistered.set(true);
				}, "MDNSDeregister"));

				runServer(mdns);

			} finally {
				if (!deregistered.get()) {
					mdns.get().unregisterAllServices();
					deregistered.set(true);
				}
			}
		}
		else {
			runServer(Optional.empty());
		}

		return 0;
	}

	@Override
	public CommandSpec spec() {
		return spec;
	}

	@Override
	public boolean verboseExceptions() {
		return verboseExceptions;
	}

	private void runServer(Optional<MDNS> mdns) throws IOException {

		if (configuration.server().getBoolean(Constants.UPNP_KEY)) {
			runServer(mdns, UPnP.gateway());
		} else {
			runServer(mdns, Optional.empty());
		}

	}

	private void runServer(Optional<MDNS> mdns, Optional<Gateway> gateway) throws IOException {
		var bldr = UHTTPD.server()
				.post("/api/(.*)", this::apiPost)
				.get("/api/(.*)", this::apiGet)
				.get("/index.html", UHTTPD.classpathResource("web/ui/index.html"))
				.get("/", (tx) -> tx.redirect(Status.MOVED_TEMPORARILY, "/index.html"))
				.classpathResources("/theme/(.*)", "web/theme")
				.classpathResources("/(.*)", "web/elfinder");

		
		var http = configuration.http();
		var httpPort = http.getInt(Constants.PORT_KEY);
		if(httpPort > 0)
			bldr.withHttp(httpPort);
		bldr.withHttpAddress(Net.parseAddress(http.get(Constants.ADDRESS_KEY, "localhost"), "localhost"));
		
		var https = configuration.https();
		var httpsPort = https.getInt(Constants.PORT_KEY);
		if(httpsPort > 0)
			bldr.withHttps(httpsPort);
		bldr.withHttpsAddress(Net.parseAddress(https.get(Constants.ADDRESS_KEY, "localhost"), "localhost"));
		https.getOr(Constants.KEY_PASSWORD_KEY).ifPresent(kp -> bldr.withKeyPassword(kp.toCharArray()));
		https.getOr(Constants.KEYSTORE_FILE_KEY).ifPresent(ks -> bldr.withKeyStoreFile(Paths.get(ks)));
		https.getOr(Constants.KEYSTORE_PASSWORD_KEY).ifPresent(kp -> bldr.withKeyPassword(kp.toCharArray()));
		https.getOr(Constants.KEYSTORE_TYPE_KEY).ifPresent(kp -> bldr.withKeyStoreType(kp));
		
		if(configuration.tuning().getBoolean(Constants.COMPRESSION_KEY)) {
				bldr.withoutCompression();
		}
		
		/* TODO Arggh... The "view source" in firefox bug when compression is on really needs fixing */
		bldr.withoutCompression();
		
		var ncsa = configuration.ncsa();
		if(ncsa.getBoolean(Constants.ENABLED_KEY)) {

			bldr.withLogger(new NCSALoggerBuilder().
					withAppend(ncsa.getBoolean("append")).
					withDirectory(Paths.get(ncsa.get("directory"))).
					withExtended(ncsa.getBoolean("extended")).
					withServerName(ncsa.getBoolean("server-name")).
					withFilenamePattern(ncsa.get("pattern")).
					withFilenameDateFormat(ncsa.get("date-format")).
					build());
		}

		try (var httpd = bldr.build()) {

			gateway.ifPresent(gw -> {
				httpd.httpPort().ifPresent(p -> {
					log.info("Mapping port {} for HTTP on your router to this machine", p);
					gw.map(p, Protocol.TCP);
					log.warn("Mapping port {} for HTTP successful. Your server is now on the internet!", p);
				});
				httpd.httpsPort().ifPresent(p -> {
					log.info("Mapping port {} for HTTPS on your router to this machine", p);
					gw.map(p, Protocol.TCP);
					log.warn("Mapping port {} for HTTPS successful. Your server is now on the internet!", p);
				});
			});

			mdns.ifPresent(d -> {
				httpd.httpPort().ifPresent(p -> {
					log.info("Registering mDNS for HTTP on {}", p);
					try {
						d.registerService( ServiceInfo.create("_http._tcp.local.", "TNFSJ Web", p, "path=index.html"));
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				});
				httpd.httpsPort().ifPresent(p -> {
					log.info("Registering mDNS for HTTPS on {}", p);
					try {
						d.registerService( ServiceInfo.create("_https._tcp.local.", "TNFSJ Web", p, "path=index.html"));
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				});
			});

			httpd.run();
		}
	}

	private void apiPost(Transaction tx) throws Exception {
		commandFactory.get(tx.request().asFormData(ElFinderConstants.ELFINDER_PARAMETER_COMMAND).asString())
				.execute(new ElfinderContext() {

					@Override
					public ElfinderStorageFactory getVolumeSourceFactory() {
						return storageFactory;
					}

					@Override
					public Transaction getTx() {
						return tx;
					}
				});
		;
	}

	private void apiGet(Transaction tx) throws Exception {
		commandFactory.get(tx.parameter(ElFinderConstants.ELFINDER_PARAMETER_COMMAND).asString())
				.execute(new ElfinderContext() {

					@Override
					public ElfinderStorageFactory getVolumeSourceFactory() {
						return storageFactory;
					}

					@Override
					public Transaction getTx() {
						return tx;
					}
				});
		;
	}
	
	private final static String[] parseArray(String arrStr) {
		if(arrStr.startsWith("[") && arrStr.endsWith("]")) {
			return Arrays.asList(arrStr.substring(1, arrStr.length() - 1).split(",")).stream().map(String::trim).toList().toArray(new String[0]);
		}
		else
			throw new IllegalArgumentException("Not array string.");
	}
}
