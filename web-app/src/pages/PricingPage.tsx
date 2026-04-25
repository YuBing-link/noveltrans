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
  const { isAuthenticated } = useAuth();
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
      const url = result.data.checkoutUrl;
      if (url) window.location.href = url;
      else toast('error', '获取支付链接失败，请稍后重试');
    } catch {
      toast('error', '创建支付会话失败，请稍后重试');
    } finally {
      setLoading(null);
    }
  };

  return (
    <div className="mx-auto" style={{ maxWidth: '80rem', padding: '0 0 0' }}>
      {/* Hero */}
      <div style={{ textAlign: 'center', paddingTop: '64px', paddingBottom: '40px' }}>
        <div style={{
          display: 'inline-flex', alignItems: 'center', gap: '8px',
          padding: '6px 16px', borderRadius: '9999px',
          background: 'rgba(201,100,66,0.10)', color: '#c96442',
          fontSize: '13px', fontWeight: 500, marginBottom: '24px',
        }}>
          <Sparkles className="w-3.5 h-3.5" />
          升级到专业版，解锁全部能力
        </div>
        <h1 style={{
          fontFamily: 'var(--font-serif)', fontSize: '36px', lineHeight: 1.2,
          fontWeight: 500, letterSpacing: '-0.025em', margin: '0 0 16px',
          color: '#141413',
        }}>
          选择适合你的方案
        </h1>
        <p style={{ fontSize: '17px', lineHeight: 1.6, color: '#5e5d59', maxWidth: '640px', margin: '0 auto' }}>
          免费方案即可满足日常需求，升级方案获得更多翻译额度与高级功能
        </p>
      </div>

      {/* Billing toggle */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '12px', marginBottom: '48px' }}>
        <span style={{ fontSize: '14px', color: billingCycle === 'monthly' ? '#141413' : '#87867f', fontWeight: billingCycle === 'monthly' ? 500 : 400 }}>月付</span>
        <button
          onClick={() => setBillingCycle(billingCycle === 'monthly' ? 'yearly' : 'monthly')}
          style={{
            position: 'relative', width: '48px', height: '24px', borderRadius: '9999px',
            background: billingCycle === 'yearly' ? '#c96442' : '#d1cfc5',
            border: 'none', cursor: 'pointer', padding: 0, transition: 'background 0.2s',
          }}
        >
          <div style={{
            position: 'absolute', top: '2px', left: billingCycle === 'yearly' ? '26px' : '2px',
            width: '20px', height: '20px', borderRadius: '50%', background: '#fff',
            boxShadow: '0 1px 3px rgba(0,0,0,0.15)', transition: 'left 0.2s',
          }} />
        </button>
        <span style={{ fontSize: '14px', color: billingCycle === 'yearly' ? '#141413' : '#87867f', fontWeight: billingCycle === 'yearly' ? 500 : 400 }}>年付</span>
        <span style={{ fontSize: '12px', color: '#4a7c59', fontWeight: 500, background: 'rgba(74,124,89,0.10)', padding: '2px 10px', borderRadius: '9999px' }}>省 20%</span>
      </div>

      {/* Plan cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: '32px', marginBottom: '80px', padding: '0 16px' }}>
        <PlanCard config={PLAN_CONFIGS.FREE} isCurrent={false} onSubscribe={null} loading={false} billingCycle={billingCycle} />
        <PlanCard config={PLAN_CONFIGS.PRO} isCurrent={false} onSubscribe={() => handleSubscribe('PRO')} loading={loading === 'PRO'} billingCycle={billingCycle} />
        <PlanCard config={PLAN_CONFIGS.MAX} isCurrent={false} onSubscribe={() => handleSubscribe('MAX')} loading={loading === 'MAX'} billingCycle={billingCycle} />
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
    <div style={{
      position: 'relative', borderRadius: '8px',
      border: config.highlighted ? '1px solid #c96442' : '1px solid #f0eee6',
      padding: '24px', display: 'flex', flexDirection: 'column',
      background: config.highlighted ? 'rgba(201,100,66,0.05)' : '#fff',
      boxShadow: config.highlighted ? '0 4px 24px rgba(0,0,0,0.08)' : 'none',
      transition: 'transform 0.2s, box-shadow 0.2s',
    }}>
      {config.highlighted && (
        <div style={{
          position: 'absolute', top: '-12px', left: '50%', transform: 'translateX(-50%)',
          padding: '2px 12px', borderRadius: '9999px', background: '#c96442',
          color: '#fff', fontSize: '11px', fontWeight: 600, whiteSpace: 'nowrap',
        }}>
          最受欢迎
        </div>
      )}

      <h3 style={{
        fontFamily: 'var(--font-serif)', fontSize: '24px', lineHeight: 1.2,
        fontWeight: 500, letterSpacing: '-0.025em', color: '#141413', margin: 0,
      }}>{config.name}</h3>

      <div style={{ marginTop: '16px', marginBottom: '24px' }}>
        <span style={{ fontFamily: 'var(--font-serif)', fontSize: '36px', lineHeight: 1.2, fontWeight: 500, color: '#141413' }}>{price}</span>
        {billingCycle === 'yearly' && (config as any).yearlyTotal && (
          <span style={{ marginLeft: '8px', fontSize: '13px', color: '#87867f' }}>{(config as any).yearlyTotal}</span>
        )}
      </div>

      <ul style={{ listStyle: 'none', padding: 0, margin: '0 0 32px', flex: 1 }}>
        {config.features.map((f) => (
          <li key={f} style={{ display: 'flex', alignItems: 'flex-start', gap: '8px', fontSize: '14px', color: '#5e5d59', marginBottom: '12px' }}>
            <Check className="w-4 h-4" style={{ color: '#4a7c59', marginTop: '2px', flexShrink: 0 }} />
            {f}
          </li>
        ))}
      </ul>

      {isCurrent ? (
        <div style={{
          width: '100%', padding: '10px', borderRadius: '8px', border: '1px solid #f0eee6',
          fontSize: '14px', fontWeight: 500, color: '#5e5d59', cursor: 'default', textAlign: 'center',
        }}>
          当前方案
        </div>
      ) : onSubscribe ? (
        <button
          onClick={onSubscribe}
          disabled={loading}
          style={{
            width: '100%', padding: '10px', borderRadius: '8px', fontSize: '14px', fontWeight: 500,
            border: config.highlighted ? 'none' : 'none',
            background: config.highlighted ? '#c96442' : 'rgba(201,100,66,0.10)',
            color: config.highlighted ? '#fff' : '#c96442',
            cursor: loading ? 'not-allowed' : 'pointer',
            opacity: loading ? 0.6 : 1, transition: 'all 0.15s',
          }}
        >
          {loading ? '跳转中...' : config.cta}
        </button>
      ) : (
        <div style={{
          width: '100%', padding: '10px', borderRadius: '8px', border: '1px solid #f0eee6',
          fontSize: '14px', fontWeight: 500, color: '#5e5d59', textAlign: 'center',
        }}>
          {config.cta}
        </div>
      )}
    </div>
  );
}
