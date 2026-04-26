import { ForgotPasswordForm } from '../components/features/ForgotPasswordForm';
import { useTranslation } from 'react-i18next';

function ForgotPasswordPage() {
  const { t } = useTranslation();
  return (
    <div className="flex items-center justify-center py-16 px-4">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <h1 className="text-[24px] font-semibold text-text-primary mb-1">{t('forgotPassword.title')}</h1>
          <p className="text-text-secondary text-[14px]">{t('forgotPassword.subtitle')}</p>
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
