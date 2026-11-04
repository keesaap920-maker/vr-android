#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
JAR=gradle/wrapper/gradle-wrapper.jar
if [ ! -f "$JAR" ]; then
  echo 'Downloading official Gradle 8.9 wrapper...'
  curl -fLsS 'https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar' -o "$JAR"
  echo '498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17  gradle/wrapper/gradle-wrapper.jar' | sha256sum -c -
fi
exec java -cp "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
