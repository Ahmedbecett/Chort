# ZEVORA Auth Setup (Google / Facebook / Phone SMS)

Nothing here contains secrets — only variable **names** and where they go.

## 1. Vercel backend variables (required for real OAuth + SMS)

Vercel Dashboard → your backend project → **Settings → Environment Variables**
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

> ZEVORA production policy: OTP development echo is permanently disabled in
> code (`devEchoAllowed()` always returns `false`). There is no setup-mode
> code path anymore — real SMS via Twilio is the only delivery channel, and
> the Android app never displays verification codes.

### Google client ID (must be identical on both sides)

The Android app signs in with the web client ID in
`app/src/main/res/values/strings.xml` (`default_web_client_id`):

```text
358490968062-n584hegcbbavgsbbq621191bfbvo78q1.apps.googleusercontent.com
```

Set `GOOGLE_CLIENT_ID` in Vercel to exactly this value (it is a public
identifier, safe to copy). Google Cloud Console → Credentials must also list
your Android app (package `com.aistudio.tokpulse.social`, SHA-1 of your
release key) or Google will refuse the request.

## 2. Android app placeholders (no secrets)

In `app/src/main/res/values/strings.xml`, replace:

- `facebook_app_id` → your numeric Facebook App ID
- `facebook_client_token` → Client Token (Settings → Advanced)
- `fb_login_protocol_scheme` → `fb` + your App ID (e.g. `fb123456789`)

Facebook app setup: Developers → your app → **Facebook Login → Settings** →
add Android platform, package `com.aistudio.tokpulse.social`, and your key
hash derived from the production keystore:

```bash
keytool -exportcert -alias <KEY_ALIAS> -keystore <production-keystore.jks> \
  | openssl sha1 -binary | openssl base64
```

(Use the same production key whose base64 is stored in the
`ANDROID_KEYSTORE_BASE64` GitHub Secret. No keystore is committed to this
repository.)

Until these are set, Facebook sign-in shows a clear "not configured" message
instead of crashing.

## 3. What works when

| State | Behaviour |
|---|---|
| Nothing configured | Email auth works. Google/Facebook show "not configured". Phone OTP requires Twilio (no code is ever echoed). |
| `GOOGLE_CLIENT_ID` set | Real Google sign-in (account picker → backend verifies with Google). |
| `FACEBOOK_*` + app strings set | Real Facebook Login (backend verifies via `debug_token`). |
| `TWILIO_*` set | Real SMS delivery worldwide. |

## 4. Verify after deploy

- `GET /api/v1/health` shows `googleOAuthConfigured`, `facebookOAuthConfigured`
  and `smsConfigured` flags.
- Admin app → **Logins** tab shows real sign-in records; **Live Reports**
  tab drives the real moderation queue.
