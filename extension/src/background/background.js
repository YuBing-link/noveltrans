// background.js - Extreme Translator 核心后台脚本
importScripts('../lib/browser-polyfill.js');
importScripts('../lib/vendor/idb-simple.js');
importScripts('../lib/config.js');
importScripts('../lib/vendor/p-limit.js');

// 生产环境日志：仅在 DEBUG 模式下输出调试信息
const DEBUG = false;
const logger = {
	log: (...args) => { if (DEBUG) logger.log(...args); },
	warn: (...args) => { if (DEBUG) logger.warn(...args); },
	error: (...args) => console.error(...args),
};

// 翻译服务管理器 - 统一调用后端API
class TranslationServiceManager {
    constructor() {
        // 内存缓存
        this.cache = new Map();

        // 并发控制：限制同时进行的请求数量
        this.limit = pLimit(10); // 最多3个并发请求

        // 请求去重：存储正在进行中的请求
        this.pendingRequests = new Map();

        // IndexedDB 持久化缓存
        this.idbCache = createTranslationCacheDB();

        // 缓存配置
        this.CACHE_CONFIG = {
            MAX_MEMORY_CACHE_SIZE: 1000,      // 内存缓存最大条数
            MEMORY_CLEANUP_THRESHOLD: 500,     // 清理阈值
            MAX_IDB_CACHE_SIZE: 10000,         // IDB缓存最大条数
            IDB_EXPIRATION_HOURS: 24,          // IDB缓存过期时间（小时）
            IDB_CLEANUP_THRESHOLD: 5000        // IDB清理阈值
        };

        this.init();
    }

    init() {
        this.setupCacheCleanup();
        this.initIDBCache();
    }

    // 初始化 IndexedDB 缓存
    async initIDBCache() {
        try {
            await this.idbCache.open();
            logger.log('✅ IndexedDB 缓存已初始化');
        } catch (error) {
            console.error('❌ IndexedDB 缓存初始化失败:', error);
        }
    }

    // 生成缓存键
    generateCacheKey(text, sourceLang, targetLang, engine, bilingual) {
        return `${engine}:${sourceLang}:${targetLang}:${bilingual}:${this.hashString(text)}`;
    }

    // 生成请求键（用于去重）
    generateRequestKey(text, sourceLang, targetLang, engine, bilingual) {
        return `${this.generateCacheKey(text, sourceLang, targetLang, engine, bilingual)}`;
    }

    // 字符串哈希
    hashString(str) {
        let hash = 0;
        for (let i = 0; i < str.length; i++) {
            hash = ((hash << 5) - hash) + str.charCodeAt(i);
            hash |= 0;
        }
        return hash.toString(36);
    }

    // 从缓存获取（优先级：内存 > IDB）
    async getCachedTranslation(cacheKey) {
        // 1. 先查内存缓存
        if (this.cache.has(cacheKey)) {
            return { source: 'memory', data: this.cache.get(cacheKey) };
        }

        // 2. 再查 IDB 缓存
        try {
            const record = await this.idbCache.get('translations', cacheKey);
            if (record && record.value) {
                // 将 IDB 缓存加载到内存中
                this.cache.set(cacheKey, record.value);
                return { source: 'idb', data: record.value };
            }
        } catch (error) {
            logger.warn('⚠️ 从 IDB 缓存读取失败:', error);
        }

        return { source: 'none', data: null };
    }

    // 保存到缓存（内存 + IDB）
    async saveToCache(cacheKey, translation) {
        // 1. 保存到内存
        this.cache.set(cacheKey, translation);

        // 2. 异步保存到 IDB
        try {
            await this.idbCache.set('translations', cacheKey, translation);
        } catch (error) {
            logger.warn('⚠️ 保存到 IDB 缓存失败:', error);
        }

        // 3. 检查内存缓存大小，如果太大则清理
        if (this.cache.size > this.CACHE_CONFIG.MAX_MEMORY_CACHE_SIZE) {
            this.cleanMemoryCache();
        }
    }

    // 清理内存缓存
    cleanMemoryCache() {
        const maxSize = this.CACHE_CONFIG.MEMORY_CLEANUP_THRESHOLD;
        const keys = Array.from(this.cache.keys());

        // 保留最新的 maxSize 条
        const keysToRemove = keys.slice(0, this.cache.size - maxSize);
        keysToRemove.forEach(key => this.cache.delete(key));

        logger.log(`🧹 内存缓存已清理，保留 ${maxSize} 条，删除 ${keysToRemove.length} 条`);
    }

    // 清理过期的 IDB 缓存
    async cleanExpiredIDBCache() {
        try {
            const maxAge = this.CACHE_CONFIG.IDB_EXPIRATION_HOURS * 60 * 60 * 1000; // 毫秒
            await this.idbCache.deleteExpired('translations', maxAge);
            logger.log(`🧹 已清理过期的 IDB 缓存（${this.CACHE_CONFIG.IDB_EXPIRATION_HOURS} 小时）`);
        } catch (error) {
            console.error('❌ 清理 IDB 缓存失败:', error);
        }
    }

    // 请求去重：检查是否已有相同请求在进行中
    checkPendingRequest(requestKey) {
        return this.pendingRequests.get(requestKey);
    }

    // 添加进行中的请求
    addPendingRequest(requestKey, promise) {
        this.pendingRequests.set(requestKey, promise);
        return promise;
    }

    // 移除进行中的请求
    removePendingRequest(requestKey) {
        this.pendingRequests.delete(requestKey);
    }

    // 并发控制包装
    limitConcurrency(fn) {
        return this.limit(fn);
    }

    // 翻译文本（单条）- 使用后端API
    async translateText(text, sourceLang, targetLang, engine = null, bilingual = false) {
        const cacheKey = this.generateCacheKey(text, sourceLang, targetLang, engine, bilingual);
        const requestKey = this.generateRequestKey(text, sourceLang, targetLang, engine, bilingual);

        // 1. 检查是否有相同的请求正在进行中
        const pendingPromise = this.checkPendingRequest(requestKey);
        if (pendingPromise) {
            logger.log('🔁 使用共享的请求结果:', requestKey);
            return await pendingPromise;
        }

        // 2. 检查缓存（内存 → IDB）
        const cached = await this.getCachedTranslation(cacheKey);
        if (cached.data) {
            logger.log(`📦 命中${cached.source}缓存:`, cacheKey.substring(0, 30));
            return cached.data;
        }

        // 3. 创建翻译请求
        const translateFn = async () => {
            try {
                // 获取API密钥
                const apiKey = await this.getApiKey(engine);

                // 调用后端API
                const result = await GlobalConfig.callBackendAPI('QUICK', {
                    text: text,
                    sourceLang: sourceLang,
                    targetLang: targetLang,
                    bilingual: bilingual
                }, engine, apiKey);

                // 详细记录后端返回的数据
                logger.log('🈴【后端返回数据-单条翻译】', {
                    raw_response: result,
                    success: result.success,
                    data: result.data,
                    engine: engine,
                    original_text: text,
                    translation: result.data?.translation || result.data?.translatedText,
                    bilingual: bilingual,
                    timestamp: result.timestamp
                });
                // 构建返回结果
                const translation = {
                    original: text,
                    translation: result.data.translation || result.data.translatedText || text,
                    bilingual: bilingual ? `${text}\n${result.data.translation || result.data.translatedText || ''}` : null,
                    sourceLang: result.data.sourceLang || sourceLang,
                    targetLang: targetLang,
                    service: engine,
                    timestamp: result.timestamp
                };

                // 4. 存入缓存
                await this.saveToCache(cacheKey, translation);

                return translation;
            } catch (error) {
                console.error(`${engine}翻译失败:`, error);
                throw error;
            } finally {
                // 移除进行中的请求标记
                this.removePendingRequest(requestKey);
            }
        };

        // 5. 添加到进行中的请求，并应用并发控制
        const requestPromise = this.addPendingRequest(
            requestKey,
            this.limitConcurrency(translateFn)
        );

        return await requestPromise;
    }

    // 模式2：阅读器翻译API (read.js - 阅读模式中的文章翻译) - 严格按照接口文档
    async callReaderTranslationAPI(articleContent, targetLang, sourceLang = 'auto', engine = null) {
        try {
            // 参数验证
            if (!articleContent || articleContent.trim().length === 0) {
                throw new Error('文章内容不能为空');
            }

            const apiKey = await this.getApiKey(engine);

            // 严格按照接口文档格式：
// content: ✅ 必须在这个位置
// targetLang: ✅ 必须在这个位置
// sourceLang: ✅ 必须在这个位置
// engine: ✅ 必须在这个位置
            const result = await GlobalConfig.callBackendAPI('READER', {
                content: articleContent,
                targetLang: targetLang,
                sourceLang: sourceLang
            }, engine, apiKey);

            // 详细记录后端返回的数据
            logger.log('📚【后端返回数据-阅读器翻译】', {
                raw_response: result,
                success: result.success,
                data: result.data,
                engine: engine,
                translatedContentLength: result.data?.translatedContent?.length,
                timestamp: result.timestamp
            });
            // 验证返回数据
            if (!result.data || !result.data.translatedContent) {
                throw new Error('翻译服务返回数据格式错误');
            }

            // 模式2标准响应格式: {success, engine, translatedContent}
            return {
                success: result.success,
                translatedContent: result.data.translatedContent,
                engine: engine,
                mode: 'reader',
                timestamp: Date.now()
            };

        } catch (error) {
            console.error('❌ 阅读器翻译API调用失败:', error);
            throw error; // 保持抛出异常，由调用方处理
        }
    }

