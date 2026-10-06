#!/usr/bin/env python3
"""
verify_apk.py - Prove that a built APK really contains the current source code.

Why this exists
---------------
Chort shipped a stale APK: the committed artifact kept versionCode 20200 /
versionName "2.2.0" identical to the source, so a build made *before* the
speaker-icon migration was indistinguishable from a fresh one and kept being
installed on-device.

This script performs binary-level verification of the APK against the source
tree, so "the APK reflects the latest source" is a checked fact, not a claim:

  1. Manifest identity      - versionCode / versionName / package / minSdk.
  2. Signature              - signature schemes + signer certificate.
  3. Global DEX string scan - strings that only exist in the *current* source
                              must be present in the APK.
  4. Class-level DEX check  - com.example.ui.components.VideoPlayerViewKt must
                              NOT contain the removed top-end speaker indicator
                              (Alignment.TopEnd + VolumeUp/VolumeOff icons),
                              while FeedScreenKt must contain the replacement
                              bottom audio control (testTag feed_mute_button).
  5. Commit stamp           - the short git hash compiled into
                              BuildConfig.GIT_COMMIT must be present.
  6. Anti-stale check       - the produced APK must differ from any previous
                              release APK, which must itself fail check 3/4.

Exit code 0 = every check passed. Non-zero = at least one check failed.

Usage:
  python3 scripts/verify_apk.py \
      --apk release/Chort-v1.1.0-release.apk \
      --old-apk /tmp/old/Chort-v2.2.0-release.apk \
      --report release/VERIFICATION_REPORT.md
"""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

# ----------------------------------------------------------------------------
# Expectations derived from the current source tree (see the referenced files).
# ----------------------------------------------------------------------------

# Strings that exist ONLY in the current source and must therefore be compiled
# into a fresh APK. Each entry is (string, where it comes from).
REQUIRED_DEX_STRINGS = [
    ("feed_mute_button", "FeedScreen.kt - bottom audio control testTag (moved from top)"),
    ("player_retry_button", "VideoPlayerView.kt - real retry UI testTag"),
    ("player_skip_button", "VideoPlayerView.kt - skip control testTag"),
    ("Failed to decode/stream media", "VideoPlayerView.kt - playback error path"),
    ("Primary API error", "TokPulseRepository.kt - primary cluster fallback"),
    ("https://chort-nine.vercel.app/", "TokPulseApi.kt - fallback cluster"),
    ("https://chort-nmk4.vercel.app/", "TokPulseApi.kt - primary cluster"),
    ("thileli dz-Android/", "VideoPlayerView.kt - version-stamped User-Agent"),
]

# Markers that must be ABSENT from VideoPlayerViewKt: they belonged to the
# top-end speaker indicator removed in commit 32c841e.
FORBIDDEN_IN_VIDEOPLAYERVIEW = [
    ("getTopEnd()", "top-end alignment of the removed speaker button"),
    ("VolumeOffKt", "VolumeOff icon of the removed speaker button"),
    ("VolumeUpKt", "VolumeUp icon of the removed speaker button"),
    ("Mute Audio", "contentDescription of the removed top speaker button"),
    ("Unmute Audio", "contentDescription of the removed top speaker button"),
]

# Markers that must be PRESENT in FeedScreenKt: the replacement bottom control.
REQUIRED_IN_FEEDSCREEN = [
    ("feed_mute_button", "bottom audio control testTag"),
    ("Mute Audio", "bottom audio control contentDescription"),
    ("VolumeUpKt", "VolumeUp icon used by the bottom audio control"),
]

VIDEO_CLASS = "com.example.ui.components.VideoPlayerViewKt"
FEED_CLASS = "com.example.ui.screens.feed.FeedScreenKt"

# ----------------------------------------------------------------------------
# Tooling helpers
# ----------------------------------------------------------------------------

RESULTS: list[tuple[str, bool, str]] = []


def record(name: str, ok: bool, detail: str = "") -> None:
    RESULTS.append((name, ok, detail))
    flag = "PASS" if ok else "FAIL"
    print(f"[{flag}] {name}" + (f" - {detail}" if detail else ""))


def find_sdk() -> Path:
    for candidate in (
        os.environ.get("ANDROID_SDK_ROOT"),
        os.environ.get("ANDROID_HOME"),
        "/opt/android-sdk",
        str(Path.home() / "Android" / "Sdk"),
    ):
        if candidate and Path(candidate).is_dir():
            return Path(candidate)
    sys.exit("Could not locate the Android SDK (set ANDROID_HOME or ANDROID_SDK_ROOT).")


def find_tool(sdk: Path, name: str) -> Path:
    if name == "apkanalyzer":
        direct = sdk / "cmdline-tools" / "latest" / "bin" / "apkanalyzer"
        if direct.exists():
            return direct
    if name == "apksigner":
        for bt in sorted((sdk / "build-tools").glob("*"), reverse=True):
            candidate = bt / "apksigner"
            if candidate.exists():
                return candidate
    if name == "aapt2":
        for bt in sorted((sdk / "build-tools").glob("*"), reverse=True):
            candidate = bt / "aapt2"
            if candidate.exists():
                return candidate
    found = shutil.which(name)
    if found:
        return Path(found)
    sys.exit(f"Required tool not found: {name}")


