import React, { useState, useEffect } from 'react';
import { api } from './services/api';
import { UserProfile, VideoItem } from './types';
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
import { LanguagesView } from './components/LanguagesView';
import { OfficialProfileView } from './components/OfficialProfileView';
import { AdminPanel } from './components/AdminPanel';

import { Download, Users, Bell, Play, X, Heart, MessageCircle, Share2, Music } from 'lucide-react';

export default function App() {
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false);
  const [user, setUser] = useState<UserProfile>(() => api.getUser());
  const [videos, setVideos] = useState<VideoItem[]>([]);
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
  const [specialView, setSpecialView] = useState<'languages'|'official'|'admin'|null>(null);

  // Load videos from API
  useEffect(() => {
    const open = () => setSpecialView('languages');
    window.addEventListener('zevora:open-languages', open);
    return () => window.removeEventListener('zevora:open-languages', open);
  }, []);

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

  if (specialView === 'languages') return <LanguagesView onBack={() => setSpecialView(null)} />;
  if (specialView === 'official') return <OfficialProfileView onBack={() => setSpecialView(null)} onSelectVideo={(video) => setSelectedVideoModal(video)} />;
  if (specialView === 'admin') return <AdminPanel onBack={() => setSpecialView(null)} />;

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

      <div className="fixed top-3 right-3 z-50 flex gap-2">
        <button onClick={() => setSpecialView('official')} className="px-3 py-2 rounded-full bg-black/70 border border-white/10 text-xs font-bold backdrop-blur">ZEVORA الرسمي</button>
        <button onClick={() => setSpecialView('admin')} className="px-3 py-2 rounded-full bg-black/70 border border-white/10 text-xs font-bold backdrop-blur">الإدارة</button>
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

            <div className="py-20 text-center max-w-md mx-auto">
              <Users className="w-10 h-10 mx-auto text-zinc-700" />
              <p className="mt-4 text-sm font-bold text-zinc-300">لا توجد اقتراحات أصدقاء بعد</p>
              <p className="text-xs text-zinc-500 mt-2">ستظهر هنا الحسابات الحقيقية من الخادم عند توفرها.</p>
            </div>
          </div>
        )}

        {/* 3. Inbox Tab */}
        {currentTab === 'inbox' && (
          <div className="min-h-screen bg-black p-4 pb-24 text-right" dir="rtl">
            <div className="sticky top-0 bg-black/95 backdrop-blur-md py-4 border-b border-zinc-800 flex items-center justify-between z-10"><h2 className="text-lg font-black text-white">صندوق الوارد</h2><Bell className="w-5 h-5 text-zinc-400"/></div>
            <div className="py-20 text-center max-w-md mx-auto"><Bell className="w-10 h-10 mx-auto text-zinc-700"/><p className="mt-4 text-sm font-bold text-zinc-300">لا توجد إشعارات معروضة</p><p className="text-xs text-zinc-500 mt-2">سيتم عرض الإشعارات الحقيقية بعد اتصال الحساب بواجهة الإشعارات الخلفية.</p></div>
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

