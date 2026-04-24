import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useToast } from '../components/ui/Toast';
import { subscriptionApi } from '../api/subscription';
import type { SubscriptionStatusResponse } from '../api/types';
import { CreditCard, ExternalLink, AlertCircle, Clock, CheckCircle } from 'lucide-react';

export { SubscriptionStatusPage };

const STATUS_CONFIG: Record<string, { label: string; color: string; icon: React.ReactNode }> = {
  active: { label: '活跃', color: 'text-green', icon: <CheckCircle className="w-4 h-4" /> },
  trialing: { label: '试用中', color: 'text-green', icon: <Clock className="w-4 h-4" /> },
  past_due: { label: '待付款', color: 'text-yellow', icon: <AlertCircle className="w-4 h-4" /> },
  canceled: { label: '已取消', color: 'text-text-tertiary', icon: <ExternalLink className="w-4 h-4" /> },
  unpaid: { label: '未支付', color: 'text-red', icon: <AlertCircle className="w-4 h-4" /> },
  paused: { label: '已暂停', color: 'text-yellow', icon: <Clock className="w-4 h-4" /> },
  none: { label: '未订阅', color: 'text-text-tertiary', icon: null },
};

const PLAN_LABELS: Record<string, string> = {
  FREE: '免费版',
  PRO: '专业版',
  MAX: '旗舰版',
};

function SubscriptionStatusPage() {
  const navigate = useNavigate();
  const { toast } = useToast();
  const [sub, setSub] = useState<SubscriptionStatusResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);

  useEffect(() => {
    subscriptionApi.getStatus()
      .then(r => setSub(r.data))
      .catch(() => toast('error', '获取订阅状态失败'))
      .finally(() => setLoading(false));
  }, []);

  const handleCancel = async () => {
    setActionLoading(true);
    try {
      const result = await subscriptionApi.cancel();
      setSub(result.data);
      toast('success', '订阅将在当前周期结束后取消');
    } catch {
      toast('error', '取消订阅失败');
    } finally {
      setActionLoading(false);
    }
  };

  const handleManage = async () => {
    try {
      const result = await subscriptionApi.portal();
      window.open(result.data.portalUrl, '_blank');
    } catch {
      toast('error', '打开账单管理页面失败');
    }
  };

  if (loading) return <div className="flex justify-center py-12 text-text-tertiary text-[13px]">加载中...</div>;
  if (!sub) return null;

  const statusConfig = STATUS_CONFIG[sub.status] || STATUS_CONFIG.none;
  const planLabel = PLAN_LABELS[sub.plan] || sub.plan;
  const isActive = sub.status === 'active' || sub.status === 'trialing';

  return (
    <div className="p-5 space-y-4">
      <h2 className="text-[15px] font-semibold text-text-primary">订阅管理</h2>

      {/* Status card */}
      <div className="p-4 rounded-lg bg-surface-secondary">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-3">
            <CreditCard className="w-5 h-5 text-accent" />
            <span className="text-[15px] font-medium text-text-primary">{planLabel}</span>
          </div>
          <span className={`inline-flex items-center gap-1.5 text-[12px] font-medium ${statusConfig.color}`}>
            {statusConfig.icon}
            {statusConfig.label}
          </span>
        </div>

        {sub.periodEnd && isActive && (
          <div className="text-[13px] text-text-secondary">
            当前周期至：{new Date(sub.periodEnd).toLocaleDateString('zh-CN')}
          </div>
        )}

        {sub.cancelAtPeriodEnd && (
          <div className="mt-2 text-[13px] text-yellow flex items-center gap-1.5">
            <AlertCircle className="w-4 h-4" />
            订阅将在周期结束后取消
          </div>
        )}

        {sub.status === 'none' && (
          <div>
            <p className="text-[13px] text-text-tertiary mb-3">
              当前未订阅任何方案，可前往定价页选择适合的方案
            </p>
            <button
              onClick={() => navigate('/pricing')}
              className="px-4 py-2 text-[13px] font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-colors"
            >
              查看定价方案
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
            管理账单
          </button>
          <button
            onClick={handleCancel}
            disabled={actionLoading || sub.cancelAtPeriodEnd}
            className="flex-1 py-2.5 text-[13px] font-medium text-red border border-red/20 rounded-button hover:bg-red-bg disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
          >
            {actionLoading ? '处理中...' : sub.cancelAtPeriodEnd ? '已设置取消' : '取消订阅'}
          </button>
        </div>
      )}

      {sub.status !== 'none' && sub.status !== 'active' && sub.status !== 'trialing' && (
        <div className="flex gap-3">
          <button
            onClick={() => navigate('/pricing')}
            className="flex-1 py-2.5 text-[13px] font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-colors"
          >
            重新订阅
          </button>
          {sub.status !== 'canceled' && (
            <button
              onClick={handleManage}
              className="flex-1 py-2.5 text-[13px] font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-colors"
            >
              管理账单
            </button>
          )}
        </div>
      )}
    </div>
  );
}
