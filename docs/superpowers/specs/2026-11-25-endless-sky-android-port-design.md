# Endless Sky → Android port — Design (Milestone 1)

**Date:** 2026-11-25
**Status:** Approved (brainstorm), pre-implementation
**Upstream:** https://github.com/endless-sky/endless-sky (GPL-3.0, CMake + vcpkg, default branch `master`)

## Goal
A genuine, playable Android port of Endless Sky with a **touch-first, phone-landscape UI**. We are free to modify the engine/UI as much as needed for a good experience. The Android **emulator (x86_64)** is the dev/test target; real devices (arm64-v8a) follow.

## Non-goals (this milestone)
- Not a SWE-Bench task / not gradability-constrained.
- Not portrait (the game is inherently landscape).
- Not upstream-mergeable (hard fork is fine).
- Not all ~40 panels touch-redesigned — just the core flight path.
- Not Play Store asset delivery / signing / release.

## Key findings (grounding)
Recon of upstream substantially de-risks rendering:
- **OpenGL ES 3.0 path already exists and is CI-tested.** `source/opengl.h` has an `ES_GLES` branch (`#include <GLES3/gl3.h>`); `CMakeLists.txt` exposes `option(ES_GLES ...)`; `CMakePresets.json` ships `linux-gles` configure/build/test/CI presets.
- **Shaders need no changes.** The 22 files in `shaders/` are already GLSL-ES style (`precision mediump float;`, `in`/`out`, `sampler2DArray`, `texture()`). `source/shader/Shader.cpp` auto-emits the `#version` line by querying `glGetString(GL_SHADING_LANGUAGE_VERSION)` at runtime → emits `#version 300 es` on a GLES context automatically.
- **SDL2 / SDL3 both supported** (`ES_USE_SDL3`), with a **headless mode** already in `GameWindow::Init(bool headless)`.
- Deps (vcpkg `vcpkg.json`): SDL2, libpng, libjpeg-turbo, libavif, openal-soft, libmad, libflac, minizip, (glew — desktop only, **not needed on GLES**).
- Hard-coded `minWidth=1024, minHeight=768` in `GameWindow.cpp` must be relaxed for phones.

**Conclusion:** the real work is Android **packaging + dependency builds + touch input + phone-landscape UI**, not GL porting.

## Architecture / approach
1. **App shell** — SDL2's official `android-project` Gradle template (provides `SDLActivity` Java glue, `AndroidManifest.xml`, Gradle skeleton, and `externalNativeBuild` → CMake). The ES engine builds as the native shared lib SDLActivity loads. Don't hand-roll the Java/activity layer.
2. **Native build** — Endless Sky's own `CMakeLists.txt` driven via the NDK toolchain with `ES_GLES=ON`. **ABI arm64-v8a** (this Mac is Apple Silicon → the emulator image *and* real devices are arm64-v8a, so one ABI covers both).
3. **Dependencies** — SDL2 built from source in-tree (so the Java glue matches the native lib); the rest via vcpkg **`arm64-android`** (libpng, libjpeg-turbo, libavif, openal-soft, libmad, libflac, minizip). `glew` dropped. Fallback for any dep lacking clean android-triplet support: build from source in the NDK tree, or defer (audio/avif) / substitute. *Highest-risk step → validated first.*
4. **Assets** — for dev, `adb push` all resources (`data/ images/ sounds/ shaders/ credits.txt`) to the app's files dir and point ES's `resources` path there — works unchanged with ES's `std::filesystem` file code (no SDL_RWops/AAsset rewrite). APK bundling / first-run extraction / Play Asset Delivery come later.
5. **Input** — baseline: enable SDL touch→mouse emulation so the existing click-driven UI works immediately; then layer real touch gestures (pan/pinch map, drag) on top.
6. **UI (phone-landscape first pass)** — relax the 1024×768 floor; apply a phone-appropriate UI scale (ES already has UI zoom + HiDPI in `Screen`); enlarge hit targets on the **flight HUD + radar + map/landing** only; lock landscape orientation.

## Milestone-1 definition of done
APK installs on the arm64-v8a emulator, launches, loads game data, renders the main menu + flight view via GLES, and the player can **tap-navigate menus, fly, land, and open the map** by touch, with the core HUD/map legibly touch-sized in phone landscape.

## Internal build sequence
1. **Emulator smoke** — confirm an AVD boots + renders GL in this environment (GUI risk; fallback `emulator -no-window` + `adb exec-out screencap`).
2. **Deps-on-Android spike** — get the vcpkg android dep set building for x86_64.
3. **Native `.so`** — ES engine compiles/links with `ES_GLES=ON` for Android.
4. **Launch + render** — APK runs, loads assets, draws a frame.
5. **Touch→mouse** — navigate + fly.
6. **First UI/scale pass** — phone-landscape legibility on the core path.
Stop + reassess at milestone end.

## Verification / testing
- **Desktop regression guard:** upstream unit tests (`tests/`, catch2) continue to run on a desktop build — any engine change must keep them green.
- **Android smoke:** scripted `adb install` → launch → `adb logcat` error scan → `adb exec-out screencap` screenshots checked per sub-step.
- No new test framework; reuse catch2 for any engine-logic changes, manual/screenshot verification for UI.

## Risks & mitigations
| Risk | Mitigation |
|---|---|
| Emulator can't boot/render in the agent sandbox | Headless `-no-window` + screencap; worst case user runs the emulator, I drive via `adb`. |
| vcpkg lacks android triplet for a dep (avif/mad/flac/openal) | Build that dep from source in the NDK tree, or substitute (e.g. skip avif path, minimal audio first). |
| Asset size (images ~hundreds of MB) blows APK limits | `adb push` to app storage for dev; Play Asset Delivery later. |
| Phone-landscape UX for a dense desktop UI | Touch→mouse baseline keeps it usable; scope first pass to the core flight path only, iterate. |
| SDL2 vs SDL3 on Android | Default SDL2 (template is mature); SDL3 is an option if a blocker appears. |

## Logistics
- I clone upstream to work; **you fork** on GitHub (or `gh repo fork endless-sky/endless-sky --clone` in a normal terminal — `gh` is sandbox-blocked for me). I wire your fork as the push remote when ready.
- Per your git rule: work on the fork's `master`, no new branches.
- Spec committed to the repo at `docs/superpowers/specs/2026-11-25-endless-sky-android-port-design.md`; working copy also in PARA `01_projects/endless-sky-android/`.

## Future stages (out of scope for M1)
arm64-v8a device builds · full touch gesture system · all-panel touch redesign · settings/persistence paths · audio polish · APK signing / asset delivery / Play listing.
