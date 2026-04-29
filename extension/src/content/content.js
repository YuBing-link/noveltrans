// content.js - 网页翻译核心逻辑（模式1: 整个网页翻译）
// 包含 DOMWalker、TextRegistry、TranslationApplier、MutationHandler 模块

// 添加通知动画样式
(function() {
  const style = document.createElement('style');
  style.textContent = `
    @keyframes nt-slideInRight {
      from {
        transform: translateX(100%);
        opacity: 0;
      }
      to {
        transform: translateX(0);
        opacity: 1;
      }
    }
    @keyframes nt-slideOutRight {
      from {
        transform: translateX(0);
        opacity: 1;
      }
      to {
        transform: translateX(100%);
        opacity: 0;
      }
    }
    @keyframes fadeIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }
    @keyframes fadeOut {
      from { opacity: 1; }
      to { opacity: 0; }
    }

    /* 双语模式样式 - 确保换行正确显示 */
    .extreme-bilingual-text {
      white-space: pre-line !important; /* 保留换行符 */
      line-height: 1.8 !important;
    }


    /* 翻译进度条 - 页面顶部蓝紫色进度条 */
    @keyframes nt-progressSlideDown {
      from {
        transform: translateY(-100%);
      }
      to {
        transform: translateY(0);
      }
    }
    @keyframes nt-progressFadeOut {
      from {
        opacity: 1;
      }
      to {
        opacity: 0;
      }
    }
    @keyframes nt-progressGlow {
      0%, 100% {
        box-shadow: 0 2px 10px rgba(102, 126, 234, 0.5);
      }
      50% {
        box-shadow: 0 2px 20px rgba(102, 126, 234, 0.8);
      }
    }

    /* 翻译进度条容器 */
    #extreme-translation-progress-bar {
      position: fixed !important;
      top: 0 !important;
      left: 0 !important;
      width: 100% !important;
      height: 4px !important;
      background: linear-gradient(90deg, rgba(30, 30, 46, 0.95) 0%, rgba(40, 40, 60, 0.95) 100%) !important;
      z-index: 2147483647 !important;
      display: none;
      animation: nt-progressSlideDown 0.3s ease-out;
    }

    /* 进度条填充 */
    #extreme-translation-progress-bar .progress-fill {
      height: 100% !important;
      width: 0% !important;
      background: linear-gradient(90deg, #667eea 0%, #764ba2 50%, #f093fb 100%) !important;
      background-size: 200% 100% !important;
      animation: nt-progressGlow 1.5s ease-in-out infinite, nt-shimmerProgress 1s linear infinite !important;
      transition: width 0.2s ease-out !important;
      position: relative !important;
      overflow: hidden !important;
    }

    /* 进度条光泽动画 */
    @keyframes nt-shimmerProgress {
      0% {
        background-position: -200% 0;
      }
      100% {
        background-position: 200% 0;
      }
    }

    /* 进度条文本标签 */
    #extreme-translation-progress-bar .progress-label {
      position: absolute !important;
      right: 10px !important;
      top: 50% !important;
      transform: translateY(-50%) !important;
      color: #fff !important;
      font-size: 11px !important;
      font-weight: 600 !important;
      text-shadow: 0 1px 3px rgba(0, 0, 0, 0.3) !important;
      z-index: 1 !important;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif !important;
      white-space: nowrap !important;
    }

    /* 进度条完成状态 */
    #extreme-translation-progress-bar.completed {
      animation: nt-progressFadeOut 0.4s ease-out 0.5s forwards;
    }

    #extreme-translation-progress-bar.completed .progress-fill {
      background: linear-gradient(90deg, #11998e 0%, #38ef7d 100%);
      animation: none;
    }

    /* 阅读模式双语元素 - 已有样式，不需要重复定义 */
  `;
  document.head.appendChild(style);
})();

// 全局变量
let translationOverlay = null;
let isTranslationActive = false;
let originalTextMap = new Map();
let mutationObserver = null;


// DOMWalker 模块 - 智能DOM遍历和节点过滤
class DOMWalker {
  constructor() {
    this.defaultFilter = this.createDefaultFilter();
    this.aggressiveFilter = this.createAggressiveFilter();
    this.conservativeFilter = this.createConservativeFilter();
  }

  // 创建默认过滤器（宽松版 - 减少过滤规则以识别更多内容）
  createDefaultFilter() {
    return {
      acceptNode: function(node) {
        // 跳过空文本节点
        if (!node.textContent.trim()) {
          return NodeFilter.FILTER_REJECT;
        }

        // 获取父元素进行检查
        const parent = node.parentElement;
        if (!parent) return NodeFilter.FILTER_REJECT;

        // 跳过脚本、样式、注释等节点
        if (parent.tagName === 'SCRIPT' ||
            parent.tagName === 'STYLE' ||
            parent.tagName === 'NOSCRIPT' ||
            parent.tagName === 'TEMPLATE' ||
            parent.tagName === 'META' ||
            parent.tagName === 'LINK' ||
            parent.tagName === 'TITLE') {
          return NodeFilter.FILTER_REJECT;
        }

        // 跳过已翻译的内容
        if (parent.classList.contains('extreme-translated') ||
            parent.hasAttribute('data-translation-ignore')) {
          return NodeFilter.FILTER_REJECT;
        }

        // 跳过密码输入框等敏感元素
        if (parent.tagName === 'INPUT' &&
            (parent.type === 'password' || parent.type === 'hidden')) {
          return NodeFilter.FILTER_REJECT;
        }

        // 只过滤明显的广告元素（使用简化版检查）
        if (this.isObviousAd(parent)) {
          return NodeFilter.FILTER_REJECT;
        }

        // 检查是否为可见文本（使用宽松版检查）
        if (!this.isVisibleTextLoose(parent)) {
          return NodeFilter.FILTER_REJECT;
        }

        return NodeFilter.FILTER_ACCEPT;
      }.bind(this)
    };
  }

  // 创建激进过滤器
  createAggressiveFilter() {
    return {
      acceptNode: function(node) {
        // 基本空值检查
        if (!node.textContent.trim()) {
          return NodeFilter.FILTER_REJECT;
        }

        const parent = node.parentElement;
        if (!parent) return NodeFilter.FILTER_REJECT;

        // 严格的排除规则
        const excludedTags = ['SCRIPT', 'STYLE', 'NOSCRIPT', 'TEMPLATE', 'META', 'LINK', 'TITLE', 'svg'];
        if (excludedTags.includes(parent.tagName)) {
          return NodeFilter.FILTER_REJECT;
        }

        // 排除特定CSS选择器的元素
        const excludedSelectors = [
          '[aria-hidden="true"]',
          '[data-translation-ignore]',
          '.hidden', // 常见隐藏类
          '[style*="display:none"]',
          '[style*="visibility:hidden"]'
        ];

        if (excludedSelectors.some(selector => parent.matches && parent.matches(selector))) {
          return NodeFilter.FILTER_REJECT;
        }

        // 只过滤明显的广告元素
        if (this.isObviousAd(parent)) {
          return NodeFilter.FILTER_REJECT;
        }

        // 跳过已翻译的内容
        if (parent.classList.contains('extreme-translated')) {
          return NodeFilter.FILTER_REJECT;
        }

        return NodeFilter.FILTER_ACCEPT;
      }.bind(this)
    };
  }

