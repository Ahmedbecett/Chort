import express, { Request, Response, NextFunction } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import jwt from 'jsonwebtoken';
import { config } from './config';
import { prisma } from './lib/prisma';
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

// Minimal request log: method + path + status + latency only.
// Never logs headers, bodies, tokens, or keys.
app.use((req: Request, res: Response, next: NextFunction) => {
  const start = Date.now();
  res.on('finish', () => {
    const ms = Date.now() - start;
    const path = req.path.length > 160 ? `${req.path.slice(0, 160)}…` : req.path;
    console.log(`${req.method} ${path} -> ${res.statusCode} (${ms}ms)`);
  });
  next();
});

// Optional JWT Authenticator. Tokens carrying a jti are valid only while
// their Session row exists, which makes logout/revoke GLOBAL (works across
// serverless instances with plain PostgreSQL, no shared Redis required).
// DB errors fail open (availability first); a definitively-missing row fails
// closed. Pre-jti tokens keep working unchanged.
app.use(async (req: Request, res: Response, next: NextFunction) => {
  const authHeader = req.headers.authorization;
  if (authHeader && authHeader.startsWith('Bearer ')) {
    const token = authHeader.substring(7);
    try {
      const decoded = jwt.verify(token, config.jwtSecret) as any;
      let revoked = false;
      if (decoded?.jti) {
        try {
          const row = await prisma.session.findUnique({
            where: { token: decoded.jti },
            select: { id: true },
          });
          if (row === null) revoked = true;
        } catch {
          revoked = false;
        }
      }
      if (revoked) {
        (req as any).userRevoked = true;
      } else {
        (req as any).user = decoded;
      }
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
    app: 'Rivo High-Scale Video Platform Backend',
    version: '3.0.0',
    documentation: '/api/v1/health',
    status: 'ONLINE',
  });
});

// JSON 404 (must come after all routes)
app.use((req: Request, res: Response) => {
  res.status(404).json({ error: `Route not found: ${req.method} ${req.path}` });
});

// JSON error boundary (must come last; never leaks internals)
app.use((err: unknown, req: Request, res: Response, _next: NextFunction) => {
  const message = err instanceof Error ? err.message : 'Internal server error';
  const status = (err as { statusCode?: number }).statusCode || 500;
  if (status >= 500) {
    console.error(`Unhandled ${req.method} ${req.path}:`, message.slice(0, 300));
  }
  res.status(status).json({ error: status === 500 ? 'Internal server error' : message });
});

if (!process.env.VERCEL) {
  app.listen(config.port, '0.0.0.0', () => {
    console.log(`🚀 Rivo API Server running on port ${config.port} (Production Mode)`);
  });
}

export default app;
