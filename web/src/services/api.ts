import { VideoItem, UserProfile } from '../types';
import { INITIAL_USER, INITIAL_VIDEOS, INITIAL_COMMENTS } from '../data/mockData';

const STORAGE_KEYS = {
  USER: 'chort_user_profile',
  VIDEOS: 'chort_videos_db',
  COMMENTS: 'chort_comments_db',
  WATCH_HISTORY: 'chort_watch_history',
  OFFLINE_VIDEOS: 'chort_offline_videos',
  AUTH_TOKEN: 'chort_auth_token',
  AUTH_LOGGED_IN: 'chort_logged_in',
};

export const api = {
  // Authentication
  getAuthStatus(): boolean {
    return localStorage.getItem(STORAGE_KEYS.AUTH_LOGGED_IN) === 'true';
  },

  setAuthStatus(loggedIn: boolean) {
    if (loggedIn) {
      localStorage.setItem(STORAGE_KEYS.AUTH_LOGGED_IN, 'true');
    } else {
      localStorage.removeItem(STORAGE_KEYS.AUTH_LOGGED_IN);
      localStorage.removeItem(STORAGE_KEYS.AUTH_TOKEN);
    }
  },

  async sendOtp(target: string, type: 'email' | 'phone'): Promise<{ success: boolean; code: string; message: string }> {
    try {
      const res = await fetch('/api/auth/send-otp', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ target, type }),
      });
      const data = await res.json().catch(() => null);
      if (res.ok && data) return data;
      return {
        success: false,
        code: '',
        message: data?.error || data?.message || 'تعذر إرسال رمز التحقق من الخادم.',
      };
    } catch {
      return {
        success: false,
        code: '',
        message: 'تعذر الاتصال بخادم التحقق. تحقق من الاتصال ثم أعد المحاولة.',
      };
    }
  },

  async verifyOtp(target: string, code: string): Promise<{ success: boolean; message: string }> {
    try {
      const res = await fetch('/api/auth/verify-otp', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ target, code }),
      });
      const data = await res.json().catch(() => null);
      if (res.ok && data?.success) {
        api.setAuthStatus(true);
        return data;
      }
      return {
        success: false,
        message: data?.message || 'رمز التحقق غير صحيح، يرجى المحاولة مرة أخرى.',
      };
    } catch {
      return {
        success: false,
        message: 'تعذر الاتصال بخادم التحقق. تحقق من الاتصال ثم أعد المحاولة.',
      };
    }
  },

  // User Profile
  getUser(): UserProfile {
    const cached = localStorage.getItem(STORAGE_KEYS.USER);
    if (cached) {
      try {
        return JSON.parse(cached);
      } catch {
        // ignore
      }
    }
    localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(INITIAL_USER));
    return INITIAL_USER;
  },

  updateUser(updates: Partial<UserProfile>): UserProfile {
    const current = api.getUser();
    const updated = { ...current, ...updates };
    localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(updated));
    return updated;
  },

  // Videos
  // Purges previously stored sample/demo videos (Google sample bucket,
  // unsplash placeholders) so no fake media is ever served to the user.
  purgeStoredSampleVideos(videos: VideoItem[]): { cleaned: VideoItem[]; removed: number } {
    const FAKE_MARKERS = ['gtv-videos-bucket', 'commondatastorage.googleapis.com', 'images.unsplash.com', 'sample.mp4'];
    const cleaned = videos.filter(
      (v) => !FAKE_MARKERS.some((m) => (v.url || '').includes(m) || (v.thumbnail || '').includes(m))
    );
    return { cleaned, removed: videos.length - cleaned.length };
  },

  async getVideos(): Promise<VideoItem[]> {
    try {
      const res = await fetch('/api/videos');
      if (res.ok) {
        const data = await res.json();
        if (Array.isArray(data) && data.length > 0) {
          const { cleaned } = api.purgeStoredSampleVideos(data);
          localStorage.setItem(STORAGE_KEYS.VIDEOS, JSON.stringify(cleaned));
          return cleaned;
        }
      }
    } catch {
      // fallback to storage
    }

    const cached = localStorage.getItem(STORAGE_KEYS.VIDEOS);
    if (cached) {
      try {
        const parsed: VideoItem[] = JSON.parse(cached);
        const { cleaned, removed } = api.purgeStoredSampleVideos(
          Array.isArray(parsed) ? parsed : []
        );
        if (removed > 0) {
          localStorage.setItem(STORAGE_KEYS.VIDEOS, JSON.stringify(cleaned));
        }
        return cleaned;
      } catch {
        // ignore
      }
    }
    localStorage.setItem(STORAGE_KEYS.VIDEOS, JSON.stringify(INITIAL_VIDEOS));
    return INITIAL_VIDEOS;
  },

  async toggleLikeVideo(videoId: string): Promise<{ liked: boolean; count: number }> {
    const videos = await api.getVideos();
    const index = videos.findIndex(v => v.id === videoId);
    if (index === -1) return { liked: false, count: 0 };

    const video = videos[index];
    const newLiked = !video.isLiked;
    const newCount = newLiked ? video.likes + 1 : Math.max(0, video.likes - 1);

    videos[index] = {
      ...video,
      isLiked: newLiked,
      likes: newCount,
    };
    localStorage.setItem(STORAGE_KEYS.VIDEOS, JSON.stringify(videos));

    try {
      await fetch(`/api/videos/${videoId}/like`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ liked: newLiked }),
      });
    } catch {
      // ignore
    }

    return { liked: newLiked, count: newCount };
  },

  async toggleSaveVideo(videoId: string): Promise<{ saved: boolean }> {
    const videos = await api.getVideos();
    const index = videos.findIndex(v => v.id === videoId);
    if (index === -1) return { saved: false };

    const video = videos[index];
    const newSaved = !video.isSaved;
    videos[index] = {
      ...video,
      isSaved: newSaved,
      saves: newSaved ? video.saves + 1 : Math.max(0, video.saves - 1),
    };
    localStorage.setItem(STORAGE_KEYS.VIDEOS, JSON.stringify(videos));
    return { saved: newSaved };
  },

  async addVideo(videoData: Partial<VideoItem>): Promise<VideoItem> {
    const user = api.getUser();
    if (!videoData.url || !videoData.url.trim()) {
      throw new Error('لا يمكن النشر بدون فيديو حقيقي من جهازك');
    }
    const newVideo: VideoItem = {
      id: `vid-${Date.now()}`,
      url: videoData.url as string,
      thumbnail: videoData.thumbnail || '',
      caption: videoData.caption || '',
      author: {
        id: user.id,
        name: user.name,
        username: user.username,
        avatar: user.avatar,
        isVerified: user.isVerified,
      },
      song: videoData.song || {
        title: 'Original Sound - ' + user.name,
        artist: user.name,
      },
      likes: 0,
      commentsCount: 0,
      shares: 0,
      saves: 0,
      views: 1,
      tags: videoData.tags || ['zevora', 'dz', 'video'],
      isLiked: false,
      isSaved: false,
      createdAt: new Date().toISOString(),
      category: 'trending',
    };

    const videos = await api.getVideos();
    const updatedVideos = [newVideo, ...videos];
    localStorage.setItem(STORAGE_KEYS.VIDEOS, JSON.stringify(updatedVideos));

    try {
      await fetch('/api/videos', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(newVideo),
      });
    } catch {
      // ignore
    }

    return newVideo;
  },

  // Comments
  getComments(videoId: string) {
    const cached = localStorage.getItem(STORAGE_KEYS.COMMENTS);
    let all = INITIAL_COMMENTS;
    if (cached) {
      try {
        all = JSON.parse(cached);
      } catch {
        // ignore
      }
    }
    return all.filter(c => c.videoId === videoId);
  },

  addComment(videoId: string, text: string) {
    const user = api.getUser();
    const newComment = {
      id: `c-${Date.now()}`,
      videoId,
      username: user.username,
      userAvatar: user.avatar,
      text,
      likes: 0,
      timestamp: 'الآن',
    };

    const cached = localStorage.getItem(STORAGE_KEYS.COMMENTS);
    const all = cached ? JSON.parse(cached) : INITIAL_COMMENTS;
    all.unshift(newComment);
    localStorage.setItem(STORAGE_KEYS.COMMENTS, JSON.stringify(all));

    // increment comment count on video
    api.getVideos().then(videos => {
      const idx = videos.findIndex(v => v.id === videoId);
      if (idx !== -1) {
        videos[idx].commentsCount += 1;
        localStorage.setItem(STORAGE_KEYS.VIDEOS, JSON.stringify(videos));
      }
    });

    return newComment;
  },

  // History & Offline
  recordWatchHistory(video: VideoItem) {
    const cached = localStorage.getItem(STORAGE_KEYS.WATCH_HISTORY);
    let history: any[] = cached ? JSON.parse(cached) : [];
    history = history.filter(h => h.videoId !== video.id);
    history.unshift({
      id: `hist-${Date.now()}`,
      videoId: video.id,
      thumbnail: video.thumbnail,
      title: video.caption.slice(0, 45) + '...',
      author: video.author.name,
      watchedAt: new Date().toLocaleTimeString('ar-DZ', { hour: '2-digit', minute: '2-digit' }),
      duration: '0:35',
    });
    localStorage.setItem(STORAGE_KEYS.WATCH_HISTORY, JSON.stringify(history.slice(0, 30)));
  },

  getWatchHistory() {
    const cached = localStorage.getItem(STORAGE_KEYS.WATCH_HISTORY);
    return cached ? JSON.parse(cached) : [];
  },

  clearWatchHistory() {
    localStorage.removeItem(STORAGE_KEYS.WATCH_HISTORY);
  },

  getOfflineVideos() {
    const cached = localStorage.getItem(STORAGE_KEYS.OFFLINE_VIDEOS);
    return cached ? JSON.parse(cached) : [];
  },

  async saveOfflineVideo(video: VideoItem): Promise<void> {
    const offline = api.getOfflineVideos();
    if (offline.some((v: any) => v.id === video.id)) return;
    // Real size when the bytes are reachable (blob/local URLs); otherwise
    // honestly unknown — never a random number.
    let fileSizeMb = '';
    try {
      const res = await fetch(video.url);
      if (res.ok) {
        const blob = await res.blob();
        if (blob.size > 0) fileSizeMb = (blob.size / (1024 * 1024)).toFixed(1);
      }
    } catch {
      fileSizeMb = '';
    }
    offline.unshift({
      ...video,
      downloadedAt: new Date().toISOString(),
      fileSizeMb,
    });
    localStorage.setItem(STORAGE_KEYS.OFFLINE_VIDEOS, JSON.stringify(offline));
  },

  removeOfflineVideo(videoId: string) {
    let offline = api.getOfflineVideos();
    offline = offline.filter((v: any) => v.id !== videoId);
    localStorage.setItem(STORAGE_KEYS.OFFLINE_VIDEOS, JSON.stringify(offline));
  },
};
