import React, { useMemo, useState } from 'react';
import { ArrowRight, Check, Search, Globe2 } from 'lucide-react';

const LANGUAGES = [
  ['ar','العربية'],['en','English'],['fr','Français'],['es','Español'],['de','Deutsch'],['it','Italiano'],['pt','Português'],
  ['tr','Türkçe'],['ru','Русский'],['uk','Українська'],['nl','Nederlands'],['pl','Polski'],['sv','Svenska'],['no','Norsk'],
  ['da','Dansk'],['fi','Suomi'],['el','Ελληνικά'],['cs','Čeština'],['sk','Slovenčina'],['hu','Magyar'],['ro','Română'],
  ['bg','Български'],['sr','Српски'],['hr','Hrvatski'],['he','עברית'],['fa','فارسی'],['ur','اردو'],['hi','हिन्दी'],
  ['bn','বাংলা'],['ta','தமிழ்'],['te','తెలుగు'],['mr','मराठी'],['gu','ગુજરાતી'],['pa','ਪੰਜਾਬੀ'],['id','Bahasa Indonesia'],
  ['ms','Bahasa Melayu'],['vi','Tiếng Việt'],['th','ไทย'],['ko','한국어'],['ja','日本語'],['zh-CN','简体中文'],['zh-TW','繁體中文'],
  ['fil','Filipino'],['sw','Kiswahili'],['am','አማርኛ'],['yo','Yorùbá'],['ig','Igbo'],['ha','Hausa'],['zu','isiZulu'],
  ['af','Afrikaans'],['sq','Shqip'],['bs','Bosanski'],['ca','Català'],['et','Eesti'],['lv','Latviešu'],['lt','Lietuvių'],
  ['sl','Slovenščina'],['is','Íslenska'],['ga','Gaeilge'],['mt','Malti'],['mk','Македонски'],['ka','ქართული'],['hy','Հայերեն'],
  ['az','Azərbaycan'],['kk','Қазақша'],['uz','O‘zbek'],['mn','Монгол'],['ne','नेपाली'],['si','සිංහල'],['km','ខ្មែរ'],
  ['lo','ລາວ'],['my','မြန်မာ'],['jv','Basa Jawa'],['so','Soomaali'],['rw','Kinyarwanda'],['sn','ChiShona'],
];

export const LanguagesView: React.FC<{onBack:()=>void}> = ({onBack}) => {
  const [query,setQuery]=useState('');
  const [selected,setSelected]=useState(localStorage.getItem('zevora_language') || 'ar');
  const filtered=useMemo(()=>LANGUAGES.filter(([code,name])=>`${code} ${name}`.toLowerCase().includes(query.toLowerCase())),[query]);
  const choose=(code:string)=>{setSelected(code);localStorage.setItem('zevora_language',code);};
  return <div className="fixed inset-0 z-[80] bg-zinc-950 text-white overflow-y-auto" dir="rtl">
    <div className="sticky top-0 z-10 bg-zinc-950/95 backdrop-blur border-b border-white/10 p-4 flex items-center justify-between">
      <button onClick={onBack} className="flex items-center gap-2 font-bold"><ArrowRight className="w-5 h-5"/> اللغة</button>
      <Globe2 className="w-5 h-5 text-cyan-400"/>
    </div>
    <div className="max-w-lg mx-auto p-4">
      <div className="relative mb-4"><Search className="absolute right-3 top-3 w-4 h-4 text-zinc-500"/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="ابحث عن لغة..." className="w-full rounded-2xl bg-white/5 border border-white/10 p-3 pr-10 outline-none focus:border-cyan-500"/></div>
      <div className="rounded-3xl overflow-hidden border border-white/10 divide-y divide-white/5 bg-white/[0.03]">
        {filtered.map(([code,name])=><button key={code} onClick={()=>choose(code)} className="w-full p-4 flex items-center justify-between hover:bg-white/5"><span className="font-semibold">{name}</span>{selected===code&&<Check className="w-5 h-5 text-cyan-400"/>}</button>)}
      </div>
      <p className="text-xs text-zinc-500 mt-4 text-center">تم تجهيز واجهة اللغات. ترجمة النصوص الفعلية تحتاج ملفات ترجمة لكل لغة قبل الادعاء بدعمها بالكامل.</p>
    </div>
  </div>;
};

