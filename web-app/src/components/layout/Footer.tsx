import { Link } from 'react-router-dom';

function Footer() {
  return (
    <footer className="bg-surface-secondary dark:bg-gray-50 border-t border-divider dark:border-border">
      <div style={{ width: '92%', maxWidth: '1440px', margin: '0 auto' }} className="px-6 py-8">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-8">
          <div>
            <h4 className="text-[12px] font-semibold text-text-primary mb-3">产品</h4>
            <ul className="space-y-2.5">
              <li><Link to="/" className="text-[12px] text-text-tertiary hover:text-text-primary transition-colors">文本翻译</Link></li>
              <li><Link to="/documents" className="text-[12px] text-text-tertiary hover:text-text-primary transition-colors">文档翻译</Link></li>
              <li><Link to="/glossary" className="text-[12px] text-text-tertiary hover:text-text-primary transition-colors">术语管理</Link></li>
            </ul>
          </div>
          <div>
            <h4 className="text-[12px] font-semibold text-text-primary mb-3">支持</h4>
            <ul className="space-y-2.5">
              <li><Link to="/help" className="text-[12px] text-text-tertiary hover:text-text-primary transition-colors">帮助中心</Link></li>
              <li><Link to="/about" className="text-[12px] text-text-tertiary hover:text-text-primary transition-colors">关于我们</Link></li>
            </ul>
          </div>
          <div>
            <h4 className="text-[12px] font-semibold text-text-primary mb-3">法律</h4>
            <ul className="space-y-2.5">
              <li><Link to="/privacy" className="text-[12px] text-text-tertiary hover:text-text-primary transition-colors">隐私政策</Link></li>
              <li><Link to="/terms" className="text-[12px] text-text-tertiary hover:text-text-primary transition-colors">服务条款</Link></li>
            </ul>
          </div>
          <div>
            <h4 className="text-[12px] font-semibold text-text-primary mb-3">账户</h4>
            <ul className="space-y-2.5">
              <li><Link to="/register" className="text-[12px] text-text-tertiary hover:text-text-primary transition-colors">注册</Link></li>
            </ul>
          </div>
        </div>
        <div className="mt-8 pt-6 border-t border-divider dark:border-border flex flex-col sm:flex-row items-center justify-between gap-2">
          <p className="text-[12px] text-text-tertiary">&copy; {new Date().getFullYear()} NovelTrans. All rights reserved.</p>
          <p className="text-[12px] text-text-tertiary">智能翻译，让语言不再是障碍</p>
        </div>
      </div>
    </footer>
  );
}

export { Footer };
