package com.yumu.noveltranslator.service.pipeline;

import com.yumu.noveltranslator.dto.ConsistencyTranslationResult;
import com.yumu.noveltranslator.dto.RagTranslationResponse;
import com.yumu.noveltranslator.service.EntityConsistencyService;
import com.yumu.noveltranslator.service.RagTranslationService;
import com.yumu.noveltranslator.service.TranslationCacheService;
import com.yumu.noveltranslator.service.TranslationPostProcessingService;
import com.yumu.noveltranslator.service.UserLevelThrottledTranslationClient;
import com.yumu.noveltranslator.util.CacheKeyUtil;
import com.yumu.noveltranslator.util.ExternalResponseUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 统一翻译管线组件
 *
 * 四级翻译管线架构：
 * L1: 三级缓存查询（Caffeine → Redis → 数据库）
 * L2: RAG 语义匹配（向量相似度查询）
 * L3: 实体一致性翻译（术语表 + 占位符保护）
 * L4: 直译（Python/MTranServer 轮询）
 *
 * 所有翻译路径统一使用此组件，消除 TranslationService、
 * MultiAgentTranslationService、TranslationTaskService 中的重复管线逻辑。
 */
@Slf4j
public class TranslationPipeline {

    // 广告关键词检测列表
    private static final String[] AD_KEYWORDS = {
            "人工智能助手", "生成式人工智能", "体验生成式", "获取写作", "Gemini", "Google AI"
    };

    private final TranslationCacheService cacheService;
    private final RagTranslationService ragTranslationService;
    private final EntityConsistencyService entityConsistencyService;
    private final UserLevelThrottledTranslationClient translationClient;
    private final TranslationPostProcessingService postProcessingService;
    private final Long userId;
    private final String docId;

    /**
     * 创建翻译管线实例
     *
     * @param cacheService        三级缓存服务
     * @param ragTranslationService RAG 翻译服务
     * @param entityConsistencyService 实体一致性服务
     * @param translationClient   用户级限流翻译客户端
     * @param postProcessingService 翻译后处理服务
     * @param userId              用户 ID（可为 null，null 时跳过一致性翻译）
     * @param docId               文档 ID（可为 null，用于一致性翻译的实体缓存）
     */
    public TranslationPipeline(
            TranslationCacheService cacheService,
            RagTranslationService ragTranslationService,
            EntityConsistencyService entityConsistencyService,
            UserLevelThrottledTranslationClient translationClient,
            TranslationPostProcessingService postProcessingService,
            Long userId,
            String docId) {
        this.cacheService = cacheService;
        this.ragTranslationService = ragTranslationService;
        this.entityConsistencyService = entityConsistencyService;
        this.translationClient = translationClient;
        this.postProcessingService = postProcessingService;
        this.userId = userId;
        this.docId = docId;
    }

    /**
     * 执行完整四级翻译管线
     *
     * @param text       待翻译文本
     * @param targetLang 目标语言
     * @param engine     翻译引擎
     * @return 翻译结果，失败返回 null
     */
    public String execute(String text, String targetLang, String engine) {
        String cacheKey = CacheKeyUtil.buildCacheKey(text, targetLang, engine);

        // L1: 三级缓存查询
        String cached = cacheService.getCache(cacheKey);
        if (cached != null) {
            log.debug("Pipeline 缓存命中 [{}]", cacheKey.substring(0, Math.min(16, cacheKey.length())));
            return cached;
        }

        // L2: RAG 语义匹配
        RagTranslationResponse ragResult = ragTranslationService.searchSimilar(text, targetLang, engine);
        if (ragResult.isDirectHit()) {
            log.info("Pipeline RAG 直接命中，相似度: {}", ragResult.getSimilarity());
            String result = postProcessingService.fixUntranslatedChinese(text, ragResult.getTranslation(), targetLang, engine);
            cacheService.putCache(cacheKey, text, result, "auto", targetLang, engine);
            return result;
        }

        // L3: 实体一致性翻译（条件触发：userId 非 null 且文本长度超阈值）
        if (userId != null && entityConsistencyService.shouldUseConsistency(text)) {
            log.info("Pipeline 启用实体一致性翻译");
            ConsistencyTranslationResult consistencyResult =
                    entityConsistencyService.translateWithConsistency(text, targetLang, engine, userId, docId);
            if (consistencyResult.isConsistencyApplied() && consistencyResult.getTranslatedText() != null) {
                String result = postProcessingService.fixUntranslatedChinese(text, consistencyResult.getTranslatedText(), targetLang, engine);
                if (shouldCache(text, result)) {
                    cacheService.putCache(cacheKey, text, result, "auto", targetLang, engine);
                }
                ragTranslationService.storeTranslationMemory(text, result, targetLang, engine);
                return result;
            }
        }

        // L4: 直译
        String rawJson = translationClient.translate(text, targetLang, engine, false);
        String result = ExternalResponseUtil.extractDataField(rawJson);

        if (result == null) {
            log.warn("Pipeline L4 翻译失败，原始响应: {}", rawJson);
            return null;
        }

        if (!isValidTranslation(text, result)) {
            log.warn("Pipeline L4 翻译结果无效（广告关键词或长度异常）");
            return null;
        }

        // 后处理 + 缓存
        result = postProcessingService.fixUntranslatedChinese(text, result, targetLang, engine);
        if (shouldCache(text, result)) {
            cacheService.putCache(cacheKey, text, result, "auto", targetLang, engine);
            ragTranslationService.storeTranslationMemory(text, result, targetLang, engine);
        } else {
            log.debug("Pipeline 译文与原文一致，跳过缓存");
        }

        return result;
    }

