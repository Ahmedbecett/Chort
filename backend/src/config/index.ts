import dotenv from 'dotenv';
import Redis from 'ioredis';
import { S3Client } from '@aws-sdk/client-s3';

dotenv.config();

export const config = {
  port: parseInt(process.env.PORT || '4000', 10),
  nodeEnv: process.env.NODE_ENV || 'production',
  jwtSecret: process.env.JWT_SECRET || (process.env.NODE_ENV === 'production'
    ? (() => { throw new Error('JWT_SECRET environment variable is strictly required in production'); })()
    : 'dev-ephemeral-jwt-secret-local-testing-only'),
  databaseUrl: process.env.DATABASE_URL || '',
  redisUrl: process.env.REDIS_URL || '',
  s3: {
    endpoint: process.env.S3_ENDPOINT || undefined,
    region: process.env.S3_REGION || 'us-east-1',
    bucket: process.env.S3_BUCKET || '',
    accessKeyId: process.env.S3_ACCESS_KEY_ID || '',
    secretAccessKey: process.env.S3_SECRET_ACCESS_KEY || '',
    forcePathStyle: process.env.S3_FORCE_PATH_STYLE !== undefined
      ? process.env.S3_FORCE_PATH_STYLE === 'true'
      : Boolean(process.env.S3_ENDPOINT),
  },
  cdn: {
    baseUrl: process.env.CDN_BASE_URL || 'https://chort-nine.vercel.app',
  },
  primaryUpstreamUrl: process.env.PRIMARY_UPSTREAM_URL || 'https://chort-nine.vercel.app',
  pexels: {
    apiKey: process.env.PEXELS_API_KEY || '',
  },
  coverr: {
    apiKey: process.env.COVERR_API_KEY || '',
  },
  pixabay: {
    apiKey: process.env.PIXABAY_API_KEY || '',
  },
  google: {
    // Public OAuth web-client ID shipped in the Android app (strings.xml).
    // Safe as a default: it only identifies the audience; every token is
    // still cryptographically verified with Google before any login.
    clientId: process.env.GOOGLE_CLIENT_ID || '358490968062-n584hegcbbavgsbbq621191bfbvo78q1.apps.googleusercontent.com',
  },
  facebook: {
    appId: process.env.FACEBOOK_APP_ID || '',
    appSecret: process.env.FACEBOOK_APP_SECRET || '',
  },
  twilio: {
    accountSid: process.env.TWILIO_ACCOUNT_SID || '',
    authToken: process.env.TWILIO_AUTH_TOKEN || '',
    fromNumber: process.env.TWILIO_FROM_NUMBER || '',
    messagingServiceSid: process.env.TWILIO_MESSAGING_SERVICE_SID || '',
  },
  otp: {
    ttlSeconds: Math.max(60, parseInt(process.env.OTP_TTL_SECONDS || '600', 10) || 600),
    cooldownSeconds: Math.max(10, parseInt(process.env.OTP_RESEND_COOLDOWN_SECONDS || '60', 10) || 60),
    maxPerHour: Math.max(1, parseInt(process.env.OTP_MAX_PER_HOUR || '5', 10) || 5),
    maxAttempts: Math.max(1, parseInt(process.env.OTP_MAX_ATTEMPTS || '5', 10) || 5),
  },
  feed: {
    // External licensed-video providers are opt-in in production; the feed never injects them by default.
    allowExternalVideos: (process.env.ENABLE_EXTERNAL_VIDEOS || 'false').trim().toLowerCase() === 'true',
    // When true (default), licensed seed videos must carry a verified audio
    // track before entering the feed. Set FEED_REQUIRE_EXTERNAL_AUDIO=false
    // to allow silent-but-valid licensed clips. Never applies to user uploads.
    requireExternalAudio: process.env.FEED_REQUIRE_EXTERNAL_AUDIO !== 'false',
  },
};

// S3 Client configuration
export const s3Client = new S3Client({
  region: config.s3.region,
  endpoint: config.s3.endpoint,
  forcePathStyle: config.s3.forcePathStyle,
  credentials: {
    accessKeyId: config.s3.accessKeyId || 'PUBLIC_ANON',
    secretAccessKey: config.s3.secretAccessKey || 'PUBLIC_ANON',
  },
});

export function isStorageConfigured(): boolean {
  return Boolean(config.s3.accessKeyId && config.s3.secretAccessKey && config.s3.bucket);
}

// Resilient Cache Interface for Serverless
export interface CacheClient {
  get(key: string): Promise<string | null>;
  set(key: string, value: string, mode?: string, duration?: number): Promise<string>;
  incr(key: string): Promise<number>;
  del(key: string): Promise<number>;
}

class InMemoryCache implements CacheClient {
  private store = new Map<string, { value: string; expires?: number }>();

  async get(key: string): Promise<string | null> {
    const item = this.store.get(key);
    if (!item) return null;
    if (item.expires && Date.now() > item.expires) {
      this.store.delete(key);
      return null;
    }
    return item.value;
  }

  async set(key: string, value: string, mode?: string, duration?: number): Promise<string> {
    let expires: number | undefined;
    if (mode === 'EX' && duration) {
      expires = Date.now() + duration * 1000;
    }
    this.store.set(key, { value, expires });
    return 'OK';
  }

  async incr(key: string): Promise<number> {
    const current = await this.get(key);
    const count = (current ? parseInt(current, 10) : 0) + 1;
    this.store.set(key, { value: count.toString() });
    return count;
  }

  async del(key: string): Promise<number> {
    const deleted = this.store.delete(key);
    return deleted ? 1 : 0;
  }
}

// Redis initialization with fallback so Vercel Serverless never hangs
function initCache(): CacheClient {
  if (config.redisUrl && !config.redisUrl.includes('localhost')) {
    try {
      const client = new Redis(config.redisUrl, {
        maxRetriesPerRequest: 1,
        connectTimeout: 2000,
        lazyConnect: true,
        enableOfflineQueue: false,
      });
      client.on('error', (err) => {
        console.warn('Redis notice:', err.message);
      });
      return client as unknown as CacheClient;
    } catch (err) {
      console.warn('Using in-memory cache fallback');
      return new InMemoryCache();
    }
  }
  return new InMemoryCache();
}

export const redis = initCache();
