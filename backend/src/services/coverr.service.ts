import { config, redis } from '../config';
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
   * Respects rate limits via caching and formats output for Chort feed.
   * Shape mirrors PexelsService so the dispatcher can swap providers safely.
   */
  public static async getVideos(options: {
    query?: string;
    page?: number;
    perPage?: number;
  } = {}): Promise<{
    configured: boolean;
    provider: string;
    page: number;
    perPage: number;
    total: number;
    videos: FormattedExternalVideo[];
    error?: string;
  }> {
    const page = Math.max(1, options.page || 1);
    const perPage = Math.min(20, Math.max(1, options.perPage || 15));
    const coverrPage = page - 1;
    const query = options.query?.trim() || 'vertical';

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

    const cacheKey = `coverr:${query || 'popular'}:page_${page}:limit_${perPage}`;

    try {
      const cached = await redis.get(cacheKey);
      if (cached) {
        return JSON.parse(cached);
      }
    } catch {}

    const params = new URLSearchParams({
      query,
      page: String(coverrPage),
      page_size: String(perPage),
      sort: 'popular',
      urls: 'true',
    });
    const endpoint = `${this.API_BASE}?${params.toString()}`;

    try {
      const response = await fetch(endpoint, {
        headers: {
          Authorization: `Bearer ${config.coverr.apiKey}`,
          Accept: 'application/json',
          'User-Agent': 'Chort-Video-Platform/2.2.0',
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
          likesCount: 120 + seed * 3,
          commentsCount: 15 + (seed % 40),
          sharesCount: 8 + (seed % 25),
          viewsCount: 1500 + seed * 12,
          aspectRatio: width && height ? `${width}:${height}` : '9:16',
          source: 'coverr',
          provider: 'coverr',
          attributionUrl: `https://coverr.co/videos/${v.id}`,
          photographerUrl: 'https://coverr.co',
          createdAt: Date.now() - (seed % 86400000),
        });
      }

      const result = {
        configured: true,
        provider: 'coverr',
        page: (Number(data.page) || coverrPage) + 1,
        perPage: Number(data.page_size) || perPage,
        total: data.total || formattedVideos.length,
        videos: formattedVideos,
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
