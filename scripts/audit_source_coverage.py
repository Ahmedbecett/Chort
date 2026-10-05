#!/usr/bin/env python3
"""
audit_source_coverage.py - How much of the *changed* source is really in the APK?

The class-level checks in verify_apk.py prove specific behaviours (speaker icon
gone, bottom audio control present). This auditor is the broad safety net: it
takes every Kotlin/Java file changed since a baseline commit, extracts the
string literals from the *current* source, and measures how many of them are
present in the APK's DEX.

A stale APK - one built from an older revision - scores far below 100% on files
that changed after that revision, so this catches "the APK is not what the
source says" in general, not just for the icon fix.

Usage:
  python3 scripts/audit_source_coverage.py \
      --apk release/Chort-v2.3.0-release.apk --baseline b65c33e
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import zipfile
from pathlib import Path

# String literals shorter than this are too generic to be meaningful evidence.
MIN_LITERAL_LEN = 8

def changed_source_files(baseline: str) -> list[str]:
    """Production sources changed since the baseline.

    Test sources are excluded: they are never compiled into a release APK, so
    their literals cannot be used as evidence.
    """
    out = subprocess.run(
        ["git", "diff", "--name-only", f"{baseline}..HEAD", "--", "app/src"],
        capture_output=True, text=True, check=True,
    ).stdout
    files = []
    for line in out.splitlines():
        if not line.endswith((".kt", ".java")):
            continue
        if "/src/test/" in line or "/src/androidTest/" in line:
            continue
        files.append(line)
    return files


def source_literals(path: str) -> set[str]:
    """Real string literals in a source file (comments and templates skipped).

    Scanned with a small state machine instead of a regex: a regex happily
    matches the *code between* two literals, which produced bogus "missing"
    entries before this was fixed.
    """
    try:
        text = Path(path).read_text(encoding="utf-8", errors="replace")
    except OSError:
        return set()

    values: set[str] = set()
    i, n = 0, len(text)
    while i < n:
        chunk = text[i]
        if chunk == "/" and text.startswith("//", i):
            i = text.find("\n", i)
            if i < 0:
                break
        elif chunk == "/" and text.startswith("/*", i):
            i = text.find("*/", i) + 2
        elif text.startswith('"""', i):
            end = text.find('"""', i + 3)
            i = end + 3 if end >= 0 else n
        elif chunk == '"':
            j, buf = i + 1, []
            while j < n and text[j] not in '"\n':
                if text[j] == "\\":
                    buf.append(text[j])
                    buf.append(text[j + 1] if j + 1 < n else "")
                    j += 2
                    continue
                buf.append(text[j])
                j += 1
            literal = "".join(buf)
            # Runtime templates (${x}/$x) and escapes never appear verbatim in DEX.
            if len(literal) >= MIN_LITERAL_LEN and "$" not in literal and "\\" not in literal:
                if re.search(r"[A-Za-z]", literal):
                    values.add(literal)
            i = j + 1
        else:
            i += 1
    return values


def to_mutf8(text: str) -> bytes:
    """CESU-8/MUTF-8 encoding used by DEX for non-BMP characters (emoji etc.).

    Plain UTF-8 search misses literals containing emoji, because Dalvik stores
    supplementary code points as surrogate pairs.
    """
    expanded: list[str] = []
    for char in text:
        code = ord(char)
        if code > 0xFFFF:
            code -= 0x10000
            expanded.append(chr(0xD800 + (code >> 10)))
            expanded.append(chr(0xDC00 + (code & 0x3FF)))
        else:
            expanded.append(char)
    return "".join(expanded).encode("utf-8", "surrogatepass")


def dex_contains(blob: bytes, literal: str) -> bool:
    return literal.encode("utf-8") in blob or to_mutf8(literal) in blob


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--baseline", default="b65c33e",
                        help="commit the shipped APK was built from")
    parser.add_argument("--min-coverage", type=float, default=85.0,
                        help="minimum per-file coverage percentage (default 85)")
    args = parser.parse_args()

    if not args.apk.exists():
        sys.exit(f"APK not found: {args.apk}")

    with zipfile.ZipFile(args.apk) as archive:
        names = sorted(n for n in archive.namelist() if re.fullmatch(r"classes\d*\.dex", n))
        blob = b"".join(archive.read(n) for n in names)

    files = changed_source_files(args.baseline)
    if not files:
        print("No changed Kotlin/Java sources found for this baseline.")
        return 0

    print(f"APK        : {args.apk.name}")
    print(f"baseline   : {args.baseline}..HEAD ({len(files)} changed source files)\n")
    print(f"{'file':64} {'literals':>8} {'found':>6} {'coverage':>9}")

    failures: list[tuple[str, float, list[str]]] = []
    total_literals = total_found = 0
    for path in files:
        literals = source_literals(path)
        if not literals:
            continue
        found = [lit for lit in literals if dex_contains(blob, lit)]
        total_literals += len(literals)
        total_found += len(found)
        pct = 100.0 * len(found) / len(literals)
        short = path.split("app/src/main/java/")[-1] if "app/src/main/java/" in path else path
        print(f"{short:64} {len(literals):>8} {len(found):>6} {pct:>8.1f}%")
        if pct < args.min_coverage:
            failures.append((short, pct, sorted(set(literals) - set(found))))

    overall = 100.0 * total_found / total_literals if total_literals else 0.0
    print(f"\noverall coverage: {total_found}/{total_literals} literals = {overall:.1f}%")

    if failures:
        print(f"\nFAIL: {len(failures)} file(s) below {args.min_coverage:.0f}% coverage")
        for path, pct, missing in failures:
            print(f"  - {path}: {pct:.1f}%")
            for literal in missing[:5]:
                print(f"      missing: {literal!r}")
        return 1

    print(f"\nPASS: every changed source file is reflected in the APK "
          f"(>= {args.min_coverage:.0f}% literal coverage)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
