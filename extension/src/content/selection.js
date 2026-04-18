// selection.js - 智能选中翻译按钮系统 (模式 3: 鼠标选中翻译)

class SelectionTranslator {
    constructor() {
        this.translationButton = null;
        this.currentSelection = '';
        this.selectionRange = null;
        this.selectionRects = null; // 保存选区的矩形信息
        this.isDragging = false;
        this.isTranslating = false; // 标记是否正在翻译
        this.translationResultVisible = false; // 标记翻译框是否可见
        this.buttonTimeoutId = null; // 按钮显示延迟计时器
        this.resizeHandler = null; // 窗口大小变化处理函数
        this.scrollHandler = null; // 滚动处理函数
        this.keyHandler = null; // 键盘处理函数
        this.clickHandler = null; // 点击处理函数
        this.selectionChangeHandler = null; // 选择变化处理函数
        this.mouseUpHandler = null; // 鼠标抬起处理函数
        this.mouseDownHandler = null; // 鼠标按下处理函数
        this.keyUpHandler = null; // 键盘抬起处理函数

        this.init();
    }

    init() {
        // 加载 Remix Icon 字体（用于按钮图标）
        this.loadRemixIcon();

        // 跟踪鼠标位置
        this.trackMousePosition();

        this.setupSelectionListeners();
        this.createTranslationButton();
        this.setupGlobalEvents();
    }

    // 动态加载 Remix Icon 字体
    loadRemixIcon() {
        // 检查是否已经加载
        if (document.querySelector('link[href*="remixicon"]')) {
            return;
        }

        const remixIconLink = document.createElement('link');
        remixIconLink.rel = 'stylesheet';
        remixIconLink.href = 'https://cdn.jsdelivr.net/npm/remixicon@3.5.0/fonts/remixicon.css';

        // 确保在 head 可用时再添加
        if (document.head) {
            document.head.appendChild(remixIconLink);
        } else {
            // 如果 head 还未加载，等待 DOMContentLoaded
            document.addEventListener('DOMContentLoaded', () => {
                document.head.appendChild(remixIconLink);
            });
        }
    }

    // 跟踪鼠标位置，以便在必要时用作后备位置
    trackMousePosition() {
        document.addEventListener('mousemove', (e) => {
            window.mouseX = e.clientX;
            window.mouseY = e.clientY;
        }, { passive: true });
    }

    // 设置选择监听器
    setupSelectionListeners() {
        // 监听鼠标按下事件
        this.mouseDownHandler = (e) => {
            this.isDragging = true;
        };
        document.addEventListener('mousedown', this.mouseDownHandler);

        // 监听鼠标抬起事件（选中完成）
        this.mouseUpHandler = (e) => {
            this.isDragging = false;
            // 延迟处理选择，让选择事件先完成
            setTimeout(() => this.handleSelection(), 100);
        };
        document.addEventListener('mouseup', this.mouseUpHandler);

        // 监听文本选择变化
        this.selectionChangeHandler = () => {
            if (!this.isDragging) {
                this.handleSelection();
            }
        };
        document.addEventListener('selectionchange', this.selectionChangeHandler);

        // 监听键盘选择（Shift + 方向键）
        this.keyUpHandler = (e) => {
            if (e.shiftKey && (e.key === 'ArrowLeft' || e.key === 'ArrowRight' || e.key === 'ArrowUp' || e.key === 'ArrowDown')) {
                this.handleSelection();
            }
        };
        document.addEventListener('keyup', this.keyUpHandler);
    }

    // 创建翻译按钮（使用 CSS 类名而非内联样式）
    createTranslationButton() {
        // 如果按钮已存在，先移除
        this.removeTranslationButton();

        // 创建按钮元素
        this.translationButton = document.createElement('div');
        this.translationButton.id = 'selection-translator-button';

        // 按钮内部结构：图标 + 文字
        const icon = document.createElement('span');
        icon.className = 'button-icon';
        // 使用 Remix Icon 图标
        icon.innerHTML = '<i class="ri-translate"></i>';

        const text = document.createElement('span');
        text.className = 'button-text';
        text.textContent = '翻译';

        this.translationButton.appendChild(icon);
        this.translationButton.appendChild(text);

        // 添加到页面
        document.body.appendChild(this.translationButton);

        // 绑定点击事件
        this.translationButton.addEventListener('click', (e) => {
            e.stopPropagation();
            this.translateSelection();
        });
    }

