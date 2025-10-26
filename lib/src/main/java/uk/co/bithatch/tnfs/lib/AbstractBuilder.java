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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;

public class AbstractBuilder<BLDR extends AbstractBuilder<BLDR>> {

	protected Optional<Integer> port = Optional.empty();
	protected Optional<Integer> messageSize = Optional.empty();
	protected Optional<String> hostname = Optional.empty();
	protected Protocol protocol = Protocol.UDP;

	/**
	 * Set the maximum size of a single message. By default, this will be
	 * 
	 * @return this for chaining
	 */
	@SuppressWarnings("unchecked")
	public BLDR withMessageSize(int messageSize) {
		this.messageSize = Optional.of(messageSize);
		return (BLDR)this;
	}

	/**
	 * Use {@link Protocol#TCP}.
	 * 
	 * @return this for chaining
	 */
	public BLDR withTcp() {
		return withProtocol(Protocol.TCP);
	}

	/**
	 * Set the {@link Protocol} to use.
	 * 
	 * @param protocol protocol
	 * @return this for chaining
	 */
	@SuppressWarnings("unchecked")
	public BLDR withProtocol(Protocol protocol) {
		this.protocol = protocol;
		return (BLDR)this;
	}

	/**
	 * Set the port to use. If not provided, {@link TNFS#DEFAULT_PORT} will be
	 * used.
	 * 
	 * @param port port
	 * @return this for chaining
	 */
	public BLDR withPort(int port) {
		return withPort(Optional.of(port));
	}

	/**
	 * Set the port to use. If not provided, {@link TNFS#DEFAULT_PORT} will be
	 * used.
	 * 
	 * @param port port
	 * @return this for chaining
	 */
	@SuppressWarnings("unchecked")
	public BLDR withPort(Optional<Integer> port) {
		this.port = port;
		return (BLDR)this;
	}

	/**
	 * Set the <strong>host</strong> to use. May result in name resolution as
	 * internally {@link InetAddress#getHostName()} will be used resolve the address
	 * to string.
	 * 
	 * @param address address
	 * @return this for chaining
	 */
	public BLDR withHost(InetAddress address) {
		return withHostname(address.getHostName());
	}

	/**
	 * Set the <strong>hostname</strong> to use. This must be either a valid
	 * hostname or IP address. If not provided, <code>localhost</code> will be used.
	 * 
	 * @param SshClientContext sshContext
	 * @return this for chaining
	 */
	@SuppressWarnings("unchecked")
	public BLDR withHostname(String hostname) {
		this.hostname = Optional.of(hostname);
		return (BLDR)this;
	}

	/**
	 * Set the <strong>host</strong> and <strong>port</code> to use from the
	 * provided address. May result in name resolution as internally
	 * {@link InetAddress#getHostName()} will be used resolve the address to string.
	 * 
	 * @param address address
	 * @return this for chaining
	 */
	public BLDR withAddress(InetSocketAddress address) {
		return withHostname(address.getHostName()).withPort(address.getPort());
	}

	/**
	 * Set the <strong>hostname</strong> and <strong>port</code> to use from the
	 * provided address.
	 * 
	 * @param hostname hostname
	 * @param port     port
	 * @return this for chaining
	 */
	public BLDR withAddress(String hostname, int port) {
		return withHostname(hostname).withPort(port);
	}
}