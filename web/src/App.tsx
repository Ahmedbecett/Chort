import React, { useState, useEffect } from 'react';
import { api } from './services/api';
import { UserProfile, VideoItem } from './types';
import { INITIAL_USER, INITIAL_VIDEOS } from './data/mockData';
import { LoginScreen } from './components/LoginScreen';
import { BottomNav } from './components/BottomNav';
import { VideoFeed } from './components/VideoFeed';
import { ProfileView } from './components/ProfileView';
import { ProfileDrawer } from './components/ProfileDrawer';
import { SettingsView } from './components/SettingsView';
import { CameraStudio } from './components/CameraStudio';
import { UploadVideoView } from './components/UploadVideoView';
import { DownloadApkModal } from './components/DownloadApkModal';

// Modals
import { WalletModal } from './components/modals/WalletModal';
import { ActivityCenterModal } from './components/modals/ActivityCenterModal';
import { OfflineVideosModal } from './components/modals/OfflineVideosModal';
import { QrCodeModal } from './components/modals/QrCodeModal';
import { StudioAnalyticsModal } from './components/modals/StudioAnalyticsModal';
import { PromoteModal } from './components/modals/PromoteModal';

import { Download, Users, Bell, Play, X, Heart, MessageCircle, Share2, Music } from 'lucide-react';

