import React, { useState, useEffect, useRef } from 'react';
import { Mail, Phone, Lock, Sparkles, ArrowRight, CheckCircle2, AlertCircle, RefreshCw, Smartphone, Download, Globe } from 'lucide-react';
import { api } from '../services/api';

interface LoginScreenProps {
  onLoginSuccess: () => void;
  onOpenDownloadModal: () => void;
}

export const LoginScreen: React.FC<LoginScreenProps> = ({ onLoginSuccess, onOpenDownloadModal }) => {
  const [method, setMethod] = useState<'options' | 'email' | 'phone' | 'otp'>('options');
  const [email, setEmail] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [countryCode, setCountryCode] = useState('+213');
  const [otpDigits, setOtpDigits] = useState(['', '', '', '', '', '']);
  const [targetDestination, setTargetDestination] = useState('');
  const [countdown, setCountdown] = useState(45);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [successNotice, setSuccessNotice] = useState('');

  const otpInputsRef = useRef<(HTMLInputElement | null)[]>([]);

  // Timer countdown
  useEffect(() => {
    let timer: NodeJS.Timeout;
    if (method === 'otp' && countdown > 0) {
      timer = setInterval(() => {
        setCountdown((prev) => prev - 1);
      }, 1000);
    }
    return () => clearInterval(timer);
  }, [method, countdown]);

  const handleSendOtp = async (dest: string, type: 'email' | 'phone') => {
    if (!dest) {
      setErrorMsg(type === 'email' ? 'يرجى إدخال البريد الإلكتروني' : 'يرجى إدخال رقم الهاتف');
      return;
    }
    setErrorMsg('');
    setIsLoading(true);

    try {
      const res = await api.sendOtp(dest, type);
      setIsLoading(false);
      if (res.success) {
        setTargetDestination(dest);
        setMethod('otp');
        setCountdown(45);
        setSuccessNotice(`تم إرسال رمز التحقق بنجاح! الرمز التجريبي هو: ${res.code}`);
        // Focus first OTP field
        setTimeout(() => {
          otpInputsRef.current[0]?.focus();
        }, 100);
      } else {
        setErrorMsg('حدث خطأ أثناء إرسال الرمز');
      }
    } catch {
      setIsLoading(false);
      setErrorMsg('تعذر الاتصال بالخادم');
    }
  };

  const handleOtpChange = (index: number, value: string) => {
    if (value.length > 1) {
      // User pasted multiple characters
      const pasted = value.replace(/\D/g, '').slice(0, 6);
      if (pasted) {
        const newDigits = [...otpDigits];
        for (let i = 0; i < 6; i++) {
          newDigits[i] = pasted[i] || '';
        }
        setOtpDigits(newDigits);
        if (pasted.length === 6) {
          verifyOtpCode(newDigits.join(''));
        }
      }
      return;
    }

    const digit = value.replace(/\D/g, '');
    const newDigits = [...otpDigits];
    newDigits[index] = digit;
    setOtpDigits(newDigits);

    // Auto-advance
    if (digit && index < 5) {
      otpInputsRef.current[index + 1]?.focus();
    }

    // Auto verify if all 6 filled
    if (digit && index === 5 && newDigits.every(d => d.length === 1)) {
      verifyOtpCode(newDigits.join(''));
    }
  };

  const handleKeyDown = (index: number, e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace' && !otpDigits[index] && index > 0) {
      otpInputsRef.current[index - 1]?.focus();
    }
  };

  const verifyOtpCode = async (codeToVerify?: string) => {
    const code = codeToVerify || otpDigits.join('');
    if (code.length < 6) {
      setErrorMsg('يرجى إدخال الرمز المكون من 6 أرقام');
      return;
    }
    setErrorMsg('');
    setIsLoading(true);

    const res = await api.verifyOtp(targetDestination, code);
    setIsLoading(false);

    if (res.success) {
      setSuccessNotice('تم التحقق بنجاح! مرحباً بك.');
      setTimeout(() => {
        onLoginSuccess();
      }, 700);
    } else {
      setErrorMsg(res.message || 'رمز التحقق غير صحيح، يرجى المحاولة ثانية');
    }
  };

  const fillAutoOtp = () => {
    const code = '180782';
    setOtpDigits(['1', '8', '0', '7', '8', '2']);
    setErrorMsg('');
    verifyOtpCode(code);
  };

  const handleSocialLogin = (provider: string) => {
    setIsLoading(true);
    setSuccessNotice(`جاري المصادقة عبر ${provider}...`);
    setTimeout(() => {
      setIsLoading(false);
      api.setAuthStatus(true);
      onLoginSuccess();
    }, 1200);
  };

  return (
    <div className="relative min-h-screen bg-black text-white flex flex-col justify-between overflow-x-hidden" dir="rtl">
      {/* Background visual accents */}
      <div className="absolute inset-0 pointer-events-none overflow-hidden opacity-30">
        <div className="absolute -top-32 -right-32 w-96 h-96 bg-pink-600/30 rounded-full blur-3xl"></div>
        <div className="absolute -bottom-32 -left-32 w-96 h-96 bg-cyan-600/30 rounded-full blur-3xl"></div>
      </div>

      {/* Top Bar with APK download banner */}
      <div className="relative z-10 w-full px-4 pt-4 pb-2 flex items-center justify-between border-b border-zinc-800/80 bg-zinc-950/70 backdrop-blur-md">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-cyan-500 to-pink-500 flex items-center justify-center font-black text-white text-base">
            C
          </div>
          <span className="font-extrabold text-lg tracking-tight bg-gradient-to-r from-white via-zinc-200 to-zinc-400 bg-clip-text text-transparent">
            ZEVORA <span className="text-pink-500 text-xs font-medium">Short Videos</span>
          </span>
        </div>

        {/* Prominent APK Download Button */}
        <button
          onClick={onOpenDownloadModal}
          className="flex items-center gap-2 py-1.5 px-3 rounded-full text-xs font-semibold bg-gradient-to-r from-pink-600 to-cyan-600 hover:from-pink-500 hover:to-cyan-500 text-white shadow-md shadow-pink-600/20 active:scale-95 transition-all"
        >
          <Download className="w-3.5 h-3.5 animate-bounce" />
          <span>تحميل APK (v3.0.0)</span>
        </button>
      </div>

      {/* Main Login Content Card */}
      <div className="relative z-10 flex-1 flex flex-col justify-center items-center px-4 py-6 max-w-md mx-auto w-full">
        {/* App Title & Subtitle */}
        <div className="text-center mb-7 space-y-2">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-zinc-800/70 border border-zinc-700/60 text-xs text-zinc-300 mb-1">
            <Sparkles className="w-3.5 h-3.5 text-pink-400" />
            <span>تسجيل الدخول إلى حسابك</span>
          </div>
          <h1 className="text-2xl font-black text-white">
            {method === 'otp' ? 'أدخل رمز التحقق (OTP)' : 'انضم إلى مجتمع ZEVORA'}
          </h1>
          <p className="text-xs text-zinc-400 max-w-xs mx-auto">
            {method === 'otp'
              ? `أرسلنا رمزاً مكوناً من 6 أرقام إلى ${targetDestination}`
              : 'شاهد ملايين الفيديوهات، أنشئ محتواك وتفاعل مع المبدعين'}
          </p>
        </div>

        {/* Error or Success banners */}
        {errorMsg && (
          <div className="w-full mb-4 p-3 bg-red-950/60 border border-red-800/80 rounded-xl flex items-center gap-2 text-xs text-red-300 animate-in fade-in">
            <AlertCircle className="w-4 h-4 shrink-0 text-red-400" />
            <span>{errorMsg}</span>
          </div>
        )}
        {successNotice && (
          <div className="w-full mb-4 p-3 bg-emerald-950/60 border border-emerald-800/80 rounded-xl flex items-center gap-2 text-xs text-emerald-300 animate-in fade-in">
            <CheckCircle2 className="w-4 h-4 shrink-0 text-emerald-400" />
            <span>{successNotice}</span>
          </div>
        )}

        {/* View 1: Main Provider Options */}
        {method === 'options' && (
          <div className="w-full space-y-3">
            {/* Google / Gmail */}
            <button
              onClick={() => handleSocialLogin('Google')}
              disabled={isLoading}
              className="w-full flex items-center justify-center gap-3 py-3.5 px-4 rounded-xl font-semibold bg-white text-zinc-900 hover:bg-zinc-100 active:scale-[0.99] transition-all shadow-md text-sm"
            >
              <svg className="w-5 h-5" viewBox="0 0 24 24">
                <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
                <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
                <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z" />
                <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z" />
              </svg>
              <span>المتابعة باستخدام Google (Gmail)</span>
            </button>

            {/* Facebook */}
            <button
              onClick={() => handleSocialLogin('Facebook')}
              disabled={isLoading}
              className="w-full flex items-center justify-center gap-3 py-3.5 px-4 rounded-xl font-semibold bg-[#1877F2] text-white hover:bg-[#166fe5] active:scale-[0.99] transition-all shadow-md text-sm"
            >
              <svg className="w-5 h-5 fill-current" viewBox="0 0 24 24">
                <path d="M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z" />
              </svg>
              <span>المتابعة باستخدام فيسبوك Facebook</span>
            </button>

            {/* Divider */}
            <div className="relative py-2 flex items-center justify-center">
              <div className="w-full border-t border-zinc-800"></div>
              <span className="absolute bg-black px-3 text-xs text-zinc-500 font-medium">أو عبر رمز التحقق</span>
            </div>

            {/* Email OTP Option */}
            <button
              onClick={() => {
                setMethod('email');
                setErrorMsg('');
              }}
              className="w-full flex items-center justify-between py-3.5 px-4 rounded-xl font-medium bg-zinc-900 border border-zinc-800 hover:border-zinc-700 hover:bg-zinc-800/80 active:scale-[0.99] transition-all text-sm text-zinc-200"
            >
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-lg bg-pink-500/10 flex items-center justify-center text-pink-400">
                  <Mail className="w-4 h-4" />
                </div>
                <div className="text-right">
                  <div className="font-semibold text-white">البريد الإلكتروني (Gmail)</div>
                  <div className="text-[11px] text-zinc-400">استلام كود OTP فوري على البريد</div>
                </div>
              </div>
              <ArrowRight className="w-4 h-4 text-zinc-400 rotate-180" />
            </button>

            {/* Phone OTP Option */}
            <button
              onClick={() => {
                setMethod('phone');
                setErrorMsg('');
              }}
              className="w-full flex items-center justify-between py-3.5 px-4 rounded-xl font-medium bg-zinc-900 border border-zinc-800 hover:border-zinc-700 hover:bg-zinc-800/80 active:scale-[0.99] transition-all text-sm text-zinc-200"
            >
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-lg bg-cyan-500/10 flex items-center justify-center text-cyan-400">
                  <Phone className="w-4 h-4" />
                </div>
                <div className="text-right">
                  <div className="font-semibold text-white">رقم الهاتف (SMS)</div>
                  <div className="text-[11px] text-zinc-400">استلام كود OTP فوري برسالة نصية</div>
                </div>
              </div>
              <ArrowRight className="w-4 h-4 text-zinc-400 rotate-180" />
            </button>
          </div>
        )}

        {/* View 2: Email Input */}
        {method === 'email' && (
          <div className="w-full space-y-4">
            <div className="space-y-1.5 text-right">
              <label className="text-xs font-semibold text-zinc-300">أدخل عنوان بريدك الإلكتروني (Gmail):</label>
              <div className="relative">
                <input
                  type="email"
                  dir="ltr"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="name@gmail.com"
                  className="w-full bg-zinc-900 border border-zinc-700 rounded-xl px-4 py-3.5 text-sm text-white placeholder-zinc-500 focus:outline-none focus:border-pink-500 transition-colors text-left"
                />
                <Mail className="absolute right-3.5 top-3.5 w-5 h-5 text-zinc-500 pointer-events-none" />
              </div>
            </div>

            <button
              onClick={() => handleSendOtp(email, 'email')}
              disabled={isLoading || !email}
              className="w-full py-3.5 px-4 rounded-xl font-bold bg-gradient-to-r from-pink-600 to-cyan-600 hover:from-pink-500 hover:to-cyan-500 text-white shadow-lg shadow-pink-600/20 active:scale-[0.98] transition-all disabled:opacity-50 text-sm"
            >
              {isLoading ? 'جاري الإرسال...' : 'إرسال كود التحقق (OTP)'}
            </button>

            <button
              onClick={() => setMethod('options')}
              className="w-full py-2.5 text-xs text-zinc-400 hover:text-white transition-colors text-center"
            >
              الرجوع لخيارات الدخول
            </button>
          </div>
        )}

        {/* View 3: Phone Input */}
        {method === 'phone' && (
          <div className="w-full space-y-4">
            <div className="space-y-1.5 text-right">
              <label className="text-xs font-semibold text-zinc-300">أدخل رقم هاتفك لاستلام رسالة SMS:</label>
              <div className="flex gap-2" dir="ltr">
                <select
                  value={countryCode}
                  onChange={(e) => setCountryCode(e.target.value)}
                  className="bg-zinc-900 border border-zinc-700 rounded-xl px-3 py-3.5 text-xs text-white focus:outline-none focus:border-cyan-500 shrink-0"
                >
                  <option value="+213">🇩🇿 +213 الجزائر</option>
                  <option value="+33">🇫🇷 +33 فرنسا</option>
                  <option value="+212">🇲🇦 +212 المغرب</option>
                  <option value="+216">🇹🇳 +216 تونس</option>
                  <option value="+966">🇸🇦 +966 السعودية</option>
                  <option value="+971">🇦🇪 +971 الإمارات</option>
                  <option value="+1">🇺🇸 +1 أمريكا</option>
                </select>
                <input
                  type="tel"
                  value={phoneNumber}
                  onChange={(e) => setPhoneNumber(e.target.value)}
                  placeholder="0661 23 45 67"
                  className="flex-1 bg-zinc-900 border border-zinc-700 rounded-xl px-4 py-3.5 text-sm text-white placeholder-zinc-500 focus:outline-none focus:border-cyan-500 transition-colors"
                />
              </div>
            </div>

            <button
              onClick={() => handleSendOtp(`${countryCode} ${phoneNumber}`, 'phone')}
              disabled={isLoading || !phoneNumber}
              className="w-full py-3.5 px-4 rounded-xl font-bold bg-gradient-to-r from-cyan-600 to-pink-600 hover:from-cyan-500 hover:to-pink-500 text-white shadow-lg shadow-cyan-600/20 active:scale-[0.98] transition-all disabled:opacity-50 text-sm"
            >
              {isLoading ? 'جاري الإرسال...' : 'إرسال كود التحقق (OTP) SMS'}
            </button>

            <button
              onClick={() => setMethod('options')}
              className="w-full py-2.5 text-xs text-zinc-400 hover:text-white transition-colors text-center"
            >
              الرجوع لخيارات الدخول
            </button>
          </div>
        )}

        {/* View 4: 6-Digit OTP Screen matching screenshot */}
        {method === 'otp' && (
          <div className="w-full space-y-6">
            {/* 6 Digit Input boxes */}
            <div className="flex justify-center gap-2.5" dir="ltr">
              {otpDigits.map((digit, index) => (
                <input
                  key={index}
                  ref={(el) => {
                    otpInputsRef.current[index] = el;
                  }}
                  type="text"
                  inputMode="numeric"
                  maxLength={1}
                  value={digit}
                  onChange={(e) => handleOtpChange(index, e.target.value)}
                  onKeyDown={(e) => handleKeyDown(index, e)}
                  className="w-11 h-14 bg-zinc-900 border-2 border-zinc-700 focus:border-pink-500 focus:bg-zinc-800 rounded-xl text-center text-xl font-bold text-white transition-all outline-none shadow-inner"
                />
              ))}
            </div>

            {/* Demo Auto-fill Helper */}
            <div className="flex flex-col items-center gap-2">
              <button
                onClick={fillAutoOtp}
                type="button"
                className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-semibold bg-zinc-800/90 hover:bg-zinc-700 text-cyan-300 border border-zinc-700 active:scale-95 transition-all"
              >
                <Sparkles className="w-3.5 h-3.5 text-yellow-400" />
                <span>ملء الرمز تلقائياً (180782) للاختبار الفوري</span>
              </button>
            </div>

            {/* Countdown / Resend */}
            <div className="text-center text-xs text-zinc-400">
              {countdown > 0 ? (
                <span>إعادة إرسال الرمز خلال ({countdown}ث)</span>
              ) : (
                <button
                  onClick={() => handleSendOtp(targetDestination, targetDestination.includes('@') ? 'email' : 'phone')}
                  className="text-pink-400 hover:text-pink-300 font-semibold underline inline-flex items-center gap-1"
                >
                  <RefreshCw className="w-3 h-3" /> إعادة إرسال رمز التحقق الآن
                </button>
              )}
            </div>

            {/* Verify CTA */}
            <button
              onClick={() => verifyOtpCode()}
              disabled={isLoading || otpDigits.some((d) => !d)}
              className="w-full py-3.5 px-4 rounded-xl font-bold bg-gradient-to-r from-pink-600 via-rose-600 to-cyan-600 hover:from-pink-500 hover:to-cyan-500 text-white shadow-xl shadow-pink-600/25 active:scale-[0.98] transition-all disabled:opacity-50 text-sm"
            >
              {isLoading ? 'جاري التحقق...' : 'تأكيد الرمز والدخول إلى الحساب'}
            </button>

            <button
              onClick={() => setMethod('options')}
              className="w-full py-2 text-xs text-zinc-400 hover:text-white transition-colors text-center"
            >
              استخدام طريقة أخرى لتسجيل الدخول
            </button>
          </div>
        )}
      </div>

      {/* Bottom Footer & APK Quick Link */}
      <div className="relative z-10 w-full p-4 border-t border-zinc-800/80 bg-zinc-950/80 text-center space-y-2">
        <div className="flex items-center justify-center gap-3 text-xs text-zinc-400">
          <span>شروط الاستخدام</span>
          <span>·</span>
          <span>سياسة الخصوصية</span>
          <span>·</span>
          <button onClick={onOpenDownloadModal} className="text-pink-400 hover:underline font-medium">
            تحميل أحدث إصدار APK v3.0.0
          </button>
        </div>
        <p className="text-[11px] text-zinc-600">
          ZEVORA Short Videos © 2026 - جميع الحقوق محفوظة
        </p>
      </div>
    </div>
  );
};
