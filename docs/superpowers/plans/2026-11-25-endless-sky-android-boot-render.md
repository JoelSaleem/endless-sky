# Endless Sky Android — Plan A: Boot & Render + Basic Touch (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Get upstream Endless Sky compiling to an APK and running on the Android emulator — loading game data, rendering the main menu + flight view via OpenGL ES 3.0, and responding to taps (touch→mouse) — on `arm64-v8a`.

**Architecture:** Reuse SDL2's official `android-project` Gradle/`SDLActivity` template as the app shell. SDL2 is built from source in-tree (so the Java glue matches the native lib); the remaining C/C++ deps come from vcpkg's `arm64-android` triplet. Endless Sky's own CMake builds `EndlessSkyLib` with `ES_GLES=ON`; a thin `main` **shared** lib (`libmain.so`) is what `SDLActivity` loads. Game assets are `adb push`ed to the app's files dir for dev (keeps Endless Sky's `std::filesystem` resource code unchanged).

**Tech Stack:** Android SDK platform **android-35**, build-tools **35.0.0**, NDK **30.0.16248370**, SDK CMake **4.1.2**, Gradle (wrapper), SDL2 (source), vcpkg (`arm64-android`), C++17, OpenGL ES 3.0.

**Milestone-1 note:** This is **Plan A** of milestone 1. The **phone-landscape UI pass is Plan B**, written after Plan A yields a running app to measure. Plan A targets the existing `Pixel_Fold_API_35` AVD; Plan B adds a phone AVD.

**Environment caveats (this machine):**
- `gh`/`jf` are sandbox-blocked; forking is the user's step. Work on the local clone's `master` (no new branches, per user rule).
- SDK tools aren't on `PATH`. Export once per shell:
  ```bash
  export ANDROID_HOME="$HOME/Library/Android/sdk"
  export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/30.0.16248370"
  export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
  ```
  (`ANDROID_HOME` is globally mis-set to `/opt/android_sdk`; the export above overrides it.)
- Run build/emulator/adb/network commands **with the sandbox disabled** (Meta 3PAI sandbox blocks the Meta-wrapped toolchain + network otherwise).

**Repo root:** `/Users/joelsaleem/Documents/GitHub/endless-sky` (referred to below as `$ES`).

---

## File Structure

New Android project (lives in-repo under `android/`):

- `android/` — Gradle project root (copied/adapted from SDL2's `android-project` template).
  - `android/settings.gradle`, `android/build.gradle`, `android/gradle.properties`, `android/gradlew`, `android/gradle/wrapper/…` — Gradle scaffolding (from template).
  - `android/app/build.gradle` — app module: `minSdk 24`, `compileSdk 35`, `ndkVersion`, `abiFilters 'arm64-v8a'`, `externalNativeBuild { cmake { path "jni/CMakeLists.txt" } }`.
  - `android/app/src/main/AndroidManifest.xml` — `android:screenOrientation="landscape"`, `<uses-feature android:glEsVersion="0x00030000" android:required="true"/>`, launcher activity.
  - `android/app/src/main/java/io/github/endless_sky/endless_sky/MainActivity.java` — `extends org.libsdl.app.SDLActivity`.
  - `android/app/src/main/java/org/libsdl/app/…` — SDL2's Java glue (from SDL source, version-matched).
  - `android/app/jni/CMakeLists.txt` — builds SDL2 (source) + `add_subdirectory($ES)` for `EndlessSkyLib` + `main` shared lib.
  - `android/app/jni/SDL/` — SDL2 source tree (version-matched to the Java glue).
  - `android/scripts/emu.sh`, `android/scripts/push-assets.sh`, `android/scripts/smoke.sh` — AVD boot, asset push, install+launch+screencap helpers.

Engine changes (small, guarded so desktop builds are unaffected):

- `CMakeLists.txt` — wrap the `EndlessSky` **executable** target + its link line in `if(NOT ANDROID)`; nothing else on the non-Android path changes.
- `source/main.cpp` — add `#include <SDL_main.h>` under `#ifdef __ANDROID__` so `main`→`SDL_main`.
- `source/Files.cpp` — in `Files::Init`, add an `#ifdef __ANDROID__` branch that defaults `resources` to the app's external files dir when `--resources` isn't passed.
- `source/GameWindow.cpp` — on Android, skip the `minWidth/minHeight` (1024×768) floor and create a fullscreen-desktop window at the device resolution.

Everything else (shaders, GLES path, SDL abstraction) is reused as-is.

---

## Task 0: Emulator smoke test (de-risk the environment)

**The single biggest unknown is whether the emulator boots + renders GL in this sandbox.** Prove it before building anything.

**Files:** Create `android/scripts/emu.sh`.

- [ ] **Step 1: Write the emulator helper**

Create `android/scripts/emu.sh`:
```bash
#!/usr/bin/env bash
# Boot the arm64 AVD headless and wait until it's ready. Usage: emu.sh [avd-name]
set -euo pipefail
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
AVD="${1:-Pixel_Fold_API_35}"
emulator -avd "$AVD" -no-window -no-snapshot -no-audio -gpu swiftshader_indirect &
adb wait-for-device
# Wait for full boot (up to ~2 min)
for i in $(seq 1 60); do
  [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
  sleep 2
done
adb shell getprop sys.boot_completed | tr -d '\r'
```

- [ ] **Step 2: Boot it and confirm readiness**

Run (sandbox disabled, background the emulator process):
```bash
bash android/scripts/emu.sh Pixel_Fold_API_35
```
Expected: prints `1` (boot completed). If it never reaches `1` within ~2 min, or `emulator` errors about GPU/HVF/KVM, the sandbox can't host the emulator → **STOP and escalate to the user** (they run the emulator; we drive via `adb` over their session). Try `-gpu host` and `-gpu swiftshader_indirect` before concluding.

- [ ] **Step 3: Confirm GL + capture a screenshot**

Run:
```bash
adb shell dumpsys SurfaceFlinger | grep -i "GLES" | head
adb exec-out screencap -p > /tmp/emu-home.png && echo "wrote /tmp/emu-home.png"
```
Expected: a `GLES: ...` line naming an ES 3.x renderer (SwiftShader is fine), and a non-empty PNG of the launcher. View the PNG to confirm it rendered.

- [ ] **Step 4: Commit the helper**

```bash
cd "$ES" && git add android/scripts/emu.sh && git commit -m "chore(android): emulator boot helper + smoke test"
```

---

## Task 1: SDL2 blank-window APK (prove the full toolchain before the engine)

Before wiring Endless Sky + its deps, prove Gradle→NDK→SDL→emulator works end-to-end with a trivial native lib. Also check for prior art first.

**Files:** Create `android/**` (from SDL template), `android/app/jni/src/stub_main.c` (temporary).

- [ ] **Step 1: Check for prior Android work (reuse before building)**

Run:
```bash
cd "$ES" && git log --oneline --all | grep -iE "android|gradle|ndk" | head
curl -s "https://api.github.com/search/code?q=AndroidManifest+repo:endless-sky/endless-sky" | head -c 200; echo
```
If a usable Android scaffold already exists upstream (branch/PR/dir), adopt it and skip the from-scratch template below. (Code-search may be rate-limited/unauth; if empty, proceed.)

- [ ] **Step 2: Get a version-matched SDL2 source**

Pick a recent SDL2 release (e.g. `release-2.30.x`) and vendor it:
```bash
cd "$ES"
curl -L -o /tmp/sdl2.tar.gz https://github.com/libsdl-org/SDL/archive/refs/tags/release-2.30.9.tar.gz
mkdir -p android/app/jni && tar -xzf /tmp/sdl2.tar.gz -C /tmp
cp -R /tmp/SDL-release-2.30.9 android/app/jni/SDL
```
Record the exact version chosen — the vcpkg SDL2 in Task 2 and this must agree on major/minor.

- [ ] **Step 3: Scaffold the Android project from SDL's template**

```bash
cd "$ES"
cp -R android/app/jni/SDL/android-project/. android/
# Java glue from SDL:
mkdir -p android/app/src/main/java/org/libsdl/app
cp android/app/jni/SDL/android/../android-project/app/src/main/java/org/libsdl/app/*.java \
   android/app/src/main/java/org/libsdl/app/ 2>/dev/null || \
cp -R android/app/jni/SDL/android-project/app/src/main/java/org/libsdl/app android/app/src/main/java/org/libsdl/
```
Then edit `android/app/build.gradle`: set `namespace`/`applicationId` `io.github.endless_sky.endless_sky`, `compileSdk 35`, `minSdk 24`, `ndkVersion "30.0.16248370"`, and inside `defaultConfig`: `ndk { abiFilters 'arm64-v8a' }`.

- [ ] **Step 4: Point Gradle at the SDK/NDK**

Create `android/local.properties`:
```properties
sdk.dir=/Users/joelsaleem/Library/Android/sdk
```
Create `android/app/jni/CMakeLists.txt` (stub phase):
```cmake
cmake_minimum_required(VERSION 3.16)
project(main)
add_subdirectory(SDL)
add_library(main SHARED src/stub_main.c)
target_link_libraries(main SDL2 SDL2main)
```
Create `android/app/jni/src/stub_main.c`:
```c
#include <SDL.h>
int main(int argc, char *argv[]) {
    SDL_Init(SDL_INIT_VIDEO);
    SDL_Window *w = SDL_CreateWindow("es", 0, 0, 0, 0, SDL_WINDOW_FULLSCREEN | SDL_WINDOW_OPENGL);
    SDL_Renderer *r = SDL_CreateRenderer(w, -1, 0);
    for (int i = 0; i < 300; ++i) { SDL_SetRenderDrawColor(r, 20, 30, 80, 255); SDL_RenderClear(r); SDL_RenderPresent(r); SDL_Delay(16); SDL_Event e; while (SDL_PollEvent(&e)) {} }
    SDL_Quit(); return 0;
}
```

- [ ] **Step 5: Build, install, launch, screenshot**

```bash
cd "$ES/android"
export ANDROID_HOME="$HOME/Library/Android/sdk"; export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/30.0.16248370"
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.github.endless_sky.endless_sky/.MainActivity
sleep 4 && adb exec-out screencap -p > /tmp/stub-window.png && echo wrote /tmp/stub-window.png
```
Expected: `/tmp/stub-window.png` shows a solid dark-blue window. Fix any Gradle/NDK/manifest error here where it's cheap. View the PNG to confirm.

- [ ] **Step 6: Commit the scaffold**

```bash
cd "$ES" && git add android && git commit -m "feat(android): SDL2 android project scaffold + blank-window smoke"
```

---

## Task 2: Build the C/C++ dependencies for `arm64-android` (vcpkg)

The fiddliest step. SDL2 comes from source (Task 1); the rest come from vcpkg.

**Files:** none in-repo (vcpkg is external); record results in `android/DEPS-NOTES.md`.

- [ ] **Step 1: Bootstrap vcpkg (if not present)**

```bash
export VCPKG_ROOT="$HOME/vcpkg"
[ -d "$VCPKG_ROOT" ] || git clone https://github.com/microsoft/vcpkg "$VCPKG_ROOT"
"$VCPKG_ROOT"/bootstrap-vcpkg.sh
```

- [ ] **Step 2: Install the dep set for arm64-android**

```bash
export ANDROID_NDK_HOME="$HOME/Library/Android/sdk/ndk/30.0.16248370"
"$VCPKG_ROOT"/vcpkg install --triplet arm64-android \
  libpng libjpeg-turbo libavif libmad libflac minizip openal-soft
```
Expected: each reports `Building ... succeeded` / `already installed`.

- [ ] **Step 3: Triage failures (documented fallbacks — not a placeholder)**

For any dep that fails on `arm64-android`, apply in this order and record what you did in `android/DEPS-NOTES.md`:
1. **avif** fails → it's only used for one image codec path; drop it for Plan A by removing it here and guarding its use (Task 3 will reveal the call sites; stub the AVIF loader to return "unsupported"). PNG/JPEG cover the game's images.
2. **mad / flac / openal-soft** fail → these are audio-only. Audio isn't needed to render a frame. If blocking, defer audio: build with it stubbed (Task 3 notes the `Audio` init call to no-op). Prefer fixing openal-soft (it supports Android via OpenSL) before stubbing.
3. **png / jpeg-turbo / minizip** fail → these are required; build from source with the NDK toolchain (`cmake -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24`) and install into a local prefix referenced by Task 3.

- [ ] **Step 4: Commit the dep notes**

```bash
cd "$ES" && git add android/DEPS-NOTES.md && git commit -m "docs(android): record arm64-android dependency build results"
```

---

## Task 3: Build the Endless Sky engine as `libmain.so`

Swap the stub for the real engine, guarded so desktop builds are untouched.

**Files:** Modify `CMakeLists.txt`, `source/main.cpp`; replace `android/app/jni/CMakeLists.txt`; delete `android/app/jni/src/stub_main.c`.

- [ ] **Step 1: Guard the desktop executable target**

In `CMakeLists.txt`, find the non-Apple/non-Win32 `add_executable(EndlessSky source/main.cpp)` (around line 307) and its `target_link_libraries(EndlessSky ...)` (around line 312). Wrap the whole exe+link block:
```cmake
if(NOT ANDROID)
    # ... existing add_executable(EndlessSky ...) + target_link_libraries(EndlessSky ...) ...
endif()
```
This leaves `EndlessSkyLib` building on all platforms, but only builds the desktop exe off-Android.

- [ ] **Step 2: SDL_main entry for Android**

In `source/main.cpp`, above `int main(` (line 98), add:
```cpp
#ifdef __ANDROID__
#include <SDL_main.h>   // renames main -> SDL_main; SDLActivity calls SDL_main
#endif
```
Verify desktop build is unaffected: `__ANDROID__` is only defined by the NDK.

- [ ] **Step 3: Android jni CMake that builds the engine**

Replace `android/app/jni/CMakeLists.txt`:
```cmake
cmake_minimum_required(VERSION 3.16)
project(main)

set(ES_GLES ON CACHE BOOL "" FORCE)
set(ES_USE_SDL3 OFF CACHE BOOL "" FORCE)
set(ES_USE_VCPKG OFF CACHE BOOL "" FORCE)   # we feed deps manually below
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)

add_subdirectory(SDL)                        # SDL2 from source (Task 1)
add_subdirectory(${CMAKE_SOURCE_DIR}/../../../.. es_build)  # the ES repo root -> EndlessSkyLib

# Deps from the vcpkg arm64-android install (adjust prefix to your VCPKG_ROOT):
set(VCPKG_ARM64 "$ENV{HOME}/vcpkg/installed/arm64-android")
find_library(PNG_LIB png PATHS ${VCPKG_ARM64}/lib)
find_library(JPEG_LIB jpeg PATHS ${VCPKG_ARM64}/lib)
find_library(MINIZIP_LIB minizip PATHS ${VCPKG_ARM64}/lib)
# (add avif/mad/flac/openal libs here unless deferred per Task 2)
target_include_directories(EndlessSkyLib PUBLIC ${VCPKG_ARM64}/include)

add_library(main SHARED ${CMAKE_SOURCE_DIR}/../../../../source/main.cpp)
target_link_libraries(main EndlessSkyLib SDL2 SDL2main
    ${PNG_LIB} ${JPEG_LIB} ${MINIZIP_LIB} GLESv3 EGL log android)
```
> If `add_subdirectory` on the ES root fails because the ES CMake expects vcpkg/SDL targets by specific names, set `SDL_LIBRARIES`/`SDL2::SDL2` aliases before the `add_subdirectory`, and set `ES_USE_SYSTEM_LIBRARIES` appropriately. Resolve concretely against the actual CMake error.

Delete the stub: `git rm android/app/jni/src/stub_main.c`.

- [ ] **Step 4: Compile the native lib**

```bash
cd "$ES/android" && ./gradlew :app:externalNativeBuildDebug 2>&1 | tail -30
```
Expected: `libmain.so` produced for `arm64-v8a` with no link errors. Fix compile/link errors here (missing GLES symbols → ensure `GLESv3`+`EGL` linked; missing dep symbols → add the lib in Step 3).

- [ ] **Step 5: Commit**

```bash
cd "$ES" && git add -A && git commit -m "feat(android): build Endless Sky engine as libmain.so (ES_GLES, arm64-v8a)"
```

---

## Task 4: Asset delivery + Android resource path

Endless Sky needs `credits.txt`, `data/`, `images/`, `sounds/`, `shaders/` reachable as real files (it uses `std::filesystem`). For dev we push them to the app's files dir.

**Files:** Modify `source/Files.cpp`; create `android/scripts/push-assets.sh`.

- [ ] **Step 1: Android resources default in `Files::Init`**

In `source/Files.cpp`, inside `Files::Init` where `resources` is still empty after arg parsing (before the `SDL_GetBasePath()` default near line 118), add:
```cpp
#ifdef __ANDROID__
    if(resources.empty())
    {
        const char *ext = SDL_AndroidGetExternalStoragePath(); // app-private, world-readable via adb
        if(ext)
            resources = std::filesystem::path(ext) / "assets";
    }
#endif
```
Keep the existing desktop logic in the `#else`/fall-through. The later `while(!Exists(resources / "credits.txt"))` walk then validates it.

- [ ] **Step 2: Asset-push helper**

Create `android/scripts/push-assets.sh`:
```bash
#!/usr/bin/env bash
set -euo pipefail
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
ES="$(cd "$(dirname "$0")/../.." && pwd)"
PKG=io.github.endless_sky.endless_sky
DEST="$(adb shell run-as $PKG sh -c 'echo $EXTERNAL_STORAGE' 2>/dev/null | tr -d '\r')"
# Fallback to the SDL external path convention:
DEST="/sdcard/Android/data/$PKG/files/assets"
adb shell mkdir -p "$DEST"
for d in data images sounds shaders; do adb push "$ES/$d" "$DEST/"; done
adb push "$ES/credits.txt" "$DEST/"
echo "pushed assets to $DEST"
```

- [ ] **Step 3: Push the assets**

```bash
bash "$ES/android/scripts/push-assets.sh"
```
Expected: `pushed assets to …`. (First push is slow — images are large. Confirm with `adb shell ls "$DEST"` showing `credits.txt data images sounds shaders`.)

- [ ] **Step 4: Verify the path resolves (desktop regression guard)**

The desktop unit tests must still pass (we edited `Files.cpp`):
```bash
cd "$ES" && cmake --preset linux  # or your desktop preset
cmake --build --preset linux-debug --target EndlessSkyLib 2>&1 | tail -5
```
Expected: compiles clean (the Android branch is `#ifdef`-guarded, so desktop is unchanged). If a desktop test target is configured, run it.

- [ ] **Step 5: Commit**

```bash
cd "$ES" && git add -A && git commit -m "feat(android): resolve game resources from app files dir + asset push script"
```

---

## Task 5: Launch + render a frame

**Files:** Modify `source/GameWindow.cpp`.

- [ ] **Step 1: Android window creation**

In `source/GameWindow.cpp` `GameWindow::Init`, guard the `minWidth/minHeight` clamp and force a device-sized fullscreen window on Android:
```cpp
#ifdef __ANDROID__
    // Use the device display size; ignore the desktop 1024x768 floor.
    SDL_DisplayMode dm; SDL_GetCurrentDisplayMode(0, &dm);
    width = dm.w; height = dm.h;
    Uint32 flags = SDL_WINDOW_OPENGL | SDL_WINDOW_FULLSCREEN | SDL_WINDOW_ALLOW_HIGHDPI;
#else
    // ... existing desktop sizing + flags ...
#endif
```
Ensure the GL attributes request an ES 3.0 context on Android (SDL picks GLES for the ES profile): before `SDL_CreateWindow`, where context attrs are set, confirm/add under `__ANDROID__` or `ES_GLES`:
```cpp
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK, SDL_GL_CONTEXT_PROFILE_ES);
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, 3);
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION, 0);
```

- [ ] **Step 2: Rebuild + reinstall + launch with logs**

```bash
cd "$ES/android" && ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start -n io.github.endless_sky.endless_sky/.MainActivity
sleep 8 && adb logcat -d | grep -iE "SDL|EndlessSky|OpenGL|GLES|error|fatal|resources" | tail -40
```
Expected logs: SDL creates a GLES3 context; Endless Sky logs data loading; **no** "resources not found"/fatal. Common fixes: missing asset (re-run Task 4); GL context fail (Step 1 attrs); a desktop-GL call not in the ES_GLES path (wrap it — rare, since `linux-gles` is CI-tested).

- [ ] **Step 3: Screenshot the rendered menu**

```bash
adb exec-out screencap -p > /tmp/es-menu.png && echo wrote /tmp/es-menu.png
```
Expected: `/tmp/es-menu.png` shows the Endless Sky main menu (starfield + logo). **View it.** This is the render gate.

- [ ] **Step 4: Commit**

```bash
cd "$ES" && git add -A && git commit -m "feat(android): device-sized GLES3 window; renders main menu on emulator"
```

---

## Task 6: Basic touch input (touch → mouse)

**Files:** Modify `source/main.cpp` (SDL hints, Android-guarded).

- [ ] **Step 1: Enable touch→mouse synthesis**

In `source/main.cpp`, early in `main` (after includes, before SDL init via GameWindow), add:
```cpp
#ifdef __ANDROID__
    SDL_SetHint(SDL_HINT_TOUCH_MOUSE_EVENTS, "1");   // taps become mouse clicks
    SDL_SetHint(SDL_HINT_MOUSE_TOUCH_EVENTS, "0");
#endif
```
This makes the existing click-driven UI respond to taps with zero UI rework.

- [ ] **Step 2: Rebuild, install, and drive by touch**

```bash
cd "$ES/android" && ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.github.endless_sky.endless_sky/.MainActivity
sleep 6
# Tap near screen center (adjust coords to the menu button from the screenshot):
adb shell input tap 540 1200
sleep 3 && adb exec-out screencap -p > /tmp/es-after-tap.png && echo wrote /tmp/es-after-tap.png
```
Expected: the tap activates a menu item (e.g. opens the pilot/load screen). **View `/tmp/es-after-tap.png`** to confirm the UI reacted.

- [ ] **Step 3: Reach the flight view**

Tap through: new/load pilot → enter flight. Capture:
```bash
adb exec-out screencap -p > /tmp/es-flight.png && echo wrote /tmp/es-flight.png
```
Expected: the flight view renders (ship + HUD + radar). This is **Plan A's definition of done**: boots, renders via GLES, tap-navigable to flight.

- [ ] **Step 4: Commit**

```bash
cd "$ES" && git add -A && git commit -m "feat(android): touch->mouse input; tap-navigable to flight view"
```

---

## Self-Review (completed by author)

- **Spec coverage:** app shell (T1), native build + ES_GLES (T3), deps (T2), assets (T4), GLES render (T5), touch baseline (T6), emulator verification (T0), desktop regression guard (T4.4). The spec's *phone-landscape UI pass* is intentionally deferred to **Plan B** (noted up top) — not a gap.
- **ABI:** spec said x86_64; corrected to **arm64-v8a** throughout (Apple-Silicon emulator image + devices). Spec being synced separately.
- **Type/naming consistency:** target names `EndlessSkyLib` (existing), `main` (shared lib), package `io.github.endless_sky.endless_sky`, AVD `Pixel_Fold_API_35` used consistently.
- **Known soft spots (honestly flagged, with in-task fallbacks):** vcpkg-android dep failures (T2.3 fallbacks); exact ES-root `add_subdirectory` wiring (T3.3 note); emulator-in-sandbox (T0.2 escalation). These are real contingencies with concrete gates, not placeholders.

## Out of scope (Plan B and later)
Phone-landscape UI/scale pass · phone AVD · real touch gestures (pan/pinch map) · all-panel touch redesign · APK asset bundling/extraction (replace dev adb-push) · audio polish if deferred · signing/release.