    // 处理文本选择
    handleSelection() {
        const selection = window.getSelection();

        // 如果翻译框可见，不处理新的选择（避免干扰）
        if (this.translationResultVisible) {
            return;
        }

        // 检查是否有选中文本
        if (!selection || !selection.toString().trim()) {
            this.hideTranslationButton();
            return;
        }

        // 验证选中文本长度
        const selectedText = selection.toString().trim();
        if (selectedText.length === 0 || selectedText.length > 5000) {
            this.hideTranslationButton();
            return;
        }

        // 保存当前选中文本和范围
        this.currentSelection = selectedText;
        this.selectionRange = selection.rangeCount > 0 ? selection.getRangeAt(0) : null;

        // 保存选区的矩形信息（用于后续定位）
        if (this.selectionRange) {
            // 获取选中文本的所有矩形区域
            const allRects = Array.from(this.selectionRange.getClientRects());

            // 过滤掉太小的矩形（可能来自图片等元素周围）
            this.selectionRects = allRects.filter(rect =>
                rect.height > 2 && rect.width > 2
            );
        } else {
            this.selectionRects = null;
        }

        // 检查是否有有效的文本区域可以定位按钮
        if (this.selectionRects && this.selectionRects.length > 0) {
            // 智能定位按钮（仅在翻译框不可见时显示）
            if (!this.translationResultVisible) {
                this.positionTranslationButton(selection);
            }
        } else {
            // 如果没有有效的位置矩形，尝试使用其他方式定位
            this.fallbackPositionTranslationButton(selection);
        }
    }

    // 当常规定位失败时的后备定位方法
    fallbackPositionTranslationButton(selection) {
        try {
            // 如果有选中范围但没有有效矩形，尝试获取一个大概的位置
            if (this.selectionRange) {
                const range = this.selectionRange;
                const rect = range.getBoundingClientRect();

                // 如果获取到的矩形有效，使用它
                if (rect && rect.height > 0 && rect.width > 0) {
                    this.selectionRects = [rect];
                    if (!this.translationResultVisible) {
                        this.positionTranslationButton(selection);
                    }
                    return;
                }
            }

            // 如果仍然无法定位，隐藏按钮
            this.hideTranslationButton();
        } catch (e) {
            // 在异常情况下也隐藏按钮
            this.hideTranslationButton();
        }
    }

    // 智能定位按钮位置
    positionTranslationButton(selection) {
        if (!this.selectionRange) return;

        const button = this.translationButton;
        if (!button) return;

        // 获取选中文本的所有矩形（可能跨越多行）
        const rects = Array.from(this.selectionRange.getClientRects());

        // 过滤掉无效的矩形（高度或宽度太小的）
        const validRects = rects.filter(rect =>
            rect.height > 2 && rect.width > 2 &&
            rect.top < window.innerHeight && rect.left < window.innerWidth
        );

        if (validRects.length === 0) {
            this.hideTranslationButton();
            return;
        }

        // 计算最佳按钮位置
        const position = this.calculateBestPosition(validRects);

        // 设置按钮位置
        button.style.left = `${position.x}px`;
        button.style.top = `${position.y}px`;

        // 显示按钮
        this.showTranslationButton();
    }

    // 计算最佳按钮位置
    calculateBestPosition(rects) {
        const viewport = {
            width: window.innerWidth,
            height: window.innerHeight
        };

        // 计算按钮大小
        const buttonWidth = 80; // 按钮大致宽度
        const buttonHeight = 36; // 按钮大致高度

        let bestPosition;

        // 过滤掉高度为 0 或非常小的矩形（可能是图片周围的空隙）
        const validRects = rects.filter(rect => rect.height > 2 && rect.width > 2);

        // 如果过滤后还有有效矩形
        if (validRects.length > 0) {
            // 情况 1：选中是单行文本
            if (validRects.length === 1) {
                bestPosition = this.getSingleLinePosition(validRects[0], buttonWidth, buttonHeight, viewport);
            }
            // 情况 2：选中的是段落或多行
            else {
                bestPosition = this.getMultiLinePosition(validRects, buttonWidth, buttonHeight, viewport);
            }
        } else {
            // 如果没有有效的文本矩形，尝试从选择范围获取一个合理的位置
            bestPosition = this.getFallbackPosition(buttonWidth, buttonHeight, viewport);
        }

        // 确保按钮在视口内
        bestPosition = this.constrainToViewport(bestPosition, buttonWidth, buttonHeight, viewport);

        return bestPosition;
    }

