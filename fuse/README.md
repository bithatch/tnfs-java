# tnfs-java-fuse

You want this module if you intend to write your own application that mounts a TNFS server as a local file system.

It is based on

*If you are looking for ready-made TNFS FUSE Mount tool, see [tnfs-java-cli](../cli)*

## Requirements

 * Java 22 or higher.
 * [Libfuse](https://github.com/libfuse/libfuse)

## Installation

The library is on Maven Central, so just add to your POM, or your build systems equivalent.
See the [Home Page](../) for the current version.

```xml
<dependency>
	<groupId>uk.co.bithatch</groupId>
	<artifactId>tnfs-java-fuse</artifactId>
	<version>x.y.z<version>
</dependency>
```

## Quick Start

 * Build a `TNFSMount` as usual (see [client](../client) library).
 * Create a `FuseBuilder` using `Fuse.builder()`. 
 * Construct a `TNFSFUSEFileSystem`.
 * Build a `Fuse` given the file system and run for as long as needed.

```java
try(var clnt = new TNFSClient.Builder().
	withHostname("terra").
	withTcp().
	build()) {
	
	try(var mnt = clnt.mount().build()) {
				
		var builder = Fuse.builder();
		var fuseOps = new TNFSFUSEFileSystem(mount, builder.errno());
		try (var fuse = builder.build(fuseOps)) {
			fuse.mount(getSpec().name(), mountPoint, "-s");
			while(true) {
				Thread.sleep(Integer.MAX_VALUE);
			}
		} 
	}
}
```