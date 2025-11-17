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
package uk.co.bithatch.tnfs.client.extensions;

import java.io.IOException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ongres.scram.client.ScramClient;
import com.ongres.scram.common.ScramMechanism;
import com.ongres.scram.common.StringPreparation;
import com.ongres.scram.common.exception.ScramInvalidServerSignatureException;
import com.ongres.scram.common.exception.ScramParseException;
import com.ongres.scram.common.exception.ScramServerErrorException;

import uk.co.bithatch.tnfs.client.AbstractTNFSMount;
import uk.co.bithatch.tnfs.client.AbstractTNFSMount.AbstractBuilder;
import uk.co.bithatch.tnfs.client.TNFSClient;
import uk.co.bithatch.tnfs.client.TNFSClientExtension.AbstractTNFSClientExtension;
import uk.co.bithatch.tnfs.lib.Message;
import uk.co.bithatch.tnfs.lib.Version;
import uk.co.bithatch.tnfs.lib.extensions.Extensions;
import uk.co.bithatch.tnfs.lib.extensions.Extensions.ServerFinal;

public class SecureMount extends AbstractTNFSClientExtension {
	
	private final static Logger LOG = LoggerFactory.getLogger(SecureMount.class);
	
	public static class Builder extends AbstractBuilder<Builder> {

		/**
		 * Construct a new mount builder.
		 *
		 * @param path path
		 * @param client client
		 * @return this for chaining
		 */
		Builder(String path, TNFSClient client) {
			super(path, client);
		}

		/**
		 * Create the client from this builders configuration.
		 *
		 * @return client
		 */
		public SecureTNFSMount build() throws IOException {
			return new SecureTNFSMount(this);
		}
	}

	/**
	 * Create a new mount builder for the default mount.
	 * 
	 * @return mount builder
	 */
	public SecureMount.Builder mount() {
		return mount("");
	}
	
	/**
	 * Create a new mount builder that may be used to mount a path.
	 * 
	 * @param path path of mount
	 * @return mount builder
	 */
	public SecureMount.Builder mount(String path) {
		return new SecureMount.Builder(path, client);
	}
	
	public final static class SecureTNFSMount extends AbstractTNFSMount {

		private final int sessionId;
		private final Version serverVersion;

		private SecureTNFSMount(Builder bldr) throws IOException {
			super(bldr);
			
			/* Get the server capabilities to decide what hashing algorithms
			 * we can advertise to actually use, and the server key for
			 * potential channel binding
			 */
			var capsRes = client.send(Extensions.SRVRCAPS, Message.of(Extensions.SRVRCAPS, new Extensions.ServerCaps()));
			var caps = capsRes.result();
			var binding = Arrays.asList(caps.hashAlgos()).stream().map(ScramMechanism::valueOf).filter(f->f.isPlus()).findFirst().isPresent();
			
			LOG.info("Supported Hash Algos: {} (supports binding {})", Arrays.asList(caps.hashAlgos()), binding);
			
			var scramBldr = ScramClient.builder()
				    .advertisedMechanisms(Arrays.asList(caps.hashAlgos()).stream().map(s -> s.replace("_", "-")).toList())
				    .username(username.orElseThrow(() -> new IllegalStateException("Username is required for secure mount.")))
				    .password(password.orElseThrow(() -> new IllegalStateException("Password is required for secure mount.")));
			
			if(binding) {
				scramBldr.channelBinding("speck",  caps.srvKey()); // client supports channel binding
			}
			
			var scramClient = scramBldr.stringPreparation(StringPreparation.NO_PREPARATION).
					build();

			LOG.info("Client decided on: {}",  scramClient.getScramMechanism());

			var clientFirstMsg = scramClient.clientFirstMessage();
			
			LOG.info("Sending Client First: {}",  clientFirstMsg);

			var srvRes = client.send(Extensions.CLNTFRST, Message.of(Extensions.CLNTFRST, new Extensions.ClientFirst(mountPath, clientFirstMsg.toString())));;
			var res = srvRes.result();
			
			try {
				serverVersion = res.version();
				sessionId  = srvRes.mesage().connectionId();
				
				LOG.info("Received Server First: {}", res.serverFirstMessage());
				scramClient.serverFirstMessage(res.serverFirstMessage());
				var clientFinalMsg = scramClient.clientFinalMessage();

//				LOG.info("Auth Message: {}", ScramFunctions.authMessage(clientFirstMsg, srvRes.result(), null));
				
				LOG.info("Sending Client Final: {}", clientFinalMsg);
				ServerFinal finRes = client.sendMessage(Extensions.CLNTFINL, Message.of(sessionId, Extensions.CLNTFINL, new Extensions.ClientFinal(clientFinalMsg.toString())));

				LOG.info("Received Server Final: {}", finRes.serverFinalMessage());
				var serverFinal = scramClient.serverFinalMessage(finRes.serverFinalMessage());
			}
			catch(ScramParseException | ScramServerErrorException | ScramInvalidServerSignatureException spe) {
				throw new IOException("SCRAM error.", spe);
			}
			
			
//			if(res.result().isError()) {
//				throw new TNFSException(res.result(), MessageFormat.format("Failed to mount {0}.", bldr.path));
//			}
//			sessionId = rep.connectionId();
		}

		@Override
		public int sessionId() {
			return sessionId;
		}

		@Override
		public Version serverVersion() {
			return serverVersion;
		}
		
	}
}
