import React from 'react';
import { 
  X, Coins, History, WifiOff, QrCode, BarChart3, Rocket, 
  Settings, LogOut, Download, ChevronLeft, ShieldCheck 
} from 'lucide-react';
import { UserProfile } from '../types';

interface ProfileDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  onOpenWallet: () => void;
  onOpenActivity: () => void;
  onOpenOffline: () => void;
  onOpenQr: () => void;
  onOpenStudio: () => void;
  onOpenPromote: () => void;
  onOpenSettings: () => void;
  onOpenDownloadModal: () => void;
  onLogout: () => void;
  user: UserProfile;
}

export const ProfileDrawer: React.FC<ProfileDrawerProps> = ({
  isOpen,
  onClose,
  onOpenWallet,
  onOpenActivity,
  onOpenOffline,
  onOpenQr,
  onOpenStudio,
  onOpenPromote,
  onOpenSettings,
  onOpenDownloadModal,
  onLogout,
  user,
}) => {
  if (!isOpen) return null;

  const items = [
    {
      title: 'الرصيد والمحفظة',
      subtitle: `${user.coins.toLocaleString()} عملة · ${user.balanceDzd.toLocaleString()} دج`,
      icon: Coins,
      iconColor: 'text-amber-400',
      action: onOpenWallet,
    },
    {
      title: 'مركز النشاط (Activity center)',
      subtitle: 'سجل المشاهدة، الإعجابات والبحث',
      icon: History,
      iconColor: 'text-pink-400',
      action: onOpenActivity,
    },
    {
      title: 'فيديوهات بدون إنترنت (Offline)',
      subtitle: 'المشاهدة بدون استهلاك رصيد البيانات',
      icon: WifiOff,
      iconColor: 'text-cyan-400',
      action: onOpenOffline,
    },
    {
      title: 'رمز QR الخاص بحسابي',
      subtitle: `@${user.username}`,
      icon: QrCode,
      iconColor: 'text-purple-400',
      action: onOpenQr,
    },
    {
      title: 'Chort Studio (التحليلات)',
      subtitle: 'مخطط المشاهدات ونمو الجمهور',
      icon: BarChart3,
      iconColor: 'text-emerald-400',
      action: onOpenStudio,
    },
    {
      title: 'الترويج (Promote)',
      subtitle: 'ترويج مقاطعك عبر ولايات الجزائر',
      icon: Rocket,
      iconColor: 'text-rose-400',
      action: onOpenPromote,
    },
    {
      title: 'الإعدادات والخصوصية',
      subtitle: 'الحساب، الأمان وتفريغ الذاكرة',
      icon: Settings,
      iconColor: 'text-zinc-400',
      action: onOpenSettings,
    },
  ];

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/75 backdrop-blur-sm" dir="rtl">
      {/* Backdrop click */}
      <div className="flex-1" onClick={onClose} />

      {/* Drawer panel */}
      <div className="w-full max-w-xs sm:max-w-sm bg-zinc-950 border-r border-zinc-800 flex flex-col justify-between shadow-2xl animate-in slide-in-from-right duration-200">
        {/* Header */}
        <div>
          <div className="p-4 border-b border-zinc-800/80 flex items-center justify-between">
            <h3 className="font-bold text-white text-base">القائمة والمميزات</h3>
            <button onClick={onClose} className="p-1 rounded-full text-zinc-400 hover:text-white bg-zinc-800">
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Quick APK Download Card */}
          <div className="p-4 border-b border-zinc-800/60">
            <div 
              onClick={() => {
                onClose();
                onOpenDownloadModal();
              }}
              className="p-3.5 rounded-2xl bg-gradient-to-r from-pink-600/20 via-rose-600/20 to-cyan-600/20 border border-pink-500/30 cursor-pointer hover:border-pink-500 transition-all flex items-center justify-between"
            >
              <div className="flex items-center gap-2.5">
                <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-pink-500 to-cyan-500 flex items-center justify-center text-white">
                  <Download className="w-5 h-5" />
                </div>
                <div>
                  <div className="text-xs font-bold text-white">تحميل APK الأحدث</div>
                  <div className="text-[10px] text-emerald-400 font-mono">v2.4.4 من المستودع</div>
                </div>
              </div>
              <ChevronLeft className="w-4 h-4 text-zinc-400" />
            </div>
          </div>

          {/* Drawer Menu Items */}
          <div className="p-2 space-y-1 overflow-y-auto max-h-[60vh]">
            {items.map((item, idx) => {
              const Icon = item.icon;
              return (
                <button
                  key={idx}
                  onClick={() => {
                    onClose();
                    item.action();
                  }}
                  className="w-full p-3 rounded-xl hover:bg-zinc-900 flex items-center justify-between text-right transition-colors group"
                >
                  <div className="flex items-center gap-3">
                    <div className="w-9 h-9 rounded-xl bg-zinc-900 border border-zinc-800 flex items-center justify-center group-hover:border-zinc-700">
                      <Icon className={`w-4 h-4 ${item.iconColor}`} />
                    </div>
                    <div>
                      <div className="text-xs font-bold text-zinc-200 group-hover:text-white">{item.title}</div>
                      <div className="text-[10px] text-zinc-500 truncate max-w-[170px]">{item.subtitle}</div>
                    </div>
                  </div>
                  <ChevronLeft className="w-4 h-4 text-zinc-600 group-hover:text-zinc-400" />
                </button>
              );
            })}
          </div>
        </div>

        {/* Footer Logout */}
        <div className="p-4 border-t border-zinc-800/80">
          <button
            onClick={() => {
              onClose();
              onLogout();
            }}
            className="w-full py-2.5 px-4 rounded-xl font-bold bg-zinc-900 hover:bg-red-950/40 border border-zinc-800 hover:border-red-900/50 text-red-400 transition-colors text-xs flex items-center justify-center gap-2"
          >
            <LogOut className="w-4 h-4" />
            <span>تسجيل الخروج</span>
          </button>
        </div>
      </div>
    </div>
  );
};
