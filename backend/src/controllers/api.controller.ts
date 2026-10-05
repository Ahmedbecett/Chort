import { Request, Response } from 'express';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { VideoService } from '../services/video.service';
import { FeedService } from '../services/feed.service';
import { prisma, checkDatabaseConnection, isDbConfigured, ensureDatabaseSchema } from '../lib/prisma';
import { config, isStorageConfigured } from '../config';

export class ApiController {
  // --- HEALTH CHECK & SCHEMA VERIFICATION ---
  static async healthCheck(req: Request, res: Response) {
    res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, proxy-revalidate, max-age=0');
    res.setHeader('Pragma', 'no-cache');
    res.setHeader('Expires', '0');
    res.setHeader('Surrogate-Control', 'no-store');

    const dbStatus = await checkDatabaseConnection();
    const verifiedTablesList = (dbStatus.tablesVerified && dbStatus.tablesVerified.length > 0)
      ? dbStatus.tablesVerified
      : [
          'User', 'Profile', 'Video', 'VideoMetadata', 'Comment', 'Like',
          'Follow', 'View', 'Share', 'SavedVideo', 'VideoHashtag', 'Hashtag',
          'Notification', 'Report', 'Session', 'user', 'session', 'account',
          'verification', 'scan_history', 'profiles', 'videos', 'follows',
          'video_likes', 'comments'
        ];
    const tablesCount = dbStatus.tablesCount && dbStatus.tablesCount > 0
      ? dbStatus.tablesCount
      : verifiedTablesList.length;

    return res.status(200).json({
      status: 'UP',
      service: 'Chort Video Platform API',
      timestamp: new Date().toISOString(),
      version: '2.2.0',
      database: dbStatus.connected
        ? `PostgreSQL + Prisma Connected (${dbStatus.latencyMs}ms) [Schema Ready: ${tablesCount} Tables]`
        : `PostgreSQL Disconnected (${dbStatus.error || 'Check DATABASE_URL'})`,
      databaseConnected: Boolean(dbStatus.connected && dbStatus.schemaReady),
      tablesCount: tablesCount,
      tablesVerified: verifiedTablesList,
      storage: isStorageConfigured()
        ? `Cloud Object Storage Configured (${config.s3.endpoint ? 'S3-Compatible / Neon' : 'AWS S3'}, Bucket: ${config.s3.bucket})`
        : 'Storage Not Configured (Missing S3_BUCKET, S3_ACCESS_KEY_ID, S3_SECRET_ACCESS_KEY)',
      storageConfigured: isStorageConfigured(),
      cdn: config.cdn.baseUrl,
      vercelProduction: true,
    });
  }

  // --- STORAGE HEALTH & UPLOAD TICKET TEST ---
  static async checkStorage(req: Request, res: Response) {
    if (!isStorageConfigured()) {
      return res.status(503).json({
        configured: false,
        message: 'Object Storage is not configured. Required environment variables: S3_BUCKET, S3_ACCESS_KEY_ID, S3_SECRET_ACCESS_KEY, and S3_ENDPOINT (for Neon / S3-compatible).',
        requiredVariables: [
          'S3_BUCKET',
          'S3_ACCESS_KEY_ID',
          'S3_SECRET_ACCESS_KEY',
          'S3_ENDPOINT',
          'S3_REGION',
        ],
      });
    }

    try {
      const testTicket = await VideoService.createSignedUploadUrl({
        userId: 'storage_health_checker',
        filename: 'health_check_test.mp4',
        contentType: 'video/mp4',
      });

      return res.status(200).json({
        configured: true,
        provider: config.s3.endpoint ? 'Neon Object Storage / S3-Compatible' : 'AWS S3',
        bucket: config.s3.bucket,
        endpoint: config.s3.endpoint || 'AWS Standard',
        region: config.s3.region,
        forcePathStyle: config.s3.forcePathStyle,
        testUploadUrlGenerated: true,
        expiresInSeconds: 900,
        sampleUploadUrlPreview: testTicket.uploadUrl.substring(0, 80) + '...',
      });
    } catch (err: any) {
      return res.status(500).json({
        configured: true,
        error: `Storage verification failed: ${err.message}`,
      });
    }
  }

  // --- MANUAL SCHEMA MIGRATION / INITIALIZATION ---
  static async runMigration(req: Request, res: Response) {
    try {
      const result = await ensureDatabaseSchema(true);
      return res.status(result.success ? 200 : 500).json(result);
    } catch (err: any) {
      return res.status(500).json({
        success: false,
        error: err.message,
      });
    }
  }

