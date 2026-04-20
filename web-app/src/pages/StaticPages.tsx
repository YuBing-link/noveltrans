import { PageLayout } from '../components/layout/PageLayout';
import { Card } from '../components/ui/Card';

function AboutPage() {
  return (
    <PageLayout className="py-8 min-h-[calc(100vh-3.5rem)]">
      <div className="max-w-2xl mx-auto">
        <div className="mb-8 text-center">
          <h1 className="text-3xl sm:text-4xl font-semibold text-text-primary tracking-heading mb-2">关于 NovelTrans</h1>
          <p className="text-text-secondary text-base">AI 驱动的文档翻译平台</p>
        </div>
        <Card>
          <div className="prose prose-sm max-w-none text-text-secondary space-y-4">
            <p>NovelTrans 是一个基于 AI 的智能翻译平台，专注于为小说、文档等长文本内容提供高质量的翻译服务。</p>
            <h3 className="text-text-primary font-semibold text-base mt-6">核心功能</h3>
            <ul className="list-disc list-inside space-y-2">
              <li>文本翻译 — 即时双栏对比翻译</li>
              <li>文档翻译 — 支持 TXT、EPUB、DOCX、PDF</li>
              <li>术语管理 — 自定义专业术语表</li>
              <li>翻译历史 — 完整的翻译记录管理</li>
              <li>API 服务 — 为开发者提供 RESTful API</li>
            </ul>
            <h3 className="text-text-primary font-semibold text-base mt-6">技术架构</h3>
            <p>采用 Spring Boot 后端 + Python 翻译微服务架构，支持多引擎智能切换（Google、DeepL、百度等），确保翻译质量和稳定性。</p>
          </div>
        </Card>
      </div>
    </PageLayout>
  );
}

function HelpPage() {
  return (
    <PageLayout className="py-8 min-h-[calc(100vh-3.5rem)]">
      <div className="max-w-2xl mx-auto">
        <div className="mb-8 text-center">
          <h1 className="text-3xl sm:text-4xl font-semibold text-text-primary tracking-heading mb-2">帮助中心</h1>
          <p className="text-text-secondary text-base">常见问题解答</p>
        </div>
        <div className="space-y-4">
          {[
            { q: '如何使用文本翻译？', a: '在翻译主页输入源文本，选择目标语言，点击"翻译"按钮即可获取翻译结果。' },
            { q: '支持哪些文档格式？', a: '目前支持 TXT、EPUB、DOCX、PDF 格式，单个文件最大 50MB。' },
            { q: '如何管理翻译历史？', a: '在"翻译历史"页面可以查看、搜索、删除您的所有翻译记录。' },
            { q: '如何创建 API Key？', a: '在个人中心的 "API Keys" 选项卡中点击"创建"即可生成新的 API Key。' },
            { q: '免费用户有什么限制？', a: '免费用户有每日翻译次数和文件大小限制，升级套餐可获得更高配额。' },
          ].map((item, i) => (
            <Card key={i} variant="subtle">
              <h3 className="text-sm font-semibold text-text-primary mb-2">{item.q}</h3>
              <p className="text-sm text-text-secondary">{item.a}</p>
            </Card>
          ))}
        </div>
      </div>
    </PageLayout>
  );
}

function PrivacyPage() {
  return (
    <PageLayout className="py-8 min-h-[calc(100vh-3.5rem)]">
      <div className="max-w-2xl mx-auto">
        <div className="mb-8 text-center">
          <h1 className="text-3xl sm:text-4xl font-semibold text-text-primary tracking-heading mb-2">隐私政策</h1>
          <p className="text-xs text-text-muted">最后更新: 2026年4月20日</p>
        </div>
        <Card>
          <div className="prose prose-sm max-w-none text-text-secondary space-y-4">
            <p>NovelTrans 重视并保护用户隐私。本政策说明我们如何收集、使用和存储您的信息。</p>
            <h3 className="text-text-primary font-semibold">信息收集</h3>
            <p>我们仅收集提供服务所必需的信息，包括注册邮箱、翻译记录和账户设置。</p>
            <h3 className="text-text-primary font-semibold">信息使用</h3>
            <p>您的翻译数据仅用于提供翻译服务，不会与第三方共享。</p>
            <h3 className="text-text-primary font-semibold">数据安全</h3>
            <p>我们采用加密传输和存储技术保护您的数据安全。</p>
          </div>
        </Card>
      </div>
    </PageLayout>
  );
}

function TermsPage() {
  return (
    <PageLayout className="py-8 min-h-[calc(100vh-3.5rem)]">
      <div className="max-w-2xl mx-auto">
        <div className="mb-8 text-center">
          <h1 className="text-3xl sm:text-4xl font-semibold text-text-primary tracking-heading mb-2">服务条款</h1>
          <p className="text-xs text-text-muted">最后更新: 2026年4月20日</p>
        </div>
        <Card>
          <div className="prose prose-sm max-w-none text-text-secondary space-y-4">
            <p>使用 NovelTrans 服务即表示您同意以下条款。</p>
            <h3 className="text-text-primary font-semibold">服务使用</h3>
            <p>您可使用我们的服务进行文本和文档翻译。请勿将服务用于违法目的。</p>
            <h3 className="text-text-primary font-semibold">账户责任</h3>
            <p>您有责任保护账户安全，不得共享账户凭证。</p>
            <h3 className="text-text-primary font-semibold">知识产权</h3>
            <p>您上传的内容的知识产权归您所有。我们不会将您的内容用于翻译以外的目的。</p>
          </div>
        </Card>
      </div>
    </PageLayout>
  );
}

function NotFoundPage() {
  return (
    <PageLayout className="py-16 min-h-[calc(100vh-3.5rem)] flex items-center justify-center">
      <div className="text-center">
        <h1 className="text-6xl font-semibold text-text-primary tracking-heading mb-4">404</h1>
        <p className="text-text-secondary text-lg mb-6">页面未找到</p>
        <a href="/" className="inline-flex items-center px-6 py-2 rounded-button bg-text-primary text-white text-sm font-medium hover:bg-gray-900 transition-colors dark:bg-white dark:text-text-primary dark:hover:bg-gray-100">
          返回首页
        </a>
      </div>
    </PageLayout>
  );
}

export { AboutPage, HelpPage, PrivacyPage, TermsPage, NotFoundPage };
