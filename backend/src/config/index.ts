import dotenv from 'dotenv';
import Redis from 'ioredis';
import { S3Client } from '@aws-sdk/client-s3';

dotenv.config();

export const config = {
  port: parseInt(process.env.PORT || '4000', 10),
  nodeEnv: process.env.NODE_ENV || 'production',
  jwtSecret: process.env.JWT_SECRET || 'chort-super-secure-production-jwt-key-2026',
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
