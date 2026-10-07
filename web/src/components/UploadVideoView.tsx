import React, { useState, useRef } from 'react';
import { X, Upload, Film, CheckCircle2, RotateCcw, Hash, Music, Shield, Sparkles } from 'lucide-react';
import { api } from '../services/api';
import { VideoItem } from '../types';

interface UploadVideoViewProps {
  isOpen: boolean;
  onClose: () => void;
  onVideoPublished: (video: VideoItem) => void;
  initialVideoUrl?: string;
}

export const UploadVideoView: React.FC<UploadVideoViewProps> = ({ 
  isOpen, 
  onClose, 
  onVideoPublished,
  initialVideoUrl 
}) => {
  // No default sample video: publishing requires a real video from the
  // device (file picker) or a real recording from the camera studio.
  const [videoUrl, setVideoUrl] = useState<string>(initialVideoUrl || '');
  const [caption, setCaption] = useState('فيديو جديد على ZEVORA 🇩🇿 شاركونا رأيكم! #الجزائر #dz #trending');
  const [isUploading, setIsUploading] = useState(false);
  const [privacy, setPrivacy] = useState<'public' | 'friends' | 'private'>('public');
  const [allowComments, setAllowComments] = useState(true);

  const fileInputRef = useRef<HTMLInputElement | null>(null);

  if (!isOpen) return null;

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const url = URL.createObjectURL(file);
      setVideoUrl(url);
    }
  };

  const handleAddHashtag = (tag: string) => {
    if (!caption.includes(tag)) {
      setCaption((prev) => `${prev} ${tag}`);
    }
  };

  // Captures a real thumbnail frame from the actual video (no stock images).
  const captureThumbnail = (src: string): Promise<string> => {
    return new Promise((resolve) => {
      try {
        const video = document.createElement('video');
        video.muted = true;
        (video as any).playsInline = true;
        video.preload = 'auto';
        video.src = src;
        // NOTE: never revoke src here — the blob URL belongs to the caller
        // and is still needed for playback after publishing.
        const done = (thumb: string) => {
          video.src = '';
          resolve(thumb);
        };
        video.onloadeddata = () => {
          try {
            video.currentTime = Math.min(0.5, (video.duration || 1) / 2);
          } catch {
            done('');
          }
        };
        video.onseeked = () => {
          try {
            const canvas = document.createElement('canvas');
            canvas.width = video.videoWidth || 360;
            canvas.height = video.videoHeight || 640;
            canvas.getContext('2d')?.drawImage(video, 0, 0, canvas.width, canvas.height);
            done(canvas.toDataURL('image/jpeg', 0.6));
          } catch {
            done('');
          }
        };
        video.onerror = () => done('');
        setTimeout(() => done(''), 4000);
      } catch {
        resolve('');
      }
    });
  };

  const handlePublish = async () => {
    if (!videoUrl) {
      alert('اختر فيديو حقيقياً من جهازك أولاً');
      return;
    }
    if (!caption.trim()) {
      alert('يرجى كتابة وصف للفيديو');
      return;
    }
    setIsUploading(true);

    try {
      const thumbnail = await captureThumbnail(videoUrl);
      const newVid = await api.addVideo({
        url: videoUrl,
        thumbnail,
        caption,
        tags: ['algeria', 'dz', 'zevora', 'video'],
      });
      setIsUploading(false);
      onVideoPublished(newVid);
      onClose();
    } catch (e: any) {
      setIsUploading(false);
      alert(e?.message || 'حدث خطأ أثناء النشر');
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/90 backdrop-blur-md flex items-end sm:items-center justify-center p-0 sm:p-4 overflow-y-auto" dir="rtl">
      <div className="bg-zinc-900 border border-zinc-800 rounded-t-3xl sm:rounded-2xl w-full max-w-lg max-h-[92vh] overflow-y-auto shadow-2xl flex flex-col">
        {/* Header */}
        <div className="sticky top-0 bg-zinc-900/95 backdrop-blur-md p-4 border-b border-zinc-800 flex items-center justify-between z-10">
          <div className="flex items-center gap-2">
            <Film className="w-5 h-5 text-pink-400" />
            <h3 className="font-bold text-white text-base">نشر فيديو جديد</h3>
          </div>
          <button onClick={onClose} className="p-1 rounded-full text-zinc-400 hover:text-white bg-zinc-800">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="p-4 space-y-4 flex-1 overflow-y-auto">
          {/* Video Preview & File input */}
          <div className="relative rounded-2xl overflow-hidden bg-black border border-zinc-800 h-64 flex items-center justify-center">
            {videoUrl ? (
              <video
                src={videoUrl}
                autoPlay
                loop
                muted
                playsInline
                className="w-full h-full object-contain"
              />
            ) : (
              <button
                onClick={() => fileInputRef.current?.click()}
                className="flex flex-col items-center gap-3 p-6 text-center"
              >
                <div className="w-16 h-16 rounded-full bg-zinc-800 border border-zinc-700 flex items-center justify-center">
                  <Film className="w-8 h-8 text-zinc-400" />
                </div>
                <div>
                  <div className="text-sm font-bold text-white">اختر فيديو من جهازك</div>
                  <div className="text-xs text-zinc-500 mt-1">لن يُنشر أي فيديو تجريبي — فقط ما تختاره أنت</div>
                </div>
              </button>
            )}
            {/* Change video button */}
            {videoUrl ? (
              <button
                onClick={() => fileInputRef.current?.click()}
                className="absolute bottom-3 left-3 flex items-center gap-1.5 py-1.5 px-3 rounded-full bg-black/70 backdrop-blur-md border border-white/20 text-white text-xs font-medium hover:bg-black/90 transition-colors"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                <span>اختيار فيديو آخر من الجهاز</span>
              </button>
            ) : null}
            <input
              type="file"
              ref={fileInputRef}
              onChange={handleFileChange}
              accept="video/*"
              className="hidden"
            />
          </div>

          {/* Caption textarea */}
          <div className="space-y-1.5 text-right">
            <label className="text-xs font-bold text-zinc-300">وصف الفيديو والهاشتاغات:</label>
            <textarea
              value={caption}
              onChange={(e) => setCaption(e.target.value)}
              rows={3}
              placeholder="اكتب وصفاً جذاباً للفيديو مع الوسوم..."
              className="w-full bg-zinc-950 border border-zinc-700 rounded-xl p-3 text-xs text-white placeholder-zinc-500 focus:outline-none focus:border-pink-500 resize-none"
            />
          </div>

          {/* Quick Hashtags */}
          <div className="space-y-1.5">
            <span className="text-[11px] text-zinc-400">هاشتاغات مقترحة شائعة:</span>
            <div className="flex flex-wrap gap-1.5">
              {['#أغاني', '#موسيقى', '#طرب', '#dance', '#طبخ', '#dz', '#algerie', '#explore'].map((tag) => (
                <button
                  key={tag}
                  onClick={() => handleAddHashtag(tag)}
                  className="px-2.5 py-1 rounded-lg bg-zinc-800 hover:bg-zinc-700 text-zinc-300 hover:text-white text-[11px] font-medium transition-colors"
                >
                  {tag}
                </button>
              ))}
            </div>
          </div>

          {/* Settings list */}
          <div className="space-y-2 pt-2 border-t border-zinc-800 text-xs">
            {/* Privacy */}
            <div className="flex items-center justify-between p-3 rounded-xl bg-zinc-950 border border-zinc-800">
              <span className="text-zinc-300 font-medium">من يمكنه مشاهدة هذا الفيديو:</span>
              <select
                value={privacy}
                onChange={(e) => setPrivacy(e.target.value as any)}
                className="bg-zinc-800 text-white text-xs rounded-lg px-2.5 py-1 border border-zinc-700"
              >
                <option value="public">الجميع (عام)</option>
                <option value="friends">الأصدقاء فقط</option>
                <option value="private">أنا فقط (خاص)</option>
              </select>
            </div>

            {/* Allow comments */}
            <div className="flex items-center justify-between p-3 rounded-xl bg-zinc-950 border border-zinc-800">
              <span className="text-zinc-300 font-medium">السماح بالتعليقات:</span>
              <input
                type="checkbox"
                checked={allowComments}
                onChange={(e) => setAllowComments(e.target.checked)}
                className="w-4 h-4 accent-pink-500 rounded"
              />
            </div>
          </div>

          {/* Publish CTA */}
          <button
            onClick={handlePublish}
            disabled={isUploading}
            className="w-full py-3.5 px-4 rounded-xl font-bold bg-gradient-to-r from-pink-600 via-rose-600 to-cyan-600 hover:from-pink-500 hover:to-cyan-500 text-white shadow-xl shadow-pink-600/25 active:scale-[0.98] transition-all text-sm flex items-center justify-center gap-2 disabled:opacity-50"
          >
            {isUploading ? (
              <span>جاري النشر وتجهيز الفيديو...</span>
            ) : (
              <>
                <Upload className="w-4 h-4" />
                <span>نشر الفيديو فوراً على ZEVORA</span>
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
};
