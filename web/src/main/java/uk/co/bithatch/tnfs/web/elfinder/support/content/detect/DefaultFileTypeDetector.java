package uk.co.bithatch.tnfs.web.elfinder.support.content.detect;

import java.io.IOException;
import java.net.URLConnection;

public class DefaultFileTypeDetector implements Detector<String> {

	@Override
	public String detect(String path) throws IOException {
		var guessedType = URLConnection.guessContentTypeFromName(path);
		if(guessedType == null && path.endsWith(".cjs")) {
			return "application/javascript";
		}
		return guessedType;
	}

}
