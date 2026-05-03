// content.js - 网页翻译核心逻辑（模式1: 整个网页翻译）
// DOM 识别算法：深度优先遍历 + 语义单元分组 + 文本占位符保护

// DOMTranslator 模块内联到 content.js 中（浏览器扩展不支持 ES module import）
// 见下方 DOMTranslator 类定义

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

    /* 翻译 wrapper — 平行兄弟元素（由 injectBilingualStyles 统一注入） */
    .extreme-translation-wrapper {
      display: inline !important;
      white-space: pre-line !important;
    }

    /* DOM 译文样式 */


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

// 网页翻译 DOM 扫描与翻译应用（原创实现）
// 算法思路：TreeWalker 遍历叶子文本节点 → 按块级祖先聚类分组 → 容器克隆提取文本

// HTML 转义（防止翻译结果中的特殊字符破坏 DOM）
function _escapeHTML(str) {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// 译文容器类名
const _TRANS_CONTAINER = {
  wrapperClass: 'dom-trans-wrapper',
  textClass: 'dom-trans-text',
};

// 自动跳过的文本模式（URL、邮箱、时间戳等常见非翻译内容）
const _SKIP_PATTERNS = [
  /^(?:(?:https?|ftp):\/\/|www\.)[^\s/$.?#].[^\s]*$/i,
  /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/,
  /^[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$/,
  /^v?\d+(?:\.\d+){1,3}$/,
  /^({{[^}]+}}|\${[^}]+}|__\w+__|%\w+)$/,
  /^&\w{1,10};$/,
  /^\[\d+\]$/,
  /^\d{1,2}:\d{2}(:\d{2})?$/,
  /^#\d{3,6}$/,
  /^\/\w+\/\w+/,       // URL路径 /a/b
];

// 不应作为文本目标的元素选择器
const _IGNORE_SELECTOR = `
  script, style, noscript, svg, math, iframe,
  object, embed, template, textarea, input, select,
  .notranslate, [translate='no'], [contenteditable='true'],
  .${_TRANS_CONTAINER.wrapperClass}
`;

// DOM 翻译器核心类
// 使用 TreeWalker 直接定位叶子文本节点，而非递归遍历整个 DOM 树
class DOMTranslator {
  constructor(config = {}) {
    this.targetLang = config.targetLang || 'zh';
    this.sourceLang = config.sourceLang || 'auto';
    this.engine = config.engine || 'google';
    this.showBilingual = config.bilingual || false;
    this.minLength = config.minTextLength || 2;
    this.maxLength = config.maxTextLength || 5000;
    this.autoMode = config.autoScan !== false;
    this.cssSelector = config.selector || 'p, li, h1, h2, h3, h4, h5, h6, td, th, blockquote';

    // 翻译状态存储
    this._nodeRegistry = new Map();
    this._wrapperLookup = new Map();
    this._skipSet = new RegExp(_SKIP_PATTERNS.map(p => `(${p.source})`).join('|'));

    // 切换模式：true=显示译文（默认），false=显示原文
    this._showingTranslation = true;

    // 计数器
    this._idCounter = 0;
    this._scanVersion = 0;

    // 动态内容监控
    this._mutationObserver = null;
    this._pendingRescans = new Set();
    this._isRescanning = false;
    this._onContentChanged = null;

    // getComputedStyle 结果缓存
    this._displayCache = new WeakMap();
  }

  /**
   * 判断元素是否为块级容器（基于浏览器布局引擎，非标签名硬编码）
   */
  static _isBlock(el, displayCache) {
    if (!(el instanceof Element)) return false;

    // TABLE 是块级但 computed display 可能为 'table'
    if (el.nodeName === 'TABLE') return true;

    try {
      if (displayCache?.has(el)) return displayCache.get(el);
      const display = window.getComputedStyle(el).display;
      // block / flex / grid / table-* 均为块级
      const block = display !== 'inline' && !display.startsWith('inline');
      displayCache?.set(el, block);
      return block;
    } catch {
      return false;
    }
  }

  /**
   * 查找文本节点的最近块级祖先（翻译单元容器）
   */
  _nearestBlock(textNode) {
    let node = textNode.parentElement;
    while (node && node !== document.body) {
      if (DOMTranslator._isBlock(node, this._displayCache)) return node;
      node = node.parentElement;
    }
    return node; // document.body 或 null
  }

  /**
   * 检测文本节点是否位于应忽略的元素内部
   */
  static _insideIgnored(textNode) {
    let node = textNode.parentElement;
    while (node && node !== document.body) {
      if (node.matches?.(_IGNORE_SELECTOR)) return true;
      node = node.parentElement;
    }
    return false;
  }

  /**
   * 识别结构性断裂元素（分隔翻译单元）
   */
  static _isStructuralBreak(el) {
    if (!(el instanceof Element)) return false;
    const tag = el.nodeName;
    if (tag === 'BR' || tag === 'HR') return true;
    return el.matches?.(_IGNORE_SELECTOR);
  }

  /**
   * 入口：扫描整个文档树
   */
  scan(root = document.body) {
    this._scanVersion++;
    this._nodeRegistry.clear();
    this._idCounter = 0;

    const resultBatches = [];
    let activeBatch = [];

    const pushEntry = (entry) => {
      if (!entry) return;
      if (Array.isArray(entry)) {
        for (const item of entry) pushEntry(item);
      } else {
        activeBatch.push(entry);
        if (activeBatch.length >= 20) {
          resultBatches.push(activeBatch);
          activeBatch = [];
        }
      }
    };

    if (this.autoMode) {
      this._scanByTreeWalker(root, pushEntry);
    } else {
      this._scanBySelector(root, pushEntry);
    }

    if (activeBatch.length > 0) resultBatches.push(activeBatch);

    return {
      totalTexts: this._idCounter,
      batches: resultBatches,
      batchCount: resultBatches.length,
      url: window.location.href,
      timestamp: Date.now(),
    };
  }

  /**
   * TreeWalker 扫描：遍历所有叶子文本节点，按块级祖先分组
   */
  _scanByTreeWalker(root, emitEntry) {
    if (!(root instanceof Element || root instanceof DocumentFragment)) return;
    if (root.matches?.(_IGNORE_SELECTOR)) return;

    // 收集所有叶子文本节点
    const leafTexts = [];
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
      acceptNode: (node) => {
        if (DOMTranslator._insideIgnored(node)) return NodeFilter.FILTER_REJECT;
        const parent = node.parentElement;
        if (parent && parent.childNodes.length > 1) return NodeFilter.FILTER_ACCEPT;
        if (parent && parent.childNodes.length === 1 && parent.textContent.trim()) {
          return NodeFilter.FILTER_ACCEPT;
        }
        return NodeFilter.FILTER_ACCEPT;
      }
    });

    let node;
    while ((node = walker.nextNode())) {
      if (/\S/.test(node.nodeValue)) {
        leafTexts.push(node);
      }
    }

    // 按块级祖先分组，同时考虑结构断裂
    const groups = this._groupTextsByBlock(leafTexts);
    for (const group of groups) {
      const entry = this._buildEntry(group);
      if (entry) emitEntry(entry);
    }
  }

  /**
   * 将叶子文本节点按最近块级祖先分组
   * 结构元素（BR/HR/忽略元素）自动断组
   */
  _groupTextsByBlock(leafTexts) {
    if (leafTexts.length === 0) return [];

    const groups = [];
    let currentGroup = [];
    let currentBlock = null;

    for (let i = 0; i < leafTexts.length; i++) {
      const textNode = leafTexts[i];
      const block = this._nearestBlock(textNode);

      // 检查从上一个文本节点到当前文本节点之间是否存在结构断裂
      let hasBreak = false;
      if (i > 0) {
        const prevText = leafTexts[i - 1];
        hasBreak = this._hasStructuralBreakBetween(prevText, textNode);
      }

      // 块级祖先改变或遇到结构断裂 → 结束当前组
      const shouldSplit = !currentGroup || block !== currentBlock || hasBreak;
      if (shouldSplit && currentGroup.length > 0) {
        groups.push(currentGroup);
        currentGroup = [];
      }

      currentBlock = block;
      currentGroup.push(textNode);
    }

    if (currentGroup.length > 0) groups.push(currentGroup);
    return groups;
  }

  /**
   * 检测两个文本节点之间是否存在结构性断裂元素
   * 通过检查从 nodeA 到 nodeB 的文档序路径上是否有 BR/HR/忽略元素
   */
  _hasStructuralBreakBetween(nodeA, nodeB) {
    const parentA = nodeA.parentElement;
    const parentB = nodeB.parentElement;
    if (!parentA || !parentB) return false;

    // 同父节点时，检查两节点之间的兄弟是否有断裂元素
    if (parentA === parentB) {
      const children = parentA.childNodes;
      const idxA = Array.prototype.indexOf.call(children, nodeA);
      const idxB = Array.prototype.indexOf.call(children, nodeB);
      for (let i = idxA + 1; i < idxB; i++) {
        if (DOMTranslator._isStructuralBreak(children[i])) return true;
      }
      return false;
    }

    // 不同父节点：查找 LCA，检查路径上是否有断裂
    const ancestorsA = new Map();
    let cur = parentA;
    let depth = 0;
    while (cur && cur !== document.body) {
      ancestorsA.set(cur, depth);
      cur = cur.parentElement;
      depth++;
    }

    let curB = parentB;
    let lca = null;
    while (curB && curB !== document.body) {
      if (ancestorsA.has(curB)) {
        lca = curB;
        break;
      }
      curB = curB.parentElement;
    }
    if (!lca) return true; // 没有共同祖先（极端情况）

    // 检查 nodeA 父链到 LCA 之间是否有断裂
    cur = parentA;
    while (cur && cur !== lca) {
      if (DOMTranslator._isStructuralBreak(cur)) return true;
      cur = cur.parentElement;
    }

    // 检查 nodeB 父链到 LCA 之间是否有断裂
    cur = parentB;
    while (cur && cur !== lca) {
      if (DOMTranslator._isStructuralBreak(cur)) return true;
      cur = cur.parentElement;
    }

    return false;
  }

  /**
   * 非自动模式：通过 CSS 选择器收集文本
   */
  _scanBySelector(root, emitEntry) {
    if (!(root instanceof Element || root instanceof DocumentFragment)) return;

    const matches = root.matches?.(this.cssSelector) ? [root] : [];
    root.querySelectorAll(this.cssSelector).forEach(el => {
      if (!el.closest?.(_IGNORE_SELECTOR)) matches.push(el);
    });

    for (const el of matches) {
      const leafTexts = [];
      const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT, {
        acceptNode: (node) => {
          if (DOMTranslator._insideIgnored(node)) return NodeFilter.FILTER_REJECT;
          return NodeFilter.FILTER_ACCEPT;
        }
      });
      let node;
      while ((node = walker.nextNode())) {
        if (/\S/.test(node.nodeValue)) leafTexts.push(node);
      }
      if (leafTexts.length > 0) {
        const entry = this._buildEntry(leafTexts);
        if (entry) emitEntry(entry);
      }
    }
  }

  /**
   * 从一组文本节点构建翻译条目
   */
  _buildEntry(textNodes) {
    if (!textNodes || textNodes.length === 0) return null;

    const container = textNodes[0].parentElement;
    if (!container) return null;

    // 通过克隆容器提取纯文本
    const plainText = this._extractText(textNodes);
    if (!this._shouldTranslate(plainText)) return null;

    // 序列化：HTML 转义
    const serialized = this._serializeText(textNodes);
    if (!this._shouldTranslate(serialized)) return null;

    this._idCounter++;
    const entryId = `text_${this._idCounter}`;

    this._nodeRegistry.set(entryId, {
      sourceNodes: textNodes,
      container,
      originalText: plainText,
    });

    return {
      id: entryId,
      original: serialized,
      context: plainText.length < 50 ? null : plainText,
      position: { visible: this._checkViewport(container) },
      metadata: {
        sourceNodes: textNodes,
        container,
        hasPlaceholder: false,
        detectedLang: null,
        preTranslated: false,
      },
    };
  }

  /**
   * 仅提取本组文本节点的纯文本内容
   */
  _extractText(textNodes) {
    if (textNodes.length === 0) return '';
    return textNodes.map(n => n.textContent || '').join('');
  }

  /**
   * 将本组文本节点内容序列化为翻译用字符串（HTML 转义）
   */
  _serializeText(textNodes) {
    if (textNodes.length === 0) return '';
    return _escapeHTML(textNodes.map(n => n.textContent || '').join(''));
  }

  /**
   * 判断文本是否应该翻译
   */
  _shouldTranslate(text) {
    if (typeof text !== 'string') return false;
    const trimmed = text.trim();
    if (trimmed.length < this.minLength || trimmed.length > this.maxLength) return false;
    if (trimmed.length === 1 && !/[a-zA-Z一-鿿぀-ゟ゠-ヿ]/.test(trimmed)) return false;
    if (!isNaN(parseFloat(trimmed)) && isFinite(trimmed)) return false;
    if (this._skipSet.test(trimmed)) return false;
    return true;
  }

  /**
   * 将翻译结果应用到 DOM
   */
  apply(textId, translatedContent) {
    const entry = this._nodeRegistry.get(textId);
    if (!entry) return false;

    const { sourceNodes, container } = entry;
    if (!sourceNodes || sourceNodes.length === 0) return false;

    // 创建译文容器
    const wrapper = document.createElement('dom-trans-container');
    wrapper.className = `${_TRANS_CONTAINER.wrapperClass} notranslate`;

    const separator = document.createElement('br');
    separator.hidden = !this.showBilingual;
    wrapper.appendChild(separator);

    const textEl = document.createElement('font');
    textEl.className = `${_TRANS_CONTAINER.textClass}`;
    textEl.lang = this.targetLang;
    textEl.innerHTML = translatedContent;
    wrapper.appendChild(textEl);

    // 插入到最后一个源节点之后
    try {
      sourceNodes[sourceNodes.length - 1].after(wrapper);
    } catch (e) {
      console.warn('[DOMTranslator] 插入译文失败:', e);
      return false;
    }

    const sourceText = sourceNodes.map(n => n.textContent || '').join('');

    const wrapperInfo = {
      sourceNodes,
      sourceText,
      container,
      hidden: !this.showBilingual,
      hiddenElements: [],
      sourceVisible: this.showBilingual,
      sourceSpan: null,
    };
    this._wrapperLookup.set(wrapper, wrapperInfo);

    if (!this.showBilingual) {
      wrapperInfo.hiddenElements = this._hideSourceNodes(sourceNodes);
      wrapperInfo.sourceVisible = false;
    }
    return true;
  }

  /**
   * 隐藏原文节点，返回创建的隐藏元素数组
   */
  _hideSourceNodes(nodes) {
    const hiddenElements = [];
    for (const node of nodes) {
      if (node.nodeType === Node.TEXT_NODE) {
        const hiddenSpan = document.createElement('span');
        hiddenSpan.hidden = true;
        hiddenSpan.textContent = node.textContent;
        try { node.replaceWith(hiddenSpan); } catch {}
        hiddenElements.push(hiddenSpan);
      } else if (node.nodeType === Node.ELEMENT_NODE) {
        try { node.hidden = true; } catch {}
        hiddenElements.push(node);
      }
    }
    return hiddenElements;
  }

  /**
   * 恢复原文显示
   */
  showSource(wrapper) {
    const info = this._wrapperLookup.get(wrapper);
    if (!info) return;

    // 如果有 sourceSpan（双语模式下创建的原文包裹），显示它
    if (info.sourceSpan && document.contains(info.sourceSpan)) {
      info.sourceSpan.hidden = false;
    }

    // 如果有 hiddenElements 中的隐藏 span，恢复为文本
    const hiddenElements = info.hiddenElements;
    if (hiddenElements && hiddenElements.length > 0) {
      const restoredNodes = [];
      for (const el of hiddenElements) {
        if (el instanceof HTMLSpanElement && el.hidden) {
          const textNode = document.createTextNode(el.textContent);
          restoredNodes.push(textNode);
          try {
            wrapper.parentNode?.insertBefore(textNode, wrapper);
            el.remove();
          } catch (e) {
            console.warn(`[showSource] 恢复失败: ${e.message}`);
          }
        } else if (el.nodeType === Node.ELEMENT_NODE && el.hidden) {
          try { el.hidden = false; } catch {}
          restoredNodes.push(el);
        }
      }
      this._wrapperLookup.set(wrapper, { ...info, hidden: false, hiddenElements: restoredNodes, sourceVisible: true });
    } else {
      this._wrapperLookup.set(wrapper, { ...info, hidden: false, sourceVisible: true });
    }
  }

  /**
   * 隐藏原文
   */
  hideSource(wrapper) {
    const info = this._wrapperLookup.get(wrapper);
    if (!info) return;

    // 双语模式下，原文应始终可见，不隐藏
    if (this.showBilingual) {
      this._wrapperLookup.set(wrapper, { ...info, hidden: false, sourceVisible: true });
      return;
    }

    const newHiddenElements = [];

    // 如果有 sourceSpan，隐藏它
    if (info.sourceSpan && document.contains(info.sourceSpan)) {
      info.sourceSpan.hidden = true;
      newHiddenElements.push(info.sourceSpan);
      this._wrapperLookup.set(wrapper, { ...info, hidden: true, hiddenElements: newHiddenElements, sourceVisible: false });
      return;
    }

    // 如果有 hiddenElements，隐藏其中的文本节点
    const hiddenElements = info.hiddenElements;
    if (hiddenElements && hiddenElements.length > 0) {
      for (const el of hiddenElements) {
        if (document.contains(el)) {
          if (el.nodeType === Node.TEXT_NODE) {
            const hiddenSpan = document.createElement('span');
            hiddenSpan.hidden = true;
            hiddenSpan.textContent = el.textContent;
            try { el.replaceWith(hiddenSpan); } catch {}
            newHiddenElements.push(hiddenSpan);
          } else if (el.nodeType === Node.ELEMENT_NODE) {
            try { el.hidden = true; } catch {}
            newHiddenElements.push(el);
          }
        }
      }
      if (newHiddenElements.length > 0) {
        this._wrapperLookup.set(wrapper, { ...info, hidden: true, hiddenElements: newHiddenElements, sourceVisible: false });
        return;
      }
    }

    // fallback: 原文是散落的文本节点，需要包裹成 span 才能控制
    // 从 wrapper 前面向前查找原文本节点
    let sibling = wrapper.previousSibling;
    const parent = wrapper.parentNode;
    const sourceText = info.sourceText;

    while (sibling) {
      const prev = sibling.previousSibling;
      if (sibling.nodeType === Node.TEXT_NODE && sibling.textContent.trim()) {
        // 查找原文本节点
        if (sourceText && (sourceText.includes(sibling.textContent) || sibling.textContent.includes(sourceText) || sibling.textContent.trim() === sourceText.trim())) {
          // 创建原文包裹 span
          const sourceSpan = document.createElement('span');
          sourceSpan.hidden = true;
          sourceSpan.textContent = sibling.textContent;
          try {
            sibling.replaceWith(sourceSpan);
            info.sourceSpan = sourceSpan;
          } catch {}
          newHiddenElements.push(sourceSpan);
          break;
        }
      }
      sibling = prev;
    }

    this._wrapperLookup.set(wrapper, { ...info, hidden: true, hiddenElements: newHiddenElements, sourceVisible: false });
  }

  /**
   * 切换双语模式
   */
  toggleBilingual() {
    this.showBilingual = !this.showBilingual;
    this._wrapperLookup.forEach((info, wrapper) => {
      const separator = wrapper.querySelector(':scope > br');
      if (separator) separator.hidden = !this.showBilingual;
      if (this.showBilingual) this.showSource(wrapper);
      else this.hideSource(wrapper);
    });
  }

  /**
   * 清理所有翻译
   */
  cleanupAll() {
    this._scanVersion++;
    document.querySelectorAll(`.${_TRANS_CONTAINER.wrapperClass}`).forEach(el => {
      this.showSource(el);
      el.remove();
    });
    this._wrapperLookup.clear();
    this._nodeRegistry.clear();
    this._idCounter = 0;
    if (this._mutationObserver) {
      this._mutationObserver.disconnect();
      this._mutationObserver = null;
    }
  }

  /**
   * 清理指定节点的翻译
   */
  cleanupNode(node) {
    node.querySelectorAll(`.${_TRANS_CONTAINER.wrapperClass}`).forEach(el => {
      this.showSource(el);
      el.remove();
    });
  }

  /**
   * 启动 DOM 变化监听
   */
  startMutationObserver(callback) {
    if (this._mutationObserver) return;

    this._mutationObserver = new MutationObserver(mutations => {
      for (const mutation of mutations) {
        if (mutation.type === 'characterData' &&
            mutation.oldValue !== mutation.target.nodeValue &&
            !this._skipSet.test(mutation.target.nodeValue || '')) {
          this._scheduleRescan(mutation.target.parentElement);
        } else if (mutation.type === 'childList') {
          let newTextAdded = false;
          mutation.addedNodes.forEach(n => {
            if (n.nodeType === Node.TEXT_NODE) newTextAdded = true;
          });
          if (newTextAdded) this._scheduleRescan(mutation.target);
        }
      }
      if (callback) callback();
    });

    this._mutationObserver.observe(document.body, {
      childList: true,
      subtree: true,
      characterData: true,
      characterDataOldValue: true,
    });
  }

  /**
   * 调度节点重新扫描
   */
  _scheduleRescan(target) {
    if (!(target instanceof Element || target instanceof DocumentFragment)) return;

    // 找到最近块级祖先作为重扫范围
    let scope = target;
    while (scope && scope !== document.body) {
      if (DOMTranslator._isBlock(scope, this._displayCache)) break;
      scope = scope.parentElement;
    }

    this._pendingRescans.add(scope || target);

    if (!this._isRescanning) {
      this._isRescanning = true;
      setTimeout(() => {
        this._pendingRescans.forEach(node => this._rescanScope(node));
        this._pendingRescans.clear();
        this._isRescanning = false;
      }, 100);
    }
  }

  /**
   * 重新扫描指定范围
   */
  _rescanScope(node) {
    this.cleanupNode(node);
    this._scanByTreeWalker(node, (entry) => {
      if (entry) this._onContentChanged?.(entry);
    });
  }

  set onNewContent(handler) { this._onContentChanged = handler; }

  /**
   * 检查元素是否在视口范围内
   */
  _checkViewport(el) {
    if (!el?.getBoundingClientRect) return false;
    const rect = el.getBoundingClientRect();
    const viewH = window.innerHeight || document.documentElement.clientHeight;
    return rect.top >= -500 && rect.bottom <= viewH + 500;
  }

  /**
   * 获取翻译统计
   */
  getStats() {
    return {
      totalEntries: this._idCounter,
      translatedCount: this._wrapperLookup.size,
      pendingCount: 0,
    };
  }

  stop() {
    this.cleanupAll();
  }

  // 向后兼容别名（供 TranslationService 旧代码调用）
  get bilingual() { return this.showBilingual; }
  set bilingual(v) { this.showBilingual = v; }
  scanAll(root) { return this.scan(root); }
  applyTranslation(textId, translatedContent) { return this.apply(textId, translatedContent); }
}

