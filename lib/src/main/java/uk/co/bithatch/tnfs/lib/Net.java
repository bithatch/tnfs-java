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
package uk.co.bithatch.tnfs.lib;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.List;

public class Net {

	public static InetAddress parseAddress(String address) {
		return parseAddress(address, "localhost");
	}
	
	public static InetAddress parseAddress(String address, String defaultAddress) {
		try {
			if("%".equals(address)) {
				return getIpAddress();
			}
			else if(address == null || address.equals("")) {
				if(defaultAddress == null || defaultAddress.equals(""))
					return InetAddress.getLoopbackAddress();
				else
					return InetAddress.getByName(defaultAddress);
			}
			else {
				if(address.equals("*")) {
					if(Boolean.getBoolean("java.net.preferIPv6Address")) {
						return InetAddress.getByName("::");
					}
					else {
						return InetAddress.getByName("0.0.0.0");
					}
				}
				else {
					return InetAddress.getByName(address);
				}
			}
		}
		catch(UnknownHostException uhe) {
			throw new UncheckedIOException(uhe);
		}
	}
	
	/**
     * If running in container attempt to get the host address else return localhost. Retrieves the IP address of the local
     * machine. It iterates through all available
     * network interfaces and checks their IP addresses. If a suitable non-loopback,
     * site-local address is found, it is returned as the IP address of the local machine.
     *
     * @return the {@link InetAddress} representing the IP address of the local machine.
     * @throws UnknownHostException if the local host name could not be resolved into an address.
     * @throws SocketException if an I/O error occurs when querying the network interfaces.
     */
    public static InetAddress getIpAddress()  {
        // Get all network interfaces
    	try {
	        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
	
	        InetAddress localhost = InetAddress.getLocalHost();
	
	        if (!isRunningInContainer()) {
	            return localhost;
	        }
	
	        // Iterate through all interfaces
	        while (networkInterfaces.hasMoreElements()) {
	            NetworkInterface networkInterface = networkInterfaces.nextElement();
	
	            // Get all IP addresses for each network interface
	            Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
	
	            while (inetAddresses.hasMoreElements()) {
	                InetAddress inetAddress = inetAddresses.nextElement();
	
	                if (!inetAddress.isLoopbackAddress() && inetAddress.isSiteLocalAddress()) {
	                    // Print out information about each IP address
	                    String displayName = networkInterface.getDisplayName();
	
	                    if (displayName.startsWith("wlan") || displayName.startsWith("eth")) {
	                        localhost = inetAddress;
	                    }
	                }
	            }
	        }
	        return localhost;
    	}
    	catch(IOException ioe) {
    		throw new UncheckedIOException(ioe);
    	}
    }

    /**
     * Determines if the application is running inside a container (such as Docker or Kubernetes).
     * This is done by inspecting the '/proc/1/cgroup' file and checking for the presence of
     * "docker" or "kubepods". Additionally, it checks specific environment variables to verify
     * the container environment.
     *
     * @return {@code true} if the application is running inside a container; {@code false} otherwise.
     */
    public static boolean isRunningInContainer() {
        try {
            List<String> lines = Files.readAllLines(Paths.get("/proc/1/cgroup"));
            for (String line : lines) {
                if (line.contains("docker") || line.contains("kubepods")) {
                    return true;
                }
            }
        } catch (IOException e) {
            // Ignore, likely not in a container if the file doesn't exist
        }

        // check environment variables
        return System.getenv("CONTAINER") != null || System.getenv("KUBERNETES_SERVICE_HOST") != null;
    }

}
