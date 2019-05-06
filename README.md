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