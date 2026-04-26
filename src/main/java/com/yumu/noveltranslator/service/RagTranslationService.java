package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.RagTranslationResponse;
import com.yumu.noveltranslator.entity.TranslationMemory;
import com.yumu.noveltranslator.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/**
 * RAG 翻译服务
 * 核心查询链路：生成向量 → Redis KNN 查询（按 user_id 过滤） → 相似度决策
 * Redis 失败时降级为 MySQL 余弦相似度计算
 * 翻译完成后自动存储到翻译记忆
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RagTranslationService {

    private final EmbeddingService embeddingService;
    private final TranslationMemoryService translationMemoryService;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${embedding.provider:openai}")
    private String provider;

    @Value("${embedding.rag.direct-hit-threshold:0.85}")
    private double directHitThreshold;

    @Value("${embedding.rag.reference-threshold:0.5}")
    private double referenceThreshold;

    @Value("${embedding.rag.knn-top-k:5}")
    private int knnTopK;

    @Value("${embedding.rag.fallback-mysql-limit:20}")
    private int fallbackMysqlLimit;

    /**
     * 查询相似翻译记忆
     */
    public RagTranslationResponse searchSimilar(String sourceText, String targetLang, String engine) {
        if (sourceText == null || sourceText.isBlank()) {
            return buildEmptyResponse();
        }

        Long userId = SecurityUtil.getCurrentUserId().orElse(null);
        if (userId == null) {
            return buildEmptyResponse();
        }

        try {
            // 1. 生成向量
            float[] queryVector = embeddingService.embed(sourceText);
            if (queryVector.length == 0) {
                return buildEmptyResponse();
            }

            // 2. Redis FT.SEARCH KNN 查询（按用户过滤）
            List<RagTranslationResponse.RagMatch> matches = searchInRedis(queryVector, userId, targetLang);

            if (matches.isEmpty()) {
                // 3. Redis 查询失败或无结果，尝试 MySQL 降级
                matches = searchFallback(queryVector, userId, targetLang);
            }

            if (matches.isEmpty()) {
                return buildEmptyResponse();
            }

            // 4. 构建响应
            RagTranslationResponse response = new RagTranslationResponse();
            response.setMatches(matches);

            RagTranslationResponse.RagMatch best = matches.get(0);
            if (best.getSimilarity() >= directHitThreshold) {
                response.setDirectHit(true);
                response.setTranslation(best.getTargetText());
                response.setSimilarity(best.getSimilarity());

                // 增加使用计数
                translationMemoryService.incrementUsage(best.getMemoryId());
                log.info("RAG 直接命中: similarity={}, memoryId={}", best.getSimilarity(), best.getMemoryId());
            } else if (best.getSimilarity() >= referenceThreshold) {
                response.setDirectHit(false);
                response.setSimilarity(best.getSimilarity());
                log.info("RAG 提供参考: similarity={}", best.getSimilarity());
            }

            return response;
        } catch (Exception e) {
            log.error("RAG 查询失败: {}", e.getMessage(), e);
            return buildEmptyResponse();
        }
    }

    /**
     * 查询相似翻译记忆（指定 userId，用于后台任务等非 HTTP 上下文场景）
     */
    public RagTranslationResponse searchSimilarWithUser(
            String sourceText, String targetLang, String engine, Long userId) {
        if (sourceText == null || sourceText.isBlank() || userId == null) {
            return buildEmptyResponse();
        }

        try {
            // 1. 生成向量
            float[] queryVector = embeddingService.embed(sourceText);
            if (queryVector.length == 0) {
                return buildEmptyResponse();
            }

            // 2. Redis KNN 查询
            List<RagTranslationResponse.RagMatch> matches = searchInRedis(queryVector, userId, targetLang);

            if (matches.isEmpty()) {
                matches = searchFallback(queryVector, userId, targetLang);
            }

            if (matches.isEmpty()) {
                return buildEmptyResponse();
            }

            RagTranslationResponse response = new RagTranslationResponse();
            response.setMatches(matches);

            RagTranslationResponse.RagMatch best = matches.get(0);
            if (best.getSimilarity() >= directHitThreshold) {
                response.setDirectHit(true);
                response.setTranslation(best.getTargetText());
                response.setSimilarity(best.getSimilarity());
                translationMemoryService.incrementUsage(best.getMemoryId());
                log.info("RAG 直接命中: similarity={}, memoryId={}", best.getSimilarity(), best.getMemoryId());
            } else if (best.getSimilarity() >= referenceThreshold) {
                response.setDirectHit(false);
                response.setSimilarity(best.getSimilarity());
                log.info("RAG 提供参考: similarity={}", best.getSimilarity());
            }

            return response;
        } catch (Exception e) {
            log.error("RAG 查询失败: {}", e.getMessage(), e);
            return buildEmptyResponse();
        }
    }

    /**
     * 翻译完成后存储到翻译记忆（带质量筛选，指定 userId，用于后台任务等非 HTTP 上下文场景）
     */
    public void storeTranslationMemory(String sourceText, String targetText, String targetLang, String engine, Long userId) {
        if (userId == null || sourceText == null || sourceText.isBlank()) {
            return;
        }

        // 质量筛选：拒绝低质量翻译
        String rejectionReason = rejectQuality(sourceText, targetText);
        if (rejectionReason != null) {
            log.debug("RAG 质量筛选拦截: reason={}, engine={}, sourceLen={}", rejectionReason, engine, sourceText.length());
            return;
        }

        try {
            translationMemoryService.storeTranslation(sourceText, targetText, "auto", targetLang,
                    userId, null, engine);

            // 注册到 Redis 向量索引（传入 MySQL 返回的自增 ID）
            storeToRedisVector(sourceText, targetText, targetLang, userId, engine);

            log.debug("RAG 存储翻译记忆: sourceLen={}, targetLen={}", sourceText.length(), targetText.length());
        } catch (Exception e) {
            log.error("RAG 存储翻译记忆失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 翻译完成后存储到翻译记忆（带质量筛选）
     */
    public void storeTranslationMemory(String sourceText, String targetText, String targetLang, String engine) {
        Long userId = SecurityUtil.getCurrentUserId().orElse(null);
        if (userId == null || sourceText == null || sourceText.isBlank()) {
            return;
        }

        // 质量筛选：拒绝低质量翻译
        String rejectionReason = rejectQuality(sourceText, targetText);
        if (rejectionReason != null) {
            log.debug("RAG 质量筛选拦截: reason={}, engine={}, sourceLen={}", rejectionReason, engine, sourceText.length());
            return;
        }

        try {
            translationMemoryService.storeTranslation(sourceText, targetText, "auto", targetLang,
                    userId, null, engine);

            // 注册到 Redis 向量索引（传入 MySQL 返回的自增 ID）
            storeToRedisVector(sourceText, targetText, targetLang, userId, engine);

            log.debug("RAG 存储翻译记忆: sourceLen={}, targetLen={}", sourceText.length(), targetText.length());
        } catch (Exception e) {
            log.error("RAG 存储翻译记忆失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 质量筛选：返回拒绝原因（如果通过则返回 null）
     */
    private String rejectQuality(String source, String target) {
        // 1. 译文为空或与原文完全一致
        if (target == null || target.isBlank()) {
            return "empty_target";
        }
        if (source.trim().equalsIgnoreCase(target.trim())) {
            return "identical_to_source";
        }

        // 2. 长度比率异常（译文超过原文 10 倍或不足 0.1 倍）
        int sourceLen = source.replaceAll("\\s+", "").length();
        int targetLen = target.replaceAll("\\s+", "").length();
        if (sourceLen > 0) {
            double ratio = (double) targetLen / sourceLen;
            if (ratio > 10.0) {
                return "length_ratio_too_high:" + String.format("%.1f", ratio);
            }
            if (ratio < 0.1) {
                return "length_ratio_too_low:" + String.format("%.2f", ratio);
            }
        }

        // 3. 广告/占位符关键词
        String[] adKeywords = {"人工智能助手", "Gemini", "GPT-4", "Claude", "ChatGPT",
                "powered by", "generated by", "翻译引擎", "未翻译", "translation pending"};
        String targetLower = target.toLowerCase();
        for (String keyword : adKeywords) {
            if (targetLower.contains(keyword.toLowerCase())) {
                return "ad_keyword:" + keyword;
            }
        }

        // 4. 译文包含过多数字或特殊字符（超过 60%）
        if (targetLen > 10) {
            int nonAlphanumeric = 0;
            for (char c : target.toCharArray()) {
                if (!Character.isLetterOrDigit(c) && !Character.isWhitespace(c)) {
                    nonAlphanumeric++;
                }
            }
            if ((double) nonAlphanumeric / targetLen > 0.6) {
                return "too_many_special_chars";
            }
        }

        return null;
    }

    /**
     * Redis KNN 向量搜索（按用户和目标语言过滤）
     */
    private List<RagTranslationResponse.RagMatch> searchInRedis(float[] queryVector, Long userId, String targetLang) {
        String vectorStr = formatVectorForRedis(queryVector);

        // 使用 RediSearch 过滤语法：先按 TAG 字段过滤，再在子集中做 KNN
        String filterQuery = String.format("(@user_id:{%s} @target_lang:{%s})", userId, targetLang);
        String knnQuery = String.format("%s=>[KNN %d @embedding $query_vector AS score]", filterQuery, knnTopK);

        Object result = stringRedisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection ->
                connection.execute("FT.SEARCH",
                        "translation_memory_idx".getBytes(),
                        knnQuery.getBytes(),
                        "PARAMS".getBytes(), "2".getBytes(),
                        "query_vector".getBytes(), vectorStr.getBytes(),
                        "RETURN".getBytes(), "4".getBytes(),
                        "source_text".getBytes(), "target_text".getBytes(), "score".getBytes(), "id".getBytes(),
                        "SORTBY".getBytes(), "score".getBytes(), "ASC".getBytes(),
                        "LIMIT".getBytes(), "0".getBytes(), String.valueOf(knnTopK).getBytes(),
                        "DIALECT".getBytes(), "2".getBytes()
                )
        );

        return parseSearchResult(result);
    }

    /**
     * 解析 RediSearch FT.SEARCH 返回结果
     *
     * 返回格式：List<Object>，结构为：
     *   [0]: 总记录数 (Long)
     *   [1]: 文档1的 key (byte[])
     *   [2]: 文档1的字段值数组 (byte[][]) - [field1, value1, field2, value2, ...]
     *   [3]: 文档2的 key
     *   [4]: 文档2的字段值数组
     *   ...
     */
    private List<RagTranslationResponse.RagMatch> parseSearchResult(Object result) {
        List<RagTranslationResponse.RagMatch> matches = new ArrayList<>();
        if (result == null) {
            return matches;
        }

        // Lettuce 返回的是 java.util.List
        if (!(result instanceof List<?> resultList)) {
            log.warn("Redis FT.SEARCH 返回格式异常: {}", result.getClass().getName());
            return matches;
        }

        if (resultList.isEmpty()) {
            return matches;
        }

        // 从索引 1 开始遍历文档（索引 0 是总记录数）
        for (int i = 1; i < resultList.size(); i += 2) {
            Object keyObj = resultList.get(i);
            Object fieldsObj = (i + 1 < resultList.size()) ? resultList.get(i + 1) : null;

            if (fieldsObj == null) {
                continue;
            }

            Map<String, byte[]> fieldMap = parseFieldArray(fieldsObj);
            if (fieldMap.isEmpty()) {
                continue;
            }

            RagTranslationResponse.RagMatch match = new RagTranslationResponse.RagMatch();
            match.setSourceText(getFieldAsString(fieldMap, "source_text"));
            match.setTargetText(getFieldAsString(fieldMap, "target_text"));

            // RediSearch 返回的 score 是距离值（0 = 完全相同，1 = 完全不同）
            // 相似度 = 1 - score
            byte[] scoreBytes = fieldMap.get("score");
            if (scoreBytes != null) {
                try {
                    double distance = Double.parseDouble(new String(scoreBytes));
                    match.setSimilarity(1.0 - distance);
                } catch (NumberFormatException e) {
                    log.warn("score 解析失败: {}", new String(scoreBytes));
                    continue;
                }
            }

            // 从 Redis key 中提取 MySQL id（key 格式：tm:<mysql_id>）
            byte[] keyBytes = keyObj instanceof byte[] kb ? kb : null;
            if (keyBytes != null) {
                String keyStr = new String(keyBytes);
                if (keyStr.startsWith("tm:")) {
                    try {
                        match.setMemoryId(Long.parseLong(keyStr.substring(3)));
                    } catch (NumberFormatException e) {
                        log.warn("从 Redis key 提取 memoryId 失败: {}", keyStr);
                    }
                }
            }

            matches.add(match);
        }

        // 按相似度降序排序
        matches.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
        return matches;
    }

    /**
     * 解析 RediSearch 返回的字段数组（byte[][] 格式：[field1, value1, field2, value2, ...]）
     */
    @SuppressWarnings("unchecked")
    private Map<String, byte[]> parseFieldArray(Object fieldsObj) {
        Map<String, byte[]> map = new LinkedHashMap<>();
        if (!(fieldsObj instanceof List<?> fieldList)) {
            return map;
        }
        for (int j = 0; j + 1 < fieldList.size(); j += 2) {
            Object nameObj = fieldList.get(j);
            Object valueObj = fieldList.get(j + 1);
            if (nameObj instanceof byte[] nb && valueObj instanceof byte[] vb) {
                map.put(new String(nb), vb);
            }
        }
        return map;
    }

    private String getFieldAsString(Map<String, byte[]> map, String fieldName) {
        byte[] bytes = map.get(fieldName);
        return bytes != null ? new String(bytes) : "";
    }

    /**
     * MySQL 降级方案：查询用户的翻译记忆，用余弦相似度计算匹配度
     */
    private List<RagTranslationResponse.RagMatch> searchFallback(float[] queryVector, Long userId, String targetLang) {
        try {
            // 查询该用户的翻译记忆（取最近的 N 条）
            List<TranslationMemory> memories = translationMemoryService
                    .searchByUserAndLang(userId, "auto", targetLang, fallbackMysqlLimit);

            if (memories.isEmpty()) {
                return Collections.emptyList();
            }

            List<RagTranslationResponse.RagMatch> matches = new ArrayList<>();
            for (TranslationMemory memory : memories) {
                List<Float> storedEmbedding = memory.getEmbedding();
                if (storedEmbedding == null || storedEmbedding.isEmpty()) {
                    continue;
                }

                double similarity = cosineSimilarity(queryVector, storedEmbedding);
                if (similarity >= referenceThreshold) {
                    RagTranslationResponse.RagMatch match = new RagTranslationResponse.RagMatch();
                    match.setSourceText(memory.getSourceText());
                    match.setTargetText(memory.getTargetText());
                    match.setSimilarity(similarity);
                    match.setMemoryId(memory.getId());
                    matches.add(match);
                }
            }

            // 按相似度降序排序
            matches.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
            return matches;
        } catch (Exception e) {
            log.warn("MySQL 降级查询失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 计算余弦相似度
     */
    private double cosineSimilarity(float[] vectorA, List<Float> vectorB) {
        if (vectorA.length != vectorB.size()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            float a = vectorA[i];
            float b = vectorB.get(i);
            dotProduct += a * b;
            normA += a * a;
            normB += b * b;
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 存储到 Redis 向量索引
     * 使用 MySQL 返回的自增 ID 作为 Redis key 的一部分，确保可追溯
     */
    private void storeToRedisVector(String sourceText, String targetText, String targetLang, Long userId, String engine) {
        try {
            float[] embedding = embeddingService.embed(sourceText);
            if (embedding.length == 0) {
                return;
            }

            String vectorStr = formatVectorForRedis(embedding);

            // 先查 MySQL 获取最新插入的记录 ID
            List<TranslationMemory> recent = translationMemoryService
                    .searchByUserAndLang(userId, "auto", targetLang, 1);

            Long memoryId = null;
            if (!recent.isEmpty()) {
                // 检查最近的一条是否匹配当前原文（精确匹配确认）
                TranslationMemory latest = recent.get(0);
                if (latest.getSourceText().equals(sourceText) && latest.getTargetText().equals(targetText)) {
                    memoryId = latest.getId();
                }
            }

            // 使用 MySQL ID 作为 Redis key，方便 parseSearchResult 提取
            String key = "tm:" + (memoryId != null ? memoryId : UUID.randomUUID());

            stringRedisTemplate.opsForHash().put(key, "id", memoryId != null ? memoryId.toString() : "0");
            stringRedisTemplate.opsForHash().put(key, "source_text", sourceText);
            stringRedisTemplate.opsForHash().put(key, "target_text", targetText);
            stringRedisTemplate.opsForHash().put(key, "source_lang", "auto");
            stringRedisTemplate.opsForHash().put(key, "target_lang", targetLang);
            stringRedisTemplate.opsForHash().put(key, "user_id", userId.toString());
            stringRedisTemplate.opsForHash().put(key, "embedding", vectorStr);

        } catch (Exception e) {
            log.warn("Redis 向量存储失败: {}", e.getMessage());
        }
    }

    private String formatVectorForRedis(float[] vector) {
        StringBuilder sb = new StringBuilder();
        sb.append(vector.length).append(",");
        for (int i = 0; i < vector.length; i++) {
            sb.append(String.format("%.6f", vector[i]));
            if (i < vector.length - 1) sb.append(",");
        }
        return sb.toString();
    }

    private RagTranslationResponse buildEmptyResponse() {
        RagTranslationResponse response = new RagTranslationResponse();
        response.setDirectHit(false);
        response.setMatches(new ArrayList<>());
        return response;
    }
}
