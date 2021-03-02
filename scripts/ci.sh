#!/usr/bin/env bash

set -e
set -x

export PATH="$ANDROID_HOME"/tools/bin:$PATH

./gradlew fullCheck

# check that the public API did not change with Metalava
# reenable when the 3.x API is more stable
# ./gradlew metalavaCheckCompatibility

./gradlew :core-build:publishSnapshotsIfNeeded  --parallel

./gradlew :core-build:publishToOssStagingIfNeeded
./gradlew :core-build:publishToGradlePortalIfNeeded -Pgradle.publish.key="$GRADLE_PUBLISH_KEY" -Pgradle.publish.secret="$GRADLE_PUBLISH_SECRET"