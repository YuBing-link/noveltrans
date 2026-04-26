import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { subscriptionApi } from '../api/subscription';
import { CheckCircle, XCircle, Loader2, ArrowRight, AlertCircle } from 'lucide-react';

import { useTranslation } from 'react-i18next';

export { PaymentResultPage };

function PaymentResultPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { refreshUser } = useAuth();
  const { t } = useTranslation();
  const [loading, setLoading] = useState(true);
  const [status, setStatus] = useState<'success' | 'cancel' | 'error'>('success');
  const [message, setMessage] = useState('');

  useEffect(() => {
    const cancelled = searchParams.get('canceled');
    if (cancelled === 'true') {
      setStatus('cancel');
      setLoading(false);
      return;
    }

    const sessionId = searchParams.get('session_id');
    if (!sessionId) {
      setStatus('error');
      setMessage(t('payment.error.description'));
      setLoading(false);
      return;
    }

    // 主动调用后端接口验证支付状态
    subscriptionApi.verify(sessionId)
      .then((res) => {
        if (res.data.paid) {
          setStatus('success');
          setMessage(res.data.message);
          // 刷新用户信息以获取最新的订阅状态
          refreshUser().catch(() => {});
        } else {
          setStatus('error');
          setMessage(res.data.message || t('payment.pending.title'));
        }
      })
      .catch(() => {
        setStatus('error');
        setMessage(t('payment.error.action'));
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  if (loading) {
    return (
      <div className="min-h-[calc(100vh-3.5rem)] flex items-center justify-center">
        <div className="text-center">
          <Loader2 className="w-8 h-8 text-accent animate-spin mx-auto mb-4" />
          <p className="text-[15px] text-text-secondary">{t('payment.pending.verifying')}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-[calc(100vh-3.5rem)] flex items-center justify-center bg-gradient-to-b from-surface to-surface-secondary">
      <div className="text-center max-w-md px-6">
        {status === 'success' && (
          <>
            <div className="w-16 h-16 rounded-full bg-green/10 flex items-center justify-center mx-auto mb-6">
              <CheckCircle className="w-10 h-10 text-green" />
            </div>
            <h2 className="text-hero font-bold text-text-primary mb-3">{t('payment.success.title')}</h2>
            <p className="text-body text-text-secondary mb-8">
              {message || t('payment.success.description')}
            </p>
            <div className="flex gap-3 justify-center">
              <button
                onClick={() => navigate('/user/subscription')}
                className="inline-flex items-center gap-2 px-6 py-2.5 text-[14px] font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-colors"
              >
                {t('payment.success.viewSubscription')} <ArrowRight className="w-4 h-4" />
              </button>
              <button
                onClick={() => navigate('/')}
                className="inline-flex items-center gap-2 px-6 py-2.5 text-[14px] font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-colors"
              >
                {t('payment.success.backToHome')}
              </button>
            </div>
          </>
        )}

        {status === 'cancel' && (
          <>
            <div className="w-16 h-16 rounded-full bg-yellow/10 flex items-center justify-center mx-auto mb-6">
              <XCircle className="w-10 h-10 text-yellow" />
            </div>
            <h2 className="text-hero font-bold text-text-primary mb-3">{t('payment.cancelled.title')}</h2>
            <p className="text-body text-text-secondary mb-8">
              {t('payment.cancelled.description')}
            </p>
            <div className="flex gap-3 justify-center">
              <button
                onClick={() => navigate('/pricing')}
                className="inline-flex items-center gap-2 px-6 py-2.5 text-[14px] font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-colors"
              >
                {t('payment.cancelled.reselect')}
              </button>
              <button
                onClick={() => navigate('/')}
                className="inline-flex items-center gap-2 px-6 py-2.5 text-[14px] font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-colors"
              >
                {t('payment.success.backToHome')}
              </button>
            </div>
          </>
        )}

        {status === 'error' && (
          <>
            <div className="w-16 h-16 rounded-full bg-red/10 flex items-center justify-center mx-auto mb-6">
              <AlertCircle className="w-10 h-10 text-red" />
            </div>
            <h2 className="text-hero font-bold text-text-primary mb-3">{t('payment.error.title')}</h2>
            <p className="text-body text-text-secondary mb-8">
              {message}
            </p>
            <div className="flex gap-3 justify-center">
              <button
                onClick={() => navigate('/pricing')}
                className="inline-flex items-center gap-2 px-6 py-2.5 text-[14px] font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-colors"
              >
                {t('payment.pending.retry')}
              </button>
              <button
                onClick={() => navigate('/user/subscription')}
                className="inline-flex items-center gap-2 px-6 py-2.5 text-[14px] font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-colors"
              >
                {t('payment.success.viewSubscription')}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
