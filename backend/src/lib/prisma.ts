import { PrismaClient } from '@prisma/client';

const globalForPrisma = global as unknown as { prisma?: PrismaClient };

export const isDbConfigured = (): boolean => {
  return Boolean(process.env.DATABASE_URL && process.env.DATABASE_URL.startsWith('postgres'));
};

let prismaInstance: PrismaClient | null = null;

export function getPrisma(): PrismaClient {
  if (!isDbConfigured()) {
    throw new Error('DATABASE_URL is not configured');
  }
  if (!prismaInstance) {
    if (globalForPrisma.prisma) {
      prismaInstance = globalForPrisma.prisma;
    } else {
      prismaInstance = new PrismaClient({
        log: process.env.NODE_ENV === 'development' ? ['warn', 'error'] : ['error'],
      });
      if (process.env.NODE_ENV !== 'production') {
        globalForPrisma.prisma = prismaInstance;
      }
    }
  }
  return prismaInstance;
}

// Resilient Proxy: never throws on import/cold start if DATABASE_URL is not provided
export const prisma = new Proxy({} as PrismaClient, {
  get(_target, prop) {
    const client = getPrisma();
    const value = (client as any)[prop];
    if (typeof value === 'function') {
      return value.bind(client);
    }
    return value;
  },
});

let isSchemaEnsured = false;

/**
 * Ensures all required PostgreSQL tables, enums, indexes, and relations
 * are safely created in Neon PostgreSQL without dropping or destroying existing data.
 */
