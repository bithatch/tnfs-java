# tnfs-java

An alternative stack for [TNFS](https://github.com/spectrumero/tnfsd) written in Java. TNFS is the simple file system protocol designed for use with low power computers, such as retro 8-bit or 16-bit machines.

I started this to help me understand the TNFS protocol, as I intend to implement a client for the ZX Spectrum Next. But, it kinda snowballed! From a simple client library, it turned into a rather complete TNFS implementation with all the parts you need to work with TNFS in Java.

# For Users

*TNFS Java* provides a full suite of ready-made tools for TNFS.

 * `tnfscp` or *TNFS Copy*. A `cp` like tool for simple copies of files and directories from the command line and scripts.
 * `tnfstp` or *TNFS Transfer Program*. An `ftp` like tool, but with advanced command line completion, highlighting and more. 
 * `tnfs-fuse`. Mount TNFS resources to your file system and access them easily in any program. Makes use of [libfuse](https://github.com/libfuse/libfuse).
 * `tnfsd`. An alternative server implementation with extra features such as mDNS support, UPnP support, and enhanced file systemm and security features, particularly when used with *TNFS Java* client tools.
 * `tnfs-web`. An alternative web front-end based on [elFinder](https://github.com/Studio-42/elFinder).
 * All server tools have a `Dockerfile` to build your own docker containers. 
 
See [Releases](releases) for downloads. 

# For Developers

 * A modern client library making use of `SeekableByteChannel` and `Stream`.
 * A server API to build your own Java based TNFS servers.
 * Compatible with Graal Native Image. 
 * TNFS authentication supported in client and server libraries and all tools.
 * Client and server are fully extendable.
 * Unofficial protocol extensions for massively increasing authentication security, remote copy, checksums and mount listing.
 * Pick the tools you need, or download the full [SDK](sdk).
 * Great for use with low power computers, such as retro 8-bit and 16-bit machines, or \
   more modern IOT devices. 
 * Libraries can be used with Java 17 or higher, the tools need at least Java 21. For building the entire project, Java 24 is currently recommended. 
 
## Extensions

TNFS Java implements some unofficial protocol extensions, see [Extensions](extensions) for more information.

## Modules

This project is separated into several modules.

| Module | Directory | Min. Java | Description |
| --- | --- | --- | --- |
| tnfs-java-lib | [lib](lib) | 17 | The core library, shared by other components. |
| tnfs-java-client | [client](client/README.md) | 17 | The base client library. Use this if you wish to build your own TNFS based client. |
| tnfs-java-server | [server](server) | 17 | The base server library. Use this if you wish to build your own TNFS based server.  |
| tnfs-java-extensions | [extensions](extensions) | 17 | Unofficial protocol extension for the server and client.  |
| tnfs-java-cli | [cli](cli) | 24 | Some read-to-use tools for working with TNFS. A simple copy tool, and an FTP-like client. |
| tnfs-java-daemon | [daemon](daemon) | 24 | A fully featured TNFS server. |
| tnfs-java-fuse | [fuse](fuse) | 22 | A jFUSE implementation for TNFS. Mount TNFS resources as a local file system. |
| tnfs-java-web | [web](web) | 24 | A fully featured front-end to TNFS that runs in your browser. |
| tnfs-java-sdk | [sdk](sdk) | 24 | Assembly for a complete TNFS Development Kit including executables, libraries and documentation. |



## Installation

All will be available on Maven Central, so just add the following dependency to your project's `pom.xml`.

```xml
<dependency>
    <groupId>uk.co.bithatch</groupId>
    <artifactId>tnfs-[module]</artifactId>
    <version>0.9.0</version>
</dependency>
```


### SNAPSHOT versions

or `SNAPSHOT` versions are available right now from the Maven Snapshots repository.


```xml
    <repository>
        <id>central-snapshots</id>
        <url>https://central.sonatype.com/repository/maven-snapshots</url>
        <snapshots/>
        <releases>
            <enabled>false</enabled>
        </releases>
    </repository>
```

and 

```xml
<dependency>
    <groupId>uk.co.bithatch</groupId>
    <artifactId>tnfs-[module]</artifactId>
    <version>0.9.0-SNAPSHOT</version>
</dependency>
```

## Other Info

See [FujiNet](https://github.com/FujiNetWIFI).