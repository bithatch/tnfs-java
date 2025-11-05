# tnfs-java-web

A simple webapp for TNFS resources using an [elFinder](https://github.com/Studio-42/elFinder) front-end.
 
## Running

### Download Pre-built Binaries

There are self-contained, pre-built [downloads](https://bithatch.co.uk/?page_id=344) for a few platforms. If yours is one supported, just download the executables, change directory to the build name. E.g.

```
cd tnfs-java-sdk-linux-amd64
sbin/tnfsjd-web -C etc -L INFO
```

or

```
CD tnfs-java-sdk-windows-amd64
sbin\tnfsjd-web.exe -C etc -L INFO
```

The `-C` tells the daemon where to load the configuration from. If you omit this, a default location will be used. The `-L INFO` increases the output level a little so you can see what is going on.

### Download Cross Platform Jars

If you have a JDK installed, you can just [downloads](https://bithatch.co.uk/?page_id=344) the Cross Platform builds and run them.

```
cd tnfs-java-sdk-xplatform-anycpu
java -jar sbin/tnfsjd-web.jar -C etc -L INFO
```

### Run From Source

You probably only want to do this if you are working on tnfs-java itself. You will need.

 * A JDK (version 24 and above). 
 * Maven
 
 
The run the application by choosing the appropriate profile and supplying the `args` property.

```
mvn exec:run -P tnfsjd-web -Dargs="-C etc -L INFO"
```
### Docker

#### From Hub

```
docker pull bithatch/tnfsjd-web:latest
```

The image exposes the two ports `14080` and `14443` on `TCP`.  It exposes a single volume,
`/configuration`, where you can place configuration files that override the defaults.

#### From Source

Make sure you are in the root of the `tnfs-java` project (i.e. one directory above where this file is). Then a `Dockerfile` is used for the next step.

```
docker build -t your-name/tnfsj-web -f web/Dockerfile .
```

Then run.

```
docker run your-name/tnfsj-web
```

## Configuration

If you are using [tnfs-java-daemon](../daemon) on the same network (or Docker Container) as `tnfsjd-web`, then zero configuration is needed. mDNS will be used to automatically locate all of your shares and present them in the web interface.

If you are using standard TNFS servers, you will have to add their locations to one or more `[mount]`  sections in the main configuration file.

### Location Of Configuration Files

By default, configuration files are expected to be in certain locations depending on your operating system and whether you are running as an administrator, service or normal user.

| OS | User | Location |
| --- | --- | --- |
| Linux | root / server | /etc/tnfsjd-web |
| Linux | standard users | $HOME/.configuration/tnfsjd-web |
| Windows | administrator / service | C:\\Program Files\\Common Files\\tnfsjd-web |
| Windows | standard users | %HOME%/AppData/Roaming\\tnfsjd-web |
| Other | administrator / service | /etc/tnfsjd-web |
| Other | standard users | $HOME/.tnfsjd-web |

Or to run the daemon with its configuration elsewhere.

```
tnfs-web -C /data/my-tnfsjd-web-config
```

In the configuration directory will be a single configuration file `tnfsjd-web.ini`,  or the *drop-in*  directory `tnfsjd-web.d`.

## Credits

The browser components uses [elFinder](https://github.com/Studio-42/elFinder), and the server uses a hacked version of [elfinder-java-connector](https://github.com/trustsystems/elfinder-java-connector) to work with my own HTTP server, [uhttpd](https://github.com/sshtoosl/uhttpd) and to get rid of some other dependencies such as Spring and JAXB. See the license files in the root of the project, or the source, for further information.