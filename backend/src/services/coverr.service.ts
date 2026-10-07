import { config, redis } from '../config';
import { checkMediaBatch } from './media-verify';
import { nextTopic, rotatedPage, rotatedTopic } from './provider-rotation';
import { FormattedExternalVideo } from './pexels.service';

export interface CoverrVideoUrls {
  mp4?: string;
  mp4_preview?: string;
  mp4_download?: string;
}

export interface CoverrVideoItem {
  id: string;
  title?: string;
  description?: string;
  poster?: string;
  thumbnail?: string;
  is_vertical?: boolean;
  aspect_ratio?: string;
  duration?: number;
  max_height?: number;
  max_width?: number;
  urls?: CoverrVideoUrls;
}

export class CoverrService {
  private static readonly API_BASE = 'https://api.coverr.co/videos';

  public static isConfigured(): boolean {
    return Boolean(config.coverr.apiKey && config.coverr.apiKey.trim().length > 0);
  }

  /**
   * Fetches portrait licensed videos from Coverr Videos API.
   * Respects rate limits via caching and formats output for ZEVORA feed.
   * Shape mirrors PexelsService so the dispatcher can swap providers safely.
   */
  public static async getVideos(options: {
    query?: string;
    page?: number;
    perPage?: number;
    verifyAudio?: boolean;
  } = {}): Promise<{
    configured: boolean;
    provider: string;
    page: number;
    perPage: number;
    total: number;
    videos: FormattedExternalVideo[];
    error?: string;
  }> {
    const explicitPage = options.page && options.page > 0 ? Math.floor(options.page) : undefined;
    const requestedPage = explicitPage ?? 1;
    const page = rotatedPage('coverr', explicitPage);
    const perPage = Math.min(20, Math.max(1, options.perPage || 15));
    const coverrPage = page - 1;
    const explicitQuery = options.query?.trim();
    const query = rotatedTopic('coverr', options.query);

    if (!this.isConfigured()) {
      return {
        configured: false,
        provider: 'coverr',
        page,
        perPage,
        total: 0,
        videos: [],
        error: 'COVERR_API_KEY environment variable is not configured on Vercel.',
      };
    }

    const audioFlag = (options.verifyAudio ?? true) ? 'a1' : 'a0';
    const cacheKey = `coverr:${audioFlag}:${query || 'popular'}:page_${page}:limit_${perPage}`;

    try {
      const cached = await redis.get(cacheKey);
      if (cached) {
        return JSON.parse(cached);
      }
    } catch {}

    const fetchCount = Math.min(20, perPage * 2);
    const params = new URLSearchParams({
      query,
      page: String(coverrPage),
      page_size: String(fetchCount),
      sort: 'popular',
      urls: 'true',
    });
    const endpoint = `${this.API_BASE}?${params.toString()}`;

    try {
      const response = await fetch(endpoint, {
        headers: {
          Authorization: `Bearer ${config.coverr.apiKey}`,
          Accept: 'application/json',
          'User-Agent': 'ZEVORA-Video-Platform/3.0.0',
        },
      });

      if (!response.ok) {
        const errorText = await response.text();
        console.warn(`Coverr API error status ${response.status}:`, errorText);
        return {
          configured: true,
          provider: 'coverr',
          page,
          perPage,
          total: 0,
          videos: [],
          error: `Coverr API responded with status ${response.status}`,
        };
      }

      const data = await response.json();
      const rawVideos: CoverrVideoItem[] = data.hits || [];

      const formattedVideos: FormattedExternalVideo[] = [];
      for (const v of rawVideos) {
        const videoFileUrl = v.urls?.mp4 || '';
        const thumb = v.thumbnail || v.poster || '';
        if (!v.id || !videoFileUrl.startsWith('https://') || !thumb.startsWith('https://')) continue;
        const width = Number(v.max_width || 0);
        const height = Number(v.max_height || 0);
        const vertical = v.is_vertical === true || (width > 0 && height > 0 && height >= width);
        if (!vertical) continue;

        const idStr = String(v.id);
        let seed = 0;
        for (let i = 0; i < idStr.length; i++) seed = (seed + idStr.charCodeAt(i)) % 1000;

        const title = v.title?.trim() || `Coverr video ${v.id}`;
        formattedVideos.push({
          id: `cov_${v.id}`,
          creatorId: 'coverr_contributor',
          creatorUsername: 'Coverr Creator (Coverr)',
          creatorAvatar: `https://api.dicebear.com/7.x/identicon/png?seed=${encodeURIComponent(title)}`,
          caption: `${title} on Coverr • Licensed for free use #coverr #creative #viral`,
          streamUrl: videoFileUrl,
          videoUrl: videoFileUrl,
          thumbnailUrl: thumb,
          musicTitle: 'Coverr Audio • Original Track',
          // Providers do not report engagement: zeros are honest, never fabricated.
          likesCount: 0,
          commentsCount: 0,
          sharesCount: 0,
          viewsCount: 0,
          aspectRatio: width && height ? `${width}:${height}` : '9:16',
          source: 'coverr',
          provider: 'coverr',
          attributionUrl: `https://coverr.co/videos/${v.id}`,
          photographerUrl: 'https://coverr.co',
          // Ranking hint only (Coverr reports no publish date): aged 7-37 days
          // so fresh user uploads always outrank licensed backfill.
          createdAt: Date.now() - 7 * 86400000 - ((seed * 7919) % (30 * 86400000)),
        });
      }

      const wantAudio = options.verifyAudio ?? true;
      const mask = await checkMediaBatch(
        formattedVideos.map((v) => v.streamUrl),
        redis,
        wantAudio,
      );
      let verified = formattedVideos.filter((_, i) => mask[i]);
      if (wantAudio && verified.length < perPage) {
        // Audio-preferred backfill: silent-but-valid clips fill the page
        // instead of a thin/empty slice (verdicts are cached, so this is cheap).
        const silentMask = await checkMediaBatch(
          formattedVideos.map((v) => v.streamUrl),
          redis,
          false,
        );
        for (let i = 0; i < formattedVideos.length && verified.length < perPage; i++) {
          if (silentMask[i] && !mask[i]) verified.push({ ...formattedVideos[i], musicTitle: 'Original Audio' });
        }
      }
      verified = verified.slice(0, perPage);
      if (verified.length === 0 && !explicitQuery) {
        // Rotation fallback: one retry on the next topic before giving up.
        return this.getVideos({ query: nextTopic('coverr', query), page: requestedPage, perPage });
      }
      const result = {
        configured: true,
        provider: 'coverr',
        page: (Number(data.page) || coverrPage) + 1,
        perPage: Number(data.page_size) || perPage,
        total: data.total || formattedVideos.length,
        videos: verified,
      };

      // Cache for 15 minutes (900 seconds) to respect Coverr rate limits
      try {
        await redis.set(cacheKey, JSON.stringify(result), 'EX', 900);
      } catch {}

      return result;
    } catch (err: any) {
      console.warn('Coverr fetch error:', err.message);
      return {
        configured: true,
        provider: 'coverr',
        page,
        perPage,
        total: 0,
        videos: [],
        error: `Failed to fetch Coverr videos: ${err.message}`,
      };
    }
  }
}