  // 创建保守过滤器
  createConservativeFilter() {
    return {
      acceptNode: function(node) {
        // 基本空值检查
        if (!node.textContent.trim()) {
          return NodeFilter.FILTER_REJECT;
        }

        const parent = node.parentElement;
        if (!parent) return NodeFilter.FILTER_REJECT;

        // 只允许最安全的文本类型
        const allowedTags = ['P', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6', 'SPAN', 'DIV', 'LI', 'TD', 'TH', 'A', 'LABEL', 'BUTTON'];

        if (!allowedTags.includes(parent.tagName)) {
          return NodeFilter.FILTER_REJECT;
        }

        // 排除所有不安全的元素
        const excludedSelectors = [
          '[data-translation-ignore]',
          '[aria-label]', // 避免ARIA标签
          '[role="button"]',
          '[href^="javascript:"]',
          '[onclick]',
          '[onkeypress]',
          '[onkeyup]',
          '[onkeydown]'
        ];

        if (excludedSelectors.some(selector => parent.matches && parent.matches(selector))) {
          return NodeFilter.FILTER_REJECT;
        }

        // 只过滤明显的广告元素
        if (this.isObviousAd(parent)) {
          return NodeFilter.FILTER_REJECT;
        }

        // 确保是可见的（使用宽松版检查）
        if (!this.isVisibleTextLoose(parent)) {
          return NodeFilter.FILTER_REJECT;
        }

        return NodeFilter.FILTER_ACCEPT;
      }.bind(this)
    };
  }

  // 检查文本是否可见（原版 - 保留向后兼容）
  isVisibleText(element) {
    if (!element.offsetParent && !element.offsetHeight && !element.offsetWidth) {
      return false;
    }

    const style = window.getComputedStyle(element);
    if (style.display === 'none' ||
        style.visibility === 'hidden' ||
        style.opacity === '0' ||
        parseInt(style.fontSize) === 0) {
      return false;
    }

    return true;
  }

  // 检查文本是否可见（宽松版 - 修复 offsetParent 和 fontSize 问题）
  isVisibleTextLoose(element) {
    // 修复：使用更宽松的检查，避免过滤掉 position: fixed 元素
    const hasSize = element.offsetHeight > 0 || element.offsetWidth > 0;
    if (!hasSize) {
      // 即使没有 offsetParent，只要有尺寸就认为是可见的
      if (!element.offsetParent) {
        // 检查是否是 fixed/sticky 定位
        const style = window.getComputedStyle(element);
        if (style.position === 'fixed' || style.position === 'sticky') {
          // 定位元素可能是可见的
        } else {
          return false;
        }
      } else {
        return false;
      }
    }

    const style = window.getComputedStyle(element);
    if (style.display === 'none' ||
        style.visibility === 'hidden' ||
        style.opacity === '0') {
      return false;
    }

    // 移除 fontSize === 0 的检查，因为某些特殊布局可能使用 0 字体但仍有内容
    return true;
  }

  // 检查是否为明显的广告/非文章内容元素
  isObviousAd(element) {
    const className = String(element.className?.baseVal || element.getAttribute?.('class') || element.className || '').toLowerCase();
    const id = (element.id || '').toLowerCase();

    // 广告选择器
    const obviousAdSelectors = [
      '.ad', '.ads', '.advert', '.advertisement',
      '.banner-ads', '.ad-banner', '.ad-container',
      '[data-ad]', '[data-ad-slot]', '[data-ad-unit]'
    ];

    // 检查是否匹配明显的广告选择器
    for (const selector of obviousAdSelectors) {
      if (selector.startsWith('.')) {
        if (className.includes(selector.substring(1))) return true;
      } else if (selector.startsWith('#')) {
        if (id.includes(selector.substring(1))) return true;
      } else if (selector.startsWith('[')) {
        if (element.hasAttribute(selector.replace(/[\[\]]/g, '').split('=')[0])) return true;
      }
    }

    // 检查明显的广告属性
    if (element.getAttribute('data-ad-slot') ||
        element.getAttribute('data-ad-unit') ||
        element.getAttribute('data-sponsored') ||
        element.getAttribute('data-promoted')) {
      return true;
    }

    // 过滤 CAPTCHA / 验证元素
    if (className.includes('captcha') || className.includes('recaptcha') ||
        className.includes('h-captcha') || className.includes('g-recaptcha') ||
        id.includes('captcha') || id.includes('recaptcha') ||
        element.closest('.captcha-box, .g-recaptcha, .h-captcha, .captcha-container')) {
      return true;
    }

    // 过滤社交分享组件（AddThis、ShareThis 等）
    if (className.includes('addthis') || className.includes('sharethis') ||
        className.includes('addtoany') || className.includes('social-share') ||
        className.includes('share-button') || className.includes('share-tools') ||
        id.includes('addthis') || id.includes('sharethis') ||
        element.closest('.addthis_toolbox, .sharethis-buttons, .social-share')) {
      return true;
    }

    // 过滤登录/注册弹窗
    if (className.includes('login') && (className.includes('modal') || className.includes('popup') || className.includes('overlay')) ||
        className.includes('signup') && (className.includes('modal') || className.includes('popup')) ||
        id.includes('login-modal') || id.includes('auth-popup')) {
      return true;
    }

    // 过滤固定悬浮/覆盖层元素（非文章内容）
    const position = window.getComputedStyle(element).position;
    if ((position === 'fixed' || position === 'sticky') &&
        (className.includes('cookie') || className.includes('banner') || className.includes('notification-bar'))) {
      return true;
    }

    // 跳过表单、输入框等交互元素
    const tagName = element.tagName.toLowerCase();
    if (tagName === 'input' || tagName === 'textarea' || tagName === 'select' ||
        tagName === 'option' || tagName === 'optgroup') {
      return false;
    }

    // 跳过装饰性元素
    const role = (element.getAttribute('role') || '').toLowerCase();
    if (role === 'presentation' || role === 'none' || role === 'decorative') {
      return false;
    }

    return false;
  }

  // 检查是否为广告或无用内容（增强版 - 保留向后兼容）
  isUselessContent(element) {
    // 广告相关关键词（类名、ID、属性）
    const adKeywords = [
      'ad', 'ads', 'advert', 'advertisement', 'sponsor', 'sponsorship',
      'banner', 'popup', 'newsletter', 'subscribe', 'recommend', 'related',
      'comment', 'footer-link', 'nav-link', 'sidebar-widget', 'widget',
      'promo', 'promotion', 'discount', 'sale', 'deal', 'offer', 'coupon',
      'shopping', 'cart', 'checkout', 'login', 'signup', 'register',
      'search', 'breadcrumb', 'tags', 'category', 'archive', 'pagination',
      'cookie', 'gdpr', 'privacy', 'terms', 'policy', 'legal', 'copyright',
      'analytics', 'tracker', 'affiliate', 'external-link', 'nofollow',
      'cta', 'button', 'btn', 'icon', 'img', 'picture', 'video', 'audio',
      'decorative', 'spacer', 'divider', 'loading', 'spinner', 'placeholder'
    ];

    // 主体内容相关关键词（这些应该被保留 - 扩展版）
    const contentKeywords = [
      'article', 'post', 'content', 'main', 'entry', 'story', 'news',
      'blog', 'page', 'body', 'text', 'prose', 'reading', 'content-body',
      'article-body', 'post-body', 'entry-content', 'article-content',
      'main-content', 'primary-content', 'content-area', 'post-content',
      'story-content', 'news-content', 'chapter-content', 'read-content',
      'detail-content', 'item-content', 'thread-content', 'doc-content'
    ];

    // 导航/侧边栏/页脚相关关键词（这些应该被过滤）
    const navigationKeywords = [
      'nav', 'menu', 'sidebar', 'widget', 'footer', 'header', 'social',
      'aside', 'complementary', 'navigation', 'breadcrumb', 'pagination',
      'page-nav', 'post-nav', 'category', 'tag-cloud', 'archive', 'calendar'
    ];

    // 检查类名
    const className = (element.className || '').toLowerCase();
    const id = (element.id || '').toLowerCase();

    // 首先检查是否为主体内容区域，如果是则跳过广告检查
    const isContentArea = contentKeywords.some(keyword =>
      className.includes(keyword) || id.includes(keyword)
    );

    if (isContentArea) {
      return false; // 主体内容区域不被视为无用内容
    }

    // 检查是否为导航/侧边栏/页脚区域
    const isNavigationArea = navigationKeywords.some(keyword =>
      className.includes(keyword) || id.includes(keyword)
    );

    if (isNavigationArea) {
      return true; // 导航区域直接视为无用内容
    }

    // 检查是否包含广告关键词
    if (adKeywords.some(keyword => {
      return className.includes(keyword) || id.includes(keyword);
    })) {
      return true;
    }

    // 检查常见广告属性
    if (element.getAttribute('data-ad') ||
        element.getAttribute('data-ad-slot') ||
        element.getAttribute('data-sponsored') ||
        element.getAttribute('data-promoted') ||
        element.getAttribute('data-affiliate') ||
        element.getAttribute('data-tracking') ||
        element.getAttribute('data-analytics') ||
        element.getAttribute('data-cookie') ||
        element.getAttribute('data-gdpr') ||
        element.getAttribute('data-privacy')) {
      return true;
    }

    // 检查是否为特定类型的内容
    const tagName = element.tagName.toLowerCase();
    const role = (element.getAttribute('role') || '').toLowerCase();

    // 跳过表单、输入框、按钮等交互元素（除非是可见的按钮文本）
    if (tagName === 'input' || tagName === 'textarea' || tagName === 'select' ||
        tagName === 'option' || tagName === 'optgroup') {
      return true;
    }

    // 跳过装饰性图标和图片
    if (tagName === 'svg' || tagName === 'img' || tagName === 'picture' ||
        tagName === 'video' || tagName === 'audio' || tagName === 'canvas' ||
        tagName === 'iframe' || tagName === 'embed' || tagName === 'object') {
      return true;
    }

    // 跳过装饰性元素
    if (tagName === 'hr' || tagName === 'br' || tagName === 'wbr') {
      return true;
    }

    // 检查是否为不可见的装饰性元素
    if (role === 'presentation' || role === 'none' || role === 'decorative') {
      return true;
    }

    // 检查是否为过短的文本（可能是装饰性文本）
    const textContent = element.textContent.trim();
    if (textContent.length < 2) {
      return true;
    }

    // 检查是否为纯数字或纯符号（可能是页码、日期等）
    if (/^[\d\s\W]+$/.test(textContent) && textContent.length < 10) {
      return true;
    }

    // 检查是否为常见的广告/无用文本
    const uselessPatterns = [
      /^(广告|sponsor|sponsored|ad|ads)$/i,
      /^(推荐 |related|recommended)$/i,
      /^(订阅|subscribe|sign up|register)$/i,
      /^(登录|login|sign in)$/i,
      /^(购物车|cart|checkout)$/i,
      /^(搜索|search)$/i,
      /^(返回顶部|back to top)$/i,
      /^(下一页|next|prev|previous)$/i,
      /^(页码|page \d+)$/i,
      /^(版权|copyright|©|\u00a9)$/i,
      /^(隐私|privacy|policy|terms)$/i,
      /^(联系我们|contact)$/i,
      /^(分享|share)$/i,
      /^(点赞|like|favorite)$/i,
      /^(评论|comment)$/i,
      /^(标签|tags)$/i,
      /^(分类|category)$/i,
      /^(优惠|discount|sale|deal)$/i,
      /^(了解更多 |learn more|read more|more)$/i,
      /^(立即|now|today)$/i,
      /^(点击|click|tap)$/i,
      /^(购买|buy|shop|order)$/i
    ];

    if (uselessPatterns.some(pattern => pattern.test(textContent))) {
      return true;
    }

    return false;
  }

  // 节点评估器 - 返回重要性评分 (1-10)
  evaluateNode(node) {
    const parent = node.parentElement;
    if (!parent) return { score: 0, context: '', visibility: false };

    let score = 5; // 基础分数

    // 根据标签类型调整分数 - 按文章结构优先级评分
    const tagScores = {
      // 标题类 (最高优先级)
      'H1': 10, 'H2': 9, 'H3': 8, 'H4': 7, 'H5': 6, 'H6': 6,

      // 文章内容类 (高优先级)
      'P': 8, 'ARTICLE': 9, 'SECTION': 7, 'MAIN': 9,

      // 列表内容 (中高优先级)
      'LI': 7, 'TD': 6, 'TH': 6,

      // 一般内容 (中等优先级)
      'SPAN': 5, 'DIV': 5, 'HEADER': 4, 'FOOTER': 3,

      // 导航类 (较低优先级)
      'NAV': 2, 'UL': 3, 'MENU': 2,

      // 按钮/链接 (中等优先级，用户可能需要翻译)
      'BUTTON': 7, 'LABEL': 6, 'A': 6
    };

    if (tagScores[parent.tagName]) {
      score = tagScores[parent.tagName];
    }

    // 根据CSS类名调整分数 - 常见的文章结构类名
    const className = parent.className || '';
    const articleClassPatterns = [
      /article/i, /post/i, /content/i, /main/i, /text/i, /body/i,
      /title/i, /headline/i, /summary/i, /excerpt/i
    ];

    const navClassPatterns = [
      /nav/i, /menu/i, /sidebar/i, /widget/i, /ad/i, /banner/i,
      /footer/i, /header/i, /social/i
    ];

    if (articleClassPatterns.some(pattern => pattern.test(className))) {
      if (parent.tagName === 'H1' || parent.tagName === 'H2') {
        score = Math.max(score, 10); // 标题类文章内容
      } else {
        score = Math.max(score, 8); // 文章内容
      }
    } else if (navClassPatterns.some(pattern => pattern.test(className))) {
      score = Math.min(score, 3); // 导航类内容
    }

    // 根据ID调整分数
    const id = parent.id || '';
    if (/(title|headline|article|post|content|main)/i.test(id)) {
      score = Math.max(score, 9);
    } else if (/(nav|menu|sidebar|footer|header)/i.test(id)) {
      score = Math.min(score, 4);
    }

    // 文本长度影响分数（长文本评分更高）
    const textLength = node.textContent.trim().length;
    if (textLength < 4) {
        score = 1; // 少于4字的文本评分最低
    } else if (textLength >= 4 && textLength <= 10) {
        score = Math.max(score - 1, 2); // 4-10字：评分2-4
    } else if (textLength > 10 && textLength <= 50) {
        // 10-50字：中等评分，保持基础分或略高
        score = Math.max(score, 4);
    } else if (textLength > 50 && textLength <= 200) {
        score += 4; // 50-200字：加分4分
    } else if (textLength > 200) {
        score += 3; // 超过200字：加分3分
    }

    // 可见性检查
    const isVisible = this.isVisibleText(parent);
    if (!isVisible) score = Math.max(1, score - 3); // 可见性扣分

    // 检查是否在语义标签中
    if (parent.closest('article') || parent.closest('main')) {
      score = Math.max(score, 7); // 在文章或主内容区域
    } else if (parent.closest('nav') || parent.closest('aside')) {
      score = Math.min(score, 4); // 在导航或侧边栏区域
    }

    // 检查是否在主体内容容器内（增强版）
    const mainContainer = this.findMainContentContainer();
    if (mainContainer && mainContainer.contains(parent)) {
      score = Math.max(score, 8); // 在主体内容容器内
    }

    // 检查是否为文章内容（增强版）
    if (this.isArticleContent(parent)) {
      score = Math.max(score, 7);
    }

    // 检查是否为导航元素
    if (this.isNavigationElement(parent)) {
      score = Math.min(score, 3);
    }

    // 上下文提取
    const context = this.extractContext(node);

    // 判断是否为首段
    let isFirstParagraph = false;
    if (parent.tagName === 'P' && this.isArticleContent(parent)) {
      // 获取所有段落并检查是否为第一个
      const articleContainer = parent.closest('article, main, .article-content, .post-content, .entry-content');
      if (articleContainer) {
        const paragraphs = Array.from(articleContainer.querySelectorAll('p'));
        isFirstParagraph = paragraphs[0] === parent;
      }
    }

    return {
      score: Math.min(10, Math.max(1, score)), // 限制在1-10范围内
      context: context,
      visibility: isVisible,
      position: this.getTextPosition(node),
      metadata: {
        tagName: parent.tagName,
        className: className,
        id: id,
        isArticleContent: this.isArticleContent(parent),
        isNavigation: this.isNavigationElement(parent),
        isTitle: this.isTitleElement(parent),
        isFirstParagraph: isFirstParagraph, // 标记是否为首段
        isInMainContainer: mainContainer ? mainContainer.contains(parent) : false
      }
    };
  }

  // 判断是否为文章内容元素（增强版）
  isArticleContent(element) {
    // 主体内容相关关键词（扩展版）
    const contentPatterns = [
      /article/, /post/, /content/, /main/, /text/, /body/,
      /entry/, /story/, /news/, /blog/, /page/, /prose/,
      /entry-content/, /article-content/, /post-content/, /content-body/,
      /article-body/, /main-content/, /primary-content/, /content-area/,
      /post-body/, /story-content/, /news-content/, /chapter/, /read/,
      /detail/, /single/, /item-content/, /doc-content/, /thread-content/
    ];

    // 主体内容常见的 CSS 选择器（扩展版）
    const contentSelectors = [
      'article', 'main', 'section[role="main"]', '[role="article"]',
      '.article', '.post', '.content', '.main', '.entry', '.story',
      '#article', '#post', '#content', '#main', '#entry', '#story',
      '.article-content', '.post-content', '.entry-content', '.story-content',
      '.article-body', '.post-body', '.entry-body', '.content-body',
      '.main-content', '.primary-content', '.content-area', '.article-container',
      '.post-container', '.content-container', '.article-wrap', '.post-wrap',
      '.chapter-content', '.reading-content', '.novel-content', '.text-content',
      '[itemprop="articleBody"]', '[itemprop="articleBody"] *'
    ];

    const className = element.className || '';
    const id = element.id || '';

    // 1. 检查类名和 ID
    const isContentByClassOrId = contentPatterns.some(pattern =>
      pattern.test(className) || pattern.test(id)
    );

    // 2. 检查是否匹配主体内容选择器
    const isContentBySelector = contentSelectors.some(selector => {
      try {
        return element.matches && element.matches(selector);
      } catch (e) {
        return false;
      }
    });

    // 3. 检查是否在语义标签中（增强版）
    const isContentByTag = element.tagName === 'ARTICLE' ||
                           element.tagName === 'MAIN' ||
                           (element.tagName === 'SECTION' && element.hasAttribute('role')) ||
                           !!element.closest('article') ||
                           !!element.closest('main') ||
                           !!element.closest('[role="main"]') ||
                           !!element.closest('[itemprop="articleBody"]');

    // 4. 检查文本密度（高文本密度通常是文章内容）
    const textDensity = this.getTextDensity(element);
    const isContentByDensity = textDensity > 0.35; // 降低阈值到 0.35

    // 5. 检查是否在常见的文章容器内
    const isContentByContainer = !!element.closest('.article') ||
                                  !!element.closest('.post') ||
                                  !!element.closest('.entry') ||
                                  !!element.closest('.story');

    // 6. 检查是否为长文本内容（超过 200 字符的文本块很可能是文章内容）
    const textLength = element.textContent.trim().length;
    const isContentByLength = textLength > 200;

    // 综合判断：满足任一条件即认为是文章内容
    return isContentByClassOrId ||
           isContentBySelector ||
           isContentByTag ||
           isContentByDensity ||
           isContentByContainer ||
           isContentByLength;
  }

  // 计算文本密度（文本内容占元素总面积的比例）
  getTextDensity(element) {
    try {
      const textLength = element.textContent.trim().length;
      const rect = element.getBoundingClientRect();
      const area = rect.width * rect.height;

      if (area === 0) return 0;

      // 文本密度 = 文本长度 / 面积（归一化到 0-1）
      // 经验公式：每 100 平方像素容纳 1 个字符为理想密度
      const expectedChars = area / 100;
      const density = Math.min(1, textLength / expectedChars);

      return density;
    } catch (e) {
      return 0;
    }
  }

  // 根据元数据判断是否为导航元素
  isNavigationElementByMetadata(entry) {
    if (!entry || !entry.metadata) return false;

    // 检查是否被标记为导航
    if (entry.metadata.isNavigation) return true;

    // 检查标签名是否为导航相关
    const navTags = ['NAV', 'HEADER', 'FOOTER', 'ASIDE', 'MENU'];
    if (navTags.includes(entry.metadata.tagName)) return true;

    // 检查类名是否包含导航关键词
    const navKeywords = ['nav', 'menu', 'sidebar', 'footer', 'header', 'widget', 'social'];
    const className = entry.metadata.className || '';
    if (navKeywords.some(keyword => className.toLowerCase().includes(keyword))) {
      return true;
    }

    // 检查ID是否包含导航关键词
    const id = entry.metadata.id || '';
    if (navKeywords.some(keyword => id.toLowerCase().includes(keyword))) {
      return true;
    }

    return false;
  }

  // 判断是否为导航元素（宽松版 - 减少误判）
  isNavigationElement(element) {
    const className = element.className || '';
    const id = element.id || '';
    const tagName = element.tagName;

    // 只检查明显的导航容器标签
    if (['NAV'].includes(tagName)) {
      return true;
    }

    // 检查明显的导航类名/ID（移除 header、footer、ad 等宽泛匹配）
    const navPatterns = [
      /nav-menu/i, /main-menu/i, /sidebar-menu/i,
      /nav-container/i, /navigation-wrapper/i
    ];

    return navPatterns.some(pattern =>
      pattern.test(className) || pattern.test(id)
    );
  }

  // 判断是否为标题元素
  isTitleElement(element) {
    return ['H1', 'H2', 'H3', 'H4', 'H5', 'H6'].includes(element.tagName) ||
           /(title|headline|h\d)/i.test(element.className || '') ||
           /(title|headline|h\d)/i.test(element.id || '');
  }

  // 提取文本上下文
  extractContext(node) {
    const parent = node.parentElement;
    if (!parent) return '';

    // 获取父元素的文本内容作为上下文
    let context = parent.textContent.trim();
    const nodeText = node.textContent.trim();

    // 如果上下文太长，截取包含当前文本的部分
    if (context.length > 500) {
      const index = context.indexOf(nodeText);
      if (index !== -1) {
        const start = Math.max(0, index - 200);
        const end = Math.min(context.length, index + nodeText.length + 200);
        context = context.substring(start, end);
      } else {
        // 如果找不到确切匹配，截取前面部分
        context = context.substring(0, 500);
      }
    }

    return context;
  }

  // 获取文本位置信息
  getTextPosition(node) {
    if (!node.parentElement) return null;

    const rect = node.parentElement.getBoundingClientRect();
    return {
      x: rect.left,
      y: rect.top,
      width: rect.width,
      height: rect.height,
      visible: rect.bottom > 0 && rect.top < window.innerHeight,
      viewportRatio: Math.max(0, Math.min(1,
        (Math.min(window.innerHeight, rect.bottom) - Math.max(0, rect.top)) / rect.height
      ))
    };
  }

  // 查找主体内容容器（智能识别）
  findMainContentContainer() {
    // 1. 优先查找常见的主体内容容器选择器
    const contentSelectors = [
      'article',
      'main',
      '[role="main"]',
      '[itemprop="articleBody"]',
      '.article',
      '.post',
      '.entry',
      '.story',
      '.content',
      '.article-content',
      '.post-content',
      '.entry-content',
      '.story-content',
      '.article-body',
      '.post-body',
      '.entry-body',
      '.content-body',
      '.main-content',
      '.primary-content',
      '.content-area',
      '.article-container',
      '.post-container',
      '.content-container',
      '.chapter-content',
      '.reading-content',
      '.novel-content',
      '.text-content',
      '#article',
      '#post',
      '#entry',
      '#story',
      '#content',
      '#main',
      '#article-content',
      '#post-content',
      '#entry-content',
      '#content-body',
      '#main-content'
    ];

    for (const selector of contentSelectors) {
      const element = document.querySelector(selector);
      if (element) {
        // 验证元素是否包含足够的文本内容
        const textLength = element.textContent.trim().length;
        if (textLength > 200) { // 至少 200 字符才认为是主体内容
          return element;
        }
      }
    }

    // 2. 查找文本密度最高的容器
    let bestContainer = null;
    let bestDensity = 0;

    const containerTags = ['DIV', 'SECTION', 'ARTICLE', 'MAIN'];
    const allContainers = [];

    for (const tag of containerTags) {
      document.querySelectorAll(tag).forEach(el => {
        // 跳过太小的容器
        const rect = el.getBoundingClientRect();
        if (rect.width < 200 || rect.height < 200) return;

        // 跳过导航、侧边栏、页脚
        const className = (el.className || '').toLowerCase();
        const id = (el.id || '').toLowerCase();
        const navKeywords = ['nav', 'menu', 'sidebar', 'widget', 'footer', 'header', 'aside'];
        if (navKeywords.some(k => className.includes(k) || id.includes(k))) return;

        allContainers.push(el);
      });
    }

    for (const container of allContainers) {
      const density = this.getTextDensity(container);
      const textLength = container.textContent.trim().length;

      // 综合考虑文本密度和文本长度
      const score = density * textLength;

      if (score > bestDensity && textLength > 300) {
        bestDensity = score;
        bestContainer = container;
      }
    }

    return bestContainer;
  }

  // 按阶段收集文本（按文章结构优先级）
  collectTextByPhase(filter = this.defaultFilter, batchSize = 100) {
    const allTextNodes = [];
    const walker = document.createTreeWalker(
      document.body,
      NodeFilter.SHOW_TEXT,
      filter,
      false
    );

    let currentNode;
    while ((currentNode = walker.nextNode())) {
      if (currentNode.textContent.trim().length > 0) {
        allTextNodes.push(currentNode);
      }
    }

    // 按文章结构和重要性分组
    const titleNodes = [];        // 标题类内容 (H1-H6)
    const mainContentNodes = [];    // 主体内容 (article, main, section 等)
    const paragraphNodes = [];      // 段落内容 (P 标签)
        // 导航类内容 (nav, ul, aside等)
    const otherContentNodes = [];  // 其他内容

    // 查找主体内容容器（用于优先识别）
    const mainContainer = this.findMainContentContainer();

    allTextNodes.forEach(node => {
      const parent = node.parentElement;
      const tagName = parent.tagName;
      const textDensity = this.getTextDensity(parent);
      if (!parent) return;

      // 检查是否在主体内容容器内
      const isInMainContainer = mainContainer && mainContainer.contains(parent);
      const hasSemanticContainer = parent.closest('article') ||
                                    parent.closest('main') ||
                                    parent.closest('section[role="main"]') ||
                                    parent.closest('[itemprop="articleBody"]');

      // 检查文本密度（高文本密度通常是文章内容）
      const isHighDensity = textDensity > 0.3;

      // 检查是否为文章内容
      const isArticle = this.isArticleContent(parent);

      // 检查是否为标题元素
      if (['H1', 'H2', 'H3', 'H4', 'H5', 'H6'].includes(tagName)) {
        titleNodes.push(node);
      }
      // 检查是否在主体内容容器内（最高优先级）
      else if (isInMainContainer || hasSemanticContainer) {
        mainContentNodes.push(node);
      }
      // 检查是否为文章内容元素且文本密度较高（高优先级）
      else if (isArticle && (isHighDensity || node.textContent.trim().length > 50)) {
        mainContentNodes.push(node);
      }
      // 检查是否为段落且文本长度足够
      else if (tagName === 'P' && node.textContent.trim().length > 10) {
        paragraphNodes.push(node);
      }
      // 其他内容（不再单独分类导航元素）
      else {
        otherContentNodes.push(node);
      }
    });

    // 按视口位置和优先级进一步细分首屏内容
    const viewportHeight = window.innerHeight;
    const firstScreenTitles = [];
    const firstScreenMain = [];
    const firstScreenParagraphs = [];
    const firstScreenOther = [];
    const belowFoldTitles = [];
    const belowFoldMain = [];
    const belowFoldParagraphs = [];
    const belowFoldOther = [];

    // 处理标题类内容
    titleNodes.forEach(node => {
      const position = this.getTextPosition(node);
      if (position && position.y < viewportHeight) {
        firstScreenTitles.push(node);
      } else {
        belowFoldTitles.push(node);
      }
    });

    // 处理主体内容（最高优先级）
    mainContentNodes.forEach(node => {
      const position = this.getTextPosition(node);
      if (position && position.y < viewportHeight) {
        firstScreenMain.push(node);
      } else {
        belowFoldMain.push(node);
      }
    });

    // 处理段落内容
    paragraphNodes.forEach(node => {
      const position = this.getTextPosition(node);
      if (position && position.y < viewportHeight) {
        firstScreenParagraphs.push(node);
      } else {
        belowFoldParagraphs.push(node);
      }
    });

    // 处理其他内容
    otherContentNodes.forEach(node => {
      const position = this.getTextPosition(node);
      if (position && position.y < viewportHeight) {
        firstScreenOther.push(node);
      } else {
        belowFoldOther.push(node);
      }
    });

    // 返回与传统方法兼容的格式（使用 batchNodes 分批）
    return {
      firstScreenTitles: this.batchNodes([...firstScreenTitles], batchSize),
      firstScreenMain: this.batchNodes([...firstScreenMain], batchSize),
      firstScreenParagraphs: this.batchNodes([...firstScreenParagraphs], batchSize),
      firstScreenOther: this.batchNodes([...firstScreenOther], batchSize),
      belowFoldMain: this.batchNodes([...belowFoldMain], batchSize),
      belowFoldTitles: this.batchNodes([...belowFoldTitles], batchSize),
      belowFoldParagraphs: this.batchNodes([...belowFoldParagraphs], batchSize),
      belowFoldOther: this.batchNodes([...belowFoldOther], batchSize),
      total: allTextNodes.length
    };
  }

  // 批处理节点
  batchNodes(nodes, batchSize) {
    const batches = [];
    for (let i = 0; i < nodes.length; i += batchSize) {
      batches.push(nodes.slice(i, i + batchSize));
    }
    return batches;
  }

  // 惰性加载 - 滚动时收集更多文本
  collectTextOnScroll(filter = this.defaultFilter, callback) {
    let isCollecting = false;
    const throttledCallback = this.throttle(() => {
      if (isCollecting) return;

      isCollecting = true;
      setTimeout(() => {
        const newNodes = this.collectTextByPhase(filter);
        callback(newNodes);
        isCollecting = false;
      }, 100);
    }, 300);

    window.addEventListener('scroll', throttledCallback);

    return () => {
      window.removeEventListener('scroll', throttledCallback);
    };
  }

  // 节流函数
  throttle(func, delay) {
    let timeoutId;
    let lastExecTime = 0;
    return function (...args) {
      const currentTime = Date.now();

      if (currentTime - lastExecTime > delay) {
        func.apply(this, args);
        lastExecTime = currentTime;
      } else {
        clearTimeout(timeoutId);
        timeoutId = setTimeout(() => {
          func.apply(this, args);
          lastExecTime = Date.now();
        }, delay - (currentTime - lastExecTime));
      }
    };
  }

  // 使用指定过滤器创建TreeWalker
  createWalker(filter = this.defaultFilter) {
    return document.createTreeWalker(
      document.body,
      NodeFilter.SHOW_TEXT,
      filter,
      false
    );
  }

  // ========== 混合内容识别：Readability.js + TreeWalker ==========
  // 结合 Readability.js 的文章提取能力和 TreeWalker 的精确遍历能力
  // 不修改原始 DOM 结构，只读操作

  // 使用 Readability.js 识别主体内容区域
  findMainContentAreaWithReadability() {
    if (typeof Readability === 'undefined') {
      console.log('[DOMWalker] Readability 未加载，使用传统方法');
      return this.findMainContentContainer();
    }

    try {
      // 克隆文档（不修改原始 DOM）
      const documentClone = document.cloneNode(true);

      // 移除克隆文档中的干扰元素
      const scripts = documentClone.querySelectorAll('script, style, noscript, template');
      scripts.forEach(el => el.remove());

      // 使用 Readability 解析文章
      const readabilityInstance = new Readability(documentClone, {
        charThreshold: 100,
        keepClasses: false
      });

      const article = readabilityInstance.parse();

      if (!article || !article.content) {
        console.log('[DOMWalker] Readability 无法提取内容，使用传统方法');
        return this.findMainContentContainer();
      }

      console.log('[DOMWalker] Readability 提取成功:', {
        title: article.title,
        contentLength: article.content.length,
        byline: article.byline || '未知'
      });

      // 在原始文档中查找对应的内容区域
      const mainContentElement = this.locateContentInOriginalDoc(article.content);

      if (mainContentElement) {
        console.log('[DOMWalker] 在原始 DOM 中定位到主体内容区域:', mainContentElement.tagName,
          mainContentElement.className || mainContentElement.id || '');
        return mainContentElement;
      }

      return this.findMainContentContainer();
    } catch (error) {
      console.warn('[DOMWalker] Readability 识别出错，使用传统方法:', error.message);
      return this.findMainContentContainer();
    }
  }

  // 在原始文档中定位 Readability 提取的内容
  locateContentInOriginalDoc(articleHTML) {
    const tempDiv = document.createElement('div');
    tempDiv.innerHTML = articleHTML;

    const textFragments = [];
    const walker = document.createTreeWalker(
      tempDiv,
      NodeFilter.SHOW_TEXT,
      null,
      false
    );

    let node;
    while ((node = walker.nextNode())) {
      const text = node.textContent.trim();
      if (text.length > 50) {
        textFragments.push(text);
      }
      if (textFragments.length >= 5) break;
    }

    if (textFragments.length === 0) {
      return null;
    }

    const candidates = new Map();

    for (const fragment of textFragments) {
      const xpath = `//*[contains(text(), "${fragment.substring(0, 100).replace(/"/g, '&quot')}")]`;
      const result = document.evaluate(
        xpath,
        document.documentElement,
        null,
        XPathResult.FIRST_ORDERED_NODE_TYPE,
        null
      );

      const element = result.singleNodeValue;
      if (element) {
        const container = this.findContentContainer(element);
        if (container) {
          const key = `${container.tagName}-${container.className || ''}-${container.id || ''}`;
          candidates.set(key, (candidates.get(key) || 0) + 1);
        }
      }
    }

    let bestContainer = null;
    let maxCount = 0;

    for (const [key, count] of candidates) {
      if (count > maxCount) {
        maxCount = count;
        for (const fragment of textFragments) {
          const xpath = `//*[contains(text(), "${fragment.substring(0, 100).replace(/"/g, '&quot')}")]`;
          const result = document.evaluate(
            xpath,
            document.documentElement,
            null,
            XPathResult.FIRST_ORDERED_NODE_TYPE,
            null
          );
          const element = result.singleNodeValue;
          if (element) {
            bestContainer = this.findContentContainer(element);
            if (bestContainer) break;
          }
        }
      }
    }

    return bestContainer;
  }

  // 查找内容容器
  findContentContainer(element) {
    const contentTags = ['ARTICLE', 'MAIN', 'SECTION', 'DIV'];
    const contentClasses = ['content', 'article', 'post', 'entry', 'main', 'story'];

    let current = element;
    while (current && current !== document.documentElement) {
      const tagName = current.tagName;
      const className = (current.className || '').toLowerCase();
      const id = (current.id || '').toLowerCase();

      if (contentTags.includes(tagName)) {
        return current;
      }

      for (const cls of contentClasses) {
        if (className.includes(cls) || id.includes(cls)) {
          return current;
        }
      }

      current = current.parentElement;
    }

    return null;
  }

  // 结合 Readability 和 TreeWalker 的内容识别方法
  collectTextByPhaseWithReadability(filter = this.defaultFilter, batchSize = 100) {
    console.log('[DOMWalker] 开始混合模式内容识别 (Readability + TreeWalker)');

    const mainContentArea = this.findMainContentAreaWithReadability();
    const readabilityResult = {
      mainContentFound: !!mainContentArea,
      mainContentTag: mainContentArea?.tagName || 'N/A',
      mainContentClass: mainContentArea?.className || mainContentArea?.id || 'N/A'
    };

    console.log('[DOMWalker] Readability 识别结果:', readabilityResult);

    const allTextNodes = [];
    const walker = document.createTreeWalker(
      document.body,
      NodeFilter.SHOW_TEXT,
      filter,
      false
    );

    let currentNode;
    while ((currentNode = walker.nextNode())) {
      if (currentNode.textContent.trim().length > 0) {
        allTextNodes.push(currentNode);
      }
    }

    const titleNodes = [];
    const mainContentNodes = [];
    const paragraphNodes = [];
    const otherContentNodes = [];

    allTextNodes.forEach(node => {
      const parent = node.parentElement;
      if (!parent) return;

      const tagName = parent.tagName;
      const textDensity = this.getTextDensity(parent);
      const isInMainContent = mainContentArea && mainContentArea.contains(parent);
      const hasSemanticContainer = parent.closest('article') ||
                                  parent.closest('main') ||
                                  parent.closest('section[role="main"]') ||
                                  parent.closest('[itemprop="articleBody"]');

      const isHighDensity = textDensity > 0.3;
      const isArticle = this.isArticleContent(parent);

      if (['H1', 'H2', 'H3', 'H4', 'H5', 'H6'].includes(tagName)) {
        titleNodes.push(node);
      } else if (isInMainContent || hasSemanticContainer) {
        mainContentNodes.push(node);
      } else if (isArticle && (isHighDensity || node.textContent.trim().length > 50)) {
        mainContentNodes.push(node);
      } else if (tagName === 'P' && node.textContent.trim().length > 10) {
        paragraphNodes.push(node);
      } else {
        // 不再将导航元素单独分类，全部归入其他内容
        otherContentNodes.push(node);
      }
    });

    console.log('[DOMWalker] 文本节点分组完成:', {
      title: titleNodes.length,
      mainContent: mainContentNodes.length,
      paragraph: paragraphNodes.length,
      other: otherContentNodes.length
    });

    const viewportHeight = window.innerHeight;
    const firstScreenTitles = [];
    const firstScreenMain = [];
    const firstScreenParagraphs = [];
    const firstScreenOther = [];
    const belowFoldTitles = [];
    const belowFoldMain = [];
    const belowFoldParagraphs = [];
    const belowFoldOther = [];

    const classifyByViewport = (nodes, firstScreenArray, belowFoldArray) => {
      nodes.forEach(node => {
        const position = this.getTextPosition(node);
        if (position && position.y < viewportHeight) {
          firstScreenArray.push(node);
        } else {
          belowFoldArray.push(node);
        }
      });
    };

    classifyByViewport(titleNodes, firstScreenTitles, belowFoldTitles);
    classifyByViewport(mainContentNodes, firstScreenMain, belowFoldMain);
    classifyByViewport(paragraphNodes, firstScreenParagraphs, belowFoldParagraphs);
    classifyByViewport(otherContentNodes, firstScreenOther, belowFoldOther);

    // 返回与传统方法兼容的格式（使用 batchNodes 分批）
    return {
      firstScreenTitles: this.batchNodes([...firstScreenTitles], batchSize),
      firstScreenMain: this.batchNodes([...firstScreenMain], batchSize),
      firstScreenParagraphs: this.batchNodes([...firstScreenParagraphs], batchSize),
      firstScreenOther: this.batchNodes([...firstScreenOther], batchSize),
      belowFoldTitles: this.batchNodes([...belowFoldTitles], batchSize),
      belowFoldMain: this.batchNodes([...belowFoldMain], batchSize),
      belowFoldParagraphs: this.batchNodes([...belowFoldParagraphs], batchSize),
      belowFoldOther: this.batchNodes([...belowFoldOther], batchSize),
      readabilityResult,  // 包含 Readability 识别结果信息
      total: allTextNodes.length
    };
  }
}

// TextRegistry 模块 - 文本注册表管理
class TextRegistry {
  constructor() {
    this.entries = new Map(); // Map<TextId, TextEntry>
    this.groups = new Map(); // Map<GroupId, TextId[]>
    this.batchSize = 100; // 增大批次大小以提高翻译速度
    this.criticalPath = new Set(); // 关键路径文本ID集合
    this.duplicateMap = new Map(); // 去重映射
  }

