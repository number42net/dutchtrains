#!/bin/sh
# Run instrumented tests on the connected device or emulator.
cd "$(dirname "$0")/.." && ./gradlew connectedDebugAndroidTest
