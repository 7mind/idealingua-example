#!/bin/bash -xe

source "izumi-version.sh"

echo "start publishing..."
coursier launch \
    -r https://oss.sonatype.org/content/repositories/snapshots \
    -r https://oss.sonatype.org/content/repositories/releases \
    ${IZUMI_COMPILER} -- scala -d common.izumiVersion=${IZUMI_VERSION}


echo '==================================================='
echo '==================================================='
echo '==================================================='
echo '==================================================='
echo '==================== SCALA ========================'
echo '==================================================='
echo '==================================================='
echo '==================================================='
echo '==================================================='

cd target/scala
sbt ";$projectStep clean; update; publishLocal"
cd ..