  // TextEntry 数据结构
  createTextEntry(id, original, context, position, metadata = {}) {
    return {
      id,
      original,
      context,
      position,
      metadata: {
        ...metadata,
        importance: metadata.importance || 5,
        visible: metadata.visible !== undefined ? metadata.visible : true,
        type: metadata.type || 'text',
        timestamp: Date.now(),
        groupId: metadata.groupId || null,
        isCritical: metadata.isCritical || false
      },
      translated: null,
      isTranslated: false,
      status: 'pending' // pending, translating, translated, error
    };
  }

  // 注册单个文本
  registerText(id, original, context, position, metadata = {}) {
    // 检查重复
    if (this.isDuplicate(original)) {
      const existingId = this.duplicateMap.get(original);
      if (existingId) {
        // 更新现有条目的上下文和位置信息
        const existingEntry = this.entries.get(existingId);
        if (existingEntry) {
          // 字数低于50字的文本不上传context
          const textLength = original.trim().length;
          existingEntry.context = textLength < 50 ? null : context;
          existingEntry.position = { ...existingEntry.position, ...position };
        }
        return existingId; // 返回已存在的ID
      }
    }

    // 字数低于50字的文本不上传context，设置为null
    const textLength = original.trim().length;
    const finalContext = textLength < 50 ? null : context;

    const entry = this.createTextEntry(id, original, finalContext, position, metadata);
    this.entries.set(id, entry);

    // 记录重复项
    this.duplicateMap.set(original, id);

    // 如果是关键路径，加入关键路径集合
    if (metadata.isCritical) {
      this.criticalPath.add(id);
    }

    // 分组处理
    if (metadata.groupId) {
      this.addToGroup(metadata.groupId, id);
    }

    return id;
  }

  // 检查是否为重复文本
  isDuplicate(text) {
    return this.duplicateMap.has(text);
  }

  // 添加到组
  addToGroup(groupId, textId) {
    if (!this.groups.has(groupId)) {
      this.groups.set(groupId, []);
    }
    const group = this.groups.get(groupId);
    if (!group.includes(textId)) {
      group.push(textId);
    }
  }

  // 按语义单元分组（段落/列表/表格）
  groupByTextSemantics(domWalker) {
    const walker = domWalker.createWalker();
    let currentNode;
    let groupId = 1;

    while ((currentNode = walker.nextNode())) {
      if (currentNode.textContent.trim().length > 0) {
        const parent = currentNode.parentElement;
        if (parent) {
          // 根据父元素类型确定组ID
          let groupType = 'other';
          if (['P', 'DIV'].includes(parent.tagName)) {
            groupType = 'paragraph';
          } else if (['LI'].includes(parent.tagName)) {
            groupType = 'list';
          } else if (['TD', 'TH'].includes(parent.tagName)) {
            groupType = 'table';
          } else if (['H1', 'H2', 'H3', 'H4', 'H5', 'H6'].includes(parent.tagName)) {
            groupType = 'heading';
          }

          const parentId = parent.getAttribute('id') || parent.className || `semantic_group_${groupId}`;
          const semanticGroupId = `${groupType}_${parentId}`;

          // 查找对应的textId并分配到组
          const textId = this.findTextIdByNode(currentNode);
          if (textId) {
            const entry = this.entries.get(textId);
            if (entry) {
              entry.metadata.groupId = semanticGroupId;
              this.addToGroup(semanticGroupId, textId);
            }
          }
        }
      }
    }
  }

  // 按视觉布局分组
  groupByVisualLayout() {
    // 按坐标相近的文本分组
    const groups = new Map();

    for (const [id, entry] of this.entries) {
      if (entry.position) {
        const pos = entry.position;
        // 计算可视区域内的网格位置
        const gridX = Math.floor(pos.x / 100); // 每100px为一个网格
        const gridY = Math.floor(pos.y / 50);  // 每50px为一个网格

        const gridKey = `${gridX}_${gridY}`;

        if (!groups.has(gridKey)) {
          groups.set(gridKey, []);
        }
        groups.get(gridKey).push(id);
      }
    }

    // 创建视觉分组
    let visualGroupId = 1;
    for (const [gridKey, textIds] of groups) {
      if (textIds.length > 1) { // 只为多个文本的组创建
        const groupId = `visual_${visualGroupId++}`;
        for (const textId of textIds) {
          const entry = this.entries.get(textId);
          if (entry && !entry.metadata.groupId) { // 不覆盖已有组
            entry.metadata.groupId = groupId;
            this.addToGroup(groupId, textId);
          }
        }
      }
    }
  }

  // 按交互状态分组
  groupByInteractionState() {
    // 检查文本是否属于交互元素（按钮、链接等）
    for (const [id, entry] of this.entries) {
      const element = this.findElementById(id);
      if (element) {
        let interactionType = 'static';
        if (element.tagName === 'BUTTON' || element.getAttribute('role') === 'button') {
          interactionType = 'button';
        } else if (element.tagName === 'A' && element.href) {
          interactionType = 'link';
        } else if (element.tagName === 'INPUT' || element.tagName === 'TEXTAREA') {
          interactionType = 'input';
        }

        if (interactionType !== 'static') {
          const groupId = `interactive_${interactionType}`;
          entry.metadata.groupId = groupId;
          this.addToGroup(groupId, id);
        }
      }
    }
  }

  // 根据节点查找textId
  findTextIdByNode(node) {
    for (const [id, entry] of this.entries) {
      // 这里简化处理，实际应用中需要更精确的映射
      if (entry.original === node.textContent.trim()) {
        return id;
      }
    }
    return null;
  }

  // 查找DOM元素
  findElementById(textId) {
    const entry = this.entries.get(textId);
    if (!entry || !entry.position) return null;

    // 使用document.elementsFromPoint或其他方法定位元素
    // 这是一个简化的实现
    const allElements = document.querySelectorAll('*');
    for (const element of allElements) {
      if (element.textContent.includes(entry.original)) {
        return element;
      }
    }
    return null;
  }

