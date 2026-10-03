# Bomberman Hero Android port

## Status — test 3 corrects the reported RT64 storage crash

Target: Retroid Pocket 5, Android 13, arm64-v8a, physical controller,
landscape, interpolated 60 FPS. Performance must be measured on the device.
No ROM or generated game data is committed or packaged here.

This branch starts from upstream commit
`400f4c005e9c2b155915b8f04ec7e0e4a95df9f4` (source version string 0.7.3).
The Android reference examined was Goemon64Recomp-Android commit
`1bc094984bcbd4486ee3890f2637944d0d95f1a7`.

Implemented so far:

- Host source preparation with US ROM SHA-1 verification and normalization of
  `.z64`, `.v64`, and `.n64` byte orders.
- Separate host generation of game functions, RSP microcode, patches, and
  `file_to_c`; Android will consume these outputs instead of attempting to run
  cross-compiled tools on the host.
- Android native shared-library target (`libBMHero.so`), PIC, and exported
  `SDL_main`; desktop retains its existing executable.
- Explicit configure-time checks for required generated sources and host tool.

An ARM64 native build and an installable debug-signed test APK have been built.
ROM import accepts ZIP or raw US 1.0 dumps, normalizes all three byte orders,
and verifies SHA-1 before replacing the imported ROM. UI assets and the
controller database are packaged; the ROM is excluded. Minimum Android API 28.
The user reports that test 1 immediately crashes on the RP5 before reaching
the native ROM menu. Test 2 moves ROM
import to an Android launcher that does not load native libraries. The game
activity runs in a separate `:game` process so the launcher can remain usable
after a native crash. It imports the verified dump to `bmhero.z64`, matching
the runtime's stored ROM name and XXH3 hash.

After a failed Start, reopen the app and choose **Save crash report**. The ZIP
contains persisted launch checkpoints, native stdout/stderr, Java exceptions
when available, Android process exit descriptions, and the newest available
native tombstone protobuf or ANR trace. Android can discard system traces;
availability is not guaranteed. Copy/View crash report also work without a PC.
Reports remain local until the user copies or exports them.

The RP5 test 2 report confirms that ROM import works and startup reaches RT64
initialization, then aborts with an uncaught `filesystem_error` trying to create
`/data/.rt64` (permission denied). RT64's default desktop data-path detection
overrides its storage path before opening its log, even when configuration-file
use is disabled. Test 3 disables that detection on Android and explicitly places
RT64 data/logs in `getFilesDir()/bmhero/rt64`. A host regression using RT64's real
UserPaths implementation first reproduced the escaped path, then verified that
the renderer log is created inside the app directory after the correction.

The app uses system Vulkan. Optional Turnip integration is not implemented
yet. Test 3 still needs RP5 verification; no successful rendering, controller,
save, suspend/resume or performance result is claimed.

## Audited dependency differences

Hero's pinned dependencies:

| Component | Commit |
| --- | --- |
| N64ModernRuntime | `ca568b6ad79b9029d14077f0c3ffa757727c5559` |
| RecompFrontend | `b3b7ebb4ec1a8a763c0191486f1b3329f9499a48` |
| RT64 | `f647df1a084ae67897dba9806c0d467aa0852894` |
| Hero symbols | `37887677d0fba8059e0c3a322e4c9f8b0200a5b4` |
| Hero decomp headers | `a07c57e1ee9bd20d054a7180017d30f5aa86d70f` |

The Android branch now pins Goemon's RT64 at
`5bec328c15ab02ab70154d32aa367f3cdb83ab38` with a replayable patch carrying
Hero's extended rectangle-aspect and view-matrix lighting fixes from upstream
`30eedd3` and `f647df1`. Its nested Plume pin supplies Android surface ownership
and stock Adreno shader compatibility changes. Other Hero dependency pins remain.
Goemon's Android RT64 is `5bec328c15ab02ab70154d32aa367f3cdb83ab38`;
its runtime is `184283bf222703a49d35115cd5fa35278455a6cb`.
Goemon keeps frontend code in its main repository; Hero uses RecompFrontend as
a submodule. Its Android folder cannot simply be copied unchanged.

Remaining device work:

