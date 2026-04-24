import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { useToast } from '../components/ui/Toast';
import { subscriptionApi } from '../api/subscription';
import { PLAN_CONFIGS } from '../api/types';
import { Check, Sparkles } from 'lucide-react';

export { PricingPage };

function PricingPage() {
  const navigate = useNavigate();
  const { isAuthenticated, user } = useAuth();
  const { toast } = useToast();
  const [billingCycle, setBillingCycle] = useState<'monthly' | 'yearly'>('monthly');
  const [loading, setLoading] = useState<string | null>(null);

  const handleSubscribe = async (plan: string) => {
    if (!isAuthenticated) {
      navigate('/login', { state: { from: '/pricing' } });
      return;
    }

    setLoading(plan);
    try {
      const result = await subscriptionApi.checkout({ plan, billingCycle });
      window.location.href = result.data.checkoutUrl;
    } catch {
      toast('error', '创建支付会话失败，请稍后重试');
    } finally {
      setLoading(null);
    }
  };

  const currentLevel = user?.userLevel?.toUpperCase() || 'FREE';

  return (
    <div className="min-h-[calc(100vh-3.5rem)] bg-gradient-to-b from-surface to-surface-secondary">
      {/* Hero */}
      <div className="text-center pt-16 pb-10">
        <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-accent/10 text-accent text-[13px] font-medium mb-6">
          <Sparkles className="w-3.5 h-3.5" />
          升级到专业版，解锁全部能力
        </div>
        <h1 className="text-hero font-bold text-text-primary mb-4">
          选择适合你的方案
        </h1>
        <p className="text-body-lg text-text-secondary max-w-xl mx-auto">
          免费方案即可满足日常需求，升级方案获得更多翻译额度与高级功能
        </p>
      </div>

      {/* Billing toggle */}
      <div className="flex items-center justify-center gap-3 mb-12">
        <span className={`text-[14px] ${billingCycle === 'monthly' ? 'text-text-primary font-medium' : 'text-text-tertiary'}`}>月付</span>
        <button
          onClick={() => setBillingCycle(billingCycle === 'monthly' ? 'yearly' : 'monthly')}
          className={`relative w-12 h-6 rounded-full transition-colors ${billingCycle === 'yearly' ? 'bg-accent' : 'bg-gray-300 dark:bg-gray-600'}`}
        >
          <div className={`absolute top-0.5 left-0.5 w-5 h-5 rounded-full bg-white shadow-sm transition-transform ${billingCycle === 'yearly' ? 'translate-x-6' : ''}`} />
        </button>
        <span className={`text-[14px] ${billingCycle === 'yearly' ? 'text-text-primary font-medium' : 'text-text-tertiary'}`}>年付</span>
        <span className="text-[12px] text-green font-medium bg-green/10 px-2.5 py-1 rounded-full">省 20%</span>
      </div>

      {/* Plan cards */}
      <div className="max-w-5xl mx-auto px-4 pb-20 grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* FREE */}
        <PlanCard
          config={PLAN_CONFIGS.FREE}
          isCurrent={currentLevel === 'FREE'}
          onSubscribe={null}
          loading={false}
          billingCycle="monthly"
        />

        {/* PRO */}
        <PlanCard
          config={PLAN_CONFIGS.PRO}
          isCurrent={currentLevel === 'PRO'}
          onSubscribe={() => handleSubscribe('PRO')}
          loading={loading === 'PRO'}
          billingCycle={billingCycle}
        />

        {/* MAX */}
        <PlanCard
          config={PLAN_CONFIGS.MAX}
          isCurrent={currentLevel === 'MAX'}
          onSubscribe={() => handleSubscribe('MAX')}
          loading={loading === 'MAX'}
          billingCycle={billingCycle}
        />
      </div>
    </div>
  );
}

function PlanCard({ config, isCurrent, onSubscribe, loading, billingCycle }: {
  config: typeof PLAN_CONFIGS.FREE;
  isCurrent: boolean;
  onSubscribe: (() => void) | null;
  loading: boolean;
  billingCycle: 'monthly' | 'yearly';
}) {
  const price = config.price !== '¥0'
    ? (billingCycle === 'monthly' ? (config as any).priceMonthly : (config as any).priceYearly)
    : config.price;

  return (
    <div
      className={`relative rounded-card border p-6 flex flex-col transition-card ${
        config.highlighted
          ? 'border-accent bg-accent/5 shadow-lg'
          : 'border-border bg-white dark:bg-surface'
      }`}
    >
      {config.highlighted && (
        <div className="absolute -top-3 left-1/2 -translate-x-1/2 px-3 py-0.5 rounded-full bg-accent text-white text-[11px] font-semibold">
          最受欢迎
        </div>
      )}

      <h3 className="text-heading font-semibold text-text-primary">{config.name}</h3>

      <div className="mt-4 mb-6">
        <span className="text-hero font-bold text-text-primary">{price}</span>
        {billingCycle === 'yearly' && (config as any).yearlyTotal && (
          <span className="ml-2 text-[13px] text-text-tertiary">{(config as any).yearlyTotal}</span>
        )}
      </div>

      <ul className="space-y-3 mb-8 flex-1">
        {config.features.map((f) => (
          <li key={f} className="flex items-start gap-2 text-[14px] text-text-secondary">
            <Check className="w-4 h-4 text-green mt-0.5 flex-shrink-0" />
            {f}
          </li>
        ))}
      </ul>

      {isCurrent ? (
        <div className="w-full py-2.5 rounded-button border border-border text-[14px] font-medium text-text-secondary cursor-default text-center">
          当前方案
        </div>
      ) : onSubscribe ? (
        <button
          onClick={onSubscribe}
          disabled={loading}
          className={`w-full py-2.5 rounded-button text-[14px] font-medium transition-button ${
            config.highlighted
              ? 'bg-accent text-white hover:bg-accent-hover disabled:bg-accent/60'
              : 'bg-accent/10 text-accent hover:bg-accent/20 disabled:bg-accent/5'
          }`}
        >
          {loading ? '跳转中...' : config.cta}
        </button>
      ) : (
        <div className="w-full py-2.5 rounded-button border border-border text-[14px] font-medium text-text-tertiary text-center">
          {config.cta}
        </div>
      )}
    </div>
  );
}
