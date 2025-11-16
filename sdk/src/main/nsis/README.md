# TNFSJ Windows SDK

This is the Windows build of TNFS Java SDK. Depending on the options chosen, you will have 2 services  installed and running, along with several client tools.

## Try Out The Web Front End

Point your browser to http://localhost:14080, and you should should see at least the automatically configured public mount *Public TNFS on localhost*. This is sharing the directory `C:/Users/Public/AppData/TNFS/public-files` with read and write access.

*If you see other mounts, you may have *mDNS* on your network. TNFSJ will by default discover other TNFS servers that announce themselves.*

The web based file browser will let you create folders, upload files, and perform just about all common file management tasks with an intuitive interface.

## Try Out The Command Line Client

There are various clients installed to the `bin` directory. For example, try out the `tnfstp` tool. This is an interactive client, similar to `ftp`.


```
tnfstp localhost
```

Run `help` to get a list of commands. Use *TAB* for filename and command completion.

## Configuration

You will probably quite quickly want to reconfigure things. Take a look in `etc`, you might want to ..

 * Create new *Mounts* pointing to other locations.
 * Remove the default mount or secure it.
 * Create users (see `tnfs-user`).
 * Change the port, or allow others on your LAN or the Internet to connect.

## Troubleshooting

If you are having problems accessing any TNFS services, first check the services themselves are running in the Windows Service Manager, starting them if they are not. If the service refuses to start, or other unexplained things happen, take a look at the various files in the `log` direcetory.