#!/bin/sh
# Build a debug APK.
cd "$(dirname "$0")/.." && ./gradlew assembleDebug
