# Idealingua Example

## Prerequisites

1. To set up new izumi version, change `recompile-scala.sh` and `recompile-ts.sh` where version is defined like IZUMI_VERSION="0.X.X-SNAPSHOT"

2. Please install coursier via brew https://get-coursier.io/docs/cli-overview.html#brew

3. install `yarn`, `tsc`, `npm`

4. If you are using Intellij, please add syntax highligher for `.izumi` files: 
https://github.com/ratoshniuk/jetbrains-izumi-idl-syntax

## Agenda

1. IDL Syntax overview 

2. Launching Server and clients

3. Executing tests for idl-based client and services invocation

4. Authorization middleware designing overview

5. Describing 2FA Module

Language Support Matrix
-----------------------

At the moment we support following languages:

| Language / Platform | Server | Client  |
|-----------|----------------------------|----------------------------|
| **Scala / JVM**          | **Yes** [(example)](./servers/scala-jvm-server) | **Yes** |
| **TypeScript / Node.js** | **Yes**     | **Yes** [(example)](./clients/typescript-node-client) |
| **Go / Native**          | **Yes** | **Yes** |
| **C# / .NET**             | **Yes** | **Yes** |

Some of them already have sample Pet Store implementations. Others are coming!

If you want your language to be supported, just submit a Pull Request to our [github repository](https://github.com/7mind/izumi)
