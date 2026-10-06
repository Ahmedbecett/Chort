import { prisma } from '../lib/prisma';

/**
 * Basic content moderation:
 * - Anyone authenticated (or id-supplied) can report a video or a user.
 * - 3+ distinct reporters auto-hide a video (visibility PRIVATE, never
 *   deleted) and resolve its pending reports — testable without an admin.
 * - Admins can list by status and resolve manually (dismiss / hide / show).
 */

export const REPORT_AUTO_HIDE_THRESHOLD = 3;

export class ReportService {
  static async submit(input: { reporterId: string; videoId?: string; targetUserId?: string; reason: string }) {
    if (input.videoId) {
      const video = await prisma.video.findUnique({ where: { id: input.videoId }, select: { id: true } });
      if (!video) {
        const err = new Error('Video not found');
        (err as any).statusCode = 404;
        throw err;
      }
    }
    if (input.targetUserId) {
      const user = await prisma.user.findUnique({ where: { id: input.targetUserId }, select: { id: true } });
      if (!user) {
        const err = new Error('User not found');
        (err as any).statusCode = 404;
        throw err;
      }
    }

    const report = await prisma.report.create({
      data: {
        reporterId: input.reporterId,
        videoId: input.videoId || null,
        targetUserId: input.targetUserId || null,
        reason: input.reason,
        status: 'PENDING',
      },
    });

    // Auto-hide on reporter consensus (moderation without deletion).
    let autoHidden = false;
    if (input.videoId) {
      try {
        const distinct = await prisma.report.findMany({
          where: { videoId: input.videoId },
          select: { reporterId: true },
          distinct: ['reporterId'],
        });
        if (distinct.length >= REPORT_AUTO_HIDE_THRESHOLD) {
          await prisma.video.update({ where: { id: input.videoId }, data: { visibility: 'PRIVATE' } });
          await prisma.report.updateMany({ where: { videoId: input.videoId, status: 'PENDING' }, data: { status: 'RESOLVED' } });
          autoHidden = true;
        }
      } catch {}
    }

    return { reportId: report.id, status: autoHidden ? 'RESOLVED' : 'PENDING', autoHidden };
  }

  static async list(status: string | undefined, page: number, limit: number) {
    const skip = (page - 1) * limit;
    const where = status && ['PENDING', 'RESOLVED', 'DISMISSED'].includes(status) ? { status } : {};
    const rows = await prisma.report.findMany({
      where,
      orderBy: { createdAt: 'desc' },
      skip,
      take: limit + 1,
    });
    const hasMore = rows.length > limit;
    if (hasMore) rows.pop();
    return { reports: rows, page, hasMore };
  }

  static async resolve(reportId: string, action: 'dismiss' | 'hide_video' | 'show_video') {
    const report = await prisma.report.findUnique({ where: { id: reportId } });
    if (!report) {
      const err = new Error('Report not found');
      (err as any).statusCode = 404;
      throw err;
    }
    if (action === 'hide_video' && report.videoId) {
      await prisma.video.update({ where: { id: report.videoId }, data: { visibility: 'PRIVATE' } });
    } else if (action === 'show_video' && report.videoId) {
      await prisma.video.update({ where: { id: report.videoId }, data: { visibility: 'PUBLIC' } });
    }
    const status = action === 'dismiss' ? 'DISMISSED' : 'RESOLVED';
    await prisma.report.update({ where: { id: reportId }, data: { status } });
    return { reportId, status, action };
  }
}
