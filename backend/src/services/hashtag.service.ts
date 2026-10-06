import { prisma } from '../lib/prisma';
import { VideoService } from './video.service';

/**
 * Hashtags: extracted from captions at publish time, served per-tag.
 * Pure extractor is unit-tested; DB writes are best-effort so tags can
 * never break an upload.
 */

const TAG_RE = /#([\p{L}\p{N}_]{2,50})/gu;

/** Extract unique lowercase tags from a caption (deterministic, pure). */
export function extractHashtags(caption?: string | null): string[] {
  if (!caption) return [];
  const out = new Set<string>();
  for (const m of caption.matchAll(TAG_RE)) {
    const tag = m[1].toLowerCase();
    if (tag.length >= 2 && out.size < 20) out.add(tag);
  }
  return [...out];
}

export class HashtagService {
  /** Link a video to its caption tags (never throws). */
  static async linkVideoTags(videoId: string, caption?: string | null): Promise<string[]> {
    const tags = extractHashtags(caption);
    if (tags.length === 0) return [];
    const linked: string[] = [];
    for (const tag of tags) {
      try {
        const row =
          (await prisma.hashtag.findUnique({ where: { tag } }).catch(() => null)) ||
          (await prisma.hashtag.create({ data: { tag } }).catch(() => null));
        if (!row) continue;
        await prisma.videoHashtag
          .upsert({
            where: { videoId_hashtagId: { videoId, hashtagId: row.id } },
            update: {},
            create: { videoId, hashtagId: row.id },
          })
          .catch(() => null);
        linked.push(tag);
      } catch {
        // tags are best-effort
      }
    }
    return linked;
  }

  /** Paginated READY/PUBLIC videos for one tag (offset page, newest first). */
  static async getTagVideos(tag: string, page: number, limit: number) {
    const clean = tag.trim().toLowerCase().replace(/^#/, '').slice(0, 50);
    const skip = (page - 1) * limit;
    const links = await prisma.videoHashtag.findMany({
      where: {
        hashtag: { tag: clean },
        video: { status: 'READY', visibility: 'PUBLIC' },
      },
      include: { video: { include: { user: { include: { profile: true } } } } },
      orderBy: { video: { createdAt: 'desc' } },
      skip,
      take: limit + 1,
    });
    const hasMore = links.length > limit;
    if (hasMore) links.pop();
    const videos = await VideoService.formatVideoRows(links.map((l) => l.video as Record<string, unknown>));
    return { tag: clean, videos, page, hasMore };
  }

  /** Tag suggestions matching a search string. */
  static async searchTags(query: string, limit = 10) {
    const clean = query.trim().toLowerCase().replace(/^#/, '').slice(0, 50);
    if (!clean) return [];
    const rows = await prisma.hashtag.findMany({
      where: { tag: { contains: clean } },
      include: { _count: { select: { videos: true } } },
      orderBy: { videos: { _count: 'desc' } },
      take: Math.min(10, Math.max(1, limit)),
    });
    return rows.map((r) => ({ tag: r.tag, videosCount: r._count.videos }));
  }
}
