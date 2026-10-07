import React, { useState } from 'react';
import { X, BarChart3, TrendingUp, Users, Eye, Sparkles, MapPin } from 'lucide-react';

interface StudioAnalyticsModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const StudioAnalyticsModal: React.FC<StudioAnalyticsModalProps> = ({ isOpen, onClose }) => {
  const [period, setPeriod] = useState<'7d' | '28d' | '60d'>('7d');

  if (!isOpen) return null;

  const viewsData = [
    { day: 'السبت', views: 4200, height: '40%' },
    { day: 'الأحد', views: 5800, height: '55%' },
    { day: 'الاثنين', views: 7100, height: '68%' },
    { day: 'الثلاثاء', views: 8900, height: '85%' },
    { day: 'الأربعاء', views: 6400, height: '62%' },
    { day: 'الخميس', views: 10200, height: '98%' },
    { day: 'الجمعة', views: 11500, height: '100%' },
  ];

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
              <BarChart3 className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-bold text-white text-base">استوديو ZEVORA للتحليلات</h3>
              <p className="text-[11px] text-zinc-400">بيانات الأداء ونمو الحساب في الوقت الفعلي</p>
            </div>
          </div>
          <button onClick={onClose} className="p-1 rounded-full text-zinc-400 hover:text-white bg-zinc-800">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Period tabs */}
        <div className="p-4 grid grid-cols-3 gap-2 border-b border-zinc-800">
          {(['7d', '28d', '60d'] as const).map((p) => (
            <button
              key={p}
              onClick={() => setPeriod(p)}
              className={`py-1.5 px-3 rounded-lg text-xs font-semibold transition-all ${
                period === p ? 'bg-pink-600 text-white shadow-md' : 'bg-zinc-800 text-zinc-400 hover:bg-zinc-700'
              }`}
            >
              {p === '7d' ? 'آخر 7 أيام' : p === '28d' ? 'آخر 28 يوم' : 'آخر 60 يوم'}
            </button>
          ))}
        </div>

        {/* Content */}
        <div className="p-4 space-y-4 flex-1 overflow-y-auto">
          {/* Key metrics cards */}
          <div className="grid grid-cols-2 gap-3">
            <div className="p-3.5 rounded-xl bg-zinc-950 border border-zinc-800 space-y-1">
              <div className="flex items-center justify-between text-zinc-400 text-xs">
                <span>مشاهدات الفيديو</span>
                <Eye className="w-4 h-4 text-cyan-400" />
              </div>
              <div className="text-xl font-black text-white">54.1K</div>
              <div className="text-[10px] text-emerald-400 flex items-center gap-1 font-semibold">
                <TrendingUp className="w-3 h-3" /> +24.8% عن الأسبوع الماضي
              </div>
            </div>

            <div className="p-3.5 rounded-xl bg-zinc-950 border border-zinc-800 space-y-1">
              <div className="flex items-center justify-between text-zinc-400 text-xs">
                <span>متابعون جدد</span>
                <Users className="w-4 h-4 text-pink-400" />
              </div>
              <div className="text-xl font-black text-white">+384</div>
              <div className="text-[10px] text-emerald-400 flex items-center gap-1 font-semibold">
                <TrendingUp className="w-3 h-3" /> +18.2% نمو سريع
              </div>
            </div>
          </div>

          {/* Interactive Bar Chart for 7 Days */}
          <div className="p-4 rounded-2xl bg-zinc-950 border border-zinc-800 space-y-3">
            <div className="flex items-center justify-between text-xs">
              <span className="font-bold text-zinc-300">نشاط المشاهدات اليومي:</span>
              <span className="text-pink-400 font-mono text-[11px]">مجموع: 54,100 مشاهدة</span>
            </div>

            <div className="h-40 flex items-end justify-between gap-2 pt-6 pb-2 px-1 border-b border-zinc-800/80">
              {viewsData.map((d, i) => (
                <div key={i} className="flex-1 flex flex-col items-center gap-1.5 h-full justify-end group">
                  <span className="text-[9px] font-mono text-zinc-500 opacity-0 group-hover:opacity-100 transition-opacity">
                    {d.views >= 1000 ? `${(d.views / 1000).toFixed(1)}k` : d.views}
                  </span>
                  <div
                    style={{ height: d.height }}
                    className="w-full max-w-[28px] rounded-t-md bg-gradient-to-t from-pink-600 via-rose-500 to-cyan-400 group-hover:brightness-125 transition-all"
                  />
                  <span className="text-[10px] text-zinc-400 mt-1">{d.day}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Audience Geography (Algeria Wilayas & diaspora) */}
          <div className="p-4 rounded-2xl bg-zinc-950 border border-zinc-800 space-y-3">
            <div className="flex items-center gap-2 text-xs font-bold text-zinc-300">
              <MapPin className="w-4 h-4 text-cyan-400" />
              <span>التوزيع الجغرافي للجمهور:</span>
            </div>

            <div className="space-y-2 text-xs">
              {[
                { name: 'الجزائر العاصمة 🇩🇿', pct: 44, color: 'bg-pink-500' },
                { name: 'وهران 🇩🇿', pct: 28, color: 'bg-cyan-500' },
                { name: 'قسنطينة 🇩🇿', pct: 15, color: 'bg-amber-500' },
                { name: 'فرنسا / المهجر 🇫🇷', pct: 9, color: 'bg-indigo-500' },
                { name: 'ولايات أخرى 🇩🇿', pct: 4, color: 'bg-zinc-500' },
              ].map((loc, i) => (
                <div key={i} className="space-y-1">
                  <div className="flex items-center justify-between text-[11px]">
                    <span className="text-zinc-300">{loc.name}</span>
                    <span className="text-zinc-400 font-mono">{loc.pct}%</span>
                  </div>
                  <div className="w-full h-1.5 bg-zinc-800 rounded-full overflow-hidden">
                    <div style={{ width: `${loc.pct}%` }} className={`h-full ${loc.color} rounded-full`} />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

