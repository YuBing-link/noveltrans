// read.js - 重构版阅读器模式 (2026)
// 参考市面优秀阅读器设计：Safari Reader、Firefox Reader View、Instapaper、Medium

// 重要：在加载脚本时立即移除任何已存在的 overlay（防止多重覆盖）
(function cleanupExistingOverlay() {
    const existingOverlays = document.querySelectorAll('#extreme-reader-overlay');
    if (existingOverlays.length > 0) {
        console.log('[Reader] 脚本加载时检测到', existingOverlays.length, '个已存在的 overlay，全部移除');
        existingOverlays.forEach(el => el.remove());
    }
})();

// 阅读模式管理器
class ReaderModeManager {
    constructor() {
        // 再次检查并移除任何已存在的 overlay（双重保险）
        const existingOverlays = document.querySelectorAll('#extreme-reader-overlay');
        if (existingOverlays.length > 0) {
            console.log('[Reader] 构造函数中检测到', existingOverlays.length, '个已存在的 overlay，全部移除');
            existingOverlays.forEach(el => el.remove());
        }

        this.overlay = null;
        this.isActive = false;
        this.isActivating = false;
        this.originalScrollPosition = 0;
        this.processedArticle = null;
        this.EVENT_LISTENERS = new Map();
        this.currentEngine = null;
        this.originalContent = null;
        this.translatedContent = null;
        this.showingTranslated = false;
        this.translationCacheKey = null;

        // 阅读偏好设置（持久化）- 仿照小说阅读器优化
        this.readingPreferences = {
            fontSize: 18,           // 字体大小 14-32
            lineHeight: 1.9,        // 行高 1.2-2.5 (增加到 1.9 更舒适)
            maxWidth: 680,          // 内容最大宽度 600-900 (减小到 680 更适合阅读)
            theme: 'light',         // 主题：light, dark, sepia, eyeCare
            fontFamily: 'serif',    // 字体：serif, sans, sans2 (衬线字体更适合长文阅读)
            textAlign: 'justify',   // 对齐：left, justify
            showFirstIndent: true   // 首行缩进 (小说风格默认开启)
        };

        // 目录数据
        this.tocData = [];

        // 阅读进度
        this.readProgress = 0;

        // 书签数据
        this.bookmarks = [];

        // 章节列表（用于小说阅读器）
        this.chapterList = [];
        this.currentChapterIndex = -1;

        this.cachedElements = new Map();
        this.ttsEnabled = false;
        this.ttsPlaying = false;
        this.ttsUtterance = null;

        this.init();
    }

    init() {
        this.loadReadingPreferences();
        this.loadBookmarks();
        this.addReaderStyles();
        this.setupScrollListener();
    }

    // 加载阅读偏好设置
    async loadReadingPreferences() {
        try {
            const result = await browser.storage.local.get(['readerPreferences']);
            if (result.readerPreferences) {
                this.readingPreferences = { ...this.readingPreferences, ...result.readerPreferences };
            }
        } catch (error) {
            console.warn('加载阅读偏好失败:', error);
        }
    }

    // 保存阅读偏好设置
    async saveReadingPreferences() {
        try {
            await browser.storage.local.set({ readerPreferences: this.readingPreferences });
        } catch (error) {
            console.warn('保存阅读偏好失败:', error);
        }
    }

    // 设置滚动监听器（用于阅读进度）
    setupScrollListener() {
        const handleScroll = () => {
            if (!this.isActive) return;

            const scrollTop = window.scrollY;
            const docHeight = document.documentElement.scrollHeight - window.innerHeight;
            const progress = docHeight > 0 ? (scrollTop / docHeight) * 100 : 0;

            this.readProgress = Math.min(100, Math.max(0, progress));
            this.updateProgressIndicator();
        };

        // 防抖处理
        let ticking = false;
        this.scrollHandler = () => {
            if (!ticking) {
                window.requestAnimationFrame(() => {
                    handleScroll();
                    ticking = false;
                });
                ticking = true;
            }
        };

        window.addEventListener('scroll', this.scrollHandler, { passive: true });
    }

    // 创建阅读模式浮层
    async createOverlay() {
        this.originalScrollPosition = window.scrollY;

        // 重要：移除所有可能存在的旧 overlay 元素（防止重复）
        const allOverlays = document.querySelectorAll('#extreme-reader-overlay');
        if (allOverlays.length > 0) {
            console.log('[Reader] 发现已存在的 overlay，数量:', allOverlays.length, '直接复用第一个');
            // 移除所有 overlay（防止多个 overlay 层叠）
            allOverlays.forEach(el => el.remove());
        }

        await this.loadReaderTemplate();
        return Promise.resolve();
    }

    // 加载阅读器模板
    async loadReaderTemplate() {
        try {
            // 重要：先移除任何已存在的 overlay（防止重复）
            const existingOverlays = document.querySelectorAll('#extreme-reader-overlay');
            if (existingOverlays.length > 0) {
                console.log('[Reader] loadReaderTemplate: 移除', existingOverlays.length, '个已存在的 overlay');
                existingOverlays.forEach(el => el.remove());
            }

            // 使用 browser.runtime.getURL 获取模板 URL
            const templateUrl = browser.runtime.getURL('src/options/reader-template.html');

            console.log('[Reader] 加载模板:', templateUrl);

            const response = await fetch(templateUrl);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const templateHtml = await response.text();

            // 创建临时容器解析 HTML
            const tempDiv = document.createElement('div');
            tempDiv.innerHTML = templateHtml;

            // 获取 overlay 元素
            const overlay = tempDiv.querySelector('#extreme-reader-overlay');
            if (!overlay) {
                throw new Error('无法找到 overlay 元素');
            }

            this.overlay = overlay;
            document.body.appendChild(this.overlay);

            if (typeof NovelTransI18n !== 'undefined') {
                NovelTransI18n.apply(this.overlay);
            }

            console.log('[Reader] 模板加载成功');

            requestAnimationFrame(() => {
                this.bindEvents();
                this.applyReadingPreferences();
            });

        } catch (error) {
            console.error('[Reader] 加载模板失败:', error);
            // 如果加载模板失败，使用备用方案：创建一个简单的 overlay
            this.createFallbackOverlay();
        }
    }

