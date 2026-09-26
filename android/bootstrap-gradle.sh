#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
# Official Gradle v8.11.1 wrapper, pinned to its upstream Git blob ID.
target=gradle/wrapper/gradle-wrapper.jar
expected=a4b76b9530d66f5e68d973ea569d8e19de379189
if [ ! -f "$target" ]; then
  curl --fail --location --output "$target.tmp" https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar
  actual=$(git hash-object "$target.tmp")
  if [ "$actual" != "$expected" ]; then
    rm -f "$target.tmp"
    echo 'Gradle wrapper verification failed' >&2
    exit 1
  fi
  mv "$target.tmp" "$target"
fi
if [ "$(git hash-object "$target")" != "$expected" ]; then
  echo 'Unexpected Gradle wrapper. Restore the official v8.11.1 wrapper.' >&2
  exit 1
fi
chmod +x gradlew
echo 'Gradle wrapper ready. Open this android folder in Android Studio.'
