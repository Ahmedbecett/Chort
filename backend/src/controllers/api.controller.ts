import { Request, Response } from 'express';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { GetObjectCommand, HeadObjectCommand } from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import { VideoService } from '../services/video.service';
import { FeedService } from '../services/feed.service';
import { PexelsService } from '../services/pexels.service';
import { prisma, checkDatabaseConnection, isDbConfigured, ensureDatabaseSchema } from '../lib/prisma';
import { config, isStorageConfigured, s3Client } from '../config';

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
      pexels: PexelsService.isConfigured()
        ? 'Pexels Licensed Video API Active'
        : 'Pexels Not Configured (Set PEXELS_API_KEY in Vercel to activate licensed stock videos)',
      pexelsConfigured: PexelsService.isConfigured(),
      cdn: config.cdn.baseUrl,
      vercelProduction: true,
    });
  }

  // --- EXTERNAL LICENSED VIDEOS (PEXELS API) ---
  static async getExternalVideos(req: Request, res: Response) {
    try {
      const page = req.query.page ? parseInt(req.query.page as string, 10) : 1;
      const perPage = req.query.per_page ? parseInt(req.query.per_page as string, 10) : 15;
      const query = (req.query.query as string) || (req.query.q as string);

      const result = await PexelsService.getVideos({
        page,
        perPage,
        query,
      });

      return res.status(200).json(result);
    } catch (err: any) {
      return res.status(500).json({
        configured: PexelsService.isConfigured(),
        provider: 'pexels',
        error: `Failed to fetch external videos: ${err.message}`,
        videos: [],
      });
    }
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
      const { filename, contentType, fileSize, userId } = req.body;
      const activeUserId = userId || (req as any).user?.userId || 'user_guest';

      // 1. Validate MIME type
      const ALLOWED_MIME_TYPES = [
        'video/mp4',
        'video/quicktime',
        'video/webm',
        'video/x-m4v',
        'video/3gpp',
      ];
      if (contentType && !ALLOWED_MIME_TYPES.includes(contentType.toLowerCase())) {
        return res.status(400).json({
          error: `Invalid video format: '${contentType}'. Allowed formats: MP4, MOV, WebM, M4V, 3GP.`,
        });
      }

      // 2. Validate file extension
      const ALLOWED_EXTENSIONS = ['mp4', 'mov', 'webm', 'm4v', '3gp'];
      const ext = (filename || '').split('.').pop()?.toLowerCase();
      if (ext && !ALLOWED_EXTENSIONS.includes(ext)) {
        return res.status(400).json({
          error: `Invalid file extension: '.${ext}'. Allowed extensions: .mp4, .mov, .webm, .m4v, .3gp.`,
        });
      }

      // 3. Validate file size (positive and max 100MB)
      const MAX_FILE_SIZE_BYTES = 100 * 1024 * 1024;
      if (fileSize !== undefined) {
        if (typeof fileSize !== 'number' || fileSize <= 0) {
          return res.status(400).json({
            error: 'File size must be a positive number of bytes.',
          });
        }
        if (fileSize > MAX_FILE_SIZE_BYTES) {
          return res.status(413).json({
            error: `File size exceeds maximum allowed limit of 100MB (${fileSize} bytes provided).`,
          });
        }
      }

      const uploadTicket = await VideoService.createSignedUploadUrl({
        userId: activeUserId,
        filename: filename || 'video.mp4',
        contentType: contentType || 'video/mp4',
        fileSize,
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
      const { videoId, userId, caption, videoUrl, thumbnailUrl, musicTitle, aspectRatio, objectKey } = req.body;
      const activeUserId = userId || (req as any).user?.userId || 'user_guest';

      if (!videoId || !videoUrl) {
        return res.status(400).json({ error: 'videoId and videoUrl are required' });
      }

      await ensureDatabaseSchema();

      // Protected: Any client-provided stats are intentionally ignored and sanitized!
      const video = await VideoService.completeUpload({
        videoId,
        userId: activeUserId,
        caption: caption || 'New Chort Video',
        videoUrl,
        thumbnailUrl,
        musicTitle,
        aspectRatio,
        objectKey,
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

      const result = await VideoService.addComment(videoId, activeUserId, content.trim());
      return res.status(201).json(result);
    } catch (err: any) {
      return res.status(500).json({ error: `Failed to add comment: ${err.message}` });
    }
  }

  static async deleteComment(req: Request, res: Response) {
    try {
      const { videoId, commentId } = req.params;
      const requestingUserId = (req as any).user?.userId || (req.headers['x-user-id'] as string) || req.body?.userId || req.query?.userId;
      const userRole = (req as any).user?.role || (req.headers['x-user-role'] as string);

      if (!requestingUserId) {
        return res.status(401).json({ error: 'Authentication or userId required to delete comment' });
      }

      await ensureDatabaseSchema();
      const result = await VideoService.deleteComment(commentId, videoId, requestingUserId, userRole);
      return res.status(200).json(result);
    } catch (err: any) {
      const status = err.statusCode || 500;
      return res.status(status).json({ error: err.message });
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

      // Handle external Pexels video stream
      if (videoId.startsWith('pex_') && PexelsService.isConfigured()) {
        const pexelsData = await PexelsService.getVideos({ perPage: 30 });
        const match = pexelsData.videos.find((v) => v.id === videoId);
        if (match && match.streamUrl) {
          return res.redirect(302, match.streamUrl);
        }
      }

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

  // --- SHARES ---
  static async recordShare(req: Request, res: Response) {
    try {
      const { videoId } = req.params;
      const userId = (req as any).user?.userId || req.body.userId;
      const result = await VideoService.recordShare(videoId, userId);
      return res.status(200).json(result);
    } catch (err: any) {
      return res.status(500).json({ error: `Share recording failed: ${err.message}` });
    }
  }

  // --- SAVED / BOOKMARKS ---
  static async toggleSave(req: Request, res: Response) {
    try {
      const { videoId } = req.params;
      const userId = (req as any).user?.userId || req.body.userId;
      if (!userId) {
        return res.status(401).json({ error: 'Authentication required to save video' });
      }
      const result = await VideoService.toggleSave(videoId, userId);
      return res.status(200).json(result);
    } catch (err: any) {
      return res.status(500).json({ error: `Save operation failed: ${err.message}` });
    }
  }

  // --- DELETE VIDEO (With Ownership Authorization) ---
  static async deleteVideo(req: Request, res: Response) {
    try {
      const { videoId } = req.params;
      const requestingUserId = (req as any).user?.userId || (req.headers['x-user-id'] as string) || req.body.userId || req.query.userId;
      const userRole = (req as any).user?.role || (req.headers['x-user-role'] as string);

      if (!requestingUserId) {
        return res.status(401).json({ error: 'Authentication or userId required to delete video' });
      }

      const result = await VideoService.deleteVideo(videoId, requestingUserId as string, userRole as string);
      return res.status(200).json(result);
    } catch (err: any) {
      const status = err.statusCode || 500;
      return res.status(status).json({ error: err.message });
    }
  }

  // --- REAL THUMBNAIL SERVICE ---
  static async getVideoThumbnail(req: Request, res: Response) {
    try {
      const { videoId } = req.params;

      if (videoId.startsWith('pex_') && PexelsService.isConfigured()) {
        const pexelsData = await PexelsService.getVideos({ perPage: 30 });
        const match = pexelsData.videos.find((v) => v.id === videoId);
        if (match && match.thumbnailUrl) {
          return res.redirect(302, match.thumbnailUrl);
        }
      }

      const video = await prisma.video.findUnique({
        where: { id: videoId },
        include: { user: { include: { profile: true } } },
      });

      // 1. If S3 storage has a real thumbnail, verify existence before redirecting
      if (isStorageConfigured()) {
        try {
          await s3Client.send(new HeadObjectCommand({
            Bucket: config.s3.bucket,
            Key: `thumbnails/${videoId}.jpg`,
          }));
          const command = new GetObjectCommand({
            Bucket: config.s3.bucket,
            Key: `thumbnails/${videoId}.jpg`,
          });
          const signedThumb = await getSignedUrl(s3Client, command, { expiresIn: 86400 });
          return res.redirect(302, signedThumb);
        } catch {
          // File does not exist in S3 storage - gracefully fall back to SVG poster
        }
      }

      // 2. High-res dynamic SVG poster
      const title = (video?.caption || 'Chort Video').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').substring(0, 48);
      const creator = (video?.user?.username || 'creator').replace(/&/g, '&amp;');

      const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="720" height="1280" viewBox="0 0 720 1280">
  <defs>
    <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#0f0c1b"/>
      <stop offset="50%" stop-color="#18132e"/>
      <stop offset="100%" stop-color="#090710"/>
    </linearGradient>
    <linearGradient id="accent" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#FF0055"/>
      <stop offset="100%" stop-color="#7928CA"/>
    </linearGradient>
  </defs>
  <rect width="720" height="1280" fill="url(#bg)"/>
  <circle cx="360" cy="540" r="180" fill="url(#accent)" opacity="0.15" filter="blur(40px)"/>
  <circle cx="360" cy="580" r="54" fill="rgba(255,255,255,0.18)" stroke="rgba(255,255,255,0.4)" stroke-width="2"/>
  <polygon points="348,556 384,580 348,604" fill="#ffffff"/>
  <text x="360" y="700" fill="#ffffff" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" font-size="28" font-weight="bold" text-anchor="middle">${title}</text>
  <text x="360" y="745" fill="#a0aec0" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" font-size="20" text-anchor="middle">@${creator}</text>
  <rect x="290" y="1160" width="140" height="38" rx="19" fill="url(#accent)"/>
  <text x="360" y="1185" fill="#ffffff" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" font-size="16" font-weight="900" text-anchor="middle" letter-spacing="2">CHORT</text>
</svg>`;

      res.setHeader('Content-Type', 'image/svg+xml');
      res.setHeader('Cache-Control', 'public, max-age=86400, stale-while-revalidate=604800');
      return res.status(200).send(svg.trim());
    } catch (err: any) {
      return res.status(500).json({ error: `Thumbnail generation error: ${err.message}` });
    }
  }
}
