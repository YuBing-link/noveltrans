// src/lib/config.js - 统一配置文件
const GlobalConfig = {
    // 后端基础路径
    // TODO: 生产环境部署时替换为实际的服务器地址
    API_BASE_URL: 'http://localhost:7341',

    // 三种翻译模式的不同 API 端点（参照 API_ENDPOINTS.md）
    TRANSLATION_MODES: {
        // 整个网页翻译 (content.js - 批量翻译整页)
        WEBPAGE: {
            name: '整个网页翻译',
            endpoint: '/v1/translate/webpage',
            description: '批量翻译整个网页，保留 DOM 结构'
        },
        // 阅读器翻译 (read.js - 阅读模式中的文章翻译)
        READER: {
            name: '阅读器翻译',
            endpoint: '/v1/translate/reader',
            description: '阅读器模式中的整篇文章翻译'
        },
        // 选中翻译 (selection.js - 鼠标选中文本翻译)
        SELECTION: {
            name: '选中翻译',
            endpoint: '/v1/translate/selection',
            description: '智能选中按钮触发的翻译'
        }
    },

    // 完整的 API URL 生成函数
    getApiUrl: function(mode) {
        const modeConfig = this.TRANSLATION_MODES[mode];
        if (!modeConfig) {
            console.error(`未知的翻译模式：${mode}`);
            return this.API_BASE_URL + '/translate/quick';
        }
        return this.API_BASE_URL + modeConfig.endpoint;
    },

    // 支持的翻译引擎（常量）
    ENGINES: {
        GOOGLE: 'google',
        DEEPL: 'deepl',
        OPENAI: 'openai',
        DEEPSEEK: 'deepseek',
        BAIDU: 'baidu',
        YOUDAO: 'youdao',
        MICROSOFT: 'microsoft'
    },

    // 翻译引擎详细信息配置
    TRANSLATION_ENGINES: {
        google: {
            id: 'google',
            name: 'Google 翻译',
            icon: 'ri-google-fill',
            color: '#4285f4'
        },
        deepl: {
            id: 'deepl',
            name: 'DeepL',
            icon: 'ri-translate',
            color: '#0f2b46'
        },
        openai: {
            id: 'openai',
            name: 'OpenAI (GPT)',
            icon: 'ri-openai-fill',
            color: '#10a37f'
        },
        deepseek: {
            id: 'deepseek',
            name: 'DeepSeek',
            icon: 'ri-robot-2-line',
            color: '#3b82f6'
        },
        baidu: {
            id: 'baidu',
            name: '百度翻译',
            icon: 'ri-baidu-fill',
            color: '#2932e1'
        },
        youdao: {
            id: 'youdao',
            name: '有道翻译',
            icon: 'ri-translate-2',
            color: '#e93b3b'
        },
        microsoft: {
            id: 'microsoft',
            name: 'Microsoft 翻译',
            icon: 'ri-microsoft-fill',
            color: '#00a4ef'
        }
    },

    // 默认语言配置
    DEFAULT_SETTINGS: {
        engine: 'google',
        source_lang: 'auto',
        target_lang: 'zh',
        bilingual: false
    },

    // API 请求超时时间（毫秒）
    API_TIMEOUT: 30000,

    // 获取翻译引擎配置
    getEngineConfig: function(engine) {
        return this.TRANSLATION_ENGINES[engine] || this.TRANSLATION_ENGINES.google;
    },

    // 获取所有支持的翻译引擎
    getAllEngines: function() {
        return Object.values(this.TRANSLATION_ENGINES);
    },

    // 构建翻译 API 请求体 - 严格按照接口文档格式
    buildTranslationRequestBody: function(mode, data, engine) {
        const defaultEngine = engine || this.DEFAULT_SETTINGS.engine;
        const sourceLang = data.sourceLang || this.DEFAULT_SETTINGS.source_lang;
        const targetLang = data.targetLang || this.DEFAULT_SETTINGS.target_lang;

        switch (mode) {
            case 'WEBPAGE':
                return {
                    targetLang: targetLang,
                    sourceLang: sourceLang,
                    engine: defaultEngine,
                    fastMode: data.fastMode !== undefined ? data.fastMode : true,
                    textRegistry: data.textRegistry || []
                };

            case 'READER':
                return {
                    content: data.content || '',
                    targetLang: targetLang,
                    sourceLang: sourceLang,
                    engine: defaultEngine
                };

            case 'SELECTION':
                return {
                    sourceLang: sourceLang,
                    targetLang: targetLang,
                    engine: defaultEngine,
                    context: data.text || data.context || ''
                };

            case 'QUICK':
            default:
                return {
                    engine: defaultEngine,
                    sourceLang: sourceLang,
                    targetLang: targetLang,
                    text: data.text,
                    texts: data.texts,
                    bilingual: data.bilingual || false
                };
        }
    },

    // 统一的后端 API 调用函数
    callBackendAPI: async function(mode, data, engine = null, apiKey = null) {
        const apiUrl = this.getApiUrl(mode);
        const requestBody = this.buildTranslationRequestBody(mode, data, engine);

        const headers = {
            'Content-Type': 'application/json'
        };

        if (apiKey) {
            headers['Authorization'] = `Bearer ${apiKey}`;
        }

        headers['X-Translation-Engine'] = requestBody.engine;

        let timeoutId;

        try {
            const controller = new AbortController();
            timeoutId = setTimeout(() => controller.abort(), 30000);

            const response = await fetch(apiUrl, {
                method: 'POST',
                headers: headers,
                body: JSON.stringify(requestBody),
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`API 错误 (${response.status}): ${errorText || response.statusText}`);
            }

            const result = await response.json();
            return {
                success: result.success !== false,
                data: result,
                engine: requestBody.engine,
                mode: mode,
                timestamp: Date.now()
            };
        } catch (error) {
            if (timeoutId) {
                clearTimeout(timeoutId);
            }

            if (error.name === 'AbortError') {
                throw new Error(`API 请求超时：服务器无响应，请检查后端服务是否运行正常`);
            }

            if (error.name === 'TypeError' && error.message.includes('fetch')) {
                throw new Error(`网络错误：无法连接到翻译服务，请检查后端服务是否在 ${this.API_BASE_URL} 运行`);
            }
            throw error;
        }
    },

    // 流式 API 调用函数 - 用于网页翻译模式（SSE Server-Sent Events）
    callBackendAPIStream: async function(mode, data, engine = null, apiKey = null, onTranslationChunk = null, onComplete = null, onError = null) {
        const apiUrl = this.getApiUrl(mode);
        const requestBody = this.buildTranslationRequestBody(mode, data, engine);

        const headers = {
            'Content-Type': 'application/json',
            'Accept': 'text/event-stream'
        };

        if (apiKey) {
            headers['Authorization'] = `Bearer ${apiKey}`;
        }

        headers['X-Translation-Engine'] = requestBody.engine;

        let timeoutId;
        let abortController;

        try {
            abortController = new AbortController();
            timeoutId = setTimeout(() => abortController.abort(), 120000);

            const response = await fetch(apiUrl, {
                method: 'POST',
                headers: headers,
                body: JSON.stringify(requestBody),
                signal: abortController.signal
            });

            clearTimeout(timeoutId);

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`API 错误 (${response.status}): ${errorText || response.statusText}`);
            }

            const contentType = response.headers.get('content-type');

            if (!contentType || !contentType.includes('text/event-stream')) {
                const result = await response.json();
                if (onComplete) {
                    onComplete(result);
                }
                return result;
            }

            const reader = response.body.getReader();
            const decoder = new TextDecoder('utf-8');
            let buffer = '';
            let translations = [];
            let chunkCount = 0;

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n');
                buffer = lines.pop() || '';

                for (const line of lines) {
                    if (!line.trim() || !line.startsWith('data:')) continue;

                    const data = line.slice(line.indexOf('{')).trim();

                    if (data === '[DONE]') {
                        if (onComplete) {
                            onComplete({
                                success: true,
                                translations: translations,
                                engine: requestBody.engine,
                                mode: mode
                            });
                        }
                        return { success: true, translations, engine: requestBody.engine };
                    }

                    if (data.startsWith('ERROR:')) {
                        const errorMsg = data.slice(6).trim();
                        if (onError) onError(new Error(errorMsg));
                        throw new Error(`翻译错误：${errorMsg}`);
                    }

                    try {
                        const result = JSON.parse(data);
                        const { textId, original, translation } = result;
                        chunkCount++;
                        translations.push({ textId, original, translation });

                        if (onTranslationChunk) {
                            onTranslationChunk({ textId, original, translation });
                        }
                    } catch (parseError) {
                        // 跳过无法解析的行
                    }
                }
            }

            if (onComplete) {
                onComplete({
                    success: true,
                    translations: translations,
                    engine: requestBody.engine,
                    mode: mode
                });
            }
            return { success: true, translations, engine: requestBody.engine };
        } catch (error) {
            if (timeoutId) {
                clearTimeout(timeoutId);
            }

            if (error.name === 'AbortError') {
                if (onError) onError(error);
                return { success: false, error: '请求已取消' };
            }

            if (onError) onError(error);
            throw error;
        }
    },

    // API 连接验证函数
    verifyAPIConnection: async function() {
        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);

            const response = await fetch(this.API_BASE_URL + this.TRANSLATION_MODES.SELECTION.endpoint, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    sourceLang: 'auto',
                    targetLang: 'zh',
                    engine: 'google',
                    context: 'test'
                }),
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (response.status === 400 || response.ok) {
                console.log('✅ API 连接正常 - 后端服务运行中');
                return true;
            }

            console.warn(`⚠️ API 返回异常状态码：${response.status}`);
            return false;
        } catch (error) {
            console.error('❌ API 连接失败:', error.message);
            return false;
        }
    }
};

