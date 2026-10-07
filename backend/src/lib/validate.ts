import { z } from 'zod';
import { Request, Response, NextFunction } from 'express';

/**
 * Central input validation (zod). Every mutating endpoint validates here so
 * bad input fails fast with a clear 400 instead of corrupting data or
 * crashing deeper layers. Read-only params are clamped, never trusted.
 */

export const schemas = {
  register: z.object({
    email: z.string().trim().toLowerCase().email().max(120),
    username: z
      .string()
      .trim()
      .toLowerCase()
      .min(3)
      .max(30)
      .regex(/^[a-z0-9_]+$/, 'username may contain letters, numbers and underscore only'),
    password: z.string().min(6).max(100),
    displayName: z.string().trim().max(50).optional(),
  }),
  login: z.object({
    identifier: z.string().trim().min(1).max(120).optional(),
    email: z.string().trim().max(120).optional(),
    username: z.string().trim().max(120).optional(),
    password: z.string().min(1).max(100),
  }),
  uploadUrl: z.object({
    filename: z.string().trim().min(1).max(200),
    contentType: z.string().trim().min(1).max(100),
    fileSize: z.number().int().positive().max(100 * 1024 * 1024).optional(),
    userId: z.string().trim().min(1).max(120).optional(),
  }),
  completeUpload: z.object({
    videoId: z.string().trim().min(1).max(120),
    userId: z.string().trim().min(1).max(120).optional(),
    caption: z.string().trim().max(500).optional().default(''),
    videoUrl: z.string().trim().url().max(2000),
    thumbnailUrl: z.string().trim().max(2000).optional(),
    musicTitle: z.string().trim().max(120).optional(),
    musicArtist: z.string().trim().max(120).optional(),
    aspectRatio: z.string().trim().max(20).optional(),
    objectKey: z.string().trim().max(300).optional(),
  }),
  comment: z.object({
    content: z.string().trim().min(1).max(500),
    userId: z.string().trim().min(1).max(120).optional(),
  }),
  report: z
    .object({
      videoId: z.string().trim().min(1).max(120).optional(),
      targetUserId: z.string().trim().min(1).max(120).optional(),
      reason: z.string().trim().min(3).max(500),
      reporterId: z.string().trim().min(1).max(120).optional(),
    })
    .refine((d) => Boolean(d.videoId) !== Boolean(d.targetUserId), {
      message: 'Exactly one of videoId or targetUserId is required',
    }),
  notificationsRead: z.object({
    ids: z.array(z.string().trim().min(1).max(120)).max(100).optional(),
  }),
  resolveReport: z.object({
    action: z.enum(['dismiss', 'hide_video', 'show_video']),
  }),
  setUserStatus: z.object({
    status: z.enum(['ACTIVE', 'SUSPENDED', 'BANNED']),
  }),
  walletEarn: z.object({
    amount: z.number().int().min(1).max(500),
    reason: z.string().trim().min(1).max(48),
  }),
  walletSpend: z.object({
    amount: z.number().int().min(1).max(100000),
    reason: z.string().trim().min(1).max(48),
  }),
  adjustCoins: z.object({
    amount: z.number().int().min(-100000).max(100000).refine((v) => v !== 0, 'amount required'),
    reason: z.string().trim().min(1).max(48),
  }),
  oauthGoogle: z.object({
    idToken: z.string().trim().min(10).max(4000),
  }),
  oauthFacebook: z.object({
    accessToken: z.string().trim().min(10).max(4000),
  }),
  phoneRequest: z.object({
    phone: z.string().trim().min(8).max(20),
  }),
  phoneVerify: z.object({
    phone: z.string().trim().min(8).max(20),
    code: z.string().trim().regex(/^\d{4,8}$/, 'code must be digits'),
    name: z.string().trim().max(50).optional(),
  }),
  recoverRequest: z.object({
    phone: z.string().trim().min(8).max(20),
  }),
  recoverConfirm: z.object({
    phone: z.string().trim().min(8).max(20),
    code: z.string().trim().regex(/^\d{4,8}$/, 'code must be digits'),
    newPassword: z.string().min(6).max(100).optional(),
  }),
  changePassword: z.object({
    currentPassword: z.string().min(1).max(100),
    newPassword: z.string().min(6).max(100),
  }),
  updateUser: z.object({
    username: z.string().trim().min(3).max(30).regex(/^[a-zA-Z0-9_.]+$/, 'letters, numbers, _ and . only').optional(),
    displayName: z.string().trim().min(1).max(50).optional(),
    bio: z.string().trim().max(300).optional(),
    avatarUrl: z.string().trim().url().max(2000).optional(),
    bannerUrl: z.string().trim().url().max(2000).optional(),
    isPrivate: z.boolean().optional(),
  }),
};

export type SchemaName = keyof typeof schemas;

/** Express middleware: validates req.body against a named schema. */
export function validateBody(name: SchemaName) {
  return (req: Request, res: Response, next: NextFunction) => {
    const parsed = schemas[name].safeParse(req.body || {});
    if (!parsed.success) {
      return res.status(400).json({
        error: 'Invalid request body',
        details: parsed.error.issues.map((i) => `${i.path.join('.') || 'body'}: ${i.message}`),
      });
    }
    req.body = parsed.data;
    next();
  };
}

/** Clamp a limit param into [1, max]. */
export function clampLimit(raw: unknown, fallback: number, max = 30): number {
  const n = typeof raw === 'string' ? parseInt(raw, 10) : Number(raw);
  if (!Number.isFinite(n)) return fallback;
  return Math.min(max, Math.max(1, Math.floor(n)));
}

/** Clamp a page param into [1, ...]. */
export function clampPage(raw: unknown): number {
  const n = typeof raw === 'string' ? parseInt(raw, 10) : Number(raw);
  if (!Number.isFinite(n) || n < 1) return 1;
  return Math.floor(n);
}

/** Feed mode allow-list. */
export type FeedMode = 'recommended' | 'trending' | 'new' | 'following';

export function parseFeedMode(raw: unknown): FeedMode {
  const m = String(raw || 'recommended').trim().toLowerCase();
  if (m === 'trending' || m === 'new' || m === 'following') return m;
  return 'recommended';
}
