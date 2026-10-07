# ZEVORA Pre-release security/feed audit

## Completed in this release candidate

- **Canonical Admin identity:** Firebase Authentication UID is now linked to the PostgreSQL `User.firebaseUid` field. The backend has a `/api/v1/auth/firebase` exchange that cryptographically verifies Firebase ID tokens and then issues the normal backend session JWT.
- **Owner protection:** the production owner is identified by `FIREBASE_OWNER_ADMIN_UID` (defaulting to the existing owner UID). Firebase custom claim `admin=true` is also accepted. The Firebase exchange does not grant admin from an email address.
- **Android Admin session:** Firebase email/Google sign-in now attempts the Firebase → backend exchange, so the Android Admin Dashboard and backend `/admin/*` endpoints share the same authenticated session.
- **Firestore privacy:** ordinary authenticated users can no longer read the entire `users` collection. Only the document owner or an Admin can read a user document. Public profile reads should use the backend profile endpoint.
- **Feed policy:** normal Feed requests default to database-owned user videos. Licensed external videos are opt-in with `includeExternal=true`; they are never a silent fallback when the DB is empty/disconnected.
- **Synthetic avatars removed:** Unsplash/DiceBear avatar fallbacks were removed from the account/profile paths. Provider videos only use a provider-supplied real avatar when one exists; otherwise the avatar is empty.
- **Stale deployment URL removed:** Android production API base is `https://chort-nine.vercel.app/`, not the previous `chort-nmk4` deployment.
- **Test leftovers:** the previous `zevora-nine` test URL remains only as the intended production test endpoint; no `chort-nmk4` reference remains in source/tests.

## Required production configuration

- `DATABASE_URL`
- `JWT_SECRET`
- `FIREBASE_PROJECT_ID=shortvideoapp-6b870`
- `FIREBASE_OWNER_ADMIN_UID=E9RrifJxb2QboG6kdQjqUfomEkQ2`
- Existing storage/CDN variables required by the backend
- AI, OAuth, SMS and external-video provider variables only for the features that are enabled

## Verification limitation

The candidate could not run a complete Gradle Android build because the environment cannot download the configured Gradle distribution. Backend dependency installation was also unavailable because required npm packages were not locally cached. Source-level checks were completed, but a successful CI build and production smoke test are still required before calling the release 100% verified.

