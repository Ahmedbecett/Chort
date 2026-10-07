import { prisma } from '../lib/prisma';
import { redis } from '../config';
import { VideoService } from './video.service';
import { NotificationService } from './notification.service';

/**
 * Social graph: follow/unfollow with counter maintenance, followers /
 * following lists, saved & liked video lists. All writes keep Profile
 * counters in sync; failures roll back via Prisma where grouped.
 */

function actorName(userId: string): Promise<string> {
  return prisma.user
    .findUnique({ where: { id: userId }, select: { username: true } })
    .then((u) => u?.username || 'Someone')
    .catch(() => 'Someone');
}

export class SocialService {
  private static async ensureUser(userId: string) {
    const user = await prisma.user.findUnique({ where: { id: userId } }).catch(() => null);
    if (!user) {
      const err = new Error('Authenticated user not found');
      (err as any).statusCode = 401;
      throw err;
    }
    return user;
  }

  static async follow(followerId: string, followingId: string) {
    if (followerId === followingId) {
      const err = new Error('You cannot follow yourself');
      (err as any).statusCode = 400;
      throw err;
    }
    await this.ensureUser(followerId);
    const target = await prisma.user.findUnique({ where: { id: followingId }, select: { id: true } });
    if (!target) {
      const err = new Error('User not found');
      (err as any).statusCode = 404;
      throw err;
    }
    const existing = await prisma.follow.findUnique({
      where: { followerId_followingId: { followerId, followingId } },
    });
    if (existing) return { following: true, already: true };

    await prisma.follow.create({ data: { followerId, followingId } });
    await prisma.profile.updateMany({ where: { userId: followerId }, data: { followingCount: { increment: 1 } } });
    await prisma.profile.updateMany({ where: { userId: followingId }, data: { followersCount: { increment: 1 } } });
    try {
      await redis.del(`feed:following:${followerId}`);
    } catch {}
    try {
      const name = await actorName(followerId);
      await NotificationService.notify({
        recipientId: followingId,
        actorId: followerId,
        type: 'follow',
        message: `@${name} started following you`,
        referenceId: followerId,
      });
    } catch {}
    return { following: true, already: false };
  }

  static async unfollow(followerId: string, followingId: string) {
    const existing = await prisma.follow.findUnique({
      where: { followerId_followingId: { followerId, followingId } },
    });
    if (!existing) return { following: false, already: true };

    await prisma.follow.delete({ where: { id: existing.id } });
    await prisma.profile.updateMany({ where: { userId: followerId }, data: { followingCount: { decrement: 1 } } });
    await prisma.profile.updateMany({ where: { userId: followingId }, data: { followersCount: { decrement: 1 } } });
    // Clamp counters at zero (decrements of stale data must not go negative)
    await prisma.profile.updateMany({ where: { userId: followerId, followingCount: { lt: 0 } }, data: { followingCount: 0 } });
    await prisma.profile.updateMany({ where: { userId: followingId, followersCount: { lt: 0 } }, data: { followersCount: 0 } });
    try {
      await redis.del(`feed:following:${followerId}`);
    } catch {}
    return { following: false, already: false };
  }

  static async followState(viewerId: string | undefined, targetId: string) {
    if (!viewerId) return { following: false, followersCount: 0, followingCount: 0 };
    const [rel, profile] = await Promise.all([
      prisma.follow
        .findUnique({ where: { followerId_followingId: { followerId: viewerId, followingId: targetId } } })
        .catch(() => null),
      prisma.profile.findFirst({ where: { userId: targetId } }).catch(() => null),
    ]);
    return {
      following: Boolean(rel),
      followersCount: profile?.followersCount || 0,
      followingCount: profile?.followingCount || 0,
    };
  }

  private static formatUserRow(u: { id: string; username: string; profile: { displayName: string; avatarUrl: string | null; bio: string | null } | null }) {
    return {
      id: u.id,
      username: u.username,
      displayName: u.profile?.displayName || u.username,
      avatarUrl: u.profile?.avatarUrl || '',
      bio: u.profile?.bio || '',
    };
  }

  static async getFollowers(userId: string, page: number, limit: number) {
    const skip = (page - 1) * limit;
    const rows = await prisma.follow.findMany({
      where: { followingId: userId },
      include: { follower: { include: { profile: true } } },
      orderBy: { createdAt: 'desc' },
      skip,
      take: limit + 1,
    });
    const hasMore = rows.length > limit;
    if (hasMore) rows.pop();
    return { users: rows.map((r) => this.formatUserRow(r.follower as never)), page, hasMore };
  }

  static async getFollowing(userId: string, page: number, limit: number) {
    const skip = (page - 1) * limit;
    const rows = await prisma.follow.findMany({
      where: { followerId: userId },
      include: { following: { include: { profile: true } } },
      orderBy: { createdAt: 'desc' },
      skip,
      take: limit + 1,
    });
    const hasMore = rows.length > limit;
    if (hasMore) rows.pop();
    return { users: rows.map((r) => this.formatUserRow(r.following as never)), page, hasMore };
  }

  static async getSavedVideos(userId: string, page: number, limit: number) {
    const skip = (page - 1) * limit;
    const rows = await prisma.savedVideo.findMany({
      where: { userId, video: { status: 'READY' } },
      include: { video: { include: { user: { include: { profile: true } } } } },
      orderBy: { createdAt: 'desc' },
      skip,
      take: limit + 1,
    });
    const hasMore = rows.length > limit;
    if (hasMore) rows.pop();
    const videos = await VideoService.formatVideoRows(rows.map((r) => r.video as Record<string, unknown>));
    return { videos, page, hasMore };
  }

  static async getLikedVideos(userId: string, page: number, limit: number) {
    const skip = (page - 1) * limit;
    const rows = await prisma.like.findMany({
      where: { userId, video: { status: 'READY', visibility: 'PUBLIC' } },
      include: { video: { include: { user: { include: { profile: true } } } } },
      orderBy: { createdAt: 'desc' },
      skip,
      take: limit + 1,
    });
    const hasMore = rows.length > limit;
    if (hasMore) rows.pop();
    const videos = await VideoService.formatVideoRows(rows.map((r) => r.video as Record<string, unknown>));
    return { videos, page, hasMore };
  }

  /** Creators the viewer saved videos from (recommendation signal, no schema change). */
  static async getSavedCreatorIds(userId: string | undefined): Promise<string[]> {
    if (!userId) return [];
    try {
      const rows = await prisma.savedVideo.findMany({
        where: { userId },
        include: { video: { select: { userId: true } } },
        orderBy: { createdAt: 'desc' },
        take: 200,
      });
      return Array.from(new Set(rows.map((r: any) => String(r.video.userId))));
    } catch {
      return [];
    }
  }
}
