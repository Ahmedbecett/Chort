import React, { useState, useRef, useEffect } from 'react';
import { 
  X, Music2, RotateCcw, Zap, Sparkles, Clock, SlidersHorizontal, 
  Circle, Image as ImageIcon, Volume2, Check, Play, Pause, ChevronRight 
} from 'lucide-react';
import { AUDIO_TRACKS } from '../data/mockData';
import { AudioTrack } from '../types';

interface CameraStudioProps {
  isOpen: boolean;
  onClose: () => void;
  onOpenUpload: (preselectedVideoUrl?: string) => void;
}

export const CameraStudio: React.FC<CameraStudioProps> = ({ isOpen, onClose, onOpenUpload }) => {
  const [selectedDuration, setSelectedDuration] = useState<'10m' | '60s' | '15s' | 'PHOTO' | 'TEXT'>('15s');
  const [cameraMode, setCameraMode] = useState<'LIVE' | 'POST' | 'CREATE'>('POST');
  const [speed, setSpeed] = useState<'0.3x' | '0.5x' | '1x' | '2x' | '3x'>('1x');
  const [timerMode, setTimerMode] = useState<0 | 3 | 10>(0);
  const [isBeautified, setIsBeautified] = useState(false);
  const [activeFilter, setActiveFilter] = useState<string>('normal');
  const [isRecording, setIsRecording] = useState(false);
  const [recordProgress, setRecordProgress] = useState(0);
  const [countdown, setCountdown] = useState<number | null>(null);
  
  // Audio selection
  const [isSoundModalOpen, setIsSoundModalOpen] = useState(false);
  const [selectedAudio, setSelectedAudio] = useState<AudioTrack | null>(AUDIO_TRACKS[0]);
  const [playingAudioId, setPlayingAudioId] = useState<string | null>(null);

  // Filters list
  const FILTERS = [
    { id: 'normal', name: 'عادي', css: 'none' },
    { id: 'vintage', name: 'Vintage 70s', css: 'sepia(0.5) contrast(1.1) saturate(1.2)' },
    { id: 'cyber', name: 'Cyber Neon', css: 'hue-rotate(90deg) contrast(1.3) saturate(1.8)' },
    { id: 'noir', name: 'Noir B&W', css: 'grayscale(1) contrast(1.4)' },
    { id: 'sunset', name: 'Warm Sunset', css: 'saturate(1.5) sepia(0.3) brightness(1.05)' },
    { id: 'cinema', name: 'Cinema 4K', css: 'contrast(1.2) brightness(0.95) saturate(1.1)' },
  ];

  const videoRef = useRef<HTMLVideoElement | null>(null);
  const [cameraStream, setCameraStream] = useState<MediaStream | null>(null);
  const [cameraError, setCameraError] = useState(false);

  // Initialize webcam
  useEffect(() => {
    if (isOpen) {
      navigator.mediaDevices?.getUserMedia({ video: { facingMode: 'user' }, audio: true })
        .then((stream) => {
          setCameraStream(stream);
          if (videoRef.current) {
            videoRef.current.srcObject = stream;
          }
          setCameraError(false);
        })
        .catch(() => {
          setCameraError(true);
        });
    } else {
      if (cameraStream) {
        cameraStream.getTracks().forEach((t) => t.stop());
        setCameraStream(null);
      }
    }
    return () => {
      if (cameraStream) {
        cameraStream.getTracks().forEach((t) => t.stop());
      }
    };
  }, [isOpen]);

  // Recording timer simulation
  useEffect(() => {
    let interval: NodeJS.Timeout;
    if (isRecording) {
      interval = setInterval(() => {
        setRecordProgress((prev) => {
          if (prev >= 100) {
            setIsRecording(false);
            onOpenUpload('https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4');
            return 0;
          }
          return prev + 2;
        });
      }, 300);
    }
    return () => clearInterval(interval);
  }, [isRecording]);

  if (!isOpen) return null;

  const currentFilterStyle = FILTERS.find((f) => f.id === activeFilter)?.css || 'none';

  const handleRecordToggle = () => {
    if (isRecording) {
      setIsRecording(false);
      setRecordProgress(0);
      onOpenUpload('https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4');
      return;
    }

    if (timerMode > 0) {
      setCountdown(timerMode);
      const timer = setInterval(() => {
        setCountdown((c) => {
          if (c && c > 1) return c - 1;
          clearInterval(timer);
          setCountdown(null);
          setIsRecording(true);
          return null;
        });
      }, 1000);
    } else {
      setIsRecording(true);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-black flex flex-col justify-between overflow-hidden" dir="rtl">
      {/* Camera Preview Area */}
      <div className="relative flex-1 w-full h-full overflow-hidden bg-zinc-950 flex items-center justify-center">
        {/* Live Video or Cinematic Fallback */}
        {!cameraError ? (
          <video
            ref={videoRef}
            autoPlay
            playsInline
            muted
            className="w-full h-full object-cover transform -scale-x-100 transition-all duration-300"
            style={{
              filter: `${currentFilterStyle} ${isBeautified ? 'blur(0.4px) brightness(1.08)' : ''}`,
            }}
          />
        ) : (
          <div 
            className="relative w-full h-full bg-cover bg-center flex items-center justify-center transition-all duration-300"
            style={{
              backgroundImage: 'url(https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1000&auto=format&fit=crop&q=80)',
              filter: `${currentFilterStyle} ${isBeautified ? 'blur(0.4px) brightness(1.08)' : ''}`,
            }}
          >
            <div className="absolute inset-0 bg-black/40 backdrop-blur-[2px]"></div>
            <div className="relative z-10 text-center px-4 space-y-2">
              <div className="w-16 h-16 mx-auto rounded-full bg-pink-500/20 border border-pink-500/40 flex items-center justify-center">
                <Sparkles className="w-8 h-8 text-pink-400 animate-pulse" />
              </div>
              <p className="text-sm font-bold text-white">استوديو الكاميرا الذكي جاهز</p>
              <p className="text-xs text-zinc-300">يمكنك تسجيل مقطع جديد أو تطبيق الفلاتر ونشره فوراً</p>
            </div>
          </div>
        )}

        {/* Countdown overlay */}
        {countdown !== null && (
          <div className="absolute inset-0 z-40 bg-black/60 flex items-center justify-center">
            <span className="text-9xl font-black text-pink-500 animate-ping">{countdown}</span>
          </div>
        )}

        {/* Top Header Controls */}
        <div className="absolute top-0 inset-x-0 z-20 p-4 flex items-center justify-between bg-gradient-to-b from-black/80 via-black/30 to-transparent">
          <button 
            onClick={onClose}
            className="w-9 h-9 rounded-full bg-black/40 backdrop-blur-md text-white flex items-center justify-center hover:bg-black/60 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>

          {/* Add Sound Button */}
          <button
            onClick={() => setIsSoundModalOpen(true)}
            className="flex items-center gap-2 py-1.5 px-4 rounded-full bg-black/50 backdrop-blur-md border border-white/20 text-white text-xs font-semibold hover:bg-black/70 transition-all"
          >
            <Music2 className="w-3.5 h-3.5 text-pink-400" />
            <span className="max-w-[130px] truncate">{selectedAudio ? selectedAudio.title : 'إضافة صوت'}</span>
          </button>

          <button
            onClick={() => alert('تم تبديل الفلاش')}
            className="w-9 h-9 rounded-full bg-black/40 backdrop-blur-md text-white flex items-center justify-center hover:bg-black/60 transition-colors"
          >
            <Zap className="w-4 h-4" />
          </button>
        </div>

        {/* Right Tools Sidebar (TikTok style) */}
        <div className="absolute top-20 right-4 z-20 flex flex-col items-center gap-4 text-white">
          {/* Flip */}
          <button 
            onClick={() => alert('تم قلب الكاميرا (أمامية / خلفية)')}
            className="flex flex-col items-center gap-1 group"
          >
            <div className="w-10 h-10 rounded-full bg-black/40 backdrop-blur-md flex items-center justify-center group-hover:bg-black/60 transition-colors">
              <RotateCcw className="w-5 h-5" />
            </div>
            <span className="text-[10px] font-medium drop-shadow">قلب</span>
          </button>

          {/* Speed */}
          <button 
            onClick={() => {
              const speeds: ('0.3x' | '0.5x' | '1x' | '2x' | '3x')[] = ['0.3x', '0.5x', '1x', '2x', '3x'];
              const next = speeds[(speeds.indexOf(speed) + 1) % speeds.length];
              setSpeed(next);
            }}
            className="flex flex-col items-center gap-1 group"
          >
            <div className="w-10 h-10 rounded-full bg-black/40 backdrop-blur-md flex items-center justify-center font-bold text-xs group-hover:bg-black/60 transition-colors">
              {speed}
            </div>
            <span className="text-[10px] font-medium drop-shadow">السرعة</span>
          </button>

          {/* Beauty */}
          <button 
            onClick={() => setIsBeautified(!isBeautified)}
            className="flex flex-col items-center gap-1 group"
          >
            <div className={`w-10 h-10 rounded-full backdrop-blur-md flex items-center justify-center transition-colors ${
              isBeautified ? 'bg-pink-600 text-white' : 'bg-black/40 text-white group-hover:bg-black/60'
            }`}>
              <Sparkles className="w-5 h-5" />
            </div>
            <span className="text-[10px] font-medium drop-shadow">تجميل</span>
          </button>

          {/* Timer */}
          <button 
            onClick={() => setTimerMode(timerMode === 0 ? 3 : timerMode === 3 ? 10 : 0)}
            className="flex flex-col items-center gap-1 group"
          >
            <div className={`w-10 h-10 rounded-full backdrop-blur-md flex items-center justify-center font-bold text-xs transition-colors ${
              timerMode > 0 ? 'bg-cyan-500 text-black' : 'bg-black/40 text-white group-hover:bg-black/60'
            }`}>
              {timerMode > 0 ? `${timerMode}s` : <Clock className="w-5 h-5" />}
            </div>
            <span className="text-[10px] font-medium drop-shadow">مؤقت</span>
          </button>

          {/* Filters */}
          <div className="flex flex-col items-center gap-1">
            <div className="w-10 h-10 rounded-full bg-black/40 backdrop-blur-md flex items-center justify-center text-white">
              <SlidersHorizontal className="w-5 h-5" />
            </div>
            <span className="text-[10px] font-medium drop-shadow">فلاتر</span>
          </div>
        </div>

        {/* Live Filters Horizontal Strip */}
        <div className="absolute bottom-28 inset-x-0 z-20 px-4 overflow-x-auto no-scrollbar flex items-center gap-2 justify-center">
          {FILTERS.map((f) => (
            <button
              key={f.id}
              onClick={() => setActiveFilter(f.id)}
              className={`py-1.5 px-3 rounded-full text-xs font-semibold backdrop-blur-md shrink-0 transition-all ${
                activeFilter === f.id
                  ? 'bg-gradient-to-r from-pink-600 to-cyan-500 text-white shadow-lg scale-105'
                  : 'bg-black/50 text-zinc-300 hover:bg-black/70'
              }`}
            >
              {f.name}
            </button>
          ))}
        </div>

        {/* Recording Progress Ring */}
        {isRecording && (
          <div className="absolute top-16 inset-x-4 z-20 h-1.5 bg-zinc-800 rounded-full overflow-hidden">
            <div 
              style={{ width: `${recordProgress}%` }}
              className="h-full bg-gradient-to-r from-pink-500 to-red-500 transition-all duration-200"
            />
          </div>
        )}
      </div>

      {/* Bottom Bar: Duration, Shutter Button, Modes */}
      <div className="relative z-20 bg-black/90 backdrop-blur-md px-4 pt-3 pb-6 space-y-4">
        {/* Durations */}
        <div className="flex justify-center gap-5 text-xs font-semibold text-zinc-400">
          {(['10m', '60s', '15s', 'PHOTO', 'TEXT'] as const).map((dur) => (
            <button
              key={dur}
              onClick={() => setSelectedDuration(dur)}
              className={`transition-colors ${selectedDuration === dur ? 'text-white font-bold scale-110' : 'hover:text-zinc-200'}`}
            >
              {dur}
            </button>
          ))}
        </div>

        {/* Shutter & Gallery Controls */}
        <div className="flex items-center justify-around px-6">
          {/* Left spacer / placeholder */}
          <div className="w-12 h-12"></div>

          {/* Center Record Button */}
          <div className="relative flex items-center justify-center">
            <button
              onClick={handleRecordToggle}
              className={`w-20 h-20 rounded-full p-1.5 border-4 transition-all duration-200 flex items-center justify-center ${
                isRecording 
                  ? 'border-red-500 scale-95' 
                  : 'border-white hover:border-pink-500 active:scale-95'
              }`}
            >
              <div 
                className={`w-full h-full rounded-full transition-all duration-200 ${
                  isRecording 
                    ? 'bg-red-600 rounded-md scale-50' 
                    : 'bg-gradient-to-tr from-pink-600 via-rose-500 to-red-600'
                }`}
              />
            </button>
          </div>

          {/* Right: Gallery Picker */}
          <button
            onClick={() => onOpenUpload()}
            className="flex flex-col items-center gap-1 group"
          >
            <div className="w-12 h-12 rounded-xl bg-zinc-800 border border-zinc-700 flex items-center justify-center text-white group-hover:border-pink-500 transition-colors overflow-hidden">
              <ImageIcon className="w-6 h-6 text-zinc-300" />
            </div>
            <span className="text-[10px] text-zinc-300 font-medium">تحميل</span>
          </button>
        </div>

        {/* Bottom Modes: LIVE | POST | CREATE */}
        <div className="flex justify-center gap-8 text-xs font-bold pt-2 border-t border-zinc-900">
          {(['LIVE', 'POST', 'CREATE'] as const).map((m) => (
            <button
              key={m}
              onClick={() => setCameraMode(m)}
              className={`transition-colors ${cameraMode === m ? 'text-white' : 'text-zinc-600 hover:text-zinc-400'}`}
            >
              {m}
            </button>
          ))}
        </div>
      </div>

      {/* Audio Track Selector Modal */}
      {isSoundModalOpen && (
        <div className="fixed inset-0 z-50 bg-black/90 backdrop-blur-md flex flex-col justify-end p-0 sm:p-4">
          <div className="bg-zinc-900 border border-zinc-800 rounded-t-3xl sm:rounded-2xl max-h-[80vh] overflow-y-auto p-4 space-y-4">
            <div className="flex items-center justify-between pb-2 border-b border-zinc-800">
              <div className="flex items-center gap-2">
                <Music2 className="w-5 h-5 text-pink-400" />
                <h4 className="font-bold text-white text-base">مكتبة الأصوات والموسيقى</h4>
              </div>
              <button 
                onClick={() => setIsSoundModalOpen(false)}
                className="p-1 rounded-full text-zinc-400 hover:text-white bg-zinc-800"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-2">
              {AUDIO_TRACKS.map((track) => (
                <div
                  key={track.id}
                  className="p-3 rounded-xl bg-zinc-950 border border-zinc-800 flex items-center justify-between gap-3 hover:border-pink-500/40 transition-colors"
                >
                  <div className="flex items-center gap-3">
                    <img src={track.cover} alt="" className="w-12 h-12 rounded-lg object-cover" />
                    <div>
                      <div className="font-semibold text-xs text-white">{track.title}</div>
                      <div className="text-[11px] text-zinc-400">{track.artist}</div>
                      <div className="text-[10px] text-zinc-500">{track.usesCount}</div>
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => setPlayingAudioId(playingAudioId === track.id ? null : track.id)}
                      className="w-8 h-8 rounded-full bg-zinc-800 flex items-center justify-center text-white"
                    >
                      {playingAudioId === track.id ? <Pause className="w-4 h-4 text-pink-400" /> : <Play className="w-4 h-4" />}
                    </button>
                    <button
                      onClick={() => {
                        setSelectedAudio(track);
                        setIsSoundModalOpen(false);
                      }}
                      className="py-1 px-3 rounded-full bg-pink-600 hover:bg-pink-500 text-white text-xs font-semibold"
                    >
                      استخدام
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
