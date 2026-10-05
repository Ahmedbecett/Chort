import { Router } from 'express';
import { ApiController } from '../controllers/api.controller';

export const apiRouter = Router();

// Health & Infrastructure Status
apiRouter.get('/health', ApiController.healthCheck);
apiRouter.all('/system/migrate', ApiController.runMigration);
apiRouter.get('/system/storage-check', ApiController.checkStorage);

// Authentication
apiRouter.post('/auth/register', ApiController.register);
apiRouter.post('/auth/login', ApiController.login);

// Video Upload Pipeline
apiRouter.post('/videos/upload-url', ApiController.requestUploadUrl);
apiRouter.post('/videos/complete-upload', ApiController.completeUpload);

// Feed & Consumption
apiRouter.get('/feed', ApiController.getFeed);
apiRouter.get('/feed/fyp', ApiController.getFeed);
apiRouter.get('/videos', ApiController.getFeed);
apiRouter.get('/videos/:videoId/stream', ApiController.streamVideo);
apiRouter.get('/videos/:videoId/thumbnail', ApiController.getVideoThumbnail);
apiRouter.post('/videos/:videoId/view', ApiController.recordView);
apiRouter.delete('/videos/:videoId', ApiController.deleteVideo);

// Interactions (Likes, Comments, Shares, Saves)
apiRouter.post('/videos/:videoId/like', ApiController.toggleLike);
apiRouter.post('/videos/:videoId/share', ApiController.recordShare);
apiRouter.post('/videos/:videoId/save', ApiController.toggleSave);
apiRouter.get('/videos/:videoId/comments', ApiController.getComments);
apiRouter.post('/videos/:videoId/comments', ApiController.addComment);

// Search
apiRouter.get('/search', ApiController.search);

// User Profile
apiRouter.get('/users/:userId/profile', ApiController.getUserProfile);
