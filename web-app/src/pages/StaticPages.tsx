import { useTranslation } from 'react-i18next';

function PageWrapper({ title, subtitle, children }: { title: string; subtitle?: string; children: React.ReactNode }) {
  return (
    <div className="px-0" style={{ maxWidth: '80rem' }}>
      <div className="border border-border/50 rounded-lg overflow-hidden">
        <div className="px-5 py-4 border-b border-border/50">
          <h1 className="text-[18px] font-semibold text-text-primary">{title}</h1>
          {subtitle && <p className="text-[13px] text-text-tertiary mt-1">{subtitle}</p>}
        </div>
        <div className="px-5 py-6 text-[14px] text-text-secondary leading-relaxed space-y-4">
          {children}
        </div>
      </div>
    </div>
  );
}

function AboutPage() {
  const { t } = useTranslation();
  return (
    <PageWrapper title={t('static.about.title')} subtitle={t('static.about.subtitle')}>
      <p>{t('static.about.description')}</p>
      <h3 className="text-text-primary font-semibold text-[15px] mt-6">{t('static.features.title')}</h3>
      <ul className="list-disc list-inside space-y-2">
        <li>{t('static.features.textTranslation')}</li>
        <li>{t('static.features.documentTranslation')}</li>
        <li>{t('static.features.glossary')}</li>
        <li>{t('static.features.history')}</li>
        <li>{t('static.features.api')}</li>
      </ul>
      <h3 className="text-text-primary font-semibold text-[15px] mt-6">{t('static.tech.title')}</h3>
      <p>{t('static.about.techDescription')}</p>
    </PageWrapper>
  );
}

function HelpPage() {
  const { t } = useTranslation();
  const faqs = [
    { q: t('static.help.howToTranslateText'), a: t('static.help.howToTranslateTextAnswer') },
    { q: t('static.help.supportedFormats'), a: t('static.help.supportedFormatsAnswer') },
    { q: t('static.help.manageHistory'), a: t('static.help.manageHistoryAnswer') },
    { q: t('static.help.createApiKey'), a: t('static.help.createApiKeyAnswer') },
    { q: t('static.help.freeLimitations'), a: t('static.help.freeLimitationsAnswer') },
  ];
  return (
    <PageWrapper title={t('static.help.title')} subtitle={t('static.help.faq')}>
      <div className="space-y-4">
        {faqs.map((item, i) => (
          <div key={i} className="border border-border/50 rounded-lg p-4">
            <h3 className="text-[14px] font-semibold text-text-primary mb-2">{item.q}</h3>
            <p className="text-[13px] text-text-tertiary">{item.a}</p>
          </div>
        ))}
      </div>
    </PageWrapper>
  );
}

function PrivacyPage() {
  const { t } = useTranslation();
  return (
    <PageWrapper title={t('static.privacy.title')} subtitle={`${t('static.privacy.lastUpdated')}: 2026-04-20`}>
      <p>{t('static.privacy.intro')}</p>
      <h3 className="text-text-primary font-semibold text-[15px] mt-4">{t('static.privacy.dataCollection')}</h3>
      <p>{t('static.privacy.dataCollectionDesc')}</p>
      <h3 className="text-text-primary font-semibold text-[15px] mt-4">{t('static.privacy.dataUsage')}</h3>
      <p>{t('static.privacy.dataUsageDesc')}</p>
      <h3 className="text-text-primary font-semibold text-[15px] mt-4">{t('static.privacy.dataSecurity')}</h3>
      <p>{t('static.privacy.dataSecurityDesc')}</p>
    </PageWrapper>
  );
}

function TermsPage() {
  const { t } = useTranslation();
  return (
    <PageWrapper title={t('static.terms.title')} subtitle={`${t('static.terms.lastUpdated')}: 2026-04-20`}>
      <p>{t('static.terms.intro')}</p>
      <h3 className="text-text-primary font-semibold text-[15px] mt-4">{t('static.terms.serviceUsage')}</h3>
      <p>{t('static.terms.serviceUsageDesc')}</p>
      <h3 className="text-text-primary font-semibold text-[15px] mt-4">{t('static.terms.accountResponsibility')}</h3>
      <p>{t('static.terms.accountResponsibilityDesc')}</p>
      <h3 className="text-text-primary font-semibold text-[15px] mt-4">{t('static.terms.intellectualProperty')}</h3>
      <p>{t('static.terms.intellectualPropertyDesc')}</p>
    </PageWrapper>
  );
}

function NotFoundPage() {
  const { t } = useTranslation();
  return (
    <div className="flex items-center justify-center py-24">
      <div className="text-center">
        <h1 className="text-[48px] font-semibold text-text-primary mb-4">404</h1>
        <p className="text-text-secondary text-[16px] mb-6">{t('static.notFound.title')}</p>
        <a href="/" className="inline-flex items-center px-5 py-2 text-[13px] font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-colors">
          {t('static.notFound.backToHome')}
        </a>
      </div>
    </div>
  );
}

export { AboutPage, HelpPage, PrivacyPage, TermsPage, NotFoundPage };
