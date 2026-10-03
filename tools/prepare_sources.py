#!/usr/bin/env python3
"""Validate a user ROM and generate native game sources using host tools."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROM_SHA1 = "a36364b7e59351f7551ab351cb3b41ebc4be285b"
ROOT = Path(__file__).resolve().parents[1]


def normalize_rom(data):
    if len(data) % 4:
        raise ValueError("ROM size must be a multiple of four bytes")
    header = data[:4]
    if header == bytes.fromhex("80371240"):
        return data
    if header == bytes.fromhex("37804012"):
        result = bytearray(data)
        result[0::2], result[1::2] = data[1::2], data[0::2]
        return bytes(result)
    if header == bytes.fromhex("40123780"):
        result = bytearray(len(data))
        for offset in range(4):
            result[offset::4] = data[3 - offset::4]
        return bytes(result)
    raise ValueError("Input is not an uncompressed N64 ROM (.z64, .v64, or .n64)")


def validate_rom(data):
    normalized = normalize_rom(data)
    digest = hashlib.sha1(normalized).hexdigest()
    if digest != ROM_SHA1:
        raise ValueError(f"Expected Bomberman Hero US 1.0 (SHA-1 {ROM_SHA1}); got {digest}")
    return normalized


def run(command, cwd=ROOT):
    subprocess.run([str(value) for value in command], cwd=cwd, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("rom", type=Path, help="Your uncompressed Bomberman Hero US 1.0 ROM")
    parser.add_argument("--check-only", action="store_true", help="Validate without writing or generating anything")
    args = parser.parse_args()
    # Reject archives / accidentally selected large files before reading them.
    if args.rom.stat().st_size != 12 * 1024 * 1024:
        raise ValueError("Expected a 12 MiB Bomberman Hero US 1.0 ROM")
    data = validate_rom(args.rom.read_bytes())
    if args.check_only:
        print("Verified Bomberman Hero US 1.0 ROM")
        return

    for tool in ("N64Recomp", "RSPRecomp"):
        path = ROOT / tool
        if not path.is_file() or not os.access(path, os.X_OK):
            raise ValueError(f"Missing executable host tool: {path}. See docs/ANDROID_PORT.md")
    for tool in ("clang", "clang++", "ld.lld", "make"):
        if not shutil.which(tool):
            raise ValueError(f"Missing host tool: {tool}")
    for file in ("BMHeroSyms/dump.toml", "lib/bmhero/include/ultra64.h",
                 "lib/rt64/src/tools/file_to_c/file_to_c.cpp"):
        if not (ROOT / file).is_file():
            raise ValueError(f"Missing submodule input: {file}. Initialize recursive submodules first")

    run([sys.executable, ROOT / "tools/apply_android_patches.py"])

    target = ROOT / "bmhero.z64"
    temporary = ROOT / "bmhero.z64.tmp"
    try:
        temporary.write_bytes(data)
        temporary.replace(target)
    finally:
        temporary.unlink(missing_ok=True)
    (ROOT / "rsp").mkdir(exist_ok=True)
    (ROOT / "RecompiledPatches").mkdir(exist_ok=True)
    host_dir = ROOT / "build-host-tools"
    host_dir.mkdir(exist_ok=True)
    file_to_c = host_dir / "file_to_c"
    run(["clang++", "-std=c++17", "-O2", "lib/rt64/src/tools/file_to_c/file_to_c.cpp", "-o", file_to_c])
    run([ROOT / "N64Recomp", "bmhero.toml"])
    run([ROOT / "RSPRecomp", "aspMain.toml"])
    run(["make", "-C", "patches", "CC=clang", "LD=ld.lld"])
    run([ROOT / "N64Recomp", "patches.toml"])
    run([file_to_c, "patches/patches.bin", "bk_patches_bin",
         "RecompiledPatches/patches_bin.c", "RecompiledPatches/patches_bin.h"])
    print("Generated game, RSP, and patch sources. This does not build an APK.")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f"Source preparation failed: {error}", file=sys.stderr)
        sys.exit(1)
