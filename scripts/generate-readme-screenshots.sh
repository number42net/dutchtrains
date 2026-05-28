#!/usr/bin/env bash
set -euo pipefail

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SCREENSHOT_DIR="$REPO_DIR/assets/screenshots"
SCREENSHOTS=(
  "commute-overview.png"
  "trip-detail-ic.png"
  "trip-detail-sprinter.png"
  "follow-notification.png"
)
DEVICE_SCREENSHOT_DIR="/sdcard/Pictures/dutchtrains"

mkdir -p "$SCREENSHOT_DIR"

adb wait-for-device
adb shell rm -rf "$DEVICE_SCREENSHOT_DIR"

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=net.number42.dutchtrains.ScreenshotCaptureInstrumentedTest

for i in "${!SCREENSHOTS[@]}"; do
  file_name="${SCREENSHOTS[$i]}"
  remote_file="$DEVICE_SCREENSHOT_DIR/$file_name"
  target="$SCREENSHOT_DIR/$file_name"

  human_index=$((i + 1))
  echo "Pull screenshot $human_index/${#SCREENSHOTS[@]} -> $target"
  adb pull "$remote_file" "$target" >/dev/null
done
echo "Screenshots updated in assets/screenshots/."

VENV_DIR="$REPO_DIR/.venv"
if [[ ! -d "$VENV_DIR" ]]; then
  echo "Creating .venv and installing Pillow..."
  python3 -m venv "$VENV_DIR"
  "$VENV_DIR/bin/pip" install --quiet Pillow
fi
# shellcheck source=/dev/null
source "$VENV_DIR/bin/activate"

echo "Post-processing screenshots..."
python3 "$REPO_DIR/scripts/crop-screenshots.py" \
  "${SCREENSHOTS[@]/#/$SCREENSHOT_DIR/}"
