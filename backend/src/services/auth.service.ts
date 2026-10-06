import { createHash, createHmac, randomInt, timingSafeEqual } from 'crypto';
import bcrypt from 'bcryptjs';
import { prisma } from '../lib/prisma';
import { config } from '../config';

/**
 * Chort authentication core: Google/Facebook OAuth, phone+SMS OTP, account
 * linking, and phone-based recovery. Extends (never replaces) the existing
 * email/JWT/session system.
 *
 * Security rules enforced here:
 * - OTPs are stored HASHED (HMAC-SHA256 with server pepper + per-code salt).
 * - OTPs expire (TTL), attempts are capped, resends are cooldown-limited and
 *   hourly-capped per phone. Failures return 429/410 with clear messages.
 * - No secret is ever logged (only masked phone prefixes + provider statuses).
 * - DEV ECHO: when NO SMS provider is configured (setup mode), request-otp
 *   returns the code in `devOtp` so flows can be tested end to end. The
 *   moment Twilio is configured, echo disappears automatically.
 *   Set OTP_DEV_ECHO=false to disable echo entirely (kill switch).
 */

export const OTP_PURPOSES = ['register', 'recovery'] as const;
export type OtpPurpose = (typeof OTP_PURPOSES)[number];

// ---------------------------------------------------------------------------
// Pure helpers (unit-tested, no I/O)
// ---------------------------------------------------------------------------

/** Normalize to E.164. Throws 400 on anything else. */
export function normalizePhone(raw: string): string {
  const clean = String(raw || '').replace(/[\s\-().]/g, '');
  if (!/^\+[1-9]\d{7,14}$/.test(clean)) {
    const err = new Error('Invalid phone number. Use international format, e.g. +213661234567');
    (err as any).statusCode = 400;
    throw err;
  }
  return clean;
}

export function maskPhone(phone: string): string {
  if (phone.length <= 6) return '***';
  return `${phone.slice(0, 4)}***${phone.slice(-2)}`;
}

export function generateOtpCode(): string {
  return String(randomInt(100000, 999999));
}

export function makeSalt(): string {
  return createHash('sha256')
    .update(`${Date.now()}:${randomInt(0, 1 << 30)}`)
    .digest('hex')
    .slice(0, 32);
}

/** HMAC-SHA256(code) keyed by server pepper + per-code salt. */
export function hashOtpCode(code: string, salt: string): string {
  return createHmac('sha256', `${config.jwtSecret}:${salt}`).update(code).digest('hex');
}

export function otpCodeMatches(code: string, salt: string, expectedHash: string): boolean {
  const clean = String(code || '').trim();
  if (!/^\d{6}$/.test(clean)) return false;
  const actual = hashOtpCode(clean, salt);
  try {
    return timingSafeEqual(Buffer.from(actual, 'hex'), Buffer.from(expectedHash, 'hex'));
  } catch {
    return false;
  }
}

export function isSmsConfigured(): boolean {
  const t = config.twilio;
  return Boolean(t.accountSid && t.authToken && (t.fromNumber || t.messagingServiceSid));
}

/** Dev echo fires ONLY in non-production local/test mode when no SMS is configured. NEVER in production. */
export function devEchoAllowed(): boolean {
  if (config.nodeEnv === 'production') return false;
  if (config.otp.devEcho === 'never') return false;
  return !isSmsConfigured();
}

// ---------------------------------------------------------------------------
// SMS delivery (Twilio Programmable Messaging)
// ---------------------------------------------------------------------------

