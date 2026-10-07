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

  // In-memory data store with initial seed
  let mockVideos: any[] = [];

  // Store for generated OTP codes
  const otpCodes: Record<string, string> = {
    'demo': '180782'
  };

  // REST API Routes
  app.get('/api/videos', (req, res) => {
    const { category, search } = req.query;
    let filtered = [...mockVideos];
    if (category && category !== 'all') {
      filtered = filtered.filter(v => v.category === category);
    }
    if (search) {
      const q = String(search).toLowerCase();
      filtered = filtered.filter(v => 
        v.caption.toLowerCase().includes(q) || 
        v.author.name.toLowerCase().includes(q) ||
        v.tags.some(t => t.toLowerCase().includes(q))
      );
    }
    res.json(filtered);
  });

  app.post('/api/videos', (req, res) => {
    const newVideo = req.body;
    if (!newVideo || !newVideo.url) {
      return res.status(400).json({ error: 'Video URL is required' });
    }
    mockVideos.unshift(newVideo);
    res.status(201).json(newVideo);
  });

  app.post('/api/videos/:id/like', (req, res) => {
    const { id } = req.params;
    const { liked } = req.body;
    const video = mockVideos.find(v => v.id === id);
    if (!video) return res.status(404).json({ error: 'Video not found' });
    
    video.likes = liked ? video.likes + 1 : Math.max(0, video.likes - 1);
    video.isLiked = liked;
    res.json({ success: true, likes: video.likes });
  });

  app.post('/api/auth/send-otp', (req, res) => {
    const { target, type } = req.body;
    if (!target) return res.status(400).json({ error: 'Target email/phone is required' });
    
    // Generate deterministic or random 6 digit code
    const code = '180782';
    otpCodes[target] = code;

    res.json({
      success: true,
      code,
      message: type === 'email' 
        ? `تم إرسال رمز التحقق OTP إلى البريد ${target}`
        : `تم إرسال رمز التحقق OTP إلى الرقم ${target}`,
      expiresIn: 45
    });
  });

  app.post('/api/auth/verify-otp', (req, res) => {
    const { target, code } = req.body;
    const expected = otpCodes[target] || '180782';
    if (code === expected || code === '180782') {
      return res.json({ success: true, message: 'تم التحقق بنجاح!' });
    }
    res.status(400).json({ success: false, message: 'رمز التحقق غير صحيح' });
  });

  app.get('/api/analytics', (req, res) => {
    res.json({
      viewsLast7Days: [1200, 1900, 2400, 3100, 4800, 6200, 7850],
      profileViews: 3420,
      engagementRate: '14.8%',
      topAudience: [
        { region: 'الجزائر العاصمة', percentage: 42 },
        { region: 'وهران', percentage: 28 },
        { region: 'قسنطينة', percentage: 14 },
        { region: 'فرنسا / المغتربين', percentage: 11 },
        { region: 'أخرى', percentage: 5 },
      ]
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
