import { config, redis } from '../config';
import { checkMediaBatch } from './media-verify';
import { nextTopic, rotatedPage, rotatedTopic } from './provider-rotation';

export interface PexelsVideoFile {
  id: number;
  quality: string;
  file_type: string;
  width: number | null;
  height: number | null;
  fps: number | null;
  link: string;
}

export interface PexelsVideoUser {
  id: number;
  name: string;
  url: string;
}

export interface PexelsVideoItem {
  id: number;
  width: number;
  height: number;
  url: string;
  image: string;
  duration: number;
  user: PexelsVideoUser;
  video_files: PexelsVideoFile[];
  video_pictures: Array<{ id: number; picture: string; nr: number }>;
}

export interface FormattedExternalVideo {
  id: string;
  creatorId: string;
  creatorUsername: string;
  creatorAvatar: string;
  caption: string;
  streamUrl: string;
  videoUrl: string;
  thumbnailUrl: string;
  musicTitle: string;
  likesCount: number;
  commentsCount: number;
  sharesCount: number;
  viewsCount: number;
  aspectRatio: string;
  source: string;
  provider: string;
  attributionUrl: string;
  photographerUrl: string;
  createdAt: number;
}

export class PexelsService {
  private static readonly API_BASE = 'https://api.pexels.com/videos';

  public static isConfigured(): boolean {
    return Boolean(config.pexels.apiKey && config.pexels.apiKey.trim().length > 0);
  }

  /**
   * Fetches portrait licensed videos from Pexels Video API.
   * Respects rate limits via caching and formats output for Chort feed.
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
    const page = rotatedPage('pexels', explicitPage);
    const perPage = Math.min(30, Math.max(1, options.perPage || 15));
    const explicitQuery = options.query?.trim();
    const query = rotatedTopic('pexels', options.query);

    if (!this.isConfigured()) {
      return {
        configured: false,
        provider: 'pexels',
        page,
        perPage,
        total: 0,
        videos: [],
        error: 'PEXELS_API_KEY environment variable is not configured on Vercel.',
      };
    }

    const audioFlag = (options.verifyAudio ?? true) ? 'a1' : 'a0';
    const cacheKey = `pexels:${audioFlag}:${query || 'popular'}:page_${page}:limit_${perPage}`;

    try {
      const cached = await redis.get(cacheKey);
      if (cached) {
        return JSON.parse(cached);
      }
    } catch {}

    const fetchCount = Math.min(30, perPage * 2);
    const endpoint = query
      ? `${this.API_BASE}/search?query=${encodeURIComponent(query)}&orientation=portrait&page=${page}&per_page=${fetchCount}`
      : `${this.API_BASE}/popular?orientation=portrait&page=${page}&per_page=${fetchCount}`;

    try {
      const response = await fetch(endpoint, {
        headers: {
          Authorization: config.pexels.apiKey,
          Accept: 'application/json',
          'User-Agent': 'Chort-Video-Platform/2.2.0',
        },
      });

      if (!response.ok) {
        const errorText = await response.text();
        console.warn(`Pexels API error status ${response.status}:`, errorText);
        return {
          configured: true,
          provider: 'pexels',
          page,
          perPage,
          total: 0,
          videos: [],
          error: `Pexels API responded with status ${response.status}`,
        };
      }

      const data = await response.json();
      const rawVideos: PexelsVideoItem[] = data.videos || [];

      const formattedVideos: FormattedExternalVideo[] = rawVideos.map((v) => {
        // Choose best video file: portrait mp4 with preferred 720p or 1080p
        const mp4Files = (v.video_files || []).filter(
          (f) => f.file_type === 'video/mp4' && f.link && f.link.startsWith('http')
        );

        // Sort by quality: prefer hd with portrait aspect ratio
        const bestFile =
          mp4Files.find((f) => f.quality === 'hd' && f.height && f.width && f.height >= f.width) ||
          mp4Files.find((f) => f.quality === 'hd') ||
          mp4Files.find((f) => f.quality === 'sd') ||
          mp4Files[0] ||
          v.video_files?.[0];

        const videoFileUrl = bestFile ? bestFile.link : '';
        const seed = v.id % 1000;

        return {
          id: `pex_${v.id}`,
          creatorId: `pexels_${v.user?.id || 'creator'}`,
          creatorUsername: `${v.user?.name || 'Pexels Creator'} (Pexels)`,
          creatorAvatar: `https://api.dicebear.com/7.x/identicon/png?seed=${encodeURIComponent(v.user?.name || v.id.toString())}`,
          caption: `Creative vertical clip by ${v.user?.name || 'Photographer'} on Pexels • Licensed for free use #pexels #creative #viral`,
          streamUrl: videoFileUrl,
          videoUrl: videoFileUrl,
          thumbnailUrl: v.image || '',
          musicTitle: `Pexels Audio • ${v.user?.name || 'Original Track'}`,
          likesCount: 120 + seed * 3,
          commentsCount: 15 + (seed % 40),
          sharesCount: 8 + (seed % 25),
          viewsCount: 1500 + seed * 12,
          aspectRatio: v.width && v.height ? `${v.width}:${v.height}` : '9:16',
          source: 'pexels',
          provider: 'pexels',
          attributionUrl: v.url || 'https://www.pexels.com',
          photographerUrl: v.user?.url || 'https://www.pexels.com',
          createdAt: Date.now() - (v.id % 86400000),
        };
      });

      const mask = await checkMediaBatch(
        formattedVideos.map((v) => v.streamUrl),
        redis,
        options.verifyAudio ?? true,
      );
      const verified = formattedVideos.filter((_, i) => mask[i]).slice(0, perPage);
      if (verified.length === 0 && !explicitQuery) {
        // Rotation fallback: one retry on the next topic before giving up.
        return this.getVideos({ query: nextTopic('pexels', query), page: requestedPage, perPage });
      }
      const result = {
        configured: true,
        provider: 'pexels',
        page: data.page || page,
        perPage: data.per_page || perPage,
        total: data.total_results || formattedVideos.length,
        videos: verified,
      };

      // Cache for 15 minutes (900 seconds) to respect Pexels rate limits
      try {
        await redis.set(cacheKey, JSON.stringify(result), 'EX', 900);
      } catch {}

      return result;
    } catch (err: any) {
      console.warn('Pexels fetch error:', err.message);
      return {
        configured: true,
        provider: 'pexels',
        page,
        perPage,
        total: 0,
        videos: [],
        error: `Failed to fetch Pexels videos: ${err.message}`,
      };
    }
  }
}
