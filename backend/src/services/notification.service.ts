import { prisma } from '../lib/prisma';

/**
 * Notifications: likes, comments, follows. Writes are best-effort so social
 * actions never fail because of a notification hiccup. Self-actions never
 * notify. Reads are offset-paginated, newest first.
 */

export type NotificationType = 'like' | 'comment' | 'follow' | 'system';

export class NotificationService {
  static async notify(input: {
    recipientId: string;
    actorId: string;
    type: NotificationType;
    message: string;
    referenceId?: string;
  }): Promise<void> {
    try {
      if (!input.recipientId || input.recipientId === input.actorId) return;
      const recipient = await prisma.user.findUnique({ where: { id: input.recipientId }, select: { id: true } });
      if (!recipient) return;
      await prisma.notification.create({
        data: {
          recipientId: input.recipientId,
          actorId: input.actorId,
          type: input.type,
          message: input.message.slice(0, 300),
          referenceId: input.referenceId?.slice(0, 120) || null,
        },
      });
      // Bound inbox growth: keep the newest 200 per recipient.
      try {
        const overflow = await prisma.notification.findMany({
          where: { recipientId: input.recipientId },
          orderBy: { createdAt: 'desc' },
          skip: 200,
          take: 50,
          select: { id: true },
        });
        if (overflow.length > 0) {
          await prisma.notification.deleteMany({ where: { id: { in: overflow.map((o) => o.id) } } });
        }
      } catch {}
    } catch {}
  }

  static async list(userId: string, page: number, limit: number) {
    const skip = (page - 1) * limit;
    const [rows, unreadCount] = await Promise.all([
      prisma.notification.findMany({
        where: { recipientId: userId },
        orderBy: [{ isRead: 'asc' }, { createdAt: 'desc' }],
        skip,
        take: limit + 1,
      }),
      prisma.notification.count({ where: { recipientId: userId, isRead: false } }).catch(() => 0),
    ]);
    const hasMore = rows.length > limit;
    if (hasMore) rows.pop();
    return {
      notifications: rows.map((n) => ({
        id: n.id,
        actorId: n.actorId,
        type: n.type,
        message: n.message,
        referenceId: n.referenceId,
        isRead: n.isRead,
        createdAt: n.createdAt instanceof Date ? n.createdAt.getTime() : new Date(n.createdAt).getTime(),
      })),
      unreadCount,
      page,
      hasMore,
    };
  }

  static async markRead(userId: string, ids?: string[]): Promise<{ marked: number }> {
    const where =
      ids && ids.length > 0 ? { recipientId: userId, id: { in: ids } } : { recipientId: userId, isRead: false };
    const res = await prisma.notification.updateMany({ where, data: { isRead: true } }).catch(() => ({ count: 0 }));
    return { marked: res.count };
  }
}
