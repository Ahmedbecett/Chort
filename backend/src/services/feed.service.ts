import { prisma, isDbConfigured } from '../lib/prisma';
import { redis, config } from '../config';
import { VideoService } from './video.service';

export interface FeedQueryOptions {
  userId?: string;
  cursor?: string;
  limit?: number;
  category?: string;
}

export class FeedService {
  /**
   * Fetches real videos from PostgreSQL with caching.
   * Strictly uses real database records - No mock or sample videos.
   */
  static async getForYouFeed(options: FeedQueryOptions) {
    const limit = options.limit || 20;
    const cacheKey = `feed:fyp:${options.userId || 'guest'}:${options.cursor || 'top'}`;

    if (!isDbConfigured()) {
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

      // Fetch strictly from PostgreSQL with real cursor pagination
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
        take: limit,
        skip: skipCount,
        cursor: cursorFilter,
      });

      const formattedVideos = await Promise.all(
        dbVideos.map(async (v) => {
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
            streamUrl: playableUrl,
            videoUrl: playableUrl,
            thumbnailUrl: realThumb,
            musicTitle: v.musicTitle || 'Original Audio',
            likesCount: v.likesCount,
            commentsCount: v.commentsCount,
            sharesCount: v.sharesCount,
            viewsCount: v.viewsCount,
            aspectRatio: v.aspectRatio,
            createdAt: v.createdAt.getTime(),
          };
        })
      );

      const lastVideo = formattedVideos[formattedVideos.length - 1];
      const hasMore = formattedVideos.length === limit;
      const nextCursor = hasMore && lastVideo ? lastVideo.id : null;

      const payload = {
        videos: formattedVideos,
        nextCursor,
        hasMore,
        databaseConnected: true,
        source: 'PostgreSQL/Prisma',
      };

      try {
        await redis.set(cacheKey, JSON.stringify(payload), 'EX', 30);
      } catch (e) {
        // Cache write ignore
      }

      return payload;
    } catch (dbError: any) {
      console.warn('Database query error:', dbError.message);
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