1. Test APK installation, ROM import, title screen, physical controls, saves
   and suspend/resume on the RP5.
2. Check Vulkan device features and rendering on the RP5 stock driver; add
   optional libadrenotools/Turnip selection if needed.
3. Measure gameplay speed and sustained 60 FPS at practical resolutions.

Do not replace Hero's interpolation patches with Goemon-specific game patches.

## ROM input

An uncompressed **Bomberman Hero US 1.0** ROM, 12 MiB, normalized SHA-1:

`a36364b7e59351f7551ab351cb3b41ebc4be285b`

Upstream's CI retrieves this from a private repository using secrets which are
not present in a new fork. Desktop release binaries are not a substitute for
the missing generated C sources. The uploaded US ROM was verified and used for local generation. The APK asks
for a ROM again on the device. It must remain out of Git and the APK.

## Host source generation

Install host CMake, Ninja, C/C++ compilers, clang, lld, make, and Python 3.
Initialize dependencies:

```sh
git submodule update --init --recursive
```

Build the same N64Recomp revision used by upstream's validation workflow:

```sh
git clone --recursive https://github.com/N64Recomp/N64Recomp.git N64RecompSource
git -C N64RecompSource checkout 98bf104b1b5ed83126af8bcab0cc964782617dbf
git -C N64RecompSource submodule update --init --recursive
cmake -S N64RecompSource -B N64RecompSource/build -G Ninja -DCMAKE_BUILD_TYPE=Release
cmake --build N64RecompSource/build --target N64RecompCLI RSPRecomp
cp N64RecompSource/build/N64Recomp N64RecompSource/build/RSPRecomp .
python3 tools/prepare_sources.py /path/to/your/rom.z64
```

Validation alone, without generating or writing any files:

```sh
python3 tools/prepare_sources.py /path/to/your/rom.z64 --check-only
python3 -m unittest discover -s tests -v
```

The Android reference pins JDK 17, NDK 27.1.12297006, CMake 3.22.1, and SDK 34.
These are the intended starting versions, not a claim of a successful Hero build.

## Build Android

After source generation:

```sh
cd android
./gradlew assembleDebug
```

For a direct NDK CMake build, pass `ANDROID_ABI=arm64-v8a`,
`ANDROID_PLATFORM=android-28`, `ANDROID_STL=c++_shared`,
`CMAKE_BUILD_TYPE=RelWithDebInfo`, and the absolute `BMHERO_FILE_TO_C` path.
`tools/apply_android_patches.py` runs automatically during Android configure
and source preparation. It rejects dependency drift or conflicting local edits.

A prebuilt native build can be packaged with `./gradlew -PprebuiltNative=true
assembleDebug`. First place the optimized `libBMHero.so`, `libSDL2.so`, and
NDK `libc++_shared.so` under `build-apk/staging/lib/arm64-v8a/` at repo root.
This is the route used for the first APK after a direct CMake build. Desktop
sources and executable entry remain intact; desktop builds were not tested.

## Verification performed

- Seven host tests passed, including acceptance of the verified 12 MiB
  upload. The real-ROM test is opt-in with `BMHERO_TEST_ROM`.
- Java import harness passed raw, v64, n64 and ZIP imports, invalid/truncated
  rejection and preservation of a previous valid import.
- A C++ window-reference handoff regression failed with the original premature
  release and passed after correcting the ownership transfer.
- Host N64Recomp/RSPRecomp built at the pinned revision; game, audio microcode
  and patch C sources generated successfully (upstream warnings remain).
- NDK ARM64 native configure/build linked `libBMHero.so`; required JNI entry
  points and `SDL_main` are exported, with only packaged or Android-system
  shared library dependencies.
- APK signature and ZIP integrity checks passed; ABI is arm64-v8a, launcher
  and manifest are correct, fonts/controller assets are present, ROM absent.
- Device behavior and 60 FPS are unverified. This is a test build.

Android shell sources draw on Goemon's SDL 2.32.8 Java files; the SDL headers
and native library are fetched from the same release. The Android frontend patch
replaces desktop dialogs with JNI document picking and resolves paths under
app-private storage. Imported ROM replacement is atomic after validation.
