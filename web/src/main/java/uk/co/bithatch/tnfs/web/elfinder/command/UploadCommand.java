/*
 * #%L
 * %%
 * Copyright (C) 2015 Trustsystems Desenvolvimento de Sistemas, LTDA.
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the Trustsystems Desenvolvimento de Sistemas, LTDA. nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package uk.co.bithatch.tnfs.web.elfinder.command;

import java.io.IOException;
import java.util.ArrayList;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.uhttpd.UHTTPD.FormData;
import com.sshtools.uhttpd.UHTTPD.Transaction;

import uk.co.bithatch.tnfs.web.elfinder.ElFinderConstants;
import uk.co.bithatch.tnfs.web.elfinder.service.ElfinderStorage;
import uk.co.bithatch.tnfs.web.elfinder.service.VolumeHandler;

public class UploadCommand extends AbstractJsonCommand implements ElfinderCommand {
	static Logger LOG = LoggerFactory.getLogger(UploadCommand.class);

	@Override
	protected void execute(ElfinderStorage elfinderStorage, Transaction tx, JSONObject json) throws Exception {

		var added = new ArrayList<VolumeHandler>();
		var content = tx.request();
//       	var target = content.asFormData(ElFinderConstants.ELFINDER_PARAMETER_TARGET).asString();

//		LOG.info("Upload request for target {}", target);
		LOG.info("Upload request");

		VolumeHandler parentDir = null;
		String target = null;
		String tempName = null;

		// TODO something not right in uhttpd. (earlier parts already ready from content
		// in TNFSWeb.apiPost
		for (var part : content.asBufferedParts(FormData.class)) {
			LOG.info("   Part name: {}", part.name());
			if (part.name().startsWith("target")) {
				target = part.asString();
				parentDir = findTarget(elfinderStorage, target);
				LOG.info("        Parent Dir: {}", parentDir);
			} else if (part.name().startsWith("upload")) {
				
				var filename = part.filename().get();
				LOG.info("        Receiving upload: {}", filename);
				
				if(filename.equals("blob")) {
					if(tempName == null)
						tempName = "upload-" + Integer.toUnsignedLong(tx.hashCode());
					filename = tempName;
				}
				
				var newFile = new VolumeHandler(parentDir, filename);
				if (!newFile.exists()) {
					newFile.createFile();
				}
				try (var is = part.asStream()) {
					try (var os = newFile.openOutputStream()) {
						is.transferTo(os);
					}
				}
				added.add(newFile);
			} else {
				LOG.info("        Content: {}", part.asString());
			}
		}

//			var newFile = uploadPart(parentDir, content.asFormData("upload[]"));
//			added.add(newFile);

		LOG.info("Upload complete target {}", target);

		json.put(ElFinderConstants.ELFINDER_JSON_RESPONSE_ADDED, buildJsonFilesArray(tx, added));
	}

}
