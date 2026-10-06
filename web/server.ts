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
  let mockVideos = [
    {
      id: 'vid-01',
      url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4',
      thumbnail: 'https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop&q=80',
      caption: 'سحر الطبيعة والغروب من أعالي جبال جرجرة 🏔️🇩🇿 شاركونا رأيكم في التعليقات! #الجزائر #طبيعة #dz #tiktok #explore',
      author: {
        id: 'u_skanpo15',
        name: 'SKANPO Officiel 🇩🇿',
        username: 'skanpo15',
        avatar: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400&auto=format&fit=crop&q=80',
        isVerified: true,
      },
      song: {
        title: 'Zina - Babylone (Remix 2026)',
        artist: 'Babylone Official',
      },
      likes: 486,
      commentsCount: 94,
      shares: 112,
      saves: 85,
      views: 4860,
      tags: ['الجزائر', 'طبيعة', 'dz', 'explore', 'algerie'],
      isLiked: false,
      isSaved: false,
      isPinned: true,
      category: 'nature',
      createdAt: new Date().toISOString(),
    },
    {
      id: 'vid-02',
      url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4',
      thumbnail: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600&auto=format&fit=crop&q=80',
      caption: 'أجمل شواطئ وهران عين الترك في الخريف 🌊 صيف مكملش عندنا! #وهران #oran #dzair #sea #beach #trend',
      author: {
        id: 'u_skanpo15',
        name: 'SKANPO Officiel 🇩🇿',
        username: 'skanpo15',
        avatar: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400&auto=format&fit=crop&q=80',
        isVerified: true,
      },
      song: {
        title: 'Cheb Khaled - Didi (Live Stage Mix)',
        artist: 'Cheb Khaled',
      },
      likes: 634,
      commentsCount: 148,
      shares: 203,
      saves: 119,
      views: 6340,
      tags: ['وهران', 'oran', 'dzair', 'sea', 'beach'],
      isLiked: true,
      isSaved: true,
      category: 'trending',
      createdAt: new Date().toISOString(),
    },
    {
      id: 'vid-03',
      url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4',
      thumbnail: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80',
      caption: 'عزف حي على آلة الموندول الجزائري 🎶 تراث الشعبي الأصيل لا يموت #شعبي #algiers #chaabi #موسيقى #فن',
      author: {
        id: 'u_skanpo15',
        name: 'SKANPO Officiel 🇩🇿',
        username: 'skanpo15',
        avatar: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400&auto=format&fit=crop&q=80',
        isVerified: true,
      },
      song: {
        title: 'Qamar Al-Layl - Mandole Solo',
        artist: 'Dahmane El Harrachi Tribute',
      },
      likes: 421,
      commentsCount: 67,
      shares: 88,
      saves: 95,
      views: 4210,
      tags: ['شعبي', 'algiers', 'chaabi', 'موسيقى', 'فن'],
      isLiked: false,
      isSaved: false,
      category: 'music',
      createdAt: new Date().toISOString(),
    },
    {
      id: 'vid-04',
      url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyBlazes.mp4',
      thumbnail: 'https://images.unsplash.com/photo-1517841905240-472988babdf9?w=600&auto=format&fit=crop&q=80',
      caption: 'تحدي الطبخ الجزائري: طاجين الزيتون بأسهل طريقة في 60 ثانية 🍲 جربوها وقولولي! #طبخ #وصفات #tajine #dzfood',
      author: {
        id: 'u_amina_cook',
        name: 'Amina Cooking 👩‍🍳',
        username: 'amina.dz.chef',
        avatar: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=400&auto=format&fit=crop&q=80',
        isVerified: true,
      },
      song: {
        title: 'Lo-Fi Chill Beats Algeria',
        artist: 'Chort Sound Studio',
      },
      likes: 1820,
      commentsCount: 312,
      shares: 490,
      saves: 620,
      views: 18200,
      tags: ['طبخ', 'وصفات', 'tajine', 'dzfood'],
      isLiked: false,
      isSaved: false,
      category: 'trending',
      createdAt: new Date().toISOString(),
    },
    {
      id: 'vid-05',
      url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4',
      thumbnail: 'https://images.unsplash.com/photo-1492691527719-9d1e07e534b4?w=600&auto=format&fit=crop&q=80',
      caption: 'سرعة ودريفت سيارات على حلبة سباق رياضية 🏎️💨 صوت المحرك إدمان! #drift #cars #speed #adrenaline #sport',
      author: {
        id: 'u_speed_dz',
        name: 'Turbo DZ 🏎️',
        username: 'turbo_motors',
        avatar: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=400&auto=format&fit=crop&q=80',
        isVerified: false,
      },
      song: {
        title: 'Phonk Tokyo Drift Bass Boosted',
        artist: 'GhostFace Phonk',
      },
      likes: 3410,
      commentsCount: 520,
      shares: 890,
      saves: 412,
      views: 45000,
      tags: ['drift', 'cars', 'speed', 'adrenaline'],
      isLiked: true,
      isSaved: false,
      category: 'trending',
      createdAt: new Date().toISOString(),
    },
    {
      id: 'vid-06',
      url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4',
      thumbnail: 'https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=600&auto=format&fit=crop&q=80',
      caption: 'أفضل مقطع سينمائي خيال علمي 4K بالذكاء الاصطناعي 🎬 إبداع بلا حدود! #cinema #vfx #future #ai #scifi',
      author: {
        id: 'u_cinema_vfx',
        name: 'VFX Masters Studio',
        username: 'vfx.masters',
        avatar: 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=400&auto=format&fit=crop&q=80',
        isVerified: true,
      },
      song: {
        title: 'Cinematic Epic Hans Style',
        artist: 'Soundtrack Lab',
      },
      likes: 8900,
      commentsCount: 1140,
      shares: 2400,
      saves: 3100,
      views: 98000,
      tags: ['cinema', 'vfx', 'future', 'ai'],
      isLiked: false,
      isSaved: true,
      category: 'trending',
      createdAt: new Date().toISOString(),
    }
  ];

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
      tag_name: 'v2.4.4',
      name: 'Chort - الإصدار الأحدث المستقر v2.4.4',
      download_url: 'https://github.com/Ahmedbecett/Chort/releases/download/v2.4.4/Chort-latest.apk',
      html_url: 'https://github.com/Ahmedbecett/Chort/releases/tag/v2.4.4',
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