    // 模式3：选中翻译API (selection.js - 鼠标选中文本翻译) - 严格按照接口文档
    async callSelectionTranslationAPI(text, sourceLang, targetLang, engine = null) {
        try {
            // 参数验证
            if (!text || text.trim().length === 0) {
                throw new Error('翻译文本不能为空');
            }

            const apiKey = await this.getApiKey(engine);

            // 严格按照接口文档格式：
// sourceLang: ✅ 必须在这个位置
// targetLang: ✅ 必须在这个位置
// engine: ✅ 必须在这个位置
// context: ✅ 必须在这个位置（text字段在config.js中映射）
            const result = await GlobalConfig.callBackendAPI('SELECTION', {
                context: text,  // context字段在config.js中会被映射
                sourceLang: sourceLang,
                targetLang: targetLang
            }, engine, apiKey);

            // 详细记录后端返回的数据
            logger.log('🖱️【后端返回数据-选中翻译】', {
                raw_response: result,
                success: result.success,
                data: result.data,
                engine: engine,
                translation: result.data?.translation,
                timestamp: result.timestamp
            });
            // 验证返回数据
            if (!result.data || !result.data.translation) {
                throw new Error('翻译服务返回数据格式错误');
            }

            return {
                success: true,
                translation: result.data.translation,
                bilingual: result.data.bilingual || null,  // 如果后端返回双语对照数据，透传给前端
                engine: engine,
                mode: 'selection',
                timestamp: Date.now()
            };
        } catch (error) {
            console.error('❌ 选中翻译API调用失败:', error);
            throw error; // 保持抛出异常，由调用方处理
        }
    }

    // 批量翻译 - 使用后端API (用于简单快速翻译)
    async batchTranslate(texts, sourceLang, targetLang, engine = null) {
        try {
            // 对每个文本应用并发控制
            const apiKey = await this.getApiKey(engine);

            // 使用并发控制包装API调用
            const result = await this.limitConcurrency(async () => {
                return await GlobalConfig.callBackendAPI('QUICK', {
                    texts: texts,
                    sourceLang: sourceLang,
                    targetLang: targetLang
                }, engine, apiKey);
            });

            // 详细记录后端返回的数据
            logger.log('⚡【后端返回数据-批量翻译】', {
                raw_response: result,
                success: result.success,
                data: result.data,
                engine: engine,
                translations_length: result.data?.translations?.length || result.data?.texts?.length,
                texts_count: texts.length,
                timestamp: result.timestamp
            });

            const translations = result.data.translations || result.data.texts || [];

            return { translations: translations, batchSize: texts.length };
        } catch (error) {
            console.error('批量翻译失败:', error);
            throw error;
        }
    }

    // 整页批量翻译 - 使用 WEBPAGE 端点（带 textRegistry 格式）- 支持 SSE 流式响应
    async batchTranslateWebpage(textRegistry, sourceLang, targetLang, engine = null, fastMode = true, onTranslationChunk = null) {
        const maxRetries = 3;
        let retryCount = 0;
        let delay = 1000;

        // 参数验证
        if (!Array.isArray(textRegistry) || textRegistry.length === 0) {
            throw new Error('textRegistry 必须是非空数组');
        }

        logger.log(`🔄 [流式] 开始批量翻译，文本数量: ${textRegistry.length}, 引擎: ${engine || 'default'}, fastMode: ${fastMode}`);

        // 使用并发控制包装整个翻译过程
        return await this.limitConcurrency(async () => {
            while (retryCount < maxRetries) {
                try {
                    logger.log(`🔄 [流式] 执行第 ${retryCount + 1} 次翻译请求`);

                    const apiKey = await this.getApiKey(engine);

                    // 使用流式 API 调用
                    const startTime = Date.now();
                    const result = await GlobalConfig.callBackendAPIStream(
                        'WEBPAGE',
                        {
                            textRegistry: textRegistry,
                            sourceLang: sourceLang,
                            targetLang: targetLang,
                            engine: engine || GlobalConfig.DEFAULT_SETTINGS.engine,
                            fastMode: fastMode
                        },
                        engine,
                        apiKey,
                        // 实时翻译块回调
                        (chunk) => {
                            if (onTranslationChunk) {
                                onTranslationChunk(chunk);
                            }
                            logger.log(`📤 [流式] 收到翻译块: ${chunk.textId}`);
                        },
                        // 完成回调
                        (completeResult) => {
                            const duration = Date.now() - startTime;
                            logger.log(`✅ [流式] 翻译完成，耗时: ${duration}ms, 总计: ${completeResult.translations?.length || 0} 个`);
                        },
                        // 错误回调
                        (error) => {
                            console.error('❌ [流式] 翻译过程中出错:', error);
                        }
                    );

                    logger.log(`✅ [流式] 后端API调用成功，返回结果长度: ${result.translations?.length || 0}`);

                    // 验证返回数据格式
                    if (!result.translations || !Array.isArray(result.translations)) {
                        throw new Error('后端返回数据格式错误: 缺少 translations 数组');
                    }

                    // 模式1标准响应格式: {success, engine, translations[{textId, original, translation}]}
                    const translations = result.translations.map(t => ({
                        textId: t.textId,
                        original: t.original,
                        translation: t.translation
                    }));

                    logger.log(`✅ [流式] 翻译处理完成，有效翻译数量: ${translations.length}`);

                    return {
                        success: true,
                        translations: translations,
                        batchSize: textRegistry.length
                    };
                } catch (error) {
                    retryCount++;

                    if (retryCount >= maxRetries) {
                        console.error(`❌ [流式] 批量翻译失败 (已重试${maxRetries}次):`, error);

                        // 根据错误类型提供更具体的错误信息
                        let errorMessage = `翻译请求失败: 服务不可用 (${error.message})`;
                        if (error.message.includes('fetch') || error.message.includes('network') ||
                            error.message.includes('timeout') || error.message.includes('connect')) {
                            errorMessage = `翻译服务连接失败: 无法连接到后端服务 (${error.message})。请确认后端服务是否已在 http://127.0.0.1:7341 启动`;
                        }

                        throw new Error(errorMessage);
                    }

                    logger.warn(`⚠️ [流式] 翻译失败，第${retryCount}次重试...`, error.message);
                    await this.delay(delay);
                    delay *= 2; // 指数退避
                }
            }
        });
    }

    // 设置缓存清理
    setupCacheCleanup() {
        // 每5分钟清理一次内存缓存
        setInterval(() => {
            this.cleanMemoryCache();
        }, 5 * 60 * 1000);

        // 每小时清理一次过期的 IDB 缓存
        setInterval(() => {
            this.cleanExpiredIDBCache();
        }, 60 * 60 * 1000);
    }

    // 清理内存缓存
    cleanMemoryCache() {
        const maxSize = this.CACHE_CONFIG.MEMORY_CLEANUP_THRESHOLD;
        const keys = Array.from(this.cache.keys());

        // 保留最新的 maxSize 条
        const keysToRemove = keys.slice(0, this.cache.size - maxSize);
        keysToRemove.forEach(key => this.cache.delete(key));

        logger.log(`🧹 内存缓存已清理，保留 ${maxSize} 条，删除 ${keysToRemove.length} 条`);
    }

    // 清理过期的 IDB 缓存
    async cleanExpiredIDBCache() {
        try {
            const maxAge = this.CACHE_CONFIG.IDB_EXPIRATION_HOURS * 60 * 60 * 1000; // 毫秒
            await this.idbCache.deleteExpired('translations', maxAge);
            logger.log(`🧹 已清理过期的 IDB 缓存（${this.CACHE_CONFIG.IDB_EXPIRATION_HOURS} 小时）`);
        } catch (error) {
            console.error('❌ 清理 IDB 缓存失败:', error);
        }
    }

    // 获取API密钥
    async getApiKey(engine) {
        const result = await browser.storage.local.get(['api_keys']);
        const apiKeys = result.api_keys || {};
        return apiKeys[engine];
    }

    // 获取设置
    async getSetting(key) {
        const result = await browser.storage.local.get(['settings']);
        const settings = result.settings || {};
        return settings[key];
    }

    // 延迟函数
    delay(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }
}

// 扩展状态管理器
class ExtensionStateManager {
    constructor() {
        this.activeTranslations = new Map(); // tabId -> 翻译状态
        this.readerModes = new Map(); // tabId -> 阅读模式状态
        this.init();
    }

    init() {
    }

    // 记录翻译活动
    recordTranslation(tabId, action, data = {}) {
        if (!this.activeTranslations.has(tabId)) {
            this.activeTranslations.set(tabId, {
                startTime: Date.now(),
                translations: []
            });
        }

        const tabState = this.activeTranslations.get(tabId);
        tabState.translations.push({
            action,
            timestamp: Date.now(),
            ...data
        });

        // 保存最近的100条记录
        if (tabState.translations.length > 100) {
            tabState.translations = tabState.translations.slice(-100);
        }
    }

    // 获取翻译状态
    getTranslationStatus(tabId) {
        if (!this.activeTranslations.has(tabId)) {
            return null;
        }

        const state = this.activeTranslations.get(tabId);
        const now = Date.now();
        const duration = now - state.startTime;

        return {
            active: true,
            duration: Math.floor(duration / 1000),
            translationCount: state.translations.length,
            recentActions: state.translations.slice(-10)
        };
    }

    // 记录阅读模式状态
    setReaderMode(tabId, active, articleData = null) {
        if (active) {
            this.readerModes.set(tabId, {
                active: true,
                activatedAt: Date.now(),
                articleData
            });
        } else {
            this.readerModes.delete(tabId);
        }
    }

