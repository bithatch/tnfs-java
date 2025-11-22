# tnfs-java-platform

This group contains platform specific modules, for example system user database implementations. 

## Modules

This project is separated into several modules.

| Module | Directory | Min. Java | Description |
| --- | --- | --- | --- |
| tnfs-java-server-linux | [server-linux](server-linux) | 24 | Uses my own [Linid](https://github.com/bithatch/linid) to provide authentication using local system users. Also works when local system is configured with a remote database such as NIS or Active Directory. |
| tnfs-java-server-windows | [server-windows](server-windows) | 24 | Uses [Waffle](https://github.com/Waffle/waffle) to provide authentication using local users. Also works when local system is configured with a remote database such as Active Directory. |