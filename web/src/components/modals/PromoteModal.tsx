import React, { useState } from 'react';
import { X, Rocket, Target, DollarSign, CheckCircle2, Sparkles, MapPin } from 'lucide-react';
import { VideoItem, UserProfile } from '../../types';
import { ALGERIA_WILAYAS } from '../../data/mockData';

interface PromoteModalProps {
  isOpen: boolean;
  onClose: () => void;
  videos: VideoItem[];
  user: UserProfile;
}

export const PromoteModal: React.FC<PromoteModalProps> = ({ isOpen, onClose, videos, user }) => {
  const [selectedVideoId, setSelectedVideoId] = useState(videos[0]?.id || '');
  const [goal, setGoal] = useState<'views' | 'followers' | 'profile'>('views');
  const [budgetDzd, setBudgetDzd] = useState(2500);
  const [selectedWilayas, setSelectedWilayas] = useState<string[]>(['16 - الجزائر العاصمة (Alger)', '31 - وهران (Oran)']);
  const [isSuccess, setIsSuccess] = useState(false);

  if (!isOpen) return null;

  const estimatedReach = Math.round(budgetDzd * 14.5);

  const toggleWilaya = (w: string) => {
    if (selectedWilayas.includes(w)) {
      setSelectedWilayas(selectedWilayas.filter((item) => item !== w));
    } else {
      setSelectedWilayas([...selectedWilayas, w]);
    }
  };

  const handleLaunchCampaign = () => {
    setIsSuccess(true);
    setTimeout(() => {
      setIsSuccess(false);
      onClose();
    }, 2200);
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
              <Rocket className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-bold text-white text-base">ترويج الفيديوهات (Chort Promote)</h3>
              <p className="text-[11px] text-zinc-400">ضخ زيارات ومشاهدات حقيقية لمقاطعك عبر الولايات</p>
            </div>
          </div>
          <button onClick={onClose} className="p-1 rounded-full text-zinc-400 hover:text-white bg-zinc-800">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Success Alert */}
        {isSuccess && (
          <div className="m-4 p-4 bg-emerald-950 border border-emerald-700 rounded-2xl flex items-center gap-3 text-xs text-emerald-300">
            <CheckCircle2 className="w-6 h-6 text-emerald-400 shrink-0" />
            <div>
              <div className="font-bold text-sm text-white">تم إطلاق حملة الترويج بنجاح! 🚀</div>
              <p className="text-zinc-300 mt-0.5">ستبدأ المشاهدات والتفاعلات بالظهور خلال الدقائق القادمة.</p>
            </div>
          </div>
        )}

        {/* Content */}
        <div className="p-4 space-y-4 flex-1 overflow-y-auto">
          {/* Step 1: Select Video */}
          <div className="space-y-2">
            <label className="text-xs font-bold text-zinc-300">1. اختر الفيديو المراد ترويجه:</label>
            <div className="grid grid-cols-3 gap-2">
              {videos.slice(0, 3).map((v) => (
                <div
                  key={v.id}
                  onClick={() => setSelectedVideoId(v.id)}
                  className={`relative rounded-xl overflow-hidden cursor-pointer border-2 transition-all ${
                    selectedVideoId === v.id ? 'border-pink-500 scale-102' : 'border-zinc-800 opacity-70 hover:opacity-100'
                  }`}
                >
                  <img src={v.thumbnail} alt="" className="w-full h-24 object-cover" />
                  <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent flex items-end p-1.5">
                    <span className="text-[10px] text-white truncate font-medium">{v.caption}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Step 2: Goal */}
          <div className="space-y-2">
            <label className="text-xs font-bold text-zinc-300">2. هدف الحملة:</label>
            <div className="grid grid-cols-3 gap-2">
              {[
                { id: 'views', label: 'المزيد من المشاهدات', icon: Target },
                { id: 'followers', label: 'كسب متابعين جدد', icon: Sparkles },
                { id: 'profile', label: 'زيارات الملف الشخصي', icon: DollarSign },
              ].map((g) => {
                const Icon = g.icon;
                return (
                  <button
                    key={g.id}
                    onClick={() => setGoal(g.id as any)}
                    className={`p-2.5 rounded-xl border text-center flex flex-col items-center gap-1 transition-all ${
                      goal === g.id
                        ? 'border-pink-500 bg-pink-500/10 text-white'
                        : 'border-zinc-800 bg-zinc-950 text-zinc-400 hover:bg-zinc-800'
                    }`}
                  >
                    <Icon className="w-4 h-4 text-pink-400" />
                    <span className="text-[11px] font-medium">{g.label}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Step 3: Target Wilayas */}
          <div className="space-y-2">
            <div className="flex items-center justify-between text-xs">
              <span className="font-bold text-zinc-300">3. استهداف الولايات الجزائرية:</span>
              <span className="text-zinc-500 text-[11px]">{selectedWilayas.length} محددة</span>
            </div>
            <div className="flex flex-wrap gap-1.5 max-h-32 overflow-y-auto p-2 bg-zinc-950 border border-zinc-800 rounded-xl">
              {ALGERIA_WILAYAS.map((w) => {
                const isSelected = selectedWilayas.includes(w);
                return (
                  <button
                    key={w}
                    onClick={() => toggleWilaya(w)}
                    className={`px-2.5 py-1 rounded-lg text-[11px] font-medium transition-colors ${
                      isSelected
                        ? 'bg-cyan-500 text-black font-semibold'
                        : 'bg-zinc-800 text-zinc-400 hover:text-white'
                    }`}
                  >
                    {w}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Step 4: Budget & Reach */}
          <div className="p-4 rounded-2xl bg-zinc-950 border border-zinc-800 space-y-3">
            <div className="flex items-center justify-between text-xs">
              <span className="text-zinc-400">الميزانية المخصصة:</span>
              <span className="text-amber-400 font-bold font-mono text-sm">{budgetDzd.toLocaleString()} دج</span>
            </div>

            <input
              type="range"
              min="1000"
              max="20000"
              step="500"
              value={budgetDzd}
              onChange={(e) => setBudgetDzd(Number(e.target.value))}
              className="w-full accent-pink-500"
            />

            <div className="pt-2 border-t border-zinc-800 flex items-center justify-between text-xs">
              <span className="text-zinc-400">الوصول المتوقع التقريبي:</span>
              <span className="text-emerald-400 font-bold font-mono text-sm">
                ~ {estimatedReach.toLocaleString()} مستخدم 🇩🇿
              </span>
            </div>
          </div>

          {/* CTA */}
          <button
            onClick={handleLaunchCampaign}
            className="w-full py-3.5 px-4 rounded-xl font-bold bg-gradient-to-r from-pink-600 via-rose-600 to-cyan-600 hover:from-pink-500 hover:to-cyan-500 text-white shadow-xl shadow-pink-600/25 active:scale-[0.98] transition-all text-sm flex items-center justify-center gap-2"
          >
            <Rocket className="w-4 h-4" />
            <span>إطلاق حملة الترويج الآن ({budgetDzd.toLocaleString()} دج)</span>
          </button>
        </div>
      </div>
    </div>
  );
};
