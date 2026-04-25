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
  return (
    <PageWrapper title="关于 NovelTrans" subtitle="AI 驱动的文档翻译平台">
      <p>NovelTrans 是一个基于 AI 的智能翻译平台，专注于为小说、文档等长文本内容提供高质量的翻译服务。</p>
      <h3 className="text-text-primary font-semibold text-[15px] mt-6">核心功能</h3>
      <ul className="list-disc list-inside space-y-2">
        <li>文本翻译 — 即时双栏对比翻译</li>
        <li>文档翻译 — 支持 TXT、EPUB、DOCX、PDF</li>
        <li>术语管理 — 自定义专业术语表</li>
        <li>翻译历史 — 完整的翻译记录管理</li>
        <li>API 服务 — 为开发者提供 RESTful API</li>
      </ul>
      <h3 className="text-text-primary font-semibold text-[15px] mt-6">技术架构</h3>
      <p>采用 Spring Boot 后端 + Python 翻译微服务架构，支持多引擎智能切换（Google、DeepL、百度等），确保翻译质量和稳定性。</p>
    </PageWrapper>
  );
}

function HelpPage() {
  const faqs = [
    { q: '如何使用文本翻译？', a: '在翻译主页输入源文本，选择目标语言，点击"翻译"按钮即可获取翻译结果。' },
    { q: '支持哪些文档格式？', a: '目前支持 TXT、EPUB、DOCX、PDF 格式，单个文件最大 50MB。' },
    { q: '如何管理翻译历史？', a: '在"翻译历史"页面可以查看、搜索、删除您的所有翻译记录。' },
    { q: '如何创建 API Key？', a: '在个人中心的 "API Keys" 选项卡中点击"创建"即可生成新的 API Key。' },
    { q: '免费用户有什么限制？', a: '免费用户有每日翻译次数和文件大小限制，升级套餐可获得更高配额。' },
  ];
  return (
    <PageWrapper title="帮助中心" subtitle="常见问题解答">
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
  return (
    <PageWrapper title="隐私政策" subtitle="最后更新: 2026年4月20日">
      <p>NovelTrans 重视并保护用户隐私。本政策说明我们如何收集、使用和存储您的信息。</p>
      <h3 className="text-text-primary font-semibold text-[15px] mt-4">信息收集</h3>
      <p>我们仅收集提供服务所必需的信息，包括注册邮箱、翻译记录和账户设置。</p>
      <h3 className="text-text-primary font-semibold text-[15px] mt-4">信息使用</h3>
      <p>您的翻译数据仅用于提供翻译服务，不会与第三方共享。</p>
      <h3 className="text-text-primary font-semibold text-[15px] mt-4">数据安全</h3>
      <p>我们采用加密传输和存储技术保护您的数据安全。</p>
    </PageWrapper>
  );
}

function TermsPage() {
  return (
    <PageWrapper title="服务条款" subtitle="最后更新: 2026年4月20日">
      <p>使用 NovelTrans 服务即表示您同意以下条款。</p>
      <h3 className="text-text-primary font-semibold text-[15px] mt-4">服务使用</h3>
      <p>您可使用我们的服务进行文本和文档翻译。请勿将服务用于违法目的。</p>
      <h3 className="text-text-primary font-semibold text-[15px] mt-4">账户责任</h3>
      <p>您有责任保护账户安全，不得共享账户凭证。</p>
      <h3 className="text-text-primary font-semibold text-[15px] mt-4">知识产权</h3>
      <p>您上传的内容的知识产权归您所有。我们不会将您的内容用于翻译以外的目的。</p>
    </PageWrapper>
  );
}

function NotFoundPage() {
  return (
    <div className="flex items-center justify-center py-24">
      <div className="text-center">
        <h1 className="text-[48px] font-semibold text-text-primary mb-4">404</h1>
        <p className="text-text-secondary text-[16px] mb-6">页面未找到</p>
        <a href="/" className="inline-flex items-center px-5 py-2 text-[13px] font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-colors">
          返回首页
        </a>
      </div>
    </div>
  );
}

export { AboutPage, HelpPage, PrivacyPage, TermsPage, NotFoundPage };
