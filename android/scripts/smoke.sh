#!/usr/bin/env bash
# Install the APK, push game assets, launch, and dump a filtered logcat.
set -euo pipefail
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
ES="$(cd "$(dirname "$0")/../.." && pwd)"
PKG=io.github.endless_sky.endless_sky
APK="$(find "$ES/android/app/build/outputs/apk" -name '*.apk' | head -1)"

[ -f "$APK" ] || { echo "NO_APK"; exit 1; }
echo "installing $APK"
adb install -r "$APK"

# Game data must exist before launch (app files dir is created on install).
bash "$ES/android/scripts/push-assets.sh"

adb logcat -c
adb shell am start -n "$PKG/.MainActivity"
sleep 12
echo "=== logcat ==="
adb logcat -d 2>&1 | grep -iE "SDL|endless|OpenGL|GLES|error|fatal|Unable|resource|credits|loading|shader" | tail -70
echo "=== screenshot ==="
adb shell screencap -p /sdcard/es.png && adb pull /sdcard/es.png /tmp/es-screen.png >/dev/null 2>&1 \
	&& echo "screenshot: /tmp/es-screen.png ($(file -b /tmp/es-screen.png))"
echo "SMOKE_DONE"
