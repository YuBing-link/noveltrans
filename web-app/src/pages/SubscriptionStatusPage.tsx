import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useToast } from '../components/ui/Toast';
import { subscriptionApi } from '../api/subscription';
import type { SubscriptionStatusResponse } from '../api/types';
import { CreditCard, ExternalLink, AlertCircle, Clock, CheckCircle } from 'lucide-react';
import { useTranslation } from 'react-i18next';

export { SubscriptionStatusPage };

function SubscriptionStatusPage() {
  const navigate = useNavigate();
  const { toast } = useToast();
  const { t } = useTranslation();
  const [sub, setSub] = useState<SubscriptionStatusResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);

  useEffect(() => {
    subscriptionApi.getStatus()
      .then(r => setSub(r.data))
      .catch(() => toast('error', t('subscription.messages.fetchFailed')))
      .finally(() => setLoading(false));
  }, []);

  const handleCancel = async () => {
    setActionLoading(true);
    try {
      const result = await subscriptionApi.cancel();
      setSub(result.data);
      toast('success', t('subscription.messages.cancelling'));
    } catch {
      toast('error', t('subscription.messages.cancelFailed'));
    } finally {
      setActionLoading(false);
    }
  };

  const handleManage = async () => {
    try {
      const result = await subscriptionApi.portal();
      window.open(result.data.portalUrl, '_blank');
    } catch {
      toast('error', t('subscription.messages.openBillingFailed'));
    }
  };

  if (loading) return <div className="flex justify-center py-12 text-text-tertiary text-[13px]">{t('common.loading')}</div>;
  if (!sub) return null;

  const statusConfig: Record<string, { label: string; color: string; icon: React.ReactNode }> = {
    active: { label: t('subscription.status.active'), color: 'text-green', icon: <CheckCircle className="w-4 h-4" /> },
    trialing: { label: t('subscription.status.trial'), color: 'text-green', icon: <Clock className="w-4 h-4" /> },
    past_due: { label: t('subscription.status.pending'), color: 'text-yellow', icon: <AlertCircle className="w-4 h-4" /> },
    canceled: { label: t('subscription.status.cancelled'), color: 'text-text-tertiary', icon: <ExternalLink className="w-4 h-4" /> },
    unpaid: { label: t('subscription.status.unpaid'), color: 'text-red', icon: <AlertCircle className="w-4 h-4" /> },
    paused: { label: t('subscription.status.paused'), color: 'text-yellow', icon: <Clock className="w-4 h-4" /> },
    none: { label: t('subscription.status.notSubscribed'), color: 'text-text-tertiary', icon: null },
  };

  const planLabels: Record<string, string> = {
    FREE: t('subscription.plans.free'),
    PRO: t('subscription.plans.pro'),
    MAX: t('subscription.plans.ultimate'),
  };

  const config = statusConfig[sub.status] || statusConfig.none;
  const planLabel = planLabels[sub.plan] || sub.plan;
  const isActive = sub.status === 'active' || sub.status === 'trialing';

  return (
    <div className="p-5 space-y-4">
      <h2 className="text-[15px] font-semibold text-text-primary">{t('subscription.title')}</h2>

      {/* Status card */}
      <div className="p-4 rounded-lg bg-surface-secondary">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-3">
            <CreditCard className="w-5 h-5 text-accent" />
            <span className="text-[15px] font-medium text-text-primary">{planLabel}</span>
          </div>
          <span className={`inline-flex items-center gap-1.5 text-[12px] font-medium ${config.color}`}>
            {config.icon}
            {config.label}
          </span>
        </div>

        {sub.periodEnd && isActive && (
          <div className="text-[13px] text-text-secondary">
            {t('subscription.messages.currentPeriodEnd')}：{new Date(sub.periodEnd).toLocaleDateString()}
          </div>
        )}

        {sub.cancelAtPeriodEnd && (
          <div className="mt-2 text-[13px] text-yellow flex items-center gap-1.5">
            <AlertCircle className="w-4 h-4" />
            {t('subscription.messages.cancelConfirmed')}
          </div>
        )}

        {sub.status === 'none' && (
          <div>
            <p className="text-[13px] text-text-tertiary mb-3">
              {t('subscription.messages.notSubscribed')}
            </p>
            <button
              onClick={() => navigate('/pricing')}
              className="px-4 py-2 text-[13px] font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-colors"
            >
              {t('subscription.actions.viewPricing')}
            </button>
          </div>
        )}
      </div>

      {/* Actions */}
      {isActive && (
        <div className="flex gap-3">
          <button
            onClick={handleManage}
            className="flex-1 py-2.5 text-[13px] font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-colors"
          >
            {t('subscription.actions.manageBilling')}
          </button>
          <button
            onClick={handleCancel}
            disabled={actionLoading || sub.cancelAtPeriodEnd}
            className="flex-1 py-2.5 text-[13px] font-medium text-red border border-red/20 rounded-button hover:bg-red-bg disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
          >
            {actionLoading ? t('subscription.actions.processing') : sub.cancelAtPeriodEnd ? t('subscription.actions.cancelSet') : t('subscription.actions.cancelSubscription')}
          </button>
        </div>
      )}

      {sub.status !== 'none' && sub.status !== 'active' && sub.status !== 'trialing' && (
        <div className="flex gap-3">
          <button
            onClick={() => navigate('/pricing')}
            className="flex-1 py-2.5 text-[13px] font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-colors"
          >
            {t('subscription.actions.resubscribe')}
          </button>
          {sub.status !== 'canceled' && (
            <button
              onClick={handleManage}
              className="flex-1 py-2.5 text-[13px] font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-colors"
            >
              {t('subscription.actions.manageBilling')}
            </button>
          )}
        </div>
      )}
    </div>
  );
}