    // 单行文本按钮位置
    getSingleLinePosition(rect, buttonWidth, buttonHeight, viewport) {
        const margin = 8;

        // 确保 rect 数据有效
        if (!rect || typeof rect.left !== 'number' || typeof rect.bottom !== 'number') {
            return this.getFallbackPosition(buttonWidth, buttonHeight, viewport);
        }

        // 始终放在选中文本下方居中
        let x = rect.left + (rect.width - buttonWidth) / 2;
        let y = rect.bottom + margin;

        return { x: Math.round(x), y: Math.round(y) };
    }

    // 多行文本按钮位置
    getMultiLinePosition(rects, buttonWidth, buttonHeight, viewport) {
        const margin = 8;

        // 获取最后一个有效的文本矩形
        let lastValidRect = null;
        for (let i = rects.length - 1; i >= 0; i--) {
            if (rects[i].height > 2 && rects[i].width > 2) {
                lastValidRect = rects[i];
                break;
            }
        }

        if (!lastValidRect) {
            return this.getFallbackPosition(buttonWidth, buttonHeight, viewport);
        }

        // 始终放在选中文本最后一行下方居中
        let x = lastValidRect.left + (lastValidRect.width - buttonWidth) / 2;
        let y = lastValidRect.bottom + margin;

        return { x: Math.round(x), y: Math.round(y) };
    }

    // 获取后备位置（当无法确定合适位置时）
    getFallbackPosition(buttonWidth, buttonHeight, viewport) {
        // 使用当前滚动位置作为参考
        const scrollX = window.pageXOffset || document.documentElement.scrollLeft;
        const scrollY = window.pageYOffset || document.documentElement.scrollTop;

        // 获取当前鼠标位置（如果可以的话）或使用页面中心
        const mouseX = window.mouseX || (viewport.width / 2);
        const mouseY = window.mouseY || (viewport.height / 2);

        // 计算相对于文档的位置
        let x = scrollX + mouseX - (buttonWidth / 2);
        let y = scrollY + mouseY - (buttonHeight / 2) + 40; // 稍微偏下

        // 确保在视口内
        if (x < 10) x = 10;
        if (x + buttonWidth > scrollX + viewport.width - 10) {
            x = scrollX + viewport.width - buttonWidth - 10;
        }

        if (y < 10) y = 10;
        if (y + buttonHeight > scrollY + viewport.height - 10) {
            y = scrollY + viewport.height - buttonHeight - 10;
        }

        return { x: Math.round(x), y: Math.round(y) };
    }

    // 获取选中文本的边界
    getSelectionBounds(rects) {
        if (rects.length === 0) return null;

        let minX = Infinity, maxX = -Infinity;
        let minY = Infinity, maxY = -Infinity;

        rects.forEach(rect => {
            minX = Math.min(minX, rect.left);
            maxX = Math.max(maxX, rect.right);
            minY = Math.min(minY, rect.top);
            maxY = Math.max(maxY, rect.bottom);
        });

        return {
            left: minX,
            top: minY,
            right: maxX,
            bottom: maxY,
            width: maxX - minX,
            height: maxY - minY
        };
    }

    // 检查矩形是否在视口中
    isRectVisible(rect, viewport) {
        const margin = 50; // 边距

        return (
            rect.top >= margin &&
            rect.bottom <= viewport.height - margin &&
            rect.left >= margin &&
            rect.right <= viewport.width - margin
        );
    }

    // 寻找最可见的矩形
    findMostVisibleRect(rects, viewport) {
        let bestRect = null;
        let bestScore = -Infinity;

        rects.forEach(rect => {
            const score = this.calculateVisibilityScore(rect, viewport);
            if (score > bestScore) {
                bestScore = score;
                bestRect = rect;
            }
        });

        return bestRect;
    }

