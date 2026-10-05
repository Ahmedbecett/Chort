import { prisma, isDbConfigured } from '../lib/prisma';
import { redis } from '../config';
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
      // Fetch strictly from PostgreSQL
      const dbVideos = await prisma.video.findMany({
        where: {
          status: 'READY',
          visibility: 'PUBLIC',
        },
        include: {
          user: {
            include: {
              profile: true,
            },
          },
        },
        orderBy: [{ createdAt: 'desc' }, { likesCount: 'desc' }],
        take: limit,
      });

      const formattedVideos = await Promise.all(
        dbVideos.map(async (v) => {
          const playableUrl = await VideoService.resolvePlayableStreamUrl(v.id, v.originalKey, v.streamUrl);
          return {
            id: v.id,
            creatorId: v.userId,
            creatorUsername: v.user.username,
            creatorAvatar: v.user.profile?.avatarUrl || '',
            caption: v.caption,
            streamUrl: playableUrl,
            videoUrl: playableUrl,
            thumbnailUrl: v.thumbnailUrl || (playableUrl ? `${playableUrl}#t=0.1` : ''),
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

      const payload = {
        videos: formattedVideos,
        nextCursor: formattedVideos.length >= limit ? `cursor_${Date.now()}` : null,
        hasMore: formattedVideos.length >= limit,
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
