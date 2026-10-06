import { isDbConfigured } from '../lib/prisma';
import { config } from '../config';
import { FeedEngine } from './feed-engine';
import { ExternalVideoService } from './external-video.service';

export interface FeedQueryOptions {
  userId?: string;
  deviceId?: string;
  ip?: string;
  cursor?: string;
  limit?: number;
  category?: string;
  includeExternal?: boolean;
  seen?: string | string[];
}

export class FeedService {
  /**
   * Real social feed served by the Feed Engine: per-viewer rotation, true
   * cursor pagination across chort + licensed content, watch-history
   * exclusion, and user-first ranking. No shared page cache on purpose — a
   * cached page would collapse every viewer back to one identical list.
   * (Provider slices stay cached 15min, media verdicts 24h, deeper down.)
   */
  static async getForYouFeed(options: FeedQueryOptions) {
    const limit = Math.min(30, Math.max(1, options.limit || 20));

    if (!isDbConfigured()) {
      try {
        const upstreamResp = await fetch(
          `${config.primaryUpstreamUrl}/api/v1/feed?limit=${limit}${options.cursor ? `&cursor=${encodeURIComponent(options.cursor)}` : ''}`
        );
        if (upstreamResp.ok) {
          const upstreamData = (await upstreamResp.json()) as any;
          if (upstreamData && Array.isArray(upstreamData.videos) && upstreamData.videos.length > 0) {
            return {
              ...upstreamData,
              source: 'PostgreSQL/Neon (Primary Cluster)',
              databaseConnected: true,
            };
          }
        }
      } catch (err: any) {
        console.warn('Upstream cluster fallback error:', err.message);
      }
      // No DB locally: the engine still serves a personalized, rotating,
      // verified licensed-seed page (it tolerates the missing database).
      if (ExternalVideoService.isConfigured()) {
        try {
          const page = await FeedEngine.getPage({ ...options, limit });
          return {
            videos: page.videos,
            nextCursor: page.nextCursor,
            hasMore: page.hasMore,
            databaseConnected: false,
            source: page.meta.source,
            meta: page.meta,
            message: 'PostgreSQL not connected; serving licensed seed videos.',
          };
        } catch {}
      }
      return {
        videos: [],
        nextCursor: null,
        hasMore: false,
        databaseConnected: false,
        message: 'DATABASE_URL environment variable is not configured on Vercel.',
      };
    }

    try {
      const page = await FeedEngine.getPage({ ...options, limit });
      return {
        videos: page.videos,
        nextCursor: page.nextCursor,
        hasMore: page.hasMore,
        databaseConnected: page.meta.databaseConnected,
        source: page.meta.source,
        meta: page.meta,
      };
    } catch (dbError: any) {
      console.warn('Feed engine error:', dbError.message);
      try {
        const upstreamResp = await fetch(
          `${config.primaryUpstreamUrl}/api/v1/feed?limit=${limit}${options.cursor ? `&cursor=${encodeURIComponent(options.cursor)}` : ''}`
        );
        if (upstreamResp.ok) {
          const upstreamData = (await upstreamResp.json()) as any;
          if (upstreamData && Array.isArray(upstreamData.videos) && upstreamData.videos.length > 0) {
            return {
              ...upstreamData,
              source: 'PostgreSQL/Neon (Primary Cluster)',
              databaseConnected: true,
            };
          }
        }
      } catch (err: any) {
        console.warn('Upstream cluster fallback error in catch block:', err.message);
      }
      if (ExternalVideoService.isConfigured()) {
        try {
          const external = await ExternalVideoService.getVideos({ perPage: limit });
          const providerLabel = external.provider === 'coverr' ? 'Coverr' : 'Pexels';
          return {
            videos: external.videos,
            nextCursor: null,
            hasMore: false,
            databaseConnected: false,
            source: `${providerLabel} Licensed API`,
            error: dbError.message,
          };
        } catch {}
      }
      return {
        videos: [],
        nextCursor: null,
        hasMore: false,
        databaseConnected: false,
        error: dbError.message || 'Error querying PostgreSQL database',
      };
    }
  }
}