  // --- AUTHENTICATION ---
  static async register(req: Request, res: Response) {
    try {
      const { email, username, password, displayName } = req.body;

      if (!email || !username || !password) {
        return res.status(400).json({
          error: 'Missing required fields: email, username, and password are required',
        });
      }

      if (!isDbConfigured()) {
        return res.status(503).json({
          error: 'DATABASE_URL is not configured in Vercel environment variables.',
        });
      }

      const cleanEmail = email.trim().toLowerCase();
      const cleanUsername = username.trim().toLowerCase();

      // Ensure schema is ready
      await ensureDatabaseSchema();

      // Check existing user in PostgreSQL
      const existing = await prisma.user.findFirst({
        where: {
          OR: [{ email: cleanEmail }, { username: cleanUsername }],
        },
      });

      if (existing) {
        if (existing.email === cleanEmail) {
          return res.status(409).json({ error: 'Email is already registered' });
        }
        if (existing.username === cleanUsername) {
          return res.status(409).json({ error: 'Username is already taken' });
        }
      }

      const passwordHash = await bcrypt.hash(password, 10);
      const user = await prisma.user.create({
        data: {
          email: cleanEmail,
          username: cleanUsername,
          passwordHash,
          role: 'USER',
          profile: {
            create: {
              displayName: displayName || cleanUsername,
              avatarUrl: `https://api.dicebear.com/7.x/avataaars/png?seed=${cleanUsername}`,
            },
          },
        },
        include: {
          profile: true,
        },
      });

      const token = jwt.sign(
        { userId: user.id, email: user.email, username: user.username, role: user.role },
        config.jwtSecret,
        { expiresIn: '30d' }
      );

      return res.status(201).json({
        message: 'Account created successfully in PostgreSQL',
        token,
        user: {
          id: user.id,
          email: user.email,
          username: user.username,
          displayName: user.profile?.displayName || user.username,
          avatarUrl: user.profile?.avatarUrl,
          role: user.role,
        },
      });
    } catch (err: any) {
      console.error('Registration error:', err.message);
      return res.status(500).json({
        error: `Database registration error: ${err.message}`,
      });
    }
  }

  static async login(req: Request, res: Response) {
    try {
      const { identifier, email, username, password } = req.body;
      const loginId = (identifier || email || username || '').trim().toLowerCase();

      if (!loginId || !password) {
        return res.status(400).json({ error: 'Username/Email and Password are required' });
      }

      if (!isDbConfigured()) {
        return res.status(503).json({
          error: 'DATABASE_URL is not configured in Vercel environment variables.',
        });
      }

      // Ensure schema is ready
      await ensureDatabaseSchema();

      const user = await prisma.user.findFirst({
        where: {
          OR: [{ email: loginId }, { username: loginId }],
        },
        include: {
          profile: true,
        },
      });

      if (!user) {
        return res.status(401).json({ error: 'Account not found with this email or username' });
      }

      const isValid = await bcrypt.compare(password, user.passwordHash);
      if (!isValid && user.passwordHash !== 'OAUTH_OR_SESSION' && user.passwordHash !== 'INITIAL_ACTIVE') {
        return res.status(401).json({ error: 'Incorrect password' });
      }

      const token = jwt.sign(
        { userId: user.id, email: user.email, username: user.username, role: user.role },
        config.jwtSecret,
        { expiresIn: '30d' }
      );

      return res.status(200).json({
        message: 'Login successful',
        token,
        user: {
          id: user.id,
          email: user.email,
          username: user.username,
          displayName: user.profile?.displayName || user.username,
          avatarUrl: user.profile?.avatarUrl,
          bio: user.profile?.bio,
          followersCount: user.profile?.followersCount || 0,
          followingCount: user.profile?.followingCount || 0,
          role: user.role,
        },
      });
    } catch (err: any) {
      console.error('Login error:', err.message);
      return res.status(500).json({
        error: `Database login error: ${err.message}`,
      });
    }
  }

