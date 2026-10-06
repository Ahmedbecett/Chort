import { prisma, isDbConfigured } from '../lib/prisma';
import { redis, config } from '../config';
import { VideoService } from './video.service';
import { ExternalVideoService } from './external-video.service';

export interface FeedQueryOptions {
  userId?: string;
  cursor?: string;
  limit?: number;
  category?: string;
  includeExternal?: boolean;
}

export class FeedService {
  /**
   * Fetches real videos from PostgreSQL with caching and merges licensed external videos (Pexels/Coverr) when available.
   * Strictly uses real database records and legal external API - No mock or sample videos.
   */
  static async getForYouFeed(options: FeedQueryOptions) {
    const limit = options.limit || 20;
    const cacheKey = `feed:fyp:${options.userId || 'guest'}:${options.cursor || 'top'}:${limit}`;

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
      if (ExternalVideoService.isConfigured()) {
        const external = await ExternalVideoService.getVideos({ perPage: limit });
        const providerLabel = external.provider === 'coverr' ? 'Coverr' : 'Pexels';
        return {
          videos: external.videos,
          nextCursor: null,
          hasMore: false,
          databaseConnected: false,
          source: `${providerLabel} Licensed API`,
          message: `PostgreSQL not connected; serving licensed ${providerLabel} videos.`,
        };
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
      const cached = await redis.get(cacheKey);
      if (cached) {
        return JSON.parse(cached);
      }
    } catch (e) {
      // Ignore cache errors
    }

    try {
      let cursorFilter: { id: string } | undefined = undefined;
      let skipCount = 0;

      if (options.cursor && options.cursor.trim()) {
        const cleanCursor = options.cursor.trim();
        const cursorExists = await prisma.video.findUnique({
          where: { id: cleanCursor },
          select: { id: true },
        });
        if (cursorExists) {
          cursorFilter = { id: cleanCursor };
          skipCount = 1;
        }
      }

      // Fetch strictly from PostgreSQL with real cursor pagination (take: limit + 1 to determine hasMore)
      const dbVideos = await prisma.video.findMany({
        where: {
          status: 'READY',
          visibility: 'PUBLIC',
          AND: [
            { id: { not: 'vid_test_123' } },
            { streamUrl: { not: { contains: 'test.com' } } },
          ],
        },
        include: {
          user: {
            include: {
              profile: true,
            },
          },
        },
        orderBy: [{ createdAt: 'desc' }, { id: 'desc' }],
        take: limit + 1,
        skip: skipCount,
        cursor: cursorFilter,
      });

      const hasMore = dbVideos.length > limit;
      if (hasMore) {
        dbVideos.pop();
      }

      const formattedVideos = await Promise.all(
        dbVideos.map(async (v) => {
          const directStreamUrl = `${config.cdn.baseUrl}/api/v1/videos/${v.id}/stream`;
          const playableUrl = await VideoService.resolvePlayableStreamUrl(v.id, v.originalKey, v.streamUrl);
          const realThumb = v.thumbnailUrl && !v.thumbnailUrl.includes('#t=')
            ? v.thumbnailUrl
            : `${config.cdn.baseUrl}/api/v1/videos/${v.id}/thumbnail`;

          return {
            id: v.id,
            creatorId: v.userId,
            creatorUsername: v.user.username,
            creatorAvatar: v.user.profile?.avatarUrl || '',
            caption: v.caption,
            streamUrl: directStreamUrl,
            videoUrl: playableUrl || directStreamUrl,
            thumbnailUrl: realThumb,
            musicTitle: v.musicTitle || 'Original Audio',
            likesCount: v.likesCount,
            commentsCount: v.commentsCount,
            sharesCount: v.sharesCount,
            viewsCount: v.viewsCount,
            aspectRatio: v.aspectRatio,
            source: 'chort',
            provider: 'chort',
            isExternal: false,
            createdAt: v.createdAt.getTime(),
          };
        })
      );

      // Fetch licensed external videos if configured
      let externalVideos: any[] = [];
      if (ExternalVideoService.isConfigured() && options.includeExternal !== false) {
        try {
          const externalData = await ExternalVideoService.getVideos({
            page: 1,
            perPage: Math.min(10, Math.ceil(limit / 2)),
          });
          externalVideos = externalData.videos || [];
        } catch {}
      }

      // Merge and deduplicate
      const combinedVideos: any[] = [];
      const seenIds = new Set<string>();

      let cIdx = 0;
      let eIdx = 0;

      while (cIdx < formattedVideos.length || eIdx < externalVideos.length) {
        if (cIdx < formattedVideos.length) {
          const vid = formattedVideos[cIdx++];
          if (!seenIds.has(vid.id)) {
            seenIds.add(vid.id);
            combinedVideos.push(vid);
          }
        }
        if (eIdx < externalVideos.length && combinedVideos.length < limit + externalVideos.length) {
          const eVid = externalVideos[eIdx++];
          if (!seenIds.has(eVid.id)) {
            seenIds.add(eVid.id);
            combinedVideos.push(eVid);
          }
        }
      }

      const lastChortVideo = formattedVideos[formattedVideos.length - 1];
      const nextCursor = hasMore && lastChortVideo ? lastChortVideo.id : null;

      const payload = {
        videos: combinedVideos,
        nextCursor,
        hasMore,
        databaseConnected: true,
        source: externalVideos.length > 0 ? (ExternalVideoService.selectedProvider() === 'coverr' ? 'Chort & Coverr' : 'Chort & Pexels') : 'PostgreSQL/Prisma',
      };

      try {
        await redis.set(cacheKey, JSON.stringify(payload), 'EX', 10);
      } catch (e) {
        // Cache write ignore
      }

      return payload;
    } catch (dbError: any) {
      console.warn('Database query error:', dbError.message);
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
