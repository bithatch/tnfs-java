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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.channels.Channels;

import com.sshtools.uhttpd.UHTTPD.Named;
import com.sshtools.uhttpd.UHTTPD.Transaction;

import uk.co.bithatch.tnfs.web.elfinder.service.ElfinderStorage;

public class FileCommand extends AbstractCommand implements ElfinderCommand {

    public static final String STREAM = "1";

    @Override
    public void execute(ElfinderStorage elfinderStorage, Transaction tx) throws Exception {
        var target = tx.parameter("target").asString();
        var download = STREAM.equals(tx.parameterOr("target").map(Named::asString).orElse(""));
        var fsi = super.findTarget(elfinderStorage, target);
        var mime = fsi.getMimeType();
        var fileName = fsi.getName();

        tx.responseType(mime);
        tx.responseLength(fsi.getSize());
        
        // TODO uhttp has no way to set this
//        response.setCharacterEncoding("utf-8");
        
        if (download) {
            tx.header("Content-Disposition",
                    "attachments; " + getAttachementFileName(fileName, tx.headerOr("USER-AGENT").orElse("")));
            tx.header("Content-Transfer-Encoding", "binary");
        }
        
        try (var is = fsi.openInputStream()) {
	        try(var os = Channels.newOutputStream(tx.responseWriter())) {
	        	is.transferTo(os);
	        }
        }

    }

    private String getAttachementFileName(String fileName, String userAgent) throws UnsupportedEncodingException {
        if (userAgent != null) {
            userAgent = userAgent.toLowerCase();

            if (userAgent.contains("msie")) {
                return "filename=\"" + URLEncoder.encode(fileName, "UTF8") + "\"";
            }

            if (userAgent.contains("opera")) {
                return "filename*=UTF-8''" + URLEncoder.encode(fileName, "UTF8");
            }
            if (userAgent.contains("safari")) {
                return "filename=\"" + new String(fileName.getBytes("UTF-8"), "ISO8859-1") + "\"";
            }
            if (userAgent.contains("mozilla")) {
                return "filename*=UTF-8''" + URLEncoder.encode(fileName, "UTF8");
            }
        }

        return "filename=\"" + URLEncoder.encode(fileName, "UTF8") + "\"";
    }
}
