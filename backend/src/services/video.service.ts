import { PutObjectCommand } from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import { s3Client, config, redis } from '../config';
import { v4 as uuidv4 } from 'uuid';
import { Queue } from 'bullmq';

// Video transcoding BullMQ queue on Redis
export const videoQueue = new Queue('video-transcoding', {
  connection: {
    host: new URL(config.redisUrl).hostname || 'localhost',
    port: parseInt(new URL(config.redisUrl).port || '6379', 10),
  },
});

export interface CreateUploadUrlInput {
  userId: string;
  filename: string;
  contentType: string;
}

export class VideoService {
  /**
   * Generates a secure pre-signed PUT URL for direct-to-S3/GCS upload.
   * Prevents large video binaries from loading the API server RAM.
   */
  static async createSignedUploadUrl(input: CreateUploadUrlInput) {
    const videoId = uuidv4();
    const extension = input.filename.split('.').pop() || 'mp4';
    const objectKey = `raw-uploads/${videoId}.${extension}`;

    const command = new PutObjectCommand({
      Bucket: config.s3.bucket,
      Key: objectKey,
      ContentType: input.contentType || 'video/mp4',
      Metadata: {
        userId: input.userId,
        videoId: videoId,
      },
    });

    // 15-minute expiration window for client upload
    const uploadUrl = await getSignedUrl(s3Client, command, { expiresIn: 900 });

    return {
      videoId,
      objectKey,
      uploadUrl,
      expectedStreamUrl: `${config.cdn.baseUrl}/hls/${videoId}/master.m3u8`,
      expectedThumbnailUrl: `${config.cdn.baseUrl}/thumbnails/${videoId}.jpg`,
    };
  }

  /**
   * Enqueues an uploaded video for FFmpeg HLS transcoding & quality scaling
   */
  static async enqueueProcessing(videoId: string, rawKey: string) {
    await videoQueue.add('transcode-video', {
      videoId,
      rawKey,
      resolutions: ['1080p', '720p', '480p', '360p'],
    });

    console.log(`🎬 Video ${videoId} dispatched to FFmpeg Transcoding Worker`);
  }

  /**
   * Fast Redis-cached view counter to absorb high viral traffic without locking DB rows
   */
  static async recordView(videoId: string, userId?: string, ipAddress?: string) {
    const viewKey = `views:${videoId}`;
    const dedupeKey = `viewed:${videoId}:${userId || ipAddress || 'anon'}`;

    // Throttle duplicate views from same user within 60 seconds
    const alreadyViewed = await redis.get(dedupeKey);
    if (!alreadyViewed) {
      await redis.set(dedupeKey, '1', 'EX', 60);
      const count = await redis.incr(viewKey);
      return { counted: true, totalViews: count };
    }

    return { counted: false };
  }
}
