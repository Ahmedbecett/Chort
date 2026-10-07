import React, { useState } from 'react';
import { X, Coins, ArrowUpRight, ArrowDownLeft, ShieldCheck, CheckCircle2, CreditCard } from 'lucide-react';
import { UserProfile } from '../../types';

interface WalletModalProps {
  isOpen: boolean;
  onClose: () => void;
  user: UserProfile;
  onUpdateUser: (updates: Partial<UserProfile>) => void;
}

export const WalletModal: React.FC<WalletModalProps> = ({ isOpen, onClose, user, onUpdateUser }) => {
  const [tab, setTab] = useState<'balance' | 'recharge' | 'withdraw'>('balance');
  const [ripNumber, setRipNumber] = useState('');
  const [withdrawAmount, setWithdrawAmount] = useState('5000');
  const [isSuccess, setIsSuccess] = useState('');

  if (!isOpen) return null;

  // Honest: the web client has no payment gateway, so it must never credit
  // coins or claim a card charge happened. Real top-ups go through the
  // Android app (CIB / EDAHABIA via SATIM).
  const handleRecharge = (_coinsToAdd: number, priceDzd: number) => {
    setIsSuccess(`الشحن غير متاح في نسخة الويب — أكمل الدفع (${priceDzd.toLocaleString()} دج) من تطبيق الأندرويد.`);
    setTimeout(() => {
      setIsSuccess('');
    }, 2600);
  };

  const handleWithdraw = () => {
    const amt = parseInt(withdrawAmount) || 0;
    if (amt <= 0 || amt > user.balanceDzd) {
      alert('المبلغ غير صالح أو يتجاوز رصيدك الحالي');
      return;
    }
    // Honest: no money moves in the web client. Withdrawals are processed
    // from the Android app after identity verification. Balance untouched.
    setIsSuccess(`طلبات السحب تتم من تطبيق الأندرويد بعد التحقق — لم يتم خصم أي مبلغ (${amt.toLocaleString()} دج).`);
    setTimeout(() => {
      setIsSuccess('');
    }, 2600);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/80 backdrop-blur-sm p-0 sm:p-4">
      <div 
        className="bg-zinc-900 border border-zinc-800 rounded-t-3xl sm:rounded-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto shadow-2xl"
        dir="rtl"
      >
        {/* Header */}
        <div className="sticky top-0 bg-zinc-900/95 backdrop-blur-md p-4 border-b border-zinc-800 flex items-center justify-between z-10">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-amber-500/10 flex items-center justify-center text-amber-400">
              <Coins className="w-5 h-5" />
            </div>
            <h3 className="font-bold text-white text-base">المحفظة والرصيد (ZEVORA Balance)</h3>
          </div>
          <button onClick={onClose} className="p-1 rounded-full text-zinc-400 hover:text-white bg-zinc-800">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Success Alert */}
        {isSuccess && (
          <div className="m-4 p-3 bg-emerald-950/80 border border-emerald-800 rounded-xl flex items-center gap-2 text-xs text-emerald-300">
            <CheckCircle2 className="w-4 h-4 shrink-0 text-emerald-400" />
            <span>{isSuccess}</span>
          </div>
        )}

        {/* Tab switcher */}
        <div className="p-4 grid grid-cols-3 gap-2">
          <button
            onClick={() => setTab('balance')}
            className={`py-2 px-3 rounded-xl text-xs font-semibold transition-all ${
              tab === 'balance' ? 'bg-amber-500 text-black shadow-md' : 'bg-zinc-800 text-zinc-300 hover:bg-zinc-700'
            }`}
          >
            نظرة عامة
          </button>
          <button
            onClick={() => setTab('recharge')}
            className={`py-2 px-3 rounded-xl text-xs font-semibold transition-all ${
              tab === 'recharge' ? 'bg-cyan-500 text-black shadow-md' : 'bg-zinc-800 text-zinc-300 hover:bg-zinc-700'
            }`}
          >
            شحن عملات
          </button>
          <button
            onClick={() => setTab('withdraw')}
            className={`py-2 px-3 rounded-xl text-xs font-semibold transition-all ${
              tab === 'withdraw' ? 'bg-emerald-500 text-black shadow-md' : 'bg-zinc-800 text-zinc-300 hover:bg-zinc-700'
            }`}
          >
            سحب الأرباح (BaridiMob)
          </button>
        </div>

        {/* View 1: Balance overview */}
        {tab === 'balance' && (
          <div className="p-4 space-y-4">
            <div className="p-5 rounded-2xl bg-gradient-to-br from-amber-500/20 via-zinc-900 to-zinc-900 border border-amber-500/30 space-y-3">
              <span className="text-xs text-zinc-400">الرصيد الكلي القابل للسحب:</span>
              <div className="flex items-baseline gap-2">
                <span className="text-3xl font-black text-white">{user.balanceDzd.toLocaleString()}</span>
                <span className="text-amber-400 font-bold text-sm">دج (DZD)</span>
              </div>
              <div className="pt-2 border-t border-zinc-800 flex items-center justify-between text-xs">
                <span className="text-zinc-400">عملات الحساب (Coins):</span>
                <span className="text-amber-300 font-bold flex items-center gap-1">
                  🪙 {user.coins.toLocaleString()} عملة
                </span>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <button
                onClick={() => setTab('recharge')}
                className="p-3 rounded-xl bg-zinc-800 hover:bg-zinc-700 border border-zinc-700 text-center flex flex-col items-center gap-1.5 transition-colors"
              >
                <ArrowDownLeft className="w-5 h-5 text-cyan-400" />
                <span className="text-xs font-bold text-white">شحن الرصيد</span>
                <span className="text-[10px] text-zinc-400">البطاقة الذهبية / CIB</span>
              </button>

              <button
                onClick={() => setTab('withdraw')}
                className="p-3 rounded-xl bg-zinc-800 hover:bg-zinc-700 border border-zinc-700 text-center flex flex-col items-center gap-1.5 transition-colors"
              >
                <ArrowUpRight className="w-5 h-5 text-emerald-400" />
                <span className="text-xs font-bold text-white">سحب الأرباح</span>
                <span className="text-[10px] text-zinc-400">BaridiMob أو CCP</span>
              </button>
            </div>

            <div className="pt-2">
              <h4 className="text-xs font-bold text-zinc-400 mb-2">آخر المعاملات:</h4>
              <div className="p-6 rounded-xl bg-zinc-950 border border-zinc-800 text-center">
                <p className="text-xs font-bold text-zinc-300">لا توجد معاملات بعد</p>
                <p className="text-[11px] text-zinc-500 mt-1">ستظهر هنا عملياتك الحقيقية فقط.</p>
              </div>
            </div>
          </div>
        )}

        {/* View 2: Recharge */}
        {tab === 'recharge' && (
          <div className="p-4 space-y-4">
            <h4 className="text-xs font-bold text-zinc-300">اختر باقة العملات لشحنها:</h4>
            <div className="grid grid-cols-2 gap-2.5">
              {[
                { coins: 350, dzd: 800 },
                { coins: 700, dzd: 1500 },
                { coins: 1400, dzd: 2900 },
                { coins: 3500, dzd: 7000 },
              ].map((pkg, i) => (
                <button
                  key={i}
                  onClick={() => handleRecharge(pkg.coins, pkg.dzd)}
                  className="p-3.5 rounded-xl bg-zinc-950 hover:bg-zinc-800/80 border border-zinc-800 hover:border-amber-500/50 flex flex-col items-center gap-1 transition-all"
                >
                  <span className="text-amber-400 font-bold text-base">🪙 {pkg.coins}</span>
                  <span className="text-xs font-semibold text-white">{pkg.dzd} دج</span>
                </button>
              ))}
            </div>
            <div className="p-3 rounded-xl bg-zinc-950 border border-zinc-800 flex items-center gap-2 text-xs text-zinc-400">
              <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0" />
              <span>الدفع الحقيقي (CIB / EDAHABIA) يتم داخل تطبيق الأندرويد فقط.</span>
            </div>
          </div>
        )}

        {/* View 3: Withdraw */}
        {tab === 'withdraw' && (
          <div className="p-4 space-y-4">
            <div className="space-y-1.5 text-right">
              <label className="text-xs font-medium text-zinc-400">المبلغ المراد سحبه (دج):</label>
              <input
                type="number"
                value={withdrawAmount}
                onChange={(e) => setWithdrawAmount(e.target.value)}
                max={user.balanceDzd}
                className="w-full bg-zinc-950 border border-zinc-700 rounded-xl px-4 py-3 text-white font-bold text-lg focus:outline-none focus:border-emerald-500"
              />
              <span className="text-[11px] text-zinc-500">الرصيد المتاح: {user.balanceDzd.toLocaleString()} دج</span>
            </div>

            <div className="space-y-1.5 text-right">
              <label className="text-xs font-medium text-zinc-400">رقم RIP بريدي موب (BaridiMob):</label>
              <input
                type="text"
                dir="ltr"
                value={ripNumber}
                onChange={(e) => setRipNumber(e.target.value)}
                placeholder="0079999900XXXXXXXXXX"
                className="w-full bg-zinc-950 border border-zinc-700 rounded-xl px-4 py-3 text-sm text-white font-mono focus:outline-none focus:border-emerald-500"
              />
            </div>

            <button
              onClick={handleWithdraw}
              className="w-full py-3.5 px-4 rounded-xl font-bold bg-emerald-600 hover:bg-emerald-500 text-white shadow-lg shadow-emerald-600/20 active:scale-[0.98] transition-all text-sm"
            >
              متابعة طلب السحب عبر تطبيق الأندرويد
            </button>
          </div>
        )}
      </div>
    </div>
  );
};
