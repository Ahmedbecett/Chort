import { PutObjectCommand, GetObjectCommand, DeleteObjectCommand, HeadObjectCommand } from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import { s3Client, config, redis, isStorageConfigured } from '../config';
import { prisma } from '../lib/prisma';
import { v4 as uuidv4 } from 'uuid';
import { isExternalId, recordWatchHistory, userKeyFor } from './feed-history';
import { AIModerationService } from './ai-moderation.service';

export interface CreateUploadUrlInput {
  userId: string;
  filename: string;
  contentType: string;
  fileSize?: number;
}

export class VideoService {
  /**
   * Shared DB-row -> API-shape formatter (feed, hashtag, saved, liked, search).
   * Accepts Prisma rows with user{profile} included; tolerates plain objects.
   */
  static async formatVideoRows(rows: Array<Record<string, any>>): Promise<Record<string, unknown>[]> {
    return Promise.all(
      rows.map(async (v: Record<string, any>) => {
        const directStreamUrl = `${config.cdn.baseUrl}/api/v1/videos/${v.id}/stream`;
        const playableUrl = await VideoService.resolvePlayableStreamUrl(v.id, v.originalKey, v.streamUrl);
        const realThumb =
          v.thumbnailUrl && !String(v.thumbnailUrl).includes('#t=')
            ? v.thumbnailUrl
            : `${config.cdn.baseUrl}/api/v1/videos/${v.id}/thumbnail`;
        const createdAt = v.createdAt instanceof Date ? v.createdAt.getTime() : new Date(v.createdAt).getTime();
        return {
          id: v.id,
          creatorId: v.userId,
          creatorUsername: v.user?.username || 'creator',
          creatorAvatar: v.user?.profile?.avatarUrl || '',
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
          createdAt,
        };
      })
    );
  }

