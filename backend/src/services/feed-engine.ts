import { prisma } from '../lib/prisma';
import { redis, config } from '../config';
import { VideoService } from './video.service';
import { SocialService } from './social.service';
import { FeedMode } from '../lib/validate';
import { ExternalVideoService } from './external-video.service';
import { checkMediaBatch } from './media-verify';
import { maxPageForProvider, topicsForProvider, RotationProvider } from './provider-rotation';
import {
  isExternalId,
  isGuestUserId,
  parseSeenParam,
  readFollowingIds,
  readGuestSeen,
  readUserSeenDb,
  userKeyFor,
} from './feed-history';

/**
 * Chort Feed Engine — a real social-video feed, not a static list.
 *
 * Guarantees:
 *  1. Different viewers get feeds that CAN differ (per-viewer daily seed).
 *  2. Real cursor pagination across BOTH chort videos and licensed seed.
 *  3. No repeats: page-level dedupe + watch-history exclusion (durable for
 *     logged users, fast seen-list for guests, `seen` param for everyone).
 *  4. User content is primary: licensed Pexels/Coverr clips are seed content
 *     that backfills thin pages and shrinks to a discovery mix as users post.
 *     Both licensed sources cooperate: the selected provider leads and the
 *     other configured one backfills when slices come back silent/empty.
 *  5. Licensed clips must be reachable + valid video + https with an https
 *     thumbnail, and carry verified audio unless FEED_REQUIRE_EXTERNAL_AUDIO
 *     is explicitly disabled. User uploads are never audio-gated (silence is
 *     legitimate) and never deleted by the feed.
 *  6. Ranking starts at recency+diversity and already consumes engagement
 *     (views/likes/comments/shares) plus follow-boost when that data exists.
 */

// ---------------------------------------------------------------------------
// Pure helpers (deterministic, unit-tested, no I/O)
// ---------------------------------------------------------------------------

export function fnv1a32(input: string): number {
  let hash = 0x811c9dc5;
  for (let i = 0; i < input.length; i++) {
    hash ^= input.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193);
  }
  return hash >>> 0;
}

export function dayString(whenMs = Date.now()): string {
  return new Date(whenMs).toISOString().slice(0, 10);
}

/** Daily seed: stable within a day (pagination consistency), fresh each day. */
export function seedFor(userKey: string, day: string): number {
  return fnv1a32(`${userKey}|${day}`);
}

