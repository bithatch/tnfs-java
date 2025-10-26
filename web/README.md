# tnfs-java-web

A simple webapp for TNFS resources using an [elFinder](https://github.com/Studio-42/elFinder) front-end.
 
## Running

### Download Pre-built Binaries

The are self-contained, pre-built binaries for a few platforms. If yours is one supported, just download the executables and run.

```
/path/to/tnfs-web
```

or

```
C:\Path\to\tnfs-web.exe
```

### Download Cross Platform Jars

If you have a JDK installed, you can just download the `.jar` builds and run them.

```
java -jar /path/to/tnfs-web.jar
```

### Run From Source

You probably only want to do this if you are working on tnfs-java itself. You will need.

 * A JDK (version 24 and above). 
 * Maven
 
 
The run the application by choosing the appropriate profile and supplying the `args` property.

```
mvn exec:run -P tnfs-web -Dargs="-Dsome.property=123"
```
### Docker

#### From Hub

**TODO**

#### From Source

A `Dockerfile` is provided.

Make sure you are in the root of the `tnfs-java` project (i.e. one directory above where this file is), and run the following.

```
docker build -t your-name/tnfs-web -f daemon/Dockerfile .
```

Then run.

```
docker run your-name/tnfs-web
```

## Configuration

If you are using [tnfs-java-daemon](../daemon) on the same network (or Docker Container) as `tnfs-web`, then zero configuration is needed. mDNS will be used to automatically locate all of your shares and present them in the web interface.

If you are using standard TNFS servers, you will have to add their locations to one or more `[mount]`  sections in the main configuration file.

### Location Of Configuration Files

By default, configuration files are expected to be in certain locations depending on your operating system and whether you are running as an administrator, service or normal user.

| OS | User | Location |
| --- | --- | --- |
| Linux | root / server | /etc/tnfs-web |
| Linux | standard users | $HOME/.configuration/tnfs-web |
| Windows | administrator / service | C:\\Program Files\\Common Files\\tnfs-web |
| Windows | standard users | %HOME%/AppData/Roaming\\tnfs-web |
| Other | administrator / service | /etc/tnfs-web |
| Other | standard users | $HOME/.tnfs-web |

Or to run the daemon with its configuration elsewhere.

```
tnfs-web -Dtnfs-web.configuration=/data/my-tnfs-web-config
```

In the configuration directory will be a single configuration file `tnfs-web.ini`,  or the *drop-in*  directory `tnfs-web.d`.

## Credits

The browser components uses [elFinder](https://github.com/Studio-42/elFinder), and the server uses a hacked version of [elfinder-java-connector](https://github.com/trustsystems/elfinder-java-connector) to work with my own HTTP server, [uhttpd](https://github.com/sshtoosl/uhttpd) and to get rid of some other dependencies such as Spring and JAXB. See the license files in the root of the project, or the source, for further information.