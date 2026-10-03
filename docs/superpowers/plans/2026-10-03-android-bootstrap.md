# Bomberman Hero Android bootstrap implementation plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build an ARM64 APK preserving Hero 0.7.3 game patches and providing ROM import, UI assets and physical controls.
**Architecture:** Hero remains the native runtime. Use pinned Goemon Android RT64, patch Hero’s separate RecompFrontend reproducibly, and load the resulting shared library from SDLActivity.
**Tech Stack:** NDK 27.1.12297006, SDK 34, SDL 2.32.8, CMake, Java 8, JDK 17.
**Spec:** docs/ANDROID_PORT.md and the approved first-milestone scope in the conversation.

## Global Constraints
- Android 13 RP5 / arm64-v8a; minimum API 28; landscape.
- Preserve Hero interpolation; aim for 60 FPS without claiming device performance.
- No ROM in source control or APK. All generation remains local.
- Desktop startup behavior must remain intact.

## Review Focus
- Valid 12 MiB ROM was rejected by old size guard: verify CLI against real upload.
- ROM ZIP and all three byte orders: verify normalized SHA1 before replacing existing import.
- Picker cancellation and import failure: deliver cancellation to the frontend without altering existing ROM.
- Android surface recreation: ensure refreshed native surface is published with ownership.
- Dependency changes: pin RT64 fork; keep frontend patch replayable on fresh checkout.

### Task 1: Validate and generate sources
- [x] Regression: check-only accepts user-provided US ROM without modifying it; observe failure from 16 MiB guard then correct to 12 MiB.
- [x] Build pinned host recompilers, generate game/RSP/patch outputs and check nonempty outputs.
- [x] Run BMHERO_TEST_ROM=bmhero.z64 python -m unittest discover -s tests -v; expected pass.

### Task 2: Android application and frontend
**Files:** android/, src/main/android_glue.cpp, include/android_support.h, tools/apply_android_patches.py, android/patches/, CMakeLists.txt.
**Interfaces:** SDL_main initializes paths before native runtime; file::open_file_dialog delegates to Android JNI; answers are dispatched during update_gfx on the runtime thread.
- [x] Test RomImporter via javac harness: raw/zip/swapped normalize to expected hash; incorrect/truncated files fail and retain previous import.
- [x] Implement app storage/asset extraction, document picker and native callbacks.
- [x] Adapt frontend CMake SDL target, replace file.cpp only on Android, convert SDL window to RT64 ANativeWindow.
- [x] Run real NDK CMake configure then build BMHeroRecompiled; expected linked ARM64 shared library.

### Task 3: Package and verify
- [x] Assemble optimized debug-signed APK; inspect ABI, SDL_main and native dependencies, ROM absence and signature.
- [x] Review branch; fix material issues, rerun checks, save to android-port.
- [x] Document untested device lifecycle/controls/rendering/performance and optional custom driver status honestly.
