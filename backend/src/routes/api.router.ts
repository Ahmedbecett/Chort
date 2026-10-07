import { Router } from 'express';
import rateLimit from 'express-rate-limit';
import { ApiController } from '../controllers/api.controller';
import { validateBody } from '../lib/validate';

export const apiRouter = Router();

// Stricter per-area limiters (in addition to the global one in server.ts).
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 100,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many auth attempts, please try again later' },
});

const otpLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 20,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many code requests, please try again later' },
});

const reportLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 30,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many reports, please try again later' },
});

// Health & Infrastructure Status
apiRouter.get('/health', ApiController.healthCheck);
apiRouter.all('/system/migrate', ApiController.runMigration);
apiRouter.get('/system/storage-check', ApiController.checkStorage);

// Authentication & Sessions
apiRouter.post('/auth/register', authLimiter, validateBody('register'), ApiController.register);
apiRouter.post('/auth/login', authLimiter, validateBody('login'), ApiController.login);
apiRouter.post('/auth/logout', ApiController.logout);
apiRouter.get('/auth/sessions', ApiController.listSessions);
apiRouter.delete('/auth/sessions/:sessionId', ApiController.revokeSession);
apiRouter.post('/auth/oauth/google', authLimiter, validateBody('oauthGoogle'), ApiController.oauthGoogle);
apiRouter.post('/auth/oauth/facebook', authLimiter, validateBody('oauthFacebook'), ApiController.oauthFacebook);
apiRouter.post('/auth/phone/request', otpLimiter, validateBody('phoneRequest'), ApiController.phoneRequest);
apiRouter.post('/auth/phone/verify', otpLimiter, validateBody('phoneVerify'), ApiController.phoneVerify);
apiRouter.post('/auth/phone/firebase', authLimiter, validateBody('phoneFirebase'), ApiController.phoneFirebase);
apiRouter.post('/auth/recover/request', otpLimiter, validateBody('recoverRequest'), ApiController.recoverRequest);
apiRouter.post('/auth/recover/confirm', otpLimiter, validateBody('recoverConfirm'), ApiController.recoverConfirm);
apiRouter.post('/auth/password/change', authLimiter, validateBody('changePassword'), ApiController.changePassword);
apiRouter.get('/auth/providers', ApiController.myProviders);
apiRouter.post('/auth/link', authLimiter, ApiController.linkProvider);

// Video Upload Pipeline
apiRouter.post('/videos/upload-url', validateBody('uploadUrl'), ApiController.requestUploadUrl);
apiRouter.post('/videos/complete-upload', validateBody('completeUpload'), ApiController.completeUpload);

// Feed & Consumption (mode=recommended|trending|new|following)
apiRouter.get('/feed', ApiController.getFeed);
apiRouter.get('/feed/fyp', ApiController.getFeed);
apiRouter.get('/videos', ApiController.getFeed);
apiRouter.get('/external/videos', ApiController.getExternalVideos);
apiRouter.get('/videos/:videoId/stream', ApiController.streamVideo);
apiRouter.get('/videos/:videoId/thumbnail', ApiController.getVideoThumbnail);
apiRouter.post('/videos/:videoId/view', ApiController.recordView);
apiRouter.delete('/videos/:videoId', ApiController.deleteVideo);

// Interactions (Likes, Comments, Shares, Saves)
apiRouter.post('/videos/:videoId/like', ApiController.toggleLike);
apiRouter.post('/videos/:videoId/share', ApiController.recordShare);
apiRouter.post('/videos/:videoId/save', ApiController.toggleSave);
apiRouter.get('/videos/:videoId/comments', ApiController.getComments);
apiRouter.post('/videos/:videoId/comments', validateBody('comment'), ApiController.addComment);
apiRouter.delete('/videos/:videoId/comments/:commentId', ApiController.deleteComment);

// Search & Hashtags
apiRouter.get('/search', ApiController.search);
apiRouter.get('/hashtags/:tag/videos', ApiController.getHashtagVideos);

// User Profile & Social Graph
apiRouter.get('/users/:userId/profile', ApiController.getUserProfile);
apiRouter.patch('/users/:userId', validateBody('updateUser'), ApiController.updateUser);
apiRouter.delete('/users/:userId', ApiController.deleteUserAccount);
apiRouter.post('/users/:userId/follow', ApiController.followUser);
apiRouter.delete('/users/:userId/follow', ApiController.unfollowUser);
apiRouter.get('/users/:userId/follow-state', ApiController.followState);
apiRouter.get('/users/:userId/followers', ApiController.getFollowers);
apiRouter.get('/users/:userId/following', ApiController.getFollowing);
apiRouter.get('/users/:userId/saved', ApiController.getSavedVideos);
apiRouter.get('/users/:userId/liked', ApiController.getLikedVideos);

// Notifications
apiRouter.get('/users/:userId/notifications', ApiController.getNotifications);
apiRouter.post('/users/:userId/notifications/read', validateBody('notificationsRead'), ApiController.readNotifications);

// Reports & Moderation
apiRouter.post('/reports', reportLimiter, validateBody('report'), ApiController.submitReport);
apiRouter.get('/admin/reports', ApiController.listReports);
apiRouter.post('/admin/reports/:reportId/resolve', validateBody('resolveReport'), ApiController.resolveReport);
apiRouter.get('/admin/logins', ApiController.listLogins);
apiRouter.get('/admin/overview', ApiController.adminOverview);
apiRouter.get('/admin/users', ApiController.listUsers);
apiRouter.post('/admin/users/:userId/status', validateBody('setUserStatus'), ApiController.setUserStatus);
apiRouter.post('/admin/users/:userId/coins', validateBody('adjustCoins'), ApiController.adjustUserCoins);
apiRouter.get('/wallet', ApiController.getMyCoins);
apiRouter.post('/wallet/earn', validateBody('walletEarn'), ApiController.earnCoins);
apiRouter.post('/wallet/spend', validateBody('walletSpend'), ApiController.spendCoins);
