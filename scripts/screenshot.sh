#!/bin/sh
# Take a screenshot from the connected ADB device and send the path to the
# tmux pane above (Claude Code) for analysis.

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SCREENSHOT_DIR="$REPO_DIR/tmp/screenshots"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
FILENAME="screenshot_$TIMESTAMP.png"
FILEPATH="$SCREENSHOT_DIR/$FILENAME"
RELATIVE="tmp/screenshots/$FILENAME"

mkdir -p "$SCREENSHOT_DIR"

adb shell screencap -p /sdcard/screenshot_tmp.png
adb pull /sdcard/screenshot_tmp.png "$FILEPATH"
adb shell rm /sdcard/screenshot_tmp.png

echo "Screenshot saved: $RELATIVE"
tmux send-keys -t 0 "Analyse this screenshot: ./$RELATIVE in the repo"
sleep 0.1
tmux send-keys -t 0 Enter
