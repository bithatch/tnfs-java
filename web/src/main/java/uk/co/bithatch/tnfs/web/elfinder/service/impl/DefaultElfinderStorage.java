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
package uk.co.bithatch.tnfs.web.elfinder.service.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import uk.co.bithatch.tnfs.web.elfinder.ElFinderConstants;
import uk.co.bithatch.tnfs.web.elfinder.core.Target;
import uk.co.bithatch.tnfs.web.elfinder.core.Volume;
import uk.co.bithatch.tnfs.web.elfinder.core.VolumeSecurity;
import uk.co.bithatch.tnfs.web.elfinder.core.impl.SecurityConstraint;
import uk.co.bithatch.tnfs.web.elfinder.service.ElfinderStorage;
import uk.co.bithatch.tnfs.web.elfinder.service.ThumbnailWidth;

public class DefaultElfinderStorage implements ElfinderStorage {

	public final static class Builder {

	    private final List<DefaultVolumeRef> volumes = new ArrayList<>();
	    private final List<VolumeSecurity> volumeSecurities = new ArrayList<>();
		private ThumbnailWidth thumbnailWidth;

	    {
	    	withThumbnailWidth(80);
	    }

		public Builder withThumbnailWidth(int thumbnailWidth) {
			return withThumbnailWidth(() -> thumbnailWidth);
		}

		public Builder withThumbnailWidth(ThumbnailWidth thumbnailWidth) {
			this.thumbnailWidth = thumbnailWidth;
			return this;
		}

	    public Builder addVolumes(DefaultVolumeRef... refs) {
	    	return addVolumes(Arrays.asList(refs));
	    }

	    public Builder addVolumes(List<DefaultVolumeRef> refs) {
	    	volumes.addAll(refs);
	    	return this;
	    }

	    public Builder addSecurities(VolumeSecurity... refs) {
	    	return addSecurities(Arrays.asList(refs));
	    }

	    public Builder addSecurities(List<VolumeSecurity> refs) {
	    	volumeSecurities.addAll(refs);
	    	return this;
	    }

	    public DefaultElfinderStorage build() {
	    	return new DefaultElfinderStorage(this);
	    }
	}

    private static final String[][] ESCAPES = {{"+", "_P"}, {"-", "_M"}, {"/", "_S"}, {".", "_D"}, {"=", "_E"}};

    private final List<Volume> volumes;
    private final List<VolumeSecurity> volumeSecurities;
    private final ThumbnailWidth thumbnailWidth;
    private final Map<Volume, Locale> volumeLocales = new ConcurrentHashMap<>();
    private final Map<Volume, String> volumeIds = new ConcurrentHashMap<>();

    private DefaultElfinderStorage(Builder builder) {
    	this.volumes = builder.volumes.stream().map(DefaultVolumeRef::getVolume).toList();
    	builder.volumes.forEach((v) -> {
    		volumeIds.put(v.getVolume(), v.getId());
    		volumeLocales.put(v.getVolume(), v.getLocale().orElseGet(Locale::getDefault));
    	});
    	this.thumbnailWidth = builder.thumbnailWidth;
    	this.volumeSecurities = Collections.unmodifiableList(new ArrayList<>(builder.volumeSecurities));
	}

	@Override
    public Target fromHash(String hash) {
        for (var v : volumes) {

            String prefix = getVolumeId(v) + "_";

            if (hash.equals(prefix)) {
                return v.getRoot();
            }

            if (hash.startsWith(prefix)) {
                String localHash = hash.substring(prefix.length());

                for (String[] pair : ESCAPES) {
                    localHash = localHash.replace(pair[1], pair[0]);
                }

                String relativePath = new String(Base64.getDecoder().decode(localHash));
                return v.fromPath(relativePath);
            }
        }

        return null;
    }

    @Override
    public String getHash(Target target) throws IOException {
        String relativePath = target.getVolume().getPath(target);
        String base = new String(Base64.getEncoder().encode(relativePath.getBytes()));

        for (String[] pair : ESCAPES) {
            base = base.replace(pair[0], pair[1]);
        }

        return getVolumeId(target.getVolume()) + "_" + base;
    }

    @Override
    public String getVolumeId(Volume volume) {
        return volumeIds.get(volume);
    }

    @Override
    public Locale getVolumeLocale(Volume volume) {
        return volumeLocales.get(volume);
    }

    @Override
    public VolumeSecurity getVolumeSecurity(Target target) {
        try {
            final String targetHash = getHash(target);
            final String targetHashFirstChar = Character.toString(targetHash.charAt(0));
            final List<VolumeSecurity> volumeSecurities = getVolumeSecurities();

            for (VolumeSecurity volumeSecurity : volumeSecurities) {
                String volumePattern = volumeSecurity.getVolumePattern();

                // checks if volume pattern is equals to targethash first character, if so,
                // this volume pattern doesn't have any regex, applying the default regex to volume pattern
                if (volumePattern.trim().equalsIgnoreCase(targetHashFirstChar)) {
                    volumePattern = volumePattern + ElFinderConstants.ELFINDER_VOLUME_SERCURITY_REGEX;
                }

                if (Pattern.compile(volumePattern, Pattern.CASE_INSENSITIVE).matcher(targetHash).matches()) {
                    return volumeSecurity;
                }
            }
            // return a default volume security
            return new VolumeSecurity() {
                @Override
                public String getVolumePattern() {
                    return targetHashFirstChar + ElFinderConstants.ELFINDER_VOLUME_SERCURITY_REGEX;
                }

                @Override
                public SecurityConstraint getSecurityConstraint() {
                    return new SecurityConstraint();
                }
            };
        } catch (IOException e) {
            throw new RuntimeException("Unable to get target hash from elfinderStorage");
        }
    }

    // getters and setters

    @Override
	public List<VolumeSecurity> getVolumeSecurities() {
        return volumeSecurities;
    }

    @Override
	public ThumbnailWidth getThumbnailWidth() {
        return thumbnailWidth;
    }

    @Override
	public List<Volume> getVolumes() {
        return volumes;
    }

}
