import React, { useState } from 'react';
import { 
  ArrowRight, Shield, User, Lock, Eye, Bell, HardDrive, HelpCircle, 
  LogOut, Users, Trash2, Smartphone, Download, CheckCircle2, ChevronLeft 
} from 'lucide-react';
import { api } from '../services/api';
import { UserProfile } from '../types';

interface SettingsViewProps {
  onBack: () => void;
  onLogout: () => void;
  onOpenDownloadModal: () => void;
  user: UserProfile;
}

export const SettingsView: React.FC<SettingsViewProps> = ({ 
  onBack, 
  onLogout, 
  onOpenDownloadModal,
  user 
}) => {
  const [isPrivateAccount, setIsPrivateAccount] = useState(false);
  const [allowDownloads, setAllowDownloads] = useState(true);
  const [dataSaver, setDataSaver] = useState(false);
  const [cacheClearedNotice, setCacheClearedNotice] = useState(false);

  const handleClearCache = () => {
    setCacheClearedNotice(true);
    setTimeout(() => setCacheClearedNotice(false), 2500);
  };

  return (
    <div className="fixed inset-0 z-50 bg-black text-white flex flex-col overflow-y-auto" dir="rtl">
      {/* Top Bar */}
      <div className="sticky top-0 z-10 bg-zinc-950/95 backdrop-blur-md p-4 border-b border-zinc-800 flex items-center justify-between">
        <button 
          onClick={onBack}
          className="flex items-center gap-2 text-zinc-300 hover:text-white transition-colors"
        >
          <ArrowRight className="w-5 h-5" />
          <span className="font-bold text-base">الإعدادات والخصوصية</span>
        </button>

        <button
          onClick={onOpenDownloadModal}
          className="flex items-center gap-1.5 py-1 px-2.5 rounded-full text-xs font-semibold bg-gradient-to-r from-pink-600 to-cyan-600 text-white"
        >
          <Download className="w-3.5 h-3.5" />
          <span>APK v2.4.6</span>
        </button>
      </div>

      {/* Notice */}
      {cacheClearedNotice && (
        <div className="m-4 p-3 bg-emerald-950/80 border border-emerald-800 rounded-xl flex items-center gap-2 text-xs text-emerald-300">
          <CheckCircle2 className="w-4 h-4 shrink-0 text-emerald-400" />
          <span>تم تفريغ ذاكرة التخزين المؤقت (142 MB) وتحرير المساحة بنجاح!</span>
        </div>
      )}

      {/* Settings list */}
      <div className="p-4 space-y-6 max-w-lg mx-auto w-full flex-1">
        {/* Section 1: Account */}
        <div className="space-y-2">
          <span className="text-xs font-bold text-zinc-500 uppercase tracking-wider px-1">الحساب</span>
          <div className="bg-zinc-900 border border-zinc-800 rounded-2xl overflow-hidden divide-y divide-zinc-800/80">
            <div className="p-3.5 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <User className="w-4 h-4 text-zinc-400" />
                <span className="text-xs font-semibold text-white">معلومات الحساب</span>
              </div>
              <span className="text-xs text-zinc-400 font-mono">@{user.username}</span>
            </div>

            <div className="p-3.5 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <Lock className="w-4 h-4 text-zinc-400" />
                <span className="text-xs font-semibold text-white">كلمة المرور والأمان</span>
              </div>
              <ChevronLeft className="w-4 h-4 text-zinc-500" />
            </div>
          </div>
        </div>

        {/* Section 2: Privacy */}
        <div className="space-y-2">
          <span className="text-xs font-bold text-zinc-500 uppercase tracking-wider px-1">الخصوصية والظهور</span>
          <div className="bg-zinc-900 border border-zinc-800 rounded-2xl overflow-hidden divide-y divide-zinc-800/80">
            <div className="p-3.5 flex items-center justify-between">
              <div>
                <div className="text-xs font-semibold text-white">حساب خاص (Private)</div>
                <div className="text-[11px] text-zinc-500">المتابعون الموافق عليهم فقط يمكنهم رؤية فيديوهاتك</div>
              </div>
              <input
                type="checkbox"
                checked={isPrivateAccount}
                onChange={(e) => setIsPrivateAccount(e.target.checked)}
                className="w-4 h-4 accent-pink-500 rounded"
              />
            </div>

            <div className="p-3.5 flex items-center justify-between">
              <div>
                <div className="text-xs font-semibold text-white">السماح بتنزيل فيديوهاتي</div>
                <div className="text-[11px] text-zinc-500">السماح للآخرين بتحميل مقاطعك على أجهزتهم</div>
              </div>
              <input
                type="checkbox"
                checked={allowDownloads}
                onChange={(e) => setAllowDownloads(e.target.checked)}
                className="w-4 h-4 accent-pink-500 rounded"
              />
            </div>
          </div>
        </div>

        {/* Section 3: Cache & Cellular */}
        <div className="space-y-2">
          <span className="text-xs font-bold text-zinc-500 uppercase tracking-wider px-1">الذاكرة والبيانات</span>
          <div className="bg-zinc-900 border border-zinc-800 rounded-2xl overflow-hidden divide-y divide-zinc-800/80">
            <button
              onClick={handleClearCache}
              className="w-full p-3.5 flex items-center justify-between text-right hover:bg-zinc-800/50 transition-colors"
            >
              <div className="flex items-center gap-3">
                <Trash2 className="w-4 h-4 text-pink-400" />
                <div>
                  <div className="text-xs font-semibold text-white">تفريغ المساحة (Free up space)</div>
                  <div className="text-[11px] text-zinc-500">مسح الذاكرة المؤقتة المؤقتة</div>
                </div>
              </div>
              <span className="text-xs text-pink-400 font-bold">142 MB</span>
            </button>

            <div className="p-3.5 flex items-center justify-between">
              <div>
                <div className="text-xs font-semibold text-white">توفير البيانات (Data Saver)</div>
                <div className="text-[11px] text-zinc-500">تقليل جودة الفيديو أثناء استخدام شبكة 4G</div>
              </div>
              <input
                type="checkbox"
                checked={dataSaver}
                onChange={(e) => setDataSaver(e.target.checked)}
                className="w-4 h-4 accent-pink-500 rounded"
              />
            </div>
          </div>
        </div>

        {/* Section 4: Support & APK */}
        <div className="space-y-2">
          <span className="text-xs font-bold text-zinc-500 uppercase tracking-wider px-1">الدعم والمستودع</span>
          <div className="bg-zinc-900 border border-zinc-800 rounded-2xl overflow-hidden divide-y divide-zinc-800/80">
            <button
              onClick={onOpenDownloadModal}
              className="w-full p-3.5 flex items-center justify-between text-right hover:bg-zinc-800/50 transition-colors"
            >
              <div className="flex items-center gap-3">
                <Smartphone className="w-4 h-4 text-cyan-400" />
                <div>
                  <div className="text-xs font-semibold text-white">تنزيل التطبيق الأحدث (APK)</div>
                  <div className="text-[11px] text-zinc-500">إصدار المستودع الرسمي v2.4.6</div>
                </div>
              </div>
              <Download className="w-4 h-4 text-cyan-400" />
            </button>

            <div className="p-3.5 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <HelpCircle className="w-4 h-4 text-zinc-400" />
                <span className="text-xs font-semibold text-white">مركز المساعدة والدعم الفني</span>
              </div>
              <ChevronLeft className="w-4 h-4 text-zinc-500" />
            </div>
          </div>
        </div>

        {/* Section 5: Logout & Switch */}
        <div className="pt-4 space-y-2.5">
          <button
            onClick={() => {
              api.setAuthStatus(false);
              onLogout();
            }}
            className="w-full py-3.5 px-4 rounded-xl font-bold bg-zinc-900 hover:bg-zinc-800 border border-zinc-800 text-zinc-200 hover:text-white transition-colors text-xs flex items-center justify-center gap-2"
          >
            <Users className="w-4 h-4 text-cyan-400" />
            <span>تبديل الحساب (Switch Account)</span>
          </button>

          <button
            onClick={() => {
              api.setAuthStatus(false);
              onLogout();
            }}
            className="w-full py-3.5 px-4 rounded-xl font-bold bg-red-950/40 hover:bg-red-900/50 border border-red-800/60 text-red-400 hover:text-red-300 transition-colors text-xs flex items-center justify-center gap-2"
          >
            <LogOut className="w-4 h-4" />
            <span>تسجيل الخروج (Log Out)</span>
          </button>
        </div>
      </div>
    </div>
  );
};