    // 获取阅读模式状态
    getReaderMode(tabId) {
        return this.readerModes.get(tabId) || { active: false };
    }

    // 清理标签页状态
    cleanupTab(tabId) {
        this.activeTranslations.delete(tabId);
        this.readerModes.delete(tabId);
    }
}

// 映射表管理器
class MappingTableManager {
    constructor() {
        this.mappingTables = new Map(); // tabId -> 映射表
        this.init();
    }

    init() {
    }

    // 处理上传的映射表
    async processMappingTable(mappingTable, tabId) {
        // 检查 tabId 是否有效
        if (!tabId) {
            console.error('无效的 tabId:', tabId);
            return {
                success: false,
                error: '无效的标签页 ID，请从网页内容脚本调用'
            };
        }

        try {
            // 存储映射表
            this.mappingTables.set(tabId, {
                ...mappingTable,
                tabId,
                uploadedAt: Date.now(),
                status: 'pending',
                translationPlan: null
            });

            // 制定翻译计划
            const translationPlan = await this.createTranslationPlan(mappingTable);

            // 更新映射表状态
            const tabMapping = this.mappingTables.get(tabId);
            tabMapping.translationPlan = translationPlan;
            tabMapping.status = 'processing';

            return {
                success: true,
                translationPlan,
                message: `翻译计划已创建，共${mappingTable.totalTexts}个文本`
            };
        } catch (error) {
            console.error('处理映射表失败:', error);
            return {
                success: false,
                error: error?.message || String(error) || '未知错误'
            };
        }
    }

    // 创建翻译计划
    async createTranslationPlan(mappingTable) {
        // 获取用户设置
        const settings = await this.getUserSettings();

        // 智能选择翻译策略
        const textIds = mappingTable.textRegistry.map(item => item.id);

        // 计算优先级：可见文本优先
        const visibleTexts = mappingTable.textRegistry.filter(text =>
            text.position && text.position.visible === true
        ).slice(0, 50); // 限制可见文本数量

        const priorityTexts = visibleTexts.map(t => t.id);
        const remainingTexts = textIds.filter(id => !priorityTexts.includes(id));

        return {
            priorityTexts,
            remainingTexts,
            allTexts: textIds,
            targetLang: mappingTable.targetLang || settings.target_lang || 'zh',
            sourceLang: mappingTable.sourceLang || mappingTable.language || 'auto',
            service: settings.engine || GlobalConfig.DEFAULT_SETTINGS.engine,
            strategy: 'priority-first',
            estimatedTime: Math.ceil(textIds.length / 100) + 5, // 预估秒数
            created: Date.now()
        };
    }

    // 获取用户设置
    async getUserSettings() {
        const result = await browser.storage.local.get(['settings']);
        return result.settings || {
            target_lang: 'zh',
            engine: 'google',
            bilingual_display: false,
            auto_translate: false
        };
    }

    // 获取映射表
    getMappingTable(tabId) {
        return this.mappingTables.get(tabId);
    }

    // 清理映射表
    cleanupMappingTable(tabId) {
        this.mappingTables.delete(tabId);
    }
}

// 右键菜单管理器
class ContextMenuManager {
    constructor() {
        this.init();
    }

    init() {
        // 创建右键菜单项（Promise-style: 不等待，让它在后台执行）
        this.createContextMenu().catch(error => {
            console.error('创建右键菜单失败:', error);
        });

        // 监听右键菜单点击
        browser.contextMenus.onClicked.addListener((info, tab) => {
            this.handleContextMenuClick(info, tab);
        });
    }

    // 创建右键菜单
    async createContextMenu() {
        // Promise-style: 使用 await 替代回调
        await browser.contextMenus.removeAll();

        // 主翻译菜单
        await browser.contextMenus.create({
            id: 'translate-selection',
            title: '翻译选中文本',
            contexts: ['selection']
        });

        // 分隔符
        await browser.contextMenus.create({
            id: 'separator-1',
            type: 'separator',
            contexts: ['selection']
        });

        // 翻译到不同语言
        await browser.contextMenus.create({
            id: 'translate-zh',
            title: '翻译成中文',
            contexts: ['selection']
        });

        await browser.contextMenus.create({
            id: 'translate-en',
            title: '翻译成英文',
            contexts: ['selection']
        });

        await browser.contextMenus.create({
            id: 'translate-ja',
            title: '翻译成日文',
            contexts: ['selection']
        });

        await browser.contextMenus.create({
            id: 'translate-ko',
            title: '翻译成韩文',
            contexts: ['selection']
        });

        // 分隔符
        await browser.contextMenus.create({
            id: 'separator-2',
            type: 'separator',
            contexts: ['selection']
        });

        // 双语翻译
        await browser.contextMenus.create({
            id: 'translate-bilingual',
            title: '双语翻译',
            contexts: ['selection']
        });

        // 整个页面翻译
        await browser.contextMenus.create({
            id: 'translate-page',
            title: '翻译整个页面',
            contexts: ['page']
        });

        // 阅读模式
        await browser.contextMenus.create({
            id: 'reader-mode',
            title: '阅读模式',
            contexts: ['page']
        });
    }

    // 处理右键菜单点击
    async handleContextMenuClick(info, tab) {
        try {
            const { menuItemId, selectionText } = info;

            switch (menuItemId) {
                case 'translate-selection':
                case 'translate-bilingual':
                    const bilingual = menuItemId === 'translate-bilingual';
                    await this.handleTranslation(tab.id, selectionText, bilingual);
                    break;

                case 'translate-zh':
                    await this.handleTranslation(tab.id, selectionText, false, 'zh');
                    break;

                case 'translate-en':
                    await this.handleTranslation(tab.id, selectionText, false, 'en');
                    break;

                case 'translate-ja':
                    await this.handleTranslation(tab.id, selectionText, false, 'ja');
                    break;

                case 'translate-ko':
                    await this.handleTranslation(tab.id, selectionText, false, 'ko');
                    break;

                case 'translate-page':
                    await this.handlePageTranslation(tab.id);
                    break;

                case 'reader-mode':
                    await this.toggleReaderMode(tab.id);
                    break;
            }
        } catch (error) {
            console.error('右键菜单处理失败:', error);
        }
    }

    // 处理翻译请求
    async handleTranslation(tabId, text, bilingual = false, targetLang = null) {
        try {
            const settings = await this.getUserSettings();

            browser.tabs.sendMessage(tabId, {
                action: 'translateTextFromMenu',
                text: text,
                sourceLang: 'auto',
                targetLang: targetLang || settings.target_lang || 'zh',
                engine: settings.engine || GlobalConfig.DEFAULT_SETTINGS.engine,
                bilingual: bilingual
            });
        } catch (error) {
            console.error('发送翻译请求失败:', error);
            // 重新注入内容脚本
            await this.retryInjection(tabId);
        }
    }

    // 处理整个页面翻译
    async handlePageTranslation(tabId) {
        try {
            browser.tabs.sendMessage(tabId, {
                action: 'translateWebPage'
            });
        } catch (error) {
            console.error('发送页面翻译请求失败:', error);
            await this.retryInjection(tabId);
        }
    }

    // 切换阅读模式
    async toggleReaderMode(tabId) {
        try {
            browser.tabs.sendMessage(tabId, {
                action: 'toggleReaderMode'
            });
        } catch (error) {
            console.error('发送阅读模式请求失败:', error);
            await this.retryInjection(tabId);
        }
    }

    // 重试注入脚本（MV3 兼容版本）
    async retryInjection(tabId) {
        try {
            // 尝试重新注入内容脚本 - 使用 browser.scripting API (MV3标准)
            await browser.scripting.executeScript({
                target: { tabId: tabId },
                files: ['src/lib/browser-polyfill.js', 'src/lib/purify.js', 'src/lib/Readability.js', 'src/lib/config.js', 'src/content/read.js', 'src/content/selection.js', 'src/content/content.js'],
                injectImmediately: true
            });

        } catch (error) {
            console.error('重新注入脚本失败:', error);
        }
    }

    // 获取用户设置
    async getUserSettings() {
        const result = await browser.storage.local.get(['settings']);
        return result.settings || {};
    }
}

// 快捷键管理器
class ShortcutManager {
    constructor() {
        this.init();
    }

    init() {
        // 监听快捷键
        browser.commands.onCommand.addListener((command) => {
            this.handleCommand(command);
        });
    }

    // 处理快捷键命令
    async handleCommand(command) {
        try {
            const [tab] = await browser.tabs.query({ active: true, currentWindow: true });
            if (!tab) return;

            // 匹配 manifest.json 中定义的命令名
            switch (command) {
                case 'toggle-feature':
                    // 默认触发选中翻译
                    await this.handleTranslateSelection(tab.id);
                    break;

                // 以下命令保留，可在 manifest.json 中扩展使用
                case 'translate_selection':
                    await this.handleTranslateSelection(tab.id);
                    break;

                case 'translate_page':
                    await this.handleTranslatePage(tab.id);
                    break;

                case 'toggle_reader':
                    await this.toggleReaderMode(tab.id);
                    break;

                case 'clear_translations':
                    await this.clearTranslations(tab.id);
                    break;

                default:
                    logger.warn('未知命令:', command);
            }
        } catch (error) {
            console.error('处理快捷键失败:', error);
        }
    }

