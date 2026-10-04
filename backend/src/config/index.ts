import dotenv from 'dotenv';
import Redis from 'ioredis';
import { S3Client } from '@aws-sdk/client-s3';

dotenv.config();

export const config = {
  port: parseInt(process.env.PORT || '4000', 10),
  jwtSecret: process.env.JWT_SECRET || 'tokpulse-super-secure-production-jwt-key-2026',
  databaseUrl: process.env.DATABASE_URL || 'postgresql://tokpulse_admin:pulse_secure_pass@localhost:5432/tokpulse_db?schema=public',
  redisUrl: process.env.REDIS_URL || 'redis://localhost:6379',
  s3: {
    endpoint: process.env.S3_ENDPOINT,
    region: process.env.S3_REGION || 'us-east-1',
    bucket: process.env.S3_BUCKET || 'tokpulse-videos-production',
    accessKeyId: process.env.S3_ACCESS_KEY_ID || 'AKIA_TOKPULSE_PROD',
    secretAccessKey: process.env.S3_SECRET_ACCESS_KEY || 'SECRET_KEY_PROD',
    forcePathStyle: process.env.S3_FORCE_PATH_STYLE === 'true',
  },
  cdn: {
    baseUrl: process.env.CDN_BASE_URL || 'https://cdn.tokpulse.social',
  },
};

export const s3Client = new S3Client({
  region: config.s3.region,
  endpoint: config.s3.endpoint,
  forcePathStyle: config.s3.forcePathStyle,
  credentials: {
    accessKeyId: config.s3.accessKeyId,
    secretAccessKey: config.s3.secretAccessKey,
  },
});

export const redis = new Redis(config.redisUrl, {
  maxRetriesPerRequest: null,
  enableReadyCheck: false,
});

redis.on('connect', () => console.log('✅ Connected to Redis Cache & Queue Cluster'));
redis.on('error', (err) => console.warn('⚠️ Redis Notice:', err.message));
