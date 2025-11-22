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
package uk.co.bithatch.tnfs.daemonlib;

import java.io.IOException;
import java.net.InetAddress;

import javax.jmdns.JmDNS;
import javax.jmdns.JmmDNS;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import javax.jmdns.ServiceTypeListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MDNS {

	private static Logger LOG = LoggerFactory.getLogger(MDNS.class);

	private final JmmDNS mmDNS;
	private final JmDNS mDNS;

	public static final String MDNS_TNFS_UDP_LOCAL = "_tnfs._udp.local.";

	public static final String MDNS_TNFS_TCP_LOCAL = "_tnfs._tcp.local.";

	public MDNS(InetAddress address) throws IOException {
		
		if(address == null || address.isAnyLocalAddress()) {
			mmDNS =JmmDNS.Factory.getInstance();
			mDNS = null;
			LOG.info("Using multihome mDNS");
		}
		else {
			if(address.isLoopbackAddress()) {
				LOG.info("Using mDNS on loopback address {}", address);
				mDNS = JmDNS.create(InetAddress.getLoopbackAddress(),  "127.0.0.1");
				mmDNS = null;
			}
			else {
				LOG.info("Using mDNS on {}", address);
				mDNS = JmDNS.create(address);
				mmDNS = null;
			}
		}
	}

	public void removeListener(ServiceListener listener) {

		if(mDNS == null) {
			mmDNS.removeServiceListener(MDNS_TNFS_UDP_LOCAL, listener);
			mmDNS.removeServiceListener(MDNS_TNFS_TCP_LOCAL, listener);
		}
		else {
			mDNS.removeServiceListener(MDNS_TNFS_UDP_LOCAL, listener);
			mDNS.removeServiceListener(MDNS_TNFS_TCP_LOCAL, listener);
		}
	}
	
	public void addListener(ServiceListener listener) {
		if(mDNS == null) {
			mmDNS.addServiceListener(MDNS_TNFS_UDP_LOCAL, listener);
			mmDNS.addServiceListener(MDNS_TNFS_TCP_LOCAL, listener);
		}
		else {
			mDNS.addServiceListener(MDNS_TNFS_UDP_LOCAL, listener);
			mDNS.addServiceListener(MDNS_TNFS_TCP_LOCAL, listener);
		}
	}
	
	public void unregisterAllServices() {
		if(mDNS == null) {
			mmDNS.unregisterAllServices();
		}
		else {
			mDNS.unregisterAllServices();
		}
		
	}

	public void registerService(ServiceInfo serviceInfo) throws IOException {
		if(mDNS == null) {
			mmDNS.registerService(serviceInfo);
		}
		else {
			mDNS.registerService(serviceInfo);
		}
	}

	public ServiceInfo[] list(String type) {
		if(mDNS == null) {
			return mmDNS.list(type);
		}
		else {
			return mDNS.list(type);
		}
	}

	public void close() throws IOException {
		if(mDNS == null) {
			mmDNS.close();
		}
		else {
			mDNS.close();
		}
		
	}
}
