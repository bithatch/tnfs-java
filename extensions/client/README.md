# tnfs-java-client-extensions

This modules contains the client side components of *TNFS Java* protocol extensions. See the [parent](..) module for a general description of these extensions.

## Installation

The library is on Maven Central, so just add to your POM, or your build systems equivalent.
See the [Home Page](../..) for the current version.

```xml
<dependency>
	<groupId>uk.co.bithatch</groupId>
	<artifactId>tnfs-java-client-extensions</artifactId>
	<version>x.y.z<version>
</dependency>
```

You will of course need a server that supports the extension, i.e. [tnfs-java-daemon](../../daemon).

## Quick Start

 * Build a `TNFSClient` using `TNFSClient.Builder`. This connects you to a particular server.
 * At the point you can access *Client Extensions*. Use `TNFSClientExtension.extension(..)`.
 * As usual, build a `TNFSMount` using the `TNFSMount.Builder` returned by `TNFSClient.mount()`. This connects you to a particular mount shared by the server.
 * From that, access a *Mount Extension* using `TNFSMount.extension(..)`.
 
 
```java
try(var clnt = new TNFSClient.Builder().
	withHostname("terra").
	build()) {
	
	try(var mnt = clnt.mount().build()) {
		var sumExt = clnt.extension(Sum.class);
		System.out.println("Sum: " + sumExt.sum("/path/to/file.txt");
	}
}
```

*Note the above example assumes there is a valid user configured on the server. By default, the username and password will be prompted for if standard input is available.*