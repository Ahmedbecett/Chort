# TikTok Clone - Clean Build

A production-ready short-form video platform inspired by TikTok.

## Stack

- **Frontend (Android):** Kotlin + Jetpack Compose + ExoPlayer
- **Frontend (Web):** React + TypeScript + Vite + Tailwind
- **Backend:** Node.js + Express + TypeScript + Prisma + PostgreSQL
- **Storage:** S3-compatible (MinIO/AWS S3)
- **Real-time:** WebSocket (future notifications)
- **Auth:** JWT + Google OAuth + Phone OTP

## Project Structure

```
.
├── app/                    # Android application
│   ├── src/main/java/com/tiktok/
│   │   ├── ui/screens/     # TikTok screens (Feed, Upload, Profile, Search)
│   │   ├── data/           # Models, API client
│   │   └── viewmodel/      # State management
│   └── build.gradle.kts
├── backend/                # Node.js API server
│   ├── src/
│   │   ├── routes/         # API endpoints
│   │   ├── controllers/    # Business logic
│   │   ├── services/       # Domain services
│   │   ├── models/         # Prisma schema
│   │   └── server.ts       # Entry point
│   ├── docker-compose.yml  # Local dev (PostgreSQL, Redis, MinIO)
│   └── package.json
└── web/                    # React web app
    ├── src/
    │   ├── screens/        # Main screens
    │   ├── components/     # Reusable UI
    │   ├── api/            # API client
    │   └── App.tsx
    └── package.json
```

## Features (TikTok-like)

✅ **Video Feed**
- Vertical infinite scroll
- Auto-play on view
- Swipe to next/previous

✅ **Interactions**
- Like/Unlike
- Comment
- Share
- Follow/Unfollow

✅ **Creator Tools**
- Upload video
- Edit caption + hashtags
- Publish

✅ **Discovery**
- For You Page (FYP) - Personalized feed
- Following feed
- Search by hashtag/creator

✅ **Profile**
- Creator profile
- Video library
- Followers/Following
- Statistics

✅ **Authentication**
- Email/Phone signup
- Google OAuth
- Session management

## Getting Started

### Backend

```bash
cd backend
npm install
docker-compose up -d
npm run dev
# API: http://localhost:4000
```

### Android App

```bash
cd app
./gradlew assembleDebug
```

### Web

```bash
cd web
npm install
npm run dev
# Web: http://localhost:5173
```

## API Endpoints

### Auth
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/oauth/google`
- `POST /api/v1/auth/phone/request`
- `POST /api/v1/auth/phone/verify`

### Videos
- `GET /api/v1/feed` - Get FYP feed
- `GET /api/v1/feed/following` - Following feed
- `POST /api/v1/videos/upload-url` - Get presigned upload URL
- `POST /api/v1/videos/complete-upload` - Finalize upload
- `DELETE /api/v1/videos/:id` - Delete video
- `GET /api/v1/videos/:id/stream` - Stream video

### Interactions
- `POST /api/v1/videos/:id/like`
- `DELETE /api/v1/videos/:id/like`
- `GET /api/v1/videos/:id/comments`
- `POST /api/v1/videos/:id/comments`
- `DELETE /api/v1/videos/:id/comments/:commentId`

### Users
- `GET /api/v1/users/:id/profile`
- `PATCH /api/v1/users/:id` - Update profile
- `POST /api/v1/users/:id/follow`
- `DELETE /api/v1/users/:id/follow`
- `GET /api/v1/users/:id/followers`
- `GET /api/v1/users/:id/following`
- `GET /api/v1/users/:id/videos`

### Search
- `GET /api/v1/search?q=query` - Search videos/creators
- `GET /api/v1/hashtags/:tag` - Videos by hashtag

---

**Developed by:** Ahmed Becetti  
**Repository:** https://github.com/Ahmedbecett/Chort  
**License:** MIT