    // 创建备用覆盖层（当模板加载失败时使用）
    createFallbackOverlay() {
        const existingOverlays = document.querySelectorAll('#extreme-reader-overlay');
        if (existingOverlays.length > 0) {
            console.log('[Reader] createFallbackOverlay: 移除', existingOverlays.length, '个已存在的 overlay');
            existingOverlays.forEach(el => el.remove());
        }

        this.overlay = document.createElement('div');
        this.overlay.id = 'extreme-reader-overlay';
        this.overlay.className = 'novel-theme-light';
        this.overlay.innerHTML = `
            <div class="reader-toolbar">
                <div class="reader-toolbar-left">
                    <button id="reader-back-btn" class="reader-back-button" data-i18n-title="reader_back" title="退出阅读模式 (ESC)">
                        <i class="ri-arrow-left-line"></i>
                    </button>
                    <div id="reader-title" class="reader-toolbar-title" data-i18n="reader_mode">阅读模式</div>
                </div>
                <div class="reader-toolbar-right">
                    <button id="reader-font-minus" class="reader-icon-button" data-i18n-title="font_minus" title="减小字号">
                        <span class="font-label">A</span><span class="font-minus">⁻</span>
                    </button>
                    <button id="reader-font-plus" class="reader-icon-button" data-i18n-title="font_plus" title="增大字号">
                        <span class="font-label">A</span><span class="font-plus">⁺</span>
                    </button>
                    <button id="reader-more-btn" class="reader-icon-button" data-i18n-title="more_options" title="更多选项">
                        <i class="ri-more-2-fill"></i>
                    </button>
                </div>
            </div>
            <div class="reader-progress-bar">
                <div class="reader-progress-fill"></div>
            </div>
            <div id="reader-settings-panel" class="reader-settings-panel">
                <div class="settings-section">
                    <div class="settings-section-title" data-i18n="theme">主题</div>
                    <div class="theme-options">
                        <div class="theme-option active" data-theme="light"><i class="ri-sun-line"></i><span data-i18n="theme_light">日间</span></div>
                        <div class="theme-option" data-theme="sepia"><i class="ri-book-open-line"></i><span data-i18n="theme_sepia">护眼</span></div>
                        <div class="theme-option" data-theme="eyecare"><i class="ri-leaf-line"></i><span data-i18n="theme_eyecare">绿色</span></div>
                        <div class="theme-option" data-theme="dark"><i class="ri-moon-line"></i><span data-i18n="theme_dark">夜间</span></div>
                        <div class="theme-option" data-theme="paper"><i class="ri-article-line"></i><span data-i18n="theme_paper">纸质</span></div>
                    </div>
                </div>
                <div class="settings-section">
                    <div class="settings-section-title" data-i18n="font_family">字体</div>
                    <div class="font-options">
                        <div class="font-option active" data-font="serif"><span style="font-family: Georgia, serif;" data-i18n="font_serif">衬线</span></div>
                        <div class="font-option" data-font="sans"><span style="font-family: -apple-system, sans-serif;" data-i18n="font_sans">无衬线</span></div>
                        <div class="font-option" data-font="kai"><span style="font-family: KaiTi, serif;" data-i18n="font_kai">楷体</span></div>
                    </div>
                </div>
                <div class="settings-section">
                    <div class="settings-section-title" data-i18n="font_size">字号</div>
                    <div class="slider-control">
                        <input type="range" id="font-size-range" min="14" max="32" step="1" value="${this.readingPreferences.fontSize}">
                        <div class="slider-value"><span id="font-size-value">${this.readingPreferences.fontSize}px</span></div>
                    </div>
                </div>
                <div class="settings-section">
                    <div class="settings-section-title" data-i18n="line_height">行距</div>
                    <div class="slider-control">
                        <input type="range" id="line-height-range" min="1.2" max="2.5" step="0.1" value="${this.readingPreferences.lineHeight}">
                        <div class="slider-value"><span id="line-height-value">${this.readingPreferences.lineHeight}</span></div>
                    </div>
                </div>
                <div class="settings-section">
                    <div class="settings-section-title" data-i18n="content_width">内容宽度</div>
                    <div class="slider-control">
                        <input type="range" id="max-width-range" min="600" max="900" step="20" value="${this.readingPreferences.maxWidth}">
                        <div class="slider-value"><span id="max-width-value">${this.readingPreferences.maxWidth}px</span></div>
                    </div>
                </div>
                <div class="settings-section">
                    <div class="settings-section-title" data-i18n="alignment">对齐</div>
                    <div class="align-options">
                        <div class="align-option" data-align="left"><i class="ri-align-left"></i></div>
                        <div class="align-option active" data-align="justify"><i class="ri-align-justify"></i></div>
                    </div>
                    <label class="checkbox-label">
                        <input type="checkbox" id="first-indent-check">
                        <span data-i18n="first_indent">首行缩进</span>
                    </label>
                </div>
                <div class="settings-divider"></div>
                <div class="settings-section">
                    <button id="reader-tts-btn" class="settings-action-btn"><i class="ri-volume-up-line"></i><span data-i18n="start_tts">开始朗读</span></button>
                    <button id="reader-toc-btn" class="settings-action-btn"><i class="ri-menu-2-line"></i><span data-i18n="table_of_contents">目录</span></button>
                    <button id="reader-bookmark-btn" class="settings-action-btn"><i class="ri-bookmark-line"></i><span data-i18n="bookmark">书签</span></button>
                </div>
            </div>
            <div class="reader-backdrop" id="settings-backdrop"></div>
            <div class="reader-backdrop" id="toc-backdrop"></div>
            <div id="reader-toc-sidebar" class="reader-toc-sidebar">
                <div class="toc-header">
                    <span class="toc-title" data-i18n="table_of_contents">目录</span>
                    <button id="toc-close-btn" class="toc-close-btn"><i class="ri-close-line"></i></button>
                </div>
                <div id="toc-content" class="toc-content"></div>
            </div>
            <!-- 翻译按钮（内容区底部居中） -->
            <button id="reader-translate-btn" class="reader-translate-fab" data-i18n-title="translate_article" title="翻译文章">
                <i class="ri-translate-line"></i>
                <span data-i18n="translate_webpage">翻译</span>
            </button>
            <button id="reader-switch-language" class="reader-translate-fab" style="display:none;" data-i18n-title="switch_language" title="切换原文/译文">
                <i class="ri-translate"></i>
                <span id="reader-lang-text" data-i18n="translation_text">译文</span>
            </button>
            <div id="reader-content" class="reader-content-container">
                <div class="loading-container">
                    <div class="loading-spinner">
                        <div class="spinner-ring"></div>
                        <div class="spinner-ring"></div>
                        <div class="spinner-ring"></div>
                        <div class="spinner-ring"></div>
                    </div>
                    <div class="loading-text" data-i18n="loading_content">正在提取文章内容...</div>
                </div>
            </div>
            <div id="reader-image-lightbox" class="reader-image-lightbox">
                <button class="reader-image-lightbox-close"><i class="ri-close-line"></i></button>
                <img src="" alt="放大图片">
            </div>
        `;

        document.body.appendChild(this.overlay);

        if (typeof NovelTransI18n !== 'undefined') {
            NovelTransI18n.apply(this.overlay);
        }

        requestAnimationFrame(() => {
            this.bindEvents();
            this.applyReadingPreferences();
        });
    }

    // 添加阅读模式样式（备用样式，当模板加载失败时使用）
    addReaderStyles() {
        if (document.getElementById('reader-mode-styles')) return;

        // 首先添加 Remix Icon 外部 CSS
        const remixIconLink = document.createElement('link');
        remixIconLink.rel = 'stylesheet';
        remixIconLink.href = 'https://cdn.jsdelivr.net/npm/remixicon@3.5.0/fonts/remixicon.css';
        document.head.appendChild(remixIconLink);

        // 添加小说阅读器新样式文件
        const novelStylesLink = document.createElement('link');
        novelStylesLink.rel = 'stylesheet';
        novelStylesLink.href = browser.runtime.getURL('src/content/reader-styles.css');
        novelStylesLink.id = 'novel-reader-styles';
        document.head.appendChild(novelStylesLink);

        const style = document.createElement('style');
        style.id = 'reader-mode-styles';
        style.textContent = `
            /* 基础动画（备用，当 CSS 文件未加载时） */
            @keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
            @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }

            #extreme-reader-overlay {
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                height: 100dvh;
                background: #f5f4ed;
                z-index: 999999;
                overflow-y: scroll;
                overflow-x: hidden;
                font-family: Georgia, serif;
                display: block !important;
                animation: fadeIn 0.25s ease-out;
                scroll-behavior: smooth;
            }

            .reader-toolbar {
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                background: rgba(250, 249, 245, 0.95);
                border-bottom: 1px solid #f0eee6;
                z-index: 1000000;
                display: flex;
                justify-content: space-between;
                align-items: center;
                padding: 0 16px;
                height: 48px;
            }

            .reader-toolbar-left, .reader-toolbar-right {
                display: flex;
                align-items: center;
                gap: 4px;
            }

            .reader-back-button {
                background: transparent;
                border: none;
                width: 36px;
                height: 36px;
                border-radius: 8px;
                cursor: pointer;
                display: flex;
                align-items: center;
                justify-content: center;
                font-size: 18px;
                color: #5e5d59;
            }

            .reader-toolbar-title {
                font-size: 14px;
                font-weight: 500;
                color: #141413;
                max-width: 300px;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
                margin-left: 4px;
            }

            .reader-icon-button {
                width: 36px;
                height: 36px;
                border: none;
                background: transparent;
                color: #5e5d59;
                cursor: pointer;
                border-radius: 8px;
                display: flex;
                align-items: center;
                justify-content: center;
                font-size: 15px;
            }

            .reader-icon-button:hover {
                background: #e8e6dc;
                color: #141413;
            }

            .reader-progress-bar {
                position: fixed;
                top: 48px;
                left: 0;
                width: 100%;
                height: 2px;
                background: #f0eee6;
                z-index: 999999;
            }

            .reader-progress-fill {
                height: 100%;
                width: 0%;
                background: #c96442;
                transition: width 0.15s ease-out;
            }

            .reader-content-container {
                max-width: 680px;
                margin: 0 auto;
                padding: 24px 20px 80px;
            }

            .reader-article {
                font-family: Georgia, serif;
                font-size: 18px;
                line-height: 1.8;
                color: #141413;
            }

            .loading-container {
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                min-height: 400px;
                gap: 16px;
            }

            .loading-spinner {
                position: relative;
                width: 36px;
                height: 36px;
            }

            .spinner-ring {
                position: absolute;
                width: 100%;
                height: 100%;
                border: 3px solid transparent;
                border-top-color: #c96442;
                border-radius: 50%;
                animation: spin 0.8s linear infinite;
            }

            .loading-text {
                font-size: 14px;
                color: #5e5d59;
            }

            .reader-translate-fab {
                position: fixed;
                bottom: 20px;
                left: 50%;
                transform: translateX(-50%);
                display: flex;
                align-items: center;
                gap: 6px;
                padding: 10px 20px;
                border: 1px solid #f0eee6;
                border-radius: 20px;
                background: rgba(250, 249, 245, 0.95);
                color: #141413;
                cursor: pointer;
                font-size: 13px;
                font-weight: 500;
                z-index: 1000001;
                box-shadow: 0 4px 16px rgba(0,0,0,0.08);
            }
        `;
        document.head.appendChild(style);
    }

    // 应用阅读偏好设置
    applyReadingPreferences() {
        const overlay = this.overlay;
        if (!overlay) return;

        overlay.className = `extreme-reader-overlay novel-theme-${this.readingPreferences.theme}`;

        overlay.style.setProperty('--reader-font-size', `${this.readingPreferences.fontSize}px`);
        overlay.style.setProperty('--reader-line-height', this.readingPreferences.lineHeight);
        overlay.style.setProperty('--reader-max-width', `${this.readingPreferences.maxWidth}px`);
        overlay.style.setProperty('--reader-align', this.readingPreferences.textAlign);
        overlay.style.setProperty('--reader-indent', this.readingPreferences.showFirstIndent ? '2em' : '0');

        const fontFamilies = {
            serif: "'Georgia', '思源宋体', 'SimSun', serif",
            sans: "-apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif",
            kai: "'KaiTi', '楷体', serif"
        };
        overlay.style.setProperty('--reader-font', fontFamilies[this.readingPreferences.fontFamily]);

        this.updateSettingsPanelUI();
    }

