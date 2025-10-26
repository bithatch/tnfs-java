# tnfs-java-cli

A set of ready-to-go tools for working with TNFS.

 * `tnfscp`, or *TNFS Copy*, an scp-like tool, in function, not security.
 * `tnfstp`, or *TNFS Transfer Program*, an ftp-like tool.
 * `tnfs-fuse`, mount TNFS resources as a local driver via FUSE.
 
## TODO

 * Bulk/Recursive copies in tnfscp and tnfstp.
 * Complete FUSE implementation.
 
## Applications
 
### TNFSTP

*TNFSTP* is modelled on `ftp` or `sftp` like tools. It can be used interactively with it's own shell, or from scripts. 

 * Command, filename and option completion for remote and local paths in shell.
 * Coloured directory listing.
 * File transfer progress bars.
 
You can start the application with ... (see below for more options)

```
tnfstp fujinet.eu
```

Add `--help` to see other options.
 
Once you see the `tnfs>` prompt, the shell supports the following sub-commmands (many have aliases, see `help` command).

 * `ls` - List directories.
 * `rm` - Remove files.
 * `get` - Download files.
 * `put` - Upload files.
 * `chmod` - Change permissions.
 * `mkdir` - Create directories.
 * `rmdir` - Remove directories.
 * `stat` - Show file information.
 * `free` - Show disk space information.
 * `cd`, `lcd`, `pwd` and `lpwd` - Show or change local or remote working directory.
 * `bye` - Exit.
 * `help` - Show help. 
 
Each sub-command will also have option help, see `--help`.
 
### TNFSCP

TODO

### TNFS-Fuse

TODO
 
## Running

All executables have a `--help` option. Use this for more information.

### Download Pre-built Binaries

The are self-contained, pre-built binaries for a few platforms. If yours is one supported, just download the executables and run.

### Download Cross Platform Jars

If you have a JDK installed, you can just download the `.jar` builds and run them.

```
java -jar /path/to/tnfscp.jar --help
```

### Run From Source

You probably only want to do this if you are working on tnfs-java itself. You will need.

 * A JDK (version 24 and above). 
 * Maven
 
 
The run the application by choosing the appropriate profile and supplying the `args` property.

```
mvn exec:run -P tnfstp -Dargs="localhost --tcp"
```