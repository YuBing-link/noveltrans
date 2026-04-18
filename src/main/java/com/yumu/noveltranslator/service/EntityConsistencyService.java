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

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
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

            // 2. 调用 Python 服务提取实体
            List<String> extractedEntities = extractEntities(sourceText, targetLang);

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

    /**
     * 调用 Python /extract-entities 提取实体
     */
    private List<String> extractEntities(String text, String targetLang) throws Exception {
        String baseUrl = pythonTranslateUrl.replace("/translate", "");
        String url = baseUrl + "/extract-entities";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text);
        body.put("source_lang", "auto");
        body.put("target_lang", targetLang);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("实体提取 HTTP 错误: " + response.statusCode());
        }

        EntityExtractionResponse result = JSON.parseObject(response.body(), EntityExtractionResponse.class);
        return result.getEntities() != null ? result.getEntities() : Collections.emptyList();
    }

    /**
     * 调用 Python /translate-entities 批量翻译实体
     */
    private Map<String, String> translateEntities(List<String> entities, String targetLang) throws Exception {
        String baseUrl = pythonTranslateUrl.replace("/translate", "");
        String url = baseUrl + "/translate-entities";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entities", entities);
        body.put("source_lang", "auto");
        body.put("target_lang", targetLang);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("实体翻译 HTTP 错误: " + response.statusCode());
        }

        EntityTranslationResponse result = JSON.parseObject(response.body(), EntityTranslationResponse.class);
        return result.getTranslations() != null ? result.getTranslations() : Collections.emptyMap();
    }

    /**
     * 调用 Python /translate-with-placeholders 翻译带占位符的文本
     */
    private String translateWithPlaceholders(String text, String targetLang, String engine) throws Exception {
        String baseUrl = pythonTranslateUrl.replace("/translate", "");
        String url = baseUrl + "/translate-with-placeholders";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text);
        body.put("target_lang", targetLang);
        body.put("engine", engine != null ? engine : "openai");
        body.put("fallback", true);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("占位符翻译 HTTP 错误: " + response.statusCode());
        }

        JSONObject json = JSON.parseObject(response.body());
        return json.getString("data");
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
     * 构建占位符映射上下文
     */
    private EntityMappingContext buildMapping(Map<String, String> entityTranslations) {
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
     * 原文中替换实体为占位符
     * 注意：按实体长度降序替换，避免短实体先替换导致长实体无法匹配
     */
    private String replaceEntitiesWithPlaceholders(String text, EntityMappingContext context) {
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

            // 只保留在原文中实际出现的术语
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
     * 译文中将占位符替换为翻译后的实体名
     */
    private String restorePlaceholders(String text, EntityMappingContext context) {
        String result = text;
        for (EntityMapping mapping : context.mappings) {
            result = result.replace(mapping.getPlaceholder(), mapping.getTranslatedText());
        }
        return result;
    }

    /**
     * 占位符映射上下文
     */
    private record EntityMappingContext(
            List<EntityMapping> mappings,
            Map<String, String> entityToPlaceholder
    ) {}
}
