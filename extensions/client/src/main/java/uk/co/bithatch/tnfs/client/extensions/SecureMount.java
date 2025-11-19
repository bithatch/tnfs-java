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
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.KeyAgreement;
import javax.crypto.interfaces.DHPublicKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.bithatch.tnfs.client.AbstractTNFSMount;
import uk.co.bithatch.tnfs.client.AbstractTNFSMount.AbstractBuilder;
import uk.co.bithatch.tnfs.client.TNFSClient;
import uk.co.bithatch.tnfs.client.TNFSClientExtension.AbstractTNFSClientExtension;
import uk.co.bithatch.tnfs.lib.Command;
import uk.co.bithatch.tnfs.lib.Debug;
import uk.co.bithatch.tnfs.lib.Message;
import uk.co.bithatch.tnfs.lib.TNFS;
import uk.co.bithatch.tnfs.lib.Version;
import uk.co.bithatch.tnfs.lib.extensions.Crypto;
import uk.co.bithatch.tnfs.lib.extensions.Extensions;
import uk.co.bithatch.tnfs.lib.extensions.Extensions.SecureMountResult;
import uk.co.bithatch.tnfs.lib.extensions.SpeckEngine;

public class SecureMount extends AbstractTNFSClientExtension {
	
	private final static Logger LOG = LoggerFactory.getLogger(SecureMount.class);
	
	public static class Builder extends AbstractBuilder<Builder> {
		
		private int blockSize = SpeckEngine.SPECK_128;
		private Optional<Integer> keySize = Optional.empty();

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
		
		/**
		 * Set the block size in bits. See {@link SpeckEngine#SPECK_128} and others.
		 * 
		 * @param blockSize block size
		 * @return this for chaining
		 */
		public Builder withBlockSize(int blockSize) {
			this.blockSize = blockSize;
			return  this;
		}
		
		/**
		 * Set the key size in bits. If not set, default is twice the key size.
		 * 
		 * @param keySize size in bit
		 * @return this for chaining
		 */
		public Builder withKeySize(int keySize) {
			this.keySize = Optional.of(keySize);
			return  this;
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
		private final int blockSize;
		private final int keySize;

		private SecureTNFSMount(Builder bldr) throws IOException {
			super(bldr);
			
			this.blockSize = bldr.blockSize;
			this.keySize = bldr.keySize.orElse(blockSize * 2);
			
			 // 1. Generate client's DH key pair
			try {
		        var kpg = KeyPairGenerator.getInstance("DH");
		        // This chooses DH parameters; the server will extract them from our public key
		        kpg.initialize(512);
		        var clientKp = kpg.generateKeyPair();
		        var clientPubEnc = clientKp.getPublic().getEncoded();
		        
		        if(LOG.isDebugEnabled()) {
		        	LOG.debug("Client public key: {}", Base64.getEncoder().encodeToString(clientPubEnc));
		        }
		        
		        var dhParams = ((DHPublicKey) clientKp.getPublic()).getParams();

		        if(LOG.isDebugEnabled()) {
		        	LOG.info("DH Params: G={}, L={}, P={}", dhParams.getG(), dhParams.getL(), dhParams.getP());
		        }
		
		        // 2. Send opening message and receive server public key, session ID and server version
				var serverResult= client().send(this, 
						Extensions.SECMNT, Message.of(0, Extensions.SECMNT, 
								new Extensions.SecureMount(TNFS.PROTOCOL_VERSION, keySize, blockSize, clientPubEnc)));
				SecureMountResult serverReply = serverResult.result();
				sessionId = serverResult.message().connectionId();
				serverVersion = serverReply.version();
				
				var serverPubEnc = serverReply.key();

		        if(LOG.isDebugEnabled()) {
		        	LOG.debug("Server public key: {}", Base64.getEncoder().encodeToString(serverPubEnc));
		        }
				
		        // 3. Rebuild server public key
		        var kf = KeyFactory.getInstance("DH");
		        var x509KeySpec = new X509EncodedKeySpec(serverPubEnc);
		        var serverPubKey = kf.generatePublic(x509KeySpec);
		        
		        // 4. Perform key agreement
		        var ka = KeyAgreement.getInstance("DH");
		        ka.init(clientKp.getPrivate());
		        ka.doPhase(serverPubKey, true);
		        var sharedSecret = ka.generateSecret();

		        if(LOG.isTraceEnabled()) {
		        	LOG.trace("Shared secret: {}", Base64.getEncoder().encodeToString(sharedSecret));
		        }
		        
		        // 5. Derive symmetric key of desired size
		        var derivedKey = Crypto.deriveKey(sharedSecret, keySize);

		        if(LOG.isTraceEnabled()) {
		        	LOG.trace("Derived key: {}", Base64.getEncoder().encodeToString(derivedKey));
		        }
				setupEncryption(derivedKey, blockSize);
				
				// 6. Send original mount command but with a sessionId
				client().sendMessage(this, 
						Command.MOUNT, Message.of(sessionId, Command.MOUNT, 
								new Command.Mount(mountPath, username, password)));
				
				LOG.info("Securely mounted {}", mountPath);
			}
			catch(NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException nsae) {
				throw new IOException("Failed to start encryption.", nsae);
			}
		}
		
		public int blockSize() {
			return blockSize;
		}
		
		public int keySize() {
			return keySize;
		}

		@Override
		public int sessionId() {
			return sessionId;
		}

		@Override
		public Version serverVersion() {
			return serverVersion;
		}
		
		private void setupEncryption(byte[] key, int blockSz) {
			/* Decryption */
	        
			var decEngine = new SpeckEngine(blockSz);
	        decEngine.init(false, key);
			inProcessors().add((ctx, bufin) -> {

				if(LOG.isTraceEnabled()) {
					LOG.trace("Decrypting {} bytes", bufin.remaining());
					LOG.trace("  " + Debug.dump(bufin));
				}
				
				var sz = bufin.remaining() - 2;
				var thisblkSz = sz;
				if(thisblkSz < blockSz / 8) {

					if(LOG.isTraceEnabled()) {
						LOG.trace("Short block of {}, increasing to {}", thisblkSz, blockSz / 8);
					}
					
					thisblkSz = blockSz / 8;
					
					bufin.limit(2 + thisblkSz);
				}
				
				var work  = new byte[thisblkSz];
				bufin.get(2, work, 0, sz);
				decEngine.processBlock(work, 0, work, 0);
				bufin.put(2, work, 0, thisblkSz);
			});
			
			/* Encryption */
	        var encEngine = new SpeckEngine(blockSz);
	        encEngine.init(true, key);
			outProcessors().add((ctx, bufin) -> {	

				if(LOG.isTraceEnabled()) {
					LOG.trace("Encrypting {} bytes", bufin.remaining());
					LOG.trace("  " + Debug.dump(bufin));
				}
				
				var sz = bufin.remaining() - 2;
				var thisblkSz = sz;
				if(thisblkSz < blockSz / 8) {
					
					if(LOG.isTraceEnabled()) {
						LOG.trace("Short block of {}, increasing to {}", thisblkSz, blockSz / 8);
					}
					
					thisblkSz = blockSz / 8;
					
					bufin.limit(2 + thisblkSz);
				}
				
				var work  = new byte[thisblkSz];
				bufin.get(2, work, 0, sz);
				encEngine.processBlock(work, 0, work, 0);
				
				bufin.put(2, work, 0, thisblkSz);
			});
		}
		
	}
}
