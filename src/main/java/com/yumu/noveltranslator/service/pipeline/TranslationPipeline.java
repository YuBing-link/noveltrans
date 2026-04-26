package com.yumu.noveltranslator.service.pipeline;

import com.yumu.noveltranslator.dto.ConsistencyTranslationResult;
import com.yumu.noveltranslator.dto.RagTranslationResponse;
import com.yumu.noveltranslator.entity.Glossary;
import com.yumu.noveltranslator.service.EntityConsistencyService;
import com.yumu.noveltranslator.service.RagTranslationService;
import com.yumu.noveltranslator.service.TeamTranslationService;
import com.yumu.noveltranslator.service.TranslationCacheService;
import com.yumu.noveltranslator.service.TranslationPostProcessingService;
import com.yumu.noveltranslator.service.UserLevelThrottledTranslationClient;
import com.yumu.noveltranslator.util.CacheKeyUtil;
import com.yumu.noveltranslator.util.ExternalResponseUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    /** 文本超过此字符数时启用分段翻译 */
    private static final int SEGMENT_TRANSLATION_THRESHOLD = 5000;
    /** 分段翻译的目标段大小（字符数） */
    private static final int TRANSLATION_SEGMENT_SIZE = 3000;

    private final TranslationCacheService cacheService;
    private final RagTranslationService ragTranslationService;
    private final EntityConsistencyService entityConsistencyService;
    private final UserLevelThrottledTranslationClient translationClient;
    private final TranslationPostProcessingService postProcessingService;
    private final TeamTranslationService teamTranslationService;
    private final Long userId;
    private final String docId;

    /**
     * 创建翻译管线实例（标准模式，L4 走直译）
     */
    public TranslationPipeline(
            TranslationCacheService cacheService,
            RagTranslationService ragTranslationService,
            EntityConsistencyService entityConsistencyService,
            UserLevelThrottledTranslationClient translationClient,
            TranslationPostProcessingService postProcessingService,
            Long userId,
            String docId) {
        this(cacheService, ragTranslationService, entityConsistencyService, translationClient,
             postProcessingService, null, userId, docId);
    }

    /**
     * 创建翻译管线实例（支持团队模式，L4 可走 TeamTranslationService）
     *
     * @param teamTranslationService 团队翻译服务（可为 null，null 时 executeTeam 降级为标准直译）
     */
    public TranslationPipeline(
            TranslationCacheService cacheService,
            RagTranslationService ragTranslationService,
            EntityConsistencyService entityConsistencyService,
            UserLevelThrottledTranslationClient translationClient,
            TranslationPostProcessingService postProcessingService,
            TeamTranslationService teamTranslationService,
            Long userId,
            String docId) {
        this.cacheService = cacheService;
        this.ragTranslationService = ragTranslationService;
        this.entityConsistencyService = entityConsistencyService;
        this.translationClient = translationClient;
        this.postProcessingService = postProcessingService;
        this.teamTranslationService = teamTranslationService;
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
        // Check if segmentation is needed
        List<String> segments = splitTextForTranslation(text);

        if (segments.size() == 1) {
            // Short text: original single-pass flow
            return executeSegment(text, targetLang, engine);
        }

        // Long text: translate each segment and merge
        log.info("分段翻译: 原文{}字, 分为{}段", text.length(), segments.size());
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            String translated = executeSegment(segment, targetLang, engine);
            if (translated != null && !translated.isBlank()) {
                result.append(translated);
            } else {
                // Translation failed, keep original segment
                result.append(segment);
            }
            // Add paragraph separator between segments (but not after the last one)
            if (i < segments.size() - 1 && !result.toString().endsWith("\n")) {
                result.append("\n");
            }
        }

        return result.toString();
    }

    /**
     * 团队模式翻译管线（完整四级管线 + L4 走多 Agent 协作）
     *
     * L1: 缓存查询
     * L2: RAG 语义匹配
     * L3: 实体一致性（提取实体 + 占位符保护）
     * L4: TeamTranslationService 多 Agent 协作翻译
     *
     * @param text           待翻译文本
     * @param sourceLang     源语言
     * @param targetLang     目标语言
     * @param engine         翻译引擎
     * @param novelType      小说类型
     * @param glossaryTerms  术语表
     * @return 翻译结果，失败返回 null
     */
    public String executeTeam(
            String text,
            String sourceLang,
            String targetLang,
            String engine,
            String novelType,
            List<Glossary> glossaryTerms) {

        String cacheKey = CacheKeyUtil.buildCacheKey(text, targetLang, engine);

        // L1: 三级缓存查询
        String cached = cacheService.getCache(cacheKey);
        if (cached != null) {
            log.debug("Pipeline 团队模式缓存命中 [{}]", cacheKey.substring(0, Math.min(16, cacheKey.length())));
            return cached;
        }

        // L2: RAG 语义匹配
        RagTranslationResponse ragResult = ragTranslationService.searchSimilar(text, targetLang, engine);
        if (ragResult.isDirectHit()) {
            log.info("Pipeline 团队模式 RAG 直接命中，相似度: {}", ragResult.getSimilarity());
            String result = postProcessingService.fixUntranslatedChinese(text, ragResult.getTranslation(), targetLang, engine);
            cacheService.putCache(cacheKey, text, result, "auto", targetLang, engine, "");
            return result;
        }

        // L3: 实体一致性 + 占位符保护
        String textForTranslation = text;
        EntityConsistencyService.EntityMappingContext mappingContext = null;

        if (userId != null && entityConsistencyService.shouldUseConsistency(text)) {
            log.info("Pipeline 团队模式启用实体一致性");
            try {
                List<String> extractedEntities = entityConsistencyService.extractEntitiesSegmented(text, targetLang);
                if (!extractedEntities.isEmpty()) {
                    Map<String, String> entityTranslations = entityConsistencyService.translateEntities(
                            extractedEntities, targetLang);
                    mappingContext = entityConsistencyService.buildMapping(entityTranslations);
                    textForTranslation = entityConsistencyService.replaceEntitiesWithPlaceholders(text, mappingContext);
                }
            } catch (Exception e) {
                log.warn("团队模式实体一致性失败，降级为无占位符翻译: {}", e.getMessage());
            }
        }

        // L4: 多 Agent 协作翻译
        if (teamTranslationService == null) {
            log.warn("团队模式未初始化 TeamTranslationService，降级为标准直译");
            return executeSegment(text, targetLang, engine);
        }

        try {
            String translated = teamTranslationService.translateChapter(
                    textForTranslation, novelType, sourceLang, targetLang, glossaryTerms);

            if (translated == null || translated.trim().isEmpty()) {
                log.warn("Pipeline 团队模式 L4 翻译结果为空");
                return null;
            }

            // 还原占位符
            if (mappingContext != null) {
                translated = entityConsistencyService.restorePlaceholders(translated, mappingContext);
            }

            // 后处理 + 缓存
            translated = postProcessingService.fixUntranslatedChinese(text, translated, targetLang, engine);
            if (shouldCache(text, translated)) {
                cacheService.putCache(cacheKey, text, translated, sourceLang, targetLang, "ai-team", "");
                ragTranslationService.storeTranslationMemory(text, translated, targetLang, "ai-team");
            }

            return translated;
        } catch (Exception e) {
            log.warn("Pipeline 团队模式翻译失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 执行单段翻译流程
     */
    private String executeSegment(String text, String targetLang, String engine) {
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
            cacheService.putCache(cacheKey, text, result, "auto", targetLang, engine, "");
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
                    cacheService.putCache(cacheKey, text, result, "auto", targetLang, engine, "");
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
            cacheService.putCache(cacheKey, text, result, "auto", targetLang, engine, "");
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

        // L4: 直译（跳过 RAG 和一致性，快速模式直连 MTranServer）
        try {
            String rawJson = translationClient.translate(text, targetLang, engine, html, true);
            String result = ExternalResponseUtil.extractDataField(rawJson);

            if (result != null && !result.isBlank()) {
                if (!isValidTranslation(text, result)) {
                    log.warn("Pipeline 快速模式翻译结果无效，返回原文");
                    return text;
                }
                result = postProcessingService.fixUntranslatedChinese(text, result, targetLang, engine);
                if (shouldCache(text, result)) {
                    cacheService.putCache(cacheKey, text, result, "auto", targetLang, engine, "");
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
     * 将长文本按段落边界切分为多个片段，用于分段翻译
     *
     * 规则：
     * - 文本 <= 5000 字：不分段，返回单片段
     * - 文本 > 5000 字：按 3000 字分段
     * - 切分点在段落边界（\n\n）或句子边界（。！？\n）
     * - 不破坏原有文字完整性
     */
    private static List<String> splitTextForTranslation(String text) {
        if (text == null || text.length() <= SEGMENT_TRANSLATION_THRESHOLD) {
            return List.of(text != null ? text : "");
        }

        List<String> segments = new ArrayList<>();
        // Split at paragraph boundaries
        String[] paragraphs = text.split("(?<=\n\n)");
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            if (current.length() + para.length() > TRANSLATION_SEGMENT_SIZE && current.length() > 0) {
                String segment = current.toString();
                if (segment.length() > TRANSLATION_SEGMENT_SIZE * 1.5) {
                    segments.addAll(splitAtSentenceBoundaryForTranslation(segment));
                } else {
                    segments.add(segment);
                }
                current = new StringBuilder();
            }
            current.append(para);
        }

        if (current.length() > 0) {
            String remaining = current.toString();
            if (remaining.length() > TRANSLATION_SEGMENT_SIZE * 1.5) {
                segments.addAll(splitAtSentenceBoundaryForTranslation(remaining));
            } else {
                segments.add(remaining);
            }
        }

        if (segments.isEmpty()) {
            return List.of(text);
        }

        return segments;
    }

    /**
     * 在句子边界切分超长片段
     */
    private static List<String> splitAtSentenceBoundaryForTranslation(String text) {
        List<String> parts = new ArrayList<>();
        String[] sentences = text.split("(?<=[。！？\n])");
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            if (current.length() + sentence.length() > TRANSLATION_SEGMENT_SIZE && current.length() > 0) {
                parts.add(current.toString());
                current = new StringBuilder();
            }
            current.append(sentence);
        }

        if (current.length() > 0) {
            parts.add(current.toString());
        }

        return parts.isEmpty() ? List.of(text) : parts;
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
