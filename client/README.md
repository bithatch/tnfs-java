# tnfs-java-client

You want this module if you intend to write your own TNFS client in Java.

*If you are looking for ready-made TNFS clients, see [tnfs-java-cli](../cli)*

## TODO

 * Timeouts.

## Installation

The library is on Maven Central, so just add to your POM, or your build systems equivalent.
See the [Home Page](../) for the current version.

```xml
<dependency>
	<groupId>uk.co.bithatch</groupId>
	<artifactId>tnfs-java-client</artifactId>
	<version>x.y.z<version>
</dependency>
```

## Quick Start

 * Build a `TNFSClient` using `TNFSClient.Builder`. This connects you to a particular server.
 * From that, build a `TNFSMount` using the `TNFSMount.Builder` returned by `TNFSClient.mount()`. This connects you to a particular mount shared by the server.
 * From that, access functions such as `list()`, `entries()`, `open()` and more.

```java
try(var clnt = new TNFSClient.Builder().
	withHostname("terra").
	withTcp().
	build()) {
	
	try(var mnt = clnt.mount().build()) {
				
		try(var dirstr = mnt.list()) {
			dirstr.forEach(d -> {
				System.out.println(d);
			});
		}
	}
}
```