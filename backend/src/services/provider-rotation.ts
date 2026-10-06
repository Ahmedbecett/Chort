/**
 * Deterministic provider rotation: no state, no extra infrastructure.
 * Time buckets pick (topic, page) slices so consecutive windows serve fresh
 * content while requests inside one window stay consistent (and cacheable).
 * Explicit caller query/page always win (tests + direct pagination).
 */

const WINDOW_MS = 15 * 60 * 1000;

// Portrait-rich Pexels topics (Pexels filters orientation server-side).
const PEXELS_TOPICS = ['nature', 'city', 'people', 'ocean', 'dance', 'travel', 'animals', 'food'];
const PEXELS_MAX_PAGE = 3;

// Coverr topics empirically returning portrait videos.
const COVERR_TOPICS = ['vertical', 'fashion', 'concert', 'crowd'];
const COVERR_MAX_PAGE = 2;

// Pixabay search is broad; same portrait-friendly discovery pool.
const PIXABAY_TOPICS = ['nature', 'city', 'people', 'ocean', 'dance', 'travel', 'animals', 'food'];
const PIXABAY_MAX_PAGE = 3;

export type RotationProvider = 'pexels' | 'coverr' | 'pixabay';

function topicsFor(provider: RotationProvider): string[] {
  if (provider === 'coverr') return COVERR_TOPICS;
  if (provider === 'pixabay') return PIXABAY_TOPICS;
  return PEXELS_TOPICS;
}

function maxPageFor(provider: RotationProvider): number {
  if (provider === 'coverr') return COVERR_MAX_PAGE;
  if (provider === 'pixabay') return PIXABAY_MAX_PAGE;
  return PEXELS_MAX_PAGE;
}

export function rotationBucket(whenMs = Date.now()): number {
  return Math.floor(whenMs / WINDOW_MS);
}

export function rotatedTopic(provider: RotationProvider, explicitQuery: string | undefined, whenMs = Date.now()): string {
  const explicit = explicitQuery?.trim();
  if (explicit) return explicit;
  const topics = topicsFor(provider);
  return topics[rotationBucket(whenMs) % topics.length];
}

export function rotatedTopicIndex(provider: RotationProvider, whenMs = Date.now()): number {
  return rotationBucket(whenMs) % topicsFor(provider).length;
}

export function nextTopic(provider: RotationProvider, failedTopic: string): string {
  const topics = topicsFor(provider);
  const idx = topics.indexOf(failedTopic);
  return topics[(idx + 1 + topics.length) % topics.length];
}

export function rotatedPage(provider: RotationProvider, requestedPage: number | undefined, whenMs = Date.now()): number {
  // Explicit pages (including 1) always win: callers paginating deliberately
  // must get exactly the slice they asked for. Time rotation applies only
  // when the caller omitted the page entirely.
  if (requestedPage !== undefined) return Math.max(1, Math.floor(requestedPage));
  const topics = topicsFor(provider);
  const cycle = Math.floor(rotationBucket(whenMs) / topics.length);
  return 1 + (cycle % maxPageFor(provider));
}

/** Feed Engine drives rotation explicitly per user+page; expose the pools. */
export function topicsForProvider(provider: RotationProvider): string[] {
  return [...topicsFor(provider)];
}

export function maxPageForProvider(provider: RotationProvider): number {
  return maxPageFor(provider);
}
