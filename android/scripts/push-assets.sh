#!/usr/bin/env bash
# Push Endless Sky game resources to the app's external files dir on the
# connected device/emulator. Must run AFTER the app is installed (the dir is
# created on install). Matches Files::Init's Android path (<external>/assets).
set -euo pipefail
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
ES="$(cd "$(dirname "$0")/../.." && pwd)"
PKG=io.github.endless_sky.endless_sky
DEST="/sdcard/Android/data/$PKG/files/assets"

adb shell mkdir -p "$DEST"
for d in data images sounds shaders; do
	echo "pushing $d/ ..."
	adb push "$ES/$d" "$DEST/" >/dev/null
done
adb push "$ES/credits.txt" "$DEST/" >/dev/null
echo "pushed resources to $DEST"
adb shell ls "$DEST"
