#!/usr/bin/env bash
# Load Endless Sky resources into the app's INTERNAL files dir (app-owned, normal
# POSIX; the external dir has scoped-storage ownership that blocks the app from
# reading adb-pushed files). Pushes a tar to /data/local/tmp (binary-safe), then
# extracts it as the app via run-as. Requires a debuggable build.
#
# NOTE: each adb-shell call uses a SINGLE simple command (or a fully-wrapped
# sh -c '...') because adb re-joins+re-parses arguments device-side.
set -euo pipefail
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
ES="$(cd "$(dirname "$0")/../.." && pwd)"
PKG=io.github.endless_sky.endless_sky
TAR=/tmp/es-assets.tar

echo "packing assets..."
tar cf "$TAR" -C "$ES" data images sounds shaders credits.txt keys.txt
echo "pushing $(du -h "$TAR" | cut -f1) to device..."
adb push "$TAR" /data/local/tmp/es-assets.tar >/dev/null
adb shell chmod 0644 /data/local/tmp/es-assets.tar

echo "extracting into internal storage (run-as)..."
adb shell run-as "$PKG" rm -rf files/assets
adb shell run-as "$PKG" mkdir -p files/assets
adb shell "run-as $PKG sh -c 'cd files/assets && tar xf /data/local/tmp/es-assets.tar && : > .assets-extracted'"
adb shell rm -f /data/local/tmp/es-assets.tar
rm -f "$TAR"

echo "verifying:"
adb shell "run-as $PKG sh -c 'ls files/assets && printf \"credits head: \" && head -c 40 files/assets/credits.txt && echo'"
