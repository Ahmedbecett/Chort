# 🔍 ZEVORA Comprehensive Audit & Repair Plan

**Version:** 1.0  
**Date:** 2026-10-07  
**Approach:** Conservative - Analyze, Preserve, Fix Only What's Broken  
**Status:** 🔵 Planning Phase

---

## ✅ Audit Principles

1. **NO deletions without proof of non-use** - Check ALL references first
2. **NO replacements of real functions with mocks** - Only remove actual mocks
3. **NO structural changes** - Keep app design intact
4. **Document everything** - Every file, every reference
5. **Test after fixes** - Verify functionality still works
6. **Create backups** - Before major changes
7. **Minimal changes** - Fix only what's broken

---

## 📁 Phase 1: Project Structure Analysis

### Android App (`app/`)

#### 1.1 Build Configuration
- [ ] `build.gradle.kts` - Review gradle setup
- [ ] `settings.gradle.kts` - Module configuration
- [ ] `gradle.properties` - Build properties
- [ ] `proguard-rules.pro` - Minification rules
- **Action:** Check for misconfigurations, NOT remove

#### 1.2 Manifest & Configuration
- [ ] `AndroidManifest.xml` - App permissions, activities, intent filters
- [ ] `app/google-services.json` - Firebase configuration
- [ ] `strings.xml` - App strings
- [ ] `colors.xml` - Color definitions
- [ ] `dimen.xml` - Dimension values
- [ ] XML config files in `res/xml/`
- **Action:** Verify connectivity, check for hardcoded test values