async function sendSmsViaTwilio(to: string, body: string): Promise<{ ok: boolean; sid?: string; error?: string }> {
  const t = config.twilio;
  const url = `https://api.twilio.com/2010-04-01/Accounts/${t.accountSid}/Messages.json`;
  const params = new URLSearchParams({ To: to, Body: body });
  if (t.messagingServiceSid) params.set('MessagingServiceSid', t.messagingServiceSid);
  else if (t.fromNumber) params.set('From', t.fromNumber);
  const basic = Buffer.from(`${t.accountSid}:${t.authToken}`).toString('base64');
  try {
    const resp = await fetch(url, {
      method: 'POST',
      headers: {
        Authorization: `Basic ${basic}`,
        'Content-Type': 'application/x-www-form-urlencoded',
        Accept: 'application/json',
      },
      body: params.toString(),
      signal: AbortSignal.timeout(15000),
    });
    if (!resp.ok) {
      const text = await resp.text().catch(() => '');
      console.warn(`Twilio SMS failed for ${maskPhone(to)}: HTTP ${resp.status} ${text.slice(0, 160)}`);
      return { ok: false, error: `SMS provider error (HTTP ${resp.status})` };
    }
    const data = (await resp.json().catch(() => ({}))) as { sid?: string };
    console.log(`Twilio SMS queued for ${maskPhone(to)}`);
    return { ok: true, sid: data.sid };
  } catch (err: any) {
    console.warn(`Twilio SMS exception for ${maskPhone(to)}: ${String(err.message || err).slice(0, 160)}`);
    return { ok: false, error: 'SMS provider unreachable, please try again' };
  }
}

// ---------------------------------------------------------------------------
// OTP lifecycle
// ---------------------------------------------------------------------------

export interface OtpRequestResult {
  sent: boolean;
  via: 'sms' | 'dev-echo' | 'failed';
  expiresInSeconds: number;
  resendCooldownSeconds: number;
  devOtp?: string;
  error?: string;
}

export class AuthService {
  /**
   * Issue (or re-issue) an OTP for a phone+purpose. Creates/refreshes the
   * hashed record, then delivers via SMS or setup-mode dev echo.
   */
  static async requestOtp(phoneRaw: string, purpose: OtpPurpose = 'register'): Promise<OtpRequestResult> {
    const phone = normalizePhone(phoneRaw);
    const now = Date.now();
    const ttlMs = config.otp.ttlSeconds * 1000;
    const cooldownMs = config.otp.cooldownSeconds * 1000;

    const existing = await prisma.phoneOtp.findUnique({ where: { phone_purpose: { phone, purpose } } }).catch(() => null);

    if (existing) {
      const lastSent = new Date(existing.lastSentAt).getTime();
      const waitLeft = Math.ceil((lastSent + cooldownMs - now) / 1000);
      if (waitLeft > 0) {
        const err = new Error(`Please wait ${waitLeft}s before requesting a new code`);
        (err as any).statusCode = 429;
        (err as any).retryAfter = waitLeft;
        throw err;
      }
      const withinHour = now - lastSent < 3600 * 1000;
      const count = withinHour ? existing.sendCount : 0;
      if (count >= config.otp.maxPerHour) {
        const err = new Error('Too many codes sent to this number. Try again in an hour');
        (err as any).statusCode = 429;
        throw err;
      }
    }

    const code = generateOtpCode();
    const salt = makeSalt();
    const record = await prisma.phoneOtp.upsert({
      where: { phone_purpose: { phone, purpose } },
      update: {
        codeHash: hashOtpCode(code, salt),
        salt,
        expiresAt: new Date(now + ttlMs),
        attempts: 0,
        sendCount: existing && now - new Date(existing.lastSentAt).getTime() < 3600 * 1000 ? existing.sendCount + 1 : 1,
        lastSentAt: new Date(now),
      },
      create: {
        phone,
        purpose,
        codeHash: hashOtpCode(code, salt),
        salt,
        expiresAt: new Date(now + ttlMs),
        attempts: 0,
        maxAttempts: config.otp.maxAttempts,
        sendCount: 1,
        lastSentAt: new Date(now),
      },
    });

    void record;
    const base = { expiresInSeconds: config.otp.ttlSeconds, resendCooldownSeconds: config.otp.cooldownSeconds };

    if (devEchoAllowed()) {
      console.warn(`OTP SETUP-MODE echo for ${maskPhone(phone)} (no SMS provider configured)`);
      return { sent: true, via: 'dev-echo', ...base, devOtp: code };
    }

    const sms = await sendSmsViaTwilio(phone, `thileli dz code: ${code}. It expires in ${Math.round(config.otp.ttlSeconds / 60)} minutes.`);
    if (!sms.ok) {
      return { sent: false, via: 'failed', ...base, error: sms.error || 'SMS delivery failed' };
    }
    return { sent: true, via: 'sms', ...base };
  }