    /**
     * 快速模式翻译管线（仅缓存 + 直译）
     * 跳过 RAG 和实体一致性，适用于网页翻译等高性能场景
     *
     * @param text       待翻译文本
     * @param targetLang 目标语言
     * @param engine     翻译引擎
     * @return 翻译结果，失败时返回原文
     */
    public String executeFast(String text, String targetLang, String engine) {
        return executeFast(text, targetLang, engine, false);
    }

    /**
     * 快速模式翻译管线（仅缓存 + 直译）
     *
     * @param text       待翻译文本
     * @param targetLang 目标语言
     * @param engine     翻译引擎
     * @param html       是否启用 HTML 翻译模式（仅对 MTranServer 有效）
     * @return 翻译结果，失败时返回原文
     */
    public String executeFast(String text, String targetLang, String engine, boolean html) {
        String cacheKey = CacheKeyUtil.buildCacheKey(text, targetLang, engine);

        // L1: 缓存查询
        String cached = cacheService.getCache(cacheKey);
        if (cached != null) {
            log.debug("Pipeline 快速模式缓存命中");
            return cached;
        }

        // L4: 直译（跳过 RAG 和一致性）
        try {
            String rawJson = translationClient.translate(text, targetLang, engine, false, html);
            String result = ExternalResponseUtil.extractDataField(rawJson);

            if (result != null && !result.isBlank()) {
                if (!isValidTranslation(text, result)) {
                    log.warn("Pipeline 快速模式翻译结果无效，返回原文");
                    return text;
                }
                result = postProcessingService.fixUntranslatedChinese(text, result, targetLang, engine);
                if (shouldCache(text, result)) {
                    cacheService.putCache(cacheKey, text, result, "auto", targetLang, engine);
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("Pipeline 快速模式翻译失败: {}", e.getMessage());
        }

        // 失败时返回原文
        log.warn("Pipeline 快速模式翻译结果为空，返回原文");
        return text;
    }

    /**
     * 判断是否应该缓存翻译结果
     * 仅当译文与原文不一致时才缓存
     */
    public static boolean shouldCache(String original, String translated) {
        if (original == null || translated == null) {
            return false;
        }
        String cleanOriginal = original.trim();
        String cleanTranslated = translated.trim();

        if (cleanOriginal.equals(cleanTranslated)) {
            return false;
        }
        if (cleanOriginal.equalsIgnoreCase(cleanTranslated)) {
            return false;
        }
        return true;
    }

    /**
     * 校验翻译结果是否有效
     * 检测非翻译内容（如广告文案、系统提示等）和长度异常
     */
    public static boolean isValidTranslation(String text, String result) {
        if (text == null || result == null) {
            return false;
        }
        // 检测明显的广告/系统提示关键词
        for (String keyword : AD_KEYWORDS) {
            if (result.contains(keyword)) {
                log.warn("翻译结果包含广告关键词：{}", keyword);
                return false;
            }
        }
        // 检测译文长度异常（超过原文 10 倍）
        if (result.length() > text.length() * 10) {
            log.warn("翻译结果长度异常：原文 {} 字符，译文 {} 字符", text.length(), result.length());
            return false;
        }
        return true;
    }
}
