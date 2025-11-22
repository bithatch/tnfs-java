# tnfs-java-mountlib

This common library is used by the other two client projects, `tnfs-java-drive` and `tnfs-java-web`. You shouldn't ever need to explicitly include it yourself unless you want to write a tool like *TNFS Drives* or *TNFS Web*

It provides the `MountManager` that monitors mDNS and local configuration files for mounts, and fires events for client tools to listen for and adjust their user interfaces accordingly. Depending on configuration, it may automount discovered resources.

