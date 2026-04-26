import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Input } from '../ui/Input';
import { Button } from '../ui/Button';
import { useAuth } from '../../hooks/useAuth';
import { useToast } from '../ui/Toast';
import { Eye, EyeOff } from 'lucide-react';
import { useTranslation } from 'react-i18next';

function RegisterForm() {
  const { register, sendCode } = useAuth();
  const { success, error: toastError } = useToast();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [sendingCode, setSendingCode] = useState(false);
  const [countdown, setCountdown] = useState(0);

  const handleSendCode = async () => {
    if (!email) { toastError(t('register.errors.emailRequired')); return; }
    setSendingCode(true);
    try {
      await sendCode(email);
      success(t('register.success.codeSent'));
      setCountdown(60);
      const timer = setInterval(() => {
        setCountdown(prev => {
          if (prev <= 1) { clearInterval(timer); return 0; }
          return prev - 1;
        });
      }, 1000);
    } catch (err) {
      toastError(err instanceof Error ? err.message : t('register.errors.sendFailed'));
    } finally {
      setSendingCode(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      await register(email, password, code, username || undefined);
      success(t('register.success.registered'));
      navigate('/', { replace: true });
    } catch (err) {
      toastError(err instanceof Error ? err.message : t('register.errors.registerFailed'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <Input
        label={t('register.email')}
        type="email"
        value={email}
        onChange={e => setEmail(e.target.value)}
        placeholder={t('login.emailPlaceholder')}
        required
      />
      <div className="flex gap-2">
        <div className="flex-1">
          <Input
            label={t('register.verificationCode')}
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
            {countdown > 0 ? `${countdown}s` : t('register.sendCode')}
          </Button>
        </div>
      </div>
      <Input
        label={t('register.username')}
        value={username}
        onChange={e => setUsername(e.target.value)}
        placeholder={t('register.usernamePlaceholder')}
      />
      <div className="relative">
        <Input
          label={t('register.password')}
          type={showPassword ? 'text' : 'password'}
          value={password}
          onChange={e => setPassword(e.target.value)}
          placeholder={t('register.passwordPlaceholder')}
          required
          minLength={6}
        />
        <button
          type="button"
          onClick={() => setShowPassword(!showPassword)}
          className="absolute right-3 top-8 text-text-tertiary hover:text-text-primary"
        >
          {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
        </button>
      </div>
      <Button type="submit" variant="primary" loading={loading} className="w-full">
        {t('register.submit')}
      </Button>
      <p className="text-center text-[13px] text-text-tertiary">
        {t('register.hasAccount')}{' '}
        <Link to="/login" className="text-accent hover:underline">
          {t('register.loginNow')}
        </Link>
      </p>
    </form>
  );
}

export { RegisterForm };