export default function App() {
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false);
  const [user, setUser] = useState<UserProfile>(() => api.getUser());
  const [videos, setVideos] = useState<VideoItem[]>(INITIAL_VIDEOS);
  const [currentTab, setCurrentTab] = useState<'home' | 'friends' | 'inbox' | 'profile'>('home');

  // Modals & Sheets
  const [isDownloadModalOpen, setIsDownloadModalOpen] = useState(false);
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [isCameraOpen, setIsCameraOpen] = useState(false);
  const [isUploadOpen, setIsUploadOpen] = useState(false);
  const [preselectedVideoUrl, setPreselectedVideoUrl] = useState<string | undefined>();
  const [selectedVideoModal, setSelectedVideoModal] = useState<VideoItem | null>(null);

  // Drawer specific modals
  const [isWalletOpen, setIsWalletOpen] = useState(false);
  const [isActivityOpen, setIsActivityOpen] = useState(false);
  const [isOfflineOpen, setIsOfflineOpen] = useState(false);
  const [isQrOpen, setIsQrOpen] = useState(false);
  const [isStudioOpen, setIsStudioOpen] = useState(false);
  const [isPromoteOpen, setIsPromoteOpen] = useState(false);

  // Load videos from API
  useEffect(() => {
    api.getVideos().then((vids) => {
      setVideos(vids);
    });
  }, []);

  const handleUpdateUser = (updates: Partial<UserProfile>) => {
    const updated = api.updateUser(updates);
    setUser(updated);
  };

  const handleLogout = () => {
    api.setAuthStatus(false);
    setIsAuthenticated(false);
    setIsDrawerOpen(false);
    setIsSettingsOpen(false);
  };

  const handleOpenUpload = (videoUrl?: string) => {
    setPreselectedVideoUrl(videoUrl);
    setIsCameraOpen(false);
    setIsUploadOpen(true);
  };

  const handleVideoPublished = (newVideo: VideoItem) => {
    setVideos([newVideo, ...videos]);
    setCurrentTab('profile');
  };

  // If NOT logged in, show Login Screen first!
  if (!isAuthenticated) {
    return (
      <>
        <LoginScreen
          onLoginSuccess={() => {
            setIsAuthenticated(true);
            setUser(api.getUser());
          }}
          onOpenDownloadModal={() => setIsDownloadModalOpen(true)}
        />
        <DownloadApkModal
          isOpen={isDownloadModalOpen}
          onClose={() => setIsDownloadModalOpen(false)}
        />
      </>
    );
  }

  return (
    <div className="relative min-h-screen bg-black text-white font-sans select-none overflow-x-hidden">
      {/* Floating Global APK Download Badge (Always accessible) */}
      <div className="fixed top-3 left-3 z-50 pointer-events-auto">
        <button
          onClick={() => setIsDownloadModalOpen(true)}
          className="flex items-center gap-1.5 py-1.5 px-3 rounded-full bg-gradient-to-r from-pink-600 via-rose-600 to-cyan-600 hover:from-pink-500 hover:to-cyan-500 text-white text-xs font-bold shadow-lg shadow-pink-600/30 border border-white/20 active:scale-95 transition-all"
        >
          <Download className="w-3.5 h-3.5 animate-bounce" />
          <span>تحميل APK الأحدث v3.0.0</span>
        </button>
      </div>

      {/* Main Tab Content */}
      <main className="w-full h-full">
        {/* 1. Home Tab: Video Feed */}
        {currentTab === 'home' && (
          <VideoFeed
            videos={videos}
            onOpenDownloadModal={() => setIsDownloadModalOpen(true)}
            onOpenProfile={() => setCurrentTab('profile')}
            onOpenLive={() => setIsCameraOpen(true)}
          />
        )}

        {/* 2. Friends Tab */}
        {currentTab === 'friends' && (
          <div className="min-h-screen bg-black p-4 pb-24 text-right" dir="rtl">
            <div className="sticky top-0 bg-black/95 backdrop-blur-md py-4 border-b border-zinc-800 flex items-center justify-between z-10">
              <h2 className="text-lg font-black text-white">الأصدقاء والمقترحات 🇩🇿</h2>
              <button
                onClick={() => setIsDownloadModalOpen(true)}
                className="py-1 px-3 rounded-full text-xs font-bold bg-pink-600 text-white"
              >
                تحميل التطبيق
              </button>
            </div>

            <div className="py-6 space-y-4 max-w-md mx-auto">
              <p className="text-xs text-zinc-400">تابع أصدقائك وتفاعل مع فيديوهاتهم الحصرية:</p>
              
              {[
                { name: 'Amina Cooking 👩‍🍳', handle: '@amina.dz.chef', mutual: '12 صديق مشترك' },
                { name: 'Turbo DZ 🏎️', handle: '@turbo_motors', mutual: '8 أصدقاء مشتركين' },
                { name: 'VFX Masters Studio', handle: '@vfx.masters', mutual: '24 صديق مشترك' },
              ].map((friend, i) => (
                <div key={i} className="p-3.5 rounded-2xl bg-zinc-900 border border-zinc-800 flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="w-11 h-11 rounded-full bg-gradient-to-tr from-pink-500 to-cyan-500 flex items-center justify-center font-bold text-white">
                      {friend.name.charAt(0)}
                    </div>
                    <div>
                      <div className="font-bold text-xs text-white">{friend.name}</div>
                      <div className="text-[11px] text-zinc-400 font-mono" dir="ltr">{friend.handle}</div>
                      <div className="text-[10px] text-pink-400 mt-0.5">{friend.mutual}</div>
                    </div>
                  </div>
                  <button 
                    onClick={() => alert(`تمت متابعة ${friend.name}`)}
                    className="py-1.5 px-4 rounded-xl bg-pink-600 hover:bg-pink-500 text-white text-xs font-bold transition-colors"
                  >
                    متابعة
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* 3. Inbox Tab */}
        {currentTab === 'inbox' && (
          <div className="min-h-screen bg-black p-4 pb-24 text-right" dir="rtl">
            <div className="sticky top-0 bg-black/95 backdrop-blur-md py-4 border-b border-zinc-800 flex items-center justify-between z-10">
              <h2 className="text-lg font-black text-white">صندوق الوارد (99+)</h2>
              <Bell className="w-5 h-5 text-zinc-400" />
            </div>

            <div className="py-4 space-y-3 max-w-md mx-auto">
              <div className="p-3.5 rounded-2xl bg-gradient-to-r from-pink-950/40 to-zinc-900 border border-pink-800/40 flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-pink-600 flex items-center justify-center text-white shrink-0">
                  <Heart className="w-5 h-5" />
                </div>
                <div>
                  <div className="text-xs font-bold text-white">إشعارات التفاعل الجديدة</div>
                  <div className="text-[11px] text-zinc-400">أعجب 142 مستخدماً بفيديو جبال جرجرة الأخير</div>
                </div>
              </div>

              <div className="p-3.5 rounded-2xl bg-zinc-900 border border-zinc-800 flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-cyan-600 flex items-center justify-center text-white shrink-0">
                  <Users className="w-5 h-5" />
                </div>
                <div>
                  <div className="text-xs font-bold text-white">متابعون جدد اليوم</div>
                  <div className="text-[11px] text-zinc-400">بدأ @mehdi_oran31 و 28 آخرين بمتابعتك</div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* 4. Profile Tab */}
        {currentTab === 'profile' && (
          <ProfileView
            user={user}
            videos={videos}
            onOpenDrawer={() => setIsDrawerOpen(true)}
            onOpenDownloadModal={() => setIsDownloadModalOpen(true)}
            onSelectVideo={(video) => setSelectedVideoModal(video)}
            onUpdateUser={handleUpdateUser}
            onOpenUpload={() => setIsUploadOpen(true)}
          />
        )}
      </main>

      {/* Persistent Bottom Nav Bar */}
      <BottomNav
        currentTab={currentTab}
        onChangeTab={setCurrentTab}
        onOpenCreate={() => setIsCameraOpen(true)}
      />

      {/* Profile Slide-Out Drawer */}
      <ProfileDrawer
        isOpen={isDrawerOpen}
        onClose={() => setIsDrawerOpen(false)}
        onOpenWallet={() => setIsWalletOpen(true)}
        onOpenActivity={() => setIsActivityOpen(true)}
        onOpenOffline={() => setIsOfflineOpen(true)}
        onOpenQr={() => setIsQrOpen(true)}
        onOpenStudio={() => setIsStudioOpen(true)}
        onOpenPromote={() => setIsPromoteOpen(true)}
        onOpenSettings={() => setIsSettingsOpen(true)}
        onOpenDownloadModal={() => setIsDownloadModalOpen(true)}
        onLogout={handleLogout}
        user={user}
      />

      {/* Settings View */}
      {isSettingsOpen && (
        <SettingsView
          onBack={() => setIsSettingsOpen(false)}
          onLogout={handleLogout}
          onOpenDownloadModal={() => setIsDownloadModalOpen(true)}
          user={user}
        />
      )}

      {/* Camera Studio */}
      <CameraStudio
        isOpen={isCameraOpen}
        onClose={() => setIsCameraOpen(false)}
        onOpenUpload={handleOpenUpload}
      />

      {/* Upload Video View */}
      <UploadVideoView
        isOpen={isUploadOpen}
        onClose={() => setIsUploadOpen(false)}
        onVideoPublished={handleVideoPublished}
        initialVideoUrl={preselectedVideoUrl}
      />

      {/* Modals from Drawer */}
      <WalletModal
        isOpen={isWalletOpen}
        onClose={() => setIsWalletOpen(false)}
        user={user}
        onUpdateUser={handleUpdateUser}
      />

      <ActivityCenterModal
        isOpen={isActivityOpen}
        onClose={() => setIsActivityOpen(false)}
        onSelectVideo={(v) => setSelectedVideoModal(v)}
      />

      <OfflineVideosModal
        isOpen={isOfflineOpen}
        onClose={() => setIsOfflineOpen(false)}
        onPlayOfflineVideo={(v) => setSelectedVideoModal(v)}
      />

      <QrCodeModal
        isOpen={isQrOpen}
        onClose={() => setIsQrOpen(false)}
        user={user}
      />

      <StudioAnalyticsModal
        isOpen={isStudioOpen}
        onClose={() => setIsStudioOpen(false)}
      />

      <PromoteModal
        isOpen={isPromoteOpen}
        onClose={() => setIsPromoteOpen(false)}
        videos={videos}
        user={user}
      />

      {/* APK Direct Download Modal */}
      <DownloadApkModal
        isOpen={isDownloadModalOpen}
        onClose={() => setIsDownloadModalOpen(false)}
      />

      {/* Selected Video Player Modal */}
      {selectedVideoModal && (
        <div className="fixed inset-0 z-50 bg-black/95 backdrop-blur-md flex items-center justify-center p-0 sm:p-4" dir="rtl">
          <div className="relative w-full max-w-sm h-full sm:h-[85vh] bg-black rounded-none sm:rounded-2xl overflow-hidden flex flex-col justify-between shadow-2xl border border-zinc-800">
            {/* Top Close */}
            <div className="absolute top-4 right-4 z-20">
              <button
                onClick={() => setSelectedVideoModal(null)}
                className="w-9 h-9 rounded-full bg-black/60 text-white flex items-center justify-center hover:bg-black/80"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Video */}
            <video
              src={selectedVideoModal.url}
              poster={selectedVideoModal.thumbnail}
              controls
              autoPlay
              loop
              playsInline
              className="w-full h-full object-cover"
            />

            {/* Bottom info */}
            <div className="absolute bottom-4 inset-x-4 z-20 p-3 rounded-xl bg-black/60 backdrop-blur-md text-white text-xs space-y-1">
              <span className="font-bold">{selectedVideoModal.caption}</span>
              <div className="text-[11px] text-zinc-300">
                ❤️ {selectedVideoModal.likes} إعجاب · 💬 {selectedVideoModal.commentsCount} تعليق
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
