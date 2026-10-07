# 🎬 ZEVORA - TikTok Clone Implementation Guide

## Core Architecture

### Frontend Stack
- **Android:** Kotlin + Jetpack Compose + ExoPlayer
- **Web:** React + TypeScript + Vite

### Backend Stack
- **Runtime:** Node.js + Express
- **Database:** PostgreSQL + Prisma ORM
- **Cache:** Redis
- **Storage:** S3-compatible (AWS S3 / MinIO / Cloud Storage)
- **Auth:** Firebase Auth + JWT
- **Real-time:** Firestore (fallback) / WebSocket (future)

---

## 📱 Android App Features

### Screens
1. **Splash Screen** - App startup, restore session
2. **Auth Screens** - Login/Signup (email, Google, Facebook, phone)
3. **Feed Screen** - Main vertical video feed (TikTok-style)
4. **Upload Screen** - Select video, add caption, publish
5. **Profile Screen** - User profile, video library, followers
6. **Search Screen** - Search videos/creators by hashtag/name
7. **Notifications Screen** - Like/comment/follow notifications
8. **Comments Screen** - View/add comments on video
9. **Admin Panel** - Moderation, reports, user management
10. **Settings Screen** - Profile edit, password change, logout

### Key Features
- ✅ Vertical infinite scroll feed
- ✅ Double-tap to like
- ✅ Swipe to next/previous video
- ✅ Auto-play on view
- ✅ Comment creation/deletion
- ✅ Follow/Unfollow users
- ✅ Real-time notifications
- ✅ Video upload with thumbnail
- ✅ User search
- ✅ Hashtag search
- ✅ Video sharing
- ✅ Save/Bookmark videos
- ✅ Account management
- ✅ Content moderation (admin)

---

## 🖥️ Backend API Endpoints

### Authentication
```
POST   /api/v1/auth/register          - Email registration
POST   /api/v1/auth/login             - Email login
POST   /api/v1/auth/logout            - Logout
POST   /api/v1/auth/oauth/google      - Google OAuth
POST   /api/v1/auth/oauth/facebook    - Facebook OAuth
POST   /api/v1/auth/phone/request     - Request phone OTP
POST   /api/v1/auth/phone/verify      - Verify phone OTP
POST   /api/v1/auth/recover/request   - Request account recovery
POST   /api/v1/auth/recover/confirm   - Confirm recovery
POST   /api/v1/auth/password/change   - Change password
GET    /api/v1/auth/providers         - List linked providers
POST   /api/v1/auth/link              - Link OAuth provider
```

### Videos
```
GET    /api/v1/feed                   - Get FYP feed (with pagination)
GET    /api/v1/feed/following         - Get following feed
GET    /api/v1/videos                 - Alias for /feed
GET    /api/v1/external/videos        - Licensed stock videos
GET    /api/v1/videos/:id/stream      - Stream video
GET    /api/v1/videos/:id/thumbnail   - Get thumbnail
POST   /api/v1/videos/:id/view        - Record view
POST   /api/v1/videos/upload-url      - Get presigned upload URL
POST   /api/v1/videos/complete-upload - Finalize upload
DELETE /api/v1/videos/:id             - Delete video
```

### Interactions
```
POST   /api/v1/videos/:id/like        - Toggle like
GET    /api/v1/videos/:id/comments    - Get comments
POST   /api/v1/videos/:id/comments    - Add comment
DELETE /api/v1/videos/:id/comments/:cid - Delete comment
POST   /api/v1/videos/:id/share       - Record share
POST   /api/v1/videos/:id/save        - Save/bookmark
```

### Users
```
GET    /api/v1/users/:id/profile      - Get user profile
PATCH  /api/v1/users/:id              - Update profile
DELETE /api/v1/users/:id              - Delete account
POST   /api/v1/users/:id/follow       - Follow user
DELETE /api/v1/users/:id/follow       - Unfollow user
GET    /api/v1/users/:id/followers    - List followers
GET    /api/v1/users/:id/following    - List following
GET    /api/v1/users/:id/saved        - Saved videos
GET    /api/v1/users/:id/liked        - Liked videos
```

### Search
```
GET    /api/v1/search?q=query         - Search videos/creators
GET    /api/v1/hashtags/:tag/videos   - Videos by hashtag
```

### Notifications
```
GET    /api/v1/users/:id/notifications      - Get notifications
POST   /api/v1/users/:id/notifications/read - Mark as read
```