    // 计算可见性分数
    calculateVisibilityScore(rect, viewport) {
        let score = 0;

        // 完全在视口中的分数最高
        if (rect.top >= 0 && rect.bottom <= viewport.height &&
            rect.left >= 0 && rect.right <= viewport.width) {
            score += 100;
        }

        // 部分可见的根据可见面积计算分数
        const visibleTop = Math.max(rect.top, 0);
        const visibleBottom = Math.min(rect.bottom, viewport.height);
        const visibleLeft = Math.max(rect.left, 0);
        const visibleRight = Math.min(rect.right, viewport.width);

        if (visibleBottom > visibleTop && visibleRight > visibleLeft) {
            const visibleArea = (visibleBottom - visibleTop) * (visibleRight - visibleLeft);
            const totalArea = rect.width * rect.height;
            score += (visibleArea / totalArea) * 50;
        }

        // 靠近视口中心的分数更高
        const centerX = rect.left + rect.width / 2;
        const centerY = rect.top + rect.height / 2;
        const distanceToCenter = Math.sqrt(
            Math.pow(centerX - viewport.width / 2, 2) +
            Math.pow(centerY - viewport.height / 2, 2)
        );
        const maxDistance = Math.sqrt(Math.pow(viewport.width, 2) + Math.pow(viewport.height, 2));

        score += 50 * (1 - distanceToCenter / maxDistance);

        return score;
    }

    // 约束位置到视口内
    constrainToViewport(position, buttonWidth, buttonHeight, viewport) {
        let { x, y } = position;

        // 水平约束
        if (x < 10) x = 10;
        if (x + buttonWidth > viewport.width - 10) {
            x = viewport.width - buttonWidth - 10;
        }

        // 垂直约束
        if (y < 10) y = 10;
        if (y + buttonHeight > viewport.height - 10) {
            y = viewport.height - buttonHeight - 10;
        }

        return { x: Math.round(x), y: Math.round(y) };
    }

    // 显示翻译按钮
    showTranslationButton() {
        const button = this.translationButton;
        if (!button) return;

        // 清除之前的延时器
        if (this.buttonTimeoutId) {
            clearTimeout(this.buttonTimeoutId);
        }

        // 确保按钮有正确的样式
        button.style.pointerEvents = 'auto';

        // 使用 requestAnimationFrame 确保样式已应用
        requestAnimationFrame(() => {
            requestAnimationFrame(() => {
                button.classList.add('visible');
            });
        });

        // 添加鼠标悬停效果
        button.onmouseenter = () => {
            button.style.transform = 'translateY(0) scale(1.05)';
            button.style.boxShadow = '0 6px 20px rgba(0, 0, 0, 0.2)';
        };

        button.onmouseleave = () => {
            button.style.transform = 'translateY(0) scale(1)';
            button.style.boxShadow = '0 4px 12px rgba(0, 0, 0, 0.15)';
        };
    }

    // 隐藏翻译按钮
    hideTranslationButton(keepSelectionInfo = false) {
        const button = this.translationButton;
        if (!button) return;

        // 清除之前的延时器
        if (this.buttonTimeoutId) {
            clearTimeout(this.buttonTimeoutId);
        }

        button.classList.remove('visible');
        button.style.pointerEvents = 'none';

        // 如果不需要保留选区信息，则清除选中文本
        if (!keepSelectionInfo) {
            this.currentSelection = '';
            this.selectionRange = null;
            this.selectionRects = null;
        }
    }

    // 移除翻译按钮
    removeTranslationButton() {
        if (this.translationButton && this.translationButton.parentNode) {
            this.translationButton.parentNode.removeChild(this.translationButton);
        }
        this.translationButton = null;
    }

