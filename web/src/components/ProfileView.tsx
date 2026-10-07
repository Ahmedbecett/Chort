===== ZEVORA_BUNDLE_PATH: web/src/components/ProfileView.tsx =====
import React, { useState } from 'react';
import { 
  Menu, Edit3, UserPlus, Shield, Sparkles, Plus, Share2, 
  Grid, Lock, Repeat, Bookmark, Heart, Play, Download, CheckCircle2 
} from 'lucide-react';
import { UserProfile, VideoItem } from '../types';

interface ProfileViewProps {
  user: UserProfile;
  videos: VideoItem[];
  onOpenDrawer: () => void;
  onOpenDownloadModal: () => void;
  onSelectVideo: (video: VideoItem) => void;
  onUpdateUser: (updates: Partial<UserProfile>) => void;
  onOpenUpload: () => void;
}

export const ProfileView: React.FC<ProfileViewProps> = ({
  user,
  videos,
  onOpenDrawer,
  onOpenDownloadModal,
  onSelectVideo,
  onUpdateUser,
  onOpenUpload,
}) => {
  const [activeTab, setActiveTab] = useState<'public' | 'private' | 'repost' | 'saved' | 'liked'>('public');
  const [statusBubble, setStatusBubble] = useState(user.statusText || "What's good? 💭");
  const [isEditingBio, setIsEditingBio] = useState(false);
  const [bioInput, setBioInput] = useState(user.bio);
  const [subscribed, setSubscribed] = useState(false);

  const publicVideos = videos.filter((v) => v.author.username === user.username);
  const likedVideos = videos.filter((v) => v.isLiked);
  const savedVideos = videos.filter((v) => v.isSaved);

  const handleSaveBio = () => {
    onUpdateUser({ bio: bioInput });
    setIsEditingBio(false);
  };

  return (
    <div className="min-h-screen bg-black text-white pb-24 overflow-x-hidden" dir="rtl">
      {/* Top Header Bar */}
      <div className="sticky top-0 z-20 bg-zinc-950/90 backdrop-blur-md px-4 py-3 border-b border-zinc-800/80 flex items-center justify-between">
        <div className="flex items-center gap-3">
          {/* Hamburger Menu (Opens Drawer) */}
          <button
            onClick={onOpenDrawer}
            className="w-9 h-9 rounded-full bg-zinc-900 border border-zinc-800 flex items-center justify-center text-zinc-300 hover:text-white hover:bg-zinc-800 transition-colors"
          >
            <Menu className="w-5 h-5" />
          </button>

          {/* Add Friends */}
          <button 
            onClick={() => alert('تم فتح قائمة اقتراحات الأصدقاء')}
            className="w-9 h-9 rounded-full bg-zinc-900 border border-zinc-800 flex items-center justify-center text-zinc-300 hover:text-white transition-colors"
          >
            <UserPlus className="w-4 h-4" />
          </button>
        </div>

        {/* Center: Level Badge */}
        <div className="flex items-center gap-1.5 px-3 py-1 rounded-full bg-gradient-to-r from-amber-500/20 to-yellow-500/10 border border-amber-500/40">
          <Shield className="w-3.5 h-3.5 text-amber-400" />
          <span className="text-xs font-black text-amber-300 font-mono">Lv. {user.level}</span>
        </div>

        {/* Right: Edit & APK button */}
        <div className="flex items-center gap-2">
          <button
            onClick={() => setIsEditingBio(!isEditingBio)}
            className="w-9 h-9 rounded-full bg-zinc-900 border border-zinc-800 flex items-center justify-center text-zinc-300 hover:text-white transition-colors"
          >
            <Edit3 className="w-4 h-4" />
          </button>

          <button
            onClick={onOpenDownloadModal}
            className="flex items-center gap-1 py-1 px-2.5 rounded-full text-xs font-semibold bg-gradient-to-r from-pink-600 to-cyan-600 text-white shadow-md active:scale-95 transition-all"
          >
            <Download className="w-3 h-3" />
            <span>APK</span>
          </button>
        </div>
      </div>

      {/* Profile Bio & Stats Section */}
      <div className="px-4 pt-4 pb-2 flex flex-col items-center text-center">
        {/* Avatar with Story Ring and Status Bubble */}
        <div className="relative mb-3">
          {/* Status Bubble ("What's good? 💭") */}
          <div 
            onClick={() => {
              const newStatus = prompt('اكتب حالتك الجديدة:', statusBubble);
              if (newStatus !== null) {
                setStatusBubble(newStatus);
                onUpdateUser({ statusText: newStatus });
              }
            }}
            className="absolute -top-7 right-0 transform translate-x-2 bg-zinc-900/90 border border-zinc-700/80 px-2.5 py-1 rounded-full text-[10px] font-medium text-zinc-200 shadow-lg cursor-pointer hover:border-pink-500 transition-colors whitespace-nowrap flex items-center gap-1"
          >
            <span>{statusBubble}</span>
            <Sparkles className="w-2.5 h-2.5 text-pink-400" />
          </div>

          {/* Avatar Ring */}
          <div className="w-24 h-24 rounded-full p-1 bg-gradient-to-tr from-cyan-500 via-pink-500 to-amber-400">
            <img
              src={user.avatar}
              alt=""
              className="w-full h-full rounded-full object-cover border-2 border-black"
            />
          </div>

          {/* Plus button to add story */}
          <button 
            onClick={onOpenUpload}
            className="absolute bottom-0 right-0 w-7 h-7 rounded-full bg-cyan-500 border-2 border-black flex items-center justify-center text-black font-bold hover:scale-110 transition-transform shadow-md"
            title="إضافة قصة أو فيديو جديد"
          >
            <Plus className="w-4 h-4" />
          </button>
        </div>

        {/* Name and Handle */}
        <div className="flex items-center gap-1.5">
          <h2 className="text-lg font-black text-white">{user.name}</h2>
          {user.isVerified && (
            <CheckCircle2 className="w-4 h-4 text-cyan-400 fill-cyan-400" />
          )}
        </div>
        <p className="text-xs text-zinc-400 font-mono mt-0.5" dir="ltr">@{user.username}</p>

        {/* Stats Row (Following · Followers · Likes) */}
        <div className="flex items-center justify-center gap-6 my-4 w-full max-w-xs">
          <div className="text-center">
            <span className="block text-base font-extrabold text-white">{user.followingCount.toLocaleString()}</span>
            <span className="text-[11px] text-zinc-400">Following</span>
          </div>
          <div className="w-[1px] h-6 bg-zinc-800" />
          <div className="text-center">
            <span className="block text-base font-extrabold text-white">{user.followersCount.toLocaleString()}</span>
            <span className="text-[11px] text-zinc-400">Followers</span>
          </div>
          <div className="w-[1px] h-6 bg-zinc-800" />
          <div className="text-center">
            <span className="block text-base font-extrabold text-white">
              {(user.likesCount / 1000).toFixed(1)}K
            </span>
            <span className="text-[11px] text-zinc-400">Likes</span>
          </div>
        </div>

        {/* Action Buttons Row */}
        <div className="flex items-center gap-2 w-full max-w-sm mb-3">
          <button
            onClick={() => setIsEditingBio(!isEditingBio)}
            className="flex-1 py-2 px-3 rounded-xl bg-zinc-900 border border-zinc-700 hover:bg-zinc-800 text-xs font-semibold text-white transition-colors"
          >
            تعديل الملف الشخصي
          </button>
          <button
            onClick={() => {
              navigator.clipboard.writeText(`${window.location.origin}/@${user.username}`);
              alert('تم نسخ رابط ملفك الشخصي!');
            }}
            className="py-2 px-3 rounded-xl bg-zinc-900 border border-zinc-700 hover:bg-zinc-800 text-xs font-semibold text-white transition-colors flex items-center gap-1"
          >
            <Share2 className="w-3.5 h-3.5" />
            <span>مشاركة</span>
          </button>
          <button
            onClick={() => setSubscribed(!subscribed)}
            className={`py-2 px-3.5 rounded-xl text-xs font-bold transition-all shadow-md flex items-center gap-1 ${
              subscribed
                ? 'bg-zinc-800 text-pink-400 border border-pink-500/40'
                : 'bg-gradient-to-r from-pink-600 to-rose-600 hover:from-pink-500 hover:to-rose-500 text-white'
            }`}
          >
            <span>🚀 {subscribed ? 'مشترك' : 'اشتراك'}</span>
          </button>
        </div>

        {/* Bio Text or Editor */}
        {isEditingBio ? (
          <div className="w-full max-w-sm p-3 rounded-xl bg-zinc-900 border border-zinc-700 space-y-2 mb-3">
            <textarea
              value={bioInput}
              onChange={(e) => setBioInput(e.target.value)}
              rows={3}
              className="w-full bg-zinc-950 border border-zinc-800 rounded-lg p-2 text-xs text-white resize-none"
            />
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setIsEditingBio(false)}
                className="py-1 px-3 text-xs text-zinc-400 hover:text-white"
              >
                إلغاء
              </button>
              <button
                onClick={handleSaveBio}
                className="py-1 px-3 bg-pink-600 text-xs font-bold text-white rounded-lg"
              >
                حفظ
              </button>
            </div>
          </div>
        ) : (
          <p className="text-xs text-zinc-300 max-w-sm leading-relaxed whitespace-pre-line mb-3">
            {user.bio}
          </p>
        )}
      </div>

      {/* 5 Profile Tabs matching screenshot */}
      <div className="border-t border-zinc-800/80 bg-zinc-950/70">
        <div className="grid grid-cols-5 text-center">
          {/* Tab 1: Public Grid */}
          <button
            onClick={() => setActiveTab('public')}
            className={`py-3 flex flex-col items-center justify-center transition-all border-b-2 ${
              activeTab === 'public'
                ? 'border-white text-white'
                : 'border-transparent text-zinc-500 hover:text-zinc-300'
            }`}
          >
            <Grid className="w-5 h-5" />
          </button>

          {/* Tab 2: Private (Lock) */}
          <button
            onClick={() => setActiveTab('private')}
            className={`py-3 flex flex-col items-center justify-center transition-all border-b-2 ${
              activeTab === 'private'
                ? 'border-white text-white'
                : 'border-transparent text-zinc-500 hover:text-zinc-300'
            }`}
          >
            <Lock className="w-5 h-5" />
          </button>

          {/* Tab 3: Repost */}
          <button
            onClick={() => setActiveTab('repost')}
            className={`py-3 flex flex-col items-center justify-center transition-all border-b-2 ${
              activeTab === 'repost'
                ? 'border-white text-white'
                : 'border-transparent text-zinc-500 hover:text-zinc-300'
            }`}
          >
            <Repeat className="w-5 h-5" />
          </button>

          {/* Tab 4: Saved / Bookmarks */}
          <button
            onClick={() => setActiveTab('saved')}
            className={`py-3 flex flex-col items-center justify-center transition-all border-b-2 ${
              activeTab === 'saved'
                ? 'border-white text-white'
                : 'border-transparent text-zinc-500 hover:text-zinc-300'
            }`}
          >
            <Bookmark className="w-5 h-5" />
          </button>

          {/* Tab 5: Liked */}
          <button
            onClick={() => setActiveTab('liked')}
            className={`py-3 flex flex-col items-center justify-center transition-all border-b-2 ${
              activeTab === 'liked'
                ? 'border-white text-white'
                : 'border-transparent text-zinc-500 hover:text-zinc-300'
            }`}
          >
            <Heart className="w-5 h-5" />
          </button>
        </div>
      </div>

      {/* Videos Grid (3 Columns) */}
      <div className="p-0.5">
        {/* Public Videos */}
        {activeTab === 'public' && (
          <div className="grid grid-cols-3 gap-0.5">
            {publicVideos.map((video) => (
              <div
                key={video.id}
                onClick={() => onSelectVideo(video)}
                className="relative aspect-[3/4] bg-zinc-900 overflow-hidden cursor-pointer group"
              >
                <img
                  src={video.thumbnail}
                  alt=""
                  className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                />

                {/* Pinned badge */}
                {video.isPinned && (
                  <div className="absolute top-1.5 right-1.5 bg-red-600 text-white text-[9px] font-bold px-1.5 py-0.5 rounded shadow">
                    مثبت
                  </div>
                )}

                {/* Bottom View Count Overlay matching screenshot: ▷ 486, ▷ 634 */}
                <div className="absolute bottom-1 right-1.5 flex items-center gap-1 text-[11px] font-bold text-white drop-shadow">
                  <Play className="w-3 h-3 fill-current" />
                  <span>{video.views >= 1000 ? `${(video.views / 1000).toFixed(1)}k` : video.views}</span>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Private Tab */}
        {activeTab === 'private' && (
          <div className="py-16 text-center text-zinc-500 text-xs space-y-2">
            <Lock className="w-8 h-8 mx-auto text-zinc-600" />
            <p>لا توجد فيديوهات خاصة حالياً. يمكنك جعل أي فيديو خاصاً عند النشر.</p>
          </div>
        )}

        {/* Repost Tab */}
        {activeTab === 'repost' && (
          <div className="py-16 text-center text-zinc-500 text-xs space-y-2">
            <Repeat className="w-8 h-8 mx-auto text-zinc-600" />
            <p>لم تقم بإعادة نشر أي فيديو بعد.</p>
          </div>
        )}

        {/* Saved Tab */}
        {activeTab === 'saved' && (
          <div className="grid grid-cols-3 gap-0.5">
            {savedVideos.length === 0 ? (
              <div className="col-span-3 py-16 text-center text-zinc-500 text-xs space-y-2">
                <Bookmark className="w-8 h-8 mx-auto text-zinc-600" />
                <p>لا توجد مقاطع في المفضلة بعد.</p>
              </div>
            ) : (
              savedVideos.map((video) => (
                <div
                  key={video.id}
                  onClick={() => onSelectVideo(video)}
                  className="relative aspect-[3/4] bg-zinc-900 overflow-hidden cursor-pointer"
                >
                  <img src={video.thumbnail} alt="" className="w-full h-full object-cover" />
                  <div className="absolute bottom-1 right-1.5 flex items-center gap-1 text-[11px] font-bold text-white drop-shadow">
                    <Play className="w-3 h-3 fill-current" />
                    <span>{video.views}</span>
                  </div>
                </div>
              ))
            )}
          </div>
        )}

        {/* Liked Tab */}
        {activeTab === 'liked' && (
          <div className="grid grid-cols-3 gap-0.5">
            {likedVideos.length === 0 ? (
              <div className="col-span-3 py-16 text-center text-zinc-500 text-xs space-y-2">
                <Heart className="w-8 h-8 mx-auto text-zinc-600" />
                <p>لا توجد مقاطع معجب بها بعد.</p>
              </div>
            ) : (
              likedVideos.map((video) => (
                <div
                  key={video.id}
                  onClick={() => onSelectVideo(video)}
                  className="relative aspect-[3/4] bg-zinc-900 overflow-hidden cursor-pointer"
                >
                  <img src={video.thumbnail} alt="" className="w-full h-full object-cover" />
                  <div className="absolute bottom-1 right-1.5 flex items-center gap-1 text-[11px] font-bold text-white drop-shadow">
                    <Play className="w-3 h-3 fill-current" />
                    <span>{video.views}</span>
                  </div>
                </div>
              ))
            )}
          </div>
        )}
      </div>
    </div>
  );
};

===== END ZEVORA_BUNDLE_FILE =====
