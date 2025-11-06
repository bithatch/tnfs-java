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
package uk.co.bithatch.tnfs.nio;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.co.bithatch.tnfs.client.TNFSMount;

public class TNFSFileSystem extends FileSystem {

	private final TNFSFileSystemProvider fileSystemProvider;
	private final TNFSMount mount;
	private final Path rootPath;
	private final boolean closeMountOnFileSystemClose;
	private final URI uri;
	private boolean closed;
	
	private UserPrincipalLookupService lookupService;

	TNFSFileSystem(TNFSMount mount, TNFSFileSystemProvider fileSystemProvider, Optional<String> rootPath,
			boolean closeMountOnFileSystemClose, URI uri) {
		this.fileSystemProvider = fileSystemProvider;
		this.mount = mount;
		this.rootPath = new TNFSPath(this, rootPath.orElseGet(() -> {
			return "/";
		}));
		this.closeMountOnFileSystemClose = closeMountOnFileSystemClose;
		this.uri = uri;
	}

	@Override
	public void close() throws IOException {
		if (!closed) {
			closed = true;
			try {
				if (closeMountOnFileSystemClose)
					mount.close();
			} finally {
				if (rootPath != null)
					fileSystemProvider.remove(uri);
			}
		}
	}

	static String toAbsolutePathString(Path path) {
		return path.toAbsolutePath().toString();
	}

	public Path getDefaultDir() {
		return rootPath;
	}

	@Override
	public Iterable<FileStore> getFileStores() {
		return Arrays.asList(new TNFSFileStore(mount, rootPath.toString()));
	}

	@Override
	public Path getPath(String first, String... more) {
		StringBuilder sb = new StringBuilder();
		if (first != null && first.length() > 0) {
			appendDedupSep(sb, first.replace('\\', '/')); // in case we are running on Windows
		}

		if (more.length > 0) {
			for (String segment : more) {
				if ((sb.length() > 0) && (sb.charAt(sb.length() - 1) != '/')) {
					sb.append('/');
				}
				// in case we are running on Windows
				appendDedupSep(sb, segment.replace('\\', '/'));
			}
		}

		if ((sb.length() > 1) && (sb.charAt(sb.length() - 1) == '/')) {
			sb.setLength(sb.length() - 1);
		}

		String path = sb.toString();
		String root = null;
		if (path.startsWith("/")) {
			root = "/";
			path = path.substring(1);
		}
		if(path.equals("")) {
			return create(root);
		}
		else {
			String[] names = path.split("/");
			return create(root, names);
		}
	}

	@Override
	public PathMatcher getPathMatcher(String syntaxAndPattern) {
		int colonIndex = syntaxAndPattern.indexOf(':');
		if ((colonIndex <= 0) || (colonIndex == syntaxAndPattern.length() - 1)) {
			throw new IllegalArgumentException(
					"syntaxAndPattern must have form \"syntax:pattern\" but was \"" + syntaxAndPattern + "\"");
		}

		String syntax = syntaxAndPattern.substring(0, colonIndex);
		String pattern = syntaxAndPattern.substring(colonIndex + 1);
		String expr;
		switch (syntax) {
		case "glob":
			expr = globToRegex(pattern);
			break;
		case "regex":
			expr = pattern;
			break;
		default:
			throw new UnsupportedOperationException("Unsupported path matcher syntax: \'" + syntax + "\'");
		}
		final Pattern regex = Pattern.compile(expr);
		return new PathMatcher() {
			@Override
			public boolean matches(Path path) {
				String str = path.toString();
				Matcher m = regex.matcher(str);
				return m.matches();
			}
		};
	}

	@Override
	public Iterable<Path> getRootDirectories() {
		return Collections.<Path>singleton(create("/"));
	}

	@Override
	public String getSeparator() {
		return "/";
	}

	@Override
	public synchronized  UserPrincipalLookupService getUserPrincipalLookupService() {
		if(lookupService == null)
			lookupService = new TNFSPrincipalLookup();
		return lookupService;
	}

	@Override
	public boolean isOpen() {
		return !closed;
	}

	@Override
	public boolean isReadOnly() {
		return false;
	}

	@Override
	public WatchService newWatchService() throws IOException {
		throw new UnsupportedOperationException("Watch service N/A");
	}

	@Override
	public TNFSFileSystemProvider provider() {
		return fileSystemProvider;
	}

	@Override
	public Set<String> supportedFileAttributeViews() {
		return TNFSFileAttributeViews.viewNames();
	}

	TNFSMount getMount() {
		return mount;
	}