export async function ensureDatabaseSchema(force = false): Promise<{
  success: boolean;
  message: string;
  tablesVerified?: string[];
  error?: string;
}> {
  if (!isDbConfigured()) {
    return {
      success: false,
      message: 'DATABASE_URL is not configured in Vercel environment variables.',
    };
  }

  if (isSchemaEnsured && !force) {
    return {
      success: true,
      message: 'Database schema is already synchronized and verified.',
    };
  }

  const client = getPrisma();

  try {
    // Check existing tables safely using information_schema.tables (standard text columns)
    const existingTablesResult: Array<{ table_name: string }> = await client.$queryRawUnsafe(`
      SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';
    `);

    const existingTables = new Set(existingTablesResult.map((r) => r.table_name));
    const requiredTables = [
      'User',
      'Profile',
      'Video',
      'VideoMetadata',
      'Comment',
      'Like',
      'Follow',
      'View',
      'Share',
      'SavedVideo',
      'Hashtag',
      'VideoHashtag',
      'Notification',
      'Report',
      'Session',
    ];

    const allTablesExist = requiredTables.every((t) => existingTables.has(t));

    if (allTablesExist && !force) {
      isSchemaEnsured = true;
      return {
        success: true,
        message: 'Neon PostgreSQL schema verified. All tables exist.',
        tablesVerified: Array.from(existingTables),
      };
    }

    // Step 1: Create Enums safely
    await client.$executeRawUnsafe(`
      DO $$ BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'Role') THEN
          CREATE TYPE "Role" AS ENUM ('USER', 'CREATOR', 'MODERATOR', 'ADMIN');
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'AccountStatus') THEN
          CREATE TYPE "AccountStatus" AS ENUM ('ACTIVE', 'SUSPENDED', 'BANNED');
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'VideoStatus') THEN
          CREATE TYPE "VideoStatus" AS ENUM ('UPLOADING', 'PROCESSING', 'READY', 'FAILED', 'REMOVED');
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'Visibility') THEN
          CREATE TYPE "Visibility" AS ENUM ('PUBLIC', 'FOLLOWERS', 'PRIVATE');
        END IF;
      END $$;
    `);

    // Step 2: Create Tables safely
    await client.$executeRawUnsafe(`
      CREATE TABLE IF NOT EXISTS "User" (
        "id" TEXT NOT NULL,
        "email" TEXT NOT NULL,
        "username" TEXT NOT NULL,
        "passwordHash" TEXT NOT NULL,
        "role" "Role" NOT NULL DEFAULT 'USER',
        "status" "AccountStatus" NOT NULL DEFAULT 'ACTIVE',
        "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT "User_pkey" PRIMARY KEY ("id")
      );
    `);

    await client.$executeRawUnsafe(`
      CREATE TABLE IF NOT EXISTS "Profile" (
        "id" TEXT NOT NULL,
        "userId" TEXT NOT NULL,
        "displayName" TEXT NOT NULL,
        "bio" TEXT DEFAULT '',
        "avatarUrl" TEXT,
        "bannerUrl" TEXT,
        "isVerified" BOOLEAN NOT NULL DEFAULT false,
        "followersCount" INTEGER NOT NULL DEFAULT 0,
        "followingCount" INTEGER NOT NULL DEFAULT 0,
        "likesReceived" INTEGER NOT NULL DEFAULT 0,
        "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT "Profile_pkey" PRIMARY KEY ("id")
      );
    `);

    await client.$executeRawUnsafe(`
      CREATE TABLE IF NOT EXISTS "Video" (
        "id" TEXT NOT NULL,
        "userId" TEXT NOT NULL,
        "caption" TEXT NOT NULL,
        "originalKey" TEXT NOT NULL,
        "streamUrl" TEXT,
        "thumbnailUrl" TEXT,
        "status" "VideoStatus" NOT NULL DEFAULT 'PROCESSING',
        "visibility" "Visibility" NOT NULL DEFAULT 'PUBLIC',
        "durationSec" DOUBLE PRECISION NOT NULL DEFAULT 0,
        "aspectRatio" TEXT NOT NULL DEFAULT '9:16',
        "musicTitle" TEXT DEFAULT 'Original Sound',
        "musicArtist" TEXT,
        "viewsCount" INTEGER NOT NULL DEFAULT 0,
        "likesCount" INTEGER NOT NULL DEFAULT 0,
        "commentsCount" INTEGER NOT NULL DEFAULT 0,
        "sharesCount" INTEGER NOT NULL DEFAULT 0,
        "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT "Video_pkey" PRIMARY KEY ("id")
      );
    `);

    await client.$executeRawUnsafe(`
      CREATE TABLE IF NOT EXISTS "VideoMetadata" (
        "id" TEXT NOT NULL,
        "videoId" TEXT NOT NULL,
        "codec" TEXT,
        "container" TEXT,
        "width" INTEGER,
        "height" INTEGER,
        "bitrate" INTEGER,
        "fps" DOUBLE PRECISION,
        "hls360pUrl" TEXT,
        "hls480pUrl" TEXT,
        "hls720pUrl" TEXT,
        "hls1080pUrl" TEXT,
        "processingTimeMs" INTEGER,
        "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT "VideoMetadata_pkey" PRIMARY KEY ("id")
      );
    `);

    await client.$executeRawUnsafe(`
      CREATE TABLE IF NOT EXISTS "Comment" (
        "id" TEXT NOT NULL,
        "videoId" TEXT NOT NULL,
        "userId" TEXT NOT NULL,
        "content" TEXT NOT NULL,
        "parentId" TEXT,
        "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT "Comment_pkey" PRIMARY KEY ("id")
      );
    `);

    await client.$executeRawUnsafe(`
      CREATE TABLE IF NOT EXISTS "Like" (
        "id" TEXT NOT NULL,
        "videoId" TEXT NOT NULL,
        "userId" TEXT NOT NULL,
        "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT "Like_pkey" PRIMARY KEY ("id")
      );
    `);

    await client.$executeRawUnsafe(`
      CREATE TABLE IF NOT EXISTS "Follow" (
        "id" TEXT NOT NULL,
        "followerId" TEXT NOT NULL,
        "followingId" TEXT NOT NULL,
        "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT "Follow_pkey" PRIMARY KEY ("id")
      );
    `);

    await client.$executeRawUnsafe(`
      CREATE TABLE IF NOT EXISTS "View" (
        "id" TEXT NOT NULL,
        "videoId" TEXT NOT NULL,
        "userId" TEXT,
        "watchSec" DOUBLE PRECISION NOT NULL,
        "completed" BOOLEAN NOT NULL DEFAULT false,
        "ipAddress" TEXT,
        "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT "View_pkey" PRIMARY KEY ("id")
      );
    `);

    await client.$executeRawUnsafe(`
      CREATE TABLE IF NOT EXISTS "Share" (
        "id" TEXT NOT NULL,
        "videoId" TEXT NOT NULL,
        "userId" TEXT NOT NULL,
        "platform" TEXT NOT NULL DEFAULT 'direct',
        "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT "Share_pkey" PRIMARY KEY ("id")
      );
    `);

    await client.$executeRawUnsafe(`
      CREATE TABLE IF NOT EXISTS "SavedVideo" (
        "id" TEXT NOT NULL,
        "videoId" TEXT NOT NULL,
        "userId" TEXT NOT NULL,
        "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT "SavedVideo_pkey" PRIMARY KEY ("id")
      );
    `);

    await client.$executeRawUnsafe(`
      CREATE TABLE IF NOT EXISTS "Hashtag" (
        "id" TEXT NOT NULL,
        "tag" TEXT NOT NULL,
        "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT "Hashtag_pkey" PRIMARY KEY ("id")
      );
    `);

    await client.$executeRawUnsafe(`
      CREATE TABLE IF NOT EXISTS "VideoHashtag" (
        "videoId" TEXT NOT NULL,
        "hashtagId" TEXT NOT NULL,
        CONSTRAINT "VideoHashtag_pkey" PRIMARY KEY ("videoId","hashtagId")
      );
    `);

    await client.$executeRawUnsafe(`
      CREATE TABLE IF NOT EXISTS "Notification" (
        "id" TEXT NOT NULL,
        "recipientId" TEXT NOT NULL,
        "actorId" TEXT NOT NULL,
        "type" TEXT NOT NULL,
        "message" TEXT NOT NULL,
        "referenceId" TEXT,
        "isRead" BOOLEAN NOT NULL DEFAULT false,
        "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT "Notification_pkey" PRIMARY KEY ("id")
      );
    `);

    await client.$executeRawUnsafe(`
      CREATE TABLE IF NOT EXISTS "Report" (
        "id" TEXT NOT NULL,
        "reporterId" TEXT NOT NULL,
        "targetUserId" TEXT,
        "videoId" TEXT,
        "reason" TEXT NOT NULL,
        "status" TEXT NOT NULL DEFAULT 'PENDING',
        "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT "Report_pkey" PRIMARY KEY ("id")
      );
    `);

    await client.$executeRawUnsafe(`
      CREATE TABLE IF NOT EXISTS "Session" (
        "id" TEXT NOT NULL,
        "userId" TEXT NOT NULL,
        "token" TEXT NOT NULL,
        "userAgent" TEXT,
        "ipAddress" TEXT,
        "expiresAt" TIMESTAMP(3) NOT NULL,
        "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT "Session_pkey" PRIMARY KEY ("id")
      );
    `);

    // Step 3: Create Indexes safely
    await client.$executeRawUnsafe(`
      CREATE UNIQUE INDEX IF NOT EXISTS "User_email_key" ON "User"("email");
      CREATE UNIQUE INDEX IF NOT EXISTS "User_username_key" ON "User"("username");
      CREATE INDEX IF NOT EXISTS "User_email_idx" ON "User"("email");
      CREATE INDEX IF NOT EXISTS "User_username_idx" ON "User"("username");
      CREATE UNIQUE INDEX IF NOT EXISTS "Profile_userId_key" ON "Profile"("userId");
      CREATE INDEX IF NOT EXISTS "Video_userId_idx" ON "Video"("userId");
      CREATE INDEX IF NOT EXISTS "Video_status_visibility_createdAt_idx" ON "Video"("status", "visibility", "createdAt");
      CREATE INDEX IF NOT EXISTS "Video_viewsCount_likesCount_idx" ON "Video"("viewsCount", "likesCount");
      CREATE UNIQUE INDEX IF NOT EXISTS "VideoMetadata_videoId_key" ON "VideoMetadata"("videoId");
      CREATE INDEX IF NOT EXISTS "Comment_videoId_createdAt_idx" ON "Comment"("videoId", "createdAt");
      CREATE INDEX IF NOT EXISTS "Like_userId_idx" ON "Like"("userId");
      CREATE UNIQUE INDEX IF NOT EXISTS "Like_videoId_userId_key" ON "Like"("videoId", "userId");
      CREATE INDEX IF NOT EXISTS "Follow_followerId_idx" ON "Follow"("followerId");
      CREATE INDEX IF NOT EXISTS "Follow_followingId_idx" ON "Follow"("followingId");
      CREATE UNIQUE INDEX IF NOT EXISTS "Follow_followerId_followingId_key" ON "Follow"("followerId", "followingId");
      CREATE INDEX IF NOT EXISTS "View_videoId_createdAt_idx" ON "View"("videoId", "createdAt");
      CREATE UNIQUE INDEX IF NOT EXISTS "SavedVideo_videoId_userId_key" ON "SavedVideo"("videoId", "userId");
      CREATE UNIQUE INDEX IF NOT EXISTS "Hashtag_tag_key" ON "Hashtag"("tag");
      CREATE INDEX IF NOT EXISTS "Hashtag_tag_idx" ON "Hashtag"("tag");
      CREATE INDEX IF NOT EXISTS "Notification_recipientId_isRead_idx" ON "Notification"("recipientId", "isRead");
      CREATE INDEX IF NOT EXISTS "Report_status_idx" ON "Report"("status");
      CREATE UNIQUE INDEX IF NOT EXISTS "Session_token_key" ON "Session"("token");
      CREATE INDEX IF NOT EXISTS "Session_token_idx" ON "Session"("token");
    `);

    // Step 4: Add Foreign Keys safely
    await client.$executeRawUnsafe(`
      DO $$ BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'Profile_userId_fkey') THEN
          ALTER TABLE "Profile" ADD CONSTRAINT "Profile_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'Video_userId_fkey') THEN
          ALTER TABLE "Video" ADD CONSTRAINT "Video_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'VideoMetadata_videoId_fkey') THEN
          ALTER TABLE "VideoMetadata" ADD CONSTRAINT "VideoMetadata_videoId_fkey" FOREIGN KEY ("videoId") REFERENCES "Video"("id") ON DELETE CASCADE ON UPDATE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'Comment_videoId_fkey') THEN
          ALTER TABLE "Comment" ADD CONSTRAINT "Comment_videoId_fkey" FOREIGN KEY ("videoId") REFERENCES "Video"("id") ON DELETE CASCADE ON UPDATE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'Comment_userId_fkey') THEN
          ALTER TABLE "Comment" ADD CONSTRAINT "Comment_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'Like_videoId_fkey') THEN
          ALTER TABLE "Like" ADD CONSTRAINT "Like_videoId_fkey" FOREIGN KEY ("videoId") REFERENCES "Video"("id") ON DELETE CASCADE ON UPDATE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'Like_userId_fkey') THEN
          ALTER TABLE "Like" ADD CONSTRAINT "Like_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'Follow_followerId_fkey') THEN
          ALTER TABLE "Follow" ADD CONSTRAINT "Follow_followerId_fkey" FOREIGN KEY ("followerId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'Follow_followingId_fkey') THEN
          ALTER TABLE "Follow" ADD CONSTRAINT "Follow_followingId_fkey" FOREIGN KEY ("followingId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'View_videoId_fkey') THEN
          ALTER TABLE "View" ADD CONSTRAINT "View_videoId_fkey" FOREIGN KEY ("videoId") REFERENCES "Video"("id") ON DELETE CASCADE ON UPDATE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'View_userId_fkey') THEN
          ALTER TABLE "View" ADD CONSTRAINT "View_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE SET NULL ON UPDATE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'Share_videoId_fkey') THEN
          ALTER TABLE "Share" ADD CONSTRAINT "Share_videoId_fkey" FOREIGN KEY ("videoId") REFERENCES "Video"("id") ON DELETE CASCADE ON UPDATE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'Share_userId_fkey') THEN
          ALTER TABLE "Share" ADD CONSTRAINT "Share_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'SavedVideo_videoId_fkey') THEN
          ALTER TABLE "SavedVideo" ADD CONSTRAINT "SavedVideo_videoId_fkey" FOREIGN KEY ("videoId") REFERENCES "Video"("id") ON DELETE CASCADE ON UPDATE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'SavedVideo_userId_fkey') THEN
          ALTER TABLE "SavedVideo" ADD CONSTRAINT "SavedVideo_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'VideoHashtag_videoId_fkey') THEN
          ALTER TABLE "VideoHashtag" ADD CONSTRAINT "VideoHashtag_videoId_fkey" FOREIGN KEY ("videoId") REFERENCES "Video"("id") ON DELETE CASCADE ON UPDATE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'VideoHashtag_hashtagId_fkey') THEN
          ALTER TABLE "VideoHashtag" ADD CONSTRAINT "VideoHashtag_hashtagId_fkey" FOREIGN KEY ("hashtagId") REFERENCES "Hashtag"("id") ON DELETE CASCADE ON UPDATE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'Notification_recipientId_fkey') THEN
          ALTER TABLE "Notification" ADD CONSTRAINT "Notification_recipientId_fkey" FOREIGN KEY ("recipientId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'Report_reporterId_fkey') THEN
          ALTER TABLE "Report" ADD CONSTRAINT "Report_reporterId_fkey" FOREIGN KEY ("reporterId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'Report_targetUserId_fkey') THEN
          ALTER TABLE "Report" ADD CONSTRAINT "Report_targetUserId_fkey" FOREIGN KEY ("targetUserId") REFERENCES "User"("id") ON DELETE SET NULL ON UPDATE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'Report_videoId_fkey') THEN
          ALTER TABLE "Report" ADD CONSTRAINT "Report_videoId_fkey" FOREIGN KEY ("videoId") REFERENCES "Video"("id") ON DELETE SET NULL ON UPDATE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'Session_userId_fkey') THEN
          ALTER TABLE "Session" ADD CONSTRAINT "Session_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;
        END IF;
      END $$;
    `);

    // Verify all created tables
    const finalTablesResult: Array<{ table_name: string }> = await client.$queryRawUnsafe(`
      SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';
    `);

    const verifiedTables = finalTablesResult.map((r) => r.table_name);
    isSchemaEnsured = true;
    return {
      success: true,
      message: 'Neon PostgreSQL schema successfully created and synchronized.',
      tablesVerified: verifiedTables,
    };
  } catch (err: any) {
    return {
      success: false,
      message: 'Failed to synchronize database schema: ' + err.message,
      error: err.message,
    };
  }
}

export async function checkDatabaseConnection(): Promise<{
  connected: boolean;
  latencyMs?: number;
  schemaReady?: boolean;
  tablesCount?: number;
  tablesVerified?: string[];
  error?: string;
}> {
  if (!isDbConfigured()) {
    return {
      connected: false,
      error: 'DATABASE_URL environment variable is not configured on Vercel',
    };
  }
  const start = Date.now();
  try {
    await prisma.$queryRaw`SELECT 1`;
    const latency = Date.now() - start;

    // Self-healing: automatically ensure schema exists
    const schemaStatus = await ensureDatabaseSchema();

    return {
      connected: true,
      latencyMs: latency,
      schemaReady: schemaStatus.success,
      tablesCount: schemaStatus.tablesVerified?.length,
      tablesVerified: schemaStatus.tablesVerified,
      error: schemaStatus.success ? undefined : schemaStatus.error,
    };
  } catch (err: any) {
    return {
      connected: false,
      error: err?.message || 'Database connection error',
    };
  }
}
