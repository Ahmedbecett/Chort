import React, { useState, useRef, useEffect } from 'react';
import { 
  Heart, MessageCircle, Bookmark, Share2, Music, Play, Pause, 
  Volume2, VolumeX, Search, Download, Plus, Check, Send, X, Radio 
} from 'lucide-react';
import { VideoItem, CommentItem } from '../types';
import { api } from '../services/api';

interface VideoFeedProps {
  videos: VideoItem[];
  onOpenDownloadModal: () => void;
  onOpenProfile: () => void;
  onOpenLive: () => void;
}

export const VideoFeed: React.FC<VideoFeedProps> = ({
  videos,
  onOpenDownloadModal,
  onOpenProfile,
  onOpenLive,
}) => {
  const [currentIndex, setCurrentIndex] = useState(0);
  const [feedMode, setFeedMode] = useState<'foryou' | 'following'>('foryou');
  const [isPlaying, setIsPlaying] = useState(true);
  const [isMuted, setIsMuted] = useState(false);
  const [showSearch, setShowSearch] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  
  // Like heart burst animation
  const [showHeartBurst, setShowHeartBurst] = useState(false);
  const [followedAuthors, setFollowedAuthors] = useState<string[]>([]);

  // Comments drawer
  const [isCommentsOpen, setIsCommentsOpen] = useState(false);
  const [commentsList, setCommentsList] = useState<CommentItem[]>([]);
  const [newCommentText, setNewCommentText] = useState('');

  const videoRef = useRef<HTMLVideoElement | null>(null);

  const currentVideo = videos[currentIndex] || videos[0];

  // Load comments & track watch history when current video changes
  useEffect(() => {
    if (currentVideo) {
      setCommentsList(api.getComments(currentVideo.id));
      api.recordWatchHistory(currentVideo);
      setIsPlaying(true);
    }
  }, [currentIndex, currentVideo]);

  // Handle play/pause
  const togglePlay = () => {
    if (!videoRef.current) return;
    if (isPlaying) {
      videoRef.current.pause();
      setIsPlaying(false);
    } else {
      videoRef.current.play().catch(() => {});
      setIsPlaying(true);
    }
  };

  // Double tap to like
  const handleDoubleTap = async () => {
    setShowHeartBurst(true);
    setTimeout(() => setShowHeartBurst(false), 900);
    if (!currentVideo.isLiked) {
      await api.toggleLikeVideo(currentVideo.id);
      currentVideo.isLiked = true;
      currentVideo.likes += 1;
    }
  };

  const handleLikeClick = async () => {
    const res = await api.toggleLikeVideo(currentVideo.id);
    currentVideo.isLiked = res.liked;
    currentVideo.likes = res.count;
  };

  const handleSaveClick = async () => {
    const res = await api.toggleSaveVideo(currentVideo.id);
    currentVideo.isSaved = res.saved;
    if (res.saved) {
      api.saveOfflineVideo(currentVideo);
      alert('تم حفظ الفيديو في المفضلة ومساحة المشاهدة بدون إنترنت!');
    }
  };

  const handleFollowToggle = (username: string) => {
    if (followedAuthors.includes(username)) {
      setFollowedAuthors(followedAuthors.filter((u) => u !== username));
    } else {
      setFollowedAuthors([...followedAuthors, username]);
    }
  };

  const handleShareClick = () => {
    navigator.clipboard.writeText(`${window.location.origin}/v/${currentVideo.id}`);
    alert('تم نسخ رابط الفيديو إلى الحافظة!');
  };

  const handlePostComment = () => {
    if (!newCommentText.trim()) return;
    const added = api.addComment(currentVideo.id, newCommentText);
    setCommentsList([added, ...commentsList]);
    setNewCommentText('');
    currentVideo.commentsCount += 1;
  };

  const filteredVideos = searchQuery
    ? videos.filter((v) => 
        v.caption.toLowerCase().includes(searchQuery.toLowerCase()) ||
        v.tags.some(t => t.toLowerCase().includes(searchQuery.toLowerCase()))
      )
    : videos;

  return (
    <div className="relative h-screen w-full bg-black overflow-hidden flex flex-col justify-between" dir="rtl">
      {/* Top Header Bar */}
      <div className="absolute top-0 inset-x-0 z-30 px-4 pt-4 pb-2 flex items-center justify-between bg-gradient-to-b from-black/80 via-black/30 to-transparent">
        {/* LIVE button */}
        <button
          onClick={onOpenLive}
          className="flex items-center gap-1.5 py-1 px-3 rounded-full bg-red-600/80 backdrop-blur-md text-white text-xs font-bold animate-pulse"
        >
          <Radio className="w-3.5 h-3.5" />
          <span>LIVE</span>
        </button>

        {/* Following / For You Switcher */}
        <div className="flex items-center gap-4 text-sm font-bold text-zinc-400">
          <button
            onClick={() => setFeedMode('following')}
            className={`transition-colors ${feedMode === 'following' ? 'text-white border-b-2 border-white pb-0.5' : 'hover:text-zinc-200'}`}
          >
            أتابعهم
          </button>
          <span className="text-zinc-600">|</span>
          <button
            onClick={() => setFeedMode('foryou')}
            className={`transition-colors ${feedMode === 'foryou' ? 'text-white border-b-2 border-white pb-0.5' : 'hover:text-zinc-200'}`}
          >
            لك (For You)
          </button>
        </div>

        {/* Right Search & APK */}
        <div className="flex items-center gap-2">
          <button
            onClick={() => setShowSearch(!showSearch)}
            className="w-8 h-8 rounded-full bg-black/40 backdrop-blur-md text-white flex items-center justify-center hover:bg-black/60 transition-colors"
          >
            <Search className="w-4 h-4" />
          </button>

          <button
            onClick={onOpenDownloadModal}
            className="flex items-center gap-1 py-1 px-2.5 rounded-full text-xs font-semibold bg-gradient-to-r from-pink-600 to-cyan-600 text-white shadow-md active:scale-95 transition-all"
          >
            <Download className="w-3.5 h-3.5" />
            <span>APK</span>
          </button>
        </div>
      </div>

      {/* Search Input Bar (when toggled) */}
      {showSearch && (
        <div className="absolute top-14 inset-x-4 z-40 bg-zinc-900/90 backdrop-blur-md border border-zinc-700 rounded-xl p-2 flex items-center gap-2 animate-in slide-in-from-top-2">
          <Search className="w-4 h-4 text-zinc-400 shrink-0" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="ابحث بالهاشتاج، الأغنية، أو صانع المحتوى..."
            className="flex-1 bg-transparent text-xs text-white placeholder-zinc-500 focus:outline-none"
            autoFocus
          />
          {searchQuery && (
            <button onClick={() => setSearchQuery('')} className="text-zinc-400 hover:text-white">
              <X className="w-4 h-4" />
            </button>
          )}
        </div>
      )}

      {/* Main Video Player */}
      <div 
        className="relative flex-1 w-full h-full flex items-center justify-center overflow-hidden cursor-pointer"
        onClick={togglePlay}
        onDoubleClick={handleDoubleTap}
      >
        <video
          ref={videoRef}
          src={currentVideo?.url}
          poster={currentVideo?.thumbnail}
          loop
          autoPlay
          muted={isMuted}
          playsInline
          className="w-full h-full object-cover"
        />

        {/* Center Play Icon when paused */}
        {!isPlaying && (
          <div className="absolute inset-0 flex items-center justify-center bg-black/30 pointer-events-none">
            <div className="w-16 h-16 rounded-full bg-black/60 backdrop-blur-md flex items-center justify-center text-white">
              <Play className="w-8 h-8 fill-current" />
            </div>
          </div>
        )}

        {/* Double-tap heart burst effect */}
        {showHeartBurst && (
          <div className="absolute inset-0 flex items-center justify-center pointer-events-none animate-in zoom-in-50 duration-300">
            <Heart className="w-28 h-28 text-pink-500 fill-pink-500 filter drop-shadow-2xl animate-pulse" />
          </div>
        )}

        {/* Sound toggle button */}
        <button
          onClick={(e) => {
            e.stopPropagation();
            setIsMuted(!isMuted);
          }}
          className="absolute top-20 left-4 z-20 w-8 h-8 rounded-full bg-black/40 backdrop-blur-md text-white flex items-center justify-center hover:bg-black/60 transition-colors"
        >
          {isMuted ? <VolumeX className="w-4 h-4" /> : <Volume2 className="w-4 h-4" />}
        </button>

        {/* Next / Previous video swiper controls for desktop / click navigation */}
        <div className="absolute left-4 top-1/2 -translate-y-1/2 z-20 flex flex-col gap-2">
          {currentIndex > 0 && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                setCurrentIndex(currentIndex - 1);
              }}
              className="w-8 h-8 rounded-full bg-black/40 backdrop-blur-md text-white hover:bg-black/70 flex items-center justify-center text-xs"
              title="الفيديو السابق"
            >
              ▲
            </button>
          )}
          {currentIndex < filteredVideos.length - 1 && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                setCurrentIndex(currentIndex + 1);
              }}
              className="w-8 h-8 rounded-full bg-black/40 backdrop-blur-md text-white hover:bg-black/70 flex items-center justify-center text-xs"
              title="الفيديو التالي"
            >
              ▼
            </button>
          )}
        </div>

        {/* Right Action Sidebar (TikTok style) */}
        <div 
          className="absolute bottom-20 left-3 z-30 flex flex-col items-center gap-4 text-white"
          onClick={(e) => e.stopPropagation()}
        >
          {/* Author Avatar with follow (+) */}
          <div className="relative">
            <img
              src={currentVideo?.author.avatar}
              alt=""
              onClick={onOpenProfile}
              className="w-11 h-11 rounded-full border-2 border-white object-cover cursor-pointer hover:scale-105 transition-transform"
            />
            {!followedAuthors.includes(currentVideo?.author.username) && (
              <button
                onClick={() => handleFollowToggle(currentVideo?.author.username)}
                className="absolute -bottom-1.5 left-1/2 -translate-x-1/2 w-4 h-4 rounded-full bg-pink-500 text-white flex items-center justify-center text-[10px] font-bold shadow-md hover:scale-110 transition-transform"
              >
                +
              </button>
            )}
          </div>

          {/* Like Button */}
          <button onClick={handleLikeClick} className="flex flex-col items-center gap-0.5 group">
            <div className={`p-2 rounded-full transition-all ${currentVideo?.isLiked ? 'text-pink-500 scale-110' : 'text-white'}`}>
              <Heart className={`w-7 h-7 ${currentVideo?.isLiked ? 'fill-current' : ''}`} />
            </div>
            <span className="text-[11px] font-bold drop-shadow font-mono">{currentVideo?.likes}</span>
          </button>

          {/* Comments Button */}
          <button onClick={() => setIsCommentsOpen(true)} className="flex flex-col items-center gap-0.5 group">
            <div className="p-2 rounded-full text-white">
              <MessageCircle className="w-7 h-7" />
            </div>
            <span className="text-[11px] font-bold drop-shadow font-mono">{currentVideo?.commentsCount}</span>
          </button>

          {/* Save / Bookmark Button */}
          <button onClick={handleSaveClick} className="flex flex-col items-center gap-0.5 group">
            <div className={`p-2 rounded-full transition-all ${currentVideo?.isSaved ? 'text-amber-400 scale-110' : 'text-white'}`}>
              <Bookmark className={`w-7 h-7 ${currentVideo?.isSaved ? 'fill-current' : ''}`} />
            </div>
            <span className="text-[11px] font-bold drop-shadow font-mono">{currentVideo?.saves}</span>
          </button>

          {/* Share Button */}
          <button onClick={handleShareClick} className="flex flex-col items-center gap-0.5 group">
            <div className="p-2 rounded-full text-white">
              <Share2 className="w-7 h-7" />
            </div>
            <span className="text-[11px] font-bold drop-shadow font-mono">{currentVideo?.shares}</span>
          </button>

          {/* Spinning Audio Disc */}
          <div className="w-10 h-10 rounded-full border-2 border-zinc-700 bg-zinc-900 p-1 flex items-center justify-center animate-spin">
            <div className="w-4 h-4 rounded-full bg-pink-500 flex items-center justify-center">
              <Music className="w-2.5 h-2.5 text-white" />
            </div>
          </div>
        </div>

        {/* Bottom Metadata Info */}
        <div 
          className="absolute bottom-16 right-4 left-16 z-20 text-white space-y-2 pointer-events-auto"
          onClick={(e) => e.stopPropagation()}
        >
          {/* Author Name */}
          <div className="flex items-center gap-2">
            <span onClick={onOpenProfile} className="font-extrabold text-sm hover:underline cursor-pointer">
              @{currentVideo?.author.username}
            </span>
            {currentVideo?.author.isVerified && (
              <span className="bg-cyan-500 text-black text-[9px] font-bold px-1.5 py-0.2 rounded-full">✓</span>
            )}
          </div>

          {/* Caption */}
          <p className="text-xs text-zinc-100 line-clamp-2 leading-relaxed drop-shadow">
            {currentVideo?.caption}
          </p>

          {/* Audio Title */}
          <div className="flex items-center gap-2 text-xs text-zinc-300">
            <Music className="w-3.5 h-3.5 text-pink-400 shrink-0" />
            <span className="truncate max-w-[220px]">
              {currentVideo?.song.title} - {currentVideo?.song.artist}
            </span>
          </div>
        </div>
      </div>

      {/* Comments Drawer */}
      {isCommentsOpen && (
        <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-end" dir="rtl">
          <div className="w-full bg-zinc-900 border-t border-zinc-800 rounded-t-3xl max-h-[70vh] flex flex-col p-4 shadow-2xl animate-in slide-in-from-bottom duration-200">
            {/* Header */}
            <div className="flex items-center justify-between pb-3 border-b border-zinc-800">
              <span className="text-xs font-bold text-white">التعليقات ({commentsList.length})</span>
              <button onClick={() => setIsCommentsOpen(false)} className="p-1 rounded-full text-zinc-400 hover:text-white">
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Comments list */}
            <div className="flex-1 overflow-y-auto py-3 space-y-3">
              {commentsList.map((c) => (
                <div key={c.id} className="flex items-start gap-3 text-xs">
                  <img src={c.userAvatar} alt="" className="w-8 h-8 rounded-full object-cover shrink-0" />
                  <div className="flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-zinc-200">@{c.username}</span>
                      <span className="text-[10px] text-zinc-500">{c.timestamp}</span>
                    </div>
                    <p className="text-zinc-300 mt-1 leading-relaxed">{c.text}</p>
                  </div>
                  <button className="flex flex-col items-center text-zinc-500 hover:text-pink-500">
                    <Heart className="w-3.5 h-3.5" />
                    <span className="text-[10px] font-mono">{c.likes}</span>
                  </button>
                </div>
              ))}
            </div>

            {/* Comment input */}
            <div className="pt-2 border-t border-zinc-800 flex items-center gap-2">
              <input
                type="text"
                value={newCommentText}
                onChange={(e) => setNewCommentText(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handlePostComment()}
                placeholder="أضف تعليقاً..."
                className="flex-1 bg-zinc-950 border border-zinc-700 rounded-full px-4 py-2 text-xs text-white placeholder-zinc-500 focus:outline-none focus:border-pink-500"
              />
              <button
                onClick={handlePostComment}
                className="p-2 rounded-full bg-pink-600 hover:bg-pink-500 text-white transition-colors"
              >
                <Send className="w-4 h-4 rotate-180" />
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

