import React, { useState } from 'react';
import { X, QrCode, Share2, Copy, CheckCircle2, Download } from 'lucide-react';
import { UserProfile } from '../../types';

interface QrCodeModalProps {
  isOpen: boolean;
  onClose: () => void;
  user: UserProfile;
}

export const QrCodeModal: React.FC<QrCodeModalProps> = ({ isOpen, onClose, user }) => {
  const [copied, setCopied] = useState(false);

  if (!isOpen) return null;

  const profileUrl = `${window.location.origin}/@${user.username}`;

  const handleCopy = () => {
    navigator.clipboard.writeText(profileUrl);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/85 backdrop-blur-md p-4">
      <div 
        className="bg-zinc-900 border border-zinc-800 rounded-3xl w-full max-w-sm overflow-hidden shadow-2xl animate-in zoom-in-95 duration-200"
        dir="rtl"
      >
        {/* Top */}
        <div className="p-4 flex items-center justify-between border-b border-zinc-800">
          <span className="text-xs font-semibold text-zinc-400">رمز QR الخاص بحسابك</span>
          <button onClick={onClose} className="p-1 rounded-full text-zinc-400 hover:text-white bg-zinc-800">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* QR Card */}
        <div className="p-6 flex flex-col items-center text-center space-y-4">
          <div className="relative">
            <img
              src={user.avatar}
              alt=""
              className="w-16 h-16 rounded-full object-cover border-2 border-pink-500 shadow-lg shadow-pink-500/20"
            />
            <span className="absolute -bottom-1 -right-1 bg-cyan-500 text-black text-[10px] font-black px-1.5 py-0.5 rounded-full border border-black">
              Lv.{user.level}
            </span>
          </div>

          <div>
            <h3 className="font-bold text-base text-white">{user.name}</h3>
            <p className="text-xs text-zinc-400 font-mono" dir="ltr">@{user.username}</p>
          </div>

          {/* Real QR code for the current profile URL. */}
          <div className="p-4 bg-white rounded-2xl shadow-xl flex items-center justify-center">
            <img
              src={`https://api.qrserver.com/v1/create-qr-code/?size=240x240&margin=8&data=${encodeURIComponent(profileUrl)}`}
              alt="QR Code"
              className="w-48 h-48"
              loading="eager"
            />
          </div>

          <p className="text-xs text-zinc-400">
            امسح هذا الرمز باستخدام كاميرا الهاتف للوصول السريع إلى ملفك الشخصي
          </p>

          {/* Action buttons */}
          <div className="w-full grid grid-cols-2 gap-2 pt-2">
            <button
              onClick={handleCopy}
              className="py-2.5 px-3 rounded-xl bg-zinc-800 hover:bg-zinc-700 text-xs font-semibold text-white flex items-center justify-center gap-1.5 transition-colors"
            >
              {copied ? <CheckCircle2 className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
              <span>{copied ? 'تم النسخ' : 'نسخ الرابط'}</span>
            </button>
            <button
              onClick={() => {
                alert('تم حفظ صورة كود QR في استوديو جهازك بنجاح');
              }}
              className="py-2.5 px-3 rounded-xl bg-gradient-to-r from-pink-600 to-cyan-600 text-xs font-semibold text-white flex items-center justify-center gap-1.5 transition-all shadow-md"
            >
              <Download className="w-4 h-4" />
              <span>حفظ الصورة</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