  // 获取批处理
  getBatches(options = {}) {
    const {
      priorityStrategy = 'importance-first', // importance-first, visible-first, critical-first, sequential
      batchSizeSchedule = [10, 20, 50, 100, 100], // 首批按5,10,20,30,50上传
      includeGroups = false
    } = options;

    let textIds = Array.from(this.entries.keys());

    // 根据策略排序
    switch (priorityStrategy) {
      case 'importance-first':
        textIds.sort((a, b) => {
          const aEntry = this.entries.get(a);
          const bEntry = this.entries.get(b);

          // 翻译优先级：标题 > 第一段内容 > 文章主体内容 > 导航 > 其他内容
          // 1. 标题优先 (H1-H6)
          const aIsTitle = aEntry.metadata.tagName && ['H1', 'H2', 'H3', 'H4', 'H5', 'H6'].includes(aEntry.metadata.tagName);
          const bIsTitle = bEntry.metadata.tagName && ['H1', 'H2', 'H3', 'H4', 'H5', 'H6'].includes(bEntry.metadata.tagName);
          if (aIsTitle !== bIsTitle) {
            return aIsTitle ? -1 : 1; // 标题优先
          }

          // 2. 第一段内容（通过元数据标记的firstParagraph）
          const aIsFirstParagraph = aEntry.metadata?.isFirstParagraph;
          const bIsFirstParagraph = bEntry.metadata?.isFirstParagraph;
          if (aIsFirstParagraph !== bIsFirstParagraph) {
            return aIsFirstParagraph ? -1 : 1; // 第一段优先
          }

          // 3. 主体内容容器内优先（新增 isInMainContainer 判断）
          const aInMainContainer = aEntry.metadata?.isInMainContainer;
          const bInMainContainer = bEntry.metadata?.isInMainContainer;
          if (aInMainContainer !== bInMainContainer) {
            return aInMainContainer ? -1 : 1; // 主体内容容器内优先
          }

          // 4. 文章主体内容优先（排除导航、页脚、侧边栏等）
          const aIsNavigation = this.isNavigationElementByMetadata(aEntry);
          const bIsNavigation = this.isNavigationElementByMetadata(bEntry);

          if (aIsNavigation !== bIsNavigation) {
            return aIsNavigation ? 1 : -1; // 导航靠后
          }

          // 5. 主体内容标记优先
          const aIsArticle = aEntry.metadata?.isArticleContent;
          const bIsArticle = bEntry.metadata?.isArticleContent;
          if (aIsArticle !== bIsArticle) {
            return aIsArticle ? -1 : 1; // 文章内容优先
          }

          // 6. 按重要性评分降序排列
          const aScore = aEntry.metadata.importance || 5;
          const bScore = bEntry.metadata.importance || 5;
          if (bScore !== aScore) {
            return bScore - aScore; // 分数高的在前
          }

          // 5. 按原始注册顺序（保持稳定性）
          return 0;
        });
        break;
      case 'critical-first':
        textIds.sort((a, b) => {
          const aEntry = this.entries.get(a);
          const bEntry = this.entries.get(b);
          if (aEntry.metadata.isCritical && !bEntry.metadata.isCritical) return -1;
          if (!aEntry.metadata.isCritical && bEntry.metadata.isCritical) return 1;
          return 0;
        });
        break;
      case 'visible-first':
        textIds.sort((a, b) => {
          const aEntry = this.entries.get(a);
          const bEntry = this.entries.get(b);
          if (aEntry.position?.visible && !bEntry.position?.visible) return -1;
          if (!aEntry.position?.visible && bEntry.position?.visible) return 1;
          return 0;
        });
        break;
      default:
        // 保持原始顺序
        break;
    }

    // 分批（按5,10,20,30,50,50...上传）
    const batches = [];
    let currentIndex = 0;

    // 使用预定义的批次大小计划
    for (let i = 0; i < batchSizeSchedule.length && currentIndex < textIds.length; i++) {
      const batchSize = batchSizeSchedule[i];
      const batch = textIds.slice(currentIndex, currentIndex + batchSize).map(id => this.entries.get(id));
      batches.push(batch);
      currentIndex += batchSize;
    }

    // 剩余文本按50个一批处理
    const defaultBatchSize = 100;
    while (currentIndex < textIds.length) {
      const batch = textIds.slice(currentIndex, currentIndex + defaultBatchSize).map(id => this.entries.get(id));
      batches.push(batch);
      currentIndex += defaultBatchSize;
    }

    return batches;
  }

  // 动态调整批次大小
  adjustBatchSize(performanceMetrics) {
    const { cpuUsage, memoryUsage, networkLatency } = performanceMetrics;

    if (cpuUsage > 80 || memoryUsage > 80) {
      this.batchSize = Math.max(10, Math.floor(this.batchSize * 0.7)); // 减小批次
    } else if (cpuUsage < 50 && memoryUsage < 50 && networkLatency < 500) {
      this.batchSize = Math.min(100, Math.ceil(this.batchSize * 1.2)); // 增大批次
    }
  }

  // 标记关键路径
  markCriticalPath(textIds) {
    for (const id of textIds) {
      const entry = this.entries.get(id);
      if (entry) {
        entry.metadata.isCritical = true;
        this.criticalPath.add(id);
      }
    }
  }

  // 获取注册表统计
  getStats() {
    return {
      totalEntries: this.entries.size,
      totalGroups: this.groups.size,
      criticalCount: this.criticalPath.size,
      translatedCount: Array.from(this.entries.values()).filter(e => e.isTranslated).length,
      pendingCount: Array.from(this.entries.values()).filter(e => e.status === 'pending').length,
      translatingCount: Array.from(this.entries.values()).filter(e => e.status === 'translating').length,
      errorCount: Array.from(this.entries.values()).filter(e => e.status === 'error').length
    };
  }

  // 清理注册表
  clear() {
    this.entries.clear();
    this.groups.clear();
    this.criticalPath.clear();
    this.duplicateMap.clear();
  }

  // 序列化
  serialize() {
    const serialized = {
      entries: [],
      groups: {},
      criticalPath: Array.from(this.criticalPath),
      duplicateMap: {}
    };

    for (const [key, value] of this.entries) {
      serialized.entries.push({ ...value });
    }

    for (const [key, value] of this.groups) {
      serialized.groups[key] = value;
    }

    for (const [key, value] of this.duplicateMap) {
      serialized.duplicateMap[key] = value;
    }

    return serialized;
  }

  // 反序列化
  deserialize(data) {
    this.clear();

    for (const entry of data.entries) {
      this.entries.set(entry.id, entry);
    }

    for (const [key, value] of Object.entries(data.groups)) {
      this.groups.set(key, value);
    }

    for (const id of data.criticalPath) {
      this.criticalPath.add(id);
    }

    for (const [key, value] of Object.entries(data.duplicateMap)) {
      this.duplicateMap.set(key, value);
    }
  }
}

// TranslationApplier 模块 - 翻译应用和渲染
class TranslationApplier {
  constructor() {
    this.currentTranslations = new Map();
    this.progressCallbacks = [];
    this.originalTextNodes = new Map(); // 保存原始文本节点的引用
    this.isSwitchingMode = false;  // 添加切换模式标志
    this.autoCloseTimer = null;  // 双语模式自动关闭计时器
    this.userInteracted = false;  // 用户是否与页面交互
    this.isProgressCompleted = false;  // 进度条是否已完成
  }

  // 注入双语显示样式（在翻译开始时调用）
  injectBilingualStyles() {
    if (document.getElementById('extreme-bilingual-styles')) return;

    const style = document.createElement('style');
    style.id = 'extreme-bilingual-styles';
    style.textContent = `
      .extreme-bilingual-text {
        display: flex !important;
        flex-direction: column !important;
        padding: 4px 0 !important;
        margin: 0 !important;
        clear: both !important;
        width: 100% !important;
        box-sizing: border-box !important;
      }
      .extreme-bilingual-text .ext-bilingual-original {
        display: block !important;
        color: rgba(128, 128, 128, 0.75) !important;
        font-size: 0.88em !important;
        line-height: 1.4 !important;
        margin: 0 0 3px 0 !important;
        padding: 0 0 3px 0 !important;
        border-bottom: 1px dashed rgba(128, 128, 128, 0.2) !important;
        width: 100% !important;
        box-sizing: border-box !important;
      }
      .extreme-bilingual-text .ext-bilingual-translated {
        display: block !important;
        font-size: 1em !important;
        line-height: 1.5 !important;
        margin: 0 !important;
        padding: 0 !important;
        width: 100% !important;
        box-sizing: border-box !important;
      }
    `;
    document.head.appendChild(style);
  }

  // 渐进式翻译更新
  async progressiveUpdate(translations, textRegistry, options = {}) {
    const {
      bilingualDisplay = false,
      updateStrategy = 'importance-first', // importance-first, visible-first, critical-first, batch-all
      batchSize = 10
    } = options;

    // 更新策略排序
    let sortedEntries;
    switch (updateStrategy) {
      case 'importance-first':
        sortedEntries = [...textRegistry.entries.values()]
          .sort((a, b) => {
            // 按重要性评分降序排列
            const aScore = a.metadata.importance || 5;
            const bScore = b.metadata.importance || 5;

            if (bScore !== aScore) {
              return bScore - aScore; // 分数高的在前
            }

            // 如果重要性相同，则优先可见区域的文本
            const aVisible = a.position?.visible ? 1 : 0;
            const bVisible = b.position?.visible ? 1 : 0;
            return bVisible - aVisible;
          });
        break;
      case 'visible-first':
        sortedEntries = [...textRegistry.entries.values()]
          .sort((a, b) => {
            const aVisible = a.position?.visible ? 1 : 0;
            const bVisible = b.position?.visible ? 1 : 0;
            return bVisible - aVisible; // 优先显示可见区域
          });
        break;
      case 'critical-first':
        sortedEntries = [...textRegistry.entries.values()]
          .sort((a, b) => {
            const aCritical = a.metadata.isCritical ? 1 : 0;
            const bCritical = b.metadata.isCritical ? 1 : 0;
            return bCritical - aCritical; // 优先显示关键路径
          });
        break;
      default:
        sortedEntries = [...textRegistry.entries.values()];
    }

    const totalEntries = sortedEntries.length;
    let processedCount = 0;

    // 分批处理
    for (let i = 0; i < sortedEntries.length; i += batchSize) {
      const batch = sortedEntries.slice(i, i + batchSize);

      // 在下一帧执行批处理以避免阻塞UI
      await new Promise(resolve => setTimeout(resolve, 0));

      for (const entry of batch) {
        const translation = translations.find(t => t.textId === entry.id);
        if (translation) {
          this.applySingleTranslation(entry, translation.translation, bilingualDisplay);

          // 更新进度回调
          processedCount++;
          const progress = (processedCount / totalEntries) * 100;
          this.updateProgress(processedCount, totalEntries);
        }
      }
    }

    // 通知完成
    this.progressCallbacks.forEach(callback => {
      try {
        callback(100, totalEntries, totalEntries);
      } catch (error) {
        console.error('进度回调执行失败:', error);
      }
    });
  }

  // 应用单个翻译 - 直接替换原始文本节点
  applySingleTranslation(entry, translatedText, bilingualDisplay = false) {
    try {
      // 检查输入参数的有效性
      if (!entry || !translatedText) {
        console.warn('applySingleTranslation: 输入参数无效', { entry, translatedText });
        if (entry) {
          entry.status = 'error';
        }
        return false;
      }

      // 查找原始文本节点
      const originalNode = this.findOriginalTextNode(entry);

      if (!originalNode) {
        console.warn(`找不到文本ID ${entry.id} 对应的原始节点`);
        entry.status = 'error';
        return false;
      }

      // 保存原始文本节点引用，便于后续恢复
      this.originalTextNodes.set(entry.id, {
        node: originalNode,
        originalText: originalNode.textContent
      });

      // 根据显示模式应用翻译
      if (bilingualDisplay) {
        this.applyBilingualTranslation(originalNode, entry.original, translatedText);
      } else {
        this.applyDirectTranslation(originalNode, translatedText);
      }

      // 保存翻译结果
      this.currentTranslations.set(entry.id, translatedText);

      // 更新条目状态
      entry.translated = translatedText;
      entry.isTranslated = true;
      entry.status = 'translated';

      return true;
    } catch (error) {
      console.error(`应用翻译失败 [${entry?.id || 'unknown'}]:`, error);
      if (entry) {
        entry.status = 'error';
      }
      return false;
    }
  }

  // 查找原始文本节点
  findOriginalTextNode(entry) {
    try {
      // 检查entry是否存在
      if (!entry) {
        console.warn('findOriginalTextNode: entry 参数为空');
        return null;
      }

      // 通过位置信息和文本内容匹配原始节点
      const allTextNodes = this.getAllTextNodes();

      // 首先尝试精确匹配
      for (const node of allTextNodes) {
        if (node && node.textContent && entry.original && node.textContent.trim() === entry.original.trim()) {
          return node;
        }
      }

      // 如果精确匹配失败，尝试模糊匹配（考虑可能的空白字符差异）
      for (const node of allTextNodes) {
        if (node && node.textContent && entry.original &&
            node.textContent.replace(/\s+/g, '') === entry.original.replace(/\s+/g, '')) {
          return node;
        }
      }

      // 如果还是找不到，尝试部分匹配
      for (const node of allTextNodes) {
        if (node && node.textContent && entry.original &&
            (entry.original.includes(node.textContent.trim()) ||
            node.textContent.trim().includes(entry.original.trim()))) {
          return node;
        }
      }

      console.warn(`找不到文本ID ${entry.id} 对应的原始节点`);
      return null;
    } catch (error) {
      console.error('查找原始文本节点时出错:', error);
      return null;
    }
  }

  // 获取页面所有文本节点
  getAllTextNodes() {
    const textNodes = [];
    const walker = document.createTreeWalker(
      document.body,
      NodeFilter.SHOW_TEXT,
      {
        acceptNode: function(node) {
          return node.textContent.trim().length > 0 ?
                 NodeFilter.FILTER_ACCEPT :
                 NodeFilter.FILTER_REJECT;
        }
      }
    );

    let node;
    while (node = walker.nextNode()) {
      textNodes.push(node);
    }

    return textNodes;
  }

  // 应用直接翻译（简化版 - 直接替换文本）
  applyDirectTranslation(textNode, translatedText) {
    // 保存原始文本内容
    const originalText = textNode.textContent;

    // 格式保护器：保留原始格式
    const formattedText = this.protectFormat(originalText, translatedText);

    // 直接替换文本
    textNode.textContent = formattedText;

    // 标记元素为已翻译
    const parentElement = textNode.parentElement;
    if (parentElement && !parentElement.classList.contains('extreme-translated')) {
      parentElement.classList.add('extreme-translated');
      parentElement.setAttribute('data-original-text', originalText);
      parentElement.setAttribute('data-translated-text', formattedText);
    }
  }

  // 应用双语翻译 - 使用styled DOM结构显示原文和译文
  applyBilingualTranslation(textNode, originalText, translatedText) {
    const parentElement = textNode.parentElement;
    if (!parentElement) {
      console.warn('原文本节点没有父元素，使用默认翻译方式');
      this.applyDirectTranslation(textNode, translatedText);
      return;
    }

    // 确保双语样式已注入
    this.injectBilingualStyles();

    const formattedTranslation = this.protectFormat(originalText, translatedText);

    // 构建双语DOM结构
    parentElement.textContent = '';

    const originalSpan = document.createElement('span');
    originalSpan.className = 'ext-bilingual-original';
    originalSpan.textContent = originalText;

    const translatedSpan = document.createElement('span');
    translatedSpan.className = 'ext-bilingual-translated';
    translatedSpan.textContent = formattedTranslation;

    parentElement.appendChild(originalSpan);
    parentElement.appendChild(translatedSpan);

    // 标记元素
    parentElement.classList.add('extreme-translated');
    parentElement.setAttribute('data-original-text', originalText);
    parentElement.setAttribute('data-translated-text', formattedTranslation);
    parentElement.setAttribute('data-bilingual-mode', 'true');
    parentElement.classList.add('extreme-bilingual-text');
  }

  // 创建并显示翻译进度条
  showTranslationProgress() {
    // 检查是否已存在进度条
    let progressBar = document.getElementById('extreme-translation-progress-bar');
    if (progressBar) {
      progressBar.classList.remove('completed');
      progressBar.style.display = 'block';
      return;
    }

    // 确保样式已注入
    if (!document.getElementById('extreme-progress-styles')) {
      const style = document.createElement('style');
      style.id = 'extreme-progress-styles';
      style.textContent = `
        @keyframes nt-progressSlideDown {
          from { transform: translateY(-100%); }
          to { transform: translateY(0); }
        }
        @keyframes nt-progressFadeOut {
          from { opacity: 1; }
          to { opacity: 0; }
        }
        @keyframes nt-shimmerProgress {
          0% { background-position: -200% 0; }
          100% { background-position: 200% 0; }
        }
        @keyframes nt-counterFadeIn {
          from { opacity: 0; transform: translateY(-4px); }
          to { opacity: 1; transform: translateY(0); }
        }
        #extreme-translation-progress-bar {
          position: fixed !important;
          top: 0 !important;
          left: 0 !important;
          width: 100% !important;
          height: 3px !important;
          background: rgba(0, 0, 0, 0.08) !important;
          z-index: 2147483647 !important;
          display: block !important;
          animation: nt-progressSlideDown 0.3s ease-out !important;
        }
        #extreme-translation-progress-bar .progress-fill {
          height: 100% !important;
          width: 0% !important;
          background: linear-gradient(90deg, #7c3aed 0%, #a78bfa 50%, #7c3aed 100%) !important;
          background-size: 200% 100% !important;
          animation: nt-shimmerProgress 1.5s linear infinite !important;
          transition: width 0.2s ease-out !important;
          position: relative !important;
          overflow: hidden !important;
        }
        #extreme-translation-progress-bar.completed .progress-fill {
          background: linear-gradient(90deg, #10b981 0%, #34d399 100%) !important;
          animation: none !important;
        }
        #extreme-translation-progress-bar.completed {
          animation: nt-progressFadeOut 0.3s ease-out 0.3s forwards !important;
        }
        #extreme-translation-progress-bar.completed .progress-counter {
          opacity: 0 !important;
          transition: opacity 0.2s ease-out !important;
        }
        .progress-counter {
          position: absolute !important;
          top: 8px !important;
          right: 12px !important;
          background: rgba(0, 0, 0, 0.7) !important;
          color: white !important;
          padding: 4px 10px !important;
          border-radius: 12px !important;
          font-size: 12px !important;
          font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif !important;
          z-index: 2147483647 !important;
          animation: nt-counterFadeIn 0.3s ease-out !important;
          pointer-events: none !important;
          line-height: 1.2 !important;
          white-space: nowrap !important;
          backdrop-filter: blur(4px) !important;
        }
      `;
      document.head.appendChild(style);
    }

    // 创建进度条（含计数器）
    progressBar = document.createElement('div');
    progressBar.id = 'extreme-translation-progress-bar';
    progressBar.innerHTML = '<div class="progress-fill"></div><div class="progress-counter">翻译中...</div>';
    document.body.appendChild(progressBar);

    // 重置完成标志
    this.isProgressCompleted = false;
  }

  // 更新翻译进度
  updateTranslationProgress(current, total) {
    let progressBar = document.getElementById('extreme-translation-progress-bar');
    if (!progressBar) return;

    // 使用线性进度，让用户能感知翻译过程
    const rawPercentage = total > 0 ? (current / total) * 100 : 0;

    // 限制最大值为 90%，给翻译完成留出余量
    const percentage = Math.min(rawPercentage, 90);

    const progressFill = progressBar.querySelector('.progress-fill');
    if (progressFill) {
      progressFill.style.width = `${percentage}%`;
    }

    // 更新计数器
    const counter = progressBar.querySelector('.progress-counter');
    if (counter) {
      counter.textContent = `${current} / ${total}`;
    }
  }

  // 隐藏翻译进度条（翻译完成）
  hideTranslationProgress() {
    // 防止重复调用
    if (this.isProgressCompleted) {
      return;
    }
    this.isProgressCompleted = true;

    let progressBar = document.getElementById('extreme-translation-progress-bar');
    if (!progressBar) return;

    // 先将进度条填充到 100%
    const progressFill = progressBar.querySelector('.progress-fill');
    if (progressFill) {
      progressFill.style.width = '100%';
    }

    // 设置为完成状态并淡出（绿色）
    progressBar.classList.add('completed');

    // 动画完成后移除
    setTimeout(() => {
      if (progressBar && progressBar.parentNode) {
        progressBar.style.display = 'none';
        progressBar.classList.remove('completed');
      }
    }, 900); // 0.5s delay + 0.4s fade out
  }

