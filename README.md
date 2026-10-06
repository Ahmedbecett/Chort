# 📱 Chort (thileli dz) - Scalable Short-Video Social Platform

[![Latest Release](https://img.shields.io/badge/Latest%20Release-v2.4.6%20(Build%2020406)-brightgreen.svg?style=for-the-badge&logo=android)](https://github.com/Ahmedbecett/Chort/releases/tag/v2.4.6)
[![Direct APK Download](https://img.shields.io/badge/Download%20APK-Chort--v2.4.6--release.apk-blue.svg?style=for-the-badge&logo=google-play)](https://github.com/Ahmedbecett/Chort/releases/download/v2.4.6/Chort-v2.4.6-release.apk)
[![Android Build](https://img.shields.io/badge/Platform-Android%2014%2B%20%7C%20Compose-3DDC84.svg?style=for-the-badge&logo=android)](https://android.com)

---

## 🚀 روابط التحميل المباشرة وإصدار التطبيق (Direct APK Downloads)

> **الإصدار الحالي:** `v2.4.6` | **رقم البناء (versionCode):** `20406` | **تاريخ التحديث:** أكتوبر 2026

- 📥 **[تحميل مباشر للإصدار v2.4.6 (Chort-v2.4.6-release.apk)](https://github.com/Ahmedbecett/Chort/releases/download/v2.4.6/Chort-v2.4.6-release.apk)**
- ⚡ **[رابط دائم لأحدث إصدار (Always Latest APK): Chort-latest.apk](https://github.com/Ahmedbecett/Chort/releases/latest/download/Chort-latest.apk)**
- 📦 **[جميع الإصدارات وسجلات البناء (Releases Page)](https://github.com/Ahmedbecett/Chort/releases)**
- ⚙️ **[متابعة بناء الـ APK عبر GitHub Actions](https://github.com/Ahmedbecett/Chort/actions)**

### 🛠️ التحديثات المطبقة في الإصدار v2.4.6:
1. **تشغيل ورفع الفيديو:** دعم كامل لتشغيل الفيديوهات المختارة من الهاتف محلياً (`content://` و `file://`) وإصلاح خطأ `تعذر تشغيل الفيديو - Invalid or empty video URL`.
2. **رسائل التحقق للهاتف (SMS):** إرسال رمز التحقق كرسالة SMS حقيقية إلى رقم الهاتف عبر Firebase Phone Auth بدلاً من إظهاره على الشاشة.
3. **تسجيل الدخول بجيميل (Google):** تحديث معرّف العميل ومطابقة مشروع Firebase (`shortvideoapp-6b870`) لمنع الرجوع للشاشة السابقة.

---

## 🏗️ High-Scale Cloud Architecture Overview

```
                          ┌───────────────────────────┐
                          │     Chort Mobile App      │
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

The repository includes a production-grade backend server located in the `/backend` folder:

### 1. Database (PostgreSQL 16 via Prisma ORM)
- Models: `Users`, `Profiles`, `Videos`, `VideoMetadata`, `Comments`, `Likes`, `Follows`, `Views`, `Shares`, `SavedVideos`, `Hashtags`, `Notifications`, `Reports`, and `Sessions`.
- Optimized indexes on `[status, visibility, createdAt]` and `[viewsCount, likesCount]` for sub-50ms Feed queries.

### 2. High-Performance Caching & Queues (Redis 7 + BullMQ)
- **Feed Cache:** Redis cache-aside pattern serves dynamic FYP recommendations instantly.
- **View Deduplication:** Anti-fraud view counter with 60-second sliding deduplication window.
- **BullMQ Workers:** Asynchronous video transcoding queue preventing API CPU bottlenecks.

### 3. Video Transcoding Pipeline (FFmpeg HLS Worker)
- Multi-bitrate Adaptive Bitrate Streaming (ABR):
  - **1080p:** 4500 kbps (1920x1080)
  - **720p:** 2500 kbps (1280x720)
  - **480p:** 1200 kbps (854x480)
  - **360p:** 800 kbps (640x360)
- Master HLS playlist (`master.m3u8`) with automatic client-side bandwidth switching.
- Automatic HD thumbnail extraction at keyframe intervals.

### 4. Direct Cloud Upload Pipeline
- Client requests pre-signed PUT URLs from the backend API.
- The mobile app uploads directly to Cloud Storage / S3 / MinIO, offloading high bandwidth traffic from API servers.
- Live byte progress listener shows real-time upload percentage (0% to 100%).

---

## 🐳 Quick Start: Running the Entire Backend with Docker

To run the complete backend stack (PostgreSQL, Redis, MinIO S3, API Server, FFmpeg Transcoder):

```bash
cd backend
docker compose up -d
```

Services will be accessible at:
- **API Server:** `http://localhost:4000/api/v1`
- **Health Check:** `http://localhost:4000/api/v1/health`
- **MinIO Storage Console:** `http://localhost:9001` (User: `minio_admin` / Pass: `minio_secure_password`)
- **PostgreSQL:** `localhost:5432`
- **Redis:** `localhost:6379`

---

## 📱 Android Client Features (`/app`)

- **Vertical Video Feed:** Smooth full-screen TikTok-style swipe gestures with seamless looping and pre-caching.
- **Real-Time Byte Progress Upload:** Direct upload with progress percentage bar from camera or gallery.
- **Interactions:** Live comments, likes, shares, user profiles, and follow system.
- **Bottom Audio Control:** The mute/unmute speaker control lives in the lower audio bar (`feed_mute_button`); the old top-end speaker icon was removed.
- **Admin Moderation Portal:** Dedicated dashboard for reviewing user reports, account status management, and policy compliance.
- **Signed Release:** Signed with a 30-year production certificate (`CN=Ahmed Becetti`, `chort-release.jks`) supporting APK Signature Schemes v1/v2/v3.
- **Verifiable Builds:** `versionCode`/`versionName` are bumped on every release, the source commit is stamped into `BuildConfig.GIT_COMMIT` and displayed in Profile, and `scripts/verify_apk.py` proves the APK matches the source tree.

---

## 👨‍💻 Developer Information

- **Developer:** Ahmed Becetti (أحمد بن ستي)
- **Email:** [ahmedbecetti41@gmail.com](mailto:ahmedbecetti41@gmail.com)
- **GitHub:** [@Ahmedbecett](https://github.com/Ahmedbecett)
- **Repository:** [https://github.com/Ahmedbecett/Chort](https://github.com/Ahmedbecett/Chort)

---

© 2026 Chort Video Social Platform. All rights reserved. Developed by Ahmed Becetti.
