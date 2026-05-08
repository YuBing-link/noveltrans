package com.yumu.noveltranslator.domain.service;

import com.yumu.noveltranslator.port.dto.translation.RagTranslationResponse;
import com.yumu.noveltranslator.domain.model.TranslationMemory;
import com.yumu.noveltranslator.util.SecurityUtil;
import com.yumu.noveltranslator.port.out.VectorStorePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
public class RagTranslationService implements com.yumu.noveltranslator.port.in.RagTranslationPort {

    private final EmbeddingService embeddingService;
    private final TranslationMemoryService translationMemoryService;
    private final VectorStorePort vectorStorePort;

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
     * 查询相似翻译记忆（带模式层级过滤）
     *
     * <p>缓存分层规则：
     * FAST → 读取 ["team","expert","fast"]
     * EXPERT → 读取 ["team","expert"]
     * TEAM → 读取 ["team"]
     */
    public RagTranslationResponse searchSimilarWithModes(String sourceText, String targetLang, List<String> allowedModes) {
        if (sourceText == null || sourceText.isBlank()) {
            return buildEmptyResponse();
        }

        Long userId = SecurityUtil.getCurrentUserId().orElse(null);
        if (userId == null) {
            return buildEmptyResponse();
        }

        try {
            float[] queryVector = embeddingService.embed(sourceText);
            if (queryVector.length == 0) {
                return buildEmptyResponse();
            }

            List<RagTranslationResponse.RagMatch> matches = searchInRedis(queryVector, userId, targetLang, allowedModes);

            if (matches.isEmpty()) {
                matches = searchFallback(queryVector, userId, targetLang, allowedModes);
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
     * 查询相似翻译记忆（旧方法，兼容调用）
     * @deprecated 使用 {@link #searchSimilarWithModes} 代替，支持模式层级过滤
     */
    @Deprecated
    public RagTranslationResponse searchSimilar(String sourceText, String targetLang, String engine) {
        return searchSimilarWithModes(sourceText, targetLang, List.of("team", "expert", "fast"));
    }

    /**
     * 查询相似翻译记忆（指定 userId，用于后台任务等非 HTTP 上下文场景）
     * @deprecated 使用 {@link #searchSimilarWithUserAndModes} 代替
     */
    @Deprecated
    public RagTranslationResponse searchSimilarWithUser(
            String sourceText, String targetLang, String engine, Long userId) {
        return searchSimilarWithUserAndModes(sourceText, targetLang, userId, List.of("team", "expert", "fast"));
    }

    /**
     * 查询相似翻译记忆（指定 userId + 模式层级过滤）
     */
    public RagTranslationResponse searchSimilarWithUserAndModes(
            String sourceText, String targetLang, Long userId, List<String> allowedModes) {
        if (sourceText == null || sourceText.isBlank() || userId == null) {
            return buildEmptyResponse();
        }

        try {
            float[] queryVector = embeddingService.embed(sourceText);
            if (queryVector.length == 0) {
                return buildEmptyResponse();
            }

            List<RagTranslationResponse.RagMatch> matches = searchInRedis(queryVector, userId, targetLang, allowedModes);

            if (matches.isEmpty()) {
                matches = searchFallback(queryVector, userId, targetLang, allowedModes);
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
        storeTranslationMemory(sourceText, targetText, targetLang, engine, userId, null);
    }

    /**
     * 翻译完成后存储到翻译记忆（带质量筛选，指定 userId + 模式标记）
     */
    public void storeTranslationMemory(String sourceText, String targetText, String targetLang, String engine, Long userId, String translationMode) {
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
                    userId, null, engine, translationMode);

            // 注册到 Redis 向量索引（传入 MySQL 返回的自增 ID）
            storeToRedisVector(sourceText, targetText, targetLang, userId, engine, translationMode);

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
        storeTranslationMemory(sourceText, targetText, targetLang, engine, userId, null);
    }

    /**
     * 翻译完成后存储到翻译记忆（带质量筛选 + 模式标记）
     */
    public void storeTranslationMemory(String sourceText, String targetText, String targetLang, String engine, String translationMode) {
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
                    userId, null, engine, translationMode);

            // 注册到 Redis 向量索引（传入 MySQL 返回的自增 ID）
            storeToRedisVector(sourceText, targetText, targetLang, userId, engine, translationMode);

            log.debug("RAG 存储翻译记忆: sourceLen={}, targetLen={}, mode={}", sourceText.length(), targetText.length(), translationMode);
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
     * 向量搜索（按用户、目标语言和翻译模式过滤）
     */
    private List<RagTranslationResponse.RagMatch> searchInRedis(float[] queryVector, Long userId, String targetLang, List<String> allowedModes) {
        List<Map<String, String>> results = vectorStorePort.vectorSearch(queryVector, userId, targetLang, allowedModes, knnTopK);
        return convertToRagMatches(results);
    }

    /**
     * 将 VectorStorePort 返回的 Map 列表转换为 RagMatch 对象
     */
    private List<RagTranslationResponse.RagMatch> convertToRagMatches(List<Map<String, String>> results) {
        List<RagTranslationResponse.RagMatch> matches = new ArrayList<>();
        if (results == null) {
            return matches;
        }

        for (Map<String, String> row : results) {
            RagTranslationResponse.RagMatch match = new RagTranslationResponse.RagMatch();
            match.setSourceText(row.getOrDefault("source_text", ""));
            match.setTargetText(row.getOrDefault("target_text", ""));

            String scoreStr = row.get("score");
            if (scoreStr != null) {
                try {
                    double distance = Double.parseDouble(scoreStr);
                    match.setSimilarity(1.0 - distance);
                } catch (NumberFormatException e) {
                    log.warn("score 解析失败: {}", scoreStr);
                    continue;
                }
            }

            String idStr = row.get("id");
            if (idStr != null) {
                try {
                    match.setMemoryId(Long.parseLong(idStr));
                } catch (NumberFormatException e) {
                    log.warn("id 解析失败: {}", idStr);
                }
            }

            matches.add(match);
        }

        matches.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
        return matches;
    }

    /**
     * MySQL 降级方案：查询用户的翻译记忆，用余弦相似度计算匹配度（带模式过滤）
     */
    private List<RagTranslationResponse.RagMatch> searchFallback(float[] queryVector, Long userId, String targetLang, List<String> allowedModes) {
        try {
            List<TranslationMemory> memories = translationMemoryService
                    .searchByUserAndLang(userId, "auto", targetLang, fallbackMysqlLimit);

            if (memories.isEmpty()) {
                return Collections.emptyList();
            }

            // 按 allowedModes 过滤
            Set<String> modeSet = allowedModes != null ? new HashSet<>(allowedModes) : Collections.emptySet();

            List<RagTranslationResponse.RagMatch> matches = new ArrayList<>();
            for (TranslationMemory memory : memories) {
                // 模式过滤：如果设置了 allowedModes，跳过不匹配的
                if (!modeSet.isEmpty() && memory.getTranslationMode() != null
                        && !modeSet.contains(memory.getTranslationMode())) {
                    continue;
                }

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
     * 存储到向量索引（带 translation_mode）
     * 使用 MySQL 返回的自增 ID 作为 key，确保可追溯
     */
    private void storeToRedisVector(String sourceText, String targetText, String targetLang, Long userId, String engine, String translationMode) {
        try {
            float[] embedding = embeddingService.embed(sourceText);
            if (embedding.length == 0) {
                return;
            }

            // 先查 MySQL 获取最新插入的记录 ID
            List<TranslationMemory> recent = translationMemoryService
                    .searchByUserAndLang(userId, "auto", targetLang, 1);

            Long memoryId = null;
            if (!recent.isEmpty()) {
                TranslationMemory latest = recent.get(0);
                if (latest.getSourceText().equals(sourceText) && latest.getTargetText().equals(targetText)) {
                    memoryId = latest.getId();
                }
            }

            String key = "tm:" + (memoryId != null ? memoryId : UUID.randomUUID());

            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("id", memoryId != null ? memoryId.toString() : "0");
            fields.put("source_text", sourceText);
            fields.put("target_text", targetText);
            fields.put("source_lang", "auto");
            fields.put("target_lang", targetLang);
            fields.put("user_id", userId.toString());
            fields.put("embedding", formatVectorForRedis(embedding));
            if (translationMode != null) {
                fields.put("translation_mode", translationMode);
            }

            vectorStorePort.storeVector(key, fields);

        } catch (Exception e) {
            log.warn("向量存储失败: {}", e.getMessage());
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

    /**
     * 清空所有 RAG 翻译记忆（调试用）
     */
    public void clearAllTranslationMemory() {
        try {
            translationMemoryService.deleteAllTranslationMemory();
            log.info("MySQL translation_memory 表已清空");
        } catch (Exception e) {
            log.warn("MySQL translation_memory 清空失败：{}", e.getMessage());
        }
        try {
            vectorStorePort.clearAllVectors();
            log.info("向量索引已清空");
        } catch (Exception e) {
            log.warn("向量索引清空失败：{}", e.getMessage());
        }
    }

    private RagTranslationResponse buildEmptyResponse() {
        RagTranslationResponse response = new RagTranslationResponse();
        response.setDirectHit(false);
        response.setMatches(new ArrayList<>());
        return response;
    }
}