  // 切换所有双语元素的显示模式（切换显示方式：双语/单语）
  toggleAllBilingualDisplay(showBilingual = true) {
    console.log(`toggleAllBilingualDisplay: 切换到 ${showBilingual ? '双语模式' : '单语模式'}`);

    try {
      // 确保双语样式已注入
      if (showBilingual) {
        this.injectBilingualStyles();
      }

      // 获取所有已翻译的元素
      const translatedElements = document.querySelectorAll('.extreme-translated');

      if (translatedElements.length === 0) {
        console.log('toggleAllBilingualDisplay: 没有找到已翻译的元素');
        this.requestReTranslateWithBilingualMode(showBilingual);
        return;
      }

      let switchedCount = 0;

      // 更新所有元素的双语模式标记
      translatedElements.forEach(element => {
        const originalText = element.getAttribute('data-original-text');
        const translatedText = element.getAttribute('data-translated-text');

        if (!originalText || !translatedText) return;

        if (showBilingual) {
          // 启用双语模式：构建原文+译语的DOM结构
          element.setAttribute('data-bilingual-mode', 'true');
          element.classList.add('extreme-bilingual-text');

          // 清除旧内容，构建双语DOM
          element.textContent = '';
          const originalSpan = document.createElement('span');
          originalSpan.className = 'ext-bilingual-original';
          originalSpan.textContent = originalText;

          const translatedSpan = document.createElement('span');
          translatedSpan.className = 'ext-bilingual-translated';
          translatedSpan.textContent = translatedText;

          element.appendChild(originalSpan);
          element.appendChild(translatedSpan);
          switchedCount++;
        } else {
          // 禁用双语模式：只显示译文
          element.removeAttribute('data-bilingual-mode');
          element.classList.remove('extreme-bilingual-text');
          element.textContent = translatedText;
          switchedCount++;
        }
      });

      console.log(`toggleAllBilingualDisplay: 已切换 ${switchedCount} 个元素到 ${showBilingual ? '双语' : '单语'} 模式`);

      // 更新本地状态
      this.updateTranslationStatus(showBilingual ? 'bilingual_mode' : 'single_mode');

      // 向popup发送双语显示切换消息
      browser.runtime.sendMessage({
        action: 'bilingualDisplayToggled',
        showBilingual: showBilingual
      }).catch(() => {});

    } catch (error) {
      console.error('toggleAllBilingualDisplay: 切换异常:', error);
      this.requestReTranslateWithBilingualMode(showBilingual);
    }
  }

  // 请求重新翻译（带双语设置）
  requestReTranslateWithBilingualMode(showBilingual) {
    console.log(`requestReTranslateWithBilingualMode: 请求重新翻译，双语模式: ${showBilingual}`);

    // 向background发送消息，请求重新翻译
    browser.runtime.sendMessage({
      action: 'toggleBilingualDisplay',
      showBilingual: showBilingual
    }).then(response => {
      console.log('requestReTranslateWithBilingualMode: 双语模式切换请求已发送:', response);
    }).catch(error => {
      console.error('requestReTranslateWithBilingualMode: 双语模式切换失败:', error);
    });
  }

  // 切换显示模式（原文/译文）
  toggleDisplayMode() {
    console.log('toggleDisplayMode: 开始执行');

    // 防止重复快速点击
    const currentTime = Date.now();
    if (this.lastToggleTime && (currentTime - this.lastToggleTime) < 500) {
      console.log('toggleDisplayMode: 切换操作过于频繁，忽略此次请求');
      return;
    }
    this.lastToggleTime = currentTime;

    // 防止重复快速点击
    if (this.isSwitchingMode) {
      console.log('toggleDisplayMode: 切换模式正在进行中，忽略新的切换请求');
      return;
    }

    this.isSwitchingMode = true;

    try {
      // 获取所有已翻译的元素
      const translatedElements = document.querySelectorAll('.extreme-translated');

      if (translatedElements.length === 0) {
        console.log('toggleDisplayMode: 没有找到已翻译的元素');
        return;
      }

      console.log(`toggleDisplayMode: 检测到 ${translatedElements.length} 个已翻译元素`);

      // 检查当前显示的是原文还是译文
      // 检查第一个元素的文本内容与 data-original-text 或 data-translated-text 的匹配情况
      const firstElement = translatedElements[0];
      const originalText = firstElement.getAttribute('data-original-text');
      const translatedText = firstElement.getAttribute('data-translated-text');

      // 双语模式下，不依赖统一的 isCurrentlyShowingOriginal，每个元素独立判断

      // 切换所有元素的显示内容
      translatedElements.forEach(element => {
        const originalText = element.getAttribute('data-original-text');
        const translatedText = element.getAttribute('data-translated-text');

        if (!originalText || !translatedText) {
          console.warn('toggleDisplayMode: 元素缺少原文或译文数据:', element);
          return;
        }

        // 根据双语模式设置显示内容
        const isBilingual = element.hasAttribute('data-bilingual-mode');

        if (isBilingual) {
          // 双语模式：在"只显示原文"和"显示双语"之间切换
          const currentText = element.textContent;
          const isCurrentlyBilingual = currentText.includes('\n');

          if (isCurrentlyBilingual) {
            // 当前显示双语，切换到只显示原文
            element.textContent = originalText;
          } else {
            // 当前只显示原文，切换到显示双语
            element.textContent = originalText + '\n' + translatedText;
          }
        } else {
          // 单语模式：在原文和译文之间切换
          const currentText = element.textContent;
          const isCurrentlyOriginal = currentText === originalText;

          if (isCurrentlyOriginal) {
            // 当前显示原文，切换到译文
            element.textContent = translatedText;
          } else {
            // 当前显示译文，切换到原文
            element.textContent = originalText;
          }
        }
      });

      // 更新状态 - 只取第一个元素的状态来判断整体状态
      const isBilingual = firstElement.hasAttribute('data-bilingual-mode');
      let newStatus;

      const firstOriginalText = firstElement.getAttribute('data-original-text');
      const firstTranslatedText = firstElement.getAttribute('data-translated-text');

      if (isBilingual) {
        const currentText = firstElement.textContent;
        const isCurrentlyBilingual = currentText.includes('\n');
        newStatus = isCurrentlyBilingual ? 'showing_original_and_translation' : 'showing_original';
      } else {
        const currentText = firstElement.textContent;
        const isCurrentlyOriginal = currentText === firstOriginalText;
        newStatus = isCurrentlyOriginal ? 'showing_original' : 'showing_translation';
      }

      console.log(`toggleDisplayMode: 切换完成，新状态: ${newStatus}`);
      this.updateTranslationStatus(newStatus);

      // 如果是双语模式，设置5秒后自动关闭原文显示
      if (firstElement.hasAttribute('data-bilingual-mode')) {
        // 清除之前的定时器
        if (this.autoCloseTimer) {
          clearTimeout(this.autoCloseTimer);
        }

        // 设置5秒自动关闭定时器
        this.autoCloseTimer = setTimeout(() => {
          console.log('toggleDisplayMode: 5秒自动关闭 - 恢复到只显示译文');

          translatedElements.forEach(element => {
            const translatedText = element.getAttribute('data-translated-text');
            if (translatedText) {
              element.textContent = translatedText;
            }
          });

          this.updateTranslationStatus('showing_translation');
          this.userInteracted = false;
        }, 5000);
      }

    } finally {
      // 重置标志
      this.isSwitchingMode = false;
    }
  }

  // 通知UI更新翻译状态
  updateTranslationStatus(status) {
    console.log('updateTranslationStatus: 开始更新翻译状态:', status);

    // 防止频繁更新状态
    const currentTime = Date.now();
    if (this.lastStatusUpdateTime && (currentTime - this.lastStatusUpdateTime) < 300) {
      console.log('updateTranslationStatus: 更新过于频繁，延迟处理');
      // 取消之前计划的更新（如果有）
      if (this.pendingStatusUpdate) {
        clearTimeout(this.pendingStatusUpdate);
      }

      // 计划一个新更新，延迟执行
      this.pendingStatusUpdate = setTimeout(() => {
        this.sendStatusUpdate(status);
        this.lastStatusUpdateTime = Date.now();
      }, 300);
      return;
    }

    this.sendStatusUpdate(status);
    this.lastStatusUpdateTime = currentTime;
    console.log('updateTranslationStatus: 状态更新处理完成');
  }

  // 发送状态更新的实际方法
  sendStatusUpdate(status) {
    console.log('sendStatusUpdate: 仅保存状态到本地存储:', status);

    // 仅保存状态到本地存储，不发送任何消息
    // 同时直接向background发送状态保存请求
    try {
      console.log('sendStatusUpdate: 发送savePageStatus消息到background');
      browser.runtime.sendMessage({
        action: 'savePageStatus',
        status: status,
        tabId: this.getCurrentTabId() // 需要获取当前标签页ID
      }).catch(() => {
        console.log('sendStatusUpdate: 发送savePageStatus失败，可能是background未响应');
        // 如果background未接收到消息，是正常情况
      });
    } catch (error) {
      console.log('sendStatusUpdate: 发送savePageStatus时捕获错误:', error.message);
      // 忽略错误
    }
  }

  // 获取当前标签页ID的辅助方法
  getCurrentTabId() {
    // 通过向background发送消息来获取tabId
    // 注意：在content script中无法直接获取tabId，所以返回一个特殊的标识符
    return document.location.href.substring(0, 100); // 使用URL的一部分作为标识符
  }

  // 切换双语显示模式
  toggleBilingualDisplay(wrapper, originalText, translatedText) {
    // 简单切换显示原文还是译文
    const isShowingTranslation = wrapper.querySelector('.extreme-translated-text').style.display !== 'none';

    if (isShowingTranslation) {
      // 显示原文
      wrapper.querySelector('.extreme-translated-text').style.display = 'none';
      wrapper.querySelector('.extreme-original-text').style.display = 'block';
    } else {
      // 显示译文
      wrapper.querySelector('.extreme-translated-text').style.display = 'block';
      wrapper.querySelector('.extreme-original-text').style.display = 'none';
    }
  }

  // 格式保护器 - 保留原始空格、标点、特殊字符、换行和缩进
  protectFormat(original, translated) {
    try {
      // 检查输入是否为null或undefined
      if (original == null || translated == null) {
        console.warn('protectFormat: 输入值为null或undefined', { original, translated });
        return translated || '';
      }

      // 确保输入是字符串
      original = String(original);
      translated = String(translated);

      // 1. 保留首尾空白字符（包括空格、制表符、换行）
      const leadingSpaces = original.match(/^[\s\u00A0]*/)?.[0] || '';
      const trailingSpaces = original.match(/[\s\u00A0]*$/)?.[0] || '';
      translated = translated.trim();

      // 2. 保留句末标点
      const originalLastChar = original.trim().slice(-1);
      const translatedLastChar = translated.slice(-1);

      if (['.', '!', '?', '。', '！', '？', ';', '；', ':', '：'].includes(originalLastChar) &&
          !['.', '!', '?', '。', '！', '？', ';', '；', ':', '：'].includes(translatedLastChar)) {
        translated = translated + originalLastChar;
      }

      // 3. 保留原文中的换行和缩进
      const originalLines = original.split('\n');
      const translatedLines = translated.split('\n');

      if (originalLines.length > 1 && translatedLines.length === 1) {
        // 如果原文多行但翻译成一行，尝试保持换行结构
        if (translated.length > 50) {
          // 按语义分割保持原文换行数
          const chunks = this.splitLongTextBySemantic(translated, Math.ceil(translated.length / originalLines.length));
          translated = chunks.join('\n');
        }
      }

      // 4. 保留缩进（如果原文有缩进）
      if (originalLines.length > 1) {
        // 获取第一行的缩进
        const firstLineIndent = originalLines[0].match(/^\s*/)?.[0] || '';
        if (firstLineIndent.length > 0) {
          // 对每一行添加相同的缩进
          translated = translated.split('\n').map((line, index) => {
            if (index === 0) return line; // 第一行不加缩进
            return firstLineIndent + line;
          }).join('\n');
        }
      }

      // 5. 保留连续空格（使用 &nbsp; 替代）
      translated = translated.replace(/ {2,}/g, match => '&nbsp;'.repeat(match.length));

      return leadingSpaces + translated + trailingSpaces;
    } catch (error) {
      console.warn('格式保护失败，返回原始翻译:', error);
      return translated || '';
    }
  }

  // 语义分割长文本
  splitLongTextBySemantic(text, maxLength = 50) {
    const chunks = [];
    let currentChunk = '';

    // 按句子边界分割
    const sentences = text.split(/([.!?。！？]+)/);

    for (let i = 0; i < sentences.length; i += 2) {
      const sentence = sentences[i] + (sentences[i + 1] || '');
      if (currentChunk.length + sentence.length <= maxLength) {
        currentChunk += sentence;
      } else {
        if (currentChunk) {
          chunks.push(currentChunk);
        }
        if (sentence.length <= maxLength) {
          currentChunk = sentence;
        } else {
          // 如果单个句子过长，按字符分割
          const subChunks = this.splitByLength(sentence, maxLength);
          chunks.push(...subChunks.slice(0, -1));
          currentChunk = subChunks[subChunks.length - 1];
        }
      }
    }

    if (currentChunk) {
      chunks.push(currentChunk);
    }

    return chunks;
  }

  // 按长度分割文本
  splitByLength(text, length) {
    const chunks = [];
    for (let i = 0; i < text.length; i += length) {
      chunks.push(text.substring(i, i + length));
    }
    return chunks;
  }

  // HTML转义
  escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  // 进度回调管理
  addProgressCallback(callback) {
    this.progressCallbacks.push(callback);
  }

  removeProgressCallback(callback) {
    const index = this.progressCallbacks.indexOf(callback);
    if (index > -1) {
      this.progressCallbacks.splice(index, 1);
    }
  }

  updateProgress(current, total) {
    const percentage = Math.round((current / total) * 100);

    this.progressCallbacks.forEach(callback => {
      try {
        callback(percentage, current, total);
      } catch (error) {
        console.error('进度回调执行失败:', error);
      }
    });
  }

  // 显示成功通知
  showSuccessNotification(message, duration = 2000) {
    const successDiv = document.createElement('div');
    successDiv.style.cssText = `
      position: fixed;
      top: 20px;
      right: 20px;
      background: #4CAF50;
      color: white;
      padding: 12px 16px;
      border-radius: 4px;
      z-index: 2147483646;
      font-size: 14px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.3);
      max-width: 300px;
      word-wrap: break-word;
      animation: nt-slideInRight 0.3s ease;
    `;

    successDiv.innerHTML = `
      <div style="display: flex; align-items: center;">
        <span style="margin-right: 8px;">✅</span>
        <span>${message}</span>
      </div>
    `;

    document.body.appendChild(successDiv);

    setTimeout(() => {
      if (successDiv.parentNode) {
        successDiv.style.animation = 'nt-slideOutRight 0.3s ease';
        setTimeout(() => {
          if (successDiv.parentNode) {
            successDiv.remove();
          }
        }, 300);
      }
    }, duration);
  }

  // 错误友好提示
  showErrorNotification(message, duration = 3000) {
    const errorDiv = document.createElement('div');
    errorDiv.style.cssText = `
      position: fixed;
      top: 20px;
      right: 20px;
      background: #f44336;
      color: white;
      padding: 12px 16px;
      border-radius: 4px;
      z-index: 2147483645;
      font-size: 14px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.3);
      max-width: 300px;
      word-wrap: break-word;
    `;
    errorDiv.textContent = message;

    document.body.appendChild(errorDiv);

    setTimeout(() => {
      if (errorDiv.parentNode) {
        errorDiv.remove();
      }
    }, duration);
  }

  // 恢复原始文本
  restoreOriginalText(textId) {
    const originalData = this.originalTextNodes.get(textId);
    if (originalData) {
      const { node, originalText } = originalData;
      node.textContent = originalText;

      // 移除翻译标记
      const parentElement = node.parentElement;
      if (parentElement) {
        parentElement.classList.remove('extreme-translated');
        parentElement.removeAttribute('data-original-text');
      }

      // 从当前翻译映射中移除
      this.currentTranslations.delete(textId);
      this.originalTextNodes.delete(textId);

      return true;
    }
    return false;
  }

  // 恢复所有原始文本
  restoreAllOriginalText() {
    for (const [textId] of this.originalTextNodes) {
      this.restoreOriginalText(textId);
    }
  }

  // 清理翻译
  cleanup() {
    this.restoreAllOriginalText();
    this.currentTranslations.clear();
    this.progressCallbacks = [];
    this.originalTextNodes.clear();

    // 清除自动关闭定时器
    if (this.autoCloseTimer) {
      clearTimeout(this.autoCloseTimer);
      this.autoCloseTimer = null;
    }
  }
}

// MutationHandler 模块 - DOM变化监测和增量更新
class MutationHandler {
  constructor() {
    this.observer = null;
    this.pendingMutations = [];
    this.debounceTimeout = null;
    this.batchProcessing = false;
    this.changeQueue = [];
    this.translationApplier = null;
    this.textRegistry = null;
    this.onChangeCallback = null;
  }

  // 启动MutationObserver
  start(translationApplier, textRegistry, onChangeCallback) {
    this.translationApplier = translationApplier;
    this.textRegistry = textRegistry;
    this.onChangeCallback = onChangeCallback;

    if (this.observer) {
      this.stop();
    }

    this.observer = new MutationObserver((mutations) => {
      this.handleMutations(mutations);
    });

    this.observer.observe(document.body, {
      childList: true,
      subtree: true,
      attributes: false,
      characterData: true,
      characterDataOldValue: true
    });

    console.log('🔄 MutationObserver 已启动，开始监测DOM变化');
  }

  // 停止MutationObserver
  stop() {
    if (this.observer) {
      this.observer.disconnect();
      this.observer = null;
      console.log('🔄 MutationObserver 已停止');
    }
  }

  // 处理DOM变化
  handleMutations(mutations) {
    // 防抖处理
    if (this.debounceTimeout) {
      clearTimeout(this.debounceTimeout);
    }

    // 添加突变到待处理队列
    mutations.forEach(mutation => {
      this.pendingMutations.push(mutation);
    });

    // 延迟处理，避免频繁触发
    this.debounceTimeout = setTimeout(() => {
      this.processPendingMutations();
    }, 100);
  }

