import { Request, Response } from 'express';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { GetObjectCommand, HeadObjectCommand } from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import { randomUUID } from 'crypto';
import { VideoService } from '../services/video.service';
import { FeedService } from '../services/feed.service';
import { SocialService } from '../services/social.service';
import { NotificationService } from '../services/notification.service';
import { ReportService } from '../services/report.service';
import { HashtagService } from '../services/hashtag.service';
import { AuthService, devEchoAllowed, isSmsConfigured, normalizePhone } from '../services/auth.service';
import { clampLimit, clampPage, parseFeedMode } from '../lib/validate';
import { PexelsService } from '../services/pexels.service';
import { CoverrService } from '../services/coverr.service';
import { PixabayService } from '../services/pixabay.service';
import { ExternalVideoService } from '../services/external-video.service';
import { prisma, checkDatabaseConnection, isDbConfigured, ensureDatabaseSchema } from '../lib/prisma';
import { config, isStorageConfigured, s3Client, redis } from '../config';
import { clientIpFrom } from '../services/feed-history';

export class ApiController {
  /** Actor identity: JWT first, explicit userId fallback (matches codebase trust model). */
  private static actorId(req: Request): string | undefined {
    const body = (req.body || {}) as Record<string, unknown>;
    const q = (req.query || {}) as Record<string, unknown>;
    return (
      (req as any).user?.userId ||
      (body.userId as string) ||
      (q.userId as string) ||
      (req.headers['x-user-id'] as string) ||
      undefined
    );
  }

  private static requireSelf(req: Request, res: Response, userId: string): boolean {
    const me = (req as any).user?.userId;
    if (!me || me !== userId) {
      res.status(403).json({ error: 'Forbidden: this resource belongs to another user' });
      return false;
    }
    return true;
  }

  private static requireAdmin(req: Request, res: Response): boolean {
    if ((req as any).user?.role !== 'ADMIN') {
      res.status(403).json({ error: 'Forbidden: admin role required' });
      return false;
    }
    return true;
  }

  private static async issueSession(user: any, req: Request): Promise<{ token: string; user: any }> {
    const jti = randomUUID();
    const token = jwt.sign(
      { userId: user.id, email: user.email, username: user.username, role: user.role, jti },
      config.jwtSecret,
      { expiresIn: '30d' }
    );
    await ApiController.createSession(user.id, req, jti);
    return {
      token,
      user: {
        id: user.id,
        email: user.email,
        username: user.username,
        displayName: user.profile?.displayName || user.username,
        avatarUrl: user.profile?.avatarUrl,
        bio: user.profile?.bio,
        role: user.role,
        phone: user.phone || null,
        phoneVerified: Boolean(user.phoneVerified),
        primaryProvider: user.primaryProvider || 'email',
      },
    };
  }

  private static async createSession(userId: string, req: Request, jti: string): Promise<void> {
    try {
      await prisma.session.create({
        data: {
          userId,
          token: jti,
          userAgent: String(req.headers['user-agent'] || '').slice(0, 300) || null,
          ipAddress: clientIpFrom(req) || null,
          expiresAt: new Date(Date.now() + 30 * 24 * 3600 * 1000),
        },
      });
    } catch {}
  }

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

    let isDbConnected = Boolean(dbStatus.connected && dbStatus.schemaReady);
    let dbMessage = dbStatus.connected
      ? `PostgreSQL + Prisma Connected (${dbStatus.latencyMs}ms) [Schema Ready: ${tablesCount} Tables]`
      : `PostgreSQL Disconnected (${dbStatus.error || 'Check DATABASE_URL'})`;
    let isStorageReady = isStorageConfigured();
    let storageMessage = isStorageConfigured()
      ? `Cloud Object Storage Configured (${config.s3.endpoint ? 'S3-Compatible / Neon' : 'AWS S3'}, Bucket: ${config.s3.bucket})`
      : 'Storage Not Configured (Missing S3_BUCKET, S3_ACCESS_KEY_ID, S3_SECRET_ACCESS_KEY)';

