import React, { useState } from 'react';
import { Download, ExternalLink, QrCode, CheckCircle2, ShieldCheck, X, Sparkles, Smartphone } from 'lucide-react';

interface DownloadApkModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const DownloadApkModal: React.FC<DownloadApkModalProps> = ({ isOpen, onClose }) => {
  const [copied, setCopied] = useState(false);

  if (!isOpen) return null;

  const REPO_URL = 'https://github.com/Ahmedbecett/Chort';
  const SOURCE_ZIP_URL = `${REPO_URL}/archive/refs/heads/zevora-cleanup.zip`;
  const RELEASE_URL = `${REPO_URL}/releases`;
  const APK_DOWNLOAD_URL = RELEASE_URL;
  const APK_VERSIONED_URL = RELEASE_URL;

  const handleCopyLink = () => {
    navigator.clipboard.writeText(APK_DOWNLOAD_URL);
    setCopied(true);
    setTimeout(() => setCopied(false), 2500);
  };

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
                <h3 className="text-lg font-bold text-white">تحميل تطبيق ZEVORA APK</h3>
                <span className="bg-emerald-500/20 text-emerald-400 text-xs px-2 py-0.5 rounded-full font-mono border border-emerald-500/30">
                  نسخة البناء الحالية
                </span>
              </div>
              <p className="text-xs text-zinc-400 mt-0.5">الإصدار الرسمي سيظهر هنا بعد نشر Release موثوق</p>
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
          {/* Main Direct Download Action */}
          <a
            href={APK_DOWNLOAD_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="w-full flex items-center justify-center gap-3 py-4 px-6 rounded-xl font-bold text-white bg-gradient-to-r from-pink-600 via-rose-600 to-cyan-600 hover:from-pink-500 hover:to-cyan-500 shadow-xl shadow-pink-600/25 active:scale-[0.98] transition-all text-center text-base"
          >
            <Download className="w-6 h-6 animate-bounce" />
            <span>فتح صفحة الإصدارات الرسمية</span>
          </a>

          {/* Secondary APK Direct Download */}
          <a
            href={APK_VERSIONED_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="w-full flex items-center justify-center gap-2 py-2.5 px-4 rounded-xl font-semibold text-zinc-200 bg-zinc-800 hover:bg-zinc-700 border border-zinc-700 active:scale-[0.98] transition-all text-center text-xs"
          >
            <Download className="w-4 h-4 text-cyan-400" />
            <span>فتح صفحة الإصدارات</span>
          </a>

          {/* Full Files Download Action */}
          <a
            href={SOURCE_ZIP_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="w-full flex items-center justify-center gap-2 py-2.5 px-4 rounded-xl font-semibold text-amber-300 bg-amber-950/40 hover:bg-amber-900/50 border border-amber-800/60 active:scale-[0.98] transition-all text-center text-xs"
          >
            <Download className="w-4 h-4 text-amber-400" />
            <span>تحميل نسخة المصدر النظيفة</span>
          </a>

          {/* Quick Info Badges */}
          <div className="grid grid-cols-3 gap-2 text-center">
            <div className="bg-zinc-800/60 p-2.5 rounded-xl border border-zinc-800">
              <span className="block text-xs text-zinc-400">الحجم</span>
              <span className="text-sm font-semibold text-white">—</span>
            </div>
            <div className="bg-zinc-800/60 p-2.5 rounded-xl border border-zinc-800">
              <span className="block text-xs text-zinc-400">الإصدار</span>
              <span className="text-sm font-semibold text-cyan-400 font-mono">cleanup</span>
            </div>
            <div className="bg-zinc-800/60 p-2.5 rounded-xl border border-zinc-800">
              <span className="block text-xs text-zinc-400">الحالة</span>
              <span className="text-sm font-semibold text-emerald-400 flex items-center justify-center gap-1">
                <ShieldCheck className="w-3.5 h-3.5" /> جاهز للمراجعة
              </span>
            </div>
          </div>

          {/* Direct Links section */}
          <div className="space-y-2 pt-2 border-t border-zinc-800">
            <div className="flex items-center justify-between text-xs text-zinc-400">
              <span>رابط الإصدارات الرسمية:</span>
              <button 
                onClick={handleCopyLink}
                className="text-cyan-400 hover:text-cyan-300 underline font-medium"
              >
                {copied ? 'تم النسخ بنجاح ✓' : 'نسخ الرابط'}
              </button>
            </div>
            <div className="p-2.5 bg-zinc-950 rounded-lg border border-zinc-800 text-[11px] font-mono text-zinc-400 break-all select-all text-left" dir="ltr">
              {APK_DOWNLOAD_URL}
            </div>
          </div>

          {/* Repository Links */}
          <div className="flex items-center gap-3 pt-1">
            <a 
              href={RELEASE_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="flex-1 py-2 px-3 rounded-lg bg-zinc-800 hover:bg-zinc-700 text-xs font-medium text-zinc-200 flex items-center justify-center gap-2 transition-colors"
            >
              <ExternalLink className="w-3.5 h-3.5 text-zinc-400" />
              <span>صفحة الإصدارات</span>
            </a>
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