  /**
   * Verify an OTP. Expiry is checked before the code (clear "expired" UX),
   * attempts are capped (lockout), and a consumed code is deleted so it can
   * never be replayed.
   */
  static async verifyOtp(phoneRaw: string, codeRaw: string, purpose: OtpPurpose = 'register'): Promise<{ phone: string }> {
    const phone = normalizePhone(phoneRaw);
    const record = await prisma.phoneOtp.findUnique({ where: { phone_purpose: { phone, purpose } } }).catch(() => null);
    if (!record) {
      const err = new Error('No active code for this number. Request a new one');
      (err as any).statusCode = 400;
      throw err;
    }
    if (new Date(record.expiresAt).getTime() <= Date.now()) {
      await prisma.phoneOtp.delete({ where: { id: record.id } }).catch(() => null);
      const err = new Error('This code has expired. Request a new one');
      (err as any).statusCode = 410;
      throw err;
    }
    if (record.attempts >= record.maxAttempts) {
      const err = new Error('Too many wrong attempts. Request a new code');
      (err as any).statusCode = 429;
      throw err;
    }
    if (!otpCodeMatches(codeRaw, record.salt, record.codeHash)) {
      const left = Math.max(0, record.maxAttempts - (record.attempts + 1));
      await prisma.phoneOtp.update({ where: { id: record.id }, data: { attempts: { increment: 1 } } }).catch(() => null);
      const err = new Error(`Wrong code. ${left} attempt${left === 1 ? '' : 's'} left`);
      (err as any).statusCode = 401;
      (err as any).attemptsLeft = left;
      throw err;
    }
    await prisma.phoneOtp.delete({ where: { id: record.id } }).catch(() => null);
    return { phone };
  }

  // -------------------------------------------------------------------------
  // OAuth verification (token -> verified identity, provider-confined)
  // -------------------------------------------------------------------------

  static async verifyGoogleIdToken(idToken: string): Promise<{ sub: string; email: string; name: string; avatar: string }> {
    if (!config.google.clientId) {
      const err = new Error('Google sign-in is not configured on the server (GOOGLE_CLIENT_ID)');
      (err as any).statusCode = 503;
      throw err;
    }
    let data: any;
    try {
      const resp = await fetch(`https://oauth2.googleapis.com/tokeninfo?id_token=${encodeURIComponent(idToken)}`, {
        signal: AbortSignal.timeout(10000),
      });
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      data = await resp.json();
    } catch {
      const err = new Error('Invalid Google credential. Please try again');
      (err as any).statusCode = 401;
      throw err;
    }
    const allowedAudiences = [
      config.google.clientId,
      '358490968062-n584hegcbbavgsbbq621191bfbvo78q1.apps.googleusercontent.com',
      '40606023128-ib7uarp2ei0opl4ekh0b2ghfj6oof1ca.apps.googleusercontent.com',
    ].filter(Boolean);
    const isAudValid = allowedAudiences.includes(data.aud) || String(data.aud || '').startsWith('358490968062');
    if (!data?.sub || !isAudValid) {
      const err = new Error('Google credential was not issued for thileli dz');
      (err as any).statusCode = 401;
      throw err;
    }
    if (data.email_verified !== 'true' && data.email_verified !== true) {
      const err = new Error('This Google email is not verified');
      (err as any).statusCode = 401;
      throw err;
    }
    return {
      sub: String(data.sub),
      email: String(data.email || '').toLowerCase(),
      name: String(data.name || ''),
      avatar: String(data.picture || ''),
    };
  }

