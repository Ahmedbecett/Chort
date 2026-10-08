# 📱 Rivo Chort — Scalable Short-Video Social Platform

[![Android Build](https://img.shields.io/badge/Platform-Android%2014%2B%20%7C%20Compose-3DDC84.svg?style=for-the-badge&logo=android)](https://android.com)
[![Release](https://img.shields.io/badge/Release-v3.2.0-FE2C55.svg?style=for-the-badge)](https://github.com/Ahmedbecett/Chort/releases/tag/v3.2.0)

> **Current release:** `v3.2.0` · **versionCode:** `32000` · Rivo rebrand + real licensed HD feed + Firebase phone sessions.

## 📥 Download the app

| | |
|---|---|
| **⬇️ Direct APK download** | **[Rivo-v3.2.0-release.apk (26.1 MB)](https://github.com/Ahmedbecett/Chort/releases/download/v3.2.0/Rivo-v3.2.0-release.apk)** |
| **Release page** | [v3.2.0 — notes + verification report](https://github.com/Ahmedbecett/Chort/releases/tag/v3.2.0) |
| **SHA-256** | `0a97d2e93d1c11902713a76225a90c157543945b3a2d2c189bca191c954038fb` |

- 📦 **[All releases](https://github.com/Ahmedbecett/Chort/releases)**
- ⚙️ **[Release builds (GitHub Actions)](https://github.com/Ahmedbecett/Chort/actions)**

Every APK is rebuilt from source by CI on each release, signed with the **stable
production key** (GitHub Secrets — never a per-build key, never committed), and
verified against the source tree by `scripts/verify_apk.py` before publishing.
Installs over previous versions with no uninstall.

---

## 🆕 What changed in v3.2.0

1. **Rivo Chort rebrand** — new name + R-mark launcher icon (legacy, adaptive,
   monochrome), all user-visible strings rebranded; same package + same
   production key, so it installs directly over 3.1.0.
2. **Real licensed feed in HD** — the server now serves fresh portrait videos
   from Coverr/Pixabay/Pexels (highest-resolution files: 1080p), with zero
   fake seed videos or profiles left in the database.
3. **Firebase phone sessions** — phone login mints a real backend JWT via
   `POST /auth/phone/firebase` (ID-token verified against Google certs).
4. **Cloudflare Stream ready** — uploads can be imported to Cloudflare Stream
   for HLS delivery when `CLOUDFLARE_STREAM_*` is configured (optional).

## 🆕 What changed in v3.1.0

1. **TikTok-style create flow** — MediaStore picker (All/Videos/Photos, albums,
   multi-select, Add-sound), real CameraX camera (right toolbar, 15s/60s/10m,
   photo + text modes), genuine audio mixing and a multi-video publish queue.
2. **Profile + drawer** — reference profile layout with live follow system,
   private-account locks and followers sheets; drawer with Balance, Activity
   center, Offline videos, QR code, ZEVORA Studio, Promote and Settings.
3. **Settings-and-privacy overhaul** — full page structure with deep links, OLED
   black theme, locale support, second-precision screen time, restricted mode.
4. **Server coin wallet** — authoritative ledger with offline earn queue, Boost
   spending, admin user lookup + coin adjustment.
5. **New launcher identity** — renamed to **ZEVORA Play** (زيفورا بلاي) with an
   original neon Z-mark icon.

---

## 🆕 What changed in v3.0.0 (rebuild)

1. **Production cleanup merged in** — OTP dev-echo removed end to end, fabricated
   profile media/avatars/bios removed, backend rejects unknown users instead of
   inventing `@chort.app` accounts, web demo seeds removed.
2. **Mock screens removed** — the simulated DM chat and the fake admin backdoor
   (`devSwitchToAdmin` + "Enter as Platform Admin" button) are gone; the LIVE
   screen is now an honest lobby wired to the real signed-in account (no fake
   viewers, no scripted chat) until the streaming server rolls out.
3. **ZEVORA identity** — app name, logo wordmark, theme, strings (EN/AR) and docs
   rebranded; `applicationId` stays `com.aistudio.tokpulse.social` and local
   data (Room DB, sessions, preferences) is preserved so updates install cleanly.
4. **Stable signing** — the release workflow fails loudly unless the production
   keystore from Secrets is present; `verify_apk.py` additionally rejects the
   debug key and any per-build isolated key, and asserts 3.0.0 DEX markers while
   asserting pre-rebuild markers are absent.
5. **Backend fix** — the missing `ai-moderation.service` module is implemented as
   an honest baseline publish gate (input validation + blocked-term screening),
   documented for a future ML-provider upgrade; backend `tsc --noEmit` is clean.

---

## 🏗️ High-Scale Cloud Architecture Overview

```
                          ┌───────────────────────────┐
                          │     ZEVORA Mobile App     │
                          │   (Kotlin / Compose M3)   │
                          └─────────────┬─────────────┘
                                        │
                         [ Direct Presigned Upload ]
                                        │
                                        ▼
    ┌────────────────────┐     ┌─────────────────────┐     ┌────────────────────┐
    │  Backend API       │     │   Object Storage    │     │ Global CDN Cache   │
    │  (Node.js / Express│◄────┤  (S3 / Cloud Storage│────►│ (Cloudflare / R2)  │
    │   & Prisma ORM)    │     │    / MinIO)         │     └─────────┬──────────┘
    └─────────┬──────────┘     └──────────┬──────────┘               │
              │                           │                          │
        ┌─────┴──────┐                    ▼                  [ Adaptive HLS ]
        │            │         ┌─────────────────────┐               │
        ▼            ▼         │ FFmpeg Transcoder   │               ▼
  ┌───────────┐ ┌───────────┐  │ (1080p, 720p, 480p, │     ┌───────────────────┐
  │ PostgreSQL│ │  Redis 7  │  │  360p Master HLS)   │     │ ExoPlayer Stream  │
  │ (Data)    │ │ (Cache)   │  └─────────────────────┘     └───────────────────┘
  └───────────┘ └───────────┘
```

---

## ⚙️ Backend Services (`/backend`)

Real production services — no demo data paths remain:

- **PostgreSQL 16 (Prisma)** — `Users`, `Profiles`, `Videos`, `Comments`,
  `Likes`, `Follows`, `Views`, `Notifications`, `Reports`, `Sessions`, …
- **Redis 7 + BullMQ** — feed cache-aside, 60s view dedup window, async
  transcode queue.
- **FFmpeg HLS worker** — 1080p/720p/480p/360p ABR + master playlist.
- **Direct cloud upload** — presigned PUT URLs, real byte-progress in-app.
- **Auth** — Google/Facebook OAuth verification, Twilio SMS OTP (dev echo
  permanently disabled), JWT sessions. See [AUTH_SETUP.md](AUTH_SETUP.md).

```bash
cd backend
docker compose up -d
# API: http://localhost:4000/api/v1 · MinIO: http://localhost:9001
```

---

## 📱 Android Client (`/app`)

- **Vertical video feed** — TikTok-style pager, ExoPlayer, bottom audio control.
- **Real upload flow** — camera/gallery → presigned PUT → moderation gate.
- **Real accounts** — email, Google, Facebook, phone OTP (Firebase + backend);
  admin role strictly by owner Firebase UID.
- **Notifications / comments / likes / follows / profiles** — synced with server.
- **Admin moderation portal** — reports queue, logins, violation actions.
- **Verifiable builds** — `versionCode`/`versionName` bumped every release,
  `BuildConfig.GIT_COMMIT` stamped + shown in Profile, APK proven against
  source by `scripts/verify_apk.py`.

Build locally (production keystore required):

```bash
KEYSTORE_PATH=/secure/zevora.jks STORE_PASSWORD=... scripts/build_release.sh
```

---

## 👨‍💻 Developer Information

- **Developer:** Ahmed Becetti (أحمد بن ستي)
- **Email:** [ahmedbecetti41@gmail.com](mailto:ahmedbecetti41@gmail.com)
- **GitHub:** [@Ahmedbecett](https://github.com/Ahmedbecett)
- **Repository:** [https://github.com/Ahmedbecett/Chort](https://github.com/Ahmedbecett/Chort)

---

© 2026 ZEVORA Video Social Platform. All rights reserved. Developed by Ahmed Becetti.