export function mulberry32(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export function seededShuffle<T>(items: T[], seed: number): T[] {
  const arr = [...items];
  const rand = mulberry32(seed);
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(rand() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}

/**
 * Trending velocity: engagement per age. Rewards clips that earn views,
 * likes, comments and shares FAST, not just old viral totals. Pure.
 */
export function trendingScore(input: {
  createdAtMs: number;
  views: number;
  likes: number;
  comments: number;
  shares: number;
  nowMs?: number;
}): number {
  const now = input.nowMs ?? Date.now();
  const ageHours = Math.max(0, (now - input.createdAtMs) / 3600000);
  const engagement = input.views + 3 * input.likes + 2 * input.comments + 4 * input.shares;
  return engagement / Math.pow(ageHours + 2, 1.2);
}

/** Single place to tune ranking. Phase 1: recency + diversity first. */
export const RANK_WEIGHTS = {
  recency: 0.55,
  engagement: 0.3,
  follow: 1.0,
  jitter: 0.15,
};

const RECENCY_HALF_LIFE_HOURS = 72;

export function scoreVideo(input: {
  createdAtMs: number;
  views: number;
  likes: number;
  comments: number;
  shares: number;
  isFollowed: boolean;
  jitter01: number;
  nowMs?: number;
}): number {
  const now = input.nowMs ?? Date.now();
  const ageHours = Math.max(0, (now - input.createdAtMs) / 3600000);
  const recency = Math.exp(-ageHours / RECENCY_HALF_LIFE_HOURS);
  const engRaw = input.views + 3 * input.likes + 2 * input.comments + 4 * input.shares;
  const engagement = Math.min(1, Math.log10(1 + Math.max(0, engRaw)) / 4);
  return (
    RANK_WEIGHTS.recency * recency +
    RANK_WEIGHTS.engagement * engagement +
    (input.isFollowed ? RANK_WEIGHTS.follow : 0) +
    RANK_WEIGHTS.jitter * input.jitter01
  );
}

/**
 * Greedy creator-diversity ordering over pre-scored items: walk the scored
 * list and always pick the best item whose creator is outside the recent
 * window when possible. Deterministic.
 */
export function orderWithDiversity<T>(items: T[], creatorOf: (item: T) => string, window = 3): T[] {
  const remaining = [...items];
  const out: T[] = [];
  const recent: string[] = [];
  while (remaining.length > 0) {
    let pick = 0;
    for (let i = 0; i < remaining.length; i++) {
      if (!recent.includes(creatorOf(remaining[i]))) {
        pick = i;
        break;
      }
    }
    const [item] = remaining.splice(pick, 1);
    out.push(item);
    recent.push(creatorOf(item));
    if (recent.length > window) recent.shift();
  }
  return out;
}

/**
 * Per-viewer licensed-seed slice: deterministic rotation over (topic, page)
 * so different viewers walk different sequences and one viewer advances
 * across pages instead of looping the same global 20-30 clips.
 */
export function externalSliceFor(provider: string, seed: number, extPage: number): { topic: string; page: number } {
  const p = (provider === 'coverr' ? 'coverr' : provider === 'pixabay' ? 'pixabay' : 'pexels') as RotationProvider;
  const topics = topicsForProvider(p);
  const maxPage = Math.max(1, maxPageForProvider(p));
  const topic = topics[(seed + extPage) % topics.length];
  const page = 1 + ((Math.floor(seed / topics.length) + extPage) % maxPage);
  return { topic, page };
}

/** Evenly spread `count` discovery slots across `limit` positions + offset. */
export function spreadPositions(limit: number, count: number, seed: number): number[] {
  if (count <= 0 || limit <= 0) return [];
  if (count >= limit) return Array.from({ length: limit }, (_, i) => i);
  const offset = seed % limit;
  const pos = new Set<number>();
  for (let k = 0; k < count; k++) {
    pos.add(Math.floor((offset + (k * limit) / count) % limit));
  }
  let i = 0;
  while (pos.size < count) {
    pos.add((offset + i) % limit);
    i++;
  }
  return [...pos].sort((a, b) => a - b);
}

// ---------------------------------------------------------------------------
// Opaque cursor: position across both sources (legacy plain-id supported)
// ---------------------------------------------------------------------------

export interface FeedCursorState {
  v: 1;
  day: string;
  seed: number;
  dbAfter: { t: number; id: string } | null;
  carry: string[];
  ep: number;
  served: number;
  /** Recently served licensed-seed ids: providers overlap across slices, so
   *  the cursor itself carries exclusion to guarantee no repeats. */
  sx: string[];
  /** Recently served chort ids: the dedup mechanism for score-ordered
   *  (trending) pages, safety net for keyset modes. */
  sd: string[];
}

const CURSOR_PREFIX = 'fe1.';
const CURSOR_CARRY_CAP = 40;
const CURSOR_SX_CAP = 100;
const CURSOR_SD_CAP = 100;

export function encodeCursor(state: FeedCursorState): string {
  return CURSOR_PREFIX + Buffer.from(JSON.stringify(state), 'utf8').toString('base64url');
}

export function decodeCursor(raw?: string): FeedCursorState | { legacyDbId: string } | null {
  if (!raw || !raw.trim()) return null;
  const clean = raw.trim();
  if (!clean.startsWith(CURSOR_PREFIX)) return { legacyDbId: clean.slice(0, 128) };
  try {
    const parsed = JSON.parse(Buffer.from(clean.slice(CURSOR_PREFIX.length), 'base64url').toString('utf8')) as Record<string, unknown>;
    if (!parsed || parsed.v !== 1) return null;
    const dbAfter = parsed.dbAfter as { t?: unknown; id?: unknown } | null | undefined;
    return {
      v: 1,
      day: String(parsed.day || ''),
      seed: (Number(parsed.seed) >>> 0) || 0,
      dbAfter:
        dbAfter && typeof dbAfter.t === 'number'
          ? { t: dbAfter.t, id: String(dbAfter.id || '') }
          : null,
      carry: Array.isArray(parsed.carry)
        ? (parsed.carry as unknown[]).filter((x): x is string => typeof x === 'string').slice(0, CURSOR_CARRY_CAP)
        : [],
      ep: Math.max(0, Math.min(100000, Number(parsed.ep) || 0)),
      served: Math.max(0, Number(parsed.served) || 0),
      sx: Array.isArray(parsed.sx)
        ? (parsed.sx as unknown[]).filter((x): x is string => typeof x === 'string').slice(0, CURSOR_SX_CAP)
        : [],
      sd: Array.isArray(parsed.sd)
        ? (parsed.sd as unknown[]).filter((x): x is string => typeof x === 'string').slice(0, CURSOR_SD_CAP)
        : [],
    };
  } catch {
    return null;
  }
}

// ---------------------------------------------------------------------------
// Orchestrator
// ---------------------------------------------------------------------------

export interface FeedPageInput {
  userId?: string;
  deviceId?: string;
  ip?: string;
  cursor?: string;
  limit?: number;
  category?: string;
  includeExternal?: boolean;
  seen?: string | string[];
  mode?: FeedMode;
}

export interface FeedPageMeta {
  mode: FeedMode;
  provider: string;
  seedProviders: string[];
  dbCount: number;
  extCount: number;
  day: string;
  source: string;
  databaseConnected: boolean;
}

const SEEN_CAP = 500;

function absolutizeMediaUrl(raw?: string | null): string {
  if (!raw) return '';
  if (/^https?:\/\//i.test(raw)) return raw;
  if (raw.startsWith('/')) return `${config.cdn.baseUrl}${raw}`;
  return '';
}

export class FeedEngine {
  static async getPage(input: FeedPageInput): Promise<{
    videos: Record<string, unknown>[];
    nextCursor: string | null;
    hasMore: boolean;
    meta: FeedPageMeta;
    message?: string;
  }> {
    const limit = Math.min(30, Math.max(1, input.limit || 20));
    const includeExternal = input.includeExternal !== false;
    const mode: FeedMode = input.mode || 'recommended';
    const userId = input.userId?.trim() || undefined;
    const userKey = userKeyFor({ userId, deviceId: input.deviceId, ip: input.ip });
    const today = dayString();
    const seed = seedFor(userKey, today);
    const provider = ExternalVideoService.selectedProvider();

    // ---- cursor → position (fresh day restarts position, keeps history) ---
    const decoded = decodeCursor(input.cursor);
    let dbAfter: { t: number; id: string } | null = null;
    let carry: string[] = [];
    let ep = 0;
    let servedSoFar = 0;
    let cursorSx: string[] = [];
    let cursorSd: string[] = [];
    if (decoded) {
      if ('legacyDbId' in decoded) {
        try {
          const row = await prisma.video.findUnique({
            where: { id: decoded.legacyDbId },
            select: { createdAt: true },
          });
          if (row) dbAfter = { t: row.createdAt.getTime(), id: decoded.legacyDbId };
        } catch {
          dbAfter = null;
        }
      } else if (decoded.day === today) {
        dbAfter = decoded.dbAfter;
        carry = decoded.carry;
        ep = decoded.ep;
        servedSoFar = decoded.served;
        cursorSx = decoded.sx || [];
        cursorSd = decoded.sd || [];
      }
    }

    // ---- watch history → exclusion sets ----------------------------------
    const paramSeen = parseSeenParam(input.seen);
    const guestSeen = await readGuestSeen(userKey);
    const dbSeen = new Set<string>();
    const extSeen = new Set<string>();
    for (const id of [...cursorSd, ...paramSeen, ...guestSeen]) {
      if (!isExternalId(id) && dbSeen.size < SEEN_CAP) dbSeen.add(id);
    }
    for (const id of [...cursorSx, ...paramSeen, ...guestSeen]) {
      if (isExternalId(id)) {
        if (extSeen.size < SEEN_CAP) extSeen.add(id);
      } else if (dbSeen.size < SEEN_CAP) {
        dbSeen.add(id);
      }
    }
    if (!isGuestUserId(userId)) {
      const durable = await readUserSeenDb(userId as string);
      for (const id of durable.db) if (dbSeen.size < SEEN_CAP) dbSeen.add(id);
      for (const id of durable.external) if (extSeen.size < SEEN_CAP) extSeen.add(id);
    }
    const followingIds = new Set(await readFollowingIds(userId));
    const savedCreatorIds = new Set(
      mode === 'recommended' || mode === 'trending' ? await SocialService.getSavedCreatorIds(userId) : []
    );

    if (mode === 'following') {
      if (isGuestUserId(userId)) {
        return {
          videos: [],
          nextCursor: null,
          hasMore: false,
          meta: { mode, provider, seedProviders: [], dbCount: 0, extCount: 0, day: today, source: 'Chort', databaseConnected: true },
          message: 'Sign in and follow creators to fill your Following feed.',
        };
      }
      if (followingIds.size === 0) {
        return {
          videos: [],
          nextCursor: null,
          hasMore: false,
          meta: { mode, provider, seedProviders: [], dbCount: 0, extCount: 0, day: today, source: 'Chort', databaseConnected: true },
          message: 'You are not following anyone yet.',
        };
      }
    }

    if (mode === 'trending') {
      return this.getTrendingPage({ limit, userId, dbSeen, cursorSd, servedSoFar, today, provider });
    }

    // ---- chort candidates: keyset pagination, unseen only ------------------
    let databaseConnected = true;
    const fetchN = Math.min(80, limit * 2 + 10);
    let batch: Array<Record<string, any>> = [];
    let batchHadMore = false;

    let carryRows: Array<Record<string, any>> = [];
    if (carry.length > 0) {
      try {
        const rows = (await prisma.video.findMany({
          where: { id: { in: carry }, status: 'READY', visibility: 'PUBLIC' },
          include: { user: { include: { profile: true } } },
        })) as Array<Record<string, any>>;
        carryRows = rows.filter(
          (r) => !dbSeen.has(r.id) && r.id !== 'vid_test_123' && !(r.streamUrl || '').includes('test.com')
        );
      } catch {
        carryRows = [];
        databaseConnected = false;
      }
    }

    const keysetAnd: Record<string, unknown>[] = [
      { id: { not: 'vid_test_123' } },
      { streamUrl: { not: { contains: 'test.com' } } },
    ];
    if (dbAfter) {
      const at = new Date(dbAfter.t);
      keysetAnd.push({
        OR: [{ createdAt: { lt: at } }, { createdAt: at, id: { lt: dbAfter.id } }],
      });
    }
    if (dbSeen.size > 0) keysetAnd.push({ id: { notIn: [...dbSeen].slice(0, SEEN_CAP) } });
    if (mode === 'following') keysetAnd.push({ userId: { in: [...followingIds] } });

    try {
      const rows = (await prisma.video.findMany({
        where: { status: 'READY', visibility: 'PUBLIC', AND: keysetAnd },
        include: { user: { include: { profile: true } } },
        orderBy: [{ createdAt: 'desc' }, { id: 'desc' }],
        take: fetchN + 1,
      })) as Array<Record<string, any>>;
      if (rows.length > fetchN) {
        batchHadMore = true;
        rows.pop();
      }
      batch = rows;
    } catch {
      batch = [];
      databaseConnected = false;
    }

    // ---- format + playability gate (never audio-gates user uploads) --------
    const rowById = new Map<string, Record<string, any>>();
    for (const r of [...carryRows, ...batch]) {
      if (!rowById.has(r.id)) rowById.set(r.id, r);
    }
    // User DB videos are already strictly verified at upload time (completeUpload verifies S3 / storage bytes).
    // They must never be dropped by a redundant network range gate.
    const playable = formatted;

    // ---- rank + creator diversity (recommended only; new/following = pure recency)
    let dbRanked: Record<string, unknown>[];
    if (mode === 'recommended') {
      const rand = mulberry32((seed ^ 0x9e3779b9) >>> 0);
      const scored = playable.map((v) => ({
        v,
        s:
          scoreVideo({
            createdAtMs: Number(v.createdAt) || 0,
            views: Number(v.viewsCount) || 0,
            likes: Number(v.likesCount) || 0,
            comments: Number(v.commentsCount) || 0,
            shares: Number(v.sharesCount) || 0,
            isFollowed: followingIds.has(String(v.creatorId || '')),
            jitter01: rand(),
          }) + (savedCreatorIds.has(String(v.creatorId || '')) ? 0.3 : 0),
      }));
      scored.sort((a, b) => b.s - a.s);
      dbRanked = orderWithDiversity(scored, (x) => String(x.v.creatorId || x.v.id)).map((x) => x.v);
    } else {
      dbRanked = [...playable].sort((a, b) => {
        const dt = Number(b.createdAt) - Number(a.createdAt);
        if (dt !== 0) return dt;
        return String(b.id).localeCompare(String(a.id));
      });
    }

    // ---- licensed discovery: per-viewer rotation, auto-advance past seen ---
    const extConfigured = includeExternal && ExternalVideoService.isConfigured();
    let extSlots = 0;
    if (mode === 'recommended' && extConfigured) {
      if (dbRanked.length === 0) extSlots = limit;
      else if (dbRanked.length >= limit) extSlots = Math.min(6, Math.floor(limit * 0.25));
      else extSlots = Math.min(limit, limit - dbRanked.length + Math.min(3, Math.floor(limit * 0.2)));
    }

    const extRanked: Record<string, unknown>[] = [];
    const seedProviders: string[] = [];
    let extSignal = false;
    if (extSlots > 0) {
      const providers = ExternalVideoService.seedProviderChain();
      const maxTotalProbes = 10;
      let totalProbes = 0;
      for (const prov of providers) {
        if (extRanked.length >= extSlots || totalProbes >= maxTotalProbes) break;
        let provProbes = 0;
        const maxProvProbes = 5;
        while (extRanked.length < extSlots && provProbes < maxProvProbes && totalProbes < maxTotalProbes) {
          const slice = externalSliceFor(prov, seed, ep);
          let sliceVideos: Record<string, unknown>[] = [];
          try {
            const res = await ExternalVideoService.getVideosFrom(prov, {
              query: slice.topic,
              page: slice.page,
              perPage: Math.min(25, (extSlots - extRanked.length) * 2),
              verifyAudio: config.feed.requireExternalAudio,
            });
            sliceVideos = (res.videos || []) as unknown as Record<string, unknown>[];
          } catch {
            sliceVideos = [];
          }
          if (sliceVideos.length > 0) extSignal = true;
          const before = extRanked.length;
          for (const ev of sliceVideos) {
            if (extRanked.length >= extSlots) break;
            const id = String(ev.id || '');
            if (!id || extSeen.has(id)) continue;
            if (!/^https:\/\//i.test(String(ev.streamUrl || ''))) continue;
            if (!/^https:\/\//i.test(String(ev.thumbnailUrl || ''))) continue;
            extRanked.push(ev);
          }
          if (extRanked.length > before && !seedProviders.includes(prov)) seedProviders.push(prov);
          ep += 1;
          provProbes += 1;
          totalProbes += 1;
        }
      }
      const shuffled = seededShuffle(extRanked, (seed ^ ep) >>> 0);
      extRanked.length = 0;
      extRanked.push(...shuffled);
      for (const ev of extRanked) extSeen.add(String(ev.id));
    }

    // ---- merge: discovery slots spread across user-first content ------------
    const pageIds = new Set<string>();
    const videos: Record<string, unknown>[] = [];
    const positions = new Set(spreadPositions(limit, Math.min(extRanked.length, limit), seed));
    let di = 0;
    let ei = 0;
    let slot = 0;
    while (videos.length < limit && (di < dbRanked.length || ei < extRanked.length)) {
      let pick: Record<string, unknown> | null = null;
      if (positions.has(slot) && ei < extRanked.length) pick = extRanked[ei++];
      else if (di < dbRanked.length) pick = dbRanked[di++];
      else if (ei < extRanked.length) pick = extRanked[ei++];
      slot += 1;
      if (!pick) break;
      const id = String(pick.id || '');
      if (!id || pageIds.has(id)) continue;
      pageIds.add(id);
      videos.push(pick);
    }

    // ---- next cursor + honest hasMore ---------------------------------------
    const servedDbIds = new Set(
      videos.filter((v) => !isExternalId(String(v.id))).map((v) => String(v.id))
    );
    const nextCarry = dbRanked
      .filter((v) => !servedDbIds.has(String(v.id)))
      .map((v) => String(v.id))
      .slice(0, CURSOR_CARRY_CAP);

    const lastBatch = batch[batch.length - 1];
    const nextDbAfter = lastBatch
      ? { t: new Date(lastBatch.createdAt).getTime(), id: String(lastBatch.id) }
      : dbAfter;

    const dbMore = batchHadMore || nextCarry.length > 0;
    const extMore = extConfigured && (extSignal || extRanked.length > 0);
    const hasMore = videos.length > 0 && (dbMore || (extMore && extSlots > 0));

    const dbCount = videos.filter((v) => !isExternalId(String(v.id))).length;
    const extCount = videos.length - dbCount;
    const providerLabel = provider === 'coverr' ? 'Coverr' : provider === 'pixabay' ? 'Pixabay' : 'Pexels';
    const source =
      dbCount > 0 && extCount > 0
        ? `Chort & ${providerLabel}`
        : dbCount > 0
          ? 'Chort'
          : extCount > 0
            ? `${providerLabel} Licensed API`
            : databaseConnected
              ? 'Chort'
              : 'Unavailable';

    const servedExtIds = videos.filter((v) => isExternalId(String(v.id))).map((v) => String(v.id));
    const nextSx = [...cursorSx, ...servedExtIds].slice(-CURSOR_SX_CAP);
    const nextSd = [...cursorSd, ...[...servedDbIds]].slice(-CURSOR_SD_CAP);

    await this.attachInteractionFlags(videos, userId);

    return {
      videos,
      nextCursor: hasMore
        ? encodeCursor({
            v: 1,
            day: today,
            seed,
            dbAfter: nextDbAfter,
            carry: nextCarry,
            ep,
            served: servedSoFar + videos.length,
            sx: nextSx,
            sd: nextSd,
          })
        : null,
      hasMore,
      meta: { mode, provider, seedProviders, dbCount, extCount, day: today, source, databaseConnected },
    };
  }

  /**
   * Trending leaderboard: top-200 by likes rescored by engagement velocity.
   * Global order (same for everyone, by definition), paginated via the
   * cursor's served-db ids so watched + served clips never resurface.
   * Pure user content: no licensed seed in trending.
   */
  private static async getTrendingPage(input: {
    limit: number;
    userId?: string;
    dbSeen: Set<string>;
    cursorSd: string[];
    servedSoFar: number;
    today: string;
    provider: string;
  }): Promise<{
    videos: Record<string, unknown>[];
    nextCursor: string | null;
    hasMore: boolean;
    meta: FeedPageMeta;
  }> {
    const { limit, userId, dbSeen, cursorSd, servedSoFar, today, provider } = input;
    let databaseConnected = true;
    let rows: Array<Record<string, any>> = [];
    try {
      rows = (await prisma.video.findMany({
        where: {
          status: 'READY',
          visibility: 'PUBLIC',
          AND: [{ id: { not: 'vid_test_123' } }, { streamUrl: { not: { contains: 'test.com' } } }],
        },
        include: { user: { include: { profile: true } } },
        orderBy: [{ likesCount: 'desc' }, { viewsCount: 'desc' }, { id: 'desc' }],
        take: 200,
      })) as Array<Record<string, any>>;
    } catch {
      databaseConnected = false;
    }

    const now = Date.now();
    const ranked = rows
      .map((r) => ({
        r,
        s: trendingScore({
          createdAtMs: new Date(r.createdAt).getTime(),
          views: Number(r.viewsCount) || 0,
          likes: Number(r.likesCount) || 0,
          comments: Number(r.commentsCount) || 0,
          shares: Number(r.sharesCount) || 0,
          nowMs: now,
        }),
      }))
      .sort((a, b) => b.s - a.s)
      .map((x) => x.r);

    const exclude = new Set<string>([...dbSeen, ...cursorSd]);
    const avail = ranked.filter((r) => !exclude.has(String(r.id)));
    const candidates = avail.slice(0, Math.min(60, limit * 2 + 10));
    const formatted = await this.formatDbVideos(candidates);
    const checkUrls = formatted.map((v) => absolutizeMediaUrl((v.videoUrl || v.streamUrl) as string));
    const mask = await checkMediaBatch(checkUrls, redis, false);
    const videos = formatted.filter((_, i) => mask[i] || !checkUrls[i]).slice(0, limit);

    const servedIds = videos.map((v) => String(v.id));
    const nextSd = [...cursorSd, ...servedIds].slice(-CURSOR_SD_CAP);
    // Honest hasMore: more UNTRIED pool remains (gate-dropped tails end here).
    const hasMore = avail.length > candidates.length;

    await this.attachInteractionFlags(videos, userId);

    return {
      videos,
      nextCursor: hasMore
        ? encodeCursor({ v: 1, day: today, seed: 0, dbAfter: null, carry: [], ep: 0, served: servedSoFar + videos.length, sx: [], sd: nextSd })
        : null,
      hasMore,
      meta: {
        mode: 'trending',
        provider,
        seedProviders: [],
        dbCount: videos.length,
        extCount: 0,
        day: today,
        source: 'Chort Trending',
        databaseConnected,
      },
    };
  }

  /** Attach likedByMe/savedByMe for authenticated viewers (new fields only). */
  private static async attachInteractionFlags(videos: Record<string, unknown>[], userId?: string): Promise<void> {
    const dbIds = videos.filter((v) => !isExternalId(String(v.id))).map((v) => String(v.id));
    const liked = new Set<string>();
    const saved = new Set<string>();
    if (userId && !isGuestUserId(userId) && dbIds.length > 0) {
      try {
        const [likeRows, savedRows] = await Promise.all([
          prisma.like.findMany({ where: { userId, videoId: { in: dbIds } }, select: { videoId: true } }),
          prisma.savedVideo.findMany({ where: { userId, videoId: { in: dbIds } }, select: { videoId: true } }),
        ]);
        likeRows.forEach((r) => liked.add(r.videoId));
        savedRows.forEach((r) => saved.add(r.videoId));
      } catch {}
    }
    for (const v of videos) {
      const id = String(v.id);
      const external = isExternalId(id);
      v.likedByMe = !external && liked.has(id);
      v.savedByMe = !external && saved.has(id);
    }
  }

  private static async formatDbVideos(rows: Array<Record<string, any>>): Promise<Record<string, unknown>[]> {
    return VideoService.formatVideoRows(rows);
  }
}