    // 处理选中文本翻译
    async handleTranslateSelection(tabId) {
        try {
            // 获取选中文本
            const [result] = await browser.scripting.executeScript({
                target: { tabId },
                func: () => {
                    const selection = window.getSelection();
                    return selection ? selection.toString().trim() : '';
                }
            });

            if (result.result && result.result.length > 0) {
                const settings = await this.getUserSettings();

                browser.tabs.sendMessage(tabId, {
                    action: 'translateTextFromMenu',
                    text: result.result,
                    sourceLang: 'auto',
                    targetLang: settings.target_lang || 'zh',
                    engine: settings.engine || GlobalConfig.DEFAULT_SETTINGS.engine,
                    bilingual: false
                });
            }
        } catch (error) {
            console.error('快捷键翻译失败:', error);
        }
    }

    // 处理页面翻译
    async handleTranslatePage(tabId) {
        try {
            browser.tabs.sendMessage(tabId, {
                action: 'translateWebPage'
            });
        } catch (error) {
            console.error('快捷键页面翻译失败:', error);
        }
    }

    // 切换阅读模式
    async toggleReaderMode(tabId) {
        try {
            browser.tabs.sendMessage(tabId, {
                action: 'toggleReaderMode'
            });
        } catch (error) {
            console.error('快捷键阅读模式失败:', error);
        }
    }

    // 切换双语显示
    async toggleBilingualDisplay(tabId, showBilingual) {
        try {
            browser.tabs.sendMessage(tabId, {
                action: 'toggleBilingualDisplay',
                showBilingual: showBilingual
            });
        } catch (error) {
            console.error('切换双语显示失败:', error);
        }
    }

    // 清理翻译
    async clearTranslations(tabId) {
        try {
            browser.tabs.sendMessage(tabId, {
                action: 'clearTranslations'
            });
        } catch (error) {
            console.error('快捷键清理翻译失败:', error);
        }
    }

    // 获取用户设置
    async getUserSettings() {
        const result = await browser.storage.local.get(['settings']);
        return result.settings || {};
    }
}

// 主后台管理器
class BackgroundManager {
    constructor() {
        this.translationService = new TranslationServiceManager();
        this.stateManager = new ExtensionStateManager();
        this.mappingManager = new MappingTableManager();
        this.contextMenuManager = new ContextMenuManager();
        this.shortcutManager = new ShortcutManager();
        this.init();
    }

    init() {
        // 设置消息监听器
        this.setupMessageListeners();

        // 设置标签页监听器
        this.setupTabListeners();

        // 设置存储监听器
        this.setupStorageListeners();

        // 设置安装事件
        this.setupInstallEvents();
    }

    // 设置消息监听器
    setupMessageListeners() {
        browser.runtime.onMessage.addListener((request, sender, sendResponse) => {
            // 立即返回true，表示会异步响应
            this.handleMessage(request, sender, sendResponse).catch(error => {
                console.error('处理消息失败:', error);
                sendResponse({ success: false, error: error.message });
            });

            return true; // 保持消息通道开放
        });
    }