### Reports & Moderation
```
POST   /api/v1/reports                           - Submit report
GET    /api/v1/admin/reports                    - List reports (admin)
POST   /api/v1/admin/reports/:id/resolve        - Resolve report (admin)
GET    /api/v1/admin/overview                   - Platform stats (admin)
GET    /api/v1/admin/logins                     - Login records (admin)
GET    /api/v1/admin/users                      - User list (admin)
POST   /api/v1/admin/users/:id/status           - Set user status (admin)
```

---

## 🗄️ Database Schema

### User
```prisma
model User {
  id                String    @id @default(cuid())
  email             String    @unique
  username          String    @unique
  passwordHash      String    // Can be "OAUTH_OR_SESSION" for OAuth-only
  phone             String?
  phoneVerified     Boolean   @default(false)
  role              String    @default("USER")  // USER, ADMIN, MODERATOR
  status            String    @default("ACTIVE") // ACTIVE, SUSPENDED, BANNED, DELETED
  primaryProvider   String    @default("email") // email, google, facebook, phone
  profile           Profile?
  videos            Video[]
  comments          Comment[]
  likes             Like[]
  followers         Follow[]   @relation("follower")
  following         Follow[]   @relation("following")
  sessions          Session[]
  accounts          Account[]
  notifications     Notification[]
  reports           Report[]   @relation("reporter")
  violations        Violation[]
  privacyRequests   PrivacyRequest[]
  createdAt         DateTime   @default(now())
  updatedAt         DateTime   @updatedAt
}

model Profile {
  id                String    @id @default(cuid())
  userId            String    @unique
  user              User      @relation(fields: [userId], references: [id], onDelete: Cascade)
  displayName       String?
  bio               String?   @db.Text
  avatarUrl         String?
  bannerUrl         String?
  isVerified        Boolean   @default(false)
  followersCount    Int       @default(0)
  followingCount    Int       @default(0)
  likesReceived     Int       @default(0)
  createdAt         DateTime  @default(now())
  updatedAt         DateTime  @updatedAt
}

model Video {
  id                String    @id @default(cuid())
  creatorId         String
  creator           User      @relation(fields: [creatorId], references: [id], onDelete: Cascade)
  caption           String?   @db.Text
  description       String?   @db.Text
  videoUrl          String    // S3 URL
  streamUrl         String?   // Streaming URL
  thumbnailUrl      String?   // Thumbnail URL
  originalKey       String?   // S3 object key
  musicTitle        String?   // Background music/sound
  duration          Int?      // Seconds
  aspectRatio       String?   // 9:16, 16:9, etc
  visibility        String    @default("PUBLIC") // PUBLIC, PRIVATE, DRAFT
  status            String    @default("READY")  // DRAFT, PROCESSING, READY, FAILED, DELETED
  comments          Comment[]
  likes             Like[]
  shares            Share[]
  views             View[]
  saves             SavedVideo[]
  hashtags          VideoHashtag[]
  reports           Report[]   @relation("videoReport")
  likesCount        Int       @default(0)
  commentsCount     Int       @default(0)
  sharesCount       Int       @default(0)
  viewsCount        Int       @default(0)
  createdAt         DateTime  @default(now())
  updatedAt         DateTime  @updatedAt
}

model Comment {
  id                String    @id @default(cuid())
  videoId           String
  video             Video     @relation(fields: [videoId], references: [id], onDelete: Cascade)
  authorId          String
  author            User      @relation(fields: [authorId], references: [id], onDelete: Cascade)
  content           String    @db.Text
  likes             Int       @default(0)
  createdAt         DateTime  @default(now())
  updatedAt         DateTime  @updatedAt
}

model Like {
  id                String    @id @default(cuid())
  videoId           String
  video             Video     @relation(fields: [videoId], references: [id], onDelete: Cascade)
  userId            String
  user              User      @relation(fields: [userId], references: [id], onDelete: Cascade)
  createdAt         DateTime  @default(now())
  @@unique([videoId, userId])
}

model Follow {
  id                String    @id @default(cuid())
  followerId        String
  follower          User      @relation("follower", fields: [followerId], references: [id], onDelete: Cascade)
  followingId       String
  following         User      @relation("following", fields: [followingId], references: [id], onDelete: Cascade)
  createdAt         DateTime  @default(now())
  @@unique([followerId, followingId])
}

model Notification {
  id                String    @id @default(cuid())
  userId            String
  user              User      @relation(fields: [userId], references: [id], onDelete: Cascade)
  actorId           String
  type              String    // like, comment, follow
  message           String
  referenceId       String?   // video ID or user ID
  isRead            Boolean   @default(false)
  createdAt         DateTime  @default(now())
}

model Report {
  id                String    @id @default(cuid())
  reporterId        String
  reporter          User      @relation("reporter", fields: [reporterId], references: [id], onDelete: Cascade)
  videoId           String?
  video             Video?    @relation("videoReport", fields: [videoId], references: [id], onDelete: SetNull)
  targetUserId      String?
  targetUser        User?     @relation(fields: [targetUserId], references: [id], onDelete: SetNull)
  reason            String
  description       String?   @db.Text
  status            String    @default("PENDING") // PENDING, REVIEWING, RESOLVED, DISMISSED
  action            String?   // remove_video, suspend_user, ban_user, none
  adminNotes        String?   @db.Text
  resolvedBy        String?   // Admin ID
  resolvedAt        DateTime?
  createdAt         DateTime  @default(now())
}

model Violation {
  id                String    @id @default(cuid())
  userId            String
  user              User      @relation(fields: [userId], references: [id], onDelete: Cascade)
  reason            String
  severity          String    // warning, suspension, ban
  strikeCount       Int       @default(1)
  expiresAt         DateTime?
  createdAt         DateTime  @default(now())
}

model PrivacyRequest {
  id                String    @id @default(cuid())
  userId            String
  user              User      @relation(fields: [userId], references: [id], onDelete: Cascade)
  type              String    // data_export, account_deletion
  status            String    @default("PENDING") // PENDING, APPROVED, REJECTED, COMPLETED
  reason            String?
  completedAt       DateTime?
  createdAt         DateTime  @default(now())
}

model Session {
  id                String    @id @default(cuid())
  userId            String
  user              User      @relation(fields: [userId], references: [id], onDelete: Cascade)
  token             String    @unique // JWT jti
  userAgent         String?
  ipAddress         String?
  expiresAt         DateTime
  createdAt         DateTime  @default(now())
}

model Account {
  id                String    @id @default(cuid())
  userId            String
  user              User      @relation(fields: [userId], references: [id], onDelete: Cascade)
  provider          String    // google, facebook
  providerId        String
  email             String?
  createdAt         DateTime  @default(now())
  @@unique([provider, providerId])
}

model Share {
  id                String    @id @default(cuid())
  videoId           String
  video             Video     @relation(fields: [videoId], references: [id], onDelete: Cascade)
  sharedBy          String?
  createdAt         DateTime  @default(now())
}

model View {
  id                String    @id @default(cuid())
  videoId           String
  video             Video     @relation(fields: [videoId], references: [id], onDelete: Cascade)
  viewedBy          String?
  createdAt         DateTime  @default(now())
}

model SavedVideo {
  id                String    @id @default(cuid())
  videoId           String
  video             Video     @relation(fields: [videoId], references: [id], onDelete: Cascade)
  userId            String
  user              User      @relation(fields: [userId], references: [id], onDelete: Cascade)
  createdAt         DateTime  @default(now())
  @@unique([videoId, userId])
}

model Hashtag {
  id                String    @id @default(cuid())
  tag               String    @unique
  usageCount        Int       @default(0)
  videos            VideoHashtag[]
  createdAt         DateTime  @default(now())
}

model VideoHashtag {
  id                String    @id @default(cuid())
  videoId           String
  video             Video     @relation(fields: [videoId], references: [id], onDelete: Cascade)
  hashtagId         String
  hashtag           Hashtag   @relation(fields: [hashtagId], references: [id], onDelete: Cascade)
  @@unique([videoId, hashtagId])
}
```

---

## 🔐 Security Checklist

- [x] Firestore rules: Role-based access control
- [x] Storage rules: User-scoped uploads
- [x] Backend: JWT authentication
- [x] Backend: Admin checks
- [x] Backend: User ownership validation
- [x] Backend: Rate limiting
- [x] Backend: Input validation
- [x] Backend: SQL injection prevention (Prisma)
- [x] Android: Session token storage (secure)
- [x] Android: HTTPS only
- [x] Android: Certificate pinning (optional)

---

## 🚀 Deployment

### Backend
- Deploy to Vercel, Heroku, AWS, or your own server
- Set environment variables (DB, Redis, S3, OAuth, etc.)
- Run migrations: `npx prisma migrate deploy`
- Start server: `npm start`

### Android
- Build release APK: `./gradlew assembleRelease`
- Sign APK with release key
- Upload to Google Play Store

### Configuration Required
- Firebase project setup
- PostgreSQL database
- Redis instance
- S3-compatible storage
- Google OAuth app
- Facebook app
- Twilio SMS account
- Domain/hosting

---

**Version:** 1.0.0  
**Last Updated:** 2026-10-07
