import { prisma } from '../lib/prisma';
import { redis } from '../config';

/**
 * Feed history & identity helpers.
 *
 * - Logged-in users get DURABLE history in PostgreSQL:
 *     ZEVORA videos  -> "View" rows (videoId FK, userId nullable w/ FK guard)
 *     licensed seed -> "ExternalSeen" rows (no FK by design: external ids are
 *                      not rows in "Video", and ids must never break writes)
 * - Guests get a FAST seen-list in cache keyed by device/IP identity.
 * - The `seen` request param lets any client exclude ids explicitly.
 *
 * Everything here is additive and failure-tolerant: history must never break
 * feed serving or view counting.
 */

const GUEST_SEEN_TTL_SECONDS = 7 * 24 * 3600;
const SEEN_CAP = 500;

export function isExternalId(videoId: string): boolean {
  return videoId.startsWith('pex_') || videoId.startsWith('cov_') || videoId.startsWith('pix_');
}

export function providerOfExternalId(videoId: string): string {
  if (videoId.startsWith('pex_')) return 'pexels';
  if (videoId.startsWith('cov_')) return 'coverr';
  if (videoId.startsWith('pix_')) return 'pixabay';
  return '';
}

export function isGuestUserId(userId?: string | null): boolean {
  if (!userId) return true;
  const u = userId.trim();
  return (
    u === '' ||
    u === 'guest' ||
    u === 'user_guest' ||
    u.startsWith('user_guest') ||
    u.startsWith('guest_') ||
    u.startsWith('anon')
  );
}

export function fnv1aHex(input: string): string {
  let hash = 0x811c9dc5;
  for (let i = 0; i < input.length; i++) {
    hash ^= input.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193);
  }
  return (hash >>> 0).toString(16);
}

/**
 * Stable per-viewer identity. Logged users win; otherwise device id; else a
 * hash of the client IP. Different viewers MUST get different keys so their
 * feeds can differ.
 */
export function userKeyFor(input: { userId?: string; deviceId?: string; ip?: string }): string {
  const userId = input.userId?.trim() || '';
  if (userId && !isGuestUserId(userId)) return `u:${userId}`;
  const deviceId = input.deviceId?.trim() || '';
  if (deviceId) return `d:${deviceId.slice(0, 64)}`;
  const ip = input.ip?.trim() || '';
  if (ip) return `ip:${fnv1aHex(ip)}`;
  return 'ip:unknown';
}

export function clientIpFrom(req: { headers: Record<string, unknown>; ip?: string }): string {
  const fwd = req.headers?.['x-forwarded-for'];
  const first = (Array.isArray(fwd) ? fwd[0] : String(fwd || '')).split(',')[0]?.trim();
  if (first) return first;
  const real = req.headers?.['x-real-ip'];
  if (real && String(real).trim()) return String(real).trim();
  return req.ip || '';
}

export function parseSeenParam(seen?: string | string[]): string[] {
  if (!seen) return [];
  const raw = Array.isArray(seen) ? seen.join(',') : String(seen);
  return raw
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
    .slice(0, SEEN_CAP);
}

function guestSeenKey(userKey: string): string {
  return `feed:seen:${userKey}`;
}

export async function readGuestSeen(userKey: string): Promise<string[]> {
  try {
    const raw = await redis.get(guestSeenKey(userKey));
    if (!raw) return [];
    const arr = JSON.parse(raw) as unknown;
    if (!Array.isArray(arr)) return [];
    return arr.filter((x): x is string => typeof x === 'string').slice(0, SEEN_CAP);
  } catch {
    return [];
  }
}

export async function appendGuestSeen(userKey: string, videoId: string): Promise<void> {
  if (!videoId) return;
  try {
    const current = await readGuestSeen(userKey);
    if (current.includes(videoId)) return;
    current.unshift(videoId);
    await redis.set(guestSeenKey(userKey), JSON.stringify(current.slice(0, SEEN_CAP)), 'EX', GUEST_SEEN_TTL_SECONDS);
  } catch {
    // history is best-effort
  }
}

export async function readUserSeenDb(userId: string): Promise<{ db: string[]; external: string[] }> {
  try {
    const [views, ext] = await Promise.all([
      prisma.view.findMany({
        where: { userId },
        orderBy: { createdAt: 'desc' },
        take: SEEN_CAP,
        select: { videoId: true },
      }),
      prisma.externalSeen.findMany({
        where: { userId },
        orderBy: { createdAt: 'desc' },
        take: SEEN_CAP,
        select: { externalId: true },
      }),
    ]);
    return {
      db: views.map((v) => v.videoId),
      external: ext.map((e) => e.externalId),
    };
  } catch {
    return { db: [], external: [] };
  }
}

export async function recordWatchHistory(input: {
  videoId: string;
  userId?: string;
  userKey: string;
  watchSec?: number;
  completed?: boolean;
  ipAddress?: string;
}): Promise<void> {
  const { videoId, userKey } = input;
  if (!videoId) return;

  // Fast path for everyone (drives exclusion immediately, survives DB hiccups).
  await appendGuestSeen(userKey, videoId);

  const userId = input.userId?.trim() || '';
  if (isGuestUserId(userId)) return;

  try {
    if (isExternalId(videoId)) {
      // No FK on ExternalSeen by design: any authenticated id string is safe.
      await prisma.externalSeen.upsert({
        where: { userId_externalId: { userId, externalId: videoId } },
        update: {},
        create: { userId, externalId: videoId, provider: providerOfExternalId(videoId) },
      });
      return;
    }

    // "View".userId has a FK to "User": only link when the row really exists,
    // otherwise store an anonymous view (userId NULL) instead of failing.
    let ownerId: string | null = null;
    try {
      const u = await prisma.user.findUnique({ where: { id: userId }, select: { id: true } });
      if (u) ownerId = u.id;
    } catch {
      ownerId = null;
    }

    // One history row per (video, viewer) per 24h keeps the table bounded
    // while remaining a true "already watched" record.
    const since = new Date(Date.now() - 24 * 3600 * 1000);
    let recent: { id: string } | null = null;
    try {
      recent = await prisma.view.findFirst({
        where: { videoId, userId: ownerId, createdAt: { gte: since } },
        select: { id: true },
      });
    } catch {
      recent = null;
    }
    if (!recent) {
      try {
        await prisma.view.create({
          data: {
            videoId,
            userId: ownerId,
            watchSec: input.watchSec ?? 0,
            completed: input.completed ?? false,
            ipAddress: input.ipAddress || null,
          },
        });
      } catch {
        // FK to a missing/deleted video, or a race: history must never throw.
      }
    }
  } catch {
    // history is best-effort
  }
}

export async function readFollowingIds(userId?: string): Promise<string[]> {
  const uid = userId?.trim() || '';
  if (isGuestUserId(uid)) return [];
  const key = `feed:following:${uid}`;
  try {
    const cached = await redis.get(key);
    if (cached) {
      const arr = JSON.parse(cached) as unknown;
      if (Array.isArray(arr)) return arr.filter((x): x is string => typeof x === 'string');
    }
  } catch {
    // fall through to DB
  }
  try {
    const rows = await prisma.follow.findMany({
      where: { followerId: uid },
      select: { followingId: true },
      take: 2000,
    });
    const ids = rows.map((r) => r.followingId);
    try {
      await redis.set(key, JSON.stringify(ids), 'EX', 60);
    } catch {
      // ignore
    }
    return ids;
  } catch {
    return [];
  }
}
