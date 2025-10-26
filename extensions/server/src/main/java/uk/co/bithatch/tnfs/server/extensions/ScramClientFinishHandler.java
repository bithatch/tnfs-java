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

import java.util.Base64;

import com.ongres.scram.common.ClientFirstMessage;
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
		    System.out.println("CFIN: " + mountmsg.clientFinalMessage());
		    
		    for(var attrVal : attributeValues) {
		    	if(attrVal.startsWith("r=")) {
		    		nonce = attrVal.substring(2);
		    	}
		    	else if(attrVal.startsWith("c=")) {
		    		cbindData = Base64.getDecoder().decode(attrVal.substring(2));
		    	}
		    	else if(attrVal.startsWith("p=")) {
		    		proof = Base64.getDecoder().decode(attrVal.substring(2));
		    	}
		    	else
		    		throw new IllegalArgumentException();
		    }


		    var session = context.session();
			var state = session.state();

		    var clientNonce = (String)state.get(ScramClientFirstHandler.CLIENT_NONCE);
		    var principal = (ScramPrincipal)state.get(ScramClientFirstHandler.PRINCIPAL);
		    
		    if(!nonce.startsWith(clientNonce)) {
				throw new TNFSException(ResultCode.PERM, "Authentication failed for " + principal.getName());		    	
		    }

		    var clientFirstMessage = (ClientFirstMessage)state.get(ScramClientFirstHandler.CLIENT_FIRST);
		    var serverFirstMessage = (ServerFirstMessage)state.get(ScramClientFirstHandler.SERVER_FIRST);
		    var authMessage = ScramFunctions.authMessage(clientFirstMessage, serverFirstMessage, cbindData);
		    
		    System.out.println("auth message: " + authMessage);
		    
		    /* TODO remove session on failure */
		    
		   if( ScramFunctions.verifyClientProof(principal.getMechanism(), proof, Base64.getDecoder().decode(principal.getStoredKey()), authMessage)) {
			   	session.authenticate();
			   
				var sfinal = new ServerFinalMessage(
					Base64.getEncoder().encode(
						ScramFunctions.serverSignature(principal.getMechanism(), context.server().serverKey(), authMessage)
					)
				);
				
				System.out.println("SFINAL: " + sfinal.toString());
//				
				return new  ServerFinal(
						ResultCode.SUCCESS,
						sfinal.toString()
						);
		   }
		   else
				throw new TNFSException(ResultCode.PERM, "Authentication failed for " + principal.getName());
			
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
