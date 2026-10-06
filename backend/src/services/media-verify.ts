/**
 * Real media verification without ffmpeg: parses MP4 box structure over HTTP
 * Range requests to determine whether a file is a reachable, valid video and
 * whether it contains an audio track (+ its codec for Android compatibility).
 * No secrets are read or logged here; only byte ranges of public media URLs.
 */

export interface MediaVerdict {
  reachable: boolean;
  hasVideo: boolean;
  hasAudio: boolean;
  audioCodec?: string;
  videoCodec?: string;
  detail?: string;
}

export interface VerdictCache {
  get(key: string): Promise<string | null>;
  set(key: string, value: string, ...args: unknown[]): Promise<unknown>;
}

const HEAD_BYTES = 131072; // 128KB covers ftyp+moov of streaming-optimized files
const TAIL_BYTES = 262144; // fallback when moov sits at the file tail
const MAX_MOOV_BYTES = 2097152;
const FETCH_TIMEOUT_MS = 10000;
const VERDICT_TTL_SECONDS = 86400; // audio-ness of a file never changes

// L1: in-memory verdicts (helps warm serverless instances; L2 = redis).
const memoryCache = new Map<string, { verdict: MediaVerdict; expiresAt: number }>();

function cacheKeyFor(url: string): string {
  const clean = url.split('?')[0];
  let hash = 5381;
  for (let i = 0; i < clean.length; i++) hash = ((hash << 5) + hash + clean.charCodeAt(i)) >>> 0;
  return `media:verdict:${hash.toString(16)}`;
}

interface Box { type: string; start: number; end: number; body: number }

export function readBoxes(buf: Buffer, start: number, end: number): Box[] {
  const boxes: Box[] = [];
  let offset = start;
  while (offset + 8 <= end) {
    let size = buf.readUInt32BE(offset);
    const type = buf.toString('ascii', offset + 4, offset + 8);
    let header = 8;
    if (size === 1) {
      if (offset + 16 > end) break;
      size = Number(buf.readBigUInt64BE(offset + 8));
      header = 16;
    } else if (size === 0) {
      size = end - offset;
    }
    if (!Number.isFinite(size) || size < header) break;
    boxes.push({ type, start: offset, end: Math.min(end, offset + size), body: offset + header });
    if (size <= 0) break;
    offset += size;
    if (boxes.length > 200) break;
  }
  return boxes;
}

export function findBox(buf: Buffer, boxes: Box[], type: string): Box | null {
  for (const box of boxes) {
    if (box.type === type) return box;
  }
  return null;
}

/**
 * Byte-scan fallback for box discovery. Box-walking from an arbitrary offset
 * (a tail window starts mid-mdat) cannot align to real boxes, so scan for the
 * 4CC with a sane size prefix instead. Returns the LAST match: moov sits at
 * the very end of tail-moov files. Without this, every non-faststart upload
 * (most phone recordings) is wrongly judged "no-moov" and dropped.
 */
export function scanForBox(buf: Buffer, type: string): Box | null {
  if (type.length !== 4) return null;
  const c0 = type.charCodeAt(0);
  const c1 = type.charCodeAt(1);
  const c2 = type.charCodeAt(2);
  const c3 = type.charCodeAt(3);
  let found: Box | null = null;
  const end = buf.length - 8;
  for (let i = 0; i <= end; i++) {
    if (buf[i + 4] !== c0 || buf[i + 5] !== c1 || buf[i + 6] !== c2 || buf[i + 7] !== c3) continue;
    const size = buf.readUInt32BE(i);
    if (size === 1) {
      if (i + 16 > buf.length) continue;
      const big = Number(buf.readBigUInt64BE(i + 8));
      if (big >= 16 && i + big <= buf.length) {
        found = { type, start: i, end: i + big, body: i + 16 };
      }
    } else if (size >= 8 && i + size <= buf.length) {
      found = { type, start: i, end: i + size, body: i + 8 };
    }
  }
  return found;
}

interface TrackInfo { handler: string; codec: string }