  static async verifyFacebookToken(accessToken: string): Promise<{ sub: string; email: string; name: string; avatar: string }> {
    if (!config.facebook.appId || !config.facebook.appSecret) {
      const err = new Error('Facebook sign-in is not configured on the server (FACEBOOK_APP_ID/SECRET)');
      (err as any).statusCode = 503;
      throw err;
    }
    try {
      const appToken = `${config.facebook.appId}|${config.facebook.appSecret}`;
      const dbgResp = await fetch(
        `https://graph.facebook.com/debug_token?input_token=${encodeURIComponent(accessToken)}&access_token=${encodeURIComponent(appToken)}`,
        { signal: AbortSignal.timeout(10000) }
      );
      const dbg = (await dbgResp.json().catch(() => ({}))) as { data?: { is_valid?: boolean; app_id?: string; user_id?: string } };
      if (!dbgResp.ok || !dbg?.data?.is_valid || String(dbg.data.app_id) !== String(config.facebook.appId)) {
        throw new Error('debug failed');
      }
      const meResp = await fetch(
        `https://graph.facebook.com/${encodeURIComponent(String(dbg.data.user_id))}?fields=id,name,email,picture.type(large)&access_token=${encodeURIComponent(accessToken)}`,
        { signal: AbortSignal.timeout(10000) }
      );
      const me = (await meResp.json().catch(() => ({}))) as {
        id?: string;
        name?: string;
        email?: string;
        picture?: { data?: { url?: string } };
      };
      if (!meResp.ok || !me?.id) throw new Error('me failed');
      return {
        sub: String(me.id),
        email: String(me.email || '').toLowerCase(),
        name: String(me.name || ''),
        avatar: String(me.picture?.data?.url || ''),
      };
    } catch {
      const err = new Error('Invalid Facebook credential. Please try again');
      (err as any).statusCode = 401;
      throw err;
    }
  }

  // -------------------------------------------------------------------------
  // Account linking: one human, one User row, many providers.
  // -------------------------------------------------------------------------

  static async findOrCreateLinkedUser(input: {
    provider: 'google' | 'facebook' | 'phone';
    providerId: string;
    email?: string;
    name?: string;
    avatar?: string;
  }): Promise<{ user: any; isNew: boolean; linked: boolean }> {
    const { provider, providerId } = input;
    const email = (input.email || '').trim().toLowerCase();

    // 1. Known provider identity -> existing user, no duplicate, ever.
    const account = await prisma.account.findUnique({
      where: { provider_providerId: { provider, providerId } },
      include: { user: { include: { profile: true } } },
    });
    if (account?.user) {
      try {
        if (input.avatar && !account.user.profile?.avatarUrl) {
          await prisma.profile.updateMany({ where: { userId: account.user.id }, data: { avatarUrl: input.avatar } });
        }
      } catch {}
      return { user: account.user, isNew: false, linked: false };
    }

    // 2. Same verified email -> LINK to the existing user, no duplicate.
    if (email) {
      const byEmail = await prisma.user.findUnique({ where: { email }, include: { profile: true } });
      if (byEmail) {
        await prisma.account
          .create({ data: { userId: byEmail.id, provider, providerId, email } })
          .catch(() => null);
        if (provider === 'phone') {
          await prisma.user.update({ where: { id: byEmail.id }, data: { phone: providerId, phoneVerified: true } }).catch(() => null);
        }
        return { user: byEmail, isNew: false, linked: true };
      }
    }

    // 2b. Same verified phone -> LINK (phone is a strong identifier).
    if (provider === 'phone') {
      const byPhone = await prisma.user.findFirst({ where: { phone: providerId }, include: { profile: true } });
      if (byPhone) {
        await prisma.account.create({ data: { userId: byPhone.id, provider, providerId, email: email || null } }).catch(() => null);
        await prisma.user.update({ where: { id: byPhone.id }, data: { phoneVerified: true } }).catch(() => null);
        return { user: byPhone, isNew: false, linked: true };
      }
    }

    // 3. Brand-new human -> create user + profile + account link.
    const base =
      (email ? email.split('@')[0] : input.name || `${provider}_${providerId.slice(-6)}`)
        .toLowerCase()
        .replace(/[^a-z0-9_]/g, '_')
        .slice(0, 24) || 'user';
    let username = base.length >= 3 ? base : `user_${base}`;
    for (let i = 0; i < 5; i++) {
      const taken = await prisma.user.findUnique({ where: { username }, select: { id: true } }).catch(() => null);
      if (!taken) break;
      username = `${base}_${randomInt(100, 9999)}`;
    }
    const user = await prisma.user.create({
      data: {
        email: email || `${provider}_${providerId}@chort.app`,
        username,
        passwordHash: 'OAUTH_OR_SESSION',
        role: 'USER',
        phone: provider === 'phone' ? providerId : null,
        phoneVerified: provider === 'phone',
        primaryProvider: provider,
        profile: { create: { displayName: input.name || username, avatarUrl: input.avatar || null } },
        accounts: { create: { provider, providerId, email: email || null } },
      },
      include: { profile: true },
    });
    return { user, isNew: true, linked: false };
  }

