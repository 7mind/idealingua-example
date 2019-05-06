#!/bin/bash -xe

source "izumi-version.sh"

echo "start publishing..."
coursier launch \
    -r https://oss.sonatype.org/content/repositories/snapshots \
    -r https://oss.sonatype.org/content/repositories/releases \
    ${IZUMI_COMPILER} -- typescript scala -d common.izumiVersion=${IZUMI_VERSION}

echo '==================================================='
echo '==================================================='
echo '==================================================='
echo '==================================================='
echo '================== TYPESCRIPT ====================='
echo '==================================================='
echo '==================================================='
echo '==================================================='
echo '==================================================='

cd target/typescript
yarn
yarn build
