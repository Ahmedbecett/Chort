import { Router } from 'express';
import { ApiController } from '../controllers/api.controller';

export const apiRouter = Router();

// Health & Infrastructure Status
apiRouter.get('/health', ApiController.healthCheck);

// Authentication
apiRouter.post('/auth/register', ApiController.register);

// Video Upload Pipeline
apiRouter.post('/videos/upload-url', ApiController.requestUploadUrl);
apiRouter.post('/videos/complete-upload', ApiController.completeUpload);

// Feed & Consumption
apiRouter.get('/feed/fyp', ApiController.getFeed);
apiRouter.post('/videos/:videoId/view', ApiController.recordView);
