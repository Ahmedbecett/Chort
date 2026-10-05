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
apiRouter.post('/videos/:videoId/view', ApiController.recordView);

// Interactions (Likes, Comments)
apiRouter.post('/videos/:videoId/like', ApiController.toggleLike);
apiRouter.get('/videos/:videoId/comments', ApiController.getComments);
apiRouter.post('/videos/:videoId/comments', ApiController.addComment);

// Search
apiRouter.get('/search', ApiController.search);

// User Profile
apiRouter.get('/users/:userId/profile', ApiController.getUserProfile);
