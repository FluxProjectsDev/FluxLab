#!/bin/sh
# Preserve the generated Gradle wrapper while selecting an API-compatible
# AAPT2 runner on Linux hosts, including arm64 development environments.
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
exec "$APP_HOME/gradlew-standard" \
    "-Pandroid.aapt2FromMavenOverride=$APP_HOME/tools/aapt2" \
    "$@"