export function parseTrak(buf: Buffer, trak: Box): TrackInfo | null {
  const mdia = findBox(buf, readBoxes(buf, trak.body, trak.end), 'mdia');
  if (!mdia) return null;
  const mdiaKids = readBoxes(buf, mdia.body, mdia.end);
  const hdlr = findBox(buf, mdiaKids, 'hdlr');
  if (!hdlr || hdlr.body + 12 > hdlr.end) return null;
  const handler = buf.toString('ascii', hdlr.body + 8, hdlr.body + 12);
  let codec = '';
  const minf = findBox(buf, mdiaKids, 'minf');
  if (minf) {
    const stbl = findBox(buf, readBoxes(buf, minf.body, minf.end), 'stbl');
    if (stbl) {
      const stsd = findBox(buf, readBoxes(buf, stbl.body, stbl.end), 'stsd');
      if (stsd && stsd.body + 16 <= stsd.end) {
        codec = buf.toString('ascii', stsd.body + 12, stsd.body + 16).replace(/[^\x20-\x7e]/g, '');
      }
    }
  }
  return { handler, codec };
}

export function parseMoov(buf: Buffer, moov: Box): { hasVideo: boolean; hasAudio: boolean; audioCodec: string; videoCodec: string } {
  let hasVideo = false;
  let hasAudio = false;
  let audioCodec = '';
  let videoCodec = '';
  for (const trak of readBoxes(buf, moov.body, moov.end)) {
    if (trak.type !== 'trak') continue;
    const info = parseTrak(buf, trak);
    if (!info) continue;
    if (info.handler === 'vide') {
      hasVideo = true;
      if (info.codec) videoCodec = info.codec;
    } else if (info.handler === 'soun') {
      hasAudio = true;
      if (info.codec) audioCodec = info.codec;
    }
  }
  return { hasVideo, hasAudio, audioCodec, videoCodec };
}

async function fetchRange(url: string, start: number, end: number | null): Promise<{ status: number; total: number | null; bytes: Buffer }> {
  const range = end === null ? `bytes=${start}-` : `bytes=${start}-${end}`;
  const response = await fetch(url, {
    method: 'GET',
    headers: {
      Range: range,
      Accept: 'video/mp4,video/*,*/*',
      'User-Agent': 'Chort-Video-Platform/2.2.0',
    },
    signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
    redirect: 'follow',
  });
  if (response.status !== 206 && response.status !== 200) {
    return { status: response.status, total: null, bytes: Buffer.alloc(0) };
  }
  const contentRange = response.headers.get('content-range') || '';
  const totalMatch = /\/(\d+)\s*$/.exec(contentRange);
  const total = totalMatch ? Number(totalMatch[1]) : null;
  const ab = await response.arrayBuffer();
  return { status: response.status, total, bytes: Buffer.from(ab) };
}

/**
 * Verdict for one media URL. unknownContainer=true result means "bytes fetched
 * but not MP4/EBML" (proven junk); callers decide strictness per source:
 * provider feeds require verified audio, user uploads only require validity.
 */