// 语言配置（带国旗 emoji）
const languages = [
    { code: 'zh', name: '简体中文', flag: '🇨🇳' },
    { code: 'en', name: 'English', flag: '🇬🇧' },
    { code: 'ja', name: '日本語', flag: '🇯🇵' },
    { code: 'ko', name: '한국어', flag: '🇰🇷' },
    { code: 'fr', name: 'Français', flag: '🇫🇷' },
    { code: 'de', name: 'Deutsch', flag: '🇩🇪' },
    { code: 'es', name: 'Español', flag: '🇪🇸' },
    { code: 'ru', name: 'Русский', flag: '🇷🇺' },
    { code: 'pt', name: 'Português', flag: '🇵🇹' },
    { code: 'it', name: 'Italiano', flag: '🇮🇹' },
    { code: 'nl', name: 'Nederlands', flag: '🇳🇱' },
    { code: 'pl', name: 'Polski', flag: '🇵🇱' },
    { code: 'tr', name: 'Türkçe', flag: '🇹🇷' },
    { code: 'th', name: 'ไทย', flag: '🇹🇭' },
    { code: 'vi', name: 'Tiếng Việt', flag: '🇻🇳' },
    { code: 'ar', name: 'العربية', flag: '🇸🇦' },
    { code: 'hi', name: 'हिन्दी', flag: '🇮🇳' },
    { code: 'id', name: 'Bahasa Indonesia', flag: '🇮🇩' },
    { code: 'ms', name: 'Bahasa Melayu', flag: '🇲🇾' },
    { code: 'sv', name: 'Svenska', flag: '🇸🇪' },
    { code: 'no', name: 'Norsk', flag: '🇳🇴' },
    { code: 'da', name: 'Dansk', flag: '🇩🇰' },
    { code: 'fi', name: 'Suomi', flag: '🇫🇮' },
    { code: 'cs', name: 'Čeština', flag: '🇨🇿' },
    { code: 'ro', name: 'Română', flag: '🇷🇴' },
    { code: 'el', name: 'Ελληνικά', flag: '🇬🇷' },
    { code: 'hu', name: 'Magyar', flag: '🇭🇺' },
    { code: 'uk', name: 'Українська', flag: '🇺🇦' },
    { code: 'fa', name: 'فارسی', flag: '🇮🇷' }
];

// 获取国旗 emoji
function getFlagEmoji(countryCode) {
    const codePoints = countryCode
        .toUpperCase()
        .split('')
        .map(char => 127397 + char.charCodeAt());
    return String.fromCodePoint(...codePoints);
}

// 导出 GlobalConfig（兼容 browser extension 环境）
if (typeof self !== 'undefined') {
    self.GlobalConfig = GlobalConfig;
}

// 导出 CommonJS（Jest 测试环境）
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { GlobalConfig, languages, getFlagEmoji };
}
