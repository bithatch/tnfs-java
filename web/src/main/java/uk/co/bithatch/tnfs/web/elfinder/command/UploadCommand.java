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

import java.nio.ByteBuffer;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

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

		if(LOG.isDebugEnabled()) {
			LOG.debug("Upload request");
		}

		VolumeHandler parentDir = null;
		String target = null;
		String filename = null;
		
		// TODO
		FileTime mtime = null;
		
		FormData uploadPart = null;
		long[] chunkData = null;

		for (var part : content.asBufferedParts(FormData.class)) {
			if(LOG.isDebugEnabled()) {
				LOG.debug("   Part name: {}", part.name());
			}
			if (part.name().equals("target")) {
				target = part.asString();
				parentDir = findTarget(elfinderStorage, target);

				if(LOG.isDebugEnabled()) {
					LOG.debug("        Parent Dir: {}", parentDir.getName());
				}
			} else if (part.name().equals("upload[]")) {
				
				if(part.filename().get().equals("blob")) {
					/* Might be a chunk. Unfortunately it seems we can't tell until we
					 * have read further form data coming after the upload, so we keep 
					 * hold of a reference to the part and deal with it later 
					 * (it will be buffered anyway so the stream will still exist)
					 */
					uploadPart = part;
				}
				else {
					filename = part.filename().get();

					if(LOG.isDebugEnabled()) {
						LOG.debug("        Receiving upload: {}", filename);
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
				}
			} else if (part.name().equals("mtime[]")) {
				mtime = FileTime.from(part.asLong(), TimeUnit.SECONDS);
				if(LOG.isDebugEnabled()) {
					LOG.debug("        Mtime: {}", part.asString());
				}
			} else if (part.name().equals("range")) {
				if(LOG.isDebugEnabled()) {
					LOG.debug("        Range: {}", part.asString());
				}
				var data = part.asString().split(",");
				chunkData = new long[] { Long.parseLong(data[0]), Long.parseLong(data[1]), Long.parseLong(data[2]) }; 
			} else if (part.name().equals("chunk")) {
				if(LOG.isDebugEnabled()) {
					LOG.debug("        Chunk: {}", part.asString());
				}
				var chunkName = part.asString();
				if(chunkName.endsWith(".part")) {
					filename = chunkName.substring(0, chunkName.lastIndexOf('.', chunkName.lastIndexOf('.') - 1));
				}
			} else {
				if(LOG.isDebugEnabled()) {
					LOG.debug("        Content: {}", part.asString());
				}
			}
		}
		
		if(uploadPart != null) {
			if(chunkData  == null) {
				throw new IllegalStateException("Expected `range` data.");
			}
			if(filename  == null) {
				throw new IllegalStateException("Expected `chunk[]` data.");
			}

			if(LOG.isDebugEnabled()) {
				LOG.debug("        Receiving upload: {} @ {}", filename, chunkData[0]);
			}

			var newFile = new VolumeHandler(parentDir, filename);
			if (!newFile.exists()) {
				newFile.createFile();
			}
			var buf = ByteBuffer.allocate(8192);
			var wrtn = 0;
			try (var is = uploadPart.asChannel()) {
				try (var os = newFile.openChannel(StandardOpenOption.WRITE)) {
					os.position(chunkData[0]);
					while(is.read(buf) != -1) {
						buf.flip();
						while(buf.hasRemaining()) {
							wrtn += os.write(buf);
						}
						buf.clear();
					}
				}
			}
			
			if(wrtn != chunkData[1]) {
				throw new IllegalStateException("Chunk of " + wrtn + " not expected size of " + chunkData[1]);
			}
			
			newFile = new VolumeHandler(parentDir, filename);
			if(newFile.getSize() == chunkData[2]) {
				if(LOG.isDebugEnabled()) {
					LOG.debug("        Have complete file of {} at {} bytes", filename, chunkData[2]);
				}
				added.add(newFile);
			}
			else {
				if(LOG.isDebugEnabled()) {
					LOG.debug("        Don't yet have complete file of {} and {} bytes at a current size of {} bytes", filename, newFile.getSize(), chunkData[2]);
				}
			}
		}

//Part name: chunk
//-         Content: sbpsite.zip.2_5.part
//-    Part name: cid
//-         Content: 946170856
// -    Part name: range
//-         Content: 4177924,2088962,11895928
//			var newFile = uploadPart(parentDir, content.asFormData("upload[]"));
//			added.add(newFile);

		if(LOG.isDebugEnabled()) {
			LOG.debug("Upload complete target {}", target);
		}

		json.put(ElFinderConstants.ELFINDER_JSON_RESPONSE_ADDED, buildJsonFilesArray(tx, added));
	}

}
