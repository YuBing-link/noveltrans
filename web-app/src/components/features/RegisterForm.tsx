import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Input } from '../ui/Input';
import { Button } from '../ui/Button';
import { useAuth } from '../../hooks/useAuth';
import { useToast } from '../ui/Toast';
import { Eye, EyeOff } from 'lucide-react';

function RegisterForm() {
  const { register, sendCode } = useAuth();
  const { success, error: toastError } = useToast();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [sendingCode, setSendingCode] = useState(false);
  const [countdown, setCountdown] = useState(0);

  const handleSendCode = async () => {
    if (!email) { toastError('请先输入邮箱'); return; }
    setSendingCode(true);
    try {
      await sendCode(email);
      success('验证码已发送');
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
    setLoading(true);
    try {
      await register(email, password, code, username || undefined);
      success('注册成功');
      navigate('/');
    } catch (err) {
      toastError(err instanceof Error ? err.message : '注册失败');
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
        placeholder="请输入邮箱"
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
      <Input
        label="用户名（可选）"
        value={username}
        onChange={e => setUsername(e.target.value)}
        placeholder="请输入用户名"
      />
      <div className="relative">
        <Input
          label="密码"
          type={showPassword ? 'text' : 'password'}
          value={password}
          onChange={e => setPassword(e.target.value)}
          placeholder="请输入密码（至少6位）"
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
        注册
      </Button>
      <p className="text-center text-[13px] text-text-tertiary">
        已有账号？{' '}
        <Link to="/login" className="text-accent hover:underline">
          立即登录
        </Link>
      </p>
    </form>
  );
}

export { RegisterForm };
