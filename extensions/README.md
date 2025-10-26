# tnfs-java-extensions

*TNFS Java* adds a few protocol extensions. Currently there is no official mechanism for server and clients to negotiate capabilities other than the version numbers exchanged. I intend to draft such an extension and submit it the community at some point.

In the meantime, clients could simple attempt to use such extended commands, and simple decide on behaviour based on the error code (or lack of) returned. If a server doesn't implement command, it should return a `NOSYS` (code `0x16`).

Each extension requires a client part and a server part. See [server](server) and [client](client) respectively for how to use them. Common extension code is found in the extensions [lib](lib) module.

*Work in progress*

### File System Extensions

| Code | Name | Description | Status |
| --- | --- | --- | --- |
| 0x81 | SUM | Returns a checksum using one of several algorithms from CRC32 to SHA-512.  | COMPLETE |
| 0x82 | COPY | Performs a copy of one remote file to another. | COMPLETE |
| 0x83 | MOUNTS | Returns a `READDIR` handle that can be used to list all available mounts. No session required. | COMPLETE |

### Security Extensions
 
As with all TNFS servers and clients, it is built for a more naive time. Security was not such  a consideration. There is a simple username and password mechanism, but its all transmitted over the network as plain text.

*TNFS Java* though, supports some protocol extensions that I hope will address this a little.


| Code | Name | Description | Status |
| --- | --- | --- | --- |
| 0x80 | STRTSECM | Start secure mount. An alternative to official `MOUNT` that starts a SCRAM-like authentication handshake  | WIP |
| 0x91 | COMPSECM | Complete secure mount, completes `STRTSECM`. | NOT STARTED |
| 0x92 | STRTENCR | Complete secure mount, complets `STRTSECM`. | NOT STARTED |