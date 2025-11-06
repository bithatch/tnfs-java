# tnfs-java-nio

You want this module if you intend to write your own application that mounts a TNFS server as a Java NIO file system. This means you can treat TNFS resources as if they as just another `Path`.

## Requirements

 * Java 17 or higher.

## Installation

The library is on Maven Central, so just add to your POM, or your build systems equivalent.
See the [Home Page](../) for the current version.

```xml
<dependency>
	<groupId>uk.co.bithatch</groupId>
	<artifactId>tnfs-java-nio</artifactId>
	<version>x.y.z<version>
</dependency>
```

## Quick Start

 * Create a `Path` using the TNFS URI format.
 * Use `Files` and friends to manipulate the mount as you would a local file system.

```java
var path = Paths.get("tnfs://myserver/mymount/a-text-file.txt");
try(var in = Files.newInputStream(path)) {
    in.transferTo(System.out);
}
```
