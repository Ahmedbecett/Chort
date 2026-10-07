import React, { useState, useEffect } from 'react';
import { X, BarChart3, Users, Eye, MapPin } from 'lucide-react';

interface StudioAnalyticsModalProps {
  isOpen: boolean;
  onClose: () => void;
}

interface AnalyticsData {
  hasData: boolean;
  viewsLast7Days: number[];
  profileViews: number;
  newFollowers: number;
  engagementRate: string;
  topAudience: Array<{ region: string; percentage: number }>;
}

const EMPTY_ANALYTICS: AnalyticsData = {
  hasData: false,
  viewsLast7Days: [0, 0, 0, 0, 0, 0, 0],
  profileViews: 0,
  newFollowers: 0,
  engagementRate: '0%',
  topAudience: [],
};

const DAY_LABELS = ['السبت', 'الأحد', 'الاثنين', 'الثلاثاء', 'الأربعاء', 'الخميس', 'الجمعة'];

// Production policy: analytics are ONLY what the server reports. When the
// server has no data yet, the UI shows honest zeros — never invented stats.
export const StudioAnalyticsModal: React.FC<StudioAnalyticsModalProps> = ({ isOpen, onClose }) => {
  const [period, setPeriod] = useState<'7d' | '28d' | '60d'>('7d');
  const [data, setData] = useState<AnalyticsData>(EMPTY_ANALYTICS);

  useEffect(() => {
    if (!isOpen) return;
    let cancelled = false;
    fetch('/api/analytics')
      .then((res) => (res.ok ? res.json() : null))
      .then((json) => {
        if (cancelled || !json) return;
        setData({
          hasData: Boolean(json.hasData),
          viewsLast7Days: Array.isArray(json.viewsLast7Days) ? json.viewsLast7Days.slice(0, 7) : [0, 0, 0, 0, 0, 0, 0],
          profileViews: Number(json.profileViews) || 0,
          newFollowers: Number(json.newFollowers) || 0,
          engagementRate: String(json.engagementRate || '0%'),
          topAudience: Array.isArray(json.topAudience) ? json.topAudience : [],
        });
      })
      .catch(() => {
        if (!cancelled) setData(EMPTY_ANALYTICS);
      });
    return () => {
      cancelled = true;
    };
  }, [isOpen]);

  if (!isOpen) return null;

  const maxViews = Math.max(1, ...data.viewsLast7Days);
  const totalViews = data.viewsLast7Days.reduce((a, b) => a + b, 0);

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
              <p className="text-[11px] text-zinc-400">بيانات الأداء الحقيقية من الخادم فقط</p>
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
          {!data.hasData && (
            <div className="p-4 rounded-2xl bg-zinc-950 border border-zinc-800 text-center">
              <BarChart3 className="w-8 h-8 mx-auto text-zinc-700" />
              <p className="mt-3 text-sm font-bold text-zinc-300">لا توجد بيانات تحليلات بعد</p>
              <p className="text-xs text-zinc-500 mt-1">ستظهر هنا الأرقام الحقيقية بعد بدء النشاط على حسابك.</p>
            </div>
          )}

          {/* Key metrics cards */}
          <div className="grid grid-cols-2 gap-3">
            <div className="p-3.5 rounded-xl bg-zinc-950 border border-zinc-800 space-y-1">
              <div className="flex items-center justify-between text-zinc-400 text-xs">
                <span>مشاهدات الفيديو</span>
                <Eye className="w-4 h-4 text-cyan-400" />
              </div>
              <div className="text-xl font-black text-white">{totalViews.toLocaleString()}</div>
              <div className="text-[10px] text-zinc-500 font-semibold">
                التفاعل: {data.engagementRate}
              </div>
            </div>

            <div className="p-3.5 rounded-xl bg-zinc-950 border border-zinc-800 space-y-1">
              <div className="flex items-center justify-between text-zinc-400 text-xs">
                <span>متابعون جدد</span>
                <Users className="w-4 h-4 text-pink-400" />
              </div>
              <div className="text-xl font-black text-white">+{data.newFollowers.toLocaleString()}</div>
              <div className="text-[10px] text-zinc-500 font-semibold">
                مشاهدات الملف: {data.profileViews.toLocaleString()}
              </div>
            </div>
          </div>

          {/* Daily views bar chart (server data only) */}
          <div className="p-4 rounded-2xl bg-zinc-950 border border-zinc-800 space-y-3">
            <div className="flex items-center justify-between text-xs">
              <span className="font-bold text-zinc-300">نشاط المشاهدات اليومي:</span>
              <span className="text-pink-400 font-mono text-[11px]">مجموع: {totalViews.toLocaleString()} مشاهدة</span>
            </div>

            <div className="h-40 flex items-end justify-between gap-2 pt-6 pb-2 px-1 border-b border-zinc-800/80">
              {data.viewsLast7Days.map((views, i) => (
                <div key={i} className="flex-1 flex flex-col items-center gap-1.5 h-full justify-end group">
                  <span className="text-[9px] font-mono text-zinc-500 opacity-0 group-hover:opacity-100 transition-opacity">
                    {views >= 1000 ? `${(views / 1000).toFixed(1)}k` : views}
                  </span>
                  <div
                    style={{ height: `${Math.max(views > 0 ? 4 : 0, (views / maxViews) * 100)}%` }}
                    className="w-full max-w-[28px] rounded-t-md bg-gradient-to-t from-pink-600 via-rose-500 to-cyan-400 group-hover:brightness-125 transition-all"
                  />
                  <span className="text-[10px] text-zinc-400 mt-1">{DAY_LABELS[i]}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Audience geography (server data only) */}
          <div className="p-4 rounded-2xl bg-zinc-950 border border-zinc-800 space-y-3">
            <div className="flex items-center gap-2 text-xs font-bold text-zinc-300">
              <MapPin className="w-4 h-4 text-cyan-400" />
              <span>التوزيع الجغرافي للجمهور:</span>
            </div>

            {data.topAudience.length === 0 ? (
              <p className="text-xs text-zinc-500 text-center py-4">لا توجد بيانات جغرافية بعد.</p>
            ) : (
              <div className="space-y-2 text-xs">
                {data.topAudience.map((loc, i) => (
                  <div key={i} className="space-y-1">
                    <div className="flex items-center justify-between text-[11px]">
                      <span className="text-zinc-300">{loc.region}</span>
                      <span className="text-zinc-400 font-mono">{loc.percentage}%</span>
                    </div>
                    <div className="w-full h-1.5 bg-zinc-800 rounded-full overflow-hidden">
                      <div style={{ width: `${loc.percentage}%` }} className="h-full bg-cyan-500 rounded-full" />
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
