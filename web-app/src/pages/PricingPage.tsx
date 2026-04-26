import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { useToast } from '../components/ui/Toast';
import { subscriptionApi } from '../api/subscription';
import { Check, Sparkles } from 'lucide-react';
import { useTranslation } from 'react-i18next';

export { PricingPage };

interface PlanData {
  key: string;
  highlighted: boolean;
}

const PLANS: PlanData[] = [
  { key: 'free', highlighted: false },
  { key: 'pro', highlighted: true },
  { key: 'ultimate', highlighted: false },
];

function PricingPage() {
  const { t } = useTranslation();
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
      else toast('error', t('pricing.messages.checkoutFailed'));
    } catch {
      toast('error', t('pricing.messages.checkoutFailed'));
    } finally {
      setLoading(null);
    }
  };

  return (
    <div className="mx-auto" style={{ maxWidth: '80rem', padding: '0 0 0' }}>
      <div style={{ textAlign: 'center', paddingTop: '64px', paddingBottom: '40px' }}>
        <div style={{
          display: 'inline-flex', alignItems: 'center', gap: '8px',
          padding: '6px 16px', borderRadius: '9999px',
          background: 'rgba(201,100,66,0.10)', color: '#c96442',
          fontSize: '13px', fontWeight: 500, marginBottom: '24px',
        }}>
          <Sparkles className="w-3.5 h-3.5" />
          {t('pricing.title')}
        </div>
        <h1 style={{
          fontFamily: 'var(--font-serif)', fontSize: '36px', lineHeight: 1.2,
          fontWeight: 500, letterSpacing: '-0.025em', margin: '0 0 16px',
          color: '#141413',
        }}>
          {t('pricing.subtitle')}
        </h1>
        <p style={{ fontSize: '17px', lineHeight: 1.6, color: '#5e5d59', maxWidth: '640px', margin: '0 auto' }}>
          {t('pricing.description')}
        </p>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '12px', marginBottom: '48px' }}>
        <span style={{ fontSize: '14px', color: billingCycle === 'monthly' ? '#141413' : '#87867f', fontWeight: billingCycle === 'monthly' ? 500 : 400 }}>{t('pricing.billing.monthly')}</span>
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
        <span style={{ fontSize: '14px', color: billingCycle === 'yearly' ? '#141413' : '#87867f', fontWeight: billingCycle === 'yearly' ? 500 : 400 }}>{t('pricing.billing.yearly')}</span>
        <span style={{ fontSize: '12px', color: '#4a7c59', fontWeight: 500, background: 'rgba(74,124,89,0.10)', padding: '2px 10px', borderRadius: '9999px' }}>{t('pricing.billing.save20')}</span>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: '32px', marginBottom: '80px', padding: '0 16px' }}>
        {PLANS.map(plan => (
          <PlanCard
            key={plan.key}
            planKey={plan.key}
            highlighted={plan.highlighted}
            isCurrent={false}
            onSubscribe={plan.key === 'free' ? null : () => handleSubscribe(plan.key.toUpperCase())}
            loading={loading === plan.key.toUpperCase()}
            billingCycle={billingCycle}
          />
        ))}
      </div>
    </div>
  );
}

function PlanCard({ planKey, highlighted, isCurrent, onSubscribe, loading, billingCycle }: {
  planKey: string;
  highlighted: boolean;
  isCurrent: boolean;
  onSubscribe: (() => void) | null;
  loading: boolean;
  billingCycle: 'monthly' | 'yearly';
}) {
  const { t } = useTranslation();
  const prefix = `pricing.plans.${planKey}` as const;
  const name = t(`${prefix}.name`);
  const price = t(`${prefix}.price`);
  const priceMonthly = t(`${prefix}.priceMonthly`);
  const priceYearly = t(`${prefix}.priceYearly`);
  const yearlyTotal = t(`${prefix}.yearlyTotal`);
  const subscribeLabel = t(`${prefix}.subscribe`);
  const popularLabel = t(`${prefix}.popular`);
  const features: string[] = t(`${prefix}.features`, { returnObjects: true, defaultValue: [] }) as string[];

  const displayPrice = planKey === 'free'
    ? price
    : billingCycle === 'monthly' ? priceMonthly : priceYearly;

  return (
    <div style={{
      position: 'relative', borderRadius: '8px',
      border: highlighted ? '1px solid #c96442' : '1px solid #f0eee6',
      padding: '24px', display: 'flex', flexDirection: 'column',
      background: highlighted ? 'rgba(201,100,66,0.05)' : '#fff',
      boxShadow: highlighted ? '0 4px 24px rgba(0,0,0,0.08)' : 'none',
      transition: 'transform 0.2s, box-shadow 0.2s',
    }}>
      {highlighted && (
        <div style={{
          position: 'absolute', top: '-12px', left: '50%', transform: 'translateX(-50%)',
          padding: '2px 12px', borderRadius: '9999px', background: '#c96442',
          color: '#fff', fontSize: '11px', fontWeight: 600, whiteSpace: 'nowrap',
        }}>
          {popularLabel}
        </div>
      )}

      <h3 style={{
        fontFamily: 'var(--font-serif)', fontSize: '24px', lineHeight: 1.2,
        fontWeight: 500, letterSpacing: '-0.025em', color: '#141413', margin: 0,
      }}>{name}</h3>

      <div style={{ marginTop: '16px', marginBottom: '24px' }}>
        <span style={{ fontFamily: 'var(--font-serif)', fontSize: '36px', lineHeight: 1.2, fontWeight: 500, color: '#141413' }}>{displayPrice}</span>
        {billingCycle === 'yearly' && yearlyTotal && (
          <span style={{ marginLeft: '8px', fontSize: '13px', color: '#87867f' }}>{yearlyTotal}</span>
        )}
      </div>

      <ul style={{ listStyle: 'none', padding: 0, margin: '0 0 32px', flex: 1 }}>
        {features.map((f) => (
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
          {t('pricing.plans.free.current')}
        </div>
      ) : onSubscribe ? (
        <button
          onClick={onSubscribe}
          disabled={loading}
          style={{
            width: '100%', padding: '10px', borderRadius: '8px', fontSize: '14px', fontWeight: 500,
            border: highlighted ? 'none' : 'none',
            background: highlighted ? '#c96442' : 'rgba(201,100,66,0.10)',
            color: highlighted ? '#fff' : '#c96442',
            cursor: loading ? 'not-allowed' : 'pointer',
            opacity: loading ? 0.6 : 1, transition: 'all 0.15s',
          }}
        >
          {loading ? t('pricing.redirecting') : subscribeLabel}
        </button>
      ) : (
        <div style={{
          width: '100%', padding: '10px', borderRadius: '8px', border: '1px solid #f0eee6',
          fontSize: '14px', fontWeight: 500, color: '#5e5d59', textAlign: 'center',
        }}>
          {t('pricing.plans.free.current')}
        </div>
      )}
    </div>
  );
}
