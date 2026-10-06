import React from 'react';
import { Home, Users, Plus, MessageSquare, User } from 'lucide-react';

interface BottomNavProps {
  currentTab: 'home' | 'friends' | 'inbox' | 'profile';
  onChangeTab: (tab: 'home' | 'friends' | 'inbox' | 'profile') => void;
  onOpenCreate: () => void;
}

export const BottomNav: React.FC<BottomNavProps> = ({ currentTab, onChangeTab, onOpenCreate }) => {
  return (
    <div className="fixed bottom-0 inset-x-0 z-40 bg-black/95 backdrop-blur-md border-t border-zinc-800/80 px-4 py-2 flex items-center justify-between" dir="rtl">
      {/* Home */}
      <button
        onClick={() => onChangeTab('home')}
        className={`flex-1 flex flex-col items-center gap-1 transition-colors ${
          currentTab === 'home' ? 'text-white' : 'text-zinc-500 hover:text-zinc-300'
        }`}
      >
        <Home className="w-5 h-5" />
        <span className="text-[10px] font-semibold">الرئيسية</span>
      </button>

      {/* Friends */}
      <button
        onClick={() => onChangeTab('friends')}
        className={`flex-1 flex flex-col items-center gap-1 transition-colors ${
          currentTab === 'friends' ? 'text-white' : 'text-zinc-500 hover:text-zinc-300'
        }`}
      >
        <Users className="w-5 h-5" />
        <span className="text-[10px] font-semibold">الأصدقاء</span>
      </button>

      {/* Center TikTok [+] Create Button */}
      <div className="flex-1 flex justify-center">
        <button
          onClick={onOpenCreate}
          className="relative w-11 h-7 flex items-center justify-center group active:scale-90 transition-transform"
        >
          {/* Left cyan background */}
          <div className="absolute inset-0 bg-[#00f2fe] rounded-lg -translate-x-1 group-hover:-translate-x-1.5 transition-transform" />
          {/* Right pink background */}
          <div className="absolute inset-0 bg-[#fe0979] rounded-lg translate-x-1 group-hover:translate-x-1.5 transition-transform" />
          {/* Center black card */}
          <div className="relative w-9 h-7 bg-white rounded-md flex items-center justify-center text-black z-10 shadow-sm">
            <Plus className="w-5 h-5 font-black stroke-[3]" />
          </div>
        </button>
      </div>

      {/* Inbox with 99+ Badge */}
      <button
        onClick={() => onChangeTab('inbox')}
        className={`flex-1 flex flex-col items-center gap-1 relative transition-colors ${
          currentTab === 'inbox' ? 'text-white' : 'text-zinc-500 hover:text-zinc-300'
        }`}
      >
        <div className="relative">
          <MessageSquare className="w-5 h-5" />
          <span className="absolute -top-1.5 -right-2.5 bg-red-600 text-white text-[9px] font-bold px-1 rounded-full border border-black min-w-[16px] text-center">
            99+
          </span>
        </div>
        <span className="text-[10px] font-semibold">صندوق الوارد</span>
      </button>

      {/* Profile */}
      <button
        onClick={() => onChangeTab('profile')}
        className={`flex-1 flex flex-col items-center gap-1 transition-colors ${
          currentTab === 'profile' ? 'text-white font-bold' : 'text-zinc-500 hover:text-zinc-300'
        }`}
      >
        <User className="w-5 h-5" />
        <span className="text-[10px] font-semibold">الملف الشخصي</span>
      </button>
    </div>
  );
};