    // 翻译选中文本（简化响应流程）
    async translateSelection() {
        const currentSelection = this.currentSelection;
        // 验证选中文本
        if (!currentSelection || currentSelection.trim().length === 0) {
            console.warn('⚠️ 没有选中文本');
            return;
        }

        // 设置翻译状态标记
        this.isTranslating = true;
        this.translationResultVisible = true;

        // 点击翻译按钮后立即隐藏按钮（保留选区信息）
        this.hideTranslationButton(true);

        // 立即显示加载状态弹窗，位置与最终翻译框完全一致
        this.showTranslationResult({ loading: true });

        // 获取用户设置
        let targetLang = 'zh';
        let engine = GlobalConfig.DEFAULT_SETTINGS.engine;
        let bilingual = false;

        try {
            const settings = await this.getUserSettings();
            targetLang = settings.targetLang || settings.target_lang || 'zh';
            engine = settings.engine || GlobalConfig.DEFAULT_SETTINGS.engine;
            bilingual = settings.bilingual || false;
        } catch (error) {
            console.warn('获取设置失败，使用默认值:', error);
        }

        // 发送翻译请求 (模式 3 - 严格按照接口文档格式)
        try {
            console.log('📤 [模式 3-选中翻译] 开始发送翻译请求:', {
                context: currentSelection.substring(0, 50) + '...',
                targetLang,
                engine,
                bilingual
            });

            const startTime = Date.now();
            const response = await browser.runtime.sendMessage({
                action: 'selectionTranslate',
                sourceLang: 'auto',
                targetLang: targetLang,
                engine: engine,
                context: currentSelection  // API 必需字段
            });
            const endTime = Date.now();
            const duration = endTime - startTime;

            console.log('📥 [模式 3-选中翻译] 收到翻译响应:', {
                response,
                duration: `${duration}ms`,
                success: response?.success
            });

            // 直接使用 sendResponse 返回的结果（移除冗余的消息处理）
            if (response && response.success) {
                const resultData = response.data || response;

                if (!resultData.translation) {
                    throw new Error('翻译结果为空');
                }

                const displayData = {
                    original: currentSelection,
                    translation: resultData.translation,
                    bilingual: resultData.bilingual || null
                };

                // 翻译完成后显示结果，保持相同位置
                this.showTranslationResult(displayData);
            } else {
                const errorMsg = response?.error || response?.message || '翻译失败';
                console.error('❌ 翻译请求失败:', errorMsg);

                // 翻译失败时重置状态并显示错误
                this.resetTranslationState();
                this.showError(errorMsg);
            }
        } catch (error) {
            console.error('❌ 翻译请求异常:', error.message);

            // 翻译异常时重置状态并显示错误
            this.resetTranslationState();

            if (error.message && error.message.includes('Could not establish connection')) {
                this.showError('翻译服务暂时不可用，请刷新页面');
            } else {
                this.showError('翻译失败：' + error.message);
            }
        }
    }

    // 获取用户设置 (MV3 使用 Promise API)
    async getUserSettings() {
        try {
            const result = await browser.storage.local.get(['settings']);
            return result.settings || {};
        } catch (error) {
            console.error('获取设置失败:', error);
            return {};
        }
    }

    // 显示翻译结果（使用 CSS 类名，添加关闭按钮）
    showTranslationResult(data) {
        // 移除已有的结果浮层（如果有）
        const existingResult = document.getElementById('selection-translation-result');
        if (existingResult) {
            existingResult.remove();
        }

        // 计算结果显示位置（加载态和完成态使用相同定位逻辑）
        const position = this.calculateResultPosition();

        // 创建结果浮层
        const resultOverlay = document.createElement('div');
        resultOverlay.id = 'selection-translation-result';

        // 加载状态处理
        const isLoading = data.loading === true;
        const displayText = data.bilingual || data.translation;

        // 构建 HTML 内容（使用 CSS 类名）
        let contentHtml = '';

        if (isLoading) {
            // 加载状态 HTML
            contentHtml = `
            <div class="selection-result-header">
                <span class="header-title">翻译中...</span>
            </div>
            <div class="selection-loading">
                <div class="selection-spinner"></div>
                <span>正在翻译，请稍候...</span>
            </div>
            `;
        } else {
            // 翻译完成 HTML（带关闭按钮）
            // 构建双语对照内容
            let bilingualContent = '';
            if (data.bilingual) {
                // 如果有双语对照数据，使用上下布局（译文在上，原文在下）
                bilingualContent = this.buildBilingualContent(data.original, data.bilingual);
            } else if (data.translation) {
                // 没有双语数据时，使用简单的上下布局显示译文和原文
                bilingualContent = `
                    <div class="selection-result-bilingual">
                        <div class="selection-bilingual-row">
                            <div class="selection-bilingual-translation">${this.escapeHtml(data.translation)}</div>
                            <div class="selection-bilingual-original">${this.escapeHtml(data.original)}</div>
                        </div>
                    </div>
                `;
            } else {
                // 只有译文
                bilingualContent = `<div class="selection-result-content">${this.escapeHtml(displayText)}</div>`;
            }

            contentHtml = `
            <div class="selection-result-header">
                <span class="header-title">翻译结果</span>
                <button class="header-close" title="关闭">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <line x1="18" y1="6" x2="6" y2="18"></line>
                        <line x1="6" y1="6" x2="18" y2="18"></line>
                    </svg>
                </button>
            </div>
            ${bilingualContent}
            <div class="selection-result-footer">
                <span>${data.bilingual ? '双语对照' : '译文'}</span>
                <span>字数：${data.original ? data.original.length : 0}</span>
            </div>
            `;
        }

        resultOverlay.innerHTML = contentHtml;

        // 设置位置（使用内联样式，因为位置是动态计算的）
        resultOverlay.style.left = `${position.x}px`;
        resultOverlay.style.top = `${position.y}px`;

        document.body.appendChild(resultOverlay);

        // 添加关闭按钮事件（翻译完成状态）
        if (!isLoading) {
            this.isTranslating = false; // 翻译完成

            const closeBtn = resultOverlay.querySelector('.header-close');
            if (closeBtn) {
                closeBtn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    this.hideTranslationResult(resultOverlay);
                });
            }

