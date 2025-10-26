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

import java.io.UncheckedIOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Optional;
import java.util.stream.Stream;

public class Net {
	
	public static Stream<InetAddress> allAddresses() {
		try {
			return NetworkInterface.networkInterfaces().
				filter(nif -> {
					try {
						return nif.isUp();
					} catch (SocketException e) {
						return false;
					}
				}).
				filter(nif -> {
					try {
						return !nif.isLoopback();
					} catch (SocketException e) {
						return false;
					}
				}).
				sorted((n1, n2) ->
					Integer.valueOf(n1.getIndex()).compareTo(n2.getIndex())
				).
				flatMap(nif -> nif.inetAddresses());
		} catch (SocketException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	public static Stream<InetAddress> allLANAddresses() {
		return allAddresses().
				filter(a -> !a.isLoopbackAddress());
	}
	
	public static Optional<InetAddress> firstLANAddress() {
		return allLANAddresses().findFirst();
	}
	
	public static Optional<Inet4Address> firstIpv4LANAddress() {
		return allLANAddresses().filter(
			a -> a instanceof Inet4Address
		).map(a -> (Inet4Address)a).findFirst();
	}
	
	public static Optional<Inet6Address> firstIpv6LANAddress() {
		return allLANAddresses().filter(
			a -> a instanceof Inet6Address
		).map(a -> (Inet6Address)a).findFirst();
	}

}
