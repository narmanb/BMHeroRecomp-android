# Bomberman Hero Android port

## Status — initial source preparation, not a playable Android release

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

There is **no Gradle application or APK yet**. These changes are the beginning
of the port, not evidence that its dependencies compile for Android.

## Audited dependency differences

Hero's pinned dependencies:

| Component | Commit |
| --- | --- |
| N64ModernRuntime | `ca568b6ad79b9029d14077f0c3ffa757727c5559` |
| RecompFrontend | `b3b7ebb4ec1a8a763c0191486f1b3329f9499a48` |
| RT64 | `f647df1a084ae67897dba9806c0d467aa0852894` |
| Hero symbols | `37887677d0fba8059e0c3a322e4c9f8b0200a5b4` |
| Hero decomp headers | `a07c57e1ee9bd20d054a7180017d30f5aa86d70f` |

Goemon's Android RT64 is `5bec328c15ab02ab70154d32aa367f3cdb83ab38`;
its runtime is `184283bf222703a49d35115cd5fa35278455a6cb`.
Goemon keeps frontend code in its main repository; Hero uses RecompFrontend as
a submodule. Its Android folder cannot simply be copied unchanged.

Remaining integration work:

1. Port Android RT64/Plume build, native-window handling, host shader compiler
   selection, and SDL dependency setup while checking compatibility with Hero's
   current frontend renderer API. Keep dependency changes pinned and reproducible.
2. Adapt RecompFrontend: Android SDL include/link settings, source-built FreeType,
   app-private paths, and asynchronous Storage Access Framework file selection.
   The current `recompui/src/util/file.cpp` invokes desktop nativefiledialog and
   uses Linux home-directory fallbacks that should not be used on Android.
3. Add Gradle/SDLActivity bootstrap, package `libBMHero.so`, extract the existing
   UI assets and controller DB, and initialize paths before starting native code.
4. Add optional libadrenotools/Turnip import and recovery. Do not claim Vulkan
   1.1 compatibility just from a manifest: verify required RT64 device features.
5. Test installation, ROM import, title screen, controls, saves, suspend/resume,
   and gameplay speed before measuring 60 FPS on the RP5.

Do not replace Hero's interpolation patches with Goemon-specific game patches.

## Required user input

An uncompressed **Bomberman Hero US 1.0** ROM, 16 MiB, normalized SHA-1:

`a36364b7e59351f7551ab351cb3b41ebc4be285b`

Upstream's CI retrieves this from a private repository using secrets which are
not present in a new fork. Desktop release binaries are not a substitute for
the missing generated C sources. The ROM is required for local source generation
and later runtime testing, but must remain out of Git and the APK.

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

## Verification performed

Four host ROM-normalization/rejection tests pass. `git diff --check` passes.
Native compilation, source generation with the real ROM, Android installation,
and performance have **not** been verified. The initial workspace lacked CMake
and the Android SDK/NDK; a CMake invocation returned `command not found`.