#### 1.3 Application Class
- [ ] `ZevoraApplication.kt` - App initialization
- [ ] Check: Firebase init, repository setup, analytics
- [ ] Check: Any mock data initialization
- **Action:** Identify and mark mock initializations (don't remove yet)

#### 1.4 MainActivity & Navigation
- [ ] `MainActivity.kt` - Entry point, screen routing
- [ ] Navigation flow: splash → auth → feed/profile
- [ ] Check: All screen transitions valid
- [ ] Check: Back button handling correct
- **Action:** Map complete navigation tree

#### 1.5 UI Layer Analysis

**Auth Screens:**
- [ ] `SplashScreen` - App startup
- [ ] `WelcomeAuthScreen` - Login options
- [ ] `AuthScreen` - Email/password auth
- [ ] `PhoneAuthScreen` - Phone OTP
- [ ] `OtpScreen` - OTP verification
- [ ] `RecoveryScreen` - Account recovery
- **Check:** Which use real Firebase? Which are mock?

**Feed & Discovery:**
- [ ] `FeedScreen` - Main video feed (TikTok-like)
- [ ] `DiscoverScreen` - Search functionality
- [ ] `FriendsScreen` - Following/followers list
- [ ] Check: Do they pull from Firestore or mock data?

**Creator Tools:**
- [ ] `UploadScreen` - Video upload
- [ ] `LiveStreamScreen` - Live streaming (if real)
- [ ] `SoundDetailScreen` - Sound/music selection
- **Check:** Real Firebase Storage? Real video processing?

**User Interaction:**
- [ ] `ProfileScreen` - User profile view
- [ ] `InboxScreen` - Messages/notifications
- [ ] `SettingsScreen` - App settings
- [ ] `ChangePasswordScreen` - Password change
- **Check:** Real database updates? Real notifications?

**Admin & Moderation:**
- [ ] `AdminDashboardScreen` - Admin panel
- [ ] `LegalScreen` - Terms/Privacy
- [ ] `ExternalTrackingCenterScreen` - Analytics/tracking
- **Check:** Real admin functions? Mock data?

#### 1.6 Data Layer

**Models & Entities:**
- [ ] Find all data classes (User, Video, Comment, Like, Follow, etc.)
- [ ] Check: Fields match Firestore schema
- [ ] Check: Any mock fields?

**Local Storage:**
- [ ] Room database setup (if used)
- [ ] SharedPreferences usage
- [ ] Cache management
- **Check:** What's stored locally vs remotely?

**Remote Services:**
- [ ] Firestore client initialization
- [ ] Firebase Auth setup
- [ ] Firebase Storage setup
- [ ] Retrofit/HTTP clients (if used)
- **Check:** All connected to REAL Firebase? Or mock endpoints?

**Repository Pattern:**
- [ ] Find all Repository classes
- [ ] Which ones call Firestore?
- [ ] Which ones call mock APIs?
- [ ] Which ones call backend APIs?
- **Check:** Data flow accuracy

#### 1.7 ViewModels & State Management
- [ ] All ViewModel classes
- [ ] State flow/Live data setup
- [ ] Error handling
- **Check:** Any hardcoded test data in viewmodels?

---

## 🔥 Phase 2: Firebase Configuration Analysis

### 2.1 Firestore Setup
- [ ] Project ID: `shortvideoapp-6b870`
- [ ] Database location
- [ ] Collections expected:
  - [ ] `users` - User profiles
  - [ ] `videos` - Video metadata
  - [ ] `comments` - Video comments
  - [ ] `likes` - Like records
  - [ ] `follows` - Follow relationships
  - [ ] `notifications` - User notifications
  - [ ] `reports` - Content reports
  - [ ] `violations` - User violations/strikes
  - [ ] `privacyRequests` - Data deletion requests
  - [ ] Other collections?
- **Action:** Map actual schema vs expected schema

### 2.2 Firestore Rules Analysis
- [ ] File: `firestore.rules`
- [ ] Check: Who can read/write each collection
- [ ] Check: Field-level security
- [ ] Check: Admin role validation
- [ ] Check: User data isolation
- **Action:** Identify security gaps (don't fix yet)

### 2.3 Storage Configuration
- [ ] Bucket: `shortvideoapp-6b870.firebasestorage.app`
- [ ] File: `storage.rules`
- [ ] Check: Video file storage path
- [ ] Check: Thumbnail storage
- [ ] Check: User avatar storage
- [ ] Check: Permissions (read/write/delete)
- [ ] Check: Quota management
- **Action:** Map storage structure

### 2.4 Authentication Providers
- [ ] Email/Password auth enabled?
- [ ] Google Sign-In configured?
  - [ ] Client ID: `358490968062-n584hegcbbavgsbbq621191bfbvo78q1.apps.googleusercontent.com`
  - [ ] Check if also in backend
- [ ] Facebook auth configured?
  - [ ] App ID: Check in strings.xml
  - [ ] Check if also in backend
- [ ] Phone OTP configured?
  - [ ] Twilio integration?
  - [ ] Real SMS or mock?
- **Action:** Check provider consistency across platforms

---

## 🖥️ Phase 3: Backend Analysis

### 3.1 API Server Setup
- [ ] `backend/src/server.ts` - Express server
- [ ] Port configuration
- [ ] Middleware setup
- [ ] CORS configuration
- [ ] Rate limiting
- [ ] Error handling
- **Check:** Production-ready configuration?

### 3.2 API Routes
- [ ] `backend/src/routes/api.router.ts` - Main router
- [ ] All endpoints list:
  - [ ] Auth routes (register, login, logout, OAuth)
  - [ ] Video routes (upload, stream, delete)
  - [ ] Feed routes (FYP, following)
  - [ ] Interaction routes (like, comment, share)
  - [ ] User routes (profile, follow, search)
  - [ ] Admin routes (reports, moderation)
  - [ ] Search routes (videos, hashtags)
- **Action:** Map all routes, check if used

### 3.3 Controllers & Services
- [ ] Controller layer - Request handling
- [ ] Service layer - Business logic
- [ ] Database integration
- [ ] Error handling
- **Check:** All async operations safe? Database transactions?

### 3.4 Database Models (Prisma)
- [ ] Schema file: `backend/prisma/schema.prisma`
- [ ] Models: User, Video, Comment, Like, Follow, Notification, Report, etc.
- [ ] Relations between models
- [ ] Field validations
- [ ] Indexes for performance
- **Check:** Schema matches Firestore structure where applicable

### 3.5 Environment & Configuration
- [ ] `.env.example` - Required environment variables
- [ ] Database connection string
- [ ] Firebase service account (if backend auth required)
- [ ] AWS/S3 credentials (if used)
- [ ] Third-party API keys (Google, Facebook, Twilio)
- **Check:** All documented? All required for production?

---

## 📱 Phase 4: Feature-by-Feature Analysis

### 4.1 Authentication Flow
**Email/Password:**
- [ ] Registration: App → Firebase → Firestore user creation
- [ ] Login: Firebase auth → Session management
- [ ] Logout: Session invalidation
- [ ] Password recovery: Email flow
- **Check:** All steps real or mock?

**Google OAuth:**
- [ ] Android app setup
- [ ] Backend setup (if needed)
- [ ] Token verification
- [ ] User profile sync
- **Check:** Single ID used everywhere? Consistent?

**Facebook OAuth:**
- [ ] SDK integration
- [ ] App ID/Secret setup
- [ ] User data mapping
- **Check:** Real or mock flow?

**Phone OTP:**
- [ ] Phone number input
- [ ] OTP generation (Twilio or mock?)
- [ ] SMS delivery (real or console log?)
- [ ] Verification logic
- [ ] Code expiry
- **Check:** CRITICAL - Is this real SMS or development echo?

### 4.2 Video Pipeline
**Upload:**
- [ ] Select video (gallery/camera)
- [ ] Compress/transcode (client or server?)
- [ ] Get presigned upload URL
- [ ] Upload to Firebase Storage
- [ ] Create Firestore document
- [ ] Update user video count
- **Check:** Each step connected? Real storage?

**Processing:**
- [ ] Thumbnail generation
- [ ] Transcoding (if server-side)
- [ ] URL generation for playback
- **Check:** Automated or manual?

**Playback:**
- [ ] Fetch video list (FYP algorithm?)
- [ ] Stream video (ExoPlayer)
- [ ] Track views
- [ ] Cache management
- **Check:** Real algorithm or random order?

**Deletion:**
- [ ] Delete from Storage
- [ ] Delete Firestore document
- [ ] Delete comments/likes
- [ ] Update counters
- **Check:** All cascades handled?

### 4.3 Interactions (Likes, Comments, Shares)
**Likes:**
- [ ] Real-time like toggle
- [ ] Counter update in Firestore
- [ ] User like history
- [ ] Notification to video creator
- **Check:** Race conditions? Double counting?

**Comments:**
- [ ] Comment creation
- [ ] Comment storage
- [ ] Comment retrieval (pagination?)
- [ ] Comment deletion (by author or admin?)
- [ ] Notification to video creator
- **Check:** All real Firestore? No mock data?

**Shares:**
- [ ] Share count tracking
- [ ] Deep link generation
- [ ] Share target options (other apps)
- **Check:** Implemented correctly?

### 4.4 Social Features
**Follow:**
- [ ] Follow/Unfollow action
- [ ] Firestore follow document
- [ ] Follower/Following counts
- [ ] Notification to followed user
- **Check:** Bidirectional consistency?

**Profile:**
- [ ] User info display
- [ ] Edit profile (name, bio, avatar)
- [ ] Video feed (user's own videos)
- [ ] Statistics (followers, likes, views)
- **Check:** Edit permissions (only own profile)?

**Search:**
- [ ] Search videos by caption/hashtag
- [ ] Search creators by name
- [ ] Search algorithm efficiency
- **Check:** Firestore query or algorithm?

**Notifications:**
- [ ] Notification generation events
- [ ] Firestore notification storage
- [ ] Notification delivery (real-time or polling?)
- [ ] Notification deletion/marking as read
- [ ] Push notifications (FCM or mock?)
- **Check:** Real notifications or dummy list?

### 4.5 Admin Features
**Reports:**
- [ ] Report submission (any user)
- [ ] Report review (admin only)
- [ ] Report resolution actions
- [ ] User violation tracking
- **Check:** Real moderation or mock?

**Moderation:**
- [ ] Video removal
- [ ] Account suspension
- [ ] Content filtering
- [ ] Appeals process
- **Check:** Implemented or placeholder?

**Admin Dashboard:**
- [ ] Access control (admin only)
- [ ] Report queue
- [ ] User management
- [ ] Statistics/analytics
- **Check:** How is admin role assigned?

### 4.6 Privacy & Data
**Account Deletion:**
- [ ] Privacy request submission
- [ ] Data deletion process
- [ ] Video content handling
- [ ] Comment history
- **Check:** Real deletion or just marking deleted?

**Blocking:**
- [ ] Block user functionality
- [ ] Blocked users list
- [ ] Content filtering from blocked users
- **Check:** Implemented?

**Privacy Settings:**
- [ ] Public/Private account
- [ ] Who can comment
- [ ] Who can message
- **Check:** Enforced in Firestore rules?

---

## 🧪 Phase 5: Data Consistency Analysis

### 5.1 Schema Alignment
- [ ] Android data models vs Firestore schema
- [ ] Backend Prisma models vs Firestore
- [ ] Field name consistency
- [ ] Data type consistency
- [ ] Required vs optional fields

### 5.2 Counter Consistency
- [ ] `videosCount` on user
- [ ] `likesCount` on video
- [ ] `commentsCount` on video
- [ ] `followersCount` on user
- [ ] `followingCount` on user
- [ ] Increment logic (Firestore transactions or manual?)
- [ ] Risk of inconsistency?

### 5.3 Permissions & Security
- [ ] User can't modify other user profiles
- [ ] User can't change their own role to admin
- [ ] User can't increase their follower count manually
- [ ] Firestore rules enforce all above
- [ ] Admin operations protected

---

## 🚀 Phase 6: Build & Release Analysis

### 6.1 Gradle Configuration
- [ ] Dependencies up to date?
- [ ] Kotlin version
- [ ] Android/Gradle plugin versions
- [ ] Build types (debug/release)
- [ ] Signing configuration
- [ ] ProGuard rules

### 6.2 Manifest
- [ ] Permissions minimal and necessary?
- [ ] Activities properly declared?
- [ ] Intent filters correct?
- [ ] Exported attributes set correctly?
- [ ] Network security config?

### 6.3 Resources
- [ ] Hardcoded test URLs?
- [ ] Hardcoded test user IDs?
- [ ] Hardcoded test Firebase project IDs?
- [ ] Debug strings left in?

---

## ⚠️ Conservative Rules

✋ **BEFORE DELETING ANY FILE:**
1. Search for ALL references in codebase
2. Check if used by Gradle
3. Check if used in resources (XML, strings)
4. Check if used in manifest
5. Check if imported anywhere
6. Ask: "Can this wait? Is it preventing build?"
7. If not preventing build → leave it for now

✋ **BEFORE REMOVING ANY FUNCTION:**
1. Check all call sites (Android Studio: Find Usages)
2. Check if part of public API
3. Check if used by UI screens
4. Check if used by tests
5. Check if part of dependency contract
6. Ask: "Does removing break functionality?"
7. If yes → fix instead of remove

✋ **BEFORE CHANGING FIREBASE:**
1. Backup current Firestore rules
2. Backup current Storage rules
3. Test changes on development project first
4. Verify backward compatibility
5. Check all client code impacts

---

## 📝 Status Tracking

- [x] Audit Plan Created
- [ ] Phase 1: Android Structure - PENDING
- [ ] Phase 2: Firebase Config - PENDING
- [ ] Phase 3: Backend - PENDING
- [ ] Phase 4: Features - PENDING
- [ ] Phase 5: Data Consistency - PENDING
- [ ] Phase 6: Build & Release - PENDING
- [ ] Findings Report - PENDING
- [ ] Prioritized Fixes - PENDING
- [ ] Fixes Implemented - PENDING
- [ ] Testing - PENDING
- [ ] Build Verification - PENDING

---

**Next:** Start detailed file-by-file analysis
