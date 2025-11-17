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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ongres.scram.common.ClientFirstMessage;
import com.ongres.scram.common.Gs2Header;
import com.ongres.scram.common.ScramFunctions;
import com.ongres.scram.common.ServerFinalMessage;
import com.ongres.scram.common.ServerFirstMessage;
import com.ongres.scram.common.exception.ScramParseException;

import uk.co.bithatch.tnfs.lib.Command;
import uk.co.bithatch.tnfs.lib.Command.Result;
import uk.co.bithatch.tnfs.lib.Message;
import uk.co.bithatch.tnfs.lib.ResultCode;
import uk.co.bithatch.tnfs.lib.TNFSException;
import uk.co.bithatch.tnfs.lib.extensions.Extensions;
import uk.co.bithatch.tnfs.lib.extensions.Extensions.ClientFinal;
import uk.co.bithatch.tnfs.lib.extensions.Extensions.ServerFinal;
import uk.co.bithatch.tnfs.server.TNFSMessageHandler;

public class ScramClientFinishHandler implements TNFSMessageHandler {
	private final static Logger LOG = LoggerFactory.getLogger(ScramClientFinishHandler.class);

	@Override
	public Result handle(Message message, HandlerContext context) {
		ClientFinal mountmsg = message.payload();
		
		var res = ioCall(() -> {
			
			byte[] cbindData = null;
			String nonce = null;
			byte[] proof = null;

		    var attributeValues = StringWritableCsv.parseFrom(mountmsg.clientFinalMessage());
		    if (attributeValues.length != 3) {
		      throw new ScramParseException("Invalid client-final-message");
		    }
		    
		    LOG.info("Received Client Final: {}", mountmsg.clientFinalMessage());
		    
		    for(var attrVal : attributeValues) {
		    	if(attrVal.startsWith("r=")) {
		    		nonce = attrVal.substring(2);
		    	}
		    	else if(attrVal.startsWith("c=")) {
		    		var cbindStr = new String(Base64.getDecoder().decode(attrVal.substring(2)), StandardCharsets.UTF_8);		    
				    LOG.info("CBind Str: {}", cbindStr);
				    var gs2hdr = Gs2Header.parseFrom(cbindStr);		    
				    LOG.info("AuthzID: {}", gs2hdr.getAuthzid());
				    LOG.info("Binding Name: {}", gs2hdr.getChannelBindingName());
				    LOG.info("Binding Flag: {}", gs2hdr.getChannelBindingFlag());
				    if(cbindData != null) {
				    	LOG.info("CBind Data: {}", Base64.getEncoder().encodeToString(cbindData));
				    }
		    	}
		    	else if(attrVal.startsWith("p=")) {
		    		proof = Base64.getDecoder().decode(attrVal.substring(2));
		    	}
		    	else
		    		throw new IllegalArgumentException();
		    }
		    
		    LOG.info("Proof: {}", Base64.getEncoder().encodeToString(proof));

		    var session = context.session();
			var state = session.state();

		    var clientNonce = (String)state.get(ScramClientFirstHandler.CLIENT_NONCE);
		    var principal = (ScramPrincipal)state.get(ScramClientFirstHandler.PRINCIPAL);
		    
		    LOG.info("Nonce: {} (vs {})", nonce, clientNonce);

		    var clientFirstMessage = (ClientFirstMessage)state.get(ScramClientFirstHandler.CLIENT_FIRST);
		    var serverFirstMessage = (ServerFirstMessage)state.get(ScramClientFirstHandler.SERVER_FIRST);
		    
		    if(!nonce.equals(clientNonce + serverFirstMessage.getServerNonce())) {
				throw new TNFSException(ResultCode.PERM, "Authentication failed for " + principal.getName());		    	
		    }
		    
		    var authMessage = ScramFunctions.authMessage(clientFirstMessage, serverFirstMessage, cbindData);
		    var storedKey = principal.getStoredKey();
		    LOG.info("Verify Auth Message: {}", authMessage);
		    LOG.info("Using Stored Key: {}", storedKey);
		    
		    /* TODO remove session on failure */
		    
		   if( ScramFunctions.verifyClientProof(principal.getMechanism(), proof, Base64.getDecoder().decode(storedKey), authMessage)) {
			   	session.authenticate();

			    var srvkey = Base64.getDecoder().decode(principal.getServerKey());
				LOG.info("Server key is : {}", Base64.getEncoder().encodeToString(srvkey));
				var srvsig = ScramFunctions.serverSignature(principal.getMechanism(), srvkey, authMessage);
				LOG.info("Server signature is : {}", Base64.getEncoder().encodeToString(srvsig));
				
				var sfinal = new ServerFinalMessage(
					srvsig
				);

			    LOG.info("Sending Server Final: {}", sfinal);
//				
				return new  ServerFinal(
						ResultCode.SUCCESS,
						sfinal.toString()
						);
		   }
		   else {
			   var sfinal = new ServerFinalMessage("invalid-proof");
			   return new  ServerFinal(
					ResultCode.ACCESS,
					sfinal.toString()
				);
		   }
		   

//		    ConcurrentMap<String, String> map = new ConcurrentHashMap<>();
//		    map.put("invalid-encoding", "The message format or encoding is incorrect");
//		    map.put("extensions-not-supported", "Requested extensions are not recognized by the server");
//		    map.put("invalid-proof", "The client-provided proof is invalid");
//		    map.put("channel-bindings-dont-match",
//		        "Channel bindings sent by the client don't match those expected by the server.");
//		    map.put("server-does-support-channel-binding",
//		        "Server doesn't support channel binding at all.");
//		    map.put("channel-binding-not-supported", "Channel binding is not supported for this user");
//		    map.put("unsupported-channel-binding-type",
//		        "The requested channel binding type is not supported.");
//		    map.put("unknown-user", "The specified username is not recognized");
//		    map.put("invalid-username-encoding",
//		        "The username encoding is invalid (either invalid UTF-8 or SASLprep failure)");
//		    map.put("no-resources", "The server lacks resources to process the request");
//		    map.put("other-error", "A generic error occurred that doesn't fit into other categories");
//		    return map;
			
		}
		, code -> new ServerFinal(code));
		return res;
	}

	@Override
	public Command<?, ?> command() {
		return Extensions.CLNTFINL;
	}

	@Override
	public boolean needsAuthentication() {
		return false;
	}

}