	protected void appendDedupSep(StringBuilder sb, CharSequence s) {
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			if ((ch != '/') || (sb.length() == 0) || (sb.charAt(sb.length() - 1) != '/')) {
				sb.append(ch);
			}
		}
	}

	protected TNFSPath create(String root, ImmutableList<String> names) {
		return new TNFSPath(this, root, names);
	}

	protected Path create(String root, String... names) {
		return create(root, new ImmutableList<>(names));
	}

	protected String globToRegex(String pattern) {
		StringBuilder sb = new StringBuilder(pattern.length());
		int inGroup = 0;
		int inClass = 0;
		boolean inQE = false;
		int firstIndexInClass = -1;
		char[] arr = pattern.toCharArray();
		for (int i = 0; i < arr.length; i++) {
			char ch = arr[i];
			switch (ch) {
			case '\\':
				if (++i >= arr.length) {
					sb.append('\\');
				} else {
					char next = arr[i];
					switch (next) {
					case ',':
						// escape not needed
						break;
					case 'Q':
						inQE = true;
						sb.append("\\");
						break;
					case 'E':
						// extra escape needed
						inQE = false;
						sb.append("\\");
						break;
					default:
						sb.append('\\');
						break;
					}
					sb.append(next);
				}
				break;
			default:
				if (inQE)
					sb.append(ch);
				else {
					switch (ch) {

					case '*':
						sb.append(inClass == 0 ? ".*" : "*");
						break;
					case '?':
						sb.append(inClass == 0 ? '.' : '?');
						break;
					case '[':
						inClass++;
						firstIndexInClass = i + 1;
						sb.append('[');
						break;
					case ']':
						inClass--;
						sb.append(']');
						break;
					case '.':
					case '(':
					case ')':
					case '+':
					case '|':
					case '^':
					case '$':
					case '@':
					case '%':
						if (inClass == 0 || (firstIndexInClass == i && ch == '^')) {
							sb.append('\\');
						}
						sb.append(ch);
						break;
					case '!':
						sb.append(firstIndexInClass == i ? '^' : '!');
						break;
					case '{':
						inGroup++;
						sb.append('(');
						break;
					case '}':
						inGroup--;
						sb.append(')');
						break;
					case ',':
						sb.append(inGroup > 0 ? '|' : ',');
						break;
					default:
						sb.append(ch);
					}
				}
				break;
			}
		}
		return sb.toString();
	}

	public URI toUri() {
		return URI.create(String.format("tnfs://%s/%s", TNFSFileSystems.formatHostnameAndPort(
				mount.client().address().getHostName(), mount.client().address().getPort()), rootPath));
	}
	
	class TNFSPrincipalLookup extends UserPrincipalLookupService {

		@Override
		public UserPrincipal lookupPrincipalByName(String name) throws IOException {
			var passwd = create("/").resolve("etc").resolve("passwd");
			if(Files.exists(passwd)) {
				return Files.readAllLines(passwd).stream().filter(l -> l.startsWith(name + ":")).map(this::passwdToPrincipal).findFirst().orElseThrow(() -> new UserPrincipalNotFoundException(name));
			}
			throw new UserPrincipalNotFoundException(name);
		}
		
		private UserPrincipal passwdToPrincipal(String passwd) {
			var st = new StringTokenizer(passwd, ":");
			var uname = st.nextToken();
			try {
				st.nextToken();
				return new TNFSPrincipals.TNFSUser(uname, Optional.of(uname), Optional.of(Integer.parseInt(st.nextToken())));
			}
			catch(Exception e) {
				return new TNFSPrincipals.TNFSUser(uname, Optional.of(uname), Optional.empty());
			}
		}
		
		private GroupPrincipal groupToPrincipal(String group) {
			var st = new StringTokenizer(group, ":");
			var gname = st.nextToken();
			try {
				st.nextToken();
				return new TNFSPrincipals.TNFSGroup(gname, Optional.of(gname), Optional.of(Integer.parseInt(st.nextToken())));
			}
			catch(Exception e) {
				return new TNFSPrincipals.TNFSGroup(gname, Optional.of(gname), Optional.empty());
			}
		}

		@Override
		public GroupPrincipal lookupPrincipalByGroupName(String group) throws IOException {
			var passwd = create("/").resolve("etc").resolve("group");
			if(Files.exists(passwd)) {
				return Files.readAllLines(passwd).stream().filter(l -> l.startsWith(group + ":")).map(this::groupToPrincipal).findFirst().orElseThrow(() -> new UserPrincipalNotFoundException(group));
			}
			throw new UserPrincipalNotFoundException(group);
		}
		
	}

}
