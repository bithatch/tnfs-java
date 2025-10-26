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
package uk.co.bithatch.tnfs.cli;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Objects;
import java.util.Optional;

import uk.co.bithatch.tnfs.lib.Protocol;

public class TNFSURI {

	// tnfs://joe@terra:12345/path/to/file#/mount
	
	private final Optional<String> username;
	private final Optional<char[]> password;
	private final Optional<Integer> port;
	private final String hostname;
	private final Optional<String> mount;
	private final Optional<String> path;
	private final Protocol protocol;
	private final boolean pathSpec;
	
	private TNFSURI(URI uri, boolean pathSpec) {
		this.pathSpec = pathSpec;
		
		if(uri.getScheme() == null || uri.getScheme().equals("") || "tnfs".equalsIgnoreCase(uri.getScheme())) {
			protocol = Protocol.UDP;
		}
		else if(uri.getScheme().equalsIgnoreCase("tnfst")) {
			protocol = Protocol.TCP;
		}
		else {
			throw new IllegalArgumentException("Invalid scheme. Must either be omitted, `tnfs` or `tnfst`.");
		}
		
		var auth = uri.getUserInfo();
		if(auth == null) {
			username = Optional.empty();
			password = Optional.empty();
		}
		else {
			var idx = auth.indexOf(':');
			if(idx == -1) {
				username = Optional.of(auth);
				password = Optional.empty();
			}
			else {
				username = Optional.of(auth.substring(0, idx));
				password = Optional.of(auth.substring(idx + 1).toCharArray());
			}
		}
		
		port = uri.getPort() == -1 ? Optional.empty() : Optional.of(uri.getPort());
		hostname = uri.getHost() == null ? "localhost" : uri.getHost();
		
		
		var p1 = Optional.ofNullable(uri.getPath());
		var p2 = Optional.ofNullable(uri.getFragment());
		
		if(pathSpec) {
			if(p2.isPresent()) {
				path = p1;
				mount = p2;
			}
			else {
				mount = Optional.empty();
				path = p1;
			}
		}
		else {
			if(p2.isPresent()) {
				mount = p2;
				path = p1;
			}
			else {
				mount = p1;
				path = Optional.empty();
			}
		}
	}
	
	private TNFSURI(Optional<String> username, Optional<char[]> password, Optional<Integer> port, String hostname,
			Optional<String> mount, Optional<String> path, Protocol protocol, boolean pathSpec) {
		super();
		this.username = username;
		this.password = password;
		this.port = port;
		this.hostname = hostname;
		this.mount = mount;
		this.path = path;
		this.protocol = protocol;
		this.pathSpec = pathSpec;
	}

	public Optional<String> username() {
		return username;
	}

	public Optional<char[]> password() {
		return password;
	}

	public Optional<Integer> port() {
		return port;
	}

	public Optional<String> path() {
		return path;
	}

	public String hostname() {
		return hostname;
	}

	public Optional<String> mount() {
		return mount;
	}

	public Protocol protocol() {
		return protocol;
	}
	
	public String toString() {
		var b = new StringBuffer(protocol == Protocol.TCP ? "tnfst" : "tnfs");
		b.append("://");
		username.ifPresent(un -> {
			try {
				b.append(URLEncoder.encode(un, "UTF-8"));
			} catch (UnsupportedEncodingException e) {
				throw new IllegalArgumentException(e);
			}
			password.ifPresent(pw -> {
				b.append(":");
				try {
					b.append(URLEncoder.encode(new String(pw), "UTF-8"));
				} catch (UnsupportedEncodingException e) {
					throw new IllegalArgumentException(e);
				}
			});
		});
		b.append(hostname);
		port.ifPresent(p -> {
			b.append(":");
			b.append(p);  
		});
		
		if(pathSpec) {
			path.ifPresent(p -> {
				if(!p.startsWith("/"))
					b.append('/');
				b.append(p);
			});
			
			mount.ifPresent(m -> {
				b.append('#');
				if(!m.startsWith("/"))
					b.append('/');
				b.append(m);
			});
		}
		else {
			mount.ifPresent(m -> {
				if(!m.startsWith("/"))
					b.append('/');
				b.append(m);
			});
			
			path.ifPresent(p -> {
				b.append('#');
				if(!p.startsWith("/"))
					b.append('/');
				b.append(p);
			});
		}
		return b.toString();
	}


	public static TNFSURI parse(TNFSURI base, String uri) {
		if(base == null) {
			return parse(uri);
		}
		else {
			try {
				return parsePath(uri);
			}
			catch(IllegalArgumentException iae) {
				return base.resolve(uri);
			}
		}
	}

	public TNFSURI resolve(String uri) {
		if(pathSpec)
			return new TNFSURI(username, password, port, hostname, mount, Optional.of(uri), protocol, pathSpec);
		else
			return new TNFSURI(username, password, port, hostname, Optional.of(uri), path, protocol, pathSpec);
	}

	public static TNFSURI parse(String uri) {
		if(!uri.toLowerCase().startsWith("tnfs:") && 
		   !uri.toLowerCase().startsWith("tnfst:") && 
		   ( uri.matches("^[a-zA-Z\\.-]+:.*") || ( uri.matches("^[a-zA-Z\\.-]+$") ) ) ) {
			
			if(!uri.startsWith("//")) {
				uri = "//" + uri;
			}
			uri = "tnfs:" + uri;
		}
		return new TNFSURI(URI.create(uri), false);
	}
	
	public static TNFSURI parsePath(String uri) {

		if(!uri.toLowerCase().startsWith("tnfs:") && 
		   !uri.toLowerCase().startsWith("tnfst:")) {
		
		   if( uri.matches("^//[a-zA-Z\\.-]+:.*") ) {
				uri = "tnfs:" + uri;   
		   }
		   else if( uri.matches("^[a-zA-Z\\.-]+:.*") ) {
				uri = "tnfs://" + uri;   
		   }
		   else
			   throw new IllegalArgumentException("`" + uri + "` not a valid TNFS path URI. Must be in format [tnfs[t]:]//[<userspec>@]<hostname>[:<port>][<path>][#<mount>]");
		}
		return new TNFSURI(URI.create(uri), true);
	}

	public boolean isSameServer(TNFSURI uri) {
		return Objects.equals(protocol, uri.protocol) &&
				Objects.equals(hostname, uri.hostname) &&
				Objects.equals(port, uri.port);
	}

}
