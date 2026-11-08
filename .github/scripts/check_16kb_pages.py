#!/usr/bin/env python3
"""Fails when an APK or app bundle has native code Google Play would refuse.

Play requires every 64-bit native library (arm64-v8a, x86_64) to support
16 KB memory pages: each loadable ELF segment aligned to a multiple of 16 KB
(p_align). In an APK the library must also be stored uncompressed, starting at
a 16 KB offset, so it can be mapped straight from the file. 32-bit libraries
are not covered by the requirement and are not checked.

2.4.2's first upload was refused for a library that failed this (the
TensorFlow Lite Task Library), and nothing before Play noticed.

Usage: check_16kb_pages.py <app.apk|app.aab> [...]
"""
import struct
import sys
import zipfile

PAGE = 16 * 1024
PT_LOAD = 1


def load_alignments(elf):
    """The p_align of each PT_LOAD segment of a 64-bit ELF, or None for a 32-bit one."""
    if elf[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")
    if elf[4] != 2:
        return None
    endian = "<" if elf[5] == 1 else ">"
    ph_off = struct.unpack_from(endian + "Q", elf, 32)[0]
    ph_size, ph_count = struct.unpack_from(endian + "HH", elf, 54)
    aligns = []
    for i in range(ph_count):
        entry = ph_off + i * ph_size
        if struct.unpack_from(endian + "I", elf, entry)[0] == PT_LOAD:
            aligns.append(struct.unpack_from(endian + "Q", elf, entry + 48)[0])
    return aligns


def data_offset(archive, raw, info):
    """Where an entry's bytes start in the zip file (after its local header)."""
    name_len, extra_len = struct.unpack_from("<HH", raw, info.header_offset + 26)
    return info.header_offset + 30 + name_len + extra_len


def check(path):
    problems = []
    checked = 0
    is_apk = path.lower().endswith(".apk")
    with open(path, "rb") as f:
        raw = f.read()
    with zipfile.ZipFile(path) as archive:
        for info in archive.infolist():
            if not info.filename.endswith(".so"):
                continue
            aligns = load_alignments(archive.read(info))
            if aligns is None:
                continue
            checked += 1
            # 0 and 1 mean "no alignment": not 16 KB either.
            bad = [hex(a) for a in aligns if a < PAGE or a % PAGE != 0]
            if bad:
                problems.append(f"{info.filename}: segments aligned to {', '.join(bad)}, not 16 KB")
            if is_apk:
                if info.compress_type != zipfile.ZIP_STORED:
                    problems.append(f"{info.filename}: compressed in the APK")
                elif data_offset(archive, raw, info) % PAGE != 0:
                    problems.append(f"{info.filename}: not at a 16 KB offset in the APK")
    return checked, problems


def main(paths):
    if not paths:
        print(__doc__.strip().splitlines()[-1])
        return 2
    failed = False
    for path in paths:
        checked, problems = check(path)
        if checked == 0:
            print(f"{path}: no 64-bit native libraries found")
            failed = True
        elif problems:
            print(f"{path}: {len(problems)} problem(s) in {checked} 64-bit libraries")
            for p in problems:
                print(f"  {p}")
            failed = True
        else:
            print(f"{path}: all {checked} 64-bit native libraries support 16 KB pages")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
