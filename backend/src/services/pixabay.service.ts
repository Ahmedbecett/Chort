import { config, redis } from '../config';
import { checkMediaBatch } from './media-verify';
import { nextTopic, rotatedPage, rotatedTopic } from './provider-rotation';
import { FormattedExternalVideo } from './pexels.service';

/**
 * Pixabay licensed video source (https://pixabay.com/api/docs/).
 * Shape mirrors PexelsService/CoverrService so the dispatcher and the Feed
 * Engine treat all licensed-seed providers uniformly:
 * portrait-only, https stream + https thumbnail required, 2x over-fetch,
 * media-verified (audio gate optional via options.verifyAudio).
 * The API key travels as a query param per Pixabay's design and is never
 * logged anywhere in this codebase.
 */

export interface PixabayVideoFile {
  url: string;
  width: number;
  height: number;
  size: number;
  // Per https://pixabay.com/api/docs/ each rendition carries its own poster.
  thumbnail?: string;
}

export interface PixabayVideoHit {
  id: number;
  pageURL?: string;
  type?: string;
  tags?: string;
  duration?: number;
  videos?: {
    large?: PixabayVideoFile;
    medium?: PixabayVideoFile;
    small?: PixabayVideoFile;
    tiny?: PixabayVideoFile;
  };
  user_id?: number;
  user?: string;
  userImageURL?: string;
}

export class PixabayService {
  private static readonly API_BASE = 'https://pixabay.com/api/videos/';

  public static isConfigured(): boolean {
    return Boolean(config.pixabay.apiKey && config.pixabay.apiKey.trim().length > 0);
  }

