/*
 * Copyright © 2025 Bithatch (bithatch@bithatch.co.uk)
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
package uk.co.bithatch.tnfs.lib;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilderFactory;

public record Version(int major, int minor) implements Comparable<Version>, Encodeable {

	public static Version decode(ByteBuffer buffer) {
		var min = Byte.toUnsignedInt(buffer.get());
		var maj = Byte.toUnsignedInt(buffer.get());
		return new Version(maj, min);
	}

	public static Version fromString(String version) {
		var a = version.split("\\.");
		return new Version(Integer.parseInt(a[0]), Integer.parseInt(a[1]));
	}

	public String toString() {
		return major + "." + minor;
	}

	@Override
	public int compareTo(Version o) {
		var v = Integer.valueOf(major).compareTo(o.major);
		if (v == 0) {
			v = Integer.valueOf(minor).compareTo(o.minor);
		}
		return v;
	}

	@Override
	public ByteBuffer encode(ByteBuffer buf) {
		buf.put((byte) minor);
		buf.put((byte) major);
		return buf;
	}

	static Map<String, String> versions = Collections.synchronizedMap(new HashMap<>());

	public static boolean isDeveloperWorkspace() {
		return new File("pom.xml").exists();
	}
	
	/**
	 * Use {@link #getVersion(String, String)}.
	 * 
	 * @param groupId
	 * @param artifactId
	 * @return
	 */
	public static String getVersion(String groupId, String artifactId) {

		var detectedVersion = versions.getOrDefault(groupId + ":" + artifactId, "");
		if (!detectedVersion.equals(""))
			return detectedVersion;

		// try to load from maven properties first
		try {
			var p = new Properties();
			var is = findMavenMeta(groupId, artifactId);
			if (is != null) {
				try {
					p.load(is);
					detectedVersion = p.getProperty("version", "");
				} finally {
					is.close();
				}
			}
		} catch (Exception e) {
			// ignore
		}

		// fallback to using Java API
		if (detectedVersion.equals("")) {
			var aPackage = Version.class.getPackage();
			if (aPackage != null) {
				detectedVersion = aPackage.getImplementationVersion();
				if (detectedVersion == null) {
					detectedVersion = aPackage.getSpecificationVersion();
				}
			}
			if (detectedVersion == null)
				detectedVersion = "";
		}

		if (detectedVersion.equals("")) {
			try {
				var docBuilderFactory = DocumentBuilderFactory.newInstance();
				var docBuilder = docBuilderFactory.newDocumentBuilder();
				var doc = docBuilder.parse(new File("pom.xml"));
				if (doc.getDocumentElement().getElementsByTagName("name").item(0).getTextContent().equals(artifactId)
						&& doc.getDocumentElement().getElementsByTagName("group").item(0).getTextContent()
								.equals(groupId)) {
					detectedVersion = doc.getDocumentElement().getElementsByTagName("version").item(0).getTextContent();
				}
			} catch (Exception e) {
			}

		}

		if (detectedVersion.equals("")) {
			detectedVersion = "DEV_VERSION";
		}

		/* Treat snapshot versions as build zero */
		if (detectedVersion.endsWith("-SNAPSHOT")) {
			detectedVersion = detectedVersion.substring(0, detectedVersion.length() - 9) + "-0";
		}

		versions.put(groupId + ":" + artifactId, detectedVersion);

		return detectedVersion;
	}

	private static InputStream findMavenMeta(String groupId, String artifactId) {
		InputStream is = null;
		var cldr = Thread.currentThread().getContextClassLoader();
		if (cldr != null) {
			is = cldr.getResourceAsStream("META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties");
		}
		if (is == null) {
			is = Version.class.getClassLoader()
					.getResourceAsStream("META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties");
		}
		if (is == null) {
			is = Version.class.getResourceAsStream("/META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties");
		}
		return is;
	}
	
	public int value() {
		return ( major * 0xff ) + minor;
	}

	public boolean gt(Version other) {
		return value() > other.value();
	}

	public boolean ge(Version other) {
		return value() >= other.value();
	}

	public boolean lt(Version other) {
		return value() < other.value();
	}

	public boolean le(Version other) {
		return value() <= other.value();
	}
}
