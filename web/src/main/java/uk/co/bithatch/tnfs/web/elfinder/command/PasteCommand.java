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

import org.json.JSONObject;

import com.sshtools.uhttpd.UHTTPD.Transaction;

import uk.co.bithatch.tnfs.web.elfinder.ElFinderConstants;
import uk.co.bithatch.tnfs.web.elfinder.service.ElfinderStorage;
import uk.co.bithatch.tnfs.web.elfinder.service.VolumeHandler;

import java.util.ArrayList;
import java.util.List;

public class PasteCommand extends AbstractJsonCommand implements ElfinderCommand {
    public static final String INT_CUT = "1";

    @Override
    protected void execute(ElfinderStorage elfinderStorage, Transaction tx, JSONObject json) throws Exception {
        var targets = tx.parameter(ElFinderConstants.ELFINDER_PARAMETER_TARGETS).values();
        var destination = tx.parameter(ElFinderConstants.ELFINDER_PARAMETER_FILE_DESTINATION).asString();
        var cut = INT_CUT.equals(tx.parameter(ElFinderConstants.ELFINDER_PARAMETER_CUT).asString());

        List<VolumeHandler> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();

        var vhDst = findTarget(elfinderStorage, destination);

        for (var target : targets) {
            VolumeHandler vhTarget = findTarget(elfinderStorage, target);
            final String name = vhTarget.getName();
            VolumeHandler newFile = new VolumeHandler(vhDst, name);
            createAndCopy(vhTarget, newFile);
            added.add(newFile);

            if (cut) {
                vhTarget.delete();
                removed.add(vhTarget.getHash());
            }
        }

        json.put(ElFinderConstants.ELFINDER_JSON_RESPONSE_ADDED, buildJsonFilesArray(tx, added));
        json.put(ElFinderConstants.ELFINDER_JSON_RESPONSE_REMOVED, removed.toArray());
    }
}
