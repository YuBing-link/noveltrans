import { LoginForm } from '../components/features/LoginForm';

function LoginPage() {
  return (
    <div className="flex items-center justify-center py-16 px-4">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <h1 className="text-[24px] font-semibold text-text-primary mb-1">欢迎回来</h1>
          <p className="text-text-secondary text-[14px]">登录您的账号继续使用</p>
        </div>
        <div className="border border-border/50 rounded-lg overflow-hidden">
          <div className="p-6">
            <LoginForm />
          </div>
        </div>
      </div>
    </div>
  );
}

export { LoginPage };