    // 更新设置面板 UI 状态
    updateSettingsPanelUI() {
        document.querySelectorAll('.font-option').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.font === this.readingPreferences.fontFamily);
        });

        document.querySelectorAll('.theme-option').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.theme === this.readingPreferences.theme);
        });

        document.querySelectorAll('.align-option').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.align === this.readingPreferences.textAlign);
        });

        const indentCheck = document.getElementById('first-indent-check');
        if (indentCheck) {
            indentCheck.checked = this.readingPreferences.showFirstIndent;
        }

        // 更新原生滑块值
        const fontSizeRange = document.getElementById('font-size-range');
        if (fontSizeRange) {
            fontSizeRange.value = this.readingPreferences.fontSize;
            document.getElementById('font-size-value').textContent = `${this.readingPreferences.fontSize}px`;
        }

        const lineHeightRange = document.getElementById('line-height-range');
        if (lineHeightRange) {
            lineHeightRange.value = this.readingPreferences.lineHeight;
            document.getElementById('line-height-value').textContent = this.readingPreferences.lineHeight;
        }

        const maxWidthRange = document.getElementById('max-width-range');
        if (maxWidthRange) {
            maxWidthRange.value = this.readingPreferences.maxWidth;
            document.getElementById('max-width-value').textContent = `${this.readingPreferences.maxWidth}px`;
        }
    }

    // 绑定事件
    bindEvents() {
        // ESC 键退出（优先关闭设置和目录面板）
        const handleEscKey = (e) => {
            if (e.key === 'Escape' && this.isActive) {
                e.stopPropagation();
                e.preventDefault();

                // 如果灯箱打开，优先关闭灯箱
                if (this.isLightboxOpen()) {
                    this.closeLightbox();
                    return;
                }

                // 检查是否有面板打开（设置或目录）
                const settingsPanel = document.getElementById('reader-settings-panel');
                const tocSidebar = document.getElementById('reader-toc-sidebar');
                const isSettingsOpen = settingsPanel && settingsPanel.classList.contains('open');
                const isTocOpen = tocSidebar && tocSidebar.classList.contains('open');

                // 如果有面板打开，只关闭面板，不退出阅读器
                if (isSettingsOpen || isTocOpen) {
                    this.closeAllPanels();
                } else {
                    // 没有面板打开时才退出阅读器
                    this.deactivate();
                }
            }
        };

        this.EVENT_LISTENERS.set('esc-key-handler', handleEscKey);
        document.addEventListener('keydown', handleEscKey, true);

        // 工具栏自动隐藏/显示 - 仿照小说阅读器
        this.setupToolbarAutoHide();

        // 图片灯箱 - 点击图片放大
        this.setupImageLightbox();

        // 按钮事件
        this.setupButtonEventListeners();

        // 关闭灯箱事件
        const closeLightboxBtn = document.querySelector('.reader-image-lightbox-close');
        if (closeLightboxBtn) {
            closeLightboxBtn.addEventListener('click', () => this.closeLightbox());
        }

        // 点击灯箱背景关闭
        const lightbox = document.getElementById('reader-image-lightbox');
        if (lightbox) {
            lightbox.addEventListener('click', (e) => {
                if (e.target === lightbox || e.target.tagName === 'IMG') {
                    this.closeLightbox();
                }
            });
        }
    }

    // 关闭所有面板
    closeAllPanels() {
        this.closeTocSidebar();
        this.closeSettingsPanel();
    }

    // 设置按钮事件监听
    setupButtonEventListeners() {
        const buttonEvents = {
            'reader-back-btn': () => this.deactivate(),
            'reader-more-btn': () => this.toggleSettingsPanel(),
            'reader-tts-btn': () => this.toggleTTS(),
            'reader-translate-btn': () => this.translateArticle(),
            'reader-switch-language': () => this.toggleLanguageView(),
            'toc-close-btn': () => this.closeTocSidebar(),
            'reader-bookmark-btn': () => this.toggleBookmark(),
            'reader-font-minus': () => this.adjustFontSize(-2),
            'reader-font-plus': () => this.adjustFontSize(2),
            'reader-toc-btn': () => this.toggleTocSidebar()
        };

        Object.entries(buttonEvents).forEach(([id, handler]) => {
            const element = document.getElementById(id);
            if (element) {
                const oldHandler = this.EVENT_LISTENERS.get(id);
                if (oldHandler) {
                    element.removeEventListener('click', oldHandler);
                }
                element.addEventListener('click', handler);
                this.EVENT_LISTENERS.set(id, handler);
            }
        });

        // 设置面板内的控件
        this.setupSettingsControls();

        // 主题选项
        document.querySelectorAll('.theme-option').forEach(btn => {
            btn.addEventListener('click', () => {
                this.setTheme(btn.dataset.theme);
            });
        });

        // 进度条点击跳转
        this.setupProgressClick();

        // 遮罩层点击关闭
        this.setupBackdropClick();
    }

    // 设置设置面板控件
    setupSettingsControls() {
        document.querySelectorAll('.font-option').forEach(btn => {
            btn.addEventListener('click', () => {
                this.setFontFamily(btn.dataset.font);
            });
        });

        document.querySelectorAll('.align-option').forEach(btn => {
            btn.addEventListener('click', () => {
                this.setTextAlign(btn.dataset.align);
            });
        });

        const indentCheck = document.getElementById('first-indent-check');
        if (indentCheck) {
            indentCheck.addEventListener('change', (e) => {
                this.setFirstIndent(e.target.checked);
            });
        }

        // 原生滑块
        const fontSizeRange = document.getElementById('font-size-range');
        if (fontSizeRange) {
            fontSizeRange.addEventListener('input', (e) => {
                const value = parseInt(e.target.value);
                this.readingPreferences.fontSize = value;
                document.getElementById('font-size-value').textContent = `${value}px`;
                this.applyReadingPreferences();
            });
            fontSizeRange.addEventListener('change', () => {
                this.saveReadingPreferences();
            });
            fontSizeRange.value = this.readingPreferences.fontSize;
        }

        const lineHeightRange = document.getElementById('line-height-range');
        if (lineHeightRange) {
            lineHeightRange.addEventListener('input', (e) => {
                const value = parseFloat(e.target.value);
                this.readingPreferences.lineHeight = value;
                document.getElementById('line-height-value').textContent = value.toFixed(1);
                this.applyReadingPreferences();
            });
            lineHeightRange.addEventListener('change', () => {
                this.saveReadingPreferences();
            });
            lineHeightRange.value = this.readingPreferences.lineHeight;
        }

        const maxWidthRange = document.getElementById('max-width-range');
        if (maxWidthRange) {
            maxWidthRange.addEventListener('input', (e) => {
                const value = parseInt(e.target.value);
                this.readingPreferences.maxWidth = value;
                document.getElementById('max-width-value').textContent = `${value}px`;
                this.applyReadingPreferences();
            });
            maxWidthRange.addEventListener('change', () => {
                this.saveReadingPreferences();
            });
            maxWidthRange.value = this.readingPreferences.maxWidth;
        }
    }

    // ========== 字体调整 ==========
    adjustFontSize(delta) {
        const newFontSize = Math.max(14, Math.min(32, this.readingPreferences.fontSize + delta));
        if (newFontSize !== this.readingPreferences.fontSize) {
            this.readingPreferences.fontSize = newFontSize;
            this.applyReadingPreferences();
            this.saveReadingPreferences();
            this.showNotification(`字体大小：${newFontSize}px`, 'info');
        }
    }

    // ========== 字体设置 ==========
    setFontFamily(fontFamily) {
        this.readingPreferences.fontFamily = fontFamily;
        this.applyReadingPreferences();
        this.saveReadingPreferences();
        this.updateSettingsPanelUI();
    }

    // ========== 文字对齐设置 ==========
    setTextAlign(align) {
        this.readingPreferences.textAlign = align;
        this.applyReadingPreferences();
        this.saveReadingPreferences();
        this.updateSettingsPanelUI();
    }

    // ========== 首行缩进设置 ==========
    setFirstIndent(enabled) {
        this.readingPreferences.showFirstIndent = enabled;
        this.applyReadingPreferences();
        this.saveReadingPreferences();
    }

    // ========== 主题设置 ==========
    setTheme(theme) {
        this.readingPreferences.theme = theme;
        this.applyReadingPreferences();
        this.saveReadingPreferences();

        const themeNames = {
            light: '日间',
            sepia: '护眼',
            eyecare: '绿色',
            dark: '夜间',
            paper: '纸质'
        };
        this.showNotification(`已切换到${themeNames[theme] || theme}模式`, 'info');
    }

    // 循环切换主题（用于主题按钮）
    cycleTheme() {
        const themes = ['light', 'sepia', 'eyecare', 'dark', 'paper'];
        const themeNames = {
            light: '日间',
            sepia: '护眼',
            eyecare: '绿色',
            dark: '夜间',
            paper: '纸质'
        };

        const currentIndex = themes.indexOf(this.readingPreferences.theme);
        const nextIndex = (currentIndex + 1) % themes.length;
        const nextTheme = themes[nextIndex];

        this.setTheme(nextTheme);
        this.showNotification(`已切换到${themeNames[nextTheme]}模式`, 'info');
    }

    // ========== 书签功能 ==========
    toggleBookmark() {
        const bookmarkBtn = document.getElementById('reader-bookmark-btn');
        if (!bookmarkBtn) return;

        const currentProgress = this.readProgress;
        const bookmarkIndex = this.bookmarks.findIndex(b => Math.abs(b.progress - currentProgress) < 1);

        if (bookmarkIndex >= 0) {
            // 移除书签
            this.bookmarks.splice(bookmarkIndex, 1);
            bookmarkBtn.classList.remove('active');
            this.showNotification('已移除书签', 'info');
        } else {
            // 添加书签
            const bookmark = {
                progress: currentProgress,
                timestamp: Date.now(),
                title: this.extractedTitle || document.title,
                scrollPosition: window.scrollY
            };
            this.bookmarks.push(bookmark);
            bookmarkBtn.classList.add('active');
            this.showNotification('已添加书签', 'success');
        }

        this.saveBookmarks();
        this.updateBookmarkButtonUI();
    }

    // 保存书签
    async saveBookmarks() {
        try {
            await browser.storage.local.set({
                readerBookmarks: this.bookmarks
            });
        } catch (error) {
            console.warn('保存书签失败:', error);
        }
    }

    // 加载书签
    async loadBookmarks() {
        try {
            const result = await browser.storage.local.get(['readerBookmarks']);
            if (result.readerBookmarks) {
                this.bookmarks = result.readerBookmarks;
            }
        } catch (error) {
            console.warn('加载书签失败:', error);
        }
    }

    // 更新书签按钮 UI
    updateBookmarkButtonUI() {
        const bookmarkBtn = document.getElementById('reader-bookmark-btn');
        if (!bookmarkBtn) return;

        const currentProgress = this.readProgress;
        const hasBookmark = this.bookmarks.some(b => Math.abs(b.progress - currentProgress) < 1);

        if (hasBookmark) {
            bookmarkBtn.classList.add('active');
            bookmarkBtn.innerHTML = '<i class="ri-bookmark-fill"></i>';
        } else {
            bookmarkBtn.classList.remove('active');
            bookmarkBtn.innerHTML = '<i class="ri-bookmark-line"></i>';
        }
    }

    // ========== 进度条点击/拖动跳转 ==========
    setupProgressClick() {
        const progressBar = document.getElementById('reader-progress-bar');
        if (!progressBar) return;

        let isDragging = false;

        const scrollToPercent = (percent) => {
            const targetScroll = (document.documentElement.scrollHeight - window.innerHeight) * percent;
            window.scrollTo({
                top: targetScroll,
                behavior: 'auto'
            });
        };

        const handleMove = (clientX) => {
            const rect = progressBar.getBoundingClientRect();
            const percent = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
            scrollToPercent(percent);
        };

        // 鼠标事件
        progressBar.addEventListener('mousedown', (e) => {
            isDragging = true;
            e.preventDefault();
            handleMove(e.clientX);
        });

        document.addEventListener('mousemove', (e) => {
            if (isDragging) {
                e.preventDefault();
                handleMove(e.clientX);
            }
        });

        document.addEventListener('mouseup', () => {
            isDragging = false;
        });

        // 触摸事件（移动端支持）
        progressBar.addEventListener('touchstart', (e) => {
            isDragging = true;
            handleMove(e.touches[0].clientX);
        }, { passive: true });

        progressBar.addEventListener('touchmove', (e) => {
            if (isDragging) {
                handleMove(e.touches[0].clientX);
            }
        }, { passive: true });

        progressBar.addEventListener('touchend', () => {
            isDragging = false;
        });
    }

    // ========== 遮罩层点击关闭 ==========
    setupBackdropClick() {
        const tocBackdrop = document.getElementById('toc-backdrop');
        const settingsBackdrop = document.getElementById('settings-backdrop');

        if (tocBackdrop) {
            tocBackdrop.addEventListener('click', () => this.closeTocSidebar());
        }

        if (settingsBackdrop) {
            settingsBackdrop.addEventListener('click', () => this.closeSettingsPanel());
        }
    }

    // ========== 目录侧边栏 ==========
    toggleTocSidebar() {
        const sidebar = document.getElementById('reader-toc-sidebar');
        const backdrop = document.getElementById('toc-backdrop');
        if (sidebar) {
            const isOpen = sidebar.classList.toggle('open');
            if (backdrop) {
                backdrop.classList.toggle('visible', isOpen);
            }
        }
    }

    closeTocSidebar() {
        const sidebar = document.getElementById('reader-toc-sidebar');
        const backdrop = document.getElementById('toc-backdrop');
        if (sidebar) {
            sidebar.classList.remove('open');
        }
        if (backdrop) {
            backdrop.classList.remove('visible');
        }
    }

    // ========== 设置面板 ==========
    toggleSettingsPanel() {
        const panel = document.getElementById('reader-settings-panel');
        const backdrop = document.getElementById('settings-backdrop');
        if (panel) {
            const isOpen = panel.classList.toggle('open');
            if (backdrop) {
                backdrop.classList.toggle('visible', isOpen);
            }
        }
    }

    closeSettingsPanel() {
        const panel = document.getElementById('reader-settings-panel');
        const backdrop = document.getElementById('settings-backdrop');
        if (panel) {
            panel.classList.remove('open');
        }
        if (backdrop) {
            backdrop.classList.remove('visible');
        }
    }

    // ========== 更新进度指示器 ==========
    updateProgressIndicator() {
        // 更新顶部进度条（如果存在）
        const fill = document.querySelector('.reader-progress-fill');
        if (fill) {
            fill.style.width = `${this.readProgress}%`;
        }

        // 更新书签按钮状态
        this.updateBookmarkButtonUI();
    }

    // ========== TTS 朗读功能 ==========
    async toggleTTS() {
        if (!('speechSynthesis' in window)) {
            this.showNotification('当前浏览器不支持朗读功能', 'error');
            return;
        }

        const ttsBtn = document.getElementById('reader-tts-btn');

        if (this.ttsPlaying) {
            // 暂停/停止
            speechSynthesis.cancel();
            this.ttsPlaying = false;
            if (ttsBtn) {
                ttsBtn.classList.remove('active');
                ttsBtn.title = '朗读文章';
            }
            this.showNotification('朗读已暂停', 'info');
        } else {
            // 开始朗读
            const content = this.showingTranslated && this.translatedContent
                ? this.translatedContent
                : this.originalContent;

            if (!content) {
                this.showNotification('没有可朗读的内容', 'error');
                return;
            }

            // 提取纯文本
            const tempDiv = document.createElement('div');
            tempDiv.innerHTML = content;
            const text = tempDiv.textContent || tempDiv.innerText;

            const utterance = new SpeechSynthesisUtterance(text);

            // 获取用户设置的目标语言
            let targetLang = 'zh';
            try {
                const result = await browser.storage.local.get(['settings']);
                if (result.settings) {
                    targetLang = result.settings.target_lang || 'zh';
                }
            } catch (error) {
                console.warn('获取语言设置失败:', error);
            }

            // 设置朗读语言
            const langMap = {
                'zh': 'zh-CN',
                'en': 'en-US',
                'ja': 'ja-JP',
                'ko': 'ko-KR',
                'fr': 'fr-FR',
                'de': 'de-DE',
                'es': 'es-ES',
                'ru': 'ru-RU'
            };
            utterance.lang = langMap[targetLang] || 'zh-CN';
            utterance.rate = 1.0;
            utterance.pitch = 1.0;

            utterance.onend = () => {
                this.ttsPlaying = false;
                if (ttsBtn) {
                    ttsBtn.classList.remove('active');
                    ttsBtn.title = '朗读文章';
                }
            };

            utterance.onerror = (event) => {
                console.error('TTS 错误:', event);
                this.ttsPlaying = false;
                if (ttsBtn) {
                    ttsBtn.classList.remove('active');
                }
                this.showNotification('朗读出错', 'error');
            };

            speechSynthesis.speak(utterance);
            this.ttsPlaying = true;
            this.ttsUtterance = utterance;

            if (ttsBtn) {
                ttsBtn.classList.add('active');
                ttsBtn.title = '暂停朗读';
            }
            this.showNotification('开始朗读', 'info');
        }
    }

    // ========== 图片灯箱 ==========
    isLightboxOpen() {
        const lightbox = document.getElementById('reader-image-lightbox');
        return lightbox && lightbox.classList.contains('open');
    }

    closeLightbox() {
        const lightbox = document.getElementById('reader-image-lightbox');
        if (lightbox) {
            lightbox.classList.remove('open');
            setTimeout(() => {
                lightbox.remove();
            }, 300);
        }
    }

    openLightbox(src) {
        // 关闭已存在的灯箱
        this.closeLightbox();

        const lightbox = document.createElement('div');
        lightbox.id = 'reader-image-lightbox';
        lightbox.className = 'reader-image-lightbox';
        lightbox.innerHTML = `
            <button class="reader-image-lightbox-close"><i class="ri-close-line"></i></button>
            <img src="${src}" alt="放大图片">
        `;

        document.body.appendChild(lightbox);

        // 关闭按钮
        lightbox.querySelector('.reader-image-lightbox-close').addEventListener('click', () => {
            this.closeLightbox();
        });

        // 点击背景关闭
        lightbox.addEventListener('click', (e) => {
            if (e.target === lightbox) {
                this.closeLightbox();
            }
        });

        // ESC 关闭
        const handleEsc = (e) => {
            if (e.key === 'Escape') {
                this.closeLightbox();
                document.removeEventListener('keydown', handleEsc);
            }
        };
        document.addEventListener('keydown', handleEsc);

        // 显示动画
        requestAnimationFrame(() => {
            lightbox.classList.add('open');
        });
    }

    // ========== 设置图片灯箱 ==========
    setupImageLightbox() {
        // 等待内容加载完成
        setTimeout(() => {
            const article = document.querySelector('.reader-article');
            if (!article) return;

            // 为所有图片添加点击事件
            article.querySelectorAll('img').forEach(img => {
                img.style.cursor = 'zoom-in';
                img.addEventListener('click', () => {
                    this.openLightbox(img.src);
                });
            });
        }, 500);
    }

    // ========== 设置工具栏自动隐藏/显示 ==========
    setupToolbarAutoHide() {
        const toolbar = document.querySelector('.reader-toolbar');
        if (!toolbar) return;

        let lastScrollY = window.scrollY;
        let ticking = false;
        let hideTimeout = null;

        const updateToolbar = () => {
            const currentScrollY = window.scrollY;
            const toolbarHeight = toolbar.offsetHeight;

            // 向下滚动且超过工具栏高度时隐藏
            if (currentScrollY > lastScrollY && currentScrollY > toolbarHeight) {
                toolbar.classList.add('hidden');
                // 隐藏时同时关闭设置面板和目录侧边栏
                this.closeSettingsPanel();
                this.closeTocSidebar();
            } else {
                toolbar.classList.remove('hidden');
            }

            lastScrollY = currentScrollY;
            ticking = false;
        };

        // 监听滚动
        window.addEventListener('scroll', () => {
            if (!ticking) {
                window.requestAnimationFrame(updateToolbar);
                ticking = true;
            }
        }, { passive: true });

        // 鼠标移动到顶部区域时显示工具栏
        document.addEventListener('mousemove', (e) => {
            if (e.clientY < 60) {
                toolbar.classList.remove('hidden');
            }

            // 自动隐藏工具栏（鼠标静止 3 秒后）
            clearTimeout(hideTimeout);
            hideTimeout = setTimeout(() => {
                if (window.scrollY > 100) {
                    toolbar.classList.add('hidden');
                }
            }, 3000);
        });

        // 初始隐藏超时
        hideTimeout = setTimeout(() => {
            if (window.scrollY > 100) {
                toolbar.classList.add('hidden');
            }
        }, 3000);
    }

    // ========== 生成目录 ==========
    generateTOC() {
        const article = document.querySelector('.reader-article');
        if (!article) return;

        const headings = article.querySelectorAll('h1, h2, h3');
        this.tocData = [];
        this.chapterList = [];

        headings.forEach((heading, index) => {
            const id = `reader-heading-${index}`;
            heading.id = id;

            const chapterInfo = {
                id,
                text: heading.textContent,
                level: heading.tagName.toLowerCase(),
                index
            };

            this.tocData.push(chapterInfo);

            // h1 作为章节标题用于章节导航
            if (heading.tagName === 'H1') {
                this.chapterList.push(chapterInfo);
            }
        });

        // 设置当前章节索引
        if (this.chapterList.length > 0) {
            this.currentChapterIndex = 0;
        }

        this.renderTOC();
    }

    // ========== 渲染目录 ==========
    renderTOC() {
        const tocContent = document.getElementById('toc-content');
        if (!tocContent || this.tocData.length === 0) return;

        tocContent.innerHTML = this.tocData.map(item => `
            <a href="#${item.id}" class="toc-item toc-${item.level}" data-target="${item.id}">
                ${item.text}
            </a>
        `).join('');

        // 绑定点击事件
        tocContent.querySelectorAll('.toc-item').forEach(item => {
            item.addEventListener('click', (e) => {
                e.preventDefault();
                const targetId = item.dataset.target;
                const target = document.getElementById(targetId);
                if (target) {
                    target.scrollIntoView({ behavior: 'smooth', block: 'start' });

                    // 更新激活状态
                    tocContent.querySelectorAll('.toc-item').forEach(i => i.classList.remove('active'));
                    item.classList.add('active');

                    // 关闭侧边栏（移动端）
                    this.closeTocSidebar();
                }
            });
        });
    }

    // ========== 显示通知 ==========
    showNotification(message, type = 'info') {
        // 移除已存在的通知
        const existing = document.querySelector('.reader-notification');
        if (existing) {
            existing.remove();
        }

        const notification = document.createElement('div');
        notification.className = `reader-notification reader-notification-${type}`;
        notification.textContent = message;

        document.body.appendChild(notification);

        // 3 秒后自动移除
        setTimeout(() => {
            notification.style.animation = 'fadeIn 0.2s reverse';
            setTimeout(() => notification.remove(), 200);
        }, 3000);
    }

    // ========== 提取内容（使用 Readability）==========
    async extractContentWithReadability() {
        try {
            // 依赖检查
            const dependencies = [
                { name: 'Readability', obj: typeof Readability !== 'undefined' ? Readability : undefined },
                { name: 'DOMPurify', obj: typeof DOMPurify !== 'undefined' ? DOMPurify : undefined },
                { name: 'GlobalConfig', obj: typeof GlobalConfig !== 'undefined' ? GlobalConfig : undefined }
            ];

            for (const dep of dependencies) {
                if (typeof dep.obj === 'undefined') {
                    console.error('[Reader] 依赖未加载:', dep.name, 'typeof', dep.name, '=', typeof dep.obj);
                    throw new Error(`${dep.name} 未加载，请检查 manifest.json`);
                } else {
                    console.log('[Reader] 依赖已加载:', dep.name);
                }
            }

            console.log('[Reader] 开始提取页面内容...');
            console.log('[Reader] 当前页面 HTML 长度:', document.documentElement.outerHTML.length);

            // 克隆文档
            const documentClone = document.cloneNode(true);

            // 重要：移除阅读模式自身的 overlay 元素，防止 Readability 错误地将其识别为内容
            const readerOverlay = documentClone.querySelector('#extreme-reader-overlay');
            if (readerOverlay) {
                readerOverlay.remove();
                console.log('[Reader] 已移除克隆文档中的 overlay 元素');
            }

            // 移除其他可能的干扰元素（脚本、样式等）
            const scripts = documentClone.querySelectorAll('script');
            scripts.forEach(script => script.remove());

            const styles = documentClone.querySelectorAll('style');
            styles.forEach(style => style.remove());

            let article;
            try {
                const readabilityInstance = new Readability(documentClone);
                console.log('[Reader] Readability 实例创建成功');
                article = readabilityInstance.parse();
                console.log('[Reader] Readability.parse() 返回:', article ? '成功' : 'null');
            } catch (readabilityError) {
                console.error('[Reader] Readability 解析出错:', readabilityError);
                throw new Error('页面内容解析失败：' + readabilityError.message);
            }

            if (!article) {
                console.error('[Reader] Readability 返回 null，可能页面不包含可读内容');
                throw new Error('Readability 无法提取文章内容');
            }

            // 检查是否有可提取的内容
            if (!article.content || article.content.trim().length === 0) {
                console.error('[Reader] article.content 为空');
                throw new Error('页面不包含可读内容');
            }

            console.log('[Reader] 内容提取成功:', {
                title: article.title,
                contentLength: article.content.length,
                byline: article.byline,
                excerpt: article.excerpt
            });

            // 清洗 HTML
            let cleanContent = '';
            if (article.content) {
                cleanContent = DOMPurify.sanitize(article.content, {
                    ALLOWED_TAGS: [
                        'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
                        'p', 'br', 'hr', 'pre', 'blockquote',
                        'ul', 'ol', 'li', 'dl', 'dt', 'dd',
                        'table', 'thead', 'tbody', 'tr', 'th', 'td',
                        'a', 'strong', 'em', 'b', 'i', 'u', 's',
                        'code', 'sub', 'sup', 'img', 'figure', 'figcaption',
                        'div', 'span', 'section', 'article'
                    ],
                    ALLOWED_ATTR: ['href', 'target', 'rel', 'src', 'alt', 'title', 'width', 'height', 'class', 'id']
                });
            }

            // 获取设置
            let targetLang = 'zh';
            let engine = GlobalConfig.DEFAULT_SETTINGS.engine;
            try {
                const result = await browser.storage.local.get(['settings']);
                if (result.settings) {
                    targetLang = result.settings.target_lang || 'zh';
                    engine = result.settings.engine || GlobalConfig.DEFAULT_SETTINGS.engine;
                }
            } catch (error) {
                console.warn('获取设置失败:', error);
            }

            // 生成缓存 key
            this.translationCacheKey = `reader-${window.location.href}-${engine}-${targetLang}`;
            this.currentEngine = engine;

            // 保存原文
            this.originalContent = cleanContent;

            // 保存元数据
            this.extractedTitle = article.title;
            this.extractedByline = article.byline || '';

            // 检查缓存
            await this.checkTranslationCache(targetLang, engine);
            const hasCachedTranslation = !!this.translatedContent;

            this.updateEngineButton(engine);

            if (hasCachedTranslation) {
                return {
                    success: true,
                    title: this.extractedTitle || article.title,
                    byline: this.extractedByline || article.byline,
                    excerpt: article.excerpt,
                    content: cleanContent,
                    translatedContent: this.translatedContent,
                    length: article.length,
                    engine: this.currentEngine
                };
            }

            return {
                success: true,
                title: article.title,
                byline: article.byline,
                excerpt: article.excerpt,
                content: cleanContent,
                translatedContent: null,
                length: article.length,
                engine: engine
            };

        } catch (error) {
            console.error('[Reader] 提取失败:', error);
            throw error;
        }
    }

    // ========== 检查翻译缓存 ==========
    async checkTranslationCache(targetLang, engine) {
        try {
            const cacheResult = await browser.storage.local.get([this.translationCacheKey]);
            if (cacheResult[this.translationCacheKey]) {
                const cached = cacheResult[this.translationCacheKey];
                if (Date.now() - cached.timestamp < 24 * 60 * 60 * 1000) {
                    console.log('[Reader] 发现有效缓存');
                    this.translatedContent = cached.translatedContent;
                    return cached;
                }
            }
            return null;
        } catch (error) {
            console.warn('[Reader] 检查缓存失败:', error);
            return null;
        }
    }

    // ========== 发送到 background 处理 ==========
    async sendToBackgroundForProcessing(requestData) {
        return new Promise(async (resolve, reject) => {
            const timeoutId = setTimeout(() => {
                reject(new Error('请求超时'));
            }, 60000);

            let cleanupDone = false;
            const cleanup = () => {
                if (cleanupDone) return;
                cleanupDone = true;
                clearTimeout(timeoutId);
                browser.runtime.onMessage.removeListener(handleMessage);
            };

            const handleMessage = async (request, sender, sendResponseInner) => {
                if (request.action === 'readerTranslationCompleted' && request.success) {
                    console.log('[Reader] 收到翻译结果');

                    if (this.translationCacheKey && request.translatedContent) {
                        browser.storage.local.set({
                            [this.translationCacheKey]: {
                                translatedContent: request.translatedContent,
                                timestamp: Date.now()
                            }
                        }).catch(err => console.warn('保存缓存失败:', err));
                    }

                    cleanup();
                    resolve({
                        success: true,
                        translatedContent: request.translatedContent,
                        engine: request.engine,
                        originalContent: request.originalContent,
                        title: request.title
                    });
                } else if (request.action === 'readerTranslationError') {
                    console.error('[Reader] 翻译错误:', request.error);

                    if (this.isActive) {
                        this.showNotification(`翻译失败：${request.error}`, 'error');
                    }

                    reject(new Error(request.error || '翻译失败'));
                    cleanup();
                }
            };

            try {
                if (typeof GlobalConfig === 'undefined') {
                    throw new Error('GlobalConfig 未定义');
                }

                console.log('[Reader] 发送翻译请求...');

                const initialResponse = await browser.runtime.sendMessage(requestData);

                if (!initialResponse) {
                    cleanup();
                    reject(new Error('未收到后台响应'));
                    return;
                }

                if (initialResponse.success === false) {
                    cleanup();
                    reject(new Error(initialResponse.errorMessage || initialResponse.error || '后台处理失败'));
                    return;
                }

                browser.runtime.onMessage.addListener(handleMessage);

            } catch (error) {
                cleanup();
                console.error('[Reader] 通讯异常:', error);
                reject(error);
            }
        });
    }

    // ========== 翻译文章 ==========
    async translateArticle() {
        // 如果已经有译文且当前显示原文，点击翻译按钮切换到译文
        if (this.translatedContent && !this.showingTranslated) {
            this.showingTranslated = true;
            this.updateSwitchButton();
            this.updateTranslateButton();
            this.displayContent({
                ...this.processedArticle,
                content: this.translatedContent,
                translatedContent: this.translatedContent
            });
            this.showNotification('已切换到译文', 'info');
            return;
        }

        // 如果已经有译文且当前显示译文，点击翻译按钮不做任何操作
        if (this.translatedContent && this.showingTranslated) {
            this.showNotification('当前已显示译文', 'info');
            return;
        }

        if (!this.isActive || !this.originalContent) {
            this.showNotification('无法翻译文章', 'error');
            return;
        }

        let targetLang = 'zh';
        let engine = this.currentEngine || GlobalConfig.DEFAULT_SETTINGS.engine;

        try {
            const result = await browser.storage.local.get(['settings']);
            if (result.settings) {
                targetLang = result.settings.target_lang || 'zh';
                engine = result.settings.engine || engine;
            }
        } catch (error) {
            console.warn('获取设置失败:', error);
        }

        this.currentEngine = engine;
        this.updateEngineButton(engine);
        this.translationCacheKey = `reader-${window.location.href}-${engine}-${targetLang}`;

        this.showNotification('正在翻译...', 'info');

        try {
            // 将标题合并到内容中一起翻译
            const title = this.processedArticle?.title || this.extractedTitle || document.title;
            const contentWithTitle = `<h1>${title}</h1>${this.originalContent}`;

            const backgroundResponse = await this.sendToBackgroundForProcessing({
                action: 'processArticleForReader',
                content: contentWithTitle,
                targetLang: targetLang,
                engine: engine,
                title: title,
                byline: this.processedArticle?.byline || this.extractedByline || ''
            });

            if (backgroundResponse && backgroundResponse.translatedContent) {
                console.log('[Reader] 翻译完成，检查原文和译文:', {
                    originalContentPreview: this.originalContent?.substring(0, 100),
                    translatedContentPreview: backgroundResponse.translatedContent?.substring(0, 100),
                    isSame: this.originalContent === backgroundResponse.translatedContent
                });

                this.translatedContent = backgroundResponse.translatedContent;
                if (this.processedArticle) {
                    this.processedArticle.translatedContent = backgroundResponse.translatedContent;
                    this.processedArticle.engine = engine;
                }

                // 切换到译文
                this.showingTranslated = true;
                this.updateSwitchButton();
                this.updateTranslateButton();

                console.log('[Reader] translateArticle: 准备调用 displayContent', {
                    processedArticleHasContent: !!this.processedArticle?.content,
                    translatedContentLength: backgroundResponse.translatedContent?.length,
                    showingTranslated: this.showingTranslated
                });

                // 显示译文
                this.displayContent({
                    ...this.processedArticle,
                    content: backgroundResponse.translatedContent,
                    translatedContent: backgroundResponse.translatedContent
                });

                this.showNotification('翻译完成', 'success');
            } else {
                throw new Error('翻译结果为空');
            }
        } catch (error) {
            console.error('[Reader] 翻译失败:', error);
            this.showNotification(`翻译失败：${error.message}`, 'error');
        }
    }

    // ========== 更新切换按钮 ==========
    updateSwitchButton() {
        const switchBtn = document.getElementById('reader-switch-language');
        const langText = document.getElementById('reader-lang-text');

        if (switchBtn && langText) {
            if (this.translatedContent) {
                switchBtn.style.display = 'flex';
            } else {
                switchBtn.style.display = 'none';
            }

            if (this.showingTranslated) {
                switchBtn.classList.add('active');
                langText.textContent = '原文';
                switchBtn.title = '切换到原文';
            } else {
                switchBtn.classList.remove('active');
                langText.textContent = '译文';
                switchBtn.title = '切换到译文';
            }
        }
    }

    // ========== 更新翻译按钮 ==========
    updateTranslateButton() {
        const translateBtn = document.getElementById('reader-translate-btn');
        if (translateBtn) {
            if (this.translatedContent) {
                translateBtn.style.display = 'none';
            } else {
                translateBtn.style.display = 'flex';
            }
        }
    }

    // ========== 更新引擎按钮 ==========
    updateEngineButton(engineId) {
        const engineNameEl = document.getElementById('current-engine-name');
        if (engineNameEl && GlobalConfig?.TRANSLATION_ENGINES?.[engineId]) {
            const engineConfig = GlobalConfig.TRANSLATION_ENGINES[engineId];
            engineNameEl.textContent = engineConfig.name;
        }
    }

    // ========== 显示内容 ==========
    displayContent(articleData) {
        console.log('[Reader] displayContent 被调用', {
            hasContent: !!articleData?.content,
            contentLength: articleData?.content?.length,
            hasTranslatedContent: !!articleData?.translatedContent,
            title: articleData?.title,
            showingTranslated: this.showingTranslated
        });

        if (!articleData) {
            console.error('[Reader] articleData 为空');
            this.showNotification('文章数据为空', 'error');
            return false;
        }

        // 检查是否有可显示的内容
        if (!articleData.content && !articleData.title) {
            console.error('[Reader] 文章内容或标题为空');
            this.showNotification('无法获取文章内容', 'error');
            // 显示友好提示
            const contentContainer = document.getElementById('reader-content');
            if (contentContainer) {
                contentContainer.innerHTML = `
                    <div class="loading-container">
                        <div class="loading-text">无法提取页面内容</div>
                        <div class="loading-hint">该页面可能不包含可读的文章内容</div>
                    </div>
                `;
            }
            return false;
        }

        this.processedArticle = articleData;

        // 保存原文和译文（只在未设置时保存）
        // 注意：需要根据 showingTranslated 判断当前 content 是原文还是译文
        console.log('[Reader] displayContent: 保存原文/译文前', {
            showingTranslated: this.showingTranslated,
            hasOriginalContent: !!this.originalContent,
            hasTranslatedContent: !!this.translatedContent,
            articleDataContentLength: articleData.content?.length
        });

        if (this.showingTranslated) {
            // 当前显示译文，content 是译文
            if (articleData.content && !this.translatedContent) {
                this.translatedContent = articleData.content;
                console.log('[Reader] 保存译文，长度:', this.translatedContent.length);
            }
        } else {
            // 当前显示原文，content 是原文
            if (articleData.content && !this.originalContent) {
                this.originalContent = articleData.content;
                console.log('[Reader] 保存原文，长度:', this.originalContent.length);
            }
        }

        // 如果有 translatedContent 字段，单独保存
        if (articleData.translatedContent && !this.translatedContent) {
            this.translatedContent = articleData.translatedContent;
            console.log('[Reader] 保存译文（从 translatedContent 字段），长度:', this.translatedContent.length);
        }

        console.log('[Reader] displayContent: 准备使用的 articleData.content 长度:', articleData.content?.length);
        console.log('[Reader] displayContent: this.translatedContent 长度:', this.translatedContent?.length);
        console.log('[Reader] displayContent: this.originalContent 长度:', this.originalContent?.length);

        const contentContainer = document.getElementById('reader-content');
        console.log('[Reader] reader-content 容器:', contentContainer);
        console.log('[Reader] reader-content innerHTML 长度 (设置前):', contentContainer?.innerHTML?.length);

        if (!contentContainer) {
            console.error('[Reader] 找不到 reader-content 容器');
            this.showNotification('阅读器容器未找到', 'error');
            return false;
        }

        console.log('[Reader] displayContent 开始显示内容', {
            title: articleData.title,
            contentLength: articleData.content?.length,
            hasTranslatedContent: !!articleData.translatedContent,
            showingTranslated: this.showingTranslated,
            isOriginalContent: articleData.content === this.originalContent,
            isTranslatedContent: articleData.content === this.translatedContent
        });

        // 确保 overlay 可见且主题已应用
        const overlay = document.getElementById('extreme-reader-overlay');
        console.log('[Reader] overlay 元素:', overlay);
        if (overlay) {
            // 确保主题类已应用（如果未应用，强制应用默认主题）
            if (!overlay.classList.contains('novel-theme-light') &&
                !overlay.classList.contains('novel-theme-sepia') &&
                !overlay.classList.contains('novel-theme-eyecare') &&
                !overlay.classList.contains('novel-theme-dark') &&
                !overlay.classList.contains('novel-theme-paper')) {
                console.log('[Reader] 主题类未应用，强制应用默认主题');
                this.applyReadingPreferences();
            }
            console.log('[Reader] overlay 类名:', overlay.className);
            console.log('[Reader] overlay 样式 display:', overlay.style.display);
        }

        // 清空容器内容，确保移除加载提示
        contentContainer.innerHTML = '';
        console.log('[Reader] contentContainer.innerHTML 已清空');

        // 确定当前显示的内容类型
        const isShowingTranslated = articleData.content === this.translatedContent;

        // 更新标题（切换原文/译文时标题也要跟着切换）
        const titleEl = document.getElementById('reader-title');
        if (titleEl) {
            const displayTitle = articleData.title || document.title || '阅读模式';
            titleEl.textContent = displayTitle;
            console.log('[Reader] 标题已设置:', displayTitle);
        }

        // 构建文章 HTML
        let html = `<article class="reader-article">`;

        // 文章内标题 - 原文模式手动添加标题，译文模式标题已在内容中
        if (!this.showingTranslated) {
            const displayTitle = articleData.title || document.title || '阅读模式';
            if (displayTitle) {
                html += `<h1 class="reader-title">${displayTitle}</h1>`;
            }
        }

        // 作者信息
        if (articleData.byline) {
            html += `<div class="article-byline">${articleData.byline}</div>`;
        }

        // 处理自定义标签（根据 showingTranslated 决定显示原文还是译文）
        // 重要：添加内联样式确保内容可见
        let content = articleData.content ? this.processCustomTags(articleData.content) : '';

        // 如果 content 为空但有 title，显示提示
        if (!content || content.trim().length === 0) {
            console.warn('[Reader] 警告：文章内容内容为空');
            content = '<p>文章内容提取成功，但内容为空。这可能是因为页面内容已被缓存或页面本身不包含可读内容。</p>';
        }

        console.log('[Reader] 准备插入的内容长度:', content.length);
        html += content;

        html += `</article>`;

        console.log('[Reader] 准备设置 contentContainer.innerHTML，HTML 长度:', html.length);
        contentContainer.innerHTML = html;
        console.log('[Reader] contentContainer.innerHTML 已设置，当前 HTML 长度:', contentContainer.innerHTML.length);

        // 验证内容是否真的被插入到 DOM 中
        const articleElement = contentContainer.querySelector('.reader-article');
        console.log('[Reader] 验证 DOM 内容:', {
            hasArticleElement: !!articleElement,
            articleInnerHTMLLength: articleElement?.innerHTML?.length,
            articleFirstChild: articleElement?.firstChild?.nodeName
        });

        // 调试：检查计算后的样式
        if (articleElement) {
            const computedStyle = window.getComputedStyle(articleElement);
            console.log('[Reader] .reader-article 计算样式:', {
                color: computedStyle.color,
                backgroundColor: computedStyle.backgroundColor,
                display: computedStyle.display,
                visibility: computedStyle.visibility,
                opacity: computedStyle.opacity,
                fontSize: computedStyle.fontSize,
                fontFamily: computedStyle.fontFamily
            });
        }

        // 调试：检查 reader-content 容器的样式
        const containerStyle = window.getComputedStyle(contentContainer);
        console.log('[Reader] .reader-content-container 计算样式:', {
            color: containerStyle.color,
            backgroundColor: containerStyle.backgroundColor,
            display: containerStyle.display,
            visibility: containerStyle.visibility
        });

        if (!articleElement) {
            console.error('[Reader] 警告：.reader-article 元素未找到！');
        }

        // 绑定图片点击事件
        contentContainer.querySelectorAll('.reader-article img').forEach(img => {
            img.addEventListener('click', () => {
                const src = img.src || img.getAttribute('src');
                if (src) {
                    this.openLightbox(src);
                }
            });
        });

        // 更新翻译按钮和切换按钮状态
        this.updateTranslateButton();
        this.updateSwitchButton();

        // 生成目录
        this.generateTOC();

        // 滚动到顶部
        window.scrollTo({ top: 0, behavior: 'smooth' });

        console.log('[Reader] displayContent 完成');
        return true;
    }

    // ========== 处理自定义标签 ==========
    processCustomTags(html) {
        if (!html) return html;

        const temp = document.createElement('div');
        temp.innerHTML = html;

        // 处理中文标签
        const tagMap = {
            '图': 'figure',
            '图标题': 'figcaption',
            '跨度': 'span',
            '作者': 'span'
        };

        Object.entries(tagMap).forEach(([oldTag, newTag]) => {
            const tags = temp.querySelectorAll(oldTag);
            tags.forEach(tag => {
                const newElement = document.createElement(newTag);
                if (oldTag === '图标题') newElement.className = 'image-caption';
                if (oldTag === '跨度') newElement.className = 'highlight';
                if (oldTag === '作者') {
                    newElement.className = 'author-name';
                    newElement.style.fontWeight = 'bold';
                }
                newElement.innerHTML = tag.innerHTML;
                tag.replaceWith(newElement);
            });
        });

        return temp.innerHTML;
    }

    // ========== 切换原文/译文 ==========
    async toggleLanguageView() {
        console.log('[Reader] toggleLanguageView 被调用', {
            showingTranslated: this.showingTranslated,
            hasOriginalContent: !!this.originalContent,
            hasTranslatedContent: !!this.translatedContent,
            originalContentLength: this.originalContent?.length,
            translatedContentLength: this.translatedContent?.length
        });

        if (!this.originalContent) {
            this.showNotification('没有可切换的内容', 'error');
            return;
        }

        // 如果显示原文但没有译文，先翻译
        if (!this.showingTranslated && !this.translatedContent) {
            console.log('[Reader] 没有译文，开始翻译');
            await this.translateArticle();
            return;
        }

        this.showingTranslated = !this.showingTranslated;
        console.log('[Reader] showingTranslated 切换为:', this.showingTranslated);

        const content = this.showingTranslated ? this.translatedContent : this.originalContent;
        // 切换标题：译文时使用原文标题（因为没有翻译标题），原文时使用原文标题
        const title = this.extractedTitle || document.title || '阅读模式';
        console.log('[Reader] 准备显示的内容', {
            isShowingTranslated: this.showingTranslated,
            contentLength: content?.length,
            contentPreview: content?.substring(0, 50),
            title: title
        });

        this.updateSwitchButton();
        this.updateTranslateButton();

        this.displayContent({
            ...this.processedArticle,
            title: title,
            content: content,
            translatedContent: this.translatedContent
        });

        const msg = this.showingTranslated ? '已显示译文' : '已显示原文';
        this.showNotification(msg, 'info');
    }

    // ========== 激活阅读模式 ==========
    async activate() {
        console.log('[Reader] 开始激活阅读模式');
        console.log('[Reader] 当前文档 URL:', window.location.href);
        console.log('[Reader] 当前文档 title:', document.title);

        // 检查是否已经激活或正在激活
        if (this.isActive) {
            console.log('[Reader] 阅读模式已激活，跳过');
            return Promise.resolve({ success: true, message: '阅读模式已激活' });
        }

        if (this.isActivating) {
            console.log('[Reader] 正在激活中，跳过');
            return Promise.resolve({ success: true, message: '正在激活中' });
        }

        // 检查是否已经存在 overlay
        const existingOverlay = document.getElementById('extreme-reader-overlay');
        if (existingOverlay) {
            console.log('[Reader] 发现已存在的 overlay，直接显示');
            existingOverlay.style.display = 'block';
            existingOverlay.style.opacity = '1';
            existingOverlay.style.visibility = 'visible';
            this.overlay = existingOverlay;
            this.isActive = true;
            return Promise.resolve({ success: true, message: '阅读模式已激活' });
        }

        this.isActivating = true;

        try {
            console.log('[Reader] 开始创建 overlay');
            await this.createOverlay();
            this.isActive = true;
            console.log('[Reader] overlay 创建完成，isActive =', this.isActive);

            // 提取并显示内容
            console.log('[Reader] 开始提取内容');
            let articleData;
            try {
                articleData = await this.extractContentWithReadability();
                console.log('[Reader] extractContentWithReadability 返回:', {
                    success: articleData?.success,
                    title: articleData?.title,
                    contentLength: articleData?.content?.length,
                    hasTranslatedContent: !!articleData?.translatedContent
                });
            } catch (extractError) {
                console.error('[Reader] extractContentWithReadability 失败:', extractError);
                // 显示错误信息到内容区域
                this.showErrorInContent(extractError.message);
                this.isActivating = false;
                return { success: false, error: extractError.message };
            }

            console.log('[Reader] 内容提取完成:', articleData);

            // 重置激活状态（放在 try 块内确保一定会执行）
            this.isActivating = false;

            if (articleData && articleData.success) {
                console.log('[Reader] 文章数据提取成功，准备显示:', {
                    title: articleData.title,
                    contentLength: articleData.content?.length,
                    hasTranslatedContent: !!articleData.translatedContent
                });

                // 优先显示原文
                this.showingTranslated = false;
                const displayResult = this.displayContent({
                    ...articleData,
                    content: articleData.content,
                    translatedContent: articleData.translatedContent
                });
                console.log('[Reader] displayContent 执行结果:', displayResult);

                // 如果有缓存译文，显示切换按钮
                if (articleData.translatedContent) {
                    this.translatedContent = articleData.translatedContent;
                    this.updateSwitchButton();
                    this.showNotification('使用缓存的翻译结果', 'info');
                }
            } else {
                console.error('[Reader] 文章数据提取失败:', articleData);
                this.showNotification('无法提取页面内容', 'error');
            }

            return { success: true, data: articleData };

        } catch (error) {
            console.error('[Reader] 激活失败:', error);
            this.isActivating = false;
            this.showNotification(`激活失败：${error.message}`, 'error');
            return { success: false, error: error.message };
        }
    }

    // 在内容区域显示错误信息
    showErrorInContent(errorMessage) {
        const contentContainer = document.getElementById('reader-content');
        if (contentContainer) {
            contentContainer.innerHTML = `
                <div class="loading-container">
                    <div class="loading-text">无法加载文章内容</div>
                    <div class="loading-hint">${errorMessage}</div>
                </div>
            `;
        }
    }

    // ========== 退出阅读模式 ==========
    async deactivate() {
        console.log('[Reader] 退出阅读模式');

        if (!this.isActive) return;

        // 停止 TTS
        if (this.ttsPlaying) {
            speechSynthesis.cancel();
            this.ttsPlaying = false;
        }

        // 关闭灯箱
        this.closeLightbox();

        // 移除所有覆盖层（包括可能存在的重复元素）
        const allOverlays = document.querySelectorAll('#extreme-reader-overlay');
        allOverlays.forEach(overlay => {
            overlay.style.opacity = '0';
            overlay.style.transition = 'opacity 0.2s ease';
            setTimeout(() => {
                overlay.remove();
            }, 200);
        });

        // 也移除可能存在的灯箱
        const lightbox = document.getElementById('reader-image-lightbox');
        if (lightbox) {
            lightbox.remove();
        }

        this.overlay = null;

        // 解绑事件
        this.unbindEvents();

        // 移除滚动监听
        if (this.scrollHandler) {
            window.removeEventListener('scroll', this.scrollHandler);
        }

        this.isActive = false;
        this.isActivating = false;

        // 恢复原始滚动位置
        window.scrollTo({ top: this.originalScrollPosition, behavior: 'smooth' });

        this.showNotification('已退出阅读模式', 'info');
    }

    // ========== 解绑事件 ==========
    unbindEvents() {
        const escHandler = this.EVENT_LISTENERS.get('esc-key-handler');
        if (escHandler) {
            document.removeEventListener('keydown', escHandler, true);
            this.EVENT_LISTENERS.delete('esc-key-handler');
        }

        const buttonIds = [
            'reader-back-btn', 'reader-more-btn',
            'reader-tts-btn', 'reader-translate-btn', 'reader-switch-language',
            'toc-close-btn', 'reader-bookmark-btn',
            'reader-theme-btn', 'reader-toc-btn',
            'reader-font-minus', 'reader-font-plus'
        ];

        buttonIds.forEach(id => {
            const handler = this.EVENT_LISTENERS.get(id);
            if (handler) {
                const element = document.getElementById(id);
                if (element) {
                    element.removeEventListener('click', handler);
                }
                this.EVENT_LISTENERS.delete(id);
            }
        });

        this.EVENT_LISTENERS.clear();
    }
}