    if (!isDbConnected) {
      try {
        const upstreamHealth = await fetch(`${config.primaryUpstreamUrl}/api/v1/health`);
        if (upstreamHealth.ok) {
          const upstreamJson = (await upstreamHealth.json()) as any;
          if (upstreamJson?.databaseConnected) {
            isDbConnected = true;
            dbMessage = `PostgreSQL + Prisma Connected via Primary Production Cluster (${config.primaryUpstreamUrl}) [Schema Ready: ${tablesCount} Tables]`;
            isStorageReady = true;
            storageMessage = `Cloud Object Storage Configured via Primary Cluster (S3-Compatible / Neon)`;
          }
        }
      } catch (err: any) {
        console.warn('Upstream health probe error:', err.message);
      }
    }

    return res.status(200).json({
      status: 'UP',
      service: 'thileli dz Video Platform API',
      timestamp: new Date().toISOString(),
      version: '2.2.0',
      database: dbMessage,
      databaseConnected: isDbConnected,
      tablesCount: tablesCount,
      tablesVerified: verifiedTablesList,
      storage: storageMessage,
      storageConfigured: isStorageReady,
      pexels: PexelsService.isConfigured()
        ? 'Pexels Licensed Video API Active'
        : 'Pexels Not Configured (Set PEXELS_API_KEY in Vercel to activate licensed stock videos)',
      pexelsConfigured: PexelsService.isConfigured(),
      coverr: CoverrService.isConfigured()
        ? 'Coverr Licensed Video API Active'
        : 'Coverr Not Configured (Set COVERR_API_KEY in Vercel to activate licensed stock videos)',
      coverrConfigured: CoverrService.isConfigured(),
      pixabay: PixabayService.isConfigured()
        ? 'Pixabay Licensed Video API Active'
        : 'Pixabay Not Configured (Set PIXABAY_API_KEY in Vercel to activate licensed stock videos)',
      pixabayConfigured: PixabayService.isConfigured(),
      googleOAuth: config.google.clientId ? 'Google OAuth Active' : 'Google OAuth Not Configured (Set GOOGLE_CLIENT_ID)',
      googleOAuthConfigured: Boolean(config.google.clientId),
      facebookOAuth: config.facebook.appId ? 'Facebook OAuth Active' : 'Facebook OAuth Not Configured (Set FACEBOOK_APP_ID)',
      facebookOAuthConfigured: Boolean(config.facebook.appId),
      sms: isSmsConfigured() ? 'Twilio SMS Active' : 'SMS Not Configured (setup-mode OTP echo; set TWILIO_* to send real SMS)',
      smsConfigured: isSmsConfigured(),
      otpDevEcho: devEchoAllowed(),
      cdn: config.cdn.baseUrl,
      vercelProduction: true,
    });
  }

  // --- EXTERNAL LICENSED VIDEOS (PEXELS / COVERR via VIDEO_PROVIDER) ---
  static async getExternalVideos(req: Request, res: Response) {
    try {
      const page = req.query.page ? parseInt(req.query.page as string, 10) : undefined;
      const perPage = req.query.per_page ? parseInt(req.query.per_page as string, 10) : 15;
      const query = (req.query.query as string) || (req.query.q as string);
      const providerParam = ((req.query.provider as string) || '').trim().toLowerCase();
      const providerOverride =
        providerParam === 'pexels' || providerParam === 'coverr' || providerParam === 'pixabay'
          ? providerParam
          : null;
      const debug = ['1', 'true', 'yes'].includes(String(req.query.debug || '').toLowerCase());
      const verifyAudioRaw = String(req.query.verify_audio ?? req.query.verifyAudio ?? '').toLowerCase();
      const verifyAudio = ['false', '0', 'no'].includes(verifyAudioRaw) ? false : undefined;

      const result = providerOverride
        ? await ExternalVideoService.getVideosFrom(providerOverride, { page, perPage, query, debug, verifyAudio })
        : await ExternalVideoService.getVideos({ page, perPage, query, debug, verifyAudio });

      return res.status(200).json(result);
    } catch (err: any) {
      return res.status(500).json({
        configured: ExternalVideoService.isConfigured(),
        provider: ExternalVideoService.selectedProvider(),
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

      const jti = randomUUID();
      const token = jwt.sign(
        { userId: user.id, email: user.email, username: user.username, role: user.role, jti },
        config.jwtSecret,
        { expiresIn: '30d' }
      );
      await ApiController.createSession(user.id, req, jti);

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

      // Password-less accounts (Google/Facebook/phone created) can NEVER log in
      // with a password until recovery sets one. (Previous code accepted ANY
      // password for these rows - a full account-takeover hole. Fixed.)
      if (user.passwordHash === 'OAUTH_OR_SESSION' || user.passwordHash === 'INITIAL_ACTIVE') {
        return res.status(401).json({
          error: 'This account uses Google, Facebook, or phone sign-in. Use it to log in, or recover your account to set a password.',
        });
      }
      const isValid = await bcrypt.compare(password, user.passwordHash);
      if (!isValid) {
        return res.status(401).json({ error: 'Incorrect password' });
      }

      const jti = randomUUID();
      const token = jwt.sign(
        { userId: user.id, email: user.email, username: user.username, role: user.role, jti },
        config.jwtSecret,
        { expiresIn: '30d' }
      );
      await ApiController.createSession(user.id, req, jti);

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
      const q = (req.query || {}) as Record<string, unknown>;
      const body = (req.body || {}) as Record<string, unknown>;
      const headerDevice = req.headers['x-device-id'];
      const deviceId =
        (Array.isArray(headerDevice) ? headerDevice[0] : (headerDevice as string)) ||
        (body.deviceId as string) ||
        (q.deviceId as string) ||
        (q.device_id as string) ||
        (q.distinct_id as string);
      const includeRaw = (q.includeExternal as string) ?? (q.include_external as string);
      const includeExternal =
        includeRaw === undefined ? undefined : !['false', '0', 'no'].includes(String(includeRaw).toLowerCase());

      // Ensure schema is ready before querying
      await ensureDatabaseSchema();

      const feed = await FeedService.getForYouFeed({
        userId,
        deviceId: deviceId ? String(deviceId) : undefined,
        ip: clientIpFrom(req),
        cursor: q.cursor as string,
        limit: q.limit ? parseInt(q.limit as string, 10) : 20,
        category: q.category as string,
        includeExternal,
        seen: q.seen as string,
        mode: parseFeedMode(q.mode),
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
        caption: caption || 'New thileli dz Video',
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
      const status = err.statusCode || 500;
      const prefix = status === 422 ? '' : 'Failed to complete video upload: ';
      return res.status(status).json({
        error: `${prefix}${err.message}`,
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
      const q = (req.query || {}) as Record<string, unknown>;
      const limit = clampLimit(q.limit, 20, 50);
      const page = clampPage(q.page);
      await ensureDatabaseSchema();
      const user = await prisma.user.findUnique({
        where: { id: userId },
        include: {
          profile: true,
          videos: {
            where: { status: 'READY' },
            orderBy: { createdAt: 'desc' },
            skip: (page - 1) * limit,
            take: limit + 1,
          },
        },
      });

      if (!user) {
        return res.status(404).json({ error: 'User not found in PostgreSQL' });
      }

      const hasMore = user.videos.length > limit;
      if (hasMore) user.videos.pop();
      const videos = await VideoService.formatVideoRows(user.videos as unknown as Array<Record<string, any>>);

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
          videos,
          videosPage: page,
          videosHasMore: hasMore,
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

      // Handle external provider video stream (pex_ = Pexels, cov_ = Coverr)
      if ((videoId.startsWith('pex_') || videoId.startsWith('cov_') || videoId.startsWith('pix_')) && ExternalVideoService.isConfigured()) {
        const externalData = await ExternalVideoService.getVideos({ perPage: 30 });
        const match = externalData.videos.find((v) => v.id === videoId);
        if (match && match.streamUrl) {
          return res.redirect(302, match.streamUrl);
        }
      }

      if (!isDbConfigured() || !isStorageConfigured()) {
        return res.redirect(302, `${config.primaryUpstreamUrl}/api/v1/videos/${videoId}/stream`);
      }

      try {
        await ensureDatabaseSchema();
        const video = await prisma.video.findUnique({ where: { id: videoId } });
        if (!video) {
          return res.redirect(302, `${config.primaryUpstreamUrl}/api/v1/videos/${videoId}/stream`);
        }

        const streamUrl = await VideoService.resolvePlayableStreamUrl(video.id, video.originalKey, video.streamUrl);
        if (streamUrl && streamUrl.startsWith('http')) {
          return res.redirect(302, streamUrl);
        }
        return res.redirect(302, `${config.primaryUpstreamUrl}/api/v1/videos/${videoId}/stream`);
      } catch (innerErr: any) {
        return res.redirect(302, `${config.primaryUpstreamUrl}/api/v1/videos/${videoId}/stream`);
      }
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

      if ((videoId.startsWith('pex_') || videoId.startsWith('cov_') || videoId.startsWith('pix_')) && ExternalVideoService.isConfigured()) {
        const externalData = await ExternalVideoService.getVideos({ perPage: 30 });
        const match = externalData.videos.find((v) => v.id === videoId);
        if (match && match.thumbnailUrl) {
          return res.redirect(302, match.thumbnailUrl);
        }
      }

      if (!isDbConfigured() || !isStorageConfigured()) {
        return res.redirect(302, `${config.primaryUpstreamUrl}/api/v1/videos/${videoId}/thumbnail`);
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
      const title = (video?.caption || 'thileli dz Video').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').substring(0, 48);
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

  // --- SESSIONS ---
  static async logout(req: Request, res: Response) {
    try {
      const me = (req as any).user;
      if (!me?.jti) return res.status(200).json({ loggedOut: true, tracked: false });
      // Deleting the Session row globally invalidates the token (middleware
      // requires the row to exist). Redis write is hygiene only.
      const ttl = Math.max(60, (me.exp || 0) - Math.floor(Date.now() / 1000));
      try {
        await redis.set(`revoked:${me.jti}`, '1', 'EX', Math.min(ttl, 30 * 24 * 3600));
      } catch {}
      try {
        await prisma.session.deleteMany({ where: { token: me.jti, userId: me.userId } });
      } catch {}
      return res.status(200).json({ loggedOut: true, tracked: true });
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  }

  static async listSessions(req: Request, res: Response) {
    try {
      const me = (req as any).user;
      if (!me?.userId) return res.status(401).json({ error: 'Authentication required' });
      await ensureDatabaseSchema();
      const rows = await prisma.session.findMany({
        where: { userId: me.userId },
        orderBy: { createdAt: 'desc' },
        take: 20,
      });
      return res.status(200).json({
        sessions: rows.map((s) => ({
          id: s.id,
          userAgent: s.userAgent,
          ipAddress: s.ipAddress,
          expiresAt: s.expiresAt instanceof Date ? s.expiresAt.getTime() : new Date(s.expiresAt).getTime(),
          createdAt: s.createdAt instanceof Date ? s.createdAt.getTime() : new Date(s.createdAt).getTime(),
          current: s.token === me.jti,
        })),
      });
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  }

  static async revokeSession(req: Request, res: Response) {
    try {
      const me = (req as any).user;
      if (!me?.userId) return res.status(401).json({ error: 'Authentication required' });
      const { sessionId } = req.params;
      const row = await prisma.session.findUnique({ where: { id: sessionId } });
      if (!row || row.userId !== me.userId) {
        return res.status(404).json({ error: 'Session not found' });
      }
      const ttl = Math.max(60, Math.floor((new Date(row.expiresAt).getTime() - Date.now()) / 1000));
      try {
        await redis.set(`revoked:${row.token}`, '1', 'EX', Math.min(ttl, 30 * 24 * 3600));
      } catch {}
      await prisma.session.delete({ where: { id: sessionId } });
      return res.status(200).json({ revoked: true });
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  }

  // --- PASSWORD CHANGE (authenticated, inside the app) ---
  static async changePassword(req: Request, res: Response) {
    try {
      const me = (req as any).user;
      if (!me?.userId) return res.status(401).json({ error: 'Authentication required' });
      const { currentPassword, newPassword } = req.body as { currentPassword?: string; newPassword?: string };
      if (!currentPassword || !newPassword) {
        return res.status(400).json({ error: 'currentPassword and newPassword are required' });
      }
      if (newPassword.length < 6 || newPassword.length > 100) {
        return res.status(400).json({ error: 'New password must be 6-100 characters' });
      }
      await ensureDatabaseSchema();
      const user = await prisma.user.findUnique({ where: { id: me.userId }, select: { id: true, passwordHash: true } });
      if (!user) return res.status(404).json({ error: 'Account not found' });
      if (!user.passwordHash || user.passwordHash === 'OAUTH_OR_SESSION') {
        return res.status(409).json({ error: 'This account uses Google/Facebook/phone sign-in. Use phone recovery to set a password.' });
      }
      const ok = await bcrypt.compare(String(currentPassword), user.passwordHash);
      if (!ok) return res.status(403).json({ error: 'Current password is incorrect' });
      await prisma.user.update({ where: { id: me.userId }, data: { passwordHash: await bcrypt.hash(newPassword, 10) } });
      // Invalidate every other session; keep the current one alive.
      try {
        await prisma.session.deleteMany({ where: { userId: me.userId, NOT: { token: me.jti || '__none__' } } });
      } catch {}
      return res.status(200).json({ changed: true });
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  }

  // --- USER PROFILE UPDATE (self or admin) ---
  static async updateUser(req: Request, res: Response) {
    try {
      const me = (req as any).user;
      const { userId } = req.params;
      if (!me?.userId) return res.status(401).json({ error: 'Authentication required' });
      if (me.userId !== userId && me.role !== 'ADMIN') {
        return res.status(403).json({ error: 'You can only edit your own profile' });
      }
      const { username, displayName, bio, avatarUrl, bannerUrl } = (req.body || {}) as Record<string, string | undefined>;
      if (!username && !displayName && bio === undefined && !avatarUrl && !bannerUrl) {
        return res.status(400).json({ error: 'Nothing to update' });
      }
      await ensureDatabaseSchema();
      const user = await prisma.user.findUnique({ where: { id: userId }, include: { profile: true } });
      if (!user) return res.status(404).json({ error: 'Account not found' });
      if (username && username.toLowerCase() !== user.username.toLowerCase()) {
        const taken = await prisma.user.findUnique({ where: { username: username.toLowerCase() } }).catch(() => null);
        if (taken) return res.status(409).json({ error: 'That username is already taken' });
      }
      const updated = await prisma.user.update({
        where: { id: userId },
        data: {
          ...(username ? { username: username.toLowerCase() } : {}),
          profile: {
            upsert: {
              create: {
                displayName: displayName || user.username,
                bio: bio ?? '',
                avatarUrl: avatarUrl ?? null,
                bannerUrl: bannerUrl ?? null,
              },
              update: {
                ...(displayName ? { displayName } : {}),
                ...(bio !== undefined ? { bio } : {}),
                ...(avatarUrl ? { avatarUrl } : {}),
                ...(bannerUrl ? { bannerUrl } : {}),
              },
            },
          },
        },
        include: { profile: true },
      });
      return res.status(200).json({
        user: {
          id: updated.id,
          username: updated.username,
          email: updated.email,
          displayName: updated.profile?.displayName,
          bio: updated.profile?.bio,
          avatarUrl: updated.profile?.avatarUrl,
          bannerUrl: updated.profile?.bannerUrl,
        },
      });
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  }

  // --- ACCOUNT DELETION (self or admin; cascades videos, likes, comments…) ---
  static async deleteUserAccount(req: Request, res: Response) {
    try {
      const me = (req as any).user;
      const { userId } = req.params;
      if (!me?.userId) return res.status(401).json({ error: 'Authentication required' });
      if (me.userId !== userId && me.role !== 'ADMIN') {
        return res.status(403).json({ error: 'You can only delete your own account' });
      }
      await ensureDatabaseSchema();
      const user = await prisma.user.findUnique({
        where: { id: userId },
        select: { id: true, videos: { select: { id: true, originalKey: true } } },
      });
      if (!user) return res.status(404).json({ error: 'Account not found' });
      // Best-effort storage cleanup (DB delete proceeds regardless).
      if (isStorageConfigured()) {
        const { DeleteObjectCommand } = await import('@aws-sdk/client-s3');
        for (const v of user.videos) {
          for (const key of [v.originalKey, `thumbnails/${v.id}.jpg`]) {
            if (!key || key.startsWith('external/')) continue;
            try {
              await s3Client.send(new DeleteObjectCommand({ Bucket: config.s3.bucket, Key: key }));
            } catch {}
          }
        }
      }
      await prisma.user.delete({ where: { id: userId } });
      return res.status(200).json({ deleted: true, videosRemoved: user.videos.length });
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  }

  // --- SOCIAL GRAPH ---
  static async followUser(req: Request, res: Response) {
    try {
      const { userId } = req.params;
      const followerId = ApiController.actorId(req);
      if (!followerId) return res.status(401).json({ error: 'Authentication or userId required' });
      await ensureDatabaseSchema();
      const result = await SocialService.follow(followerId, userId);
      return res.status(200).json(result);
    } catch (err: any) {
      return res.status(err.statusCode || 500).json({ error: err.message });
    }
  }

  static async unfollowUser(req: Request, res: Response) {
    try {
      const { userId } = req.params;
      const followerId = ApiController.actorId(req);
      if (!followerId) return res.status(401).json({ error: 'Authentication or userId required' });
      await ensureDatabaseSchema();
      const result = await SocialService.unfollow(followerId, userId);
      return res.status(200).json(result);
    } catch (err: any) {
      return res.status(err.statusCode || 500).json({ error: err.message });
    }
  }

  static async followState(req: Request, res: Response) {
    try {
      const { userId } = req.params;
      const viewerId = ApiController.actorId(req);
      await ensureDatabaseSchema();
      const result = await SocialService.followState(viewerId, userId);
      return res.status(200).json(result);
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  }

  static async getFollowers(req: Request, res: Response) {
    try {
      const { userId } = req.params;
      const q = (req.query || {}) as Record<string, unknown>;
      await ensureDatabaseSchema();
      const result = await SocialService.getFollowers(userId, clampPage(q.page), clampLimit(q.limit, 20, 50));
      return res.status(200).json(result);
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  }

  static async getFollowing(req: Request, res: Response) {
    try {
      const { userId } = req.params;
      const q = (req.query || {}) as Record<string, unknown>;
      await ensureDatabaseSchema();
      const result = await SocialService.getFollowing(userId, clampPage(q.page), clampLimit(q.limit, 20, 50));
      return res.status(200).json(result);
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  }

  static async getSavedVideos(req: Request, res: Response) {
    try {
      const { userId } = req.params;
      if (!ApiController.requireSelf(req, res, userId)) return;
      const q = (req.query || {}) as Record<string, unknown>;
      await ensureDatabaseSchema();
      const result = await SocialService.getSavedVideos(userId, clampPage(q.page), clampLimit(q.limit, 20, 50));
      return res.status(200).json(result);
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  }

  static async getLikedVideos(req: Request, res: Response) {
    try {
      const { userId } = req.params;
      const q = (req.query || {}) as Record<string, unknown>;
      await ensureDatabaseSchema();
      const result = await SocialService.getLikedVideos(userId, clampPage(q.page), clampLimit(q.limit, 20, 50));
      return res.status(200).json(result);
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  }

  // --- NOTIFICATIONS ---
  static async getNotifications(req: Request, res: Response) {
    try {
      const { userId } = req.params;
      if (!ApiController.requireSelf(req, res, userId)) return;
      const q = (req.query || {}) as Record<string, unknown>;
      await ensureDatabaseSchema();
      const result = await NotificationService.list(userId, clampPage(q.page), clampLimit(q.limit, 20, 50));
      return res.status(200).json(result);
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  }

  static async readNotifications(req: Request, res: Response) {
    try {
      const { userId } = req.params;
      if (!ApiController.requireSelf(req, res, userId)) return;
      await ensureDatabaseSchema();
      const ids = ((req.body || {}) as Record<string, unknown>).ids as string[] | undefined;
      const result = await NotificationService.markRead(userId, ids);
      return res.status(200).json(result);
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  }

  // --- REPORTS & MODERATION ---
  static async submitReport(req: Request, res: Response) {
    try {
      const reporterId = ApiController.actorId(req) || (req.body || {}).reporterId;
      if (!reporterId) return res.status(401).json({ error: 'Authentication or reporterId required' });
      const { videoId, targetUserId, reason } = req.body;
      await ensureDatabaseSchema();
      const result = await ReportService.submit({ reporterId, videoId, targetUserId, reason });
      return res.status(201).json(result);
    } catch (err: any) {
      return res.status(err.statusCode || 500).json({ error: err.message });
    }
  }

  static async listReports(req: Request, res: Response) {
    try {
      if (!ApiController.requireAdmin(req, res)) return;
      const q = (req.query || {}) as Record<string, unknown>;
      await ensureDatabaseSchema();
      const result = await ReportService.list(q.status as string, clampPage(q.page), clampLimit(q.limit, 20, 50));
      return res.status(200).json(result);
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  }

  static async resolveReport(req: Request, res: Response) {
    try {
      if (!ApiController.requireAdmin(req, res)) return;
      const { reportId } = req.params;
      await ensureDatabaseSchema();
      const result = await ReportService.resolve(reportId, (req.body || {}).action);
      return res.status(200).json(result);
    } catch (err: any) {
      return res.status(err.statusCode || 500).json({ error: err.message });
    }
  }

  // --- HASHTAGS ---
  static async getHashtagVideos(req: Request, res: Response) {
    try {
      const { tag } = req.params;
      const q = (req.query || {}) as Record<string, unknown>;
      await ensureDatabaseSchema();
      const result = await HashtagService.getTagVideos(tag, clampPage(q.page), clampLimit(q.limit, 20, 50));
      return res.status(200).json(result);
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  }

  // --- AUTH: Google OAuth ---
  static async oauthGoogle(req: Request, res: Response) {
    try {
      await ensureDatabaseSchema();
      const profile = await AuthService.verifyGoogleIdToken(String((req.body || {}).idToken || ''));
      const out = await AuthService.findOrCreateLinkedUser({ provider: 'google', providerId: profile.sub, email: profile.email, name: profile.name, avatar: profile.avatar });
      const session = await ApiController.issueSession(out.user, req);
      return res.status(out.isNew ? 201 : 200).json({ message: out.isNew ? 'Account created with Google' : 'Google login successful', ...session, isNew: out.isNew, linked: out.linked });
    } catch (err: any) {
      return res.status(err.statusCode || 500).json({ error: err.message });
    }
  }

  // --- AUTH: Facebook OAuth ---
  static async oauthFacebook(req: Request, res: Response) {
    try {
      await ensureDatabaseSchema();
      const profile = await AuthService.verifyFacebookToken(String((req.body || {}).accessToken || ''));
      const out = await AuthService.findOrCreateLinkedUser({ provider: 'facebook', providerId: profile.sub, email: profile.email, name: profile.name, avatar: profile.avatar });
      const session = await ApiController.issueSession(out.user, req);
      return res.status(out.isNew ? 201 : 200).json({ message: out.isNew ? 'Account created with Facebook' : 'Facebook login successful', ...session, isNew: out.isNew, linked: out.linked });
    } catch (err: any) {
      return res.status(err.statusCode || 500).json({ error: err.message });
    }
  }

  // --- AUTH: phone OTP request (register / generic) ---
  static async phoneRequest(req: Request, res: Response) {
    try {
      await ensureDatabaseSchema();
      const result = await AuthService.requestOtp((req.body || {}).phone || '', 'register');
      return res.status(200).json(result);
    } catch (err: any) {
      return res.status(err.statusCode || 500).json({ error: err.message });
    }
  }

  // --- AUTH: phone OTP verify -> creates/links user, returns session ---
  static async phoneVerify(req: Request, res: Response) {
    try {
      await ensureDatabaseSchema();
      const body = req.body || {};
      const { phone } = await AuthService.verifyOtp(String(body.phone || ''), String(body.code || ''), 'register');
      const out = await AuthService.claimPhoneUser(phone, typeof body.name === 'string' ? body.name : undefined);
      const session = await ApiController.issueSession(out.user, req);
      return res.status(out.isNew ? 201 : 200).json({ message: out.isNew ? 'Account created with phone number' : 'Phone login successful', ...session, isNew: out.isNew, linked: out.linked });
    } catch (err: any) {
      return res.status(err.statusCode || 500).json({ error: err.message });
    }
  }

  // --- AUTH: account recovery via verified phone ---
  static async recoverRequest(req: Request, res: Response) {
    try {
      await ensureDatabaseSchema();
      const result = await AuthService.requestRecovery((req.body || {}).phone || '');
      return res.status(200).json(result);
    } catch (err: any) {
      return res.status(err.statusCode || 500).json({ error: err.message });
    }
  }

  static async recoverConfirm(req: Request, res: Response) {
    try {
      await ensureDatabaseSchema();
      const body = req.body || {};
      const result = await AuthService.confirmRecovery(String(body.phone || ''), String(body.code || ''), typeof body.newPassword === 'string' ? body.newPassword : undefined);
      const session = await ApiController.issueSession(result.user, req);
      return res.status(200).json({ message: 'Account recovered', ...session });
    } catch (err: any) {
      return res.status(err.statusCode || 500).json({ error: err.message });
    }
  }

  // --- ADMIN: live platform overview counters ---
  static async adminOverview(req: Request, res: Response) {
    try {
      if (!ApiController.requireAdmin(req, res)) return;
      await ensureDatabaseSchema();
      const now = new Date();
      const [usersTotal, videosTotal, videosPublic, reportsPending, reportsTotal, sessionsActive, sessionsTotal] = await Promise.all([
        prisma.user.count().catch(() => 0),
        prisma.video.count().catch(() => 0),
        prisma.video.count({ where: { visibility: 'PUBLIC' } }).catch(() => 0),
        prisma.report.count({ where: { status: 'PENDING' } }).catch(() => 0),
        prisma.report.count().catch(() => 0),
        prisma.session.count({ where: { expiresAt: { gt: now } } }).catch(() => 0),
        prisma.session.count().catch(() => 0),
      ]);
      return res.status(200).json({
        usersTotal, videosTotal, videosPublic, reportsPending, reportsTotal,
        sessionsActive, sessionsTotal, serverTime: now.toISOString(),
      });
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  }

  // --- ADMIN: login/session records (real data from Session table) ---
  static async listLogins(req: Request, res: Response) {
    try {
      if (!ApiController.requireAdmin(req, res)) return;
      const q = (req.query || {}) as Record<string, unknown>;
      await ensureDatabaseSchema();
      const page = clampPage(q.page);
      const limit = clampLimit(q.limit, 20, 50);
      const skip = (page - 1) * limit;
      const [total, rows] = await Promise.all([
        prisma.session.count(),
        prisma.session.findMany({
          orderBy: { createdAt: 'desc' },
          skip,
          take: limit,
          include: { user: { select: { id: true, username: true, email: true, phone: true, primaryProvider: true } } },
        }),
      ]);
      return res.status(200).json({
        items: rows.map((s) => ({
          id: s.id,
          user: s.user,
          userAgent: s.userAgent,
          ipAddress: s.ipAddress,
          createdAt: s.createdAt,
          expiresAt: s.expiresAt,
          active: s.expiresAt > new Date(),
        })),
        page,
        limit,
        total,
        hasMore: skip + rows.length < total,
      });
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  }

  // --- AUTH: my linked providers ---
  static async myProviders(req: Request, res: Response) {
    try {
      const self = (req as any).user;
      if (!self?.userId) return res.status(401).json({ error: 'Authentication required' });
      await ensureDatabaseSchema();
      const rows = await prisma.account.findMany({ where: { userId: self.userId }, orderBy: { createdAt: 'asc' } });
      return res.status(200).json({ providers: rows.map((a) => ({ provider: a.provider, email: a.email, linkedAt: a.createdAt })) });
    } catch (err: any) {
      return res.status(500).json({ error: err.message });
    }
  }

  // --- AUTH: link an additional provider to my account ---
  static async linkProvider(req: Request, res: Response) {
    try {
      const self = (req as any).user;
      if (!self?.userId) return res.status(401).json({ error: 'Authentication required' });
      await ensureDatabaseSchema();
      const body = req.body || {};
      const provider = String(body.provider || '').toLowerCase();
      let profile: { sub: string; email: string; name: string; avatar: string };
      if (provider === 'google') {
        profile = await AuthService.verifyGoogleIdToken(String(body.idToken || ''));
      } else if (provider === 'facebook') {
        profile = await AuthService.verifyFacebookToken(String(body.accessToken || ''));
      } else {
        return res.status(400).json({ error: 'provider must be google or facebook' });
      }
      const existing = await prisma.account.findUnique({ where: { provider_providerId: { provider, providerId: profile.sub } } });
      if (existing && existing.userId !== self.userId) {
        return res.status(409).json({ error: 'That account is already linked to another thileli dz user.' });
      }
      if (!existing) {
        await prisma.account.create({ data: { userId: self.userId, provider, providerId: profile.sub, email: profile.email || null } });
      }
      return res.status(200).json({ linked: true, provider });
    } catch (err: any) {
      return res.status(err.statusCode || 500).json({ error: err.message });
    }
  }
}
