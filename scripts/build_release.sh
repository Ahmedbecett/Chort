#!/usr/bin/env bash
#
# build_release.sh - deterministic, verifiable ZEVORA release build.
#
# Guarantees:
#   * refuses to build from a dirty working tree (the commit stamped into the
#     APK must be the commit that produced it);
#   * deletes every previous build output and every previous APK, so a stale
#     artifact can never be mistaken for a fresh one;
#   * builds from scratch with the Gradle wrapper (no cached outputs reused);
#   * signs ONLY with the stable production key supplied via environment
#     (never the Android debug key, never a throwaway per-build key);
#   * verifies the produced APK against the source tree before declaring success.
#
# Required environment (same contract as .github/workflows/android-release.yml):
#   KEYSTORE_PATH   - path to the stable production keystore (outside the repo)
#   STORE_PASSWORD  - keystore password
#   KEY_ALIAS       - key alias (default: upload)
#   KEY_PASSWORD    - key password (default: STORE_PASSWORD)
#
# Usage:  KEYSTORE_PATH=/secure/zevora.jks STORE_PASSWORD=... scripts/build_release.sh
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

export JAVA_HOME="${JAVA_HOME:-/opt/toolchain/jdk17}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/opt/gradle-home}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

VERSION_CODE="$(grep -oP 'versionCode\s*=\s*\K[0-9]+' app/build.gradle.kts | head -1)"
VERSION_NAME="$(grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts | head -1)"
GIT_COMMIT="$(git rev-parse --short HEAD)"
APK_NAME="ZEVORA-v${VERSION_NAME}-release.apk"

echo "=== ZEVORA release build ==="
echo "versionCode : $VERSION_CODE"
echo "versionName : $VERSION_NAME"
echo "commit      : $GIT_COMMIT"
echo "output      : release/$APK_NAME"
echo

echo "--- 0a. production signing check ---"
if [ -z "${KEYSTORE_PATH:-}" ] || [ -z "${STORE_PASSWORD:-}" ]; then
  echo "ERROR: KEYSTORE_PATH and STORE_PASSWORD must be set to the stable" >&2
  echo "       production keystore. This script refuses to produce an APK" >&2
  echo "       signed with any other key." >&2
  exit 1
fi
if [ ! -f "$KEYSTORE_PATH" ]; then
  echo "ERROR: keystore not found: $KEYSTORE_PATH" >&2
  exit 1
fi
echo "OK: production keystore configured ($KEYSTORE_PATH)"

echo "--- 0b. working tree check ---"
if [ -n "$(git status --porcelain -- app build.gradle.kts settings.gradle.kts gradle)" ]; then
  echo "ERROR: uncommitted changes in app/ or build files." >&2
  echo "       Commit first so BuildConfig.GIT_COMMIT ($GIT_COMMIT) matches the built code." >&2
  git status --short -- app build.gradle.kts settings.gradle.kts gradle >&2
  exit 1
fi
echo "OK: source tree is clean at $GIT_COMMIT"

echo "--- 1. clean previous build artifacts ---"
rm -rf app/build build .gradle .kotlin .build-outputs
rm -f release/*.apk
echo "OK: app/build, build, .gradle, .kotlin and release/*.apk removed"

echo "--- 2. build signed release APK (from the current commit) ---"
./gradlew --no-daemon clean assembleRelease --stacktrace

BUILT="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$BUILT" ]; then
  echo "ERROR: expected output not found: $BUILT" >&2
  find app/build/outputs -name '*.apk' -print 2>/dev/null >&2 || true
  exit 1
fi

mkdir -p release
cp "$BUILT" "release/$APK_NAME"
echo "OK: release/$APK_NAME ($(stat -c%s "release/$APK_NAME") bytes)"

echo "--- 3. verify the APK against the source tree ---"
python3 scripts/verify_apk.py \
  --apk "release/$APK_NAME" \
  --expect-version-code "$VERSION_CODE" \
  --expect-version-name "$VERSION_NAME" \
  --source-commit "$GIT_COMMIT" \
  --report "release/VERIFICATION_REPORT.md"

echo
echo "=== DONE ==="
echo "commit  : $GIT_COMMIT"
echo "apk     : $ROOT/release/$APK_NAME"
echo "sha256  : $(sha256sum "release/$APK_NAME" | cut -d' ' -f1)"
