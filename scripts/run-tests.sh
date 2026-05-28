#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

# 1) JVM/unit test suite
./gradlew test

# 2) Instrumented tests in deterministic order:
#    permission-flow first, then main-flow
adb wait-for-device
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=net.number42.dutchtrains.PermissionFlowInstrumentedTest

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=net.number42.dutchtrains.MainFlowInstrumentedTest
