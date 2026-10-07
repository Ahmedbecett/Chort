# ZEVORA Internal Cleanup & UI Audit — 2026-10-07

## Android UI
- Splash/auth: ZEVORA branding, email, Google, Facebook, phone/Firebase, OTP/recovery.
- Main navigation: Feed, Discover, Create/Upload, Inbox/Notifications, Friends, Profile.
- Feed: full-screen vertical video, real server feed, likes/comments/shares/follow/sound/save.
- Discover, Live, Upload/Edit, Profile, Chat/Inbox, Settings, Legal, and Admin moderation surfaces are present.
- Dark neon ZEVORA theme and custom font assets remain in use.
- Compatibility aliases remain only where older feature modules still import legacy symbols.

## Web UI
- Removed stale chort.app profile/video links; links now use the current site origin.
- Replaced the fake QR drawing with a real QR image generated from the current profile URL.
- Removed fake APK size/verification claims from the download modal; it points to the real Releases page until an actual release exists.
- Removed the fabricated default wallet/RIB value.
- Cleaned ZEVORA storage/event keys in the cleaned web modules.

## Backend and infrastructure
- User-facing legacy branding was cleaned.
- Existing production API hostname chort-nine.vercel.app is retained intentionally as infrastructure.
- Android applicationId remains com.aistudio.tokpulse.social because Firebase is registered for that package; changing it without a new Firebase registration would break authentication.
- No private keystore or production .env file was added.

## Verification
- ZIP extraction/source structure: passed.
- JSON/XML parsing: passed.
- Python source checks: passed.
- Local Gradle compilation could not run because Gradle 9.3.1 could not be downloaded in the isolated environment.
- Cleanup PR was merged into main.
- Vercel checks reported deployment-rate-limit failures, not a source compilation error.
- No successful post-merge Android CI artifact has been observed yet, so no APK is claimed as verified.

## Final status
The cleaned source is merged to main. Remaining legacy names are deliberate infrastructure/compatibility identifiers, not visible ZEVORA branding.