  /**
   * Claim (create-or-link) the user behind a VERIFIED phone number.
   * Call only after verifyOtp succeeded.
   */
  static async claimPhoneUser(phone: string, name?: string): Promise<{ user: any; isNew: boolean; linked: boolean }> {
    return AuthService.findOrCreateLinkedUser({ provider: 'phone', providerId: phone, name: name || undefined });
  }

  /**
   * Recovery step 1: send a recovery OTP to a VERIFIED phone. The response
   * is generic so unknown numbers cannot be enumerated; a challenge is only
   * created when the phone belongs to an account. (In setup mode the echo
   * itself implies an account exists on non-production deploys; the echo
   * dies automatically once Twilio is configured.)
   */
  static async requestRecovery(phoneRaw: string): Promise<OtpRequestResult> {
    const phone = normalizePhone(phoneRaw);
    const owner = await prisma.user.findFirst({ where: { phone, phoneVerified: true }, select: { id: true } }).catch(() => null);
    if (!owner) {
      // Generic shape, identical to a real send: unknown numbers are
      // indistinguishable from the outside (no enumeration oracle).
      return { sent: true, via: 'sms', expiresInSeconds: config.otp.ttlSeconds, resendCooldownSeconds: config.otp.cooldownSeconds };
    }
    return AuthService.requestOtp(phone, 'recovery');
  }

  /**
   * Recovery step 2: consume a valid recovery OTP, optionally set a new
   * password, and hand back the recovered user. Callers issue the session.
   */
  static async confirmRecovery(phoneRaw: string, codeRaw: string, newPassword?: string): Promise<{ user: any }> {
    const { phone } = await AuthService.verifyOtp(phoneRaw, codeRaw, 'recovery');
    const user = await prisma.user.findFirst({ where: { phone }, include: { profile: true } });
    if (!user) {
      const err = new Error('No thileli dz account is linked to this number');
      (err as any).statusCode = 404;
      throw err;
    }
    const patch: Record<string, unknown> = { phoneVerified: true };
    if (newPassword !== undefined) {
      const clean = String(newPassword || '');
      if (clean.length < 6 || clean.length > 100) {
        const err = new Error('New password must be 6-100 characters');
        (err as any).statusCode = 400;
        throw err;
      }
      patch.passwordHash = await bcrypt.hash(clean, 10);
    }
    await prisma.user.update({ where: { id: user.id }, data: patch }).catch(() => null);
    return { user };
  }

  static async linkedProviders(userId: string): Promise<string[]> {
    try {
      const rows = await prisma.account.findMany({ where: { userId }, select: { provider: true } });
      return Array.from(new Set(rows.map((r: any) => String(r.provider))));
    } catch {
      return [];
    }
  }
}
