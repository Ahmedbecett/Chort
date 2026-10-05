import express, { Request, Response, NextFunction } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import jwt from 'jsonwebtoken';
import { config } from './config';
import { apiRouter } from './routes/api.router';

const app = express();

// Security Middlewares
app.use(helmet({ crossOriginResourcePolicy: { policy: 'cross-origin' } }));
app.use(cors({ origin: '*' }));
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

// Global Rate Limiting (Protects from DDoS / brute force)
const limiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 2000, // Limit each IP
  standardHeaders: true,
  legacyHeaders: false,
});
app.use(limiter);

// Optional JWT Authenticator
app.use((req: Request, res: Response, next: NextFunction) => {
  const authHeader = req.headers.authorization;
  if (authHeader && authHeader.startsWith('Bearer ')) {
    const token = authHeader.substring(7);
    try {
      const decoded = jwt.verify(token, config.jwtSecret) as any;
      (req as any).user = decoded;
    } catch {
      // Ignored for optional token
    }
  }
  next();
});

// Mount API Routes
app.use('/api/v1', apiRouter);

// Root Status
app.get('/', (req, res) => {
  res.json({
    app: 'Chort High-Scale Video Platform Backend',
    version: '2.2.0',
    documentation: '/api/v1/health',
    status: 'ONLINE',
  });
});

if (!process.env.VERCEL) {
  app.listen(config.port, '0.0.0.0', () => {
    console.log(`🚀 Chort API Server running on port ${config.port} (Production Mode)`);
  });
}

export default app;
