/**
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

import java.time.Duration;

import uk.co.bithatch.tnfs.mountlib.MountManager;
import uk.co.bithatch.tnfs.web.elfinder.service.ElfinderStorage;
import uk.co.bithatch.tnfs.web.elfinder.service.ElfinderStorageFactory;

public class TNFSStorageFactory  implements ElfinderStorageFactory {
	
	private final static String STORAGE = "tnfsStorage";
	
	private final MountManager mountManager;
	private final Configuration configuration;
	
	TNFSStorageFactory(Configuration configuration, MountManager mountManager) {
		this.mountManager = mountManager;
		this.configuration = configuration;
	}

	@Override
	public ElfinderStorage getVolumeSource() {
		var ws = WebState.get();
		return ws.get(STORAGE, () -> {
			var tnfsStorage = new TNFSStorage(mountManager);
			ws.timeout(Duration.ofMinutes(configuration.server().getInt(Constants.SESSION_TIMEOUT_MINS_KEY)));
			ws.set(STORAGE, tnfsStorage);
			return tnfsStorage;
		});
	}

}
