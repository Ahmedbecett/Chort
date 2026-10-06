// Smoke tests for the Feed Engine pure core (no DB, no network).
// Run: npm run build && node feed-engine.smoke.mjs
import {
  fnv1a32,
  seedFor,
  seededShuffle,
  mulberry32,
  scoreVideo,
  orderWithDiversity,
  externalSliceFor,
  spreadPositions,
  encodeCursor,
  decodeCursor,
} from './dist/services/feed-engine.js';
import { userKeyFor, isGuestUserId, parseSeenParam, isExternalId } from './dist/services/feed-history.js';
import { topicsForProvider, maxPageForProvider, rotatedPage } from './dist/services/provider-rotation.js';

let pass = 0;
function ok(cond, name) {
  if (!cond) {
    console.error(`FAIL: ${name}`);
    process.exitCode = 1;
  } else {
    pass++;
    console.log(`ok: ${name}`);
  }
}

// --- identity ---
ok(userKeyFor({ userId: 'u1' }) === 'u:u1', 'logged user key');
ok(userKeyFor({ userId: 'user_guest', deviceId: 'dev9' }) === 'd:dev9', 'guest falls back to device');
ok(userKeyFor({ ip: '1.2.3.4' }).startsWith('ip:'), 'ip fallback keyed');
ok(userKeyFor({ userId: 'alice' }) !== userKeyFor({ userId: 'bob' }), 'different users, different keys');
ok(isGuestUserId(undefined) && isGuestUserId('user_guest') && !isGuestUserId('alice'), 'guest detection');
ok(isExternalId('pex_1') && isExternalId('cov_x') && isExternalId('pix_7') && !isExternalId('vid_abc'), 'external id detection');
ok(JSON.stringify(parseSeenParam('a,b,,c')) === '["a","b","c"]', 'seen param parsing');

// --- seed & shuffle determinism + per-user variation ---
ok(seedFor('u:a', '2026-10-06') === seedFor('u:a', '2026-10-06'), 'seed deterministic');
ok(seedFor('u:a', '2026-10-06') !== seedFor('u:b', '2026-10-06'), 'seed differs per user');
ok(seedFor('u:a', '2026-10-06') !== seedFor('u:a', '2026-10-07'), 'seed rotates daily');
const base = [1, 2, 3, 4, 5, 6, 7, 8];
ok(
  JSON.stringify(seededShuffle(base, 42)) === JSON.stringify(seededShuffle(base, 42)),
  'shuffle deterministic'
);
ok(
  JSON.stringify(seededShuffle(base, 42)) !== JSON.stringify(seededShuffle(base, 43)),
  'shuffle differs per seed'
);
const r1 = mulberry32(7)();
ok(r1 >= 0 && r1 < 1, 'prng range');

// --- ranking: recency first, engagement helps, follow boosts ---
const now = Date.now();
const fresh = scoreVideo({ createdAtMs: now - 3600000, views: 0, likes: 0, comments: 0, shares: 0, isFollowed: false, jitter01: 0, nowMs: now });
const old = scoreVideo({ createdAtMs: now - 30 * 86400000, views: 0, likes: 0, comments: 0, shares: 0, isFollowed: false, jitter01: 0, nowMs: now });
ok(fresh > old, 'fresh outranks stale (recency)');
const engOld = scoreVideo({ createdAtMs: now - 30 * 86400000, views: 100000, likes: 5000, comments: 500, shares: 200, isFollowed: false, jitter01: 0, nowMs: now });
ok(engOld > old, 'engagement lifts score');
const followed = scoreVideo({ createdAtMs: now - 30 * 86400000, views: 0, likes: 0, comments: 0, shares: 0, isFollowed: true, jitter01: 0, nowMs: now });
ok(followed > fresh, 'followed creator wins (follow boost)');

// --- diversity ordering ---
const items = [
  { id: 'a1', c: 'alice' },
  { id: 'a2', c: 'alice' },
  { id: 'a3', c: 'alice' },
  { id: 'b1', c: 'bob' },
  { id: 'c1', c: 'cara' },
];
const ordered = orderWithDiversity(items, (x) => x.c, 2);
const ids = ordered.map((x) => x.id);
ok(ids[0] === 'a1' && ids[1] === 'b1' && ids[2] === 'c1', `diversity interleave (${ids.join(',')})`);

// --- external rotation: per-user sequences, bounded pages ---
for (const p of ['pexels', 'coverr', 'pixabay']) {
  const topics = topicsForProvider(p);
  const maxPage = maxPageForProvider(p);
  const s0 = externalSliceFor(p, 111, 0);
  const s1 = externalSliceFor(p, 111, 1);
  ok(topics.includes(s0.topic) && s0.page >= 1 && s0.page <= maxPage, `${p} slice in bounds`);
  ok(s0.topic !== s1.topic || s0.page !== s1.page, `${p} advances across pages`);
  const other = externalSliceFor(p, 112, 0); // adjacent seed => adjacent topic
  ok(other.topic !== s0.topic || other.page !== s0.page, `${p} differs per user seed`);
}

// --- spread positions ---
const pos = spreadPositions(20, 5, 12345);
ok(pos.length === 5 && new Set(pos).size === 5 && pos.every((p) => p >= 0 && p < 20), 'spread positions valid');
ok(JSON.stringify(spreadPositions(20, 5, 1)) !== JSON.stringify(spreadPositions(20, 5, 2)), 'spread differs per seed');

// --- cursor round-trip + legacy + invalid ---
const state = { v: 1, day: '2026-10-06', seed: 42, dbAfter: { t: 123, id: 'vid_x' }, carry: ['a', 'b'], ep: 3, served: 20, sx: ['pex_1', 'cov_2'] };
const enc = encodeCursor(state);
ok(enc.startsWith('fe1.'), 'cursor prefix');
const dec = decodeCursor(enc);
ok(dec && dec.v === 1 && dec.ep === 3 && dec.carry.length === 2 && dec.dbAfter.id === 'vid_x', 'cursor round-trip');
ok(dec && dec.sx.length === 2 && dec.sx[0] === 'pex_1', 'cursor carries served seed ids');
// explicit pages (incl. 1) win; rotation only when omitted
ok(rotatedPage('pexels', 1) === 1 && rotatedPage('coverr', 2) === 2, 'explicit page respected');
ok(rotatedPage('pexels', 0) === 1 && rotatedPage('pexels', -3) === 1, 'page clamped');
const auto = rotatedPage('pexels', undefined);
ok(auto >= 1 && auto <= maxPageForProvider('pexels'), 'omitted page rotates in bounds');
const legacy = decodeCursor('vid_old123');
ok(legacy && legacy.legacyDbId === 'vid_old123', 'legacy cursor supported');
ok(decodeCursor('') === null && decodeCursor('fe1.!!!') === null, 'invalid cursor rejected');
ok(fnv1a32('x') === fnv1a32('x') && typeof fnv1a32('x') === 'number', 'fnv stable');

console.log(`\n${pass} checks passed${process.exitCode ? ' WITH FAILURES' : ''}`);
