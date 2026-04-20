import { Link } from 'react-router-dom';
import { LoginForm } from '../components/features/LoginForm';
import { Card } from '../components/ui/Card';

function LoginPage() {
  return (
    <div className="min-h-[calc(100vh-3.5rem)] flex items-center justify-center py-16 px-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-10">
          <Link to="/" className="inline-flex items-center gap-2 text-[17px] font-semibold text-text-primary mb-4">
            <span className="w-8 h-8 bg-accent text-white rounded-xl flex items-center justify-center text-sm font-bold">
              N
            </span>
            NovelTrans
          </Link>
          <h1 className="text-[28px] font-semibold text-text-primary tracking-heading mb-2">欢迎回来</h1>
          <p className="text-text-secondary text-[15px]">登录您的账号继续使用</p>
        </div>
        <Card>
          <div className="p-6">
            <LoginForm />
          </div>
        </Card>
      </div>
    </div>
  );
}

export { LoginPage };