  public static async getVideos(options: {
    query?: string;
    page?: number;
    perPage?: number;
    verifyAudio?: boolean;
    debug?: boolean;
  } = {}): Promise<{
    configured: boolean;
    provider: string;
    page: number;
    perPage: number;
    total: number;
    videos: FormattedExternalVideo[];
    error?: string;
    debug?: { raw: number; portrait: number; portraitThumb: number; verified: number; verifyAudio: boolean };
  }> {
    const explicitPage = options.page && options.page > 0 ? Math.floor(options.page) : undefined;
    const requestedPage = explicitPage ?? 1;
    const page = rotatedPage('pixabay', explicitPage);
    const perPage = Math.min(30, Math.max(1, options.perPage || 15));
    const explicitQuery = options.query?.trim();
    const query = rotatedTopic('pixabay', options.query);

    if (!this.isConfigured()) {
      return {
        configured: false,
        provider: 'pixabay',
        page,
        perPage,
        total: 0,
        videos: [],
        error: 'PIXABAY_API_KEY environment variable is not configured on Vercel.',
      };
    }

    const audioFlag = (options.verifyAudio ?? true) ? 'a1' : 'a0';
    const cacheKey = `pixabay:${audioFlag}:${query || 'popular'}:page_${page}:limit_${perPage}`;

    try {
      const cached = await redis.get(cacheKey);
      if (cached) {
        return JSON.parse(cached);
      }
    } catch {}

    // Portrait clips are a small minority of Pixabay's catalog: fetch a wide
    // slice (1 API call either way) so portrait+audio filtering nets enough.
    const fetchCount = Math.min(200, Math.max(100, perPage * 5));
    const params = new URLSearchParams({
      key: config.pixabay.apiKey,
      q: query,
      page: String(page),
      per_page: String(fetchCount),
      safesearch: 'true',
      order: 'popular',
    });
    const endpoint = `${this.API_BASE}?${params.toString()}`;

    try {
      const response = await fetch(endpoint, {
        headers: {
          Accept: 'application/json',
          'User-Agent': 'Rivo-Video-Platform/3.0.0',
        },
      });

      if (!response.ok) {
        const errorText = await response.text().catch(() => '');
        console.warn(`Pixabay API error status ${response.status}:`, errorText.slice(0, 200));
        return {
          configured: true,
          provider: 'pixabay',
          page,
          perPage,
          total: 0,
          videos: [],
          error: `Pixabay API responded with status ${response.status}`,
        };
      }

      const data = await response.json();
      const rawVideos: PixabayVideoHit[] = data.hits || [];

      const formattedVideos: FormattedExternalVideo[] = [];
      let portraitCount = 0;
      for (const v of rawVideos) {
        if (!v || !v.id || !v.videos) continue;
        const files = [v.videos.large, v.videos.medium, v.videos.small, v.videos.tiny].filter(
          (f): f is PixabayVideoFile =>
            Boolean(f && f.url && f.url.startsWith('https://') && f.height > 0 && f.width > 0 && f.height >= f.width)
        );
        if (files.length === 0) continue;
        portraitCount += 1;
        files.sort((a, b) => b.width * b.height - a.width * a.height);
        const best = files[0];
        const thumb = best.thumbnail || '';
        if (!thumb.startsWith('https://')) continue;

        const creator = (v.user || '').trim() || 'Pixabay Creator';
        const tags = (v.tags || '').split(',').map((s) => s.trim()).filter(Boolean).slice(0, 3);
        const tagStr = tags.length > 0 ? ` • ${tags.map((t) => `#${t.replace(/\s+/g, '')}`).join(' ')}` : '';
        const seed = Number(v.id) % 1000;

        formattedVideos.push({
          id: `pix_${v.id}`,
          creatorId: `pixabay_${v.user_id || 'creator'}`,
          creatorUsername: `${creator} (Pixabay)`,
          creatorAvatar:
            v.userImageURL && v.userImageURL.startsWith('https://')
              ? v.userImageURL
              : `https://api.dicebear.com/7.x/identicon/png?seed=${encodeURIComponent(creator)}`,
          caption: `Licensed vertical clip by ${creator} on Pixabay${tagStr} #pixabay #creative #viral`,
          streamUrl: best.url,
          videoUrl: best.url,
          thumbnailUrl: thumb,
          musicTitle: `Pixabay Audio • ${creator}`,
          // Providers do not report engagement: zeros are honest, never fabricated.
          likesCount: 0,
          commentsCount: 0,
          sharesCount: 0,
          viewsCount: 0,
          aspectRatio: `${best.width}:${best.height}`,
          source: 'pixabay',
          provider: 'pixabay',
          attributionUrl: v.pageURL || 'https://pixabay.com',
          photographerUrl: v.pageURL || 'https://pixabay.com',
          // Ranking hint only (Pixabay reports no publish date): aged 7-37 days
          // so fresh user uploads always outrank licensed backfill.
          createdAt: Date.now() - 7 * 86400000 - ((seed * 7919) % (30 * 86400000)),
        });
      }

      const wantAudio = options.verifyAudio ?? true;
      const mask = await checkMediaBatch(
        formattedVideos.map((v) => v.streamUrl),
        redis,
        wantAudio
      );
      let verified = formattedVideos.filter((_, i) => mask[i]);
      if (wantAudio && verified.length < perPage) {
        // Audio-preferred backfill: silent-but-valid clips fill the page
        // instead of a thin/empty slice (verdicts are cached, so this is cheap).
        const silentMask = await checkMediaBatch(
          formattedVideos.map((v) => v.streamUrl),
          redis,
          false
        );
        for (let i = 0; i < formattedVideos.length && verified.length < perPage; i++) {
          if (silentMask[i] && !mask[i]) verified.push({ ...formattedVideos[i], musicTitle: 'Original Audio' });
        }
      }
      verified = verified.slice(0, perPage);
      if (verified.length === 0 && !explicitQuery) {
        // Rotation fallback: one retry on the next topic before giving up.
        return this.getVideos({ query: nextTopic('pixabay', query), page: requestedPage, perPage });
      }
      const result = {
        configured: true,
        provider: 'pixabay',
        page,
        perPage,
        total: Number(data.totalHits) || formattedVideos.length,
        videos: verified,
        ...(options.debug
          ? {
              debug: {
                raw: rawVideos.length,
                portrait: portraitCount,
                portraitThumb: formattedVideos.length,
                verified: verified.length,
                verifyAudio: options.verifyAudio ?? true,
              },
            }
          : {}),
      };

      // Pixabay requires API responses to be cached for 24 hours
      // (https://pixabay.com/api/docs/#api_rate_limit): 100 req/60s max.
      try {
        await redis.set(cacheKey, JSON.stringify(result), 'EX', 86400);
      } catch {}

      return result;
    } catch (err: any) {
      console.warn('Pixabay fetch error:', err.message);
      return {
        configured: true,
        provider: 'pixabay',
        page,
        perPage,
        total: 0,
        videos: [],
        error: `Failed to fetch Pixabay videos: ${err.message}`,
      };
    }
  }
}
