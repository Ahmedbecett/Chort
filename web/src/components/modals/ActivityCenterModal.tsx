import React, { useState, useEffect } from 'react';
import { X, History, Trash2, Heart, Search, Play, CheckCircle2 } from 'lucide-react';
import { api } from '../../services/api';
import { VideoItem } from '../../types';

interface ActivityCenterModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelectVideo: (video: VideoItem) => void;
}

export const ActivityCenterModal: React.FC<ActivityCenterModalProps> = ({ isOpen, onClose, onSelectVideo }) => {
  const [activeTab, setActiveTab] = useState<'watch' | 'likes' | 'search'>('watch');
  const [watchHistory, setWatchHistory] = useState<any[]>([]);
  const [likedVideos, setLikedVideos] = useState<VideoItem[]>([]);
  const [searchHistory, setSearchHistory] = useState<string[]>([
    'أغاني الراي 2026',
    'وصفات طاجين جزائري',
    'دريفت وهران',
    'جبال جرجرة تيزي وزو',
  ]);
  const [notice, setNotice] = useState('');

  useEffect(() => {
    if (isOpen) {
      setWatchHistory(api.getWatchHistory());
      api.getVideos().then((vids) => {
        setLikedVideos(vids.filter((v) => v.isLiked));
      });
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const handleClearWatchHistory = () => {
    api.clearWatchHistory();
    setWatchHistory([]);
    setNotice('تم مسح سجل المشاهدة بالكامل');
    setTimeout(() => setNotice(''), 2000);
  };

  const handleRemoveSearchTerm = (term: string) => {
    setSearchHistory(searchHistory.filter((t) => t !== term));
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/80 backdrop-blur-sm p-0 sm:p-4">
      <div 
        className="bg-zinc-900 border border-zinc-800 rounded-t-3xl sm:rounded-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto shadow-2xl flex flex-col"
        dir="rtl"
      >
        {/* Header */}
        <div className="sticky top-0 bg-zinc-900/95 backdrop-blur-md p-4 border-b border-zinc-800 flex items-center justify-between z-10">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-pink-500/10 flex items-center justify-center text-pink-400">
              <History className="w-5 h-5" />
            </div>
            <h3 className="font-bold text-white text-base">مركز النشاط (Activity Center)</h3>
          </div>
          <button onClick={onClose} className="p-1 rounded-full text-zinc-400 hover:text-white bg-zinc-800">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Notice */}
        {notice && (
          <div className="m-4 p-3 bg-emerald-950/80 border border-emerald-800 rounded-xl flex items-center gap-2 text-xs text-emerald-300">
            <CheckCircle2 className="w-4 h-4 shrink-0 text-emerald-400" />
            <span>{notice}</span>
          </div>
        )}

        {/* Tab switch */}
        <div className="p-4 grid grid-cols-3 gap-2 border-b border-zinc-800">
          <button
            onClick={() => setActiveTab('watch')}
            className={`py-2 px-3 rounded-xl text-xs font-semibold flex items-center justify-center gap-1.5 transition-all ${
              activeTab === 'watch' ? 'bg-pink-600 text-white' : 'bg-zinc-800 text-zinc-400 hover:bg-zinc-700'
            }`}
          >
            <History className="w-3.5 h-3.5" /> سجل المشاهدة
          </button>
          <button
            onClick={() => setActiveTab('likes')}
            className={`py-2 px-3 rounded-xl text-xs font-semibold flex items-center justify-center gap-1.5 transition-all ${
              activeTab === 'likes' ? 'bg-pink-600 text-white' : 'bg-zinc-800 text-zinc-400 hover:bg-zinc-700'
            }`}
          >
            <Heart className="w-3.5 h-3.5" /> سجل الإعجابات
          </button>
          <button
            onClick={() => setActiveTab('search')}
            className={`py-2 px-3 rounded-xl text-xs font-semibold flex items-center justify-center gap-1.5 transition-all ${
              activeTab === 'search' ? 'bg-pink-600 text-white' : 'bg-zinc-800 text-zinc-400 hover:bg-zinc-700'
            }`}
          >
            <Search className="w-3.5 h-3.5" /> سجل البحث
          </button>
        </div>

        {/* Content */}
        <div className="p-4 flex-1 overflow-y-auto space-y-3">
          {/* Watch history */}
          {activeTab === 'watch' && (
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs text-zinc-400">الفيديوهات التي شاهدتها مؤخراً ({watchHistory.length})</span>
                {watchHistory.length > 0 && (
                  <button
                    onClick={handleClearWatchHistory}
                    className="text-xs text-red-400 hover:text-red-300 flex items-center gap-1"
                  >
                    <Trash2 className="w-3 h-3" /> مسح السجل
                  </button>
                )}
              </div>

              {watchHistory.length === 0 ? (
                <div className="text-center py-12 text-zinc-500 text-xs">
                  لا توجد فيديوهات في سجل المشاهدة بعد. شاهد بعض المقاطع في الرئيسية!
                </div>
              ) : (
                watchHistory.map((item) => (
                  <div
                    key={item.id}
                    onClick={() => {
                      api.getVideos().then((vids) => {
                        const target = vids.find((v) => v.id === item.videoId);
                        if (target) {
                          onSelectVideo(target);
                          onClose();
                        }
                      });
                    }}
                    className="p-2.5 rounded-xl bg-zinc-950 border border-zinc-800 flex items-center gap-3 cursor-pointer hover:border-pink-500/40 transition-colors"
                  >
                    <img src={item.thumbnail} alt="" className="w-14 h-18 object-cover rounded-lg shrink-0" />
                    <div className="flex-1 min-w-0">
                      <div className="font-semibold text-xs text-white truncate">{item.title}</div>
                      <div className="text-[11px] text-zinc-400 mt-1">{item.author}</div>
                      <div className="text-[10px] text-zinc-500 mt-0.5">شوهد في: {item.watchedAt}</div>
                    </div>
                    <Play className="w-4 h-4 text-pink-400 shrink-0" />
                  </div>
                ))
              )}
            </div>
          )}

          {/* Likes history */}
          {activeTab === 'likes' && (
            <div className="space-y-3">
              <span className="text-xs text-zinc-400">الفيديوهات التي أُعجبت بها ({likedVideos.length})</span>
              {likedVideos.length === 0 ? (
                <div className="text-center py-12 text-zinc-500 text-xs">
                  لم تعجب بأي فيديو بعد. انقر مرتين على أي مقطع للإعجاب به!
                </div>
              ) : (
                likedVideos.map((video) => (
                  <div
                    key={video.id}
                    onClick={() => {
                      onSelectVideo(video);
                      onClose();
                    }}
                    className="p-2.5 rounded-xl bg-zinc-950 border border-zinc-800 flex items-center gap-3 cursor-pointer hover:border-pink-500/40 transition-colors"
                  >
                    <img src={video.thumbnail} alt="" className="w-14 h-18 object-cover rounded-lg shrink-0" />
                    <div className="flex-1 min-w-0">
                      <div className="font-semibold text-xs text-white line-clamp-2">{video.caption}</div>
                      <div className="text-[11px] text-zinc-400 mt-1">{video.author.name}</div>
                      <div className="text-[10px] text-pink-400 mt-0.5 font-mono">❤️ {video.likes} إعجاب</div>
                    </div>
                    <Play className="w-4 h-4 text-pink-400 shrink-0" />
                  </div>
                ))
              )}
            </div>
          )}

          {/* Search history */}
          {activeTab === 'search' && (
            <div className="space-y-3">
              <span className="text-xs text-zinc-400">عمليات البحث المحفوظة</span>
              <div className="space-y-2">
                {searchHistory.map((term, i) => (
                  <div
                    key={i}
                    className="p-3 rounded-xl bg-zinc-950 border border-zinc-800 flex items-center justify-between text-xs text-zinc-200"
                  >
                    <div className="flex items-center gap-2">
                      <Search className="w-3.5 h-3.5 text-zinc-500" />
                      <span>{term}</span>
                    </div>
                    <button
                      onClick={() => handleRemoveSearchTerm(term)}
                      className="text-zinc-500 hover:text-red-400 p-1"
                    >
                      <X className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
