#!/usr/bin/env bash
set -euo pipefail

export JAVA_HOME="${JAVA_HOME:-/home/cody/.sdkman/candidates/java/17.0.11-tem}"
export ANDROID_HOME="${ANDROID_HOME:-/home/cody/android-sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

cd "$(dirname "$0")/.."

if [[ "${1:-}" == "--prebuilt" ]]; then
  scripts/fetch-upstream-native-libs.sh
  ./gradlew :app:assembleDebug -PusePrebuiltNative=true --no-daemon --stacktrace
else
  ./gradlew :app:assembleDebug --no-daemon --stacktrace
fi