// ========== DOMTranslator 模块结束 ==========

// 兼容别名
const DOMTransEngine = DOMTranslator;

// 翻译服务管理器
class TranslationService {
    constructor() {
        this.domTranslator = null;
        this.isInitialized = false;
        this.isTranslating = false;

        this.init();
    }

    async init() {
        // 初始化 DOMTransEngine（懒加载，在首次翻译时创建）

        // 通知background当前页面已加载，重置状态为原始状态
        try {
            const urlHash = btoa(encodeURIComponent(window.location.href)).replace(/[^\w]/g, '');
            const tabIdentifier = `tab_${urlHash}`;

            await browser.runtime.sendMessage({
                action: 'pageLoaded',
                tabId: tabIdentifier,
                url: window.location.href
            });
        } catch (error) {
            // 忽略错误
        }

        // 设置消息监听器
        this.setupMessageListener();

        // 初始化用户交互事件监听器
        this.setupUserInteractionListener();

        // SPA 导航检测
        this._setupSPANavigationDetection();

        this.isInitialized = true;
        console.log('⚙️ 翻译服务初始化完成 (DOMTransEngine)');

        // 监听网页登录/登出事件
        this.setupAuthSync();
    }

    // 设置认证同步监听
    setupAuthSync() {
        window.addEventListener('userLoggedIn', (event) => {
            try {
                // 从事件 detail 中获取 token（web app 已在 client.ts 中设置 event.detail）
                const token = event.detail?.token;
                if (!token) return;
                const userInfo = event.detail?.userInfo || {};

                browser.runtime.sendMessage({
                    action: 'setAuthToken',
                    token: token,
                    userInfo: userInfo
                }).catch(() => {});
                console.log('🔐 认证状态已同步到扩展');
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
            // 用户交互标记
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

                case 'getDisplayMode':
                    // 查询当前实际的显示状态
                    if (!this.domTranslator) {
                        console.log('[toggle] getDisplayMode: domTranslator 未初始化');
                        sendResponse({ success: false, showingTranslation: null });
                    } else {
                        const state = this.domTranslator._showingTranslation;
                        const wrapperCount = this.domTranslator._wrapperLookup.size;
                        console.log(`[toggle] getDisplayMode → showingTranslation=${state}, wrappers=${wrapperCount}`);
                        sendResponse({ success: true, showingTranslation: state });
                    }
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
                    console.error('❌ 翻译错误:', request.error);
                    this._showErrorNotification(request.error);

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
                        if (this.domTranslator) {
                            if (this.domTranslator.bilingual !== showBilingual) {
                                this.domTranslator.toggleBilingual();
                            }
                        }

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
                    try {
                        if (!this.domTranslator) {
                            sendResponse({ success: false, error: '未执行过翻译，无可切换内容', displayMode: 'original' });
                            break;
                        }

                        const domTranslator = this.domTranslator;

                        // 直接检查 DOM 实际状态：wrapper 是否可见 + 原文是否可见
                        // 不依赖 _showingTranslation 标志，因为它可能和实际 DOM 不同步
                        let firstWrapperInfo = null;
                        let firstWrapper = null;
                        domTranslator._wrapperLookup.forEach((info, wrapper) => {
                            if (!firstWrapperInfo) {
                                firstWrapperInfo = info;
                                firstWrapper = wrapper;
                            }
                        });

                        if (!firstWrapper || !firstWrapperInfo) {
                            sendResponse({ success: false, error: '无翻译内容', displayMode: 'original' });
                            break;
                        }

                        // 检查原文是否可见：通过 wrapper 内的隐藏 span 是否存在于 DOM 中
                        const he = firstWrapperInfo.hiddenElements;
                        const hasHiddenSpanInDOM = he && he.length > 0 && he.some(el => el instanceof HTMLSpanElement && el.hidden && document.contains(el));
                        const cs = window.getComputedStyle(firstWrapper);
                        const actualDisplay = cs.display;
                        const inlineDisplay = firstWrapper.style.display;
                        console.log(`[toggle] computedDisplay='${actualDisplay}', inlineDisplay='${inlineDisplay}', isConnected=${firstWrapper.isConnected}, hidden=${firstWrapperInfo.hidden}, sourceVisible=${firstWrapperInfo.sourceVisible}`);
                        const wrapperVisible = actualDisplay !== 'none';

                        // 如果 wrapper 隐藏 → 显示原文，否则 → 切换到原文
                        if (!wrapperVisible) {
                            // 当前显示原文，切换到译文
                            console.log('[toggle] 检测到当前显示原文，切换到译文');
                            domTranslator._wrapperLookup.forEach((info, wrapper) => {
                                wrapper.style.display = '';
                                domTranslator.hideSource(wrapper);
                            });
                            domTranslator._showingTranslation = true;
                        } else {
                            // wrapper 可见，切换到原文
                            console.log('[toggle] 当前 wrapper 可见，切换到原文');
                            domTranslator._wrapperLookup.forEach((info, wrapper) => {
                                wrapper.style.setProperty('display', 'none', 'important');
                                domTranslator.showSource(wrapper);
                            });
                            domTranslator._showingTranslation = false;
                        }

                        const newMode = domTranslator._showingTranslation ? 'translation' : 'original';
                        console.log(`[toggle] 切换完成: ${newMode}`);
                        sendResponse({
                            success: true,
                            message: '显示模式已切换',
                            displayMode: newMode
                        });
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
                            if (this.domTranslator && this.domTranslator.bilingual !== value) {
                                this.domTranslator.toggleBilingual();
                            }

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
            // 新架构：平行 wrapper 策略
            const wrappers = document.querySelectorAll('.extreme-translation-wrapper');
            const translatedElements = document.querySelectorAll('.extreme-translated');

            // 没有翻译元素
            if (wrappers.length === 0 && translatedElements.length === 0) {
                const restoredElements = document.querySelectorAll('.extreme-restored');
                return restoredElements.length > 0 ? 'original' : 'original';
            }

            // 有翻译 wrapper，使用 DOMTransEngine 状态判断
            if (wrappers.length > 0) {
                const mode = this.domTranslator?.bilingual ? 'bilingual' : 'translation_only';
                switch (mode) {
                    case 'bilingual':
                        return 'bilingual_mode';
                    case 'original':
                        return 'showing_original';
                    case 'translation':
                        return 'showing_translation';
                    default:
                        return 'bilingual_mode';
                }
            }

            // 只有 .extreme-translated 标记，检查是否直接翻译（无 wrapper）
            if (translatedElements.length > 0) {
                return 'showing_translation';
            }

            return 'translated_suspected';
        } catch (error) {
            console.error('getCurrentPageStatus 出错:', error);
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
        // 使用 DOMTransEngine 清理所有翻译
        if (this.domTranslator) {
            this.domTranslator.cleanupAll();
        }

        // 移除旧版翻译 wrapper（兼容性）
        const translationWrappers = document.querySelectorAll('.extreme-translation-wrapper');
        translationWrappers.forEach(w => w.remove());

        // 移除旧版双语 wrapper（兼容之前的翻译结果）
        const oldWrappers = document.querySelectorAll('.extreme-bilingual-text');
        oldWrappers.forEach(w => {
            if (w.parentNode) {
                const originalText = w.getAttribute('data-original-text');
                if (originalText) {
                    w.parentNode.replaceChild(document.createTextNode(originalText), w);
                } else {
                    w.remove();
                }
            }
        });

        // 清理翻译标记和节点ID标记
        document.querySelectorAll('.extreme-translated').forEach(el => {
            el.classList.remove('extreme-translated');
            el.removeAttribute('data-original-text');
            el.removeAttribute('data-translated-text');
            el.removeAttribute('data-bilingual-mode');
            el.removeAttribute('data-nt-id');
        });
    }

    // 处理应用翻译请求（由后台主动发送翻译结果 - 非流式回退）
    async handleApplyTranslations(request, sender, sendResponse) {
        try {
            const { translations, bilingual = false } = request;

            if (!this.domTranslator) {
                sendResponse({ success: false, error: 'DOMTransEngine 未初始化' });
                return;
            }

            let successCount = 0;
            for (const translation of translations) {
                const success = this.domTranslator.applyTranslation(translation.textId, translation.translation);
                if (success) successCount++;
            }

            console.log(`✅ 已应用 ${successCount}/${translations.length} 条翻译`);

            try {
                browser.runtime.sendMessage({
                    action: 'savePageStatus',
                    status: 'showing_translation',
                    tabId: sender.tab?.id
                }).catch(() => {});
            } catch (err) {}

            sendResponse({ success: true, applied: successCount });
        } catch (error) {
            console.error('应用翻译结果失败:', error);
            this._showErrorNotification('翻译出错: ' + error.message);
            sendResponse({ success: false, error: error.message });
        }
    }

    // 处理网页翻译请求（使用 DOMTransEngine 算法）
    async handleTranslateWebPage(request, sender, sendResponse) {
        // 防止重复请求
        if (this.isTranslating) {
            console.log('⚠️ 翻译已在进行中，忽略重复请求');
            sendResponse({ success: false, error: '翻译已在进行中，请稍候...' });
            return;
        }

        this.isTranslating = true;

        try {
            console.log('🚀 翻译开始 (DOMTransEngine)...');

            // === 清理上一次翻译的残留状态 ===
            console.log('🧹 清理残留翻译状态...');
            if (this.domTranslator) {
                this.domTranslator.cleanupAll();
            }
            console.log('✅ 残留状态已清理');

            // 初始化 DOMTransEngine（懒加载）
            if (!this.domTranslator) {
                const settings = await this.getUserSettings();
                const sourceLang = request.sourceLang || 'auto';
                const targetLang = request.targetLang || settings.target_lang || 'zh';
                const bilingual = request.bilingual || settings.bilingual_display || false;

                this.domTranslator = new DOMTransEngine({
                    targetLang,
                    sourceLang,
                    engine: request.engine || settings.engine || 'google',
                    bilingual,
                    minTextLength: 2,
                    maxTextLength: 5000,
                    autoScan: true,
                });

                // 注入译文样式
                this._injectDOMStyles(bilingual);
            }

            // 显示顶部进度条
            this._showProgressBar();

            // 关闭 Cookie 同意弹窗（在扫描前）
            this.dismissConsentBanners();

            // 使用 DOMTransEngine 扫描页面
            const mappingTable = this.domTranslator.scanAll(document.body);

            if (!mappingTable || mappingTable.totalTexts === 0) {
                throw new Error('未找到可翻译的文本内容');
            }

            console.log(`📄 DOMTransEngine 扫描完成，共 ${mappingTable.totalTexts} 个文本节点，分 ${mappingTable.batchCount} 批`);

            // 获取用户设置
            const settings = await this.getUserSettings();
            const sourceLang = request.sourceLang || 'auto';
            const targetLang = request.targetLang || settings.target_lang || 'zh';
            const engine = request.engine || settings.engine || 'google';
            const expertMode = settings.expert_mode || false;

            // 发送到 background 进行流式翻译（协议不变）
            const startTime = Date.now();
            await browser.runtime.sendMessage({
                action: 'uploadMappingTableStream',
                mappingTable: {
                    totalTexts: mappingTable.totalTexts,
                    batches: mappingTable.batches,
                    batchCount: mappingTable.batchCount,
                    url: mappingTable.url,
                    timestamp: mappingTable.timestamp
                },
                sourceLang: sourceLang,
                targetLang: targetLang,
                engine: engine,
                bilingual: this.domTranslator.bilingual,
                fastMode: !expertMode,
                tabId: sender.tab?.id
            });

            const duration = Date.now() - startTime;
            console.log(`✅ 映射表已上传，耗时: ${duration}ms`);

            sendResponse({
                success: true,
                message: '映射表已上传，翻译进行中',
                stats: this.domTranslator.getStats(),
                status: 'translating',
                duration: duration
            });

        } catch (error) {
            console.error('网页翻译失败:', error);

            const errorMsg = this.getUserFriendlyErrorMessage(error.message || String(error));

            this._hideProgressBar();
            this._showErrorNotification(errorMsg);

            try {
                browser.runtime.sendMessage({
                    action: 'savePageStatus',
                    status: 'original',
                    tabId: sender.tab?.id
                }).catch(() => {});
            } catch (err) {}

            sendResponse({ success: false, error: errorMsg });
        } finally {
            // 不重置翻译锁，等待流式完成
        }
    }

    // 流式翻译块处理 - 由 background 主动推送（使用 DOMTransEngine）
    async handleStreamTranslationChunk(chunk) {
        try {
            const { textId, original, translation, bilingual = false } = chunk;

            if (!this.domTranslator) return false;

            // 使用 DOMTransEngine 应用翻译
            const success = this.domTranslator.applyTranslation(textId, translation);

            // 更新进度条
            if (success) {
                const stats = this.domTranslator.getStats();
                this._updateProgressBar(stats.translatedCount, stats.totalEntries);
            }

            return success;
        } catch (error) {
            console.error(`处理翻译块失败 ${chunk.textId}:`, error);
            return false;
        }
    }

    // 流式翻译完成处理（使用 DOMTransEngine）
    async handleStreamTranslationComplete(result) {
        try {
            // 校验翻译结果：如果 translations 为空，说明 SSE 连接提前关闭未收到数据
            if (!result.translations || result.translations.length === 0) {
                console.error('❌ 翻译完成但未收到任何翻译数据，SSE 连接可能提前关闭');
                this._hideProgressBar();
                this._showErrorNotification('翻译服务未返回数据，请检查后端服务状态或重新登录');
                try {
                    browser.runtime.sendMessage({
                        action: 'savePageStatus',
                        status: 'original',
                        tabId: this.getCurrentTabId?.()
                    }).catch(() => {});
                } catch (err) {}
                setTimeout(() => { this.isTranslating = false; }, 500);
                return;
            }

            console.log(`✅ 流式翻译完成，收到 ${result.translations.length} 条翻译，启动动态内容监控`);

            if (!this.domTranslator) return;

            // 启动 MutationObserver 监控动态内容
            this.domTranslator.startMutationObserver(() => {
                console.log('[DOMTransEngine] 页面内容发生变化');
            });

            // 动态新增内容的翻译回调
            this.domTranslator.onNewContent = async (newEntry) => {
                // 新发现的文本需要翻译
                // 通过 background 发送单个翻译请求
                try {
                    await browser.runtime.sendMessage({
                        action: 'translateSingleText',
                        textId: newEntry.id,
                        original: newEntry.original,
                        sourceLang: this.domTranslator.sourceLang,
                        targetLang: this.domTranslator.targetLang,
                        engine: this.domTranslator.engine,
                        bilingual: this.domTranslator.bilingual,
                    });
                } catch (e) {
                    console.warn('[DOMTransEngine] 发送动态内容翻译请求失败:', e);
                }
            };

            // 隐藏进度条
            this._hideProgressBar();

            // 保存状态
            try {
                browser.runtime.sendMessage({
                    action: 'savePageStatus',
                    status: 'showing_translation',
                    tabId: this.getCurrentTabId?.()
                }).catch(() => {});
            } catch (err) {}

            // 重置翻译状态锁
            setTimeout(() => {
                this.isTranslating = false;
            }, 500);

        } catch (error) {
            console.error('翻译完成处理失败:', error);
            this.isTranslating = false;
        }
    }

    // 取消正在进行的翻译请求
    cancelTranslation() {
        if (this.isTranslating) {
            this.isTranslating = false;
            console.log('翻译已取消 (DOMTransEngine)');

            // 清理已翻译的部分
            if (this.domTranslator) {
                this.domTranslator.cleanupAll();
            }

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

    // ========== DOMTransEngine 辅助方法 ==========

    /**
     * 注入译文样式
     */
    _injectDOMStyles(bilingual) {
        if (document.getElementById('dom-trans-styles')) return;

        const style = document.createElement('style');
        style.id = 'dom-trans-styles';
        style.textContent = `
            .dom-trans-wrapper {
                display: inline !important;
                white-space: pre-line !important;
            }
            .dom-trans-wrapper > br {
                margin: 0.25em 0;
            }
            .dom-trans-text {
                display: inline;
                color: inherit;
                font-family: inherit;
                font-size: inherit;
                line-height: inherit;
            }
            .dom-trans-wrapper[hidden] > .dom-trans-text,
            .dom-trans-wrapper > br[hidden] {
                display: none;
            }
        `;
        document.head.appendChild(style);
    }

    /**
     * 显示顶部进度条
     */
    _showProgressBar() {
        if (document.getElementById('extreme-translation-progress-bar')) return;

        const bar = document.createElement('div');
        bar.id = 'extreme-translation-progress-bar';
        bar.style.cssText = `
            position: fixed !important;
            top: 0 !important;
            left: 0 !important;
            width: 100% !important;
            height: 4px !important;
            background: linear-gradient(90deg, rgba(30, 30, 46, 0.95) 0%, rgba(40, 40, 60, 0.95) 100%) !important;
            z-index: 2147483647 !important;
            display: block !important;
        `;

        const fill = document.createElement('div');
        fill.className = 'progress-fill';
        fill.style.cssText = `
            height: 100% !important;
            width: 0% !important;
            background: linear-gradient(90deg, #667eea 0%, #764ba2 50%, #f093fb 100%) !important;
            transition: width 0.2s ease-out !important;
        `;

        const label = document.createElement('span');
        label.className = 'progress-label';
        label.style.cssText = `
            position: absolute !important;
            right: 10px !important;
            top: 50% !important;
            transform: translateY(-50%) !important;
            color: #fff !important;
            font-size: 11px !important;
            font-weight: 600 !important;
        `;
        label.textContent = '翻译中... 0%';

        bar.appendChild(fill);
        bar.appendChild(label);
        document.body.appendChild(bar);
    }

    /**
     * 更新进度条
     */
    _updateProgressBar(translated, total) {
        const bar = document.getElementById('extreme-translation-progress-bar');
        if (!bar) return;

        const fill = bar.querySelector('.progress-fill');
        const label = bar.querySelector('.progress-label');
        if (!fill || !label) return;

        const percent = total > 0 ? Math.round((translated / total) * 100) : 0;
        fill.style.width = `${percent}%`;
        label.textContent = `翻译中... ${percent}%`;
    }

    /**
     * 隐藏进度条
     */
    _hideProgressBar() {
        const bar = document.getElementById('extreme-translation-progress-bar');
        if (bar) {
            bar.style.transition = 'opacity 0.4s ease-out';
            bar.style.opacity = '0';
            setTimeout(() => bar.remove(), 400);
        }
    }

    /**
     * 显示错误通知
     */
    _showErrorNotification(message) {
        const notification = document.createElement('div');
        notification.style.cssText = `
            position: fixed !important;
            top: 20px !important;
            right: 20px !important;
            background: #ef4444 !important;
            color: white !important;
            padding: 12px 20px !important;
            border-radius: 8px !important;
            box-shadow: 0 4px 12px rgba(0,0,0,0.15) !important;
            z-index: 2147483647 !important;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif !important;
            font-size: 14px !important;
            max-width: 400px !important;
            animation: nt-slideInRight 0.3s ease-out;
        `;
        notification.textContent = `翻译失败: ${message}`;
        document.body.appendChild(notification);

        setTimeout(() => {
            notification.style.transition = 'opacity 0.3s ease-out';
            notification.style.opacity = '0';
            setTimeout(() => notification.remove(), 300);
        }, 5000);
    }

    // ========== 以下为新增功能 ==========

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

    /**
     * 自动关闭 Cookie 同意弹窗（三阶段策略）
     * 在 scanAll 之前调用，防止弹窗内容被翻译
     */
    async dismissConsentBanners() {
        // 阶段1: CMP API 调用（IAB TCF v2 / OneTrust 等）
        try {
            if (typeof window.__tcfapi === 'function') {
                window.__tcfapi('addEventListener', 2, (tcData, success) => {
                    if (success && tcData.eventStatus === 'tcloaded') {
                        window.__tcfapi('setGdprApplies', 2, () => {}, true);
                    }
                });
            }
            if (window.OneTrust && typeof window.OnetrustActiveGroups === 'string') {
                window.OneTrust.AcceptAll();
            }
        } catch {}

        // 阶段2: 查找并点击 accept/close 按钮
        const acceptPatterns = [
            { selector: '#onetrust-accept-btn-handler, .onetrust-accept-btn-handler, .onetrust-close-btn-handler' },
            { selector: '#cookie-notice button:contains("Accept"), #cookie-notice button:contains("同意")' },
            { selector: '[aria-label*="Accept"], [aria-label*="accept"], [aria-label*="同意"]' },
            { textPatterns: ['Accept All', 'Accept and close', 'Agree', '同意所有', 'Accept Selected', 'Allow All'] },
        ];

        for (const rule of acceptPatterns) {
            if (rule.selector) {
                try {
                    const btns = document.querySelectorAll(rule.selector);
                    for (const btn of btns) {
                        if (btn.offsetParent !== null) { // 可见
                            btn.click();
                            console.log('[CookieDismiss] 阶段2: 点击按钮', rule.selector);
                            return true;
                        }
                    }
                } catch {}
            }
            if (rule.textPatterns) {
                const allBtns = document.querySelectorAll('button, [role="button"], a[role="button"]');
                for (const btn of allBtns) {
                    const text = (btn.textContent || '').trim().toLowerCase();
                    for (const pattern of rule.textPatterns) {
                        if (text.includes(pattern.toLowerCase()) && btn.offsetParent !== null) {
                            btn.click();
                            console.log('[CookieDismiss] 阶段2: 点击文本匹配按钮', pattern);
                            return true;
                        }
                    }
                }
            }
        }

        // 阶段3: 隐藏同意容器（最后手段）
        const consentContainerSelectors = [
            '#onetrust-consent-sdk', '#onetrust-banner-sdk',
            '.onetrust-banner', '.onetrust-pc-sdk',
            '#cookiebot', '.cookiebot',
            '.osano-cm-dialog', '.osano-cm-info',
            '#didomi-host', '.didomi-consent-ui',
            '.usercentrics', '.uc-banner',
            '.cookie-consent-banner', '.cookie-banner',
            '.cc-banner', '.cc-window',
            '.gdpr-banner', '.privacy-banner',
            '[data-cookiebanner]', '[data-consent]',
            '#sp_message_container', '.sp-message',
            '.cmp-container', '.consent-container',
        ];

        for (const selector of consentContainerSelectors) {
            try {
                const elements = document.querySelectorAll(selector);
                for (const el of elements) {
                    if (el.offsetParent !== null) {
                        el.style.display = 'none';
                        el.setAttribute('aria-hidden', 'true');
                        console.log('[CookieDismiss] 阶段3: 隐藏容器', selector);
                        return true;
                    }
                }
            } catch {}
        }

        return false;
    }

    /**
     * SPA 导航检测：监听 URL 变化，自动重新扫描和翻译
     * 在 init() 中调用
     */
    _setupSPANavigationDetection() {
        let lastURL = window.location.href;
        let navTimer = null;

        const urlObserver = new MutationObserver(() => {
            if (window.location.href !== lastURL) {
                const newURL = window.location.href;
                lastURL = newURL;

                if (navTimer) clearTimeout(navTimer);
                navTimer = setTimeout(() => {
                    console.log('[SPA导航] URL 变化到:', newURL);

                    // 清理旧翻译状态
                    if (this.domTranslator) {
                        this.domTranslator.cleanupAll();
                    }
                    this.isTranslating = false;

                    // 重新关闭可能的 cookie 弹窗
                    this.dismissConsentBanners();

                    // 如果之前已翻译，自动重新翻译
                    const status = this.getCurrentPageStatus();
                    if (status !== 'original') {
                        console.log('[SPA导航] 自动重新翻译');
                        // 通知 background 重新触发翻译
                        browser.runtime.sendMessage({
                            action: 'translateWebPage',
                            targetLang: 'zh',
                            sourceLang: 'auto',
                        }).catch(() => {});
                    }
                }, 500); // 等待 500ms 确保 SPA 渲染完成
            }
        });

        urlObserver.observe(document.body, { childList: true, subtree: true, attributes: true });
    }

    // 清理翻译
    cleanupTranslations() {
        if (this.domTranslator) {
            this.domTranslator.cleanupAll();
        }

        console.log('🧹 翻译清理完成 (DOMTransEngine)');
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
// 通过 postMessage 从页面上下文中读取 localStorage（content script 无法直接访问页面的 localStorage）
(function syncAuthToExtension() {
    function requestTokenFromPage() {
        try {
            // 通过 <script src=""> 加载外部脚本，绕过 CSP 的 inline-script 限制
            const scriptEl = document.createElement('script');
            scriptEl.src = browser.runtime.getURL('src/content/auth-sync.js');
            scriptEl.onload = function() {
                document.documentElement.removeChild(scriptEl);
            };
            scriptEl.onerror = function() {
                console.warn('[NovelTrans] auth-sync.js 加载失败');
                document.documentElement.removeChild(scriptEl);
            };
            document.documentElement.appendChild(scriptEl);
        } catch (e) {
            console.warn('[NovelTrans] 注入页面脚本失败:', e.message);
        }
    }

    function handleMessage(event) {
        if (!event.data || event.data.source !== 'noveltrans-auth-sync') return;

        window.removeEventListener('message', handleMessage);
        const { token, userInfo } = event.data;

        if (token) {
            console.log('[NovelTrans] 从页面获取到 token，正在同步到扩展...');
            browser.runtime.sendMessage({
                action: 'setAuthToken',
                token: token,
                userInfo: userInfo || {}
            }).then(() => {
                console.log('[NovelTrans] 登录态已同步到扩展');
            }).catch((e) => {
                console.warn('[NovelTrans] 同步失败:', e.message);
            });
        } else {
            console.log('[NovelTrans] 页面未登录（无 authToken）');
        }
    }

    try {
        window.addEventListener('message', handleMessage);
        // 页面加载完成后再请求（确保 localStorage 已准备好）
        if (document.readyState === 'complete') {
            requestTokenFromPage();
        } else {
            window.addEventListener('load', requestTokenFromPage);
        }
    } catch (e) {
        console.warn('[NovelTrans] 登录态桥接异常:', e.message);
    }
})();