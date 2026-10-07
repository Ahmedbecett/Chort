===== ZEVORA_BUNDLE_PATH: web/src/components/OfficialProfileView.tsx =====
import React, { useEffect, useState } from 'react';
import { ArrowRight, BadgeCheck, Grid3X3, Link2, ShieldCheck } from 'lucide-react';
import { api } from '../services/api';
import { VideoItem } from '../types';

export const OfficialProfileView: React.FC<{onBack:()=>void; onSelectVideo:(v:VideoItem)=>void}> = ({onBack,onSelectVideo}) => {
  const [videos,setVideos]=useState<VideoItem[]>([]); const [loading,setLoading]=useState(true);
  useEffect(()=>{api.getVideos().then(v=>{setVideos(v.filter(x=>x.author.username==='zevora_official'||x.author.name.toLowerCase().includes('zevora')));}).finally(()=>setLoading(false));},[]);
  return <div className="fixed inset-0 z-[75] bg-black text-white overflow-y-auto" dir="rtl">
    <header className="sticky top-0 z-20 bg-black/90 backdrop-blur border-b border-white/10 p-4 flex items-center justify-between"><button onClick={onBack}><ArrowRight/></button><b>الملف الرسمي</b><ShieldCheck className="text-cyan-400"/></header>
    <div className="max-w-lg mx-auto">
      <div className="p-6 text-center border-b border-white/10">
        <img src="/vira_zen_icon.png" className="w-24 h-24 rounded-3xl mx-auto shadow-[0_0_40px_rgba(168,85,247,.35)]"/>
        <div className="mt-3 flex justify-center items-center gap-1.5"><h1 className="text-2xl font-black">ZEVORA</h1><BadgeCheck className="w-5 h-5 text-cyan-400 fill-cyan-400/20"/></div>
        <p className="text-xs text-zinc-400">@zevora_official</p><p className="text-sm text-zinc-300 mt-3">الحساب الرسمي لـ ZEVORA — Watch • Create • Share</p>
        <div className="flex justify-center gap-8 mt-5 text-sm"><span><b>{videos.length}</b><small className="block text-zinc-500">فيديو حقيقي</small></span><span><b>—</b><small className="block text-zinc-500">المتابعون</small></span><span><b>—</b><small className="block text-zinc-500">الإعجابات</small></span></div>
        <button className="mt-5 px-5 py-2 rounded-xl bg-white/5 border border-white/10 text-sm flex items-center gap-2 mx-auto"><Link2 className="w-4 h-4"/> مشاركة الملف</button>
      </div>
      <div className="p-2 flex items-center justify-center border-b border-white/10"><Grid3X3/></div>
      {loading?<div className="p-10 text-center text-zinc-500">جاري تحميل المحتوى الحقيقي...</div>:videos.length===0?<div className="p-10 text-center text-zinc-500">لا توجد فيديوهات منشورة لهذا الحساب بعد.</div>:<div className="grid grid-cols-3 gap-0.5">{videos.map(v=><button key={v.id} onClick={()=>onSelectVideo(v)} className="aspect-[9/16] bg-zinc-900 overflow-hidden"><img src={v.thumbnail} className="w-full h-full object-cover"/></button>)}</div>}
    </div>
  </div>;
};

===== END ZEVORA_BUNDLE_FILE =====
