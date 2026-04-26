import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

function Footer() {
  const { t } = useTranslation();
  return (
    <footer className="bg-surface-secondary dark:bg-gray-50 border-t border-divider dark:border-border">
      <div className="max-w-7xl mx-auto w-full px-6 py-8">
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-x-8 gap-y-6">
          <div className="text-center">
            <h4 className="text-[12px] font-semibold text-text-primary mb-3">{t('footer.product.title')}</h4>
            <ul className="space-y-2.5">
              <li><Link to="/" className="text-[12px] text-text-tertiary hover:text-text-primary transition-colors">{t('footer.product.textTranslation')}</Link></li>
              <li><Link to="/documents" className="text-[12px] text-text-tertiary hover:text-text-primary transition-colors">{t('footer.product.documentTranslation')}</Link></li>
              <li><Link to="/glossary" className="text-[12px] text-text-tertiary hover:text-text-primary transition-colors">{t('footer.product.glossary')}</Link></li>
              <li><Link to="/pricing" className="text-[12px] text-text-tertiary hover:text-text-primary transition-colors">{t('footer.product.pricing')}</Link></li>
            </ul>
          </div>
          <div className="text-center">
            <h4 className="text-[12px] font-semibold text-text-primary mb-3">{t('footer.support.title')}</h4>
            <ul className="space-y-2.5">
              <li><Link to="/help" className="text-[12px] text-text-tertiary hover:text-text-primary transition-colors">{t('footer.support.helpCenter')}</Link></li>
              <li><Link to="/about" className="text-[12px] text-text-tertiary hover:text-text-primary transition-colors">{t('footer.support.aboutUs')}</Link></li>
            </ul>
          </div>
          <div className="text-center">
            <h4 className="text-[12px] font-semibold text-text-primary mb-3">{t('footer.legal.title')}</h4>
            <ul className="space-y-2.5">
              <li><Link to="/privacy" className="text-[12px] text-text-tertiary hover:text-text-primary transition-colors">{t('footer.legal.privacyPolicy')}</Link></li>
              <li><Link to="/terms" className="text-[12px] text-text-tertiary hover:text-text-primary transition-colors">{t('footer.legal.termsOfService')}</Link></li>
            </ul>
          </div>
          <div className="text-center">
            <h4 className="text-[12px] font-semibold text-text-primary mb-3">{t('footer.account.title')}</h4>
            <ul className="space-y-2.5">
              <li><Link to="/register" className="text-[12px] text-text-tertiary hover:text-text-primary transition-colors">{t('footer.account.register')}</Link></li>
              <li><Link to="/login" className="text-[12px] text-text-tertiary hover:text-text-primary transition-colors">{t('footer.account.login')}</Link></li>
            </ul>
          </div>
        </div>
      </div>
      <div className="px-6 py-4 border-t border-divider dark:border-border">
        <div className="w-full flex flex-col sm:flex-row items-center justify-between gap-2">
          <p className="text-[12px] text-text-tertiary">&copy; {new Date().getFullYear()} NovelTrans. All rights reserved.</p>
          <p className="text-[12px] text-text-tertiary">{t('footer.slogan')}</p>
        </div>
      </div>
    </footer>
  );
}

export { Footer };
