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
  const [supportMode, setSupportMode] = useState<'help' | 'report' | null>(null);

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
          <span>تم تفريغ الذاكرة المؤقتة المحلية بنجاح.</span>
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

        {/* Language */}
        <div className="space-y-2">
          <span className="text-xs font-bold text-zinc-500 uppercase tracking-wider px-1">المحتوى واللغة</span>
          <div className="bg-zinc-900 border border-zinc-800 rounded-2xl overflow-hidden">
            <button onClick={() => { window.dispatchEvent(new CustomEvent('zevora:open-languages')); }} className="w-full p-3.5 flex items-center justify-between text-right hover:bg-zinc-800/50">
              <div className="flex items-center gap-3"><Globe2 className="w-4 h-4 text-cyan-400"/><div><div className="text-xs font-semibold text-white">اللغة</div><div className="text-[11px] text-zinc-500">العربية · English · Français وغيرها</div></div></div><ChevronLeft className="w-4 h-4 text-zinc-500"/>
            </button>
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
              <span className="text-xs text-zinc-500">مسح الآن</span>
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

            <button onClick={() => setSupportMode('help')} className="w-full p-3.5 flex items-center justify-between text-right hover:bg-zinc-800/50">
              <div className="flex items-center gap-3">
                <HelpCircle className="w-4 h-4 text-cyan-400" />
                <div><div className="text-xs font-semibold text-white">مركز المساعدة</div><div className="text-[11px] text-zinc-500">الحساب، الفيديو، الرسائل والسلامة</div></div>
              </div><ChevronLeft className="w-4 h-4 text-zinc-500" />
            </button>
            <button onClick={() => setSupportMode('report')} className="w-full p-3.5 flex items-center justify-between text-right hover:bg-zinc-800/50">
              <div className="flex items-center gap-3">
                <Shield className="w-4 h-4 text-pink-400" />
                <div><div className="text-xs font-semibold text-white">الإبلاغ عن مشكلة</div><div className="text-[11px] text-zinc-500">خطأ، إساءة أو مشكلة تتعلق بالسلامة</div></div>
              </div><ChevronLeft className="w-4 h-4 text-zinc-500" />
            </button>
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

      {supportMode && (
        <div className="fixed inset-0 z-[70] bg-black/70 backdrop-blur-md flex items-end sm:items-center justify-center p-4">
          <div className="w-full max-w-md rounded-3xl bg-zinc-950 border border-white/10 p-5 shadow-2xl">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-black text-white">{supportMode === 'help' ? 'مركز المساعدة' : 'الإبلاغ عن مشكلة'}</h3>
              <button onClick={() => setSupportMode(null)} className="text-zinc-400">×</button>
            </div>
            {supportMode === 'help' ? (
              <div className="space-y-2 text-sm text-zinc-300">
                {['تسجيل الدخول والحساب','الأمان والخصوصية','الفيديوهات والرفع','التعليقات والرسائل','الإبلاغ وحماية المجتمع'].map(x => <div key={x} className="rounded-2xl bg-white/5 border border-white/8 p-3">{x}</div>)}
              </div>
            ) : (
              <div className="space-y-3">
                <textarea className="w-full min-h-32 rounded-2xl bg-white/5 border border-white/10 p-3 text-sm text-white outline-none" placeholder="صف المشكلة بالتفصيل…" />
                <button onClick={() => setSupportMode(null)} className="w-full rounded-2xl py-3 bg-gradient-to-r from-pink-600 to-cyan-500 font-bold">إرسال البلاغ</button>
                <p className="text-[11px] text-zinc-500">سيتم إرسال البلاغ عند ربطه بخدمة الدعم الخلفية. لا نعرض حالة أو رقمًا وهميًا.</p>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

