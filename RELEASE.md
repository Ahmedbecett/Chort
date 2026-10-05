# Release & APK Build Pipeline

This document describes how a Chort APK is produced and how we guarantee that a
shipped APK actually contains the source code it claims to.

## Why this exists (the stale-APK incident)

The APK installed on devices kept showing the **top speaker icon** even though
the source had removed it. Root cause:

| Problem | Detail |
| --- | --- |
| Artifact never rebuilt | `release/Chort-v2.2.0-release.apk` was committed once in `b65c33e` and its git blob never changed again, even after commit `32c841e` removed the top-end speaker indicator. |
| Version never bumped | The APK *and* the source both said `versionCode 20200` / `versionName 2.2.0`, so a stale build was indistinguishable from a fresh one on-device. |
| Silent debug signing | The release signing config fell back to `debug.keystore` whenever `STORE_PASSWORD` was unset — so `release` APKs shipped signed with `CN=Android Debug`, while the README claimed a production certificate. |
| No build wrapper / no CI | The repo had `gradle-wrapper.properties` but no `gradlew`/`gradle-wrapper.jar`, and no workflow that could rebuild the APK. |

## Fixed pipeline

### 1. Version bump is mandatory

`app/build.gradle.kts`:

```kotlin
versionCode = 20300     // must increase on every release
versionName = "2.3.0"   // must change on every release
```

Historically `versionCode` / `versionName` were never touched between releases.
Android uses `versionCode` to decide whether an APK is an upgrade, so it must
always increase for the new build to install over the old one.

### 2. Commit stamping

Every build compiles the short git hash into `BuildConfig.GIT_COMMIT`
(`defaultConfig`), and the app displays it in **Profile → ⋮ → `Chort v… (code) · <commit>`**.
This makes it possible to confirm on-device which revision is installed and to
verify in the APK binary that it was built from a specific commit.

`scripts/build_release.sh` refuses to build from a dirty tree so the stamped
hash always matches the compiled code.

### 3. Deterministic signing (never the debug key)

Resolution order in the `release` signing config:

1. `KEYSTORE_PATH` env var — explicit keystore file.
2. `STORE_PASSWORD` (+ optional `KEY_ALIAS` / `KEY_PASSWORD`) with the official
   `my-upload-key.jks` at the repo root.
3. `chort-release.jks` — committed fallback keystore for reproducible local/CI
   builds (alias `chort`, store/key password `chortrelease`).

Signing schemes v1, v2 and v3 are all enabled; AGP emits v2 + v3 for this
project (v1/JAR signing is not required at `minSdk 24`). The chosen keystore is
printed during configuration (`[Chort] Release signing keystore: …`).

> **Production note:** `chort-release.jks` is committed for reproducibility, so
> its password is public. For Play Store or any distribution where key secrecy
> matters, pass your own key via `KEYSTORE_PATH` / `STORE_PASSWORD` and rotate.

### 4. Clean, verifiable builds

```bash
scripts/build_release.sh
```

The script:

1. verifies the working tree is clean (commit first!);
2. deletes `app/build`, `build`, `.gradle`, `.kotlin` and **every previous APK**
   in `release/` — a stale artifact can never survive;
3. runs `./gradlew clean assembleRelease` from the current commit;
4. copies the output to `release/Chort-v<versionName>-release.apk`;
5. runs `scripts/verify_apk.py` and writes `release/VERIFICATION_REPORT.md`.

### 5. Verification (the part that was missing)

`python3 scripts/verify_apk.py --apk <apk> [--old-apk <previous apk>]` checks the
binary itself, not the console output:

* manifest `versionCode` / `versionName` / package;
* signature validity, schemes and signer identity (must not be `Android Debug`);
* **global DEX string scan** — strings that only exist in the current source
  (e.g. `feed_mute_button`, `player_retry_button`, the Vercel cluster hosts) must
  be present in the APK;
* **class-level DEX checks** — `com.example.ui.components.VideoPlayerViewKt` must
  no longer reference `Alignment.TopEnd` / `VolumeUp` / `VolumeOff` (the removed
  top speaker icon), while `FeedScreenKt` must contain the replacement bottom
  audio control (`feed_mute_button`);
* **commit stamp** — the short git hash of the source commit must appear in the
  APK's DEX;
* optional anti-stale checks that prove the previous APK fails these same tests.

Exit code `0` means the APK demonstrably matches the current source.

## Installing a new build

The previous public APKs were signed with the Android **debug** key. If the
installed app carries a different signature than the new build, Android refuses
an in-place update and reports *"App not installed"*. In that case uninstall the
old app first, then install the new APK:

```bash
adb uninstall com.aistudio.tokpulse.social
adb install -r release/Chort-v2.3.0-release.apk
```

Once both the installed app and the new APK are signed with the same key
(`chort-release.jks` or your own production key), upgrades install in place.