    // 处理消息
    async handleMessage(request, sender, sendResponse) {
        const tabId = sender.tab?.id;

        // 调试日志：记录所有收到的消息
        logger.log('[BG] 收到消息:', request.action, request);

        try {
            switch (request.action) {
                // 心跳检测
                case 'ping':
                    sendResponse({ active: true, version: '2.0' });
                    break;

                // 单条文本翻译
                case 'translateText':
                    const translation = await this.translationService.translateText(
                        request.context,
                        request.sourceLang || 'auto',
                        request.targetLang || 'zh',
                        request.engine || GlobalConfig.DEFAULT_SETTINGS.engine,
                        request.bilingual || false
                    );

                    this.stateManager.recordTranslation(tabId, 'single_translation', {
                        text: request.context,
                        length: request.context.length,
                        service: request.engine
                    });

                    sendResponse({ success: true, data: translation });
                    break;

                // 模式2：阅读器翻译API调用
                case 'readerTranslate':
                    try {
                        logger.log('🔄 [模式2-阅读器翻译] 开始处理API请求:', {
                            contentLength: request.articleContent.length,
                            targetLang: request.targetLang || 'zh',
                            sourceLang: request.sourceLang || 'auto',
                            engine: request.engine || GlobalConfig.DEFAULT_SETTINGS.engine
                        });

                        const startTime = Date.now();
                        const readerResult = await this.translationService.callReaderTranslationAPI(
                            request.articleContent,
                            request.targetLang || 'zh',
                            request.sourceLang || 'auto',
                            request.engine || GlobalConfig.DEFAULT_SETTINGS.engine
                        );
                        const endTime = Date.now();
                        const duration = endTime - startTime;

                        logger.log('✅ [模式2-阅读器翻译] API请求处理完成:', {
                            success: readerResult?.success,
                            duration: `${duration}ms`,
                            translatedContentLength: readerResult?.translatedContent?.length
                        });

                        this.stateManager.recordTranslation(tabId, 'reader_translate', {
                            contentLength: request.articleContent.length,
                            mode: 'reader'
                        });

                        // 先响应原始请求
                        sendResponse({ success: true, data: readerResult });

                        // 然后主动向标签页发送阅读模式翻译完成通知
                        try {
                            logger.log('📤 [模式2-阅读器翻译] 发送翻译完成通知到标签页:', {
                                action: 'readerTranslationCompleted',
                                contentLength: request.articleContent.length,
                                tabId: tabId
                            });

                            await browser.tabs.sendMessage(tabId, {
                                action: 'readerTranslationCompleted',
                                originalContent: request.articleContent,
                                translatedContent: readerResult.translatedContent,
                                title: request.title,
                                byline: request.byline,
                                sourceLang: request.sourceLang || 'auto',
                                targetLang: request.targetLang || 'zh',
                                engine: request.engine || GlobalConfig.DEFAULT_SETTINGS.engine
                            });

                            logger.log('📥 [模式2-阅读器翻译] 翻译完成通知已发送至标签页', tabId);
                        } catch (contentScriptError) {
                            logger.log(`⚠️ 无法主动发送阅读模式翻译结果到标签页 ${tabId}:`, contentScriptError.message);
                        }
                    } catch (error) {
                        console.error('阅读器翻译失败:', error);

                        // 发送错误响应
                        sendResponse({ success: false, error: error.message });

                        // 主动通知内容脚本翻译失败
                        try {
                            await browser.tabs.sendMessage(tabId, {
                                action: 'readerTranslationError',
                                error: error.message,
                                contentLength: request.articleContent?.length
                            });
                            logger.log(`❌ 阅读模式翻译错误已主动发送至标签页 ${tabId}`);
                        } catch (contentScriptError) {
                            logger.log(`⚠️ 无法主动发送阅读模式翻译错误到标签页 ${tabId}:`, contentScriptError.message);
                        }
                    }
                    break;

                // 模式3：选中翻译API调用
                case 'selectionTranslate':
                    try {
                        logger.log('🔄 [模式3-选中翻译] 开始处理API请求:', {
                            textLength: request.context.length,
                            targetLang: request.targetLang || 'zh',
                            sourceLang: request.sourceLang || 'auto',
                            engine: request.engine || GlobalConfig.DEFAULT_SETTINGS.engine
                        });

                        const startTime = Date.now();
                        const selectionResult = await this.translationService.callSelectionTranslationAPI(
                            request.context,
                            request.sourceLang || 'auto',
                            request.targetLang || 'zh',
                            request.engine || GlobalConfig.DEFAULT_SETTINGS.engine
                        );
                        const endTime = Date.now();
                        const duration = endTime - startTime;

                        logger.log('✅ [模式3-选中翻译] API请求处理完成:', {
                            success: selectionResult?.success,
                            duration: `${duration}ms`,
                            translationLength: selectionResult?.translation?.length
                        });

                        this.stateManager.recordTranslation(tabId, 'selection_translate', {
                            text: request.context,
                            mode: 'selection'
                        });

                        // 直接响应原始请求（content script 通过 sendResponse 获取结果）
                        sendResponse({ success: true, data: selectionResult });
                        return true;

                    } catch (error) {
                        console.error('选中翻译失败:', error);

                        // 主动通知内容脚本翻译失败（保留错误消息用于特殊情况）
                        try {
                            await browser.tabs.sendMessage(tabId, {
                                action: 'selectionTranslationError',
                                error: error.message,
                                originalText: request.context
                            });
                            logger.log(`❌ 选中翻译错误已主动发送至标签页 ${tabId}`);
                        } catch (contentScriptError) {
                            logger.log(`⚠️ 无法主动发送选中翻译错误到标签页 ${tabId}:`, contentScriptError.message);
                        }

                        // 发送错误响应
                        sendResponse({ success: false, error: error.message });
                        return true;
                    }
                    break;

                // 批量翻译（模式1: 整个网页翻译）
                case 'batchTranslate':
                    let batchResult;

                    if (request.textRegistry) {
                        // 使用 textRegistry 格式，调用整页翻译API
                        logger.log('🔄 [模式1-网页翻译] 开始处理API请求:', {
                            registrySize: request.textRegistry.length,
                            targetLang: request.targetLang || 'zh',
                            sourceLang: request.sourceLang || 'auto',
                            engine: request.engine || GlobalConfig.DEFAULT_SETTINGS.engine
                        });

                        const startTime = Date.now();
                        batchResult = await this.translationService.batchTranslateWebpage(
                            request.textRegistry,
                            request.sourceLang || 'auto',
                            request.targetLang || 'zh',
                            request.engine || GlobalConfig.DEFAULT_SETTINGS.engine
                        );
                        const endTime = Date.now();
                        const duration = endTime - startTime;

                        logger.log('✅ [模式1-网页翻译] API请求处理完成:', {
                            success: batchResult?.success,
                            duration: `${duration}ms`,
                            translationsLength: batchResult?.translations?.length,
                            batchSize: request.textRegistry.length
                        });

                        this.stateManager.recordTranslation(tabId, 'batch_translation', {
                            batchId: request.batchId,
                            size: request.textRegistry.length,
                            service: request.engine
                        });
                    } else {
                        // 使用 texts 格式，调用快速翻译API
                        batchResult = await this.translationService.batchTranslate(
                            request.contexts,
                            request.sourceLang || 'auto',
                            request.targetLang || 'zh',
                            request.engine || GlobalConfig.DEFAULT_SETTINGS.engine
                        );

                        this.stateManager.recordTranslation(tabId, 'batch_translation', {
                            batchId: request.batchId,
                            size: request.contexts.length,
                            service: request.engine
                        });
                    }

                    // 模式1标准响应格式
                    sendResponse({
                        success: true,
                        translations: batchResult.translations
                    });
                    break;

                // 上传映射表进行流式翻译（模式1: 整个网页翻译 - SSE流式响应）
                case 'uploadMappingTableStream': {
                    try {
                        // 检查是否为分批上传
                        const hasBatches = request.mappingTable.batches && Array.isArray(request.mappingTable.batches);
                        const textCount = hasBatches ?
                            request.mappingTable.batches.reduce((sum, batch) => sum + batch.length, 0) :
                            request.mappingTable.textRegistry?.length || 0;

                        logger.log('🔄 [流式模式1-网页翻译] 开始处理上传映射表请求:', {
                            isBatched: hasBatches,
                            textCount: textCount,
                            batchCount: hasBatches ? request.mappingTable.batchCount : 'N/A',
                            targetLang: request.targetLang || 'zh',
                            sourceLang: request.sourceLang || 'auto',
                            engine: request.engine || GlobalConfig.DEFAULT_SETTINGS.engine,
                            bilingual: request.bilingual || false
                        });

                        if (hasBatches) {
                            logger.log('📊 批次大小分布:', request.mappingTable.batches.map(batch => batch.length));
                        }

                        // 提取实际的textRegistry（如果是分批的，需要合并）
                        const textRegistry = hasBatches ?
                            request.mappingTable.batches.flat() :
                            request.mappingTable.textRegistry;

                        logger.log('✅ 跳过API连接检查，开始流式翻译处理');
                        const startTime = Date.now();
                        const result = await this.mappingManager.processMappingTable(
                            {
                                ...request.mappingTable,
                                textRegistry: textRegistry,
                                sourceLang: request.sourceLang || 'auto',  // 使用请求中的sourceLang，覆盖页面级语言
                                targetLang: request.targetLang || 'zh'     // 使用请求中的targetLang，优先于存储设置
                            },
                            tabId
                        );
                        const mappingProcessTime = Date.now() - startTime;

                        logger.log('🔍 [流式模式1-网页翻译] 映射表处理结果:', {
                            success: result.success,
                            hasTranslationPlan: !!result.translationPlan,
                            mappingProcessTime: `${mappingProcessTime}ms`
                        });

                        if (result.success && result.translationPlan) {
                            logger.log('✅ [流式模式1-网页翻译] 映射表处理成功，开始流式翻译，文本数量:', textCount);

                            // 立即发送确认响应
                            sendResponse({
                                success: true,
                                message: '流式翻译已启动',
                                textCount: textCount
                            });

                            // 异步执行流式翻译
                            setTimeout(async () => {
                                try {
                                    const batchStartTime = Date.now();

                                    // 批量翻译并实时推送翻译块到 content
                                    const batchResult = await this.translationService.batchTranslateWebpage(
                                        textRegistry,
                                        result.translationPlan.sourceLang,
                                        result.translationPlan.targetLang,
                                        result.translationPlan.service,
                                        request.fastMode !== undefined ? request.fastMode : true,
                                        // 流式回调 - 实时推送翻译块
                                        async (chunk) => {
                                            try {
                                                // 向 content script 推送单个翻译块
                                                await browser.tabs.sendMessage(tabId, {
                                                    action: 'streamTranslationChunk',
                                                    textId: chunk.textId,
                                                    original: chunk.original,
                                                    translation: chunk.translation,
                                                    bilingual: request.bilingual || false
                                                });
                                            } catch (pushError) {
                                                logger.warn(`⚠️ [流式] 推送翻译块失败: ${chunk.textId}`, pushError.message);
                                            }
                                        }
                                    );

                                    const batchEndTime = Date.now();
                                    const batchDuration = batchEndTime - batchStartTime;

                                    logger.log('✅ [流式模式1-网页翻译] 流式翻译完成:', {
                                        translationsCount: batchResult.translations.length,
                                        batchDuration: `${batchDuration}ms`,
                                        totalTextCount: textCount
                                    });

                                    // 记录状态
                                    this.stateManager.recordTranslation(tabId, 'stream_translation', {
                                        size: textCount,
                                        service: result.translationPlan.service
                                    });

                                    // 向 content script 推送翻译完成消息
                                    try {
                                        await browser.tabs.sendMessage(tabId, {
                                            action: 'streamTranslationComplete',
                                            translations: batchResult.translations,
                                            sourceLang: result.translationPlan.sourceLang,
                                            targetLang: result.translationPlan.targetLang,
                                            engine: result.translationPlan.service,
                                            bilingual: request.bilingual || false
                                        });

                                        logger.log(`📥 [流式] 翻译完成通知已主动发送至标签页 ${tabId}`);
                                    } catch (contentScriptError) {
                                        logger.log(`⚠️ [流式] 无法主动发送翻译完成通知到标签页 ${tabId}:`, contentScriptError.message);
                                    }

                                    // 异步保存状态到本地存储
                                    setTimeout(async () => {
                                        try {
                                            const result = await browser.storage.local.get(['pageStates']);
                                            const pageStates = result.pageStates || {};
                                            pageStates[tabId] = 'showing_translation';
                                            await browser.storage.local.set({ pageStates });

                                            logger.log(`✅ [流式] 翻译完成状态已保存到本地存储: tab ${tabId}`);
                                        } catch (statusSaveError) {
                                            console.error('[流式] 保存页面状态失败:', statusSaveError);
                                        }
                                    }, 100);

                                } catch (streamError) {
                                    console.error('❌ [流式] 流式翻译处理失败:', streamError);

                                    // 向 content script 推送错误消息
                                    try {
                                        await browser.tabs.sendMessage(tabId, {
                                            action: 'streamTranslationError',
                                            error: streamError.message
                                        });
                                    } catch (errorPushError) {
                                        logger.log('⚠️ [流式] 无法推送错误消息到标签页:', errorPushError.message);
                                    }

                                    // 保存失败状态
                                    setTimeout(async () => {
                                        try {
                                            const result = await browser.storage.local.get(['pageStates']);
                                            const pageStates = result.pageStates || {};
                                            pageStates[tabId] = 'original';
                                            await browser.storage.local.set({ pageStates });
                                        } catch (saveError) {
                                            console.error('[流式] 保存失败状态时出错:', saveError);
                                        }
                                    }, 100);
                                }
                            }, 0); // 立即执行但不阻塞响应

                            return true; // 保持消息通道开放
                        } else {
                            // 使用更友好的错误消息
                            const errorMsg = result.error || '处理映射表失败';
                            console.error('❌ [流式] 映射表处理失败:', errorMsg);

                            // 简化错误消息，隐藏技术细节
                            let displayMsg = errorMsg.replace(/第\d+批.*?失败/, '翻译处理异常');

                            sendResponse({
                                success: false,
                                error: displayMsg
                            });
                        }
                    } catch (error) {
                        console.error('❌ uploadMappingTableStream处理失败:', error);

                        // 确保始终发送响应
                        sendResponse({
                            success: false,
                            error: error.message,
                            errorCode: 'TRANSLATION_ERROR'
                        });
                    }

                    return true; // 保持消息通道开放
                }

                // 上传映射表（模式1: 整个网页翻译）
                case 'uploadMappingTable': {
                    try {
                        logger.log('🔄 [模式1-网页翻译] 开始处理上传映射表请求:', {
                            textRegistrySize: request.mappingTable.textRegistry.length,
                            targetLang: request.targetLang || 'zh',
                            sourceLang: request.sourceLang || 'auto',
                            engine: request.engine || GlobalConfig.DEFAULT_SETTINGS.engine
                        });

                        logger.log('✅ 跳过API连接检查，开始处理映射表');
                        const startTime = Date.now();
                        const result = await this.mappingManager.processMappingTable(request.mappingTable, tabId);
                        const mappingProcessTime = Date.now() - startTime;

                        logger.log('🔍 [模式1-网页翻译] 映射表处理结果:', {
                            success: result.success,
                            hasTranslationPlan: !!result.translationPlan,
                            mappingProcessTime: `${mappingProcessTime}ms`
                        });

                        if (result.success && result.translationPlan) {
                            logger.log('✅ [模式1-网页翻译] 映射表处理成功，开始批量翻译，文本数量:', request.mappingTable.textRegistry.length);

                            const batchStartTime = Date.now();
                            const batchResult = await this.translationService.batchTranslateWebpage(
                                request.mappingTable.textRegistry,
                                result.translationPlan.sourceLang,
                                result.translationPlan.targetLang,
                                result.translationPlan.service
                            );
                            const batchEndTime = Date.now();
                            const batchDuration = batchEndTime - batchStartTime;

                            logger.log('✅ [模式1-网页翻译] 批量翻译完成:', {
                                translationsCount: batchResult.translations.length,
                                batchDuration: `${batchDuration}ms`,
                                batchSize: request.mappingTable.textRegistry.length
                            });

                            // 记录状态
                            this.stateManager.recordTranslation(tabId, 'batch_translation', {
                                size: request.mappingTable.textRegistry.length,
                                service: result.translationPlan.service
                            });

                            // 先发送确认给发起请求的content脚本
                            sendResponse({
                                success: true,
                                translations: batchResult.translations,
                                message: `翻译完成，共${batchResult.translations.length}个文本`
                            });

                            // 然后主动向指定标签页的内容脚本发送翻译结果
                            try {
                                logger.log('📤 [模式1-网页翻译] 发送翻译结果到标签页:', {
                                    action: 'applyTranslations',
                                    translationsCount: batchResult.translations.length,
                                    tabId: tabId
                                });

                                await browser.tabs.sendMessage(tabId, {
                                    action: 'applyTranslations',
                                    translations: batchResult.translations,
                                    sourceLang: result.translationPlan.sourceLang,
                                    targetLang: result.translationPlan.targetLang,
                                    service: result.translationPlan.service,
                                    bilingual: request.bilingual || false
                                });

                                logger.log(`📥 [模式1-网页翻译] 翻译结果已主动发送至标签页 ${tabId}`);
                            } catch (contentScriptError) {
                                logger.log(`⚠️ 无法主动发送翻译结果到标签页 ${tabId}:`, contentScriptError.message);
                            }

                            // 异步保存状态到本地存储，而不是发送给popup
                            setTimeout(async () => {
                                // 等待更长时间让内容脚本有时间应用翻译，然后向内容脚本查询准确的页面状态
                                // 使用重试机制确保获取到准确状态
                                const getStatusWithRetry = async (retries = 3, delay = 500) => {
                                    for (let i = 0; i < retries; i++) {
                                        try {
                                            const contentStatus = await browser.tabs.sendMessage(tabId, {
                                                action: 'getPageTranslationStatus'
                                            });

                                            const accurateStatus = contentStatus?.status || 'showing_translation';

                                            // 如果状态不是 'original'（意味着检测到了翻译），直接返回
                                            if (accurateStatus !== 'original' && accurateStatus !== 'translated_suspected') {
                                                return accurateStatus;
                                            }

                                            // 如果是 'original'，可能翻译尚未完全应用，等待后重试
                                            if (i < retries - 1) {
                                                await new Promise(resolve => setTimeout(resolve, delay));
                                                logger.log(`Retry ${i + 1}/${retries}: Waiting for translation to apply...`);
                                            }
                                        } catch (error) {
                                            logger.log(`Retry ${i + 1}/${retries} failed:`, error.message);
                                            if (i >= retries - 1) {
                                                throw error; // 最后一次重试失败，抛出错误
                                            }
                                            await new Promise(resolve => setTimeout(resolve, delay));
                                        }
                                    }

                                    // 如果多次重试后仍然是 'original'，返回默认的显示译文状态
                                    return 'showing_translation';
                                };

                                try {
                                    const accurateStatus = await getStatusWithRetry();

                                    // 将准确的状态保存到本地存储
                                    const result = await browser.storage.local.get(['pageStates']);
                                    const pageStates = result.pageStates || {};
                                    pageStates[tabId] = accurateStatus;
                                    await browser.storage.local.set({ pageStates });

                                    logger.log(`✅ 翻译完成状态已保存到本地存储: tab ${tabId}, accurateStatus: ${accurateStatus}`);
                                } catch (statusQueryError) {
                                    console.error('查询页面状态失败，使用默认状态:', statusQueryError);

                                    // 查询失败时，使用默认的显示译文状态
                                    const result = await browser.storage.local.get(['pageStates']);
                                    const pageStates = result.pageStates || {};
                                    pageStates[tabId] = 'showing_translation';
                                    await browser.storage.local.set({ pageStates });

                                    logger.log(`✅ 默认翻译状态已保存到本地存储: tab ${tabId}, status: showing_translation`);
                                }
                            }, 100); // 短暂延迟以确保 response has been sent
                        } else {
                            // 使用更友好的错误消息
                            const errorMsg = result.error || '处理映射表失败';
                            console.error('❌ [模式1-网页翻译] 映射表处理失败:', errorMsg);

                            // 简化错误消息，隐藏技术细节
                            let displayMsg = errorMsg.replace(/第\d+批.*?失败/, '翻译处理异常');

                            sendResponse({
                                success: false,
                                error: displayMsg
                            });

                            // 也仅保存失败状态到本地存储
                            setTimeout(async () => {
                                // 等待片刻让内容脚本有机会处理状态，然后查询准确的页面状态
                                // 使用重试机制确保获取到准确状态
                                const getStatusWithRetry = async (retries = 2, delay = 300) => {
                                    for (let i = 0; i < retries; i++) {
                                        try {
                                            const contentStatus = await browser.tabs.sendMessage(tabId, {
                                                action: 'getPageTranslationStatus'
                                            });

                                            const accurateStatus = contentStatus?.status || 'original';

                                            // 返回获取到的状态
                                            return accurateStatus;
                                        } catch (error) {
                                            logger.log(`Retry ${i + 1}/${retries} failed:`, error.message);
                                            if (i >= retries - 1) {
                                                throw error; // 最后一次重试失败，抛出错误
                                            }
                                            await new Promise(resolve => setTimeout(resolve, delay));
                                        }
                                    }
                                };

                                try {
                                    const accurateStatus = await getStatusWithRetry();

                                    // 将准确的状态保存到本地存储
                                    const result = await browser.storage.local.get(['pageStates']);
                                    const pageStates = result.pageStates || {};
                                    pageStates[tabId] = accurateStatus;
                                    await browser.storage.local.set({ pageStates });

                                    logger.log(`✅ 翻译失败状态已保存到本地存储: tab ${tabId}, accurateStatus: ${accurateStatus}`);
                                } catch (statusQueryError) {
                                    console.error('查询页面状态失败，使用默认状态:', statusQueryError);

                                    // 查询失败时，使用默认的原始状态
                                    const result = await browser.storage.local.get(['pageStates']);
                                    const pageStates = result.pageStates || {};
                                    pageStates[tabId] = 'original';
                                    await browser.storage.local.set({ pageStates });

                                    logger.log(`✅ 默认失败状态已保存到本地存储: tab ${tabId}, status: original`);
                                }
                            }, 100);
                        }
                    } catch (error) {
                        console.error('❌ uploadMappingTable处理失败:', error);

                        // 确保始终发送响应
                        sendResponse({
                            success: false,
                            error: error.message,
                            errorCode: 'TRANSLATION_ERROR',
                            stack: error.stack // 添加堆栈信息有助于调试
                        });

                        // 仅保存失败状态到本地存储
                        setTimeout(async () => {
                            try {
                                // 查询当前页面的准确状态
                                const contentStatus = await browser.tabs.sendMessage(tabId, {
                                    action: 'getPageTranslationStatus'
                                });

                                const accurateStatus = contentStatus?.status || 'original';

                                // 将准确的状态保存到本地存储
                                const result = await browser.storage.local.get(['pageStates']);
                                const pageStates = result.pageStates || {};
                                pageStates[tabId] = accurateStatus;
                                await browser.storage.local.set({ pageStates });

                                logger.log(`✅ 上传映射表失败状态已保存到本地存储: tab ${tabId}, status: ${accurateStatus}`);
                            } catch (statusQueryError) {
                                logger.log('无法获取页面准确状态，保存默认原始状态:', statusQueryError.message);

                                // 查询失败时，使用默认的原始状态
                                const result = await browser.storage.local.get(['pageStates']);
                                const pageStates = result.pageStates || {};
                                pageStates[tabId] = 'original';
                                await browser.storage.local.set({ pageStates });

                                logger.log(`✅ 默认失败状态已保存到本地存储: tab ${tabId}, status: original`);
                            }
                        }, 100);
                    }

                    return true; // 保持消息通道开放
                }

                // 获取翻译状态
                case 'getTranslationStatus':
                    const status = this.stateManager.getTranslationStatus(tabId);
                    sendResponse({ success: true, status });
                    break;

                // 处理阅读模式文章（包含翻译）- 严格按照接口文档
                case 'processArticleForReader':
                    try {
                        // 模式2: 从请求中获取翻译参数
                        const content = request.content;  // 模式2必需字段
                        const targetLang = request.targetLang || 'zh';
                        const engine = request.engine || GlobalConfig.DEFAULT_SETTINGS.engine;

                        if (!content) {
                            throw new Error('缺少 content 字段');
                        }

                        // 按照接口文档格式翻译文章内容
                        logger.log('📚 模式2: 阅读器翻译请求:', {
                            contentLength: content.length,
                            targetLang,
                            engine
                        });

                        const readerResult = await this.translationService.callReaderTranslationAPI(
                            content,
                            targetLang,
                            'auto',
                            engine
                        );

                        if (!readerResult || !readerResult.success) {
                            throw new Error('阅读器翻译API返回失败: ' + (readerResult?.error || '未知错误'));
                        }

                        // 记录状态
                        this.stateManager.setReaderMode(tabId, true, {
                            title: request.title,
                            translatedAt: Date.now()
                        });

                        // 模式2标准响应格式 - 先发送响应确认收到请求
                        sendResponse({
                            success: true,
                            message: '请求已收到，正在处理',
                            requestId: Date.now()
                        });

                        // 主动向内容脚本发送翻译结果
                        setTimeout(async () => {
                            try {
                                await browser.tabs.sendMessage(sender.tab.id, {
                                    action: 'readerTranslationCompleted',
                                    success: true,
                                    translatedContent: readerResult.translatedContent,
                                    engine: engine,
                                    originalContent: request.content,
                                    title: request.title,
                                    byline: request.byline || ''
                                });
                                logger.log('✅ 翻译结果已主动推送给内容脚本');
                            } catch (sendError) {
                                console.error('❌ 发送翻译结果到内容脚本失败:', sendError);
                                // 如果发送失败，可能内容脚本还没准备好或者已被卸载
                            }
                        }, 100); // 稍微延迟一下确保响应已发送

                        logger.log('✅ 处理阅读模式文章成功', readerResult.translatedContent);

                        // 注意：由于我们使用了异步发送消息，这里不再直接sendResponse
                        return true; // 保持消息通道开放，因为我们已经发送了初始响应
                    } catch (error) {
                        console.error('❌ 处理阅读模式文章失败:', error);

                        // 发送初始的成功响应（防止超时）
                        sendResponse({
                            success: false,
                            message: '请求已收到，但处理失败',
                            error: error.message
                        });

                        // 然后主动发送错误信息到内容脚本
                        setTimeout(async () => {
                            try {
                                await browser.tabs.sendMessage(sender.tab.id, {
                                    action: 'readerTranslationError',
                                    success: false,
                                    error: error.message,
                                    originalContent: request.content
                                });
                                logger.log('✅ 错误信息已主动推送给内容脚本');
                            } catch (sendError) {
                                console.error('❌ 发送错误信息到内容脚本失败:', sendError);
                            }
                        }, 100);

                        return true; // 保持消息通道开放
                    }
                    break;

                // 获取扩展统计
                case 'getStats':
                    const stats = await this.getExtensionStats();
                    sendResponse({ success: true, stats });
                    break;

                // 记录用户使用情况
                case 'logUsage':
                    // 这里可以发送到服务器进行统计分析
                    sendResponse({ success: true });
                    break;

                // 保存设置
                case 'saveSettings':
                    await this.saveSettings(request.settings);
                    sendResponse({ success: true });
                    break;

                // 获取设置
                case 'getSettings':
                    const settings = await this.getSettings();
                    sendResponse({ success: true, settings });
                    break;

                // 清理缓存
                case 'clearCache':
                    this.translationService.cache.clear();
                    sendResponse({ success: true, message: '缓存已清理' });
                    break;

                // 处理右键菜单/快捷键翻译
                case 'translateTextFromMenu':
                    try {
                        const result = await this.translationService.translateText(
                            request.context,
                            request.sourceLang || 'auto',
                            request.targetLang || 'zh',
                            request.engine || GlobalConfig.DEFAULT_SETTINGS.engine,
                            request.bilingual || false
                        );
                        this.stateManager.recordTranslation(tabId, 'menu_translation', {
                            text: request.context,
                            length: request.context.length,
                            service: request.engine
                        });
                        sendResponse({ success: true, data: result });
                    } catch (error) {
                        console.error('❌ 菜单翻译失败:', error);
                        sendResponse({ success: false, error: error.message });
                    }
                    break;

                // 更新右键菜单上下文
                case 'updateContextMenu':
                    // 存储选中文本，用于右键菜单更新
                    this.contextText = request.context;
                    this.hasSelection = request.hasSelection;
                    sendResponse({ success: true });
                    break;

                // 测试翻译服务

                // 修复：处理 activateReaderMode - 转发到内容脚本
                case 'activateReaderMode': {
                    logger.log('[BG] 收到 activateReaderMode 请求', request);

                    const tabId = sender.tab?.id || request.tabId;
                    if (!tabId) {
                        console.error('[BG] 无法获取 tabId');
                        sendResponse({ success: false, error: '无法获取标签页 ID' });
                        break;
                    }

                    logger.log('[BG] 转发消息到 content script, tabId:', tabId);

                    try {
                        // 转发消息到内容脚本
                        const response = await browser.tabs.sendMessage(tabId, {
                            action: 'activateReaderMode',
                            targetLang: request.targetLang,
                            sourceLang: request.sourceLang,
                            engine: request.engine
                        });

                        logger.log('[BG] 收到 content script 响应:', response);
                        sendResponse(response);
                    } catch (error) {
                        logger.log('[BG] 第一次尝试失败:', error.message);
                        logger.log('⚠️ 尝试动态注入脚本...');

                        // 如果内容脚本未注入，尝试动态注入
                        if (error.message && error.message.includes('Could not establish connection')) {
                            try {
                                // 动态注入阅读器脚本及其依赖
                                logger.log('[BG] 开始注入依赖和 read.js...');
                                await browser.scripting.executeScript({
                                    target: { tabId },
                                    files: [
                                        'src/lib/browser-polyfill.js',
                                        'src/lib/config.js',
                                        'src/lib/purify.js',
                                        'src/lib/Readability.js',
                                        'src/content/read.js'
                                    ]
                                });
                                logger.log('✅ 阅读器脚本及依赖注入成功');

                                // 等待脚本初始化
                                logger.log('[BG] 等待 800ms 让脚本初始化...');
                                await new Promise(resolve => setTimeout(resolve, 800));

                                // 重试发送消息
                                logger.log('[BG] 重试发送消息到 content script');
                                const retryResponse = await browser.tabs.sendMessage(tabId, {
                                    action: 'activateReaderMode',
                                    targetLang: request.targetLang,
                                    sourceLang: request.sourceLang,
                                    engine: request.engine
                                });

                                logger.log('[BG] 重试响应:', retryResponse);
                                sendResponse(retryResponse);
                                break;
                            } catch (injectError) {
                                console.error('[BG] 动态注入失败:', injectError);
                            }
                        }

                        console.error('[BG] 转发 activateReaderMode 到内容脚本失败:', error);
                        sendResponse({
                            success: false,
                            error: error.message,
                            message: '无法与页面通信，请刷新页面后重试。注意：浏览器内部页面不支持此功能'
                        });
                    }
                    break;
                }

                // 新增：心跳检测
                case 'ping':
                    sendResponse({ active: true, version: '2.0' });
                    break;

                // 修复：处理 translationStatusUpdate 消息
                case 'translationStatusUpdate':
                    // 通常这种消息是从content script发到popup的，现在我们直接将状态保存到本地存储
                    try {
                        const { status, tabId } = request;

                        // 将状态保存到本地存储
                        const result = await browser.storage.local.get(['pageStates']);
                        const pageStates = result.pageStates || {};
                        const targetTabId = tabId || sender.tab?.id;

                        if (targetTabId) {
                            pageStates[targetTabId] = status;
                            await browser.storage.local.set({ pageStates });

                            logger.log(`✅ 页面状态已保存到本地存储: tab ${targetTabId}, status: ${status}`);
                        }

                        sendResponse({ success: true, message: '状态更新已处理并保存', forwarded: false });
                    } catch (error) {
                        sendResponse({ success: true, message: '状态更新处理完成', forwarded: false });
                    }
                    break;

                // 新增：处理 savePageStatus 消息，直接保存状态到本地存储
                case 'savePageStatus':
                    try {
                        const { status, tabId } = request;

                        // 从本地存储获取现有页面状态
                        const result = await browser.storage.local.get(['pageStates']);
                        const pageStates = result.pageStates || {};

                        // 更新特定标签页的状态
                        const targetTabId = tabId || sender.tab?.id;
                        if (targetTabId) {
                            pageStates[targetTabId] = status;

                            // 保存回本地存储
                            await browser.storage.local.set({ pageStates });

                            logger.log(`✅ 页面状态已保存: tab ${targetTabId}, status: ${status}`);

                            // 尝试将状态更新转发到popup（如果打开的话）
                            try {
                                await browser.runtime.sendMessage({
                                    action: 'translationStatusUpdate',
                                    status: status,
                                    tabId: targetTabId
                                });
                            } catch (popupError) {
                                // 如果popup未打开，这是正常的，只需记录
                                logger.log('Popup可能未打开，状态已保存但未转发');
                            }

                            sendResponse({
                                success: true,
                                message: '页面状态已保存',
                                tabId: targetTabId,
                                status: status
                            });
                        } else {
                            sendResponse({
                                success: false,
                                error: '无法确定标签页ID'
                            });
                        }
                    } catch (error) {
                        console.error('保存页面状态失败:', error);
                        sendResponse({
                            success: false,
                            error: error.message
                        });
                    }
                    break;

                // 处理页面加载事件，重置标签页状态
                case 'pageLoaded':
                    try {
                        // 如果提供了tab ID使用tab ID，否则尝试从sender获取
                        const tabId = sender.tab?.id || `tab_${Date.now()}`;

                        if (tabId) {
                            // 将页面状态重置为原始状态
                            const result = await browser.storage.local.get(['pageStates']);
                            const pageStates = result.pageStates || {};
                            pageStates[tabId] = 'original';

                            await browser.storage.local.set({ pageStates });

                            logger.log(`🔄 页面加载，状态已重置: tab ${tabId}`);
                        }

                        sendResponse({
                            success: true,
                            message: '页面状态已重置',
                            tabId: tabId
                        });
                    } catch (error) {
                        console.error('重置页面状态失败:', error);
                        sendResponse({
                            success: false,
                            error: error.message
                        });
                    }
                    break;

                // 处理获取页面状态请求
                case 'getPageStatus':
                    try {
                        const tabId = request.tabId || sender.tab?.id;

                        if (!tabId) {
                            sendResponse({
                                success: false,
                                error: '无法确定标签页ID'
                            });
                            return;
                        }

                        // 从本地存储获取页面状态
                        const result = await browser.storage.local.get(['pageStates']);
                        const pageStates = result.pageStates || {};
                        const status = pageStates[tabId] || 'original';

                        logger.log(`✅ 返回页面状态: tab ${tabId}, status: ${status}`);

                        sendResponse({
                            success: true,
                            status: status,
                            tabId: tabId
                        });
                    } catch (error) {
                        console.error('获取页面状态失败:', error);
                        sendResponse({
                            success: false,
                            error: error.message,
                            status: 'original'
                        });
                    }
                    break;

                // 处理设置更新请求
                case 'updateSetting':
                    try {
                        const { key, value } = request;

                        // 从本地存储获取现有设置
                        const result = await browser.storage.local.get(['settings']);
                        const currentSettings = result.settings || {};

                        // 更新特定设置
                        currentSettings[key] = value;

                        // 保存回本地存储
                        await browser.storage.local.set({ settings: currentSettings });

                        logger.log(`✅ 设置已更新: ${key} = ${value}`);

                        // 广播设置变更到所有标签页
                        try {
                            const tabs = await browser.tabs.query({}); // 获取所有标签页

                            for (const tab of tabs) {
                                try {
                                    await browser.tabs.sendMessage(tab.id, {
                                        action: 'settingUpdated',
                                        key: key,
                                        value: value
                                    });
                                } catch (msgError) {
                                    // 如果标签页无法接收消息（如内容脚本未加载），则忽略
                                    logger.log(`无法向标签页 ${tab.id} 发送设置更新:`, msgError.message);
                                }
                            }
                        } catch (broadcastError) {
                            logger.warn('广播设置更新时出错:', broadcastError.message);
                        }

                        sendResponse({
                            success: true,
                            message: '设置已更新并广播到所有标签页',
                            key: key,
                            value: value
                        });
                    } catch (error) {
                        console.error('更新设置失败:', error);
                        sendResponse({
                            success: false,
                            error: error.message
                        });
                    }
                    break;

                // 认证相关消息处理
                case 'setAuthToken':
                    try {
                        const { token, userInfo } = request;
                        await browser.storage.local.set({ auth_token: token, auth_user: userInfo });
                        logger.log('✅ 认证令牌已保存');
                        sendResponse({ success: true });
                    } catch (error) {
                        console.error('保存认证令牌失败:', error);
                        sendResponse({ success: false, error: error.message });
                    }
                    break;

                case 'getAuthState':
                    try {
                        const result = await browser.storage.local.get(['auth_token', 'auth_user']);
                        sendResponse({
                            success: true,
                            isLoggedIn: !!result.auth_token,
                            token: result.auth_token || null,
                            user: result.auth_user || null
                        });
                    } catch (error) {
                        sendResponse({ success: false, error: error.message });
                    }
                    break;

                case 'clearAuthToken':
                    try {
                        await browser.storage.local.remove(['auth_token', 'auth_user']);
                        logger.log('✅ 认证令牌已清除');
                        sendResponse({ success: true });
                    } catch (error) {
                        sendResponse({ success: false, error: error.message });
                    }
                    break;

                case 'toggleDisplayMode':
                    // 将切换显示模式的请求转发到对应的标签页
                    try {
                        if (sender.tab && sender.tab.id) {
                            const response = await browser.tabs.sendMessage(sender.tab.id, {
                                action: 'toggleDisplayMode'
                            });
                            sendResponse(response);
                        } else {
                            sendResponse({ success: false, error: '无法识别标签页' });
                        }
                    } catch (error) {
                        sendResponse({ success: false, error: error.message });
                    }
                    break;


                default:
                    logger.warn('⚠️ 未知的操作:', request.action);
                    sendResponse({ success: false, error: '未知的操作: ' + request.action });
            }
        } catch (error) {
            console.error(`处理${request.action}失败:`, error);
            sendResponse({ success: false, error: error.message });
        }
    }

