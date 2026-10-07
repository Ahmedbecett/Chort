===== ZEVORA_BUNDLE_PATH: AUTH_SETUP.md =====
# ZEVORA Auth Setup (Google / Facebook / Phone SMS)

Two commits implement auth end to end (`6769650` backend, `5b226c9` app 2.4.0).
Nothing here contains secrets — only variable **names** and where they go.

## 1. Vercel backend variables (required for real OAuth + SMS)

Vercel Dashboard → your `ZEVORA` project → **Settings → Environment Variables**
→ add each for **Production** (and Preview if you test previews), then **Redeploy**.

| Variable | Where the value comes from |
|---|---|
| `GOOGLE_CLIENT_ID` | Web client ID below (must match the app) |
| `FACEBOOK_APP_ID` | https://developers.facebook.com/apps → your app → Settings → Basic |
| `FACEBOOK_APP_SECRET` | Same page (keep secret, Vercel only) |
| `TWILIO_ACCOUNT_SID` | https://console.twilio.com → Account Info |
| `TWILIO_AUTH_TOKEN` | Same page (keep secret, Vercel only) |
| `TWILIO_FROM_NUMBER` | Your Twilio number in E.164, e.g. `+12025550142` |
| `TWILIO_MESSAGING_SERVICE_SID` | Optional, use instead of `TWILIO_FROM_NUMBER` |

Optional tuning (defaults work): `OTP_TTL_SECONDS=600`,
`OTP_RESEND_COOLDOWN_SECONDS=60`, `OTP_MAX_PER_HOUR=5`, `OTP_MAX_ATTEMPTS=5`.
Kill switch: `OTP_DEV_ECHO=false` (never echo codes, even unconfigured).

### Google client ID (must be identical on both sides)

The Android app signs in with the web client ID in
`app/src/main/res/values/strings.xml` (`default_web_client_id`):

```text
40606023128-ib7uarp2ei0opl4ekh0b2ghfj6oof1ca.apps.googleusercontent.com
```

Set `GOOGLE_CLIENT_ID` in Vercel to exactly this value (it is a public
identifier, safe to copy). Google Cloud Console → Credentials must also list
your Android app (package `com.aistudio.zevora.social`, SHA-1 of your
release key) or Google will refuse the request.

## 2. Android app placeholders (no secrets)

In `app/src/main/res/values/strings.xml`, replace:

- `facebook_app_id` → your numeric Facebook App ID
- `facebook_client_token` → Client Token (Settings → Advanced)
- `fb_login_protocol_scheme` → `fb` + your App ID (e.g. `fb123456789`)

Facebook app setup: Developers → your app → **Facebook Login → Settings** →
add Android platform, package `com.aistudio.zevora.social`, and your key
hash (`keytool -exportcert -alias zevora -keystore zevora-release.jks | openssl
sha1 -binary | openssl base64` for the committed dev key; use your Play key
for production).

Until these are set, Facebook sign-in shows a clear "not configured" message
instead of crashing.

## 3. What works when

| State | Behaviour |
|---|---|
| Nothing configured | Email auth works. Google/Facebook show "not configured". Mobile phone registration uses Firebase Phone Authentication; there is no development OTP fallback. Legacy recovery OTP requires a real Twilio configuration. |
| `GOOGLE_CLIENT_ID` set | Real Google sign-in (account picker → backend verifies with Google). |
| `FACEBOOK_*` + app strings set | Real Facebook Login (backend verifies via `debug_token`). |
| `TWILIO_*` set | Real SMS delivery for legacy recovery endpoints. Mobile phone registration uses Firebase Phone Authentication directly. |

## 4. Verify after deploy

- `GET /api/v1/health` shows `googleOAuthConfigured`, `facebookOAuthConfigured`,
  `smsConfigured`, and `otpDevEcho` (always false) flags.
- Admin app → **Logins** tab shows real sign-in records; **Live Reports**
  tab drives the real moderation queue.

===== END ZEVORA_BUNDLE_FILE =====
