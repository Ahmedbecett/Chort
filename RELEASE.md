# ZEVORA Release & Build Policy

This repository contains the ZEVORA Android client, web client, and backend.

## Signing
Release signing material is **not committed**. CI must receive `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` through GitHub Actions secrets. Local release builds use `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`.

## Firebase compatibility
The Android `applicationId` remains `com.aistudio.tokpulse.social` because the checked-in Firebase configuration is registered for that package. Changing it without registering a new Firebase Android app would break Google/Firebase authentication. This is an infrastructure identifier, not the visible ZEVORA brand.

## Production API
The Android client uses the existing production API deployment at `https://chort-nine.vercel.app/`. The hostname is retained intentionally until the backend is migrated to a ZEVORA-owned deployment.

## Build
Use the repository Gradle wrapper and run `./gradlew :app:assembleDebug` for a debug APK. A release build requires signing secrets as described above.

