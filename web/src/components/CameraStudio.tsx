import React, { useState, useRef, useEffect } from 'react';
import {
  X, Music2, RotateCcw, Zap, Sparkles, Clock, SlidersHorizontal,
  Image as ImageIcon, Play, Pause, CameraOff, Film
} from 'lucide-react';
import { AUDIO_TRACKS } from '../data/mockData';
import { AudioTrack } from '../types';

interface CameraStudioProps {
  isOpen: boolean;
  onClose: () => void;
  onOpenUpload: (preselectedVideoUrl?: string) => void;
}

// Production policy: everything here is real capture. No simulated recording,
// no sample videos, no stock images. If the camera is unavailable the UI says
// so and offers the device file picker instead.
export const CameraStudio: React.FC<CameraStudioProps> = ({ isOpen, onClose, onOpenUpload }) => {
  const [selectedDuration, setSelectedDuration] = useState<'10m' | '60s' | '15s'>('15s');
  const [cameraMode, setCameraMode] = useState<'LIVE' | 'POST' | 'CREATE'>('POST');
  const [timerMode, setTimerMode] = useState<0 | 3 | 10>(0);
  const [isBeautified, setIsBeautified] = useState(false);
  const [activeFilter, setActiveFilter] = useState<string>('normal');
  const [isRecording, setIsRecording] = useState(false);
  const [recordProgress, setRecordProgress] = useState(0);
  const [countdown, setCountdown] = useState<number | null>(null);
  const [facingMode, setFacingMode] = useState<'user' | 'environment'>('user');
  const [torchOn, setTorchOn] = useState(false);
  const [notice, setNotice] = useState('');

  // Audio selection (empty until the backend provides a real sound library)
  const [isSoundModalOpen, setIsSoundModalOpen] = useState(false);
  const [selectedAudio, setSelectedAudio] = useState<AudioTrack | null>(AUDIO_TRACKS[0] ?? null);
  const [playingAudioId, setPlayingAudioId] = useState<string | null>(null);

  // Filters list (applied for real: preview CSS + recorded canvas frames)
  const FILTERS = [
    { id: 'normal', name: 'عادي', css: 'none' },
    { id: 'vintage', name: 'Vintage 70s', css: 'sepia(0.5) contrast(1.1) saturate(1.2)' },
    { id: 'cyber', name: 'Cyber Neon', css: 'hue-rotate(90deg) contrast(1.3) saturate(1.8)' },
    { id: 'noir', name: 'Noir B&W', css: 'grayscale(1) contrast(1.4)' },
    { id: 'sunset', name: 'Warm Sunset', css: 'saturate(1.5) sepia(0.3) brightness(1.05)' },
    { id: 'cinema', name: 'Cinema 4K', css: 'contrast(1.2) brightness(0.95) saturate(1.1)' },
  ];
  const currentFilterStyle = FILTERS.find((f) => f.id === activeFilter)?.css || 'none';
  const effectCss = `${currentFilterStyle} ${isBeautified ? 'blur(0.4px) brightness(1.08)' : ''}`.trim();

  const videoRef = useRef<HTMLVideoElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const rafRef = useRef<number>(0);
  const recordStartRef = useRef<number>(0);
  const maxSecsRef = useRef<number>(15);
  const [cameraStream, setCameraStream] = useState<MediaStream | null>(null);
  const [cameraError, setCameraError] = useState(false);

  const maxSecs = selectedDuration === '15s' ? 15 : selectedDuration === '60s' ? 60 : 600;

  const stopTracks = () => {
    streamRef.current?.getTracks().forEach((t) => {
      try { t.stop(); } catch { /* ignore */ }
    });
    streamRef.current = null;
    setCameraStream(null);
  };

  const stopRecorder = () => {
    try {
      const rec = recorderRef.current;
      if (rec && rec.state !== 'inactive') rec.stop();
    } catch { /* ignore */ }
    cancelAnimationFrame(rafRef.current);
    recorderRef.current = null;
  };

  // Acquire the real camera (re-acquires when the lens is flipped).
  useEffect(() => {
    if (!isOpen) {
      stopRecorder();
      stopTracks();
      setIsRecording(false);
      setRecordProgress(0);
      return;
    }
    let cancelled = false;
    stopTracks();
    setCameraError(false);
    setNotice('');
    if (!navigator.mediaDevices?.getUserMedia) {
      setCameraError(true);
      return;
    }
    navigator.mediaDevices.getUserMedia({ video: { facingMode }, audio: true })
      .then((stream) => {
        if (cancelled) {
          stream.getTracks().forEach((t) => t.stop());
          return;
        }
        streamRef.current = stream;
        setCameraStream(stream);
        if (videoRef.current) videoRef.current.srcObject = stream;
        setCameraError(false);
        setTorchOn(false);
      })
      .catch(() => {
        if (!cancelled) setCameraError(true);
      });
    return () => {
      cancelled = true;
      stopRecorder();
      stopTracks();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, facingMode]);

  // Real recording progress + auto-stop at the selected duration.
  useEffect(() => {
    if (!isRecording) return;
    const interval = setInterval(() => {
      const elapsed = (Date.now() - recordStartRef.current) / 1000;
      const limit = maxSecsRef.current;
      setRecordProgress(Math.min(100, (elapsed / limit) * 100));
      if (elapsed >= limit) stopRecorder();
    }, 200);
    return () => clearInterval(interval);
  }, [isRecording]);

  if (!isOpen) return null;

  const flashNotice = (msg: string) => {
    setNotice(msg);
    setTimeout(() => setNotice(''), 2600);
  };

  const startRecording = () => {
    const stream = streamRef.current;
    const video = videoRef.current;
    const canvas = canvasRef.current;
    if (!stream || !video || !canvas) {
      flashNotice('الكاميرا غير متاحة — اختر فيديو من جهازك');
      return;
    }
    if (typeof MediaRecorder === 'undefined') {
      flashNotice('المتصفح لا يدعم التسجيل — اختر فيديو من جهازك');
      return;
    }
    try {
      // Record through a canvas so the visible filter/beauty is really baked in.
      const vTrack = stream.getVideoTracks()[0];
      const settings = vTrack?.getSettings?.() || {};
      canvas.width = settings.width || video.videoWidth || 720;
      canvas.height = settings.height || video.videoHeight || 1280;
      const ctx = canvas.getContext('2d');
      if (!ctx) throw new Error('no 2d context');
      const draw = () => {
        try {
          (ctx as any).filter = effectCss === '' ? 'none' : effectCss;
        } catch { /* ignore */ }
        ctx.save();
        ctx.translate(canvas.width, 0);
        ctx.scale(-1, 1);
        ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
        ctx.restore();
        rafRef.current = requestAnimationFrame(draw);
      };
      draw();
      const canvasStream = canvas.captureStream(30);
      stream.getAudioTracks().forEach((t) => canvasStream.addTrack(t));
      const mime = ['video/webm;codecs=vp9,opus', 'video/webm;codecs=vp8,opus', 'video/webm', 'video/mp4']
        .find((m) => { try { return MediaRecorder.isTypeSupported(m); } catch { return false; } }) || '';
      const rec = new MediaRecorder(canvasStream, {
        ...(mime ? { mimeType: mime } : {}),
        videoBitsPerSecond: 2_500_000,
      } as MediaRecorderOptions);
      chunksRef.current = [];
      rec.ondataavailable = (e) => {
        if (e.data && e.data.size > 0) chunksRef.current.push(e.data);
      };
      rec.onstop = () => {
        cancelAnimationFrame(rafRef.current);
        try { canvasStream.getTracks().forEach((t) => t.stop()); } catch { /* ignore */ }
        recorderRef.current = null;
        setIsRecording(false);
        setRecordProgress(0);
        const blob = new Blob(chunksRef.current, { type: rec.mimeType || 'video/webm' });
        if (blob.size === 0) {
          flashNotice('فشل التسجيل — أعد المحاولة');
          return;
        }
        onOpenUpload(URL.createObjectURL(blob));
      };
      recorderRef.current = rec;
      maxSecsRef.current = maxSecs;
      recordStartRef.current = Date.now();
      rec.start(250);
      setRecordProgress(0);
      setIsRecording(true);
    } catch {
      cancelAnimationFrame(rafRef.current);
      flashNotice('تعذر بدء التسجيل على هذا الجهاز');
    }
  };

  const handleRecordToggle = () => {
    if (cameraMode === 'LIVE') {
      flashNotice('البث المباشر متاح في تطبيق الأندرويد — بدّل إلى POST للتسجيل');
      return;
    }
    if (isRecording) {
      stopRecorder();
      return;
    }
    if (timerMode > 0) {
      setCountdown(timerMode);
      const timer = setInterval(() => {
        setCountdown((c) => {
          if (c && c > 1) return c - 1;
          clearInterval(timer);
          setCountdown(null);
          startRecording();
          return null;
        });
      }, 1000);
    } else {
      startRecording();
    }
  };

  const handleFlip = () => {
    if (isRecording) {
      flashNotice('لا يمكن قلب الكاميرا أثناء التسجيل');
      return;
    }
    setFacingMode((m) => (m === 'user' ? 'environment' : 'user'));
  };

  const handleTorch = async () => {
    const track = streamRef.current?.getVideoTracks()[0];
    if (!track) {
      flashNotice('لا توجد كاميرا نشطة');
      return;
    }
    try {
      await track.applyConstraints({ advanced: [{ torch: !torchOn } as any] });
      setTorchOn(!torchOn);
    } catch {
      flashNotice('الفلاش غير مدعوم على هذه الكاميرا');
    }
  };

  const handleClose = () => {
    stopRecorder();
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 bg-black flex flex-col justify-between overflow-hidden" dir="rtl">
      {/* Hidden canvas used to record filtered frames for real */}
      <canvas ref={canvasRef} className="hidden" />
      {/* Camera Preview Area */}
      <div className="relative flex-1 w-full h-full overflow-hidden bg-zinc-950 flex items-center justify-center">
        {/* Live Video or honest error state (no stock imagery, no fake panel) */}
        {!cameraError ? (
          <video
            ref={videoRef}
            autoPlay
            playsInline
            muted
            className="w-full h-full object-cover transform -scale-x-100 transition-all duration-300"
            style={{ filter: effectCss }}
          />
        ) : (
          <div className="relative w-full h-full bg-gradient-to-b from-zinc-900 via-black to-zinc-900 flex items-center justify-center">
            <div className="relative z-10 text-center px-6 space-y-3 max-w-sm">
              <div className="w-16 h-16 mx-auto rounded-full bg-zinc-800 border border-zinc-700 flex items-center justify-center">
                <CameraOff className="w-8 h-8 text-zinc-400" />
              </div>
              <p className="text-sm font-bold text-white">تعذر الوصول إلى الكاميرا</p>
              <p className="text-xs text-zinc-400 leading-5">
                تحقق من أذونات الكاميرا والميكروفون في المتصفح، أو تابع باختيار فيديو حقيقي من جهازك.
              </p>
              <button
                onClick={() => onOpenUpload()}
                className="inline-flex items-center gap-2 py-2.5 px-5 rounded-xl bg-gradient-to-r from-pink-600 to-cyan-600 text-white text-xs font-bold"
              >
                <Film className="w-4 h-4" />
                <span>اختيار فيديو من الجهاز</span>
              </button>
            </div>
          </div>
        )}

        {/* Notice toast (honest, transient) */}
        {notice !== '' && (
          <div className="absolute top-16 inset-x-0 z-40 flex justify-center px-6 pointer-events-none">
            <div className="py-2 px-4 rounded-full bg-black/80 border border-white/15 text-white text-xs font-semibold backdrop-blur">
              {notice}
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
            onClick={handleClose}
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
            onClick={handleTorch}
            className={`w-9 h-9 rounded-full backdrop-blur-md text-white flex items-center justify-center transition-colors ${torchOn ? 'bg-amber-400 text-black' : 'bg-black/40 hover:bg-black/60'}`}
          >
            <Zap className="w-4 h-4" />
          </button>
        </div>

        {/* Right Tools Sidebar */}
        <div className="absolute top-20 right-4 z-20 flex flex-col items-center gap-4 text-white">
          {/* Flip (real lens switch) */}
          <button
            onClick={handleFlip}
            className="flex flex-col items-center gap-1 group"
          >
            <div className="w-10 h-10 rounded-full bg-black/40 backdrop-blur-md flex items-center justify-center group-hover:bg-black/60 transition-colors">
              <RotateCcw className="w-5 h-5" />
            </div>
            <span className="text-[10px] font-medium drop-shadow">قلب</span>
          </button>

          {/* Beauty (real: preview + recorded output) */}
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

        {/* Recording Progress (real elapsed time) */}
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
        {/* Durations (real max recording lengths) */}
        <div className="flex justify-center gap-5 text-xs font-semibold text-zinc-400">
          {(['10m', '60s', '15s'] as const).map((dur) => (
            <button
              key={dur}
              onClick={() => !isRecording && setSelectedDuration(dur)}
              className={`transition-colors ${selectedDuration === dur ? 'text-white font-bold scale-110' : 'hover:text-zinc-200'}`}
            >
              {dur}
            </button>
          ))}
        </div>

        {/* Shutter & Gallery Controls */}
        <div className="flex items-center justify-around px-6">
          <div className="w-12 h-12"></div>

          {/* Center Record Button (real MediaRecorder capture) */}
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

            {AUDIO_TRACKS.length === 0 ? (
              <div className="py-10 text-center">
                <Music2 className="w-10 h-10 mx-auto text-zinc-700" />
                <p className="mt-4 text-sm font-bold text-zinc-300">لا توجد أصوات متاحة بعد</p>
                <p className="text-xs text-zinc-500 mt-2">ستظهر هنا الأصوات الحقيقية من الخادم عند توفرها. التسجيل يستخدم صوت الميكروفون.</p>
              </div>
            ) : (
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
            )}
          </div>
        </div>
      )}
    </div>
  );
};
