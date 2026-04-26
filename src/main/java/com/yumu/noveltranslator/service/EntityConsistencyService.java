package com.yumu.noveltranslator.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.Glossary;
import com.yumu.noveltranslator.entity.UserPreference;
import com.yumu.noveltranslator.mapper.GlossaryMapper;
import com.yumu.noveltranslator.mapper.UserPreferenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 实体一致性翻译服务
 * 核心流程：提取实体 → 翻译实体 → 构建占位符映射 → 替换原文 → 翻译 → 还原占位符
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EntityConsistencyService {

    /** 文本超过此字符数才启用实体一致性 */
    private static final int MIN_TEXT_LENGTH = 500;

    /** 文本超过此字符数时分段提取实体 */
    private static final int SEGMENT_EXTRACTION_THRESHOLD = 5000;
    /** 分段提取的目标段大小（字符数） */
    private static final int ENTITY_SEGMENT_SIZE = 3000;

    /** 禁用代理的 ProxySelector，确保内部 Docker 服务直连 */
    private static final java.net.ProxySelector NO_PROXY_SELECTOR = new java.net.ProxySelector() {
        @Override
        public java.util.List<java.net.Proxy> select(java.net.URI uri) {
            return java.util.List.of(java.net.Proxy.NO_PROXY);
        }
        @Override
        public void connectFailed(java.net.URI uri, java.net.SocketAddress sa, java.io.IOException ioe) {}
    };

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .proxy(NO_PROXY_SELECTOR)
            .build();

    @Value("${translation.python.url:http://llm-engine:8000/translate}")
    private String pythonTranslateUrl;

    private final DocumentEntityCache documentEntityCache;
    private final GlossaryMapper glossaryMapper;
    private final UserPreferenceMapper userPreferenceMapper;

    /**
     * 判断是否需要启用实体一致性
     */
    public boolean shouldUseConsistency(String text) {
        return text != null && text.length() >= MIN_TEXT_LENGTH;
    }

    /**
     * 带实体一致性的翻译入口
     */
    public ConsistencyTranslationResult translateWithConsistency(
            String sourceText, String targetLang, String engine,
            Long userId, String documentId) {

        ConsistencyTranslationResult result = new ConsistencyTranslationResult();
        result.setConsistencyApplied(false);

        try {
            // 1. 先从文档缓存获取已有的实体映射
            Map<String, String> cachedEntities = documentEntityCache.getEntityMap(userId, documentId);

            // 2. 分段提取实体（长文本自动分段）
            List<String> extractedEntities = extractEntitiesSegmented(sourceText, targetLang);

            // 3. 合并术语库：如果用户启用了术语库，提取术语库中的译法
            Map<String, String> glossaryTerms = loadGlossaryTerms(userId, sourceText);
            if (!glossaryTerms.isEmpty()) {
                log.info("术语库匹配到 {} 个实体译法: {}", glossaryTerms.size(), glossaryTerms.keySet());
            }

            if (extractedEntities.isEmpty() && glossaryTerms.isEmpty()) {
                log.info("未提取到实体且术语库无匹配，使用常规翻译");
                return result;
            }
            log.info("提取到 {} 个实体: {}", extractedEntities.size(), extractedEntities);

            // 4. 过滤掉缓存中已有的实体，只翻译新增的
            // 术语库中已有的实体不需要 LLM 翻译
            Set<String> glossaryKeys = glossaryTerms.keySet();
            List<String> newEntities = extractedEntities.stream()
                    .filter(e -> !cachedEntities.containsKey(e) && !glossaryKeys.contains(e))
                    .toList();

            // 构建完整映射：缓存 + 术语库（优先级最高）
            Map<String, String> allTranslations = new LinkedHashMap<>(cachedEntities);
            allTranslations.putAll(glossaryTerms);  // 术语库覆盖缓存中的同名实体

            if (!newEntities.isEmpty()) {
                // 5. 翻译新增实体
                Map<String, String> newTranslations = translateEntities(newEntities, targetLang);
                allTranslations.putAll(newTranslations);
                log.info("翻译了 {} 个新增实体", newTranslations.size());
            }

            // 6. 去重 + 处理嵌套实体（保留最长匹配）
            Map<String, String> dedupedMap = deduplicateEntities(allTranslations, sourceText);

            // 7. 构建占位符映射
            EntityMappingContext context = buildMapping(dedupedMap);

            // 8. 原文中替换实体为占位符
            String textWithPlaceholders = replaceEntitiesWithPlaceholders(sourceText, context);

            // 9. 调用翻译（带占位符保护）
            String translatedWithPlaceholders = translateWithPlaceholders(
                    textWithPlaceholders, targetLang, engine);

            if (translatedWithPlaceholders == null || translatedWithPlaceholders.isBlank()) {
                log.error("带占位符翻译返回为空");
                return result;
            }

            // 10. 译文中将占位符替换为翻译后的实体名
            String finalTranslated = restorePlaceholders(translatedWithPlaceholders, context);

            // 11. 合并到文档缓存（不包含术语库，术语库始终来自 DB）
            Map<String, String> nonGlossaryMap = new LinkedHashMap<>(dedupedMap);
            glossaryKeys.forEach(nonGlossaryMap::remove);
            documentEntityCache.mergeEntityMap(userId, documentId, nonGlossaryMap);

            // 构建返回结果
            result.setTranslatedText(finalTranslated);
            result.setMappings(context.mappings);
            result.setOriginalWithPlaceholders(textWithPlaceholders);
            result.setConsistencyApplied(true);

            log.info("实体一致性翻译完成: 实体数={}, 原文长度={}, 译文长度={}",
                    context.mappings.size(), sourceText.length(), finalTranslated.length());

            return result;

        } catch (Exception e) {
            log.error("实体一致性翻译失败: {}，降级为常规翻译", e.getMessage(), e);
            return result;
        }
    }

    // ==================== 文本分段与分段实体提取 ====================

    /**
     * 将长文本按段落边界切分为多个片段，用于分段实体提取
     *
     * 规则：
     * - 文本 <= SEGMENT_EXTRACTION_THRESHOLD (5000) 字：不分段，返回单片段
     * - 文本 > 5000 字：按 ENTITY_SEGMENT_SIZE (3000) 字分段
     * - 切分点在段落边界（\n\n）或句子边界（。！？\n）
     * - 不破坏原有文字完整性
     *
     * @param text 原文
     * @return 分段后的文本列表
     */
    public List<String> splitTextForEntityExtraction(String text) {
        if (text == null || text.length() <= SEGMENT_EXTRACTION_THRESHOLD) {
            return List.of(text != null ? text : "");
        }

        List<String> segments = new ArrayList<>();
        String[] paragraphs = text.split("(?<=\n\n)");  // 保留分隔符
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            if (current.length() + para.length() > ENTITY_SEGMENT_SIZE && current.length() > 0) {
                // 当前段已满，检查是否能在句子边界切分
                String segment = current.toString();
                if (segment.length() > ENTITY_SEGMENT_SIZE * 1.5) {
                    // 如果当前段远超目标大小，尝试在句子边界回退切分
                    segments.addAll(splitAtSentenceBoundary(segment));
                } else {
                    segments.add(segment);
                }
                current = new StringBuilder();
            }
            current.append(para);
        }

        // 处理剩余内容
        if (current.length() > 0) {
            String remaining = current.toString();
            if (remaining.length() > ENTITY_SEGMENT_SIZE * 1.5) {
                segments.addAll(splitAtSentenceBoundary(remaining));
            } else {
                segments.add(remaining);
            }
        }

        // 如果分段后只剩一个空段，返回原文
        if (segments.isEmpty()) {
            return List.of(text);
        }

        log.info("文本分段: 原文{}字, 分为{}段", text.length(), segments.size());
        return segments;
    }

    /**
     * 在句子边界切分超长片段
     * 句子边界：。！？\n
     */
    private List<String> splitAtSentenceBoundary(String text) {
        List<String> parts = new ArrayList<>();
        // 按句子边界切分
        String[] sentences = text.split("(?<=[。！？\n])");
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            if (current.length() + sentence.length() > ENTITY_SEGMENT_SIZE && current.length() > 0) {
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
     * 分段提取实体（适用于长文本）
     *
     * 逻辑：
     * 1. 先调用 splitTextForEntityExtraction 分段
     * 2. 对每个分段调用 Python /extract-entities
     * 3. 合并所有分段的实体结果（去重）
     *
     * @param text       原文
     * @param targetLang 目标语言
     * @return 去重后的实体列表
     */
    public List<String> extractEntitiesSegmented(String text, String targetLang) {
        List<String> segments = splitTextForEntityExtraction(text);

        if (segments.size() == 1) {
            // 不分段，直接提取
            try {
                return extractEntities(segments.get(0), targetLang);
            } catch (Exception e) {
                log.warn("实体提取失败: {}", e.getMessage());
                return Collections.emptyList();
            }
        }

        Set<String> allEntities = new LinkedHashSet<>();
        for (int i = 0; i < segments.size(); i++) {
            try {
                List<String> segmentEntities = extractEntities(segments.get(i), targetLang);
                allEntities.addAll(segmentEntities);
                log.debug("实体提取: 第{}/{}段, 提取{}个实体", i + 1, segments.size(), segmentEntities.size());
            } catch (Exception e) {
                log.warn("实体提取: 第{}/{}段失败: {}", i + 1, segments.size(), e.getMessage());
            }
        }

        List<String> result = new ArrayList<>(allEntities);
        log.info("分段实体提取完成: 原文{}字, {}段, 共{}个实体", text.length(), segments.size(), result.size());
        return result;
    }

    // ==================== 原有方法 ====================

    /**
     * 调用 Python /extract-entities 提取实体（公开方法，供外部调用）
     */
    public List<String> extractEntities(String text, String targetLang) throws Exception {
        String baseUrl = pythonTranslateUrl.replace("/translate", "");
        String url = baseUrl + "/extract-entities";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text);
        body.put("source_lang", "auto");
        body.put("target_lang", targetLang);

        String jsonBody = JSON.toJSONString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = sendWithRetry(request, 2);
        } catch (Exception e) {
            log.warn("实体提取失败，跳过一致性处理: {}", e.getMessage());
            return Collections.emptyList();
        }
        String responseBody = response.body();
        if (responseBody == null || responseBody.isBlank()) {
            log.warn("Python服务返回空响应体, status={}, 跳过实体提取", response.statusCode());
            return Collections.emptyList();
        }

        try {
            EntityExtractionResponse result = JSON.parseObject(responseBody, EntityExtractionResponse.class);
            return result.getEntities() != null ? result.getEntities() : Collections.emptyList();
        } catch (Exception e) {
            log.error("实体提取 JSON 解析失败: 响应前200字符={}", responseBody.length() > 200 ? responseBody.substring(0, 200) : responseBody);
            throw e;
        }
    }

    /**
     * 调用 Python /translate-entities 批量翻译实体（公开方法，供外部调用）
     */
    public Map<String, String> translateEntities(List<String> entities, String targetLang) throws Exception {
        String baseUrl = pythonTranslateUrl.replace("/translate", "");
        String url = baseUrl + "/translate-entities";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entities", entities);
        body.put("source_lang", "auto");
        body.put("target_lang", targetLang);

        String jsonBody = JSON.toJSONString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = sendWithRetry(request, 2);
        String responseBody = response.body();
        if (responseBody == null || responseBody.isBlank()) {
            throw new RuntimeException("Python服务返回空响应体, status=" + response.statusCode());
        }

        try {
            EntityTranslationResponse result = JSON.parseObject(responseBody, EntityTranslationResponse.class);
            return result.getTranslations() != null ? result.getTranslations() : Collections.emptyMap();
        } catch (Exception e) {
            log.error("实体翻译 JSON 解析失败: 响应前200字符={}", responseBody.length() > 200 ? responseBody.substring(0, 200) : responseBody);
            throw e;
        }
    }

    /**
     * 调用 Python /translate-with-placeholders 翻译带占位符的文本
     */
    private String translateWithPlaceholders(String text, String targetLang, String engine) throws Exception {
        String baseUrl = pythonTranslateUrl.replace("/translate", "");
        String url = baseUrl + "/translate-with-placeholders";
        log.info("[一致性翻译] URL={}, text length={}", url, text.length());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text);
        body.put("target_lang", targetLang);
        body.put("engine", engine != null ? engine : "openai");
        body.put("fallback", true);

        String jsonBody = JSON.toJSONString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        log.info("[一致性翻译] 发送请求到 {}", request.uri());
        HttpResponse<String> response = sendWithRetry(request, 2);
        String responseBody = response.body();
        log.info("[一致性翻译] 响应: status={}, body length={}", response.statusCode(), responseBody != null ? responseBody.length() : 0);

        if (responseBody == null || responseBody.isBlank()) {
            throw new RuntimeException("Python服务返回空响应体, status=" + response.statusCode());
        }

        try {
            JSONObject json = JSON.parseObject(responseBody);
            return json.getString("data");
        } catch (Exception e) {
            log.error("占位符翻译 JSON 解析失败: 响应前200字符={}", responseBody.length() > 200 ? responseBody.substring(0, 200) : responseBody);
            throw e;
        }
    }

    /**
     * 去重 + 处理嵌套实体（保留最长匹配）
     */
    private Map<String, String> deduplicateEntities(Map<String, String> translations, String sourceText) {
        // 按实体长度降序排序
        List<Map.Entry<String, String>> sorted = new ArrayList<>(translations.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));

        Map<String, String> deduped = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : sorted) {
            String entity = entry.getKey();
            // 检查该实体是否被已添加的更长实体包含
            boolean isSubEntity = false;
            for (String existing : deduped.keySet()) {
                if (existing.contains(entity) && existing.length() > entity.length()) {
                    isSubEntity = true;
                    break;
                }
            }
            if (!isSubEntity && sourceText.contains(entity)) {
                deduped.put(entity, entry.getValue());
            }
        }

        return deduped;
    }

    /**
     * 构建占位符映射上下文（公开方法，供外部调用）
     */
    public EntityMappingContext buildMapping(Map<String, String> entityTranslations) {
        List<EntityMapping> mappings = new ArrayList<>();
        Map<String, String> entityToPlaceholder = new LinkedHashMap<>();
        int index = 1;

        for (Map.Entry<String, String> entry : entityTranslations.entrySet()) {
            String placeholder = "[{" + index + "}]";
            EntityMapping mapping = EntityMapping.builder()
                    .sourceText(entry.getKey())
                    .translatedText(entry.getValue())
                    .placeholder(placeholder)
                    .index(index)
                    .build();
            mappings.add(mapping);
            entityToPlaceholder.put(entry.getKey(), placeholder);
            index++;
        }

        return new EntityMappingContext(mappings, entityToPlaceholder);
    }

    /**
     * 原文中替换实体为占位符（公开方法，供外部调用）
     * 注意：按实体长度降序替换，避免短实体先替换导致长实体无法匹配
     */
    public String replaceEntitiesWithPlaceholders(String text, EntityMappingContext context) {
        String result = text;
        // 按实体长度降序
        List<String> sortedEntities = context.entityToPlaceholder.keySet().stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toList();

        for (String entity : sortedEntities) {
            String placeholder = context.entityToPlaceholder.get(entity);
            // 使用 String.replace 替换所有出现
            result = result.replace(entity, placeholder);
        }

        return result;
    }

    /**
     * 加载用户术语库中在原文中出现的实体译法
     * 术语库优先级高于 LLM 自动翻译
     */
    private Map<String, String> loadGlossaryTerms(Long userId, String sourceText) {
        Map<String, String> terms = new LinkedHashMap<>();

        // 检查用户是否启用了术语库
        try {
            LambdaQueryWrapper<UserPreference> prefQuery = new LambdaQueryWrapper<>();
            prefQuery.eq(UserPreference::getUserId, userId);
            UserPreference pref = userPreferenceMapper.selectOne(prefQuery);
            if (pref == null || !Boolean.TRUE.equals(pref.getEnableGlossary())) {
                log.debug("用户未启用术语库或偏好不存在");
                return terms;
            }
        } catch (Exception e) {
            log.warn("查询用户偏好失败，术语库注入跳过: {}", e.getMessage());
            return terms;
        }

        // 查询用户的所有术语条目
        try {
            LambdaQueryWrapper<Glossary> query = new LambdaQueryWrapper<>();
            query.eq(Glossary::getUserId, userId);
            List<Glossary> allTerms = glossaryMapper.selectList(query);

            // 只在原文中实际出现的术语
            for (Glossary term : allTerms) {
                if (term.getSourceWord() != null && sourceText.contains(term.getSourceWord())) {
                    terms.put(term.getSourceWord(), term.getTargetWord());
                }
            }
        } catch (Exception e) {
            log.warn("查询术语库失败: {}", e.getMessage());
        }

        return terms;
    }

    /**
     * 译文中将占位符替换为翻译后的实体名（公开方法，供外部调用）
     * 兼容 LLM 破坏占位符格式的情况：[{1}] → [1]
     */
    public String restorePlaceholders(String text, EntityMappingContext context) {
        String result = text;
        for (EntityMapping mapping : context.mappings) {
            String placeholder = mapping.getPlaceholder(); // [{N}]
            // 优先尝试标准格式还原
            if (result.contains(placeholder)) {
                result = result.replace(placeholder, mapping.getTranslatedText());
            } else {
                // LLM 可能把 [{N}] 破坏成 [N]，使用正则回退
                String degradedPattern = "\\[" + mapping.getIndex() + "\\]";
                if (result.contains("[" + mapping.getIndex() + "]")) {
                    result = result.replaceAll(degradedPattern, Matcher.quoteReplacement(mapping.getTranslatedText()));
                    log.warn("占位符被 LLM 破坏，使用回退还原: {} → {}", placeholder, mapping.getTranslatedText());
                } else {
                    log.warn("占位符还原失败: 未找到 {} (索引={}) 在译文中", placeholder, mapping.getIndex());
                }
            }
        }
        return result;
    }

    /**
     * 带重试的 HTTP 请求发送
     */
    private HttpResponse<String> sendWithRetry(HttpRequest request, int maxRetries) throws Exception {
        HttpResponse<String> response = null;
        for (int i = 0; i <= maxRetries; i++) {
            try {
                response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return response;
                }
                log.warn("HTTP 请求失败 (尝试 {}/{}): status={}", i + 1, maxRetries + 1, response.statusCode());
            } catch (Exception e) {
                log.warn("HTTP 请求异常 (尝试 {}/{}): {}", i + 1, maxRetries + 1, e.getMessage());
            }
            if (i < maxRetries) {
                Thread.sleep(1000L * (i + 1));
            }
        }
        if (response != null) {
            return response;
        }
        throw new RuntimeException("HTTP 请求重试 " + maxRetries + " 次后仍失败");
    }

    /**
     * 占位符映射上下文（公开静态类，供外部使用）
     */
    public record EntityMappingContext(
            List<EntityMapping> mappings,
            Map<String, String> entityToPlaceholder
    ) {}
}