  // 处理待处理的突变
  processPendingMutations() {
    if (this.batchProcessing || this.pendingMutations.length === 0) {
      return;
    }

    this.batchProcessing = true;

    try {
      // 合并相关突变
      const processedMutations = this.mergeRelatedMutations(this.pendingMutations);

      // 过滤无关突变（如样式变化、滚动等）
      const relevantMutations = processedMutations.filter(mutation =>
        this.isRelevantMutation(mutation)
      );

      if (relevantMutations.length > 0) {
        console.log(`🔄 检测到 ${relevantMutations.length} 个相关DOM变化，开始处理...`);

        // 添加到变更队列
        this.changeQueue.push(...relevantMutations);

        // 执行变更处理
        this.executeChangeProcessing();
      }

      this.pendingMutations = []; // 清空待处理队列
    } catch (error) {
      console.error('处理DOM变化失败:', error);
    } finally {
      this.batchProcessing = false;
    }
  }

  // 合并相关的突变
  mergeRelatedMutations(mutations) {
    const merged = [];
    const seenNodes = new Set();

    for (const mutation of mutations) {
      // 对于相同节点的多次变化，合并为一次处理
      if (mutation.target && !seenNodes.has(mutation.target)) {
        seenNodes.add(mutation.target);
        merged.push(mutation);
      } else if (mutation.type === 'childList') {
        // 对于子节点列表变化，检查是否已存在处理该父节点的变化
        let shouldAdd = true;
        for (const m of merged) {
          if (m.type === 'childList' && m.target === mutation.target) {
            shouldAdd = false;
            break;
          }
        }
        if (shouldAdd) {
          merged.push(mutation);
        }
      }
    }

    return merged;
  }

  // 判断是否为相关突变
  isRelevantMutation(mutation) {
    // 忽略样式变化
    if (mutation.type === 'attributes' &&
        ['style', 'class'].includes(mutation.attributeName)) {
      return false;
    }

    // 忽略不包含文本的元素变化
    if (mutation.type === 'childList') {
      for (const node of mutation.addedNodes) {
        if (this.containsTranslatableText(node)) {
          return true;
        }
      }
      for (const node of mutation.removedNodes) {
        if (this.containsTranslatableText(node)) {
          return true;
        }
      }
    }

    // 忽略字符数据变化中的纯空白变化
    if (mutation.type === 'characterData') {
      const oldValue = mutation.oldValue || '';
      const newValue = mutation.target.textContent || '';

      if (oldValue.trim() === newValue.trim()) {
        return false; // 仅空白字符变化
      }
    }

    // 其他类型的变化都认为是相关的
    return true;
  }

  // 检查节点是否包含可翻译文本
  containsTranslatableText(node) {
    if (node.nodeType === Node.TEXT_NODE) {
      return node.textContent.trim().length > 0;
    }

    if (node.nodeType === Node.ELEMENT_NODE) {
      // 检查元素是否包含文本节点
      const walker = document.createTreeWalker(
        node,
        NodeFilter.SHOW_TEXT,
        {
          acceptNode: function(node) {
            return node.textContent.trim().length > 0 ?
                   NodeFilter.FILTER_ACCEPT :
                   NodeFilter.FILTER_REJECT;
          }
        }
      );

      return walker.nextNode() !== null;
    }

    return false;
  }

  // 执行变更处理
  async executeChangeProcessing() {
    if (this.changeQueue.length === 0) {
      return;
    }

    // 收集新增的文本节点
    const newTextNodes = [];
    const removedTextNodes = [];

    for (const mutation of this.changeQueue) {
      if (mutation.type === 'childList') {
        // 处理新增节点
        for (const node of mutation.addedNodes) {
          this.collectTextNodes(node, newTextNodes);
        }

        // 处理移除节点
        for (const node of mutation.removedNodes) {
          this.collectTextNodes(node, removedTextNodes);
        }
      } else if (mutation.type === 'characterData') {
        // 处理文本内容变化
        newTextNodes.push(mutation.target);
      }
    }

    // 重置变更队列
    this.changeQueue = [];

    if (newTextNodes.length > 0) {
      console.log(`📄 发现 ${newTextNodes.length} 个新的可翻译文本节点`);

      // 按需翻译新增内容
      await this.translateNewContent(newTextNodes);
    }

    if (removedTextNodes.length > 0) {
      console.log(`🗑️ 检测到 ${removedTextNodes.length} 个被移除的文本节点，清理相关翻译覆盖`);

      // 清理相关翻译覆盖
      this.cleanupRemovedContent(removedTextNodes);
    }

    // 触发外部回调
    if (this.onChangeCallback) {
      this.onChangeCallback({
        newTexts: newTextNodes.length,
        removedTexts: removedTextNodes.length,
        timestamp: Date.now()
      });
    }
  }

  // 收集文本节点
  collectTextNodes(node, textNodes) {
    if (node.nodeType === Node.TEXT_NODE && node.textContent.trim().length > 0) {
      textNodes.push(node);
    } else if (node.nodeType === Node.ELEMENT_NODE) {
      // 使用TreeWalker递归收集
      const walker = document.createTreeWalker(
        node,
        NodeFilter.SHOW_TEXT,
        {
          acceptNode: function(node) {
            return node.textContent.trim().length > 0 ?
                   NodeFilter.FILTER_ACCEPT :
                   NodeFilter.FILTER_REJECT;
          }
        }
      );

      let currentNode;
      while ((currentNode = walker.nextNode())) {
        textNodes.push(currentNode);
      }
    }
  }

