import React, { useState, useEffect } from 'react';
import { X, WifiOff, Trash2, Play, HardDrive, Download, CheckCircle2 } from 'lucide-react';
import { api } from '../../services/api';
import { VideoItem } from '../../types';

interface OfflineVideosModalProps {
  isOpen: boolean;
  onClose: () => void;
  onPlayOfflineVideo: (video: VideoItem) => void;
}

export const OfflineVideosModal: React.FC<OfflineVideosModalProps> = ({ isOpen, onClose, onPlayOfflineVideo }) => {
  const [offlineList, setOfflineList] = useState<any[]>([]);
  const [notice, setNotice] = useState('');

  useEffect(() => {
    if (isOpen) {
      const items = api.getOfflineVideos();
      if (items.length === 0) {
        // seed 2 offline videos for instant demonstration
        api.getVideos().then((vids) => {
          if (vids.length >= 2) {
            api.saveOfflineVideo(vids[0]);
            api.saveOfflineVideo(vids[1]);
            setOfflineList(api.getOfflineVideos());
          }
        });
      } else {
        setOfflineList(items);
      }
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const totalStorage = offlineList.reduce((acc, v) => acc + (parseFloat(v.fileSizeMb) || 8.5), 0).toFixed(1);

  const handleDelete = (id: string) => {
    api.removeOfflineVideo(id);
    setOfflineList(api.getOfflineVideos());
    setNotice('تم حذف الفيديو من الذاكرة المحلية بنجاح');
    setTimeout(() => setNotice(''), 2000);
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
            <div className="w-8 h-8 rounded-lg bg-cyan-500/10 flex items-center justify-center text-cyan-400">
              <WifiOff className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-bold text-white text-base">فيديوهات بدون إنترنت (Offline)</h3>
              <p className="text-[11px] text-zinc-400">شاهد مقاطعك المفضلة دون استهلاك باقة الإنترنت 4G</p>
            </div>
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

        {/* Storage status bar */}
        <div className="p-4 bg-zinc-950 border-b border-zinc-800 flex items-center justify-between text-xs">
          <div className="flex items-center gap-2 text-zinc-300">
            <HardDrive className="w-4 h-4 text-cyan-400" />
            <span>المساحة المستخدمة للفيديوهات:</span>
          </div>
          <span className="font-mono text-cyan-400 font-bold">{totalStorage} MB</span>
        </div>

        {/* Video list */}
        <div className="p-4 space-y-3 flex-1 overflow-y-auto">
          {offlineList.length === 0 ? (
            <div className="text-center py-12 text-zinc-500 text-xs space-y-2">
              <Download className="w-8 h-8 mx-auto text-zinc-600" />
              <p>لا توجد فيديوهات محملة حالياً. اضغط على زر الحفظ للمشاهدة بدون إنترنت!</p>
            </div>
          ) : (
            offlineList.map((video) => (
              <div
                key={video.id}
                className="p-3 rounded-xl bg-zinc-950 border border-zinc-800 flex items-center justify-between gap-3 hover:border-cyan-500/40 transition-colors"
              >
                <div
                  onClick={() => {
                    onPlayOfflineVideo(video);
                    onClose();
                  }}
                  className="flex items-center gap-3 cursor-pointer flex-1 min-w-0"
                >
                  <div className="relative shrink-0">
                    <img src={video.thumbnail} alt="" className="w-14 h-18 object-cover rounded-lg" />
                    <div className="absolute inset-0 bg-black/30 flex items-center justify-center rounded-lg">
                      <Play className="w-5 h-5 text-white" />
                    </div>
                  </div>
                  <div className="min-w-0">
                    <div className="text-xs font-bold text-white truncate">{video.caption}</div>
                    <div className="text-[11px] text-zinc-400 mt-1">{video.author.name}</div>
                    <div className="text-[10px] text-cyan-400 mt-0.5 font-mono">
                      💾 {video.fileSizeMb || '8.2'} MB · جاهز بدون نت
                    </div>
                  </div>
                </div>

                <button
                  onClick={() => handleDelete(video.id)}
                  className="p-2 rounded-lg bg-zinc-800 text-zinc-400 hover:text-red-400 hover:bg-zinc-700 transition-colors"
                  title="حذف من الجهاز"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};
