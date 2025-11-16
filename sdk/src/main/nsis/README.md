# TNFSJ Windows SDK

This is the Windows build of TNFS Java SDK. Depending on the options chosen, you will have 2 services  installed and running.

## Try Our Web Front End

Point your browser to http://localhost:14080, and you should should see at least the automatically configured public mount *Public TNFS on localhost*. This is sharing the directory `C:/Users/Public/AppData/TNFS/public-files` with read and write access.

*If you see other mounts, you may have *mDNS* on your network. TNFSJ will by default discover other TNFS servers that announce themselves.*

## Try Out The Command Line Client

There are various clients installed to the `bin` directory. For example, try out the `tnfstp` tool. This is an interactive client, similar to `ftp`.

*Note, the PATH variable is not current set by the installer. You may want to add `bin` to your system PATH so you can just use the command name instead of it's full path.*

```
"C:\Program Files\TNFSJ\bin\tnfstp.exe" localhost
```

Run `help` to get a list of commands.

## Configuration

You probably 

## Troubleshooting

If you are having problems accessing any TNFS services,  