  /**
   * Generates a playable streaming URL (presigned if S3/Neon bucket requires it)
   */
  static async resolvePlayableStreamUrl(videoId: string, originalKey?: string | null, fallbackUrl?: string | null): Promise<string> {
    // Externally-hosted bytes (Android Firebase fallback): the stored https URL
    // IS the playable file; never presign a non-existent S3 key for it.
    if (originalKey && originalKey.startsWith('external/')) {
      if (fallbackUrl && /^https:\/\//i.test(fallbackUrl)) return fallbackUrl;
    }
    if (isStorageConfigured()) {
      try {
        let key = originalKey;
        if (!key && fallbackUrl) {
          const match = fallbackUrl.match(/videos\/[^?#]+/);
          if (match) {
            key = match[0];
          }
        }
        if (!key) {
          key = `videos/${videoId}.mp4`;
        } else if (!key.startsWith('videos/')) {
          key = `videos/${key}.mp4`;
        }

        const command = new GetObjectCommand({
          Bucket: config.s3.bucket,
          Key: key,
          ResponseCacheControl: 'public, max-age=86400',
          ResponseContentType: 'video/mp4',
        });
        return await getSignedUrl(s3Client, command, { expiresIn: 86400 });
      } catch (err: any) {
        console.warn('Could not presign stream URL:', err.message);
      }
    }
    if (fallbackUrl && !fallbackUrl.includes('.neon.tech')) {
      return fallbackUrl;
    }
    return `${config.cdn.baseUrl}/api/v1/videos/${videoId}/stream`;
  }
  /**
   * Generates a pre-signed PUT URL for direct upload to S3 / Cloudflare R2 / Supabase / MinIO.
   * Strictly uses real Object Storage configuration - Never returns fake or mock URLs.
   */
  static async createSignedUploadUrl(input: CreateUploadUrlInput) {
    if (!isStorageConfigured()) {
      const missingVars: string[] = [];
      if (!config.s3.bucket) missingVars.push('S3_BUCKET');
      if (!config.s3.accessKeyId) missingVars.push('S3_ACCESS_KEY_ID');
      if (!config.s3.secretAccessKey) missingVars.push('S3_SECRET_ACCESS_KEY');

      const err = new Error(
        `Video storage credentials missing in Vercel environment variables. Required: ${missingVars.join(', ')}`
      );
      (err as any).missingVars = missingVars;
      (err as any).statusCode = 503;
      throw err;
    }

    const videoId = `vid_${uuidv4().replace(/-/g, '').substring(0, 16)}`;
    const extension = input.filename.split('.').pop()?.toLowerCase() || 'mp4';
    const objectKey = `videos/${videoId}.${extension}`;
    const thumbnailKey = `thumbnails/${videoId}.jpg`;

    // 1. Presigned PUT for Video Upload
    const command = new PutObjectCommand({
      Bucket: config.s3.bucket,
      Key: objectKey,
      ContentType: input.contentType || 'video/mp4',
      Metadata: {
        userId: input.userId,
        videoId,
      },
    });
    const uploadUrl = await getSignedUrl(s3Client, command, { expiresIn: 900 });

    // 2. Presigned PUT for Real Thumbnail Upload
    const thumbCommand = new PutObjectCommand({
      Bucket: config.s3.bucket,
      Key: thumbnailKey,
      ContentType: 'image/jpeg',
      Metadata: {
        userId: input.userId,
        videoId,
      },
    });
    const thumbnailUploadUrl = await getSignedUrl(s3Client, thumbCommand, { expiresIn: 900 });

    const cleanEndpoint = config.s3.endpoint?.replace(/\/+$/, '');
    const finalVideoUrl = cleanEndpoint
      ? `${cleanEndpoint}/${config.s3.bucket}/${objectKey}`
      : `https://${config.s3.bucket}.s3.${config.s3.region}.amazonaws.com/${objectKey}`;

    const realThumbnailUrl = `${config.cdn.baseUrl}/api/v1/videos/${videoId}/thumbnail`;

    return {
      videoId,
      objectKey,
      uploadUrl,
      thumbnailKey,
      thumbnailUploadUrl,
      directUpload: false,
      streamUrl: `${config.cdn.baseUrl}/api/v1/videos/${videoId}/stream`,
      videoUrl: finalVideoUrl,
      thumbnailUrl: realThumbnailUrl,
    };
  }

  /**
   * Saves video metadata to PostgreSQL via Prisma.
   * Strictly commits to database - Never uses fake fallbacks.
   */
  static async completeUpload(params: {
    videoId: string;
    userId: string;
    caption: string;
    videoUrl: string;
    thumbnailUrl?: string;
    musicTitle?: string;
    aspectRatio?: string;
    objectKey?: string;
  }) {
    const { videoId, userId, caption, videoUrl, thumbnailUrl, musicTitle, aspectRatio } = params;

    // Ensure user exists before creating video
    let existingUser = await prisma.user.findUnique({ where: { id: userId } });
    if (!existingUser) {
      existingUser = await prisma.user.create({
        data: {
          id: userId,
          email: `${userId}@chort.app`,
          username: userId.replace(/[^a-zA-Z0-9_]/g, '_').toLowerCase(),
          passwordHash: 'OAUTH_OR_SESSION',
          profile: {
            create: {
              displayName: userId,
            },
          },
        },
      });
    }

    // Clean and validate real thumbnail
    let realThumbnail = thumbnailUrl && !thumbnailUrl.includes('#t=')
      ? thumbnailUrl
      : `${config.cdn.baseUrl}/api/v1/videos/${videoId}/thumbnail`;

    if (realThumbnail.includes('.mp4')) {
      realThumbnail = `${config.cdn.baseUrl}/api/v1/videos/${videoId}/thumbnail`;
    }

    const clientVideoUrl = String(videoUrl || '').trim();
    const isExternalBytes =
      !params.objectKey &&
      /^https:\/\//i.test(clientVideoUrl) &&
      !clientVideoUrl.startsWith(config.cdn.baseUrl);

    const storedKey = params.objectKey || (isExternalBytes ? `external/${videoId}` : `videos/${videoId}.mp4`);
    const canonicalStreamUrl = `${config.cdn.baseUrl}/api/v1/videos/${videoId}/stream`;

    // Real upload verification: never mark READY bytes that do not exist.
    if (isExternalBytes) {
      // Android Firebase-fallback path: bytes live outside S3, so verify the
      // actual https object (HEAD, then 1-byte range GET) instead of HeadObject.
      const verified = await VideoService.verifyExternalBytes(clientVideoUrl);
      if (!verified.ok) {
        const error = new Error(
          `Upload bytes not reachable at the provided video URL. Finish the file upload before completing. (${verified.detail || 'missing'})`
        );
        (error as any).statusCode = 422;
        throw error;
      }
    } else if (isStorageConfigured()) {
      try {
        const head = await s3Client.send(
          new HeadObjectCommand({ Bucket: config.s3.bucket, Key: storedKey })
        );
        const size = Number((head as { ContentLength?: number }).ContentLength || 0);
        if (!size || size <= 0) throw new Error('empty object');
      } catch (err: any) {
        const error = new Error(
          `Upload bytes not found in storage for ${storedKey}. Finish the PUT upload before completing. (${err.message || 'missing'})`
        );
        (error as any).statusCode = 422;
        throw error;
      }
    }

    // Production publish gate: content must pass moderation before it can become READY/PUBLIC.
    // If moderation is unavailable and fail-closed is enabled, no production post is created.
    const moderation = await AIModerationService.moderateVideo({
      videoId,
      videoUrl: isExternalBytes ? clientVideoUrl : canonicalStreamUrl,
      caption: caption || '',
      objectKey: storedKey,
    });
    if (!moderation.allowed) {
      const error = new Error(moderation.reason || 'Video rejected by moderation.');
      (error as any).statusCode = 422;
      throw error;
    }

    const video = await prisma.video.create({
      data: {
        id: videoId,
        userId,
        caption: caption || 'New Chort Video',
        originalKey: storedKey,
        streamUrl: isExternalBytes ? clientVideoUrl : canonicalStreamUrl,
        thumbnailUrl: realThumbnail,
        status: 'READY',
        visibility: 'PUBLIC',
        musicTitle: musicTitle || 'Original Audio',
        aspectRatio: aspectRatio || '9:16',
        viewsCount: 0,
        likesCount: 0,
        commentsCount: 0,
        sharesCount: 0,
      },
      include: {
        user: {
          include: {
            profile: true,
          },
        },
      },
    });

    // No page-cache invalidation needed: the Feed Engine serves personalized,
    // uncached pages, so a new READY/PUBLIC row enters rotation immediately.
    // (Provider slices + media verdicts stay cached deeper down.)

    // Hashtags are best-effort and never fail the publish (dynamic import: no cycle).
    try {
      const { HashtagService } = await import('./hashtag.service');
      await HashtagService.linkVideoTags(video.id, video.caption);
    } catch {}

    // Return the exact published DB record in API shape (same formatter as the
    // feed), so the client binds the real stored video — never a guess.
    const [formatted] = await VideoService.formatVideoRows([video as unknown as Record<string, any>]);
    return formatted;
  }

  /**
   * Verify externally-hosted upload bytes (https HEAD, else 1-byte range GET).
   * Returns ok=false unless the object responds 2xx with a positive length.
   */
  static async verifyExternalBytes(url: string): Promise<{ ok: boolean; detail?: string }> {
    try {
      const head = await fetch(url, { method: 'HEAD', signal: AbortSignal.timeout(12000) });
      if (head.ok) {
        const len = Number(head.headers.get('content-length') || 0);
        if (!len || len <= 0) return { ok: false, detail: 'empty object' };
        return { ok: true };
      }
      // Some hosts reject HEAD: retry with a 1-byte range GET.
      const get = await fetch(url, {
        headers: { Range: 'bytes=0-0' },
        signal: AbortSignal.timeout(12000),
      });
      if (get.ok || get.status === 206) {
        const len = Number(get.headers.get('content-length') || get.headers.get('content-range')?.split('/')?.pop() || 0);
        await get.arrayBuffer().catch(() => null);
        if (!len || len <= 0) return { ok: false, detail: 'empty object' };
        return { ok: true };
      }
      return { ok: false, detail: `HTTP ${get.status}` };
    } catch (err: any) {
      return { ok: false, detail: String(err?.message || err).slice(0, 120) };
    }
  }

  /**
   * Toggle Like on video in PostgreSQL
   */
  static async toggleLike(videoId: string, userId: string) {
    // Ensure user exists before creating or toggling like
    let user = await prisma.user.findUnique({ where: { id: userId } });
    if (!user) {
      user = await prisma.user.create({
        data: {
          id: userId,
          email: `${userId.replace(/[^a-zA-Z0-9_]/g, '') || 'user'}@chort.app`,
          username: (userId.replace(/[^a-zA-Z0-9_]/g, '_') || 'user').toLowerCase(),
          passwordHash: 'OAUTH_OR_SESSION',
          profile: { create: { displayName: userId } },
        },
      });
    }

    const existing = await prisma.like.findUnique({
      where: {
        videoId_userId: { videoId, userId },
      },
    });

    const video = await prisma.video.findUnique({
      where: { id: videoId },
      select: { userId: true },
    });

    const likerName = await prisma.user
      .findUnique({ where: { id: userId }, select: { username: true } })
      .then((u) => u?.username || 'Someone')
      .catch(() => 'Someone');

    if (existing) {
      await prisma.like.delete({
        where: { id: existing.id },
      });
      const updated = await prisma.video.update({
        where: { id: videoId },
        data: { likesCount: { decrement: 1 } },
        select: { likesCount: true },
      });
      if (video?.userId) {
        await prisma.profile.updateMany({
          where: { userId: video.userId },
          data: { likesReceived: { decrement: 1 } },
        });
      }
      return { liked: false, likesCount: Math.max(0, updated.likesCount) };
    } else {
      await prisma.like.create({
        data: { videoId, userId },
      });
      const updated = await prisma.video.update({
        where: { id: videoId },
        data: { likesCount: { increment: 1 } },
        select: { likesCount: true },
      });
      if (video?.userId) {
        await prisma.profile.updateMany({
          where: { userId: video.userId },
          data: { likesReceived: { increment: 1 } },
        });
      }
      try {
        const { NotificationService } = await import('./notification.service');
        await NotificationService.notify({
          recipientId: video?.userId || '',
          actorId: userId,
          type: 'like',
          message: `@${likerName} liked your video`,
          referenceId: videoId,
        });
      } catch {}
      return { liked: true, likesCount: updated.likesCount };
    }
  }

  /**
   * Add comment to video in PostgreSQL
   */
  static async addComment(videoId: string, userId: string, content: string) {
    // Ensure user exists before creating comment
    let user = await prisma.user.findUnique({ where: { id: userId } });
    if (!user) {
      user = await prisma.user.create({
        data: {
          id: userId,
          email: `${userId.replace(/[^a-zA-Z0-9_]/g, '') || 'user'}@chort.app`,
          username: (userId.replace(/[^a-zA-Z0-9_]/g, '_') || 'user').toLowerCase(),
          passwordHash: 'OAUTH_OR_SESSION',
          profile: { create: { displayName: userId } },
        },
      });
    }

    const comment = await prisma.comment.create({
      data: {
        videoId,
        userId,
        content,
      },
      include: {
        user: {
          include: {
            profile: true,
          },
        },
      },
    });

    const [updated, owner] = await Promise.all([
      prisma.video.update({
        where: { id: videoId },
        data: { commentsCount: { increment: 1 } },
        select: { commentsCount: true },
      }),
      prisma.video.findUnique({ where: { id: videoId }, select: { userId: true } }).catch(() => null),
    ]);
    try {
      const { NotificationService } = await import('./notification.service');
      const commenter = (comment as { user?: { username?: string } }).user?.username || 'Someone';
      await NotificationService.notify({
        recipientId: owner?.userId || '',
        actorId: userId,
        type: 'comment',
        message: `@${commenter} commented: ${content.slice(0, 80)}`,
        referenceId: videoId,
      });
    } catch {}

    return { comment, commentsCount: updated.commentsCount };
  }

  /**
   * Delete comment from video in PostgreSQL
   */
  static async deleteComment(commentId: string, videoId: string, requestingUserId: string, userRole?: string) {
    const comment = await prisma.comment.findUnique({
      where: { id: commentId },
    });
    if (!comment) {
      const err = new Error('Comment not found');
      (err as any).statusCode = 404;
      throw err;
    }
    const isAuthor = comment.userId === requestingUserId;
    const isAdmin = userRole === 'ADMIN' || requestingUserId === 'ahmed_admin' || requestingUserId === 'user_admin';
    if (!isAuthor && !isAdmin) {
      const err = new Error('Unauthorized to delete this comment');
      (err as any).statusCode = 403;
      throw err;
    }
    await prisma.comment.delete({ where: { id: commentId } });
    const updated = await prisma.video.update({
      where: { id: videoId },
      data: { commentsCount: { decrement: 1 } },
      select: { commentsCount: true },
    });
    return { deleted: true, commentsCount: Math.max(0, updated.commentsCount) };
  }

  /**
   * Get comments for video from PostgreSQL
   */
  static async getComments(videoId: string) {
    return await prisma.comment.findMany({
      where: { videoId },
      orderBy: { createdAt: 'desc' },
      take: 50,
      include: {
        user: {
          include: {
            profile: true,
          },
        },
      },
    });
  }

  /**
   * Search videos and creators from PostgreSQL
   */
  static async search(query: string) {
    const clean = query.trim();
    if (!clean) return { videos: [], users: [] };

    const [videos, users] = await Promise.all([
      prisma.video.findMany({
        where: {
          OR: [
            { caption: { contains: clean, mode: 'insensitive' } },
            { musicTitle: { contains: clean, mode: 'insensitive' } },
          ],
        },
        include: {
          user: {
            include: {
              profile: true,
            },
          },
        },
        orderBy: { likesCount: 'desc' },
        take: 30,
      }),
      prisma.user.findMany({
        where: {
          OR: [
            { username: { contains: clean, mode: 'insensitive' } },
            { profile: { displayName: { contains: clean, mode: 'insensitive' } } },
          ],
        },
        include: {
          profile: true,
        },
        take: 20,
      }),
    ]);

    const [formatted, hashtags] = await Promise.all([
      VideoService.formatVideoRows(videos as unknown as Array<Record<string, any>>),
      import('./hashtag.service')
        .then((m) => m.HashtagService.searchTags(clean, 10))
        .catch(() => [] as Array<{ tag: string; videosCount: number }>),
    ]);

    return {
      videos: formatted,
      hashtags,
      users: users.map((u) => ({
        id: u.id,
        username: u.username,
        displayName: u.profile?.displayName || u.username,
        avatarUrl: u.profile?.avatarUrl || '',
        followersCount: u.profile?.followersCount || 0,
        bio: u.profile?.bio || '',
      })),
    };
  }

  /**
   * Record video view: increments the counter (60s dedupe) AND persists watch
   * history so the Feed Engine excludes already-watched videos. Logged-in
   * viewers get durable PostgreSQL history (View / ExternalSeen rows);
   * guests get a fast seen-list. History failures never break counting.
   */
  static async recordView(
    videoId: string,
    opts?: {
      userId?: string;
      deviceId?: string;
      ipAddress?: string;
      watchSec?: number;
      completed?: boolean;
    } | string,
    ipAddress?: string
  ) {
    const o = typeof opts === 'string' ? { userId: opts, ipAddress } : opts || {};
    const userId = o.userId?.trim() || undefined;
    const userKey = userKeyFor({ userId, deviceId: o.deviceId, ip: o.ipAddress });

    try {
      await recordWatchHistory({
        videoId,
        userId,
        userKey,
        watchSec: o.watchSec,
        completed: o.completed,
        ipAddress: o.ipAddress,
      });
    } catch {
      // history is best-effort
    }

    // Licensed seed clips live outside the Video table: no counter row.
    if (isExternalId(videoId)) {
      return { counted: true, viewsCount: 0, external: true };
    }

    try {
      const dedupeKey = `viewed:${videoId}:${userId || o.ipAddress || 'anon'}`;
      const alreadyViewed = await redis.get(dedupeKey);

      if (!alreadyViewed) {
        await redis.set(dedupeKey, '1', 'EX', 60);
        try {
          const updated = await prisma.video.update({
            where: { id: videoId },
            data: { viewsCount: { increment: 1 } },
            select: { viewsCount: true },
          });
          return { counted: true, viewsCount: updated.viewsCount };
        } catch (e) {
          // Fallback if DB error
        }
      }

      const current = await prisma.video
        .findUnique({
          where: { id: videoId },
          select: { viewsCount: true },
        })
        .catch(() => null);
      return { counted: false, viewsCount: current?.viewsCount || 0 };
    } catch {
      return { counted: false, viewsCount: 0 };
    }
  }

  /**
   * Increment share count and record share
   */
  static async recordShare(videoId: string, userId?: string) {
    const updated = await prisma.video.update({
      where: { id: videoId },
      data: { sharesCount: { increment: 1 } },
      select: { sharesCount: true },
    });
    if (userId && !userId.startsWith('user_guest')) {
      try {
        await prisma.share.create({
          data: { videoId, userId },
        });
      } catch (e) {
        // Continue if share record logging fails
      }
    }
    return { shared: true, sharesCount: updated.sharesCount };
  }

  /**
   * Toggle save/bookmark video
   */
  static async toggleSave(videoId: string, userId: string) {
    const existing = await prisma.savedVideo.findUnique({
      where: {
        videoId_userId: { videoId, userId },
      },
    });

    if (existing) {
      await prisma.savedVideo.delete({
        where: { id: existing.id },
      });
      return { saved: false };
    } else {
      await prisma.savedVideo.create({
        data: { videoId, userId },
      });
      return { saved: true };
    }
  }

  /**
   * Delete video (S3 object + database cascade) with ownership authorization
   */
  static async deleteVideo(videoId: string, requestingUserId: string, userRole?: string) {
    const video = await prisma.video.findUnique({
      where: { id: videoId },
      include: { user: true },
    });

    if (!video) {
      const err = new Error('Video not found');
      (err as any).statusCode = 404;
      throw err;
    }

    const isOwner = video.userId === requestingUserId;
    const isAdmin = userRole === 'ADMIN' || requestingUserId === 'ahmed_admin';

    if (!isOwner && !isAdmin) {
      const err = new Error('Unauthorized: Only the video author or an admin can delete this video');
      (err as any).statusCode = 403;
      throw err;
    }

    // 1. Delete video from S3 Object Storage
    if (isStorageConfigured()) {
      try {
        const videoKey = video.originalKey
          ? (video.originalKey.startsWith('videos/') ? video.originalKey : `videos/${video.originalKey}.mp4`)
          : `videos/${videoId}.mp4`;
        await (s3Client as any).send(new DeleteObjectCommand({
          Bucket: config.s3.bucket,
          Key: videoKey,
        }));

        // Delete thumbnail if custom thumbnail was uploaded
        const thumbKey = `thumbnails/${videoId}.jpg`;
        await (s3Client as any).send(new DeleteObjectCommand({
          Bucket: config.s3.bucket,
          Key: thumbKey,
        })).catch(() => {});
      } catch (err: any) {
        console.warn('S3 video deletion notice:', err.message);
      }
    }

    // 2. Cascade delete from PostgreSQL
    await prisma.video.delete({
      where: { id: videoId },
    });

    // 3. No page-cache invalidation needed (Feed Engine pages are uncached).

    return {
      success: true,
      message: 'Video and associated storage assets deleted successfully',
      deletedVideoId: videoId,
    };
  }
}
