import { Link } from 'react-router-dom';
import { RegisterForm } from '../components/features/RegisterForm';
import { Card } from '../components/ui/Card';

function RegisterPage() {
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
          <h1 className="text-[28px] font-semibold text-text-primary tracking-heading mb-2">创建账号</h1>
          <p className="text-text-secondary text-[15px]">注册即可开始使用翻译服务</p>
        </div>
        <Card>
          <div className="p-6">
            <RegisterForm />
          </div>
        </Card>
      </div>
    </div>
  );
}

export { RegisterPage };
