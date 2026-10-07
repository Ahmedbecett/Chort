# 📱 ZEVORA — Scalable Short-Video Social Platform



[![Android Build](https://img.shields.io/badge/Platform-Android%2014%2B%20%7C%20Compose-3DDC84.svg?style=for-the-badge&logo=android)](https://android.com)

---



> **الإصدار الحالي:** `v3.0.0` | **رقم البناء (versionCode):** `30000` | **تاريخ التحديث:** أكتوبر 2026

- 📥 **تحميل APK:** استخدم صفحة الإصدارات الرسمية بعد نشر الـRelease.
- ⚡ **آخر APK:** لا يتم عرض رابط وهمي قبل توفر Release فعلي.
- 📦 **[جميع الإصدارات وسجلات البناء (Releases Page)](https://github.com/Ahmedbecett/Chort/releases)**
- ⚙️ **[متابعة بناء الـ APK عبر GitHub Actions](https://github.com/Ahmedbecett/Chort/actions)**

### 🛠️ التحديثات المطبقة في الإصدار v3.0.0 (إعادة بناء كاملة):
1. **هوية ZEVORA الكاملة:** إزالة كل المراجع القديمة من الواجهة (الاسم، الشعار، الألوان، الخط) مع الحفاظ على `applicationId` وقاعدة البيانات المحلية حتى لا يفقد المستخدمون بياناتهم.
2. **تدفق تحقق الهاتف عبر Firebase حصرًا:** إزالة نقاط OTP الاحتياطية غير المدعومة ووضع dev، والتحقق والإعادة عبر Firebase Phone Auth فقط.
3. **إزالة باب الأدمن الخلفي:** لا يوجد دخول ضيف كأدمن؛ أدوات الإدارة تظهر فقط للحسابات المصرّح لها بعد تسجيل الدخول.
4. **توقيع إنتاج ثابت:** الـ APK موقّع بمفتاح الإنتاج المحفوظ في GitHub Secrets (وليس مفتاحًا مؤقتًا)، فيُثبَّت تحديثًا فوق الإصدارات السابقة بدون حذف.
5. **تحقق ثنائي من الـ APK:** `scripts/verify_apk.py` يثبت أن الـ APK مبني من نفس السورس (البصمة، الموقّع، سلاسل DEX، رقم البناء).

---

## 🏗️ High-Scale Cloud Architecture Overview

```
                          ┌───────────────────────────┐
                          │     ZEVORA Mobile App      │
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
- **Signed Release:** signing material is supplied through CI/local environment secrets; no private keystore is committed.
- **Verifiable Builds:** `versionCode`/`versionName` are bumped on every release, the source commit is stamped into `BuildConfig.GIT_COMMIT` and displayed in Profile, and `scripts/verify_apk.py` proves the APK matches the source tree.

---

## 👨‍💻 Developer Information

- **Developer:** Ahmed Becetti (أحمد بن ستي)
- **Email:** [ahmedbecetti41@gmail.com](mailto:ahmedbecetti41@gmail.com)
- **GitHub:** [@Ahmedbecett](https://github.com/Ahmedbecett)
- **Repository:** [https://github.com/Ahmedbecett/Chort](https://github.com/Ahmedbecett/Chort)

---

© 2026 ZEVORA Video Social Platform.