    // 设置标签页监听器
    setupTabListeners() {
        // 标签页更新
        browser.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
            if (changeInfo.status === 'loading') {
                // 页面开始加载，清理相关状态
                this.stateManager.cleanupTab(tabId);
                this.mappingManager.cleanupMappingTable(tabId);

                // 同时将页面状态重置为原始状态
                this.resetPageStatus(tabId);
            }
        });

        // 标签页关闭
        browser.tabs.onRemoved.addListener((tabId) => {
            this.stateManager.cleanupTab(tabId);
            this.mappingManager.cleanupMappingTable(tabId);

            // 清理存储中的页面状态
            this.clearPageStatus(tabId);
        });
    }

    // 重置页面状态为原始状态
    async resetPageStatus(tabId) {
        try {
            const result = await browser.storage.local.get(['pageStates']);
            const pageStates = result.pageStates || {};
            pageStates[tabId] = 'original';

            await browser.storage.local.set({ pageStates });
            logger.log(`🔄 页面状态已重置: tab ${tabId}`);
        } catch (error) {
            console.error('重置页面状态失败:', error);
        }
    }

    // 清理页面状态
    async clearPageStatus(tabId) {
        try {
            const result = await browser.storage.local.get(['pageStates']);
            const pageStates = result.pageStates || {};

            // 删除指定标签页的状态
            delete pageStates[tabId];

            await browser.storage.local.set({ pageStates });
            logger.log(`🗑️ 页面状态已清理: tab ${tabId}`);
        } catch (error) {
            console.error('清理页面状态失败:', error);
        }
    }

    // 设置存储监听器
    setupStorageListeners() {
        browser.storage.onChanged.addListener((changes, namespace) => {

            // 当API密钥更新时，清理相关缓存
            if (changes.api_keys) {
                this.translationService.cache.clear();
            }

            // 当设置更新时
            if (changes.settings) {
            }
        });
    }

    // 设置安装事件
    setupInstallEvents() {
        // 扩展安装或更新
        browser.runtime.onInstalled.addListener((details) => {
            if (details.reason === 'install') {
                this.showWelcomePage();
            } else if (details.reason === 'update') {
                this.showUpdateInfo(details.previousVersion);
            }

            // 初始化默认设置
            this.setDefaultSettings();
        });
    }

    // 设置默认设置
    async setDefaultSettings() {
        try {
            const result = await browser.storage.local.get(['settings']);
            const settings = result.settings || {};

            // 设置默认值
            const defaultSettings = {
                target_lang: settings.target_lang || 'zh',
                engine: settings.engine || 'google',
                bilingual: settings.bilingual !== false, // 默认开启双语
                auto_translate: settings.auto_translate || false,
                translation_quality: settings.translation_quality || 'balanced'
            };

            await browser.storage.local.set({ settings: defaultSettings });
        } catch (error) {
            console.error('设置默认配置失败:', error);
        }
    }

    // 处理文章
    async processArticle(articleData) {
        // 这里可以对文章进行进一步处理，比如：
        // 1. 翻译整个文章
        // 2. 提取关键信息
        // 3. 优化格式

        const processedArticle = {
            ...articleData,
            processedAt: Date.now(),
            wordCount: this.countWords(articleData.content),
            readingTime: this.calculateReadingTime(articleData.content)
        };

        return processedArticle;
    }

    // 统计单词数
    countWords(content) {
        const text = content.replace(/<[^>]*>/g, ' '); // 移除HTML标签
        return text.trim().split(/\s+/).length;
    }

    // 计算阅读时间
    calculateReadingTime(content, wordsPerMinute = 200) {
        const wordCount = this.countWords(content);
        return Math.ceil(wordCount / wordsPerMinute);
    }

    // 获取扩展统计
    async getExtensionStats() {
        const [tabs] = await browser.tabs.query({});

        let totalTranslations = 0;
        let activeTranslations = 0;

        // 统计所有标签页的翻译状态
        for (const tab of tabs) {
            const status = this.stateManager.getTranslationStatus(tab.id);
            if (status) {
                totalTranslations += status.translationCount;
                activeTranslations++;
            }
        }

        return {
            totalTranslations,
            activeTranslations,
            cacheSize: this.translationService.cache.size,
            mappingTables: this.mappingManager.mappingTables.size,
            readerModes: this.stateManager.readerModes.size,
            uptime: Date.now()
        };
    }

    // 保存设置
    async saveSettings(settings) {
        await browser.storage.local.set({ settings });
    }

    // 获取设置
    async getSettings() {
        const result = await browser.storage.local.get(['settings', 'api_keys']);

        return {
            settings: result.settings || {},
            api_keys: result.api_keys || {}
        };
    }


    // 显示欢迎页面
    showWelcomePage() {
        browser.tabs.create({
            url: browser.runtime.getURL('src/options/welcome.html')
        });
    }

    // 显示更新信息
    showUpdateInfo(previousVersion) {
        // 可以在这里显示更新通知
    }

    // 处理原文恢复
    async handleRestoreText(tabId) {
        try {
            // 在这里可以 perform any restoration tasks that might need backend assistance
            // Currently, the actual restoration happens in the content script, but we'll notify the popup

            logger.log('Handling restore text for tab:', tabId);

            return { message: 'Restore command acknowledged' };
        } catch (error) {
            console.error('Error handling restore text:', error);
            throw error;
        }
    }
}

// 导出测试用类（Jest 环境）
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        TranslationServiceManager,
        ExtensionStateManager,
        MappingTableManager,
        ContextMenuManager,
        ShortcutManager,
        BackgroundManager
    };
}

// 初始化后台管理器
const backgroundManager = new BackgroundManager();