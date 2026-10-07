# RIVO CHORT — Cloudflare R2 + Stream

This branch wires the backend to Cloudflare R2 through the existing S3-compatible storage layer and optionally imports verified R2 uploads into Cloudflare Stream for HLS/DASH playback.

## 1. R2

Create an R2 bucket named:

rivo-chort-media

Create an R2 API token with Object Read & Write access to this bucket.

Set these Vercel Environment Variables:

- S3_ENDPOINT=https://<ACCOUNT_ID>.r2.cloudflarestorage.com
- S3_BUCKET=rivo-chort-media
- S3_ACCESS_KEY_ID=<R2_ACCESS_KEY_ID>
- S3_SECRET_ACCESS_KEY=<R2_SECRET_ACCESS_KEY>
- S3_REGION=auto
- S3_FORCE_PATH_STYLE=true

Do not commit the credentials.

## 2. Cloudflare Stream

Create a Cloudflare API Token with Stream Write permission.

Set:

- CLOUDFLARE_STREAM_ACCOUNT_ID=<ACCOUNT_ID>
- CLOUDFLARE_STREAM_API_TOKEN=<STREAM_API_TOKEN>

The backend uploads the original file to R2 first, verifies the object, then asks Stream to import the verified object through a short-lived signed GET URL. The returned Stream UID and HLS playback URL are stored with the video.

## 3. Runtime flow

Android -> Vercel API -> signed R2 PUT -> R2
                                      |
                                      +-> Cloudflare Stream import
                                             |
                                             +-> HLS playback

If Stream is temporarily unavailable, R2 remains the real playback fallback; no mock URL is generated.

## 4. Required deployment step

Add the variables above to the Vercel Production environment, redeploy, then test:

GET /api/v1/system/storage-check

and publish one real MP4 from the Android app.

## 5. Security

Never put the Cloudflare Stream API token or R2 secret key in the Android APK, GitHub source, or .env files committed to GitHub. They belong only in the server/Vercel environment.
