import express from 'express';
import path from 'path';
import { fileURLToPath } from 'url';
import dotenv from 'dotenv';

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

async function startServer() {
  const app = express();
  const PORT = Number(process.env.PORT) || 3000;

  app.use(express.json());

  // In-memory dev store. Starts empty: only videos really published
  // through POST /api/videos (user uploads) ever appear here.
  let devVideos: any[] = [];

  // Store for generated OTP codes
  // Random per-request OTP codes with 5-minute expiry. Never fixed/demo codes.
  const otpCodes: Record<string, { code: string; expiresAt: number }> = {};

  // REST API Routes
  app.get('/api/videos', (req, res) => {
    const { category, search } = req.query;
    let filtered = [...devVideos];
    if (category && category !== 'all') {
      filtered = filtered.filter(v => v.category === category);
    }
    if (search) {
      const q = String(search).toLowerCase();
      filtered = filtered.filter(v => 
        v.caption.toLowerCase().includes(q) || 
        v.author.name.toLowerCase().includes(q) ||
        v.tags.some((t: string) => t.toLowerCase().includes(q))
      );
    }
    res.json(filtered);
  });

  app.post('/api/videos', (req, res) => {
    const newVideo = req.body;
    if (!newVideo || !newVideo.url) {
      return res.status(400).json({ error: 'Video URL is required' });
    }
    devVideos.unshift(newVideo);
    res.status(201).json(newVideo);
  });

  app.post('/api/videos/:id/like', (req, res) => {
    const { id } = req.params;
    const { liked } = req.body;
    const video = devVideos.find(v => v.id === id);
    if (!video) return res.status(404).json({ error: 'Video not found' });
    
    video.likes = liked ? video.likes + 1 : Math.max(0, video.likes - 1);
    video.isLiked = liked;
    res.json({ success: true, likes: video.likes });
  });

  app.post('/api/auth/send-otp', (req, res) => {
    const { target, type } = req.body;
    if (!target) return res.status(400).json({ error: 'Target email/phone is required' });

    // Dev server: random 6-digit code per request (no SMS/email gateway here).
    // The code is returned so the dev UI can display it; production uses the
    // real backend OTP flow. Never a fixed/demo code.
    const code = String(Math.floor(100000 + Math.random() * 900000));
    otpCodes[target] = { code, expiresAt: Date.now() + 5 * 60 * 1000 };

    res.json({
      success: true,
      code,
      message: type === 'email'
        ? `رمز التحقق (وضع التطوير) للبريد ${target}`
        : `رمز التحقق (وضع التطوير) للرقم ${target}`,
      expiresIn: 300
    });
  });

  app.post('/api/auth/verify-otp', (req, res) => {
    const { target, code } = req.body;
    const record = target ? otpCodes[target] : undefined;
    if (!record) {
      return res.status(400).json({ success: false, message: 'لم يتم طلب رمز لهذا الحساب' });
    }
    if (Date.now() > record.expiresAt) {
      delete otpCodes[target];
      return res.status(400).json({ success: false, message: 'انتهت صلاحية الرمز، اطلب رمزاً جديداً' });
    }
    if (code === record.code) {
      delete otpCodes[target];
      return res.json({ success: true, message: 'تم التحقق بنجاح!' });
    }
    res.status(400).json({ success: false, message: 'رمز التحقق غير صحيح' });
  });

  // Honest analytics: the dev server tracks nothing, so it reports zeros.
  // Real analytics come from the production backend only.
  app.get('/api/analytics', (req, res) => {
    res.json({
      hasData: false,
      viewsLast7Days: [0, 0, 0, 0, 0, 0, 0],
      profileViews: 0,
      newFollowers: 0,
      engagementRate: '0%',
      topAudience: []
    });
  });

  app.get('/api/releases/latest', (req, res) => {
    res.json({
      tag_name: 'v3.0.0',
      name: 'ZEVORA - الإصدار الأحدث المستقر v3.0.0',
      download_url: 'https://github.com/Ahmedbecett/Chort/releases/download/v3.0.0/ZEVORA-latest.apk',
      versioned_download_url: 'https://github.com/Ahmedbecett/Chort/releases/download/v3.0.0/ZEVORA-v3.0.0-release.apk',
      source_zip_url: 'https://github.com/Ahmedbecett/Chort/archive/refs/tags/v3.0.0.zip',
      html_url: 'https://github.com/Ahmedbecett/Chort/releases/tag/v3.0.0',
      repo_url: 'https://github.com/Ahmedbecett/Chort',
    });
  });

  // Setup Vite in middleware mode
  const { createServer: createViteServer } = await import('vite');
  const vite = await createViteServer({
    server: { middlewareMode: true },
    appType: 'spa',
  });

  app.use(vite.middlewares);

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`Server running on http://localhost:${PORT}`);
  });
}

startServer().catch(err => {
  console.error('Failed to start server:', err);
});