// ========== 全局实例 ==========
let readerModeManager = null;

// 动态注入阅读器样式（当内容脚本是动态注入时，CSS 不会自动注入）
function injectReaderStyles() {
    if (document.getElementById('reader-styles-injected')) return;
    const link = document.createElement('link');
    link.id = 'reader-styles-injected';
    link.rel = 'stylesheet';
    link.href = browser.runtime.getURL('src/content/reader-styles.css');
    document.head.appendChild(link);
    console.log('[Reader] 动态注入阅读器样式');
}

console.log('[Reader] read.js 开始加载，document.readyState:', document.readyState);

// 立即注入样式（无论是否是动态注入）
injectReaderStyles();

// 监听来自 background 的消息
browser.runtime.onMessage.addListener((request, sender, sendResponse) => {
    console.log('[Reader] 收到消息:', request.action, request);

    if (request.action === 'activateReaderMode') {
        console.log('[Reader] 收到激活阅读模式请求');

        // 确保样式已注入
        injectReaderStyles();

        // 检查是否已经存在 overlay
        const existingOverlay = document.getElementById('extreme-reader-overlay');
        if (existingOverlay) {
            console.log('[Reader] overlay 已存在');
            // 如果管理器实例不存在或状态丢失，重建 overlay
            if (!readerModeManager || !readerModeManager.originalContent) {
                console.log('[Reader] 管理器状态丢失，重建 overlay');
                existingOverlay.remove();
                readerModeManager = null;
                // 继续走下面的正常激活流程
            } else {
                console.log('[Reader] 恢复显示');
                existingOverlay.style.display = 'block';
                existingOverlay.style.opacity = '1';
                existingOverlay.style.visibility = 'visible';
                // 重新绑定事件
                readerModeManager.bindEvents();
                readerModeManager.applyReadingPreferences();
                sendResponse({ success: true, message: '阅读模式已激活' });
                return true;
            }
        }

        // 使用 async IIFE 来处理异步逻辑
        (async () => {
            try {
                if (!readerModeManager) {
                    console.log('[Reader] 创建 ReaderModeManager 实例');
                    readerModeManager = new ReaderModeManager();
                }

                console.log('[Reader] 开始激活阅读模式');
                const result = await readerModeManager.activate();
                console.log('[Reader] 激活结果:', result);
                sendResponse(result);
            } catch (error) {
                console.error('[Reader] 激活失败:', error);
                sendResponse({ success: false, error: error.message });
            }
        })();

        return true; // 保持消息通道开放以发送异步响应
    }

    // 对于其他消息，返回 false 表示不处理
    return false;
});

// 页面加载时初始化（延迟创建，只在首次需要时创建实例）
// 注意：不在这里主动创建实例，而是等 background 消息触发时再创建
// 这样可以避免在不需要阅读模式的页面上创建不必要的实例
console.log('[Reader] read.js 初始化完成，等待激活消息');