def run(cmd: list[str], timeout: int = 900) -> str:
    env = dict(os.environ)
    env.setdefault("JAVA_HOME", env.get("JAVA_HOME", ""))
    proc = subprocess.run(
        cmd, capture_output=True, text=True, timeout=timeout, env=env, errors="replace"
    )
    return proc.stdout + proc.stderr


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def to_mutf8(text: str) -> bytes:
    """CESU-8/MUTF-8 encoding DEX uses for non-BMP characters (emoji)."""
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


def blob_contains(blob: bytes, needle: str) -> bool:
    return needle.encode("utf-8") in blob or to_mutf8(needle) in blob


def dex_payload(apk: Path) -> bytes:
    """Concatenated raw bytes of every classes*.dex inside the APK."""
    blob = bytearray()
    with zipfile.ZipFile(apk) as archive:
        names = sorted(n for n in archive.namelist() if re.fullmatch(r"classes\d*\.dex", n))
        for name in names:
            blob += archive.read(name)
    return bytes(blob)


def class_code(apkanalyzer: Path, apk: Path, fqcn: str) -> str:
    """Decompiled dalvik listing for one class (empty string when not found)."""
    out = run([str(apkanalyzer), "dex", "code", "--class", fqcn, str(apk)])
    if "Class not found" in out or "not found" in out.lower() and fqcn not in out:
        return ""
    return out


# ----------------------------------------------------------------------------
# Checks
# ----------------------------------------------------------------------------

def check_manifest(sdk: Path, apk: Path, expect_code: str | None, expect_name: str | None) -> str:
    badging = run([str(find_tool(sdk, "aapt2")), "dump", "badging", str(apk)])
    package = re.search(r"^package: name='([^']+)'", badging, re.M)
    version_code = re.search(r"versionCode='([^']+)'", badging)
    version_name = re.search(r"versionName='([^']+)'", badging)

    pkg = package.group(1) if package else "?"
    code = version_code.group(1) if version_code else "?"
    name = version_name.group(1) if version_name else "?"

    record(
        "Manifest identity",
        pkg == "com.aistudio.tokpulse.social" and bool(code) and bool(name),
        f"package={pkg} versionCode={code} versionName={name}",
    )
    if expect_code:
        record("versionCode bumped", code == expect_code, f"expected {expect_code}, got {code}")
    if expect_name:
        record("versionName", name == expect_name, f"expected {expect_name}, got {name}")
    return f"{pkg} {code} {name}"


def check_signature(sdk: Path, apk: Path) -> str:
    out = run([str(find_tool(sdk, "apksigner")), "verify", "--print-certs", "-v", str(apk)])
    schemes = {
        "v1": "Verified using v1 scheme (JAR signing): true" in out,
        "v2": "Verified using v2 scheme (APK Signature Scheme v2): true" in out,
        "v3": re.search(r"Verified using v3 scheme \(APK Signature Scheme v3\): true", out) is not None,
    }
    dn = re.search(r"certificate DN: (.+)", out)
    digest = re.search(r"certificate SHA-256 digest: ([0-9a-f]+)", out)
    signer = dn.group(1).strip() if dn else "?"
    record(
        "APK signature valid",
        "Verifies" in out,
        f"{signer} | SHA-256={digest.group(1)[:16] + '...' if digest else '?'}",
    )
    record(
        "Signature schemes (v2 required for minSdk 24; v3 for key rotation)",
        schemes["v2"],
        f"v2={schemes['v2']} v3={schemes['v3']} v1={schemes['v1']} "
        "(v1 is unnecessary for minSdk 24 and is not emitted by AGP)",
    )
    record(
        "Production signer (not the Android debug key)",
        "Android Debug" not in signer and signer != "?",
        signer,
    )
    return signer


def check_dex_strings(apk: Path, source_commit: str | None) -> None:
    blob = dex_payload(apk)
    missing = []
    for needle, origin in REQUIRED_DEX_STRINGS:
        if not blob_contains(blob, needle):
            missing.append(f"{needle} ({origin})")
    record(
        "Current-source strings present in DEX",
        not missing,
        f"{len(REQUIRED_DEX_STRINGS) - len(missing)}/{len(REQUIRED_DEX_STRINGS)} found"
        + (f" | MISSING: {missing}" if missing else ""),
    )

    if source_commit and source_commit != "unknown":
        present = blob_contains(blob, source_commit)
        record(
            "BuildConfig.GIT_COMMIT matches the source commit",
            present,
            f"{source_commit} {'found' if present else 'NOT found in APK'}",
        )


