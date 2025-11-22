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
package uk.co.bithatch.tnfs.ldap;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.TypeReflect;

@Reflectable
@TypeReflect(methods = true, classes = true)
public class DummySSLSocketFactory extends SSLSocketFactory {
	private SSLSocketFactory factory;

	public DummySSLSocketFactory() {
		try {
			SSLContext sslcontext = SSLContext.getInstance("TLS");
			sslcontext.init(null, new TrustManager[] { new DummyTrustManager() }, new SecureRandom());
			factory = sslcontext.getSocketFactory();
		} catch (KeyManagementException kme) {
			throw new IllegalArgumentException("Failed to create socket factory", kme);
		} catch (NoSuchAlgorithmException nsae) {
			throw new IllegalArgumentException("Failed to create socket factory", nsae);
		}
	}

	public static SocketFactory getDefault() {
		return new DummySSLSocketFactory();
	}

	@Override
	public String[] getDefaultCipherSuites() {
		return factory.getDefaultCipherSuites();
	}

	@Override
	public String[] getSupportedCipherSuites() {
		return factory.getSupportedCipherSuites();
	}

	@Override
	public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
		return configureSocket((SSLSocket) factory.createSocket(socket, host, port, autoClose));
	}

	@Override
	public Socket createSocket() throws IOException {
		return configureSocket((SSLSocket) factory.createSocket());
	}

	@Override
	public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
			throws IOException {
		return configureSocket((SSLSocket) factory.createSocket(address, port, localAddress, localPort));
	}

	@Override
	public Socket createSocket(InetAddress host, int port) throws IOException {
		return configureSocket((SSLSocket) factory.createSocket(host, port));
	}

	@Override
	public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
			throws IOException, UnknownHostException {
		return configureSocket((SSLSocket) factory.createSocket(host, port, localHost, localPort));
	}

	@Override
	public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
		return configureSocket((SSLSocket) factory.createSocket(host, port));
	}

	private Socket configureSocket(SSLSocket socket) {
		return socket;
	}
}