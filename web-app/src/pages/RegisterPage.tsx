import { RegisterForm } from '../components/features/RegisterForm';

function RegisterPage() {
  return (
    <div className="flex items-center justify-center py-16 px-4">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <h1 className="text-[24px] font-semibold text-text-primary mb-1">创建账号</h1>
          <p className="text-text-secondary text-[14px]">注册即可开始使用翻译服务</p>
        </div>
        <div className="border border-border/50 rounded-lg overflow-hidden">
          <div className="p-6">
            <RegisterForm />
          </div>
        </div>
      </div>
    </div>
  );
}

export { RegisterPage };