def check_classes(apkanalyzer: Path, apk: Path, label: str) -> None:
    video_code = class_code(apkanalyzer, apk, VIDEO_CLASS)
    if not video_code:
        record(f"{label}: {VIDEO_CLASS} decompiled", False, "class not found in APK")
    else:
        offenders = [f"{needle} ({why})" for needle, why in FORBIDDEN_IN_VIDEOPLAYERVIEW if needle in video_code]
        record(
            f"{label}: top speaker icon removed from {VIDEO_CLASS}",
            not offenders,
            "no TopEnd/VolumeUp/VolumeOff mute UI" if not offenders else f"STILL PRESENT: {offenders}",
        )

    feed_code = class_code(apkanalyzer, apk, FEED_CLASS)
    if not feed_code:
        record(f"{label}: {FEED_CLASS} decompiled", False, "class not found in APK")
    else:
        missing = [f"{needle} ({why})" for needle, why in REQUIRED_IN_FEEDSCREEN if needle not in feed_code]
        record(
            f"{label}: bottom audio control present in {FEED_CLASS}",
            not missing,
            "bottom mute control compiled in" if not missing else f"MISSING: {missing}",
        )
    return video_code, feed_code


def check_distinct(apk: Path, old_apk: Path | None, old_label: str) -> None:
    if not old_apk or not old_apk.exists():
        return
    new_hash, old_hash = sha256_of(apk), sha256_of(old_apk)
    record(
        f"New APK is not the {old_label} artifact",
        new_hash != old_hash,
        f"new={new_hash[:16]}... old={old_hash[:16]}...",
    )


def check_old_apk_fails(sdk: Path, old_apk: Path, label: str) -> None:
    """Sanity: the stale APK must fail the same checks the new one passes."""
    if not old_apk.exists():
        return
    apkanalyzer = find_tool(sdk, "apkanalyzer")
    blob = dex_payload(old_apk)
    record(
        f"{label} is provably stale (missing current-source strings)",
        b"feed_mute_button" not in blob and b"player_retry_button" not in blob,
        "old APK lacks feed_mute_button / player_retry_button",
    )
    video_code = class_code(apkanalyzer, old_apk, VIDEO_CLASS)
    record(
        f"{label} still contains the top speaker icon",
        "getTopEnd()" in video_code and "VolumeUpKt" in video_code,
        "confirms the icon was shipped before this fix",
    )


# ----------------------------------------------------------------------------
# Report
# ----------------------------------------------------------------------------

def write_report(path: Path, apk: Path, manifest: str, signer: str, commit: str) -> None:
    lines = [
        "# APK Verification Report",
        "",
        f"- **APK**: `{apk.name}`",
        f"- **Path**: `{apk}`",
        f"- **Size**: {apk.stat().st_size:,} bytes",
        f"- **SHA-256**: `{sha256_of(apk)}`",
        f"- **Manifest**: `{manifest}`",
        f"- **Signer**: {signer}",
        f"- **Source commit (BuildConfig.GIT_COMMIT)**: `{commit}`",
        "",
        "## Checks",
        "",
        "| Check | Result | Detail |",
        "| --- | --- | --- |",
    ]
    for name, ok, detail in RESULTS:
        lines.append(f"| {name} | {'PASS' if ok else 'FAIL'} | {detail} |")
    failures = [name for name, ok, _ in RESULTS if not ok]
    lines += [
        "",
        "## Verdict",
        "",
        "**ALL CHECKS PASSED** - the APK contains the current source code."
        if not failures
        else "**FAILED**: " + ", ".join(failures),
        "",
    ]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")
    print(f"\nReport written to {path}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--old-apk", type=Path, default=None, help="previous release APK for anti-stale checks")
    parser.add_argument("--old-label", default="previous", help="label used for the old APK in reports")
    parser.add_argument("--expect-version-code", default=None)
    parser.add_argument("--expect-version-name", default=None)
    parser.add_argument("--source-commit", default=None, help="short git hash compiled into BuildConfig.GIT_COMMIT")
    parser.add_argument("--report", type=Path, default=None)
    args = parser.parse_args()

    apk: Path = args.apk
    if not apk.exists():
        sys.exit(f"APK not found: {apk}")

    sdk = find_sdk()
    apkanalyzer = find_tool(sdk, "apkanalyzer")

    commit = args.source_commit
    if commit is None:
        try:
            commit = subprocess.run(
                ["git", "rev-parse", "--short", "HEAD"], capture_output=True, text=True, check=True
            ).stdout.strip()
        except Exception:
            commit = "unknown"

    print(f"=== Verifying {apk} ===")
    print(f"SDK: {sdk} | source commit: {commit}\n")

    manifest = check_manifest(sdk, apk, args.expect_version_code, args.expect_version_name)
    signer = check_signature(sdk, apk)
    check_dex_strings(apk, commit)
    check_classes(apkanalyzer, apk, "NEW APK")
    check_distinct(apk, args.old_apk, args.old_label)
    if args.old_apk:
        check_old_apk_fails(sdk, args.old_apk, args.old_label)

    failures = [name for name, ok, _ in RESULTS if not ok]
    print("\n=== SUMMARY ===")
    print(f"{len(RESULTS) - len(failures)}/{len(RESULTS)} checks passed")
    if failures:
        print("FAILED CHECKS:")
        for name in failures:
            print(f"  - {name}")

    if args.report:
        write_report(args.report, apk, manifest, signer, commit)

    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
