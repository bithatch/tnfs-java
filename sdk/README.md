# tnfs-java-sdk

The SDK all executables and libraries into a distributable archive, known as the *TNFS Java SDK*.

## Contents

 * [The TNFS Server](../daemon).
 * [The TNFS Web Front End](../daemon).
 * [The TNFS Client Tools](../cli).
 * Command manual pages.
 * Sample configuration.
 * Libraries to develop your own TNFS applications in Java.
 * All TNFS Java source.

## Obtaining The SDK

The latest SDK is available in the [RELEASES](Release) section. Complete builds are only available for a select number of architectures and operating systems.

If yours is not on the list, cross-platform builds of the SDK are available too. You'll just need a Java runtime.

## Installation

There are current no installers, so simply extract the SDK to an appropriate location, and add the `bin` directory to your `PATH`.

For the service like applications found in the `sbin` directory, e.g. `tnfsd`, or `tnfs-web`, you will have add your own operating system specific configuration to treat them as real services  that start at boot up.

Future releases may contain or installer or scripts to help with this.

## Usage

### Running The Server

### Running The Web Front End


## Building

Unless you are working on the assembly, there should be little reason to explicitly build this module, instead it is build as part of the release build from the root project.
