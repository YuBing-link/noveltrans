import { ForgotPasswordForm } from '../components/features/ForgotPasswordForm';

function ForgotPasswordPage() {
  return (
    <div className="flex items-center justify-center py-16 px-4">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <h1 className="text-[24px] font-semibold text-text-primary mb-1">重置密码</h1>
          <p className="text-text-secondary text-[14px]">通过邮箱验证重置您的密码</p>
        </div>
        <div className="border border-border/50 rounded-lg overflow-hidden">
          <div className="p-6">
            <ForgotPasswordForm />
          </div>
        </div>
      </div>
    </div>
  );
}

export { ForgotPasswordPage };