            // 点击外部关闭
            setTimeout(() => {
                const closeOnClickOutside = (e) => {
                    if (!resultOverlay.contains(e.target) &&
                        e.target !== this.translationButton) {
                        this.hideTranslationResult(resultOverlay);
                        document.removeEventListener('click', closeOnClickOutside);
                    }
                };
                document.addEventListener('click', closeOnClickOutside);
            }, 100);
        } else {
            // 加载状态时标记为可见
            this.translationResultVisible = true;
        }
    }

    // 隐藏翻译结果弹窗
    hideTranslationResult(resultOverlay) {
        resultOverlay.classList.add('hiding');
        setTimeout(() => {
            if (resultOverlay.parentNode) {
                resultOverlay.remove();
            }
            // 关闭翻译框时重置状态
            this.resetTranslationState();
        }, 300);
    }

    // 计算结果显示位置
    calculateResultPosition() {
        const viewport = {
            width: window.innerWidth,
            height: window.innerHeight
        };

        let x, y;

        // 使用保存的选区矩形信息
        let rects = this.selectionRects;

        // 如果保存的矩形信息不存在，尝试从当前选区获取
        if (!rects || rects.length === 0) {
            if (this.selectionRange) {
                const allRects = Array.from(this.selectionRange.getClientRects());
                // 过滤掉太小的矩形（可能来自图片等元素周围）
                rects = allRects.filter(rect => rect.height > 2 && rect.width > 2);
            }
        }

        // 如果有选中范围，尽量靠近选中文本
        if (rects && rects.length > 0) {
            // 获取最后一个有效的矩形
            const lastValidRect = rects[rects.length - 1];

            x = lastValidRect.left + (lastValidRect.width / 2) - 200; // 水平居中
            y = lastValidRect.bottom + 20; // 选中内容下方

            // 如果右侧空间不足，放在左侧
            if (x + 400 > viewport.width - 20) {
                x = lastValidRect.left - 420;
            }

            // 确保位置在视口内
            if (x < 20) x = 20;
            if (x + 400 > viewport.width - 20) {
                x = viewport.width - 420;
            }

            if (y < 20) y = 20;
            if (y + 300 > viewport.height - 20) {
                y = viewport.height - 320;
            }

            return { x, y };
        }

        // 默认放在屏幕右上角
        return {
            x: viewport.width - 420,
            y: 80
        };
    }

    // 显示错误消息（使用 CSS 类名）
    showError(message) {
        // 显示错误时重置翻译状态
        this.resetTranslationState();

        const errorOverlay = document.createElement('div');
        errorOverlay.className = 'selection-error-toast';

        errorOverlay.innerHTML = `
            <div class="error-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10"></circle>
                    <line x1="12" y1="8" x2="12" y2="12"></line>
                    <line x1="12" y1="16" x2="12.01" y2="16"></line>
                </svg>
            </div>
            <span class="error-message">${this.escapeHtml(message)}</span>
        `;

        document.body.appendChild(errorOverlay);

        // 3 秒后自动关闭
        setTimeout(() => {
            errorOverlay.classList.add('hiding');
            setTimeout(() => {
                if (errorOverlay.parentNode) {
                    errorOverlay.remove();
                }
            }, 300);
        }, 3000);
    }

    // 设置全局事件监听
    setupGlobalEvents() {
        // 窗口滚动时隐藏按钮和翻译框
        this.scrollHandler = () => {
            this.hideTranslationButton();
            // 如果翻译框可见，也隐藏它并重置状态
            if (this.translationResultVisible) {
                const result = document.getElementById('selection-translation-result');
                if (result) {
                    this.hideTranslationResult(result);
                }
            }
        };
        window.addEventListener('scroll', this.scrollHandler, { passive: true });

        // 窗口大小变化时重新定位按钮
        this.resizeHandler = () => {
            if (this.translationButton &&
                this.translationButton.style.opacity === '1') {
                setTimeout(() => this.handleSelection(), 100);
            }
        };
        window.addEventListener('resize', this.resizeHandler);

        // ESC 键清除选择和关闭翻译框
        this.keyHandler = (e) => {
            if (e.key === 'Escape') {
                this.hideTranslationButton();
                // 如果翻译框可见，关闭它
                if (this.translationResultVisible) {
                    const result = document.getElementById('selection-translation-result');
                    if (result) {
                        this.hideTranslationResult(result);
                    }
                }
                // 清除文本选择
                window.getSelection().removeAllRanges();
            }
        };
        document.addEventListener('keydown', this.keyHandler);

        // 点击其他地方时隐藏按钮（但翻译框可见时不隐藏）
        this.clickHandler = (e) => {
            const button = this.translationButton;
            const result = document.getElementById('selection-translation-result');

            // 只有在翻译框不可见时才处理按钮隐藏
            if (button && button.style.opacity === '1' &&
                !button.contains(e.target) &&
                (!this.translationResultVisible || !result || !result.contains(e.target))) {
                this.hideTranslationButton();
            }
        };
        document.addEventListener('click', this.clickHandler);
    }

    // HTML 转义
    escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // 构建双语对照内容（支持逐行/逐段对照）
    buildBilingualContent(original, bilingual) {
        // 如果 bilingual 是字符串，尝试解析为段落数组
        let paragraphs = [];

        if (typeof bilingual === 'string') {
            // 尝试按段落分割
            const originalParagraphs = original.split(/\n+/).filter(p => p.trim());
            const bilingualParagraphs = bilingual.split(/\n+/).filter(p => p.trim());

            // 如果段落数相同，逐段对照
            if (originalParagraphs.length === bilingualParagraphs.length) {
                paragraphs = originalParagraphs.map((orig, i) => ({
                    translation: bilingualParagraphs[i],
                    original: orig
                }));
            } else {
                // 段落数不同，整体显示
                return `
                    <div class="selection-result-content">${this.escapeHtml(bilingual)}</div>
                    <div class="selection-result-original">
                        <strong>原文：</strong>${this.escapeHtml(original)}
                    </div>
                `;
            }
        } else if (Array.isArray(bilingual)) {
            // 如果 bilingual 已经是数组格式 [{translation, original}, ...]
            paragraphs = bilingual;
        } else {
            // 未知格式，使用默认显示
            return `
                <div class="selection-result-content">${this.escapeHtml(bilingual || '')}</div>
            `;
        }

        // 构建 HTML
        const rows = paragraphs.map(p => `
            <div class="selection-bilingual-row">
                <div class="selection-bilingual-translation">${this.escapeHtml(p.translation)}</div>
                <div class="selection-bilingual-original">${this.escapeHtml(p.original)}</div>
            </div>
        `).join('');

        return `<div class="selection-result-bilingual">${rows}</div>`;
    }

    // 重置翻译状态
    resetTranslationState() {
        this.isTranslating = false;
        this.translationResultVisible = false;
        // 清除选区信息，确保下次重新选择时能正常显示按钮
        this.currentSelection = '';
        this.selectionRange = null;
        this.selectionRects = null;
    }

    // 销毁方法
    destroy() {
        // 移除翻译按钮
        this.removeTranslationButton();

        // 移除结果浮层
        const result = document.getElementById('selection-translation-result');
        if (result) result.remove();

        // 重置状态
        this.resetTranslationState();

        // 移除事件监听器
        if (this.mouseDownHandler) {
            document.removeEventListener('mousedown', this.mouseDownHandler);
        }
        if (this.mouseUpHandler) {
            document.removeEventListener('mouseup', this.mouseUpHandler);
        }
        if (this.selectionChangeHandler) {
            document.removeEventListener('selectionchange', this.selectionChangeHandler);
        }
        if (this.keyUpHandler) {
            document.removeEventListener('keyup', this.keyUpHandler);
        }
        if (this.scrollHandler) {
            window.removeEventListener('scroll', this.scrollHandler);
        }
        if (this.resizeHandler) {
            window.removeEventListener('resize', this.resizeHandler);
        }
        if (this.keyHandler) {
            document.removeEventListener('keydown', this.keyHandler);
        }
        if (this.clickHandler) {
            document.removeEventListener('click', this.clickHandler);
        }

        // 清除定时器
        if (this.buttonTimeoutId) {
            clearTimeout(this.buttonTimeoutId);
        }
    }
}

