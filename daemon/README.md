# tnfs-java-daemon

A ready-to-go TNFS server, `tnfsjd`.

 * UDP and TCP connections.
 * [TNFS Java Extensions](../extensions).
 * Basic mount authentication.
 * Dockerfile provided.
 * Runs natively on select platforms, or anything with a Java runtime.
 
## Running

There are a few different ways to run the daemon. See `--help` option for more information. 

### Download Pre-built Binaries

The are self-contained, pre-built binaries for a few platforms. If yours is one supported, just download the executables and run.

```
/path/to/tnfsjd --help
```

### Download Cross Platform Jars

If you have a JDK installed, you can just download the `.jar` builds and run them.

```
java -jar /path/to/tnfsjd.jar --help
```

### Run From Source

You probably only want to do this if you are working on tnfs-java itself. You will need.

 * A JDK (version 24 and above). 
 * Maven
 
 
The run the application by choosing the appropriate profile and supplying the `args` property.

```
mvn exec:run -P tnfsjd -Dargs="--help"
```

### Docker

#### From Hub

```
docker pull bithatch/tnfsjd:latest
```

The image exposes the single port `16384` on both `TCP` and `UDP`.  It contains two volumes,
`/public-files` and `/configuration`.  The former is the default writable file system that is shared over TNFS, the latter is where you can place configuration files that override  the defaults.

#### From Source

A `Dockerfile` is provided.

Make sure you are in the root of the `tnfs-java` project (i.e. one directory above where this file is), and run the following.

```
docker build -t your-name/tnfsjd -f daemon/Dockerfile .
```

Then run.

```
docker run your-name/tnfsjd
```

## Configuration

Before your server can useful, you likely need to do a little configuration. If you just intend to publicly share some files over TNFS with no authentication at all, then you just need to set up your Mounts.

If however you wish to authenticate users accessing your TNFS resources, you should set up some users.

*Note, the actual level of security you get by authenticating users will depend on configuration and whether clients supports TNFS Java Extensions*

### Location Of Configuration Files

By default, the daemon and it's tools expect configuration files to be in certain locations depending on your operating system and whether you are running as an administrator, service or normal user.

| OS | User | Location |
| --- | --- | --- |
| Linux | root / server | /etc/tnfsjd |
| Linux | standard users | $HOME/.configuration/tnfsjd |
| Windows | administrator / service | C:\\Program Files\\Common Files\\tnfsjd |
| Windows | standard users | %HOME%/AppData/Roaming\\tnfsjd |
| Other | administrator / service | /etc/tnfsjd |
| Other | standard users | $HOME/.tnfsjd |

If you wish to use configuration from any other directory, use the `-C` (or `--configuration`) argument with the daemon or any tools.

For example, to run the daemon with its configuration elsewhere.

```
tnfsd -C /data/my-tnfs-config
```

*Remember, if you choose a different location for configuration for the daemon, you'll have to specify the same location when you use any of the tools, for example when using `tnfs-user` to create users. All such tools support the `-C` option.*

Configuration is split across 4 files in one of the OS specific directories above. 

| File | Drop-in directory | Contents |
| --- | --- | --- |
| tnfsd.ini | tnfsd.d | Server and general configuration. |
| mounts.ini | mounts.d | Mount configuration. |
| authentication.ini | mounts.d | Authentication configuration. |
| users.properties | | Users and password (manipulated with `tnfs-user` command). |

### Server Configuration

Ports and other general server behaviour are found in `tnfsd.ini`. The defaults are as follows.

```ini
;----------------------------------------
; Server - Configuration for the TNFS server
;----------------------------------------
[server]

; UPnP - Try to map the port on your router to this machine using UPnP, exposing  this server to the internet.
;upnp = false

; Announce - Announce this server using mDNS, allowing other hosts on your network  to easily find it and its configuration.
;announce = true

; Port - The port number on which the server will listen for TNFS requests.
;port = 16384

; Address - The IPv4 or IPv6 address of the interface the server will listen for TNFS  requests. The default is to listen to loopback only.
;address = 127.0.0.1

; Protocols - The protocols to listen for. By default, both TCP and UDP are enabled.
;protocols = TCP, UDP

; Name - Use this as the advertised mDNS/Bonjour service name. Defaults to `TNFS on {hostname}`.  {hostname} is replaced by actual hostname of bound address..
;name = TNFS on {hostname}
```

### Mounts

Mounts define what local directories are shared as a *TNFS Mount* in the `mounts.ini` file. For each you can choose what authentication method (if any) is required, and other parameters. 

Mulitple mounts are defined by simply repeating `[mount]` section. The `path` and `local` values do not have defaults and *must* be supplied for each mount.

All other values are optional.

```ini

;----------------------------------------
; Mount - Defines a single mounted path.
;----------------------------------------
[mount]

; Exported Path - The path that is exported to clients.
path = ......

; Local Path - The local path where thee files for this mount actually are.
local = .......

; Authentication - The type of authentication required for this mount. An empty  list means no authentication is required at all (the default).
;authentication =  BASIC, SCRAM
```

### Authentication

The `authentication.ini` file has authentication specific options. Currently this only consists of a single item.

```ini

;----------------------------------------
; Authentication - Configuration for authentication
;----------------------------------------
;[authentication]

; Server Key - The servers key.
; key = 

```

### Users

Users are managed with the provided `tnfs-user` tool. Depending on the build you have, this will either be an executable or a jar file.

Using this tool, you can `add`, `remove` users, set the `password` for a user or `list` users. See `--help` for more information.

For example, to create the user *joeb*, use the following command.

```
tnfs-user add joeb
```

You will be prompted for the users password before it is created. If the daemon is currently running, there is no need to restart it.

