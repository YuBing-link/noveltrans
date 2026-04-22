import { useState } from 'react';
import { Input } from '../ui/Input';
import { Button } from '../ui/Button';
import { useAuth } from '../../hooks/useAuth';
import { useToast } from '../ui/Toast';
import { api } from '../../api/client';

function ForgotPasswordForm() {
  const { sendResetCode } = useAuth();
  const { success, error: toastError } = useToast();
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [sendingCode, setSendingCode] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [loading, setLoading] = useState(false);
  const [step, setStep] = useState<'email' | 'reset'>('email');

  const handleSendCode = async () => {
    if (!email) { toastError('请先输入邮箱'); return; }
    setSendingCode(true);
    try {
      await sendResetCode(email);
      success('验证码已发送');
      setStep('reset');
      setCountdown(60);
      const timer = setInterval(() => {
        setCountdown(prev => {
          if (prev <= 1) { clearInterval(timer); return 0; }
          return prev - 1;
        });
      }, 1000);
    } catch (err) {
      toastError(err instanceof Error ? err.message : '发送失败');
    } finally {
      setSendingCode(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (password !== confirmPassword) { toastError('两次密码输入不一致'); return; }
    if (password.length < 6) { toastError('密码至少6位'); return; }

    setLoading(true);
    try {
      await api.post<null>('/user/reset-password', { email, code, newPassword: password });
      success('密码重置成功，请登录');
      window.location.href = '/login';
    } catch (err) {
      toastError(err instanceof Error ? err.message : '重置失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <Input
        label="邮箱"
        type="email"
        value={email}
        onChange={e => setEmail(e.target.value)}
        placeholder="请输入注册邮箱"
        required
      />
      <div className="flex gap-2">
        <div className="flex-1">
          <Input
            label="验证码"
            value={code}
            onChange={e => setCode(e.target.value)}
            placeholder="输入验证码"
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
            {countdown > 0 ? `${countdown}s` : '发送验证码'}
          </Button>
        </div>
      </div>
      {step === 'reset' && (
        <>
          <Input
            label="新密码"
            type="password"
            value={password}
            onChange={e => setPassword(e.target.value)}
            placeholder="请输入新密码（至少6位）"
            required
            minLength={6}
          />
          <Input
            label="确认密码"
            type="password"
            value={confirmPassword}
            onChange={e => setConfirmPassword(e.target.value)}
            placeholder="请再次输入密码"
            required
          />
        </>
      )}
      <Button type="submit" variant="primary" loading={loading} className="w-full">
        重置密码
      </Button>
    </form>
  );
}

export { ForgotPasswordForm };
