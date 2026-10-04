import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import { config } from './config';
import { apiRouter } from './routes/api.router';

const app = express();

// Security Middlewares
app.use(helmet());
app.use(cors({ origin: '*' }));
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

// Global Rate Limiting (Protects from DDoS / brute force)
const limiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 1000, // Limit each IP to 1000 requests per windowMs
  standardHeaders: true,
  legacyHeaders: false,
});
app.use(limiter);

// Mount API Routes
app.use('/api/v1', apiRouter);

// Root Status
app.get('/', (req, res) => {
  res.json({
    app: 'TokPulse High-Scale Video Platform Backend',
    version: '1.1.0',
    documentation: '/api/v1/health',
  });
});

app.listen(config.port, '0.0.0.0', () => {
  console.log(`🚀 TokPulse API Server running on port ${config.port} (Production Mode)`);
});

export default app;
