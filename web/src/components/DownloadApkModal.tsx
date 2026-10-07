import React from 'react';
import { Download, ExternalLink, QrCode, CheckCircle2, ShieldCheck, X, Sparkles, Smartphone } from 'lucide-react';

interface DownloadApkModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const DownloadApkModal: React.FC<DownloadApkModalProps> = ({ isOpen, onClose }) => {
  if (!isOpen) return null;

  const REPO_URL = 'https://github.com/Ahmedbecett/Chort';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
      <div 
        className="bg-zinc-900 border border-zinc-700/80 rounded-2xl w-full max-w-md overflow-hidden shadow-2xl animate-in fade-in zoom-in-95 duration-200"
        dir="rtl"
      >
        {/* Header */}
        <div className="bg-gradient-to-r from-red-600/30 via-pink-600/20 to-cyan-600/30 p-5 border-b border-zinc-800 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-11 h-11 rounded-xl bg-gradient-to-tr from-cyan-500 to-pink-500 flex items-center justify-center shadow-lg shadow-pink-500/20">
              <Smartphone className="w-6 h-6 text-white" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-lg font-bold text-white">تحميل تطبيق ZEVORA</h3>
              </div>
              <p className="text-xs text-zinc-400 mt-0.5">لن يتم عرض رابط APK غير منشور أو إصدار قديم.</p>
            </div>
          </div>
          <button 
            onClick={onClose}
            className="w-8 h-8 rounded-full bg-zinc-800 hover:bg-zinc-700 text-zinc-400 hover:text-white flex items-center justify-center transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="p-6 space-y-4">
          <div className="rounded-xl border border-amber-700/40 bg-amber-950/30 p-4 text-sm text-amber-200 text-center">
          نسخة APK الإنتاجية تُنشر فقط بعد اجتياز الفحوصات. لا يوجد هنا رابط وهمي أو إصدار قديم.
        </div>

        {/* Quick Info Badges */}
          <div className="grid grid-cols-2 gap-2 text-center">
            <div className="bg-zinc-800/60 p-2.5 rounded-xl border border-zinc-800">
              <span className="block text-xs text-zinc-400">المحتوى</span>
              <span className="text-sm font-semibold text-white">بيانات حقيقية فقط</span>
            </div>
            <div className="bg-zinc-800/60 p-2.5 rounded-xl border border-zinc-800">
              <span className="block text-xs text-zinc-400">الحالة</span>
              <span className="text-sm font-semibold text-emerald-400 flex items-center justify-center gap-1">
                <ShieldCheck className="w-3.5 h-3.5" /> إنتاج فقط
              </span>
            </div>
          </div>

          {/* Repository Links */}
          <div className="flex items-center gap-3 pt-1">
            <a 
              href={REPO_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="flex-1 py-2 px-3 rounded-lg bg-zinc-800 hover:bg-zinc-700 text-xs font-medium text-zinc-200 flex items-center justify-center gap-2 transition-colors"
            >
              <ExternalLink className="w-3.5 h-3.5 text-zinc-400" />
              <span>مستودع المشروع</span>
            </a>
          </div>
        </div>
      </div>
    </div>
  );
};
