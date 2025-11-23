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
package uk.co.bithatch.tnfs.web;

import java.io.Closeable;
import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import uk.co.bithatch.tnfs.mountlib.MountManager;
import uk.co.bithatch.tnfs.mountlib.MountManager.MountListener;
import uk.co.bithatch.tnfs.mountlib.MountManager.Mountable;
import uk.co.bithatch.tnfs.web.elfinder.ElFinderConstants;
import uk.co.bithatch.tnfs.web.elfinder.core.Target;
import uk.co.bithatch.tnfs.web.elfinder.core.Volume;
import uk.co.bithatch.tnfs.web.elfinder.core.VolumeSecurity;
import uk.co.bithatch.tnfs.web.elfinder.core.impl.SecurityConstraint;
import uk.co.bithatch.tnfs.web.elfinder.service.ElfinderStorage;
import uk.co.bithatch.tnfs.web.elfinder.service.ThumbnailWidth;

public class TNFSStorage implements ElfinderStorage, Closeable, MountListener {
	
    private static final String[][] ESCAPES = {{"+", "_P"}, {"-", "_M"}, {"/", "_S"}, {".", "_D"}, {"=", "_E"}};


	private MountManager mountManager;
	private ThumbnailWidth thumbnailWidth = () -> 80;
    private final Map<Volume, Locale> volumeLocales = new ConcurrentHashMap<>();
    private final Map<Volume, String> volumeIds = new ConcurrentHashMap<>();
	private final List<TNFSVolume> volumes;

	TNFSStorage(MountManager mountManager) {
		this.mountManager = mountManager;
		volumes = new CopyOnWriteArrayList<>(mountManager.mounts().stream().map(TNFSVolume::new).peek(v -> {
    		indexVolume(v);
		}).toList());
		mountManager.addListener(this);
	}

	@Override
	public void mountAdded(Mountable mountable) {
		var vol = new TNFSVolume(mountable);
		volumes.add(vol);
		indexVolume(vol);
	}

	@Override
	public void mountRemoved(Mountable mountable) {
		var vol = volumes.stream().filter(v -> v.mountable().equals(mountable)).findFirst().orElse(null);
		if(vol != null) {
			volumes.remove(vol);
			volumeIds.remove(vol);
			volumeLocales.remove(vol);
		}
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
        String base = encodeStr(relativePath);

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
        return Collections.emptyList();
    }

    @Override
	public ThumbnailWidth getThumbnailWidth() {
        return thumbnailWidth ;
    }

	@Override
	public List<Volume> getVolumes() {
        return volumes.stream().map(v->(Volume)v).toList();
    }

	@Override
	public void close() {
		mountManager.removeListener(this);
	}

	private void indexVolume(TNFSVolume v) {
		volumeIds.put(v, encodeStr(v.id()));
		volumeLocales.put(v, Locale.getDefault());
	}

	private String encodeStr(String relativePath) {
		String base = new String(Base64.getEncoder().encode(relativePath.getBytes()));

        for (String[] pair : ESCAPES) {
            base = base.replace(pair[0], pair[1]);
        }
		return base;
	}
}