export async function verifyMedia(url: string, cache?: VerdictCache | null): Promise<MediaVerdict> {
  const key = cacheKeyFor(url);
  const now = Date.now();
  const mem = memoryCache.get(key);
  if (mem && mem.expiresAt > now) return mem.verdict;
  if (cache) {
    try {
      const cached = await cache.get(key);
      if (cached) {
        const verdict = JSON.parse(cached) as MediaVerdict;
        memoryCache.set(key, { verdict, expiresAt: now + VERDICT_TTL_SECONDS * 1000 });
        return verdict;
      }
    } catch {
      // fall through to live verification
    }
  }

  const store = (verdict: MediaVerdict): MediaVerdict => {
    memoryCache.set(key, { verdict, expiresAt: Date.now() + VERDICT_TTL_SECONDS * 1000 });
    if (memoryCache.size > 2000) {
      const oldest = memoryCache.keys().next();
      if (!oldest.done) memoryCache.delete(oldest.value);
    }
    if (cache) {
      Promise.resolve(cache.set(key, JSON.stringify(verdict), 'EX', VERDICT_TTL_SECONDS)).catch(() => undefined);
    }
    return verdict;
  };

  try {
    const head = await fetchRange(url, 0, HEAD_BYTES - 1);
    if (head.status !== 206 && head.status !== 200) {
      return store({ reachable: false, hasVideo: false, hasAudio: false, detail: `http-${head.status}` });
    }
    const buf = head.bytes;
    if (buf.length < 12) {
      return store({ reachable: true, hasVideo: false, hasAudio: false, detail: 'too-short' });
    }
    // EBML (WebM/MKV): cannot parse tracks cheaply -> validity only.
    if (buf[0] === 0x1a && buf[1] === 0x45 && buf[2] === 0xdf && buf[3] === 0xa3) {
      return store({ reachable: true, hasVideo: true, hasAudio: false, detail: 'ebml-unverified' });
    }
    let boxes = readBoxes(buf, 0, buf.length);
    if (!findBox(buf, boxes, 'ftyp')) {
      return store({ reachable: true, hasVideo: false, hasAudio: false, detail: 'not-mp4' });
    }
    let moov = findBox(buf, boxes, 'moov') || scanForBox(buf, 'moov');
    let full = buf;
    if (moov && head.total) {
      // Declared moov end (clamped box end cannot reveal truncation).
      let declaredEnd = moov.end;
      const rawSize = buf.readUInt32BE(moov.start);
      if (rawSize === 1 && moov.start + 16 <= buf.length) {
        declaredEnd = moov.start + Number(buf.readBigUInt64BE(moov.start + 8));
      } else if (rawSize > 1) {
        declaredEnd = moov.start + rawSize;
      }
      if (declaredEnd > buf.length) {
        const want = Math.min(MAX_MOOV_BYTES, head.total, declaredEnd);
        if (want > buf.length) {
          const more = await fetchRange(url, 0, want - 1);
          if (more.bytes.length > buf.length) {
            full = more.bytes;
            boxes = readBoxes(full, 0, full.length);
            const m2 = findBox(full, boxes, 'moov');
            if (m2) moov = m2;
          }
        }
      }
    }
    if (!moov && head.total && head.total > TAIL_BYTES) {
      // moov likely at tail: fetch the tail window and SCAN for it (a blind
      // box-walk from mid-mdat cannot align to real boxes).
      const tail = await fetchRange(url, head.total - TAIL_BYTES, head.total - 1);
      if (tail.bytes.length > 12) {
        const tailBoxes = readBoxes(tail.bytes, 0, tail.bytes.length);
        const tailMoov = findBox(tail.bytes, tailBoxes, 'moov') || scanForBox(tail.bytes, 'moov');
        if (tailMoov) {
          full = tail.bytes;
          moov = { type: 'moov', start: tailMoov.start, body: tailMoov.body, end: tailMoov.end };
        }
      }
    }
    if (!moov) {
      const hasMoof = boxes.some((b) => b.type === 'moof');
      return store({ reachable: true, hasVideo: false, hasAudio: false, detail: hasMoof ? 'fragmented-unknown' : 'no-moov' });
    }
    const tracks = parseMoov(full, moov);
    return store({
      reachable: true,
      hasVideo: tracks.hasVideo,
      hasAudio: tracks.hasAudio,
      audioCodec: tracks.audioCodec || undefined,
      videoCodec: tracks.videoCodec || undefined,
    });
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'fetch-failed';
    return store({ reachable: false, hasVideo: false, hasAudio: false, detail: message.slice(0, 60) });
  }
}

/**
 * Batch gate: returns a keep/drop mask aligned with `urls`.
 * requireAudio=true (provider feeds): reachable + video + audio.
 * requireAudio=false (user uploads): reachable + valid video (silence is legitimate).
 * Unknown containers are dropped for providers, kept for uploads (see callers).
 */
export async function checkMediaBatch(urls: string[], cache?: VerdictCache | null, requireAudio = true): Promise<boolean[]> {
  return Promise.all(urls.map(async (url) => {
    if (!url || !/^https:\/\//i.test(url)) return false;
    try {
      const verdict = await verifyMedia(url, cache);
      return verdict.reachable && verdict.hasVideo && (!requireAudio || verdict.hasAudio);
    } catch {
      return false;
    }
  }));
}