  // 按需翻译新内容
  async translateNewContent(textNodes) {
    if (!this.translationApplier || !this.textRegistry) {
      console.warn('翻译组件未初始化，跳过新内容翻译');
      return;
    }

    // 为新文本创建条目
    for (const node of textNodes) {
      const textContent = node.textContent.trim();
      const position = this.getTextPosition(node);
      const context = this.getContextAroundText(node);

      // 生成唯一的文本ID
      const textId = `dynamic_text_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

      // 注册新文本
      this.textRegistry.registerText(textId, textContent, context, position, {
        importance: 3, // 新内容重要性较低
        type: 'dynamic',
        visible: position?.visible || false
      });
    }

    // 从后端获取新文本的翻译（这里简化处理）
    console.log('💡 检测到新内容，通知后端获取翻译...');

    // 模拟发送请求到background script
    try {
      // 实际实现中应该发送消息给background获取翻译
      const response = await browser.runtime.sendMessage({
        action: 'translateDynamicContent',
        textIds: textNodes.map((_, idx) =>
          `dynamic_text_${Date.now()}_${idx}_${Math.random().toString(36).substr(2, 9)}`
        )
      });

      if (response.success && response.translations) {
        // 应用新翻译
        for (const translation of response.translations) {
          const entry = this.textRegistry.entries.get(translation.textId);
          if (entry) {
            this.translationApplier.applySingleTranslation(
              entry,
              translation.translation,
              response.bilingual || false
            );
          }
        }
      }
    } catch (error) {
      console.error('获取动态内容翻译失败:', error);
    }
  }

  // 清理已移除内容的翻译覆盖
  cleanupRemovedContent(removedNodes) {
    if (!this.translationApplier) return;

    for (const node of removedNodes) {
      // 如果是已翻译的节点，尝试恢复原文
      if (node.nodeType === Node.TEXT_NODE && node.parentElement?.classList.contains('extreme-translated')) {
        const textContent = node.textContent.trim();

        // 查找匹配的原始文本（如果能找到之前的映射）
        for (const [textId, originalData] of this.translationApplier.originalTextNodes) {
          if (originalData.originalText === textContent) {
            // 恢复原始文本
            this.translationApplier.restoreOriginalText(textId);
            console.log(`🧹 清理文本ID ${textId} 的翻译并恢复原文`);
            break;
          }
        }
      }
    }
  }

  // 获取文本位置信息
  getTextPosition(node) {
    if (!node.parentElement) return null;

    const rect = node.parentElement.getBoundingClientRect();
    return {
      x: rect.left,
      y: rect.top,
      width: rect.width,
      height: rect.height,
      visible: rect.bottom > 0 && rect.top < window.innerHeight,
      viewportRatio: Math.max(0, Math.min(1,
        (Math.min(window.innerHeight, rect.bottom) - Math.max(0, rect.top)) / rect.height
      ))
    };
  }

  // 获取文本上下文
  getContextAroundText(node) {
    const parent = node.parentElement;
    if (!parent) return '';

    const parentText = parent.textContent.trim();
    const nodeText = node.textContent.trim();

    if (parentText.length > 500) {
      const index = parentText.indexOf(nodeText);
      if (index !== -1) {
        const start = Math.max(0, index - 200);
        const end = Math.min(parentText.length, index + nodeText.length + 200);
        return parentText.substring(start, end);
      }
    }

    return parentText;
  }

  // 检查是否有活跃的观察器
  isActive() {
    return !!this.observer;
  }

  // 重置处理器状态
  reset() {
    this.stop();
    this.pendingMutations = [];
    this.changeQueue = [];
    this.batchProcessing = false;
    if (this.debounceTimeout) {
      clearTimeout(this.debounceTimeout);
      this.debounceTimeout = null;
    }
  }
}


// 翻译服务管理器
class TranslationService {
    constructor() {
        this.domWalker = new DOMWalker();
        this.textRegistry = new TextRegistry();
        this.translationApplier = new TranslationApplier();
        this.mutationHandler = new MutationHandler();
        this.isInitialized = false;
        this.isTranslating = false;  // 添加翻译状态锁

        this.init();
    }

    async init() {
        // 初始化各模块

        // 通知background当前页面已加载，重置状态为原始状态
        try {
            // 使用URL作为tab标识符，确保每次加载相同的URL时都能得到一致的标识符
            const urlHash = btoa(encodeURIComponent(window.location.href)).replace(/[^\w]/g, '');
            const tabIdentifier = `tab_${urlHash}`;

            await browser.runtime.sendMessage({
                action: 'pageLoaded',
                tabId: tabIdentifier,
                url: window.location.href
            });
        } catch (error) {
            // 如果background未准备好，忽略错误
        }

        // 设置消息监听器
        this.setupMessageListener();

        // 初始化用户交互事件监听器
        this.setupUserInteractionListener();

        // 初始化页面状态为原始状态
        this.translationApplier.updateTranslationStatus('original');

        this.isInitialized = true;
        console.log('⚙️ 翻译服务初始化完成');

        // 监听网页登录/登出事件，同步认证状态到扩展
        this.setupAuthSync();
    }

    // 设置认证同步监听
    setupAuthSync() {
        window.addEventListener('userLoggedIn', (event) => {
            try {
                const token = localStorage.getItem('authToken');
                const userStr = localStorage.getItem('userInfo');
                const userInfo = userStr ? JSON.parse(userStr) : null;

                if (token) {
                    browser.runtime.sendMessage({
                        action: 'setAuthToken',
                        token: token,
                        userInfo: userInfo
                    }).catch(() => {});
                    console.log('🔐 认证状态已同步到扩展');
                }
            } catch (error) {
                console.error('同步认证状态失败:', error);
            }
        });

        window.addEventListener('userLoggedOut', () => {
            // 仅在已知登录页域名下才清除扩展登录态，防止误触发
            if (!window.location.href.includes('localhost:7341')) {
                return;
            }
            browser.runtime.sendMessage({
                action: 'clearAuthToken'
            }).catch(() => {});
            console.log('🔐 扩展认证状态已清除');
        });
    }

    // 将错误消息转换为用户友好的消息
    getUserFriendlyErrorMessage(errorMsg) {
        // 基本错误消息
        let userMsg = '翻译出现异常';

        // 网络/连接错误
        if (errorMsg.includes('network') || errorMsg.includes('NetworkError') ||
            errorMsg.includes('fetch failed') || errorMsg.includes('Failed to fetch') ||
            errorMsg.includes('connection') || errorMsg.includes('timeout')) {
            userMsg = '服务器连接失败，请检查网络后重试';
        }
        // 服务器错误
        else if (errorMsg.includes('server') || errorMsg.includes('服务不可用') ||
                 errorMsg.includes('API') || errorMsg.includes('500') ||
                 errorMsg.includes('502') || errorMsg.includes('503') ||
                 errorMsg.includes('504')) {
            userMsg = '翻译服务暂时不可用，请稍后重试';
        }
        // 内容错误
        else if (errorMsg.includes('未找到可翻译') || errorMsg.includes('内容为空') ||
                 errorMsg.includes('empty')) {
            userMsg = '无法翻译该页面内容';
        }
        // 取消操作
        else if (errorMsg.includes('取消') || errorMsg.includes('cancel') ||
                 errorMsg.includes('cancelTranslation')) {
            userMsg = '翻译已取消';
        }

        // 移除技术细节
        userMsg = userMsg.replace(/http.*?1:7341/, '');
        userMsg = userMsg.replace(/Error:/, '');
        userMsg = userMsg.replace(/undefined|null/g, '');

        return userMsg;
    }

    // 设置用户交互事件监听器
    setupUserInteractionListener() {
        // 监听用户交互事件（点击、滚动、鼠标移动）
        const handleUserInteraction = () => {
            if (this.translationApplier) {
                this.translationApplier.userInteracted = true;
            }
        };

        // 点击事件
        document.addEventListener('click', handleUserInteraction, { passive: true });
        // 滚动事件
        window.addEventListener('scroll', handleUserInteraction, { passive: true });
        // 鼠标移动事件（防抖）
        let mouseMoveTimeout;
        window.addEventListener('mousemove', () => {
            if (mouseMoveTimeout) {
                clearTimeout(mouseMoveTimeout);
            }
            mouseMoveTimeout = setTimeout(handleUserInteraction, 100);
        }, { passive: true });
    }

    // 设置消息监听器
    setupMessageListener() {
        browser.runtime.onMessage.addListener((request, sender, sendResponse) => {
            switch (request.action) {
                case 'ping':
                    sendResponse({ active: true });
                    break;

                case 'translateWebPage':
                    this.handleTranslateWebPage(request, sender, sendResponse);
                    return true; // 异步响应

                case 'applyTranslations':
                    // 接收后台发送的翻译结果并应用
                    this.handleApplyTranslations(request, sender, sendResponse);
                    return true; // 异步响应

                // 流式翻译 - 单个翻译块
                case 'streamTranslationChunk':
                    this.handleStreamTranslationChunk(request);
                    sendResponse({ success: true });
                    break;

                // 流式翻译 - 完成通知
                case 'streamTranslationComplete':
                    this.handleStreamTranslationComplete(request);
                    sendResponse({ success: true });
                    break;

                // 流式翻译 - 错误通知
                case 'streamTranslationError':
                    console.error('❌ [流式] 翻译错误:', request.error);
                    this.translationApplier.showErrorNotification(request.error);

                    // 重置翻译状态
                    this.isTranslating = false;
                    console.log('✅ 翻译状态已重置（错误）');

                    sendResponse({ success: false });
                    break;

                case 'getPageTranslationStatus':
                    // 等待一小段时间以确保翻译应用完成，然后返回当前页面状态
                    setTimeout(() => {
                        sendResponse({ status: this.getCurrentPageStatus() });
                    }, 300); // 增加到300ms确保DOM更新完成
                    return true; // 异步响应

                case 'restoreOriginalText':
                    this.handleRestoreOriginalText(request, sender, sendResponse);
                    return true; // 异步响应

                case 'translationCompleted':
                    // 接收后台的翻译完成通知，不需要转发给其他地方
                    try {
                        sendResponse({ success: true, forwarded: false });
                    } catch (error) {
                        sendResponse({ success: false, error: error.message });
                    }
                    break;

                case 'toggleBilingualDisplay':
                    // 处理双语显示切换
                    try {
                        const showBilingual = request.showBilingual !== undefined ? request.showBilingual : true;
                        this.translationApplier.toggleAllBilingualDisplay(showBilingual);

                        // 向popup发送双语显示切换消息
                        try {
                            // 获取当前标签页ID并发送消息
                            browser.tabs.query({active: true, currentWindow: true}).then((tabs) => {
                                if (tabs[0] && tabs[0].id) {
                                    browser.runtime.sendMessage({
                                        action: 'bilingualDisplayToggled',
                                        showBilingual: showBilingual,
                                        tabId: tabs[0].id
                                    }).catch(error => {
                                        // 如果popup未打开，忽略错误
                                        console.log('发送双语切换消息到popup失败（可能popup未打开）:', error.message);
                                    });
                                }
                            }).catch(queryError => {
                                console.log('获取当前标签页失败:', queryError.message);
                                // 备用方案：发送不带tabId的消息
                                browser.runtime.sendMessage({
                                    action: 'bilingualDisplayToggled',
                                    showBilingual: showBilingual
                                }).catch(error => {
                                    console.log('发送双语切换消息到popup失败:', error.message);
                                });
                            });
                        } catch (popupError) {
                            console.log('发送双语切换消息到popup时发生错误:', popupError.message);
                        }

                        sendResponse({
                            success: true,
                            message: `双语显示已${showBilingual ? '开启' : '关闭'}`,
                            showBilingual: showBilingual
                        });
                    } catch (error) {
                        sendResponse({ success: false, error: error.message });
                    }
                    break;

        case 'toggleDisplayMode':
                    // 处理显示模式切换（原文/译文）
                    console.log('接收到 toggleDisplayMode 消息');
                    try {
                        this.translationApplier.toggleDisplayMode();

                        sendResponse({
                            success: true,
                            message: '显示模式已切换'
                        });
                        console.log('toggleDisplayMode 方法已调用');
                    } catch (error) {
                        console.error('切换显示模式时发生错误:', error);
                        sendResponse({ success: false, error: error.message });
                    }
                    break;

                case 'settingUpdated':
                    // 处理设置更新，例如双语显示偏好更改
                    try {
                        const { key, value } = request;

                        if (key === 'bilingual') {
                            // 如果双语设置发生变化，更新当前页面的双语显示状态
                            this.translationApplier.toggleAllBilingualDisplay(value);

                            console.log(`双语设置已更新: ${value}`);
                        } else if (key === 'engine' || key === 'target_lang' || key === 'source_lang') {
                            // 其他设置更新，可以在这里添加处理逻辑
                            console.log(`${key} 设置已更新: ${value}`);
                        }

                        sendResponse({
                            success: true,
                            message: `设置 ${key} 已更新`,
                            key: key,
                            value: value
                        });
                    } catch (error) {
                        console.error('处理设置更新时发生错误:', error);
                        sendResponse({ success: false, error: error.message });
                    }
                    break;

                case 'cancelTranslation':
                    // 处理取消翻译请求
                    try {
                        const result = this.cancelTranslation();
                        sendResponse(result);
                    } catch (error) {
                        sendResponse({ success: false, error: error.message });
                    }
                    break;

                default:
                    // 不返回响应，让消息继续传递
                    break;
            }
        });
    }

    // 获取当前页面翻译状态 - 改进版状态检测
    getCurrentPageStatus() {
        try {
            // 多重验证：检查多种翻译相关的类名和属性（适配 overlay 渲染）
            const translatedElements = document.querySelectorAll('.extreme-translated');
            const bilingualElements = document.querySelectorAll('.extreme-bilingual-text');
            const overlayElements = document.querySelectorAll('.extreme-translation-overlay, .extreme-direct-overlay');
            const originalContainerElements = document.querySelectorAll('.extreme-original-container');

            // 统计不同类型的元素数量
            const totalTranslated = translatedElements.length;
            const totalBilingual = bilingualElements.length;
            const totalOverlays = overlayElements.length;
            const totalOriginalContainers = originalContainerElements.length;

            // 如果存在任何翻译相关的元素，认为页面已被翻译
            if (totalTranslated > 0 || totalBilingual > 0 || totalOverlays > 0 || totalOriginalContainers > 0) {
                // 进一步验证这些元素是否真的包含了翻译内容
                let hasValidTranslation = false;

                // 检查是否有带有data-original-text属性的元素（这是翻译时添加的）
                const elementsWithDataOriginal = document.querySelectorAll('[data-original-text]');
                if (elementsWithDataOriginal.length > 0) {
                    hasValidTranslation = true;
                }

                // 检查bilingual元素是否有原始文本和翻译文本
                if (!hasValidTranslation && totalBilingual > 0) {
                    const firstBilingual = document.querySelector('.extreme-bilingual-text');
                    if (firstBilingual &&
                        firstBilingual.querySelector('.extreme-original-text') &&
                        firstBilingual.querySelector('.extreme-translated-text')) {
                        hasValidTranslation = true;
                    }
                }

                // 检查是否包含实际的翻译内容
                if (!hasValidTranslation) {
                    // 检查是否有一些包含翻译内容的标记
                    const translatedWrappers = document.querySelectorAll('.extreme-bilingual-text, .extreme-translated');
                    for (const wrapper of translatedWrappers) {
                        const hasOverlay = wrapper.querySelector('.extreme-translation-overlay') || wrapper.querySelector('.extreme-direct-overlay');
                        if (hasOverlay) {
                            hasValidTranslation = true;
                            break;
                        }

                        // 如果元素文本内容和data-original-text不同，也视为有效翻译
                        const originalText = wrapper.getAttribute('data-original-text');
                        if (originalText && originalText !== wrapper.textContent) {
                            hasValidTranslation = true;
                            break;
                        }
                    }
                }

                // 额外检测：检查页面上是否存在具有data-original-text属性的元素（这是翻译的标志）
                if (!hasValidTranslation) {
                    const elementsWithTranslationData = document.querySelectorAll('[data-original-text], [data-translated-text]');
                    if (elementsWithTranslationData.length > 0) {
                        hasValidTranslation = true;
                    }
                }

                // 如果找到了有效翻译标记，进一步判断当前显示的是原文还是译文
                if (hasValidTranslation) {
                    // 检查当前显示状态
                    const firstBilingual = document.querySelector('.extreme-bilingual-text');
                    if (firstBilingual) {
                        const transEl = firstBilingual.querySelector('.extreme-translated-text');
                        const origEl = firstBilingual.querySelector('.extreme-original-text');

                        if (transEl && origEl) {
                            // 检查哪个元素当前可见
                            const computedStyleTrans = window.getComputedStyle(transEl);
                            const computedStyleOrig = window.getComputedStyle(origEl);

                            if (computedStyleTrans.display !== 'none' && computedStyleOrig.display === 'none') {
                                return 'showing_translation'; // 显示译文
                            } else if (computedStyleOrig.display !== 'none' && computedStyleTrans.display === 'none') {
                                return 'showing_original'; // 显示原文
                            } else {
                                return 'translated'; // 一般已翻译状态
                            }
                        }
                    }

                    // 对于非双语元素，检查data-original-text属性来判断当前显示状态
                    const translatedEls = document.querySelectorAll('.extreme-translated');
                    if (translatedEls.length > 0) {
                        const firstTranslated = translatedEls[0];
                        const originalText = firstTranslated.getAttribute('data-original-text');
                        const currentText = firstTranslated.textContent;

                        if (originalText && currentText === originalText) {
                            return 'showing_original'; // 当前显示原文
                        } else if (originalText && currentText !== originalText) {
                            return 'showing_translation'; // 当前显示译文
                        }
                    }

                    return 'translated'; // 一般已翻译状态
                } else {
                    return 'translated_suspected';
                }
            }

            // 检查是否存在原文恢复的标记
            const restoredElements = document.querySelectorAll('.extreme-restored');
            if (restoredElements.length > 0) {
                return 'original';
            }

            // 额外检查：检查是否有任何带翻译数据属性的元素，即使它们没有被正确分类
            const allElementsWithTranslationData = document.querySelectorAll('[data-original-text]:not(.extreme-translated), [data-translated-text]:not(.extreme-translated)');
            if (allElementsWithTranslationData.length > 0) {
                // 有翻译数据，但没有极端类，可能处于某种中间状态
                const firstElement = allElementsWithTranslationData[0];
                const originalText = firstElement.getAttribute('data-original-text');
                const currentText = firstElement.textContent;

                if (originalText && currentText === originalText) {
                    return 'showing_original'; // 当前显示原文，但已翻译
                } else if (originalText && currentText !== originalText) {
                    return 'showing_translation'; // 当前显示译文，但已翻译
                } else {
                    return 'translated'; // 已翻译
                }
            }

            // 最终检查：如果没有找到任何特殊标记，则返回original
            return 'original';
        } catch (error) {
            console.warn('状态检测出错，返回默认值:', error);
            return 'original';
        }
    }

    // 处理恢复原文请求
    async handleRestoreOriginalText(request, sender, sendResponse) {
        // 如果正在翻译过程中，先停止当前翻译
        if (this.isTranslating) {
            this.isTranslating = false; // 取消当前翻译
            console.log('⚠️ 检测到正在翻译，已取消当前翻译进程');
        }

        try {
            console.log('🔄 开始恢复原文...');

            // 恢复所有被翻译的元素
            await this.restoreAllTranslatedElements();


            // 向background发送状态保存请求，以便更新按钮状态
            try {
                browser.runtime.sendMessage({
                    action: 'savePageStatus',
                    status: 'original',
                    tabId: sender.tab?.id
                }).catch(backgroundError => {
                    // 如果background未响应，这会产生错误，正常现象
                    console.log('Background可能未响应:', backgroundError.message);
                });
            } catch (err) {
                console.log('发送状态保存请求给background时出错:', err.message);
            }

            console.log('✅ 原文恢复完成');
            sendResponse({ success: true, message: '原文恢复完成', status: 'original' });

        } catch (error) {
            console.error('❌ 原文恢复失败:', error.message);

            // 向background发送状态保存请求
            try {
                browser.runtime.sendMessage({
                    action: 'savePageStatus',
                    status: 'translated',  // 保持原有状态
                    tabId: sender.tab?.id
                }).catch(backgroundError => {
                    // 如果background未响应，这会产生错误，正常现象
                    console.log('Background可能未响应:', backgroundError.message);
                });
            } catch (err) {
                console.log('发送状态保存请求给background时出错:', err.message);
            }

            sendResponse({ success: false, error: error.message });
        }
    }

    // 恢复所有已翻译的元素为原文
    async restoreAllTranslatedElements() {
        // 查找所有标记为已翻译的元素
        const translatedElements = document.querySelectorAll('.extreme-translated');

        for (const element of translatedElements) {
            // 如果元素具有data-original-text属性，恢复原文
            const originalText = element.getAttribute('data-original-text');
            if (originalText) {
                // 将元素的内容恢复为原始文本
                if (element.classList.contains('extreme-bilingual-text')) {
                    // 对于双语元素，恢复原始文本
                    element.innerHTML = originalText;
                } else {
                    // 对于普通元素，直接设置文本内容
                    element.textContent = originalText;
                }

                // 移除翻译相关的类和属性
                element.classList.remove('extreme-translated');
                element.removeAttribute('data-original-text');
            } else {
                // 如果没有原始文本记录，至少移除翻译标记
                element.classList.remove('extreme-translated');
                element.removeAttribute('data-original-text');
            }
        }

        // 同时需要处理极端情况，恢复可能被替换成wrapper元素的节点
        const allWrapperElements = document.querySelectorAll('.extreme-bilingual-text');
        for (const wrapper of allWrapperElements) {
            const originalText = wrapper.getAttribute('data-original-text');
            if (originalText) {
                // 恢复原始文本节点
                const textNode = document.createTextNode(originalText);
                if (wrapper.parentNode) {
                    wrapper.parentNode.replaceChild(textNode, wrapper);
                }
            }
        }
    }

    // 处理应用翻译请求（由后台主动发送翻译结果）
    async handleApplyTranslations(request, sender, sendResponse) {
        try {
            console.log('🔄 接收到后台发送的翻译结果:', request.translations);

            const { translations } = request;
            const bilingual = request.bilingual || false;

            if (!translations || translations.length === 0) {
                console.warn('⚠️ 没有翻译结果可应用');
                sendResponse({ success: false, error: '没有翻译结果可应用' });
                return;
            }

            // 使用TranslationApplier的渐进式更新来应用翻译
            await this.translationApplier.progressiveUpdate(translations, this.textRegistry, {
                bilingualDisplay: bilingual || false,
                updateStrategy: 'visible-first',
                batchSize: 10
            });

            // 启动DOM变化监测
            this.mutationHandler.start(this.translationApplier, this.textRegistry, (changes) => {
                console.log('监听页面内容变化:', changes);
            });

            // 向background发送状态保存请求，以便更新按钮状态
            try {
                browser.runtime.sendMessage({
                    action: 'savePageStatus',
                    status: 'showing_translation', // 使用更明确的状态
                    tabId: sender.tab?.id
                }).catch(backgroundError => {
                    console.log('Background可能未响应:', backgroundError.message);
                });
            } catch (err) {
                console.log('发送状态保存请求给background时出错:', err.message);
            }

            console.log('✅ 翻译结果应用完成');

            sendResponse({
                success: true,
                message: '翻译结果应用完成',
                stats: this.textRegistry.getStats(),
                status: 'translated'
            });

        } catch (error) {
            console.error('❌ 应用翻译结果失败:', error);

            // 向background发送状态保存请求，以便保持状态正确
            try {
                browser.runtime.sendMessage({
                    action: 'savePageStatus',
                    status: 'original',  // 保持原状态
                    tabId: sender.tab?.id
                }).catch(backgroundError => {
                    console.log('Background可能未响应:', backgroundError.message);
                });
            } catch (err) {
                console.log('发送状态保存请求给background时出错:', err.message);
            }

            // 显示错误信息
            // 5秒后自动隐藏错误提示
            setTimeout(() => {
                this.translationApplier.showErrorNotification('翻译出错: ' + error.message);
            }, 5000);

            sendResponse({ success: false, error: error.message });
        }
    }

    // 处理网页翻译请求
    async handleTranslateWebPage(request, sender, sendResponse) {
        // 防止重复请求
        if (this.isTranslating) {
            console.log('⚠️ 翻译已在进行中，忽略重复请求');
            sendResponse({ success: false, error: '翻译已在进行中，请稍候...' });
            return;
        }

        this.isTranslating = true;  // 设置翻译状态锁

        try {
            console.log('🚀 翻译开始...');

            // 显示顶部进度条（翻译进行中）
            this.translationApplier.showTranslationProgress();

            // 获取用户设置
            const settings = await this.getUserSettings();
            const sourceLang = request.sourceLang || 'auto';
            const targetLang = request.targetLang || settings.target_lang || 'zh';
            const engine = request.engine || settings.engine || 'google';
            const bilingual = request.bilingual || settings.bilingual_display || false;
            const expertMode = settings.expert_mode || false;

            console.log(`🔧 翻译设置: engine=${engine}, expertMode=${expertMode}, targetLang=${targetLang}`);

            // 如果启用了双语模式，提前注入样式
            if (bilingual) {
                this.translationApplier.injectBilingualStyles();
            }

            // 使用DOMWalker分析DOM结构并生成映射表（已按5,10,20,30,50分批）
            const scanResult = this.domWalker.collectTextByPhaseWithReadability(this.domWalker.aggressiveFilter);  // 混合模式：Readability + TreeWalker（使用宽松过滤器）
            const mappingTable = this.generateMappingTable(scanResult);

            if (!mappingTable || mappingTable.totalTexts === 0) {
                throw new Error('未找到可翻译的文本内容');
            }

            console.log(`📄 已生成映射表，共 ${mappingTable.totalTexts} 个文本节点，分 ${mappingTable.batchCount} 批`);
            console.log(`📊 批次大小分布:`, mappingTable.batches.map(batch => batch.length));

            // 将所有文本合并到一个数组中并注册到TextRegistry
            const allTexts = mappingTable.batches.flat();
            this.registerTextsToRegistry(allTexts);

            // 创建映射表并发送到 background 进行流式翻译
            const startTime = Date.now();
            const response = await browser.runtime.sendMessage({
                action: 'uploadMappingTableStream',
                mappingTable: {
                    totalTexts: mappingTable.totalTexts,
                    batches: mappingTable.batches,  // 发送批次数组
                    batchCount: mappingTable.batchCount,
                    language: mappingTable.language,
                    url: mappingTable.url,
                    timestamp: mappingTable.timestamp
                },
                sourceLang: sourceLang,
                targetLang: targetLang,
                engine: engine,
                bilingual: bilingual,
                fastMode: !expertMode,
                tabId: sender.tab?.id
            });

            const duration = Date.now() - startTime;
            console.log(`✅ 映射表已上传，耗时: ${duration}ms`);

            // 进度条已显示，不再单独弹出"翻译中"通知

            sendResponse({
                success: true,
                message: '映射表已上传，翻译进行中',
                stats: this.textRegistry.getStats(),
                status: 'translating',
                duration: duration
            });

        } catch (error) {
            console.error('网页翻译失败:', error);

            // 获取用户友好的错误消息
            const errorMsg = this.getUserFriendlyErrorMessage(error.message || String(error));

            // 隐藏进度条
            this.translationApplier.hideTranslationProgress();

            // 重置页面状态
            try {
                browser.runtime.sendMessage({
                    action: 'savePageStatus',
                    status: 'original',
                    tabId: sender.tab?.id
                }).catch(() => {});
            } catch (err) {}

            // 立即显示错误通知
            this.translationApplier.showErrorNotification(errorMsg);

            sendResponse({ success: false, error: errorMsg });
        } finally {
            // 注意：不在这里设置 this.isTranslating = false
            // 因为流式翻译还在进行中，等到翻译完成后再重置
        }
    }

    // 流式翻译块处理 - 由 background 主动推送
    async handleStreamTranslationChunk(chunk) {
        try {
            const { textId, original, translation, bilingual = false } = chunk;

            // 查找对应的文本条目
            const entry = this.textRegistry.entries.get(textId);
            if (!entry) {
                console.warn(`未找到文本ID ${textId} 的条目`);
                return false;
            }

            // 实时应用翻译
            const success = this.translationApplier.applySingleTranslation(entry, translation, bilingual);

            if (success) {
                console.log(`✅ [流式] 已应用翻译：${textId}`);

                // 更新进度条
                const stats = this.textRegistry.getStats();
                const translatedCount = stats.translatedCount;
                const totalCount = stats.totalEntries;
                this.translationApplier.updateTranslationProgress(translatedCount, totalCount);
            } else {
                console.warn(`⚠️ [流式] 应用翻译失败: ${textId}`);
            }

            return success;
        } catch (error) {
            console.error(`❌ [流式] 处理翻译块失败:`, error);
            return false;
        }
    }

    // 流式翻译完成处理
    async handleStreamTranslationComplete(result) {
        try {
            const { translations, sourceLang, targetLang, engine, bilingual = false } = result;

            console.log(`✅ [流式] 翻译完成，总计 ${translations.length} 个翻译`);

            // 启动DOM变化监测
            this.mutationHandler.start(this.translationApplier, this.textRegistry, () => {});

            // 向background发送状态保存请求
            try {
                browser.runtime.sendMessage({
                    action: 'savePageStatus',
                    status: 'showing_translation',
                    tabId: this.getCurrentTabId()
                }).catch(() => {});
            } catch (err) {}

            // 隐藏进度条（完成状态）
            this.translationApplier.hideTranslationProgress();

            // 重置翻译状态锁
            setTimeout(() => {
                this.isTranslating = false;
                console.log('✅ 翻译状态已重置');
            }, 500);

            return { success: true, translations };
        } catch (error) {
            console.error('❌ [流式] 处理翻译完成失败:', error);
            // 出错时也要重置状态
            this.isTranslating = false;
            return { success: false, error: error.message };
        }
    }

    // 取消正在进行的翻译请求
    cancelTranslation() {
        if (this.isTranslating) {
            this.isTranslating = false;
            console.log('翻译已取消');

            // 停止 MutationObserver
            if (this.mutationHandler) {
                this.mutationHandler.stop();
            }

            // 清理已经翻译的部分
            this.translationApplier.restoreAllOriginalText();

            // 向 background 发送状态更新
            try {
                browser.runtime.sendMessage({
                    action: 'savePageStatus',
                    status: 'original'
                }).catch(() => {});
            } catch (err) {}

            return { success: true, message: '翻译已取消' };
        }
        return { success: false, message: '没有正在进行的翻译' };
    }

    // 创建优先级批次
    createPriorityBatches(textRegistry, batchSize = 20) {
        // 按文章结构优先级排序：标题 -> 文章内容 -> 其他内容 -> 导航内容
        const sortedTexts = [...textRegistry].sort((a, b) => {
            // 从textRegistry中获取对应entry以获取重要性评分和元数据
            const aEntry = this.textRegistry.entries.get(a.id);
            const bEntry = this.textRegistry.entries.get(b.id);

            const aScore = aEntry ? (aEntry.metadata.importance || 5) : 5;
            const bScore = bEntry ? (bEntry.metadata.importance || 5) : 5;

            // 如果重要性相同，则根据文章结构进一步排序
            if (aScore === bScore) {
                const aTag = aEntry?.metadata?.tagName || '';
                const bTag = bEntry?.metadata?.tagName || '';

                // H1-H6 标题优先
                if (aTag.startsWith('H') && !bTag.startsWith('H')) return -1;
                if (!aTag.startsWith('H') && bTag.startsWith('H')) return 1;

                // ARTICLE、P 等文章内容其次
                const articleTags = ['ARTICLE', 'SECTION', 'P', 'MAIN'];
                const aIsArticle = articleTags.includes(aTag);
                const bIsArticle = articleTags.includes(bTag);

                if (aIsArticle && !bIsArticle) return -1;
                if (!aIsArticle && bIsArticle) return 1;

                // 导航类内容最后
                const navTags = ['NAV', 'HEADER', 'FOOTER', 'ASIDE'];
                const aIsNav = navTags.includes(aTag);
                const bIsNav = navTags.includes(bTag);

                if (aIsNav && !bIsNav) return 1;  // 导航内容排后面
                if (!aIsNav && bIsNav) return -1;

                // 如果标签类型相同，则优先首屏可见的内容
                const aVisible = aEntry?.position?.visible ? 1 : 0;
                const bVisible = bEntry?.position?.visible ? 1 : 0;
                return bVisible - aVisible;
            }

            return bScore - aScore; // 按重要性降序排列
        });

        // 分批
        const batches = [];
        for (let i = 0; i < sortedTexts.length; i += batchSize) {
            batches.push(sortedTexts.slice(i, i + batchSize));
        }

        return batches;
    }

    // 实时应用翻译结果（逐步更新，按重要性排序）
    async applyTranslationsRealtime(translations, bilingual, batchSize = 5) {
        if (!translations || translations.length === 0) {
            console.warn('⚠️ 没有翻译结果可应用');
            return;
        }

        console.log('🔄 开始实时应用翻译结果...');

        // 按照重要性排序翻译结果，优先翻译重要的内容
        const sortedTranslations = [...translations].sort((a, b) => {
            const aEntry = this.textRegistry.entries.get(a.textId);
            const bEntry = this.textRegistry.entries.get(b.textId);

            const aScore = aEntry ? (aEntry.metadata.importance || 5) : 5;
            const bScore = bEntry ? (bEntry.metadata.importance || 5) : 5;

            return bScore - aScore; // 按重要性降序排列
        });

        const total = sortedTranslations.length;
        let processed = 0;
        let successCount = 0;
        let errorCount = 0;

        // 分批次实时应用翻译
        for (let i = 0; i < sortedTranslations.length; i += batchSize) {
            const batch = sortedTranslations.slice(i, i + batchSize);

            // 同步处理当前批次
            for (const translation of batch) {
                const entry = this.textRegistry.entries.get(translation.textId);
                if (entry) {
                    const success = this.translationApplier.applySingleTranslation(entry, translation.translation, bilingual);

                    if (success) {
                        successCount++;
                    } else {
                        errorCount++;
                    }

                    processed++;

                    // 实时更新进度（仅在页面有大量文本时更新，避免过于频繁）
                    if (total > 50 && processed % Math.max(1, Math.floor(total / 20)) === 0) {
                        // 进度已在上级方法中更新，此处主要是应用翻译
                    }
                } else {
                    console.warn(`未找到文本ID ${translation.textId} 的条目`);
                    errorCount++;
                }
            }

            // 短暂延迟，让UI有机会更新，提供更流畅的实时体验
            if (i + batchSize < sortedTranslations.length) {
                await new Promise(resolve => setTimeout(resolve, 50)); // 减少延迟，加快实时性
            }
        }

        console.log(`✅ 实时翻译应用完成 (成功: ${successCount}, 失败: ${errorCount})`);
    }

    // 获取用户设置
    async getUserSettings() {
        try {
            const result = await browser.storage.local.get(['settings']);
            return result.settings || {};
        } catch (error) {
            console.error('获取设置失败:', error);
            return {};
        }
    }

    // 显示错误通知
    showErrorNotification(message, duration = 4000) {
        if (!this.translationApplier) {
            console.error('❌ 错误:', message);
            return;
        }

        this.showNotification(message, 'error', duration);
    }

    // 显示警告通知
    showWarningNotification(message, duration = 3000) {
        if (!this.translationApplier) {
            console.warn('⚠️ 警告:', message);
            return;
        }

        this.showNotification(message, 'warning', duration);
    }

    // 显示通知（通用方法）
    showNotification(message, type = 'info', duration = 3000) {
        if (!this.translationApplier) {
            console.log(message);
            return;
        }

        // 简化错误消息，对用户更友好
        let displayMessage = message;

        // 移除技术性错误信息
        displayMessage = displayMessage.replace(/第.*?批.*?失败/, '翻译处理异常');
        displayMessage = displayMessage.replace(/batch.*?failed/, '翻译处理异常');
        displayMessage = displayMessage.replace(/API.*?7341/, '服务器连接异常');
        displayMessage = displayMessage.replace(/http.*?1:7341/, '');
        displayMessage = displayMessage.replace(/Error:/, '');
        displayMessage = displayMessage.replace(/undefined/, '');
        displayMessage = displayMessage.replace(/null/, '');

        // 截断过长的消息
        if (displayMessage.length > 80) {
            displayMessage = displayMessage.substring(0, 80) + '...';
        }

        const colors = {
            error: { bg: '#f44336', icon: '❌' },
            warning: { bg: '#ff9800', icon: '⚠️' },
            info: { bg: '#2196F3', icon: 'ℹ️' },
            success: { bg: '#4CAF50', icon: '✅' }
        };

        const color = colors[type] || colors.info;

        const notificationDiv = document.createElement('div');
        notificationDiv.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            background: ${color.bg};
            color: white;
            padding: 12px 16px;
            border-radius: 4px;
            z-index: 2147483645;
            font-size: 14px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.3);
            max-width: 400px;
            word-wrap: break-word;
            animation: nt-slideInRight 0.3s ease;
        `;

        notificationDiv.innerHTML = `
            <div style="display: flex; align-items: center;">
                <span style="margin-right: 8px;">${color.icon}</span>
                <span>${displayMessage}</span>
            </div>
        `;

        document.body.appendChild(notificationDiv);

        setTimeout(() => {
            if (notificationDiv.parentNode) {
                notificationDiv.style.animation = 'nt-slideOutRight 0.3s ease';
                setTimeout(() => {
                    if (notificationDiv.parentNode) {
                        notificationDiv.remove();
                    }
                }, 300);
            }
        }, duration);
    }

    // 生成映射表（按5, 10, 20, 30, 50, 50, 50...分批，评分高优先，清洗文本）
    generateMappingTable(scanResult) {
        console.log('🔍 开始分析DOM结构...');

        const allTexts = [];

        // 按文章结构重要性顺序处理：首屏标题 -> 首屏主体内容 -> 首屏段落 -> 首屏其他 ->
        //                             下方主体内容 -> 下方标题 -> 下方段落 -> 下方其他
        // 注意：每个属性都是二维数组（批次数组），需要使用 flat() 展平
        // 使用可选链操作符 (?.) 处理某些字段可能不存在的情况
        const allBatches = [
            ...scanResult.firstScreenTitles?.flat() || [],
            ...scanResult.firstScreenMain?.flat() || [],
            ...scanResult.firstScreenParagraphs?.flat() || [],
            ...scanResult.firstScreenOther?.flat() || [],
            ...scanResult.belowFoldMain?.flat() || [],
            ...scanResult.belowFoldTitles?.flat() || [],
            ...scanResult.belowFoldParagraphs?.flat() || [],
            ...scanResult.belowFoldOther?.flat() || []
        ];

        let textId = 1;

        // 收集所有文本并清洗
        for (const node of allBatches) {
                // 确保node存在且有textContent属性
                if (node && typeof node.textContent === 'string') {
                    let textContent = node.textContent.trim();
                    if (textContent.length > 0) {
                        // 使用DOMWalker的评估器获取重要性评分和元数据
                        const evaluation = this.domWalker.evaluateNode(node);
                        const importance = evaluation.score || 5;

                        // 移除首尾空白，保持原文本格式
                        textContent = textContent.trim();

                        // 按评分高低排序
                        allTexts.push({
                            id: `text_${textId++}`,
                            original: textContent,
                            context: evaluation.context,
                            position: evaluation.position,
                            metadata: {
                                ...evaluation.metadata,
                                importance: importance
                            }
                        });
                    }
                }
        }

        // 按重要性评分降序排序（评分高优先）
        allTexts.sort((a, b) => {
            const scoreA = a.metadata.importance || 5;
            const scoreB = b.metadata.importance || 5;
            return scoreB - scoreA; // 高分在前
        });

        console.log('✅ DOM分析完成，共找到', allTexts.length, '个文本节点，已按评分排序');

        // 按批次大小计划分批：5, 10, 20, 30, 50, 50, 50...
        const batchSizeSchedule = [10, 20, 50, 100, 100];
        const batches = [];
        let currentIndex = 0;

        // 使用预定义的批次大小计划分批
        for (let i = 0; i < batchSizeSchedule.length && currentIndex < allTexts.length; i++) {
            const batchSize = batchSizeSchedule[i];
            const batch = allTexts.slice(currentIndex, currentIndex + batchSize);
            batches.push(batch);
            currentIndex += batchSize;
        }

        // 剩余文本按50个一批处理
        const defaultBatchSize = 100;
        while (currentIndex < allTexts.length) {
            const batch = allTexts.slice(currentIndex, currentIndex + defaultBatchSize);
            batches.push(batch);
            currentIndex += defaultBatchSize;
        }

        console.log('📊 映射表已分批: 批次数=' + batches.length + ', 每批大小=',
            batches.map(batch => batch.length));

        // 返回批次数组（而不是合并的textRegistry）
        return {
            totalTexts: allTexts.length,
            batches: batches,  // 返回批次数组
            batchCount: batches.length,
            language: this.detectLanguage(),
            url: window.location.href,
            timestamp: Date.now()
        };
    }

    // 注册文本到TextRegistry
    registerTextsToRegistry(textRegistry) {
        // 已在generateMappingTable中按评分排序，此处直接使用

        // 将文本分批（5, 10, 20, 30, 50, 50, 50...）
        const batchSizeSchedule = [10, 20, 50, 100, 100];
        let currentIndex = 0;
        let currentBatchIndex = 0;
        const batches = [];

        // 使用预定义的批次大小计划分批
        for (let i = 0; i < batchSizeSchedule.length && currentIndex < textRegistry.length; i++) {
            const batchSize = batchSizeSchedule[i];
            const batch = textRegistry.slice(currentIndex, currentIndex + batchSize);
            batches.push(batch);
            currentIndex += batchSize;
        }

        // 剩余文本按50个一批处理
        const defaultBatchSize = 100;
        while (currentIndex < textRegistry.length) {
            const batch = textRegistry.slice(currentIndex, currentIndex + defaultBatchSize);
            batches.push(batch);
            currentIndex += defaultBatchSize;
        }

        // 为每批分配批次编号并注册
        batches.forEach((batch, batchIndex) => {
            batch.forEach((entry, entryIndex) => {
                const importance = entry.metadata.importance || 5;

                const metadata = {
                    importance: importance,
                    visible: entry.position?.visible || false,
                    type: 'text',
                    tagName: entry.metadata?.tagName || '',
                    className: entry.metadata?.className || '',
                    id: entry.metadata?.id || '',
                    batchNumber: batchIndex,  // 记录批次编号
                    batchPosition: entryIndex  // 记录在批次中的位置
                };

                this.textRegistry.registerText(
                    entry.id,
                    entry.original,
                    entry.context,
                    entry.position,
                    metadata
                );
            });
        });

        console.log(`📊 文本已按优先级分批: 批次数=${batches.length}, 每批大小=`,
            batches.map(batch => batch.length));

        // 按语义和视觉布局分组
        this.textRegistry.groupByTextSemantics(this.domWalker);
        this.textRegistry.groupByVisualLayout();
        this.textRegistry.groupByInteractionState();
    }

    // 估算文本重要性（改进版）
    estimateImportance(text, position, metadata = {}) {
        let score = 5; // 默认分值

        // 确保文本参数存在
        if (!text) return score;

        // 确保文本是字符串
        if (typeof text !== 'string') {
            text = String(text);
        }

        // 根据元数据中的标签类型评分
        const tagName = metadata.tagName || '';
        const className = metadata.className || '';
        const id = metadata.id || '';

        // 按文章结构优先级评分
        if (['H1', 'H2', 'H3', 'H4', 'H5', 'H6'].includes(tagName)) {
            score += 5; // 标题最高优先级
        } else if (tagName === 'P' || tagName === 'ARTICLE' || tagName === 'SECTION' || tagName === 'MAIN') {
            score += 3; // 文章内容高优先级
        } else if (['BUTTON', 'A', 'LABEL'].includes(tagName)) {
            score += 1; // 交互元素中等优先级
        } else if (['NAV', 'UL', 'HEADER', 'FOOTER', 'ASIDE'].includes(tagName)) {
            score -= 2; // 导航元素较低优先级
        }

        // 根据类名评分
        if (/(title|headline|article|post|content|main|text|body)/i.test(className)) {
            if (tagName.startsWith('H')) {
                score += 5; // 标题类文章内容
            } else {
                score += 3; // 文章内容
            }
        } else if (/(nav|menu|sidebar|widget|ad|banner|footer|header|social|search)/i.test(className)) {
            score -= 2; // 导航类内容
        }

        // 根据ID评分
        if (/(title|headline|article|post|content|main)/i.test(id)) {
            score += 4; // 文章相关ID
        } else if (/(nav|menu|sidebar|footer|header)/i.test(id)) {
            score -= 2; // 导航相关ID
        }

        // 根据文本长度调整
        if (text.length < 2) score -= 2; // 太短的文本
        else if (text.length >= 10 && text.length <= 200) score += 1; // 合适长度的文本
        else if (text.length > 200) score += 0.5; // 很长的文本（可能是文章内容）
        else if (text.length > 500) score -= 1; // 过长的文本可能不是关键内容

        // 根据在页面中的位置调整
        if (position && typeof position === 'object') {
            if (typeof position.y === 'number' && position.y < window.innerHeight) { // 首屏内容
                score += 2;
            }
            if (position.visible === true) { // 可见文本
                score += 1;
            }
        }

        // 根据文本特征调整
        if (/[A-Z][A-Z\s]{5,}/.test(text)) { // 可能是标题
            score += 2;
        }

        // 确保text是字符串后再调用trim
        const trimmedText = typeof text === 'string' ? text.trim() : String(text).trim();
        if (/^[A-Za-z\s]{3,}$/.test(trimmedText) && text.length > 5) { // 纯字母文本
            score += 1;
        }

        // 限制在1-10范围内
        return Math.min(10, Math.max(1, score));
    }

    // 获取文本位置信息
    getTextPosition(node) {
        const rect = node.parentElement.getBoundingClientRect();
        return {
            x: rect.left,
            y: rect.top,
            width: rect.width,
            height: rect.height,
            visible: rect.bottom > 0 && rect.top < window.innerHeight
        };
    }

    // 获取文本上下文
    getContextAroundText(node) {
        const parent = node.parentElement;
        if (!parent) return '';

        // 获取父元素的文本内容作为上下文
        const parentText = parent.textContent.trim();
        const nodeText = node.textContent.trim();

        // 如果父元素文本太长，只取包含当前文本的部分
        if (parentText.length > 500) {
            const index = parentText.indexOf(nodeText);
            if (index !== -1) {
                const start = Math.max(0, index - 200);
                const end = Math.min(parentText.length, index + nodeText.length + 200);
                return parentText.substring(start, end);
            }
        }

        return parentText;
    }

    // 检测页面语言
    detectLanguage() {
        // 优先使用HTML lang属性
        const htmlLang = document.documentElement.lang;
        if (htmlLang) {
            return htmlLang.split('-')[0];
        }

        // 尝试从meta标签获取
        const metaLang = document.querySelector('meta[http-equiv="Content-Language"]');
        if (metaLang && metaLang.content) {
            return metaLang.content.split(',')[0].trim();
        }

        // 默认返回auto
        return 'auto';
    }

    // 应用翻译结果
    applyTranslations(translations, bilingual) {
        if (!translations || translations.length === 0) {
            console.warn('⚠️ 没有翻译结果可应用');
            return;
        }

        console.log('🔄 开始应用翻译结果...');

        // 使用TranslationApplier的渐进式更新
        this.translationApplier.progressiveUpdate(translations, this.textRegistry, {
            bilingualDisplay: bilingual,
            updateStrategy: 'visible-first',
            batchSize: 10
        }).then(() => {
            console.log('✅ 翻译应用完成');
        }).catch(error => {
            console.error('❌ 翻译应用失败:', error);
        });
    }

    // 启动MutationObserver监控DOM变化
    startMutationObserver() {
        this.mutationHandler.start(this.translationApplier, this.textRegistry, (changes) => {
            console.log('监听页面内容变化:', changes);
        });
    }

    // 清理翻译
    cleanupTranslations() {
        this.translationApplier.cleanup();
        this.mutationHandler.stop();

        this.textRegistry.clear();

        // 清除自动关闭定时器
        if (this.translationApplier?.autoCloseTimer) {
            clearTimeout(this.translationApplier.autoCloseTimer);
            this.translationApplier.autoCloseTimer = null;
        }

        console.log('🧹 翻译清理完成');
    }
}

// 初始化翻译服务
const translationService = new TranslationService();

// 创建全局引用以便 MessageHandler 可以访问
globalThis.mainTranslationService = translationService;

// 页面卸载时清理
window.addEventListener('beforeunload', () => {
    translationService.cleanupTranslations();
});

// ===== Web 登录态桥接 =====
// 当用户在 web 端已登录时，自动同步登录态到扩展
(function syncAuthToExtension() {
    try {
        const token = localStorage.getItem('authToken');
        if (token) {
            const userInfo = localStorage.getItem('userInfo');
            browser.runtime.sendMessage({
                action: 'setAuthToken',
                token: token,
                userInfo: userInfo ? JSON.parse(userInfo) : {}
            }).catch(() => {
                // background 未就绪时忽略
            });
            console.log('[NovelTrans] 登录态已同步到扩展');
        } else {
            // web 端未登录，不应清除扩展已保存的登录态
            // 扩展的登录态应由扩展自身（popup/背景脚本）管理
            // browser.runtime.sendMessage({
            //     action: 'clearAuthToken'
            // }).catch(() => {});
        }
    } catch (e) {
        // 非 web 页面（无 authToken）时静默跳过
    }
})();