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

import java.awt.image.BufferedImage;
import java.nio.channels.Channels;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import javax.imageio.ImageIO;

import com.sshtools.uhttpd.UHTTPD.Transaction;

import uk.co.bithatch.tnfs.web.elfinder.service.ElfinderStorage;

public class TmbCommand extends AbstractCommand implements ElfinderCommand {
    @Override
    public void execute(ElfinderStorage elfinderStorage, Transaction tx) throws Exception {

        var target = tx.parameter("target").asString();
        var fsi = super.findTarget(elfinderStorage, target);

        var dateTime = ZonedDateTime.now();
        var pattern = "d MMM yyyy HH:mm:ss 'GMT'";
        var dateTimeFormatter = DateTimeFormatter.ofPattern(pattern);

        try (var is = fsi.openInputStream()) {
            var image = ImageIO.read(is);
            var width = 80;
            
            var fac = Math.min((float)width / image.getHeight(), (float)width / image.getWidth());
            var b = (BufferedImage)image.getScaledInstance((int)(image.getWidth() * fac), (int)(image.getHeight() * fac), BufferedImage.SCALE_SMOOTH);
            
            tx.header("Last-Modified", dateTimeFormatter.format(dateTime));
            tx.header("Expires", dateTimeFormatter.format(dateTime.plusYears(2)));
            try(var wtr = Channels.newOutputStream(tx.responseWriter())) {
                ImageIO.write(b, "png", wtr);
            }
        }
    }
}
