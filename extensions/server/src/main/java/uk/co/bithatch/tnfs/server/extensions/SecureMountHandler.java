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
package uk.co.bithatch.tnfs.server.extensions;

import static uk.co.bithatch.tnfs.server.Tasks.ioCall;

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.KeyAgreement;
import javax.crypto.interfaces.DHPublicKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.bithatch.tnfs.lib.Command;
import uk.co.bithatch.tnfs.lib.Command.Result;
import uk.co.bithatch.tnfs.lib.Debug;
import uk.co.bithatch.tnfs.lib.Message;
import uk.co.bithatch.tnfs.lib.ResultCode;
import uk.co.bithatch.tnfs.lib.TNFS;
import uk.co.bithatch.tnfs.lib.extensions.Crypto;
import uk.co.bithatch.tnfs.lib.extensions.Extensions;
import uk.co.bithatch.tnfs.lib.extensions.Extensions.SecureMount;
import uk.co.bithatch.tnfs.lib.extensions.Extensions.SecureMountResult;
import uk.co.bithatch.tnfs.lib.extensions.SpeckEngine;
import uk.co.bithatch.tnfs.server.TNFSMessageHandler;

public class SecureMountHandler implements TNFSMessageHandler {
	private final static Logger LOG = LoggerFactory.getLogger(SecureMountHandler.class);
	private static final int MAX_KEY_BITS = 256;
	private static final int MIN_KEY_BITS = 64;
	
	
	@Override
	public Result handle(Message message, HandlerContext context) {
		SecureMount mountmsg = message.payload();

		if(LOG.isDebugEnabled()) {
	        LOG.debug("Client public key: {}", Base64.getEncoder().encodeToString(mountmsg.key()));
	        LOG.debug("Key size: {}, Block size: {}", mountmsg.derivedKeyBits(), mountmsg.blockSize());
		}
		
		var res = ioCall(() -> {
			
			var session = context.newSession(mountmsg.version());
			
			var clientKeyBits = mountmsg.derivedKeyBits();
			if (clientKeyBits < MIN_KEY_BITS || clientKeyBits > MAX_KEY_BITS || clientKeyBits % 8 != 0) {
				throw new IllegalArgumentException("Client requested invalid key size: " + clientKeyBits + " (allowed: "
						+ MIN_KEY_BITS + "-" + MAX_KEY_BITS + " bits, multiple of 8)");
			}
			
			var blockSize = mountmsg.blockSize();
			
			 // 2. Rebuild client public key and extract DH parameters
	        var kf = KeyFactory.getInstance("DH");
	        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(mountmsg.key());

			if(LOG.isDebugEnabled()) {
				LOG.debug("Key format: {}, Algorithm: {}", x509KeySpec.getFormat(), x509KeySpec.getAlgorithm());
			}
	        var clientPubKey = kf.generatePublic(x509KeySpec);

	        var dhParams = ((DHPublicKey) clientPubKey).getParams();
	        
			if(LOG.isDebugEnabled()) {
				LOG.debug("DH Params: G={}, L={}, P={}", dhParams.getG(), dhParams.getL(), dhParams.getP());
			}

	        // 3. Generate server DH key pair with same parameters
	        var kpg = KeyPairGenerator.getInstance("DH");
	        kpg.initialize(dhParams);
	        var serverKp = kpg.generateKeyPair();
	        var serverPubEnc = serverKp.getPublic().getEncoded();

			if(LOG.isDebugEnabled()) {
				LOG.debug("Server public key: {}", Base64.getEncoder().encodeToString(serverPubEnc));
			}

	        // 4. Perform key agreement
	        var ka = KeyAgreement.getInstance("DH");
	        ka.init(serverKp.getPrivate());
	        ka.doPhase(clientPubKey, true);
	        var sharedSecret = ka.generateSecret();

			if(LOG.isTraceEnabled()) {
				LOG.trace("Shared secret: {}", Base64.getEncoder().encodeToString(sharedSecret));
			}

	        // 5. Derive symmetric key of client-chosen size
	        var derivedKey = Crypto.deriveKey(sharedSecret, clientKeyBits);

			if(LOG.isDebugEnabled()) {
				LOG.trace("Derived key: {}", Base64.getEncoder().encodeToString(derivedKey));
			}
	        
	        var encEngine = new SpeckEngine(blockSize);
	        encEngine.init(true, derivedKey);
	        
	        var decEngine = new SpeckEngine(blockSize);
	        decEngine.init(false, derivedKey);

			if(LOG.isDebugEnabled()) {
				LOG.debug("Encryption ready");
			}
		        
			
			/* Decryption */
			session.inProcessors().add((ctx, bufin) -> {		

				if(LOG.isTraceEnabled()) {
					LOG.trace("Decrypting {} bytes", bufin.remaining());
					LOG.trace("  " + Debug.dump(bufin));
				}
				
				var sz = bufin.remaining() - 2;  // first two bytes unencrypted connection id
				var thisblkSz = sz;
				if(thisblkSz < blockSize / 8) {

					if(LOG.isTraceEnabled()) {
						LOG.trace("Short buffer of {}, increasing to {}", thisblkSz, blockSize / 8);
					}
					
					thisblkSz = blockSize / 8;
					
					bufin.limit(2 + thisblkSz);
				}
				
				var work  = new byte[thisblkSz];
				bufin.get(2, work, 0, sz);
				decEngine.processBlock(work, 0, work, 0);
				bufin.put(2, work, 0, thisblkSz);
			});
			
			/* Encryption */
			session.outProcessors().add((ctx, bufin) -> {
				
				if(LOG.isTraceEnabled()) {
					LOG.trace("Encrypting {} bytes", bufin.remaining());
					LOG.trace("  " + Debug.dump(bufin));
				}

				var sz = bufin.remaining() - 2;  // first two bytes unencrypted connection id
				var thisblkSz = sz;
				if(thisblkSz < blockSize / 8) {

					if(LOG.isTraceEnabled()) {
						LOG.trace("Short buffer of {}, increasing to {}", thisblkSz, blockSize / 8);
					}
					
					thisblkSz = blockSize / 8;
					
					bufin.limit(2 + thisblkSz);
				}
				
				var work  = new byte[thisblkSz];
				bufin.get(2, work, 0, sz);
				encEngine.processBlock(work, 0, work, 0);
				bufin.put(2, work, 0, thisblkSz);
			});
			
			/* Return out public key */
			
			return new SecureMountResult(ResultCode.SUCCESS, TNFS.PROTOCOL_VERSION, context.server().retryTime(), serverPubEnc);
		}
		, code -> new SecureMountResult(code, TNFS.PROTOCOL_VERSION));
		return res;
	}

	@Override
	public Command<?, ?> command() {
		return Extensions.SECMNT;
	}

	@Override
	public boolean needsSession() {
		return false;
	}

}
