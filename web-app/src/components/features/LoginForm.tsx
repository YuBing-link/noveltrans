import { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { Input } from '../ui/Input';
import { Button } from '../ui/Button';
import { useAuth } from '../../hooks/useAuth';
import { useToast } from '../ui/Toast';
import { Eye, EyeOff } from 'lucide-react';

function LoginForm() {
  const { login } = useAuth();
  const { error: toastError } = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      await login(email, password);
      const from = (location.state as any)?.from || '/';
      navigate(from, { replace: true });
    } catch (err) {
      toastError(err instanceof Error ? err.message : '登录失败');
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
        autoComplete="email"
      />
      <div className="relative">
        <Input
          label="密码"
          type={showPassword ? 'text' : 'password'}
          value={password}
          onChange={e => setPassword(e.target.value)}
          placeholder="请输入密码"
          required
          autoComplete="current-password"
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
        登录
      </Button>
      <p className="text-center text-[13px] text-text-tertiary">
        还没有账号？{' '}
        <Link to="/register" className="text-accent hover:underline">
          立即注册
        </Link>
      </p>
      <p className="text-center text-[13px]">
        <Link to="/forgot-password" className="text-accent hover:underline">
          忘记密码？
        </Link>
      </p>
    </form>
  );
}

export { LoginForm };
