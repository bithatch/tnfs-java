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

import java.util.List;

import org.json.JSONObject;

import com.sshtools.uhttpd.UHTTPD.Transaction;

import uk.co.bithatch.tnfs.web.elfinder.ElFinderConstants;
import uk.co.bithatch.tnfs.web.elfinder.core.Target;
import uk.co.bithatch.tnfs.web.elfinder.service.ElfinderStorage;
import uk.co.bithatch.tnfs.web.elfinder.service.VolumeHandler;
import uk.co.bithatch.tnfs.web.elfinder.support.archiver.Archiver;
import uk.co.bithatch.tnfs.web.elfinder.support.archiver.ArchiverType;

/**
 * Defines how to execute the archive command.
 *
 * @author Thiago Gutenberg Carvalho da Costa
 */
public class ArchiveCommand extends AbstractJsonCommand implements ElfinderCommand {

    @Override
    protected void execute(ElfinderStorage elfinderStorage, Transaction tx, JSONObject json) throws Exception {
        var targets = tx.parameter(ElFinderConstants.ELFINDER_PARAMETER_TARGETS).values();
        var type = tx.parameter(ElFinderConstants.ELFINDER_PARAMETER_TYPE).asString();

        List<Target> targetList = findTargets(elfinderStorage, targets);

        try {
            Archiver archiver = ArchiverType.of(type).getStrategy();
            Target targetArchive = archiver.compress(targetList.toArray(new Target[targetList.size()]));

            Object[] archiveInfos = {getTargetInfo(tx, new VolumeHandler(targetArchive, elfinderStorage))};
            json.put(ElFinderConstants.ELFINDER_JSON_RESPONSE_ADDED, archiveInfos);

        } catch (Exception e) {
            json.put(ElFinderConstants.ELFINDER_JSON_RESPONSE_ERROR, "Unable to create the archive! Error: " + e);
        }

    }
}
