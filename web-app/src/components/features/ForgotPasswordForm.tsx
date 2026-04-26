import { useState } from 'react';
import { Input } from '../ui/Input';
import { Button } from '../ui/Button';
import { useAuth } from '../../hooks/useAuth';
import { useToast } from '../ui/Toast';
import { api } from '../../api/client';
import { useTranslation } from 'react-i18next';

function ForgotPasswordForm() {
  const { sendResetCode } = useAuth();
  const { success, error: toastError } = useToast();
  const { t } = useTranslation();
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [sendingCode, setSendingCode] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [loading, setLoading] = useState(false);
  const [step, setStep] = useState<'email' | 'reset'>('email');

  const handleSendCode = async () => {
    if (!email) { toastError(t('forgotPassword.errors.emailRequired')); return; }
    setSendingCode(true);
    try {
      await sendResetCode(email);
      success(t('register.success.codeSent'));
      setStep('reset');
      setCountdown(60);
      const timer = setInterval(() => {
        setCountdown(prev => {
          if (prev <= 1) { clearInterval(timer); return 0; }
          return prev - 1;
        });
      }, 1000);
    } catch (err) {
      toastError(err instanceof Error ? err.message : t('forgotPassword.errors.sendFailed'));
    } finally {
      setSendingCode(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (password !== confirmPassword) { toastError(t('forgotPassword.errors.passwordMismatch')); return; }
    if (password.length < 6) { toastError(t('forgotPassword.errors.passwordTooShort')); return; }

    setLoading(true);
    try {
      await api.post<null>('/user/reset-password', { email, code, newPassword: password });
      success(t('forgotPassword.success.resetSuccess'));
      window.location.href = '/login';
    } catch (err) {
      toastError(err instanceof Error ? err.message : t('forgotPassword.errors.resetFailed'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <Input
        label={t('forgotPassword.email')}
        type="email"
        value={email}
        onChange={e => setEmail(e.target.value)}
        placeholder={t('forgotPassword.emailPlaceholder')}
        required
      />
      <div className="flex gap-2">
        <div className="flex-1">
          <Input
            label={t('forgotPassword.verificationCode')}
            value={code}
            onChange={e => setCode(e.target.value)}
            placeholder={t('forgotPassword.codePlaceholder')}
            required
          />
        </div>
        <div className="flex items-end">
          <Button
            type="button"
            variant="secondary"
            onClick={handleSendCode}
            loading={sendingCode}
            disabled={countdown > 0}
            className="whitespace-nowrap"
          >
            {countdown > 0 ? `${countdown}s` : t('forgotPassword.sendCode')}
          </Button>
        </div>
      </div>
      {step === 'reset' && (
        <>
          <Input
            label={t('forgotPassword.newPassword')}
            type="password"
            value={password}
            onChange={e => setPassword(e.target.value)}
            placeholder={t('forgotPassword.newPasswordPlaceholder')}
            required
            minLength={6}
          />
          <Input
            label={t('forgotPassword.confirmPassword')}
            type="password"
            value={confirmPassword}
            onChange={e => setConfirmPassword(e.target.value)}
            placeholder={t('forgotPassword.confirmPasswordPlaceholder')}
            required
          />
        </>
      )}
      <Button type="submit" variant="primary" loading={loading} className="w-full">
        {t('forgotPassword.submit')}
      </Button>
    </form>
  );
}

export { ForgotPasswordForm };