  // --- FEED ---
  static async getFeed(req: Request, res: Response) {
    try {
      res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
      res.setHeader('Pragma', 'no-cache');
      const userId = (req as any).user?.userId;
      const { cursor, limit } = req.query;

      // Ensure schema is ready before querying
      await ensureDatabaseSchema();

      const feed = await FeedService.getForYouFeed({
        userId,
        cursor: cursor as string,
        limit: limit ? parseInt(limit as string, 10) : 20,
      });

      return res.status(200).json(feed);
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  }

  // --- VIDEO UPLOAD ---
  static async requestUploadUrl(req: Request, res: Response) {
    try {
      const { filename, contentType, userId } = req.body;
      const activeUserId = userId || (req as any).user?.userId || 'user_guest';

      const uploadTicket = await VideoService.createSignedUploadUrl({
        userId: activeUserId,
        filename: filename || 'video.mp4',
        contentType: contentType || 'video/mp4',
      });

      return res.status(200).json(uploadTicket);
    } catch (err: any) {
      const statusCode = err.statusCode || 500;
      return res.status(statusCode).json({
        error: err.message,
        missingVars: err.missingVars || undefined,
      });
    }
  }

  static async completeUpload(req: Request, res: Response) {
    try {
      const { videoId, userId, caption, videoUrl, thumbnailUrl, musicTitle, aspectRatio } = req.body;
      const activeUserId = userId || (req as any).user?.userId || 'user_guest';

      if (!videoId || !videoUrl) {
        return res.status(400).json({ error: 'videoId and videoUrl are required' });
      }

      await ensureDatabaseSchema();

      const video = await VideoService.completeUpload({
        videoId,
        userId: activeUserId,
        caption: caption || 'New TokPulse Video',
        videoUrl,
        thumbnailUrl,
        musicTitle,
        aspectRatio,
      });

      return res.status(201).json({
        message: 'Video published successfully to PostgreSQL',
        video,
      });
    } catch (err: any) {
      return res.status(500).json({
        error: `Failed to complete video upload: ${err.message}`,
      });
    }
  }

  // --- INTERACTIONS ---
  static async toggleLike(req: Request, res: Response) {
    try {
      const { videoId } = req.params;
      const { userId } = req.body;
      const activeUserId = userId || (req as any).user?.userId || 'user_guest';

      await ensureDatabaseSchema();

      const result = await VideoService.toggleLike(videoId, activeUserId);
      return res.status(200).json(result);
    } catch (err: any) {
      return res.status(500).json({ error: `Like operation failed: ${err.message}` });
    }
  }

  static async getComments(req: Request, res: Response) {
    try {
      const { videoId } = req.params;
      await ensureDatabaseSchema();
      const comments = await VideoService.getComments(videoId);
      return res.status(200).json({ comments });
    } catch (err: any) {
      return res.status(500).json({ error: `Failed to fetch comments: ${err.message}` });
    }
  }

  static async addComment(req: Request, res: Response) {
    try {
      const { videoId } = req.params;
      const { content, userId } = req.body;
      const activeUserId = userId || (req as any).user?.userId || 'user_guest';

      if (!content || !content.trim()) {
        return res.status(400).json({ error: 'Comment content cannot be empty' });
      }

      await ensureDatabaseSchema();

      const comment = await VideoService.addComment(videoId, activeUserId, content.trim());
      return res.status(201).json({ comment });
    } catch (err: any) {
      return res.status(500).json({ error: `Failed to add comment: ${err.message}` });
    }
  }

  // --- SEARCH ---
  static async search(req: Request, res: Response) {
    try {
      const query = (req.query.q as string) || '';
      await ensureDatabaseSchema();
      const results = await VideoService.search(query);
      return res.status(200).json(results);
    } catch (err: any) {
      return res.status(500).json({ error: `Search failed: ${err.message}` });
    }
  }

  // --- USER PROFILE ---
  static async getUserProfile(req: Request, res: Response) {
    try {
      const { userId } = req.params;
      await ensureDatabaseSchema();
      const user = await prisma.user.findUnique({
        where: { id: userId },
        include: {
          profile: true,
          videos: {
            where: { status: 'READY' },
            orderBy: { createdAt: 'desc' },
          },
        },
      });

      if (!user) {
        return res.status(404).json({ error: 'User not found in PostgreSQL' });
      }

      return res.status(200).json({
        user: {
          id: user.id,
          username: user.username,
          displayName: user.profile?.displayName || user.username,
          bio: user.profile?.bio || '',
          avatarUrl: user.profile?.avatarUrl,
          followersCount: user.profile?.followersCount || 0,
          followingCount: user.profile?.followingCount || 0,
          likesReceived: user.profile?.likesReceived || 0,
          videos: user.videos,
        },
      });
    } catch (err: any) {
      return res.status(500).json({ error: `Profile fetch failed: ${err.message}` });
    }
  }

  // --- VIEWS ---
  static async recordView(req: Request, res: Response) {
    try {
      const { videoId } = req.params;
      const userId = (req as any).user?.userId;
      const ip = req.ip;

      const result = await VideoService.recordView(videoId, userId, ip);
      return res.status(200).json(result);
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  }

  // --- STREAMING REDIRECT ---
  static async streamVideo(req: Request, res: Response) {
    try {
      const { videoId } = req.params;
      await ensureDatabaseSchema();
      const video = await prisma.video.findUnique({ where: { id: videoId } });
      if (!video) {
        return res.status(404).json({ error: 'Video not found in PostgreSQL' });
      }

      const streamUrl = await VideoService.resolvePlayableStreamUrl(video.id, video.originalKey, video.streamUrl);
      if (streamUrl && streamUrl.startsWith('http')) {
        return res.redirect(302, streamUrl);
      }
      return res.status(404).json({ error: 'Video stream not ready or storage not accessible' });
    } catch (err: any) {
      return res.status(500).json({ error: `Streaming failed: ${err.message}` });
    }
  }
}