// 初始化系统
let selectionTranslator = null;

function initSelectionTranslator() {
    // 防止重复初始化
    if (selectionTranslator) {
        selectionTranslator.destroy();
    }

    selectionTranslator = new SelectionTranslator();
    // 注意：在 MV3 中，content script 会在页面导航时自动重新注入
    // 无需手动监听 DOM 变化重建，避免频繁销毁/重建导致的性能问题
}

// 消息监听器
browser.runtime.onMessage.addListener((request, sender, sendResponse) => {
    switch (request.action) {
        // 检测 selection.js 是否已加载
        case 'pingSelection':
            sendResponse({ active: true, mode: 'SELECTION' });
            break;

        // 启用选中翻译
        case 'enableSelectionTranslator':
            if (!selectionTranslator) {
                initSelectionTranslator();
                sendResponse({ success: true });
            } else {
                sendResponse({ success: true, alreadyEnabled: true });
            }
            break;

        // 禁用选中翻译
        case 'disableSelectionTranslator':
            if (selectionTranslator) {
                selectionTranslator.destroy();
                selectionTranslator = null;
                sendResponse({ success: true });
            } else {
                sendResponse({ success: true, alreadyDisabled: true });
            }
            break;

        // 获取当前选中文本
        case 'getSelectedText':
            const selection = window.getSelection();
            sendResponse({
                text: selection ? selection.toString().trim() : '',
                hasSelection: selection ? selection.toString().trim().length > 0 : false
            });
            break;

        // 获取选中翻译状态
        case 'getSelectionTranslatorStatus':
            sendResponse({
                enabled: !!selectionTranslator,
                version: '1.0'
            });
            break;

        // 处理选中翻译请求
        case 'translateTextFromMenu':
            if (selectionTranslator && selectionTranslator.currentSelection) {
                selectionTranslator.translateSelection()
                    .then(() => {
                        sendResponse({ success: true });
                    })
                    .catch(error => {
                        sendResponse({ success: false, error: error.message });
                    });
                return true;
            } else {
                // 如果没有当前选中文本，尝试获取当前选中文本
                const currentSelection = window.getSelection()?.toString().trim();
                if (currentSelection) {
                    if (!selectionTranslator) {
                        selectionTranslator = new SelectionTranslator();
                    }
                    selectionTranslator.currentSelection = currentSelection;
                    selectionTranslator.translateSelection()
                        .then(() => {
                            sendResponse({ success: true });
                        })
                        .catch(error => {
                            sendResponse({ success: false, error: error.message });
                        });
                    return true;
                } else {
                    sendResponse({ success: false, error: '没有选中文本' });
                }
            }
            break;

        // 处理选中翻译错误（保留用于 background 主动发送的错误通知）
        case 'selectionTranslationError':
            if (selectionTranslator) {
                const errorMsg = request.error || '翻译失败';
                selectionTranslator.showError(errorMsg);
            }
            break;

        // 不处理未知消息，让 background.js 处理
        default:
            // 不返回响应，让消息继续传递
            break;
    }
});

// 页面加载完成后初始化
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        setTimeout(initSelectionTranslator, 200);
    });
} else {
    initSelectionTranslator();
}
