package com.yumu.noveltranslator.domain.service;

import com.yumu.noveltranslator.domain.model.TranslationMemory;
import com.yumu.noveltranslator.port.dto.translation.RagTranslationResponse;
import com.yumu.noveltranslator.port.out.VectorSearchResult;
import com.yumu.noveltranslator.port.out.VectorStorePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

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

    private static final String SOURCE_LANG_AUTO = "auto";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

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

    // ==================== 查询入口 ====================

    /**
     * 查询相似翻译记忆（从当前 HTTP 上下文获取 userId，带模式层级过滤）
     */
    public RagTranslationResponse searchSimilarWithModes(String sourceText, String targetLang, List<String> allowedModes) {
        Long userId = com.yumu.noveltranslator.util.SecurityUtil.getCurrentUserId().orElse(null);
        if (userId == null) {
            return buildEmptyResponse();
        }
        return doSearch(sourceText, targetLang, userId, allowedModes);
    }

    /**
     * 查询相似翻译记忆（指定 userId，用于后台任务等非 HTTP 上下文场景）
     */
    public RagTranslationResponse searchSimilarWithUserAndModes(
            String sourceText, String targetLang, Long userId, List<String> allowedModes) {
        if (userId == null) {
            return buildEmptyResponse();
        }
        return doSearch(sourceText, targetLang, userId, allowedModes);
    }

    /**
     * @deprecated 使用 {@link #searchSimilarWithModes} 代替
     */
    @Deprecated
    public RagTranslationResponse searchSimilar(String sourceText, String targetLang, String engine) {
        return searchSimilarWithModes(sourceText, targetLang, List.of("team", "expert", "fast"));
    }

    /**
     * @deprecated 使用 {@link #searchSimilarWithUserAndModes} 代替
     */
    @Deprecated
    public RagTranslationResponse searchSimilarWithUser(
            String sourceText, String targetLang, String engine, Long userId) {
        return searchSimilarWithUserAndModes(sourceText, targetLang, userId, List.of("team", "expert", "fast"));
    }

    // ==================== 存储入口 ====================

    public void storeTranslationMemory(String sourceText, String targetText, String targetLang, String engine, Long userId, String translationMode) {
        doStore(sourceText, targetText, targetLang, engine, userId, translationMode);
    }

    public void storeTranslationMemory(String sourceText, String targetText, String targetLang, String engine, Long userId) {
        doStore(sourceText, targetText, targetLang, engine, userId, null);
    }

    /**
     * @deprecated 从 SecurityUtil 隐式获取 userId，优先使用显式传入 userId 的重载
     */
    @Deprecated
    public void storeTranslationMemory(String sourceText, String targetText, String targetLang, String engine) {
        Long userId = com.yumu.noveltranslator.util.SecurityUtil.getCurrentUserId().orElse(null);
        doStore(sourceText, targetText, targetLang, engine, userId, null);
    }

    /**
     * @deprecated 从 SecurityUtil 隐式获取 userId，优先使用显式传入 userId 的重载
     */
    @Deprecated
    public void storeTranslationMemory(String sourceText, String targetText, String targetLang, String engine, String translationMode) {
        Long userId = com.yumu.noveltranslator.util.SecurityUtil.getCurrentUserId().orElse(null);
        doStore(sourceText, targetText, targetLang, engine, userId, translationMode);
    }

    // ==================== 核心私有方法 ====================

    private RagTranslationResponse doSearch(String sourceText, String targetLang, Long userId, List<String> allowedModes) {
        if (sourceText == null || sourceText.isBlank()) {
            return buildEmptyResponse();
        }

        try {
            float[] queryVector = embeddingService.embed(sourceText);
            if (queryVector.length == 0) {
                return buildEmptyResponse();
            }

            List<VectorSearchResult> results = vectorStorePort.vectorSearch(queryVector, userId, targetLang, allowedModes, knnTopK);

            if (results.isEmpty()) {
                results = searchFallback(queryVector, userId, targetLang, allowedModes);
            }

            if (results.isEmpty()) {
                return buildEmptyResponse();
            }

            RagTranslationResponse response = new RagTranslationResponse();
            response.setMatches(toRagMatches(results));

            VectorSearchResult best = results.get(0);
            if (best.similarity() >= directHitThreshold) {
                response.setDirectHit(true);
                response.setTranslation(best.targetText());
                response.setSimilarity(best.similarity());
                translationMemoryService.incrementUsage(best.memoryId());
                log.info("RAG 直接命中: similarity={}, memoryId={}", best.similarity(), best.memoryId());
            } else if (best.similarity() >= referenceThreshold) {
                response.setDirectHit(false);
                response.setSimilarity(best.similarity());
                log.info("RAG 提供参考: similarity={}", best.similarity());
            }

            return response;
        } catch (Exception e) {
            log.error("RAG 查询失败: {}", e.getMessage(), e);
            return buildEmptyResponse();
        }
    }

    private void doStore(String sourceText, String targetText, String targetLang, String engine, Long userId, String translationMode) {
        if (userId == null || sourceText == null || sourceText.isBlank()) {
            return;
        }

        String rejectionReason = rejectQuality(sourceText, targetText);
        if (rejectionReason != null) {
            log.debug("RAG 质量筛选拦截: reason={}, engine={}, sourceLen={}", rejectionReason, engine, sourceText.length());
            return;
        }

        try {
            Long memoryId = translationMemoryService.storeTranslation(
                    sourceText, targetText, SOURCE_LANG_AUTO, targetLang,
                    userId, null, engine, translationMode);

            if (memoryId == null) {
                log.warn("RAG 存储失败: storeTranslation 返回 null memoryId");
                return;
            }

            float[] embedding = embeddingService.embed(sourceText);
            if (embedding.length > 0) {
                vectorStorePort.storeVector(
                        memoryId, sourceText, targetText, SOURCE_LANG_AUTO, targetLang,
                        userId, translationMode, embedding);
            }

            log.debug("RAG 存储翻译记忆: memoryId={}, sourceLen={}, mode={}", memoryId, sourceText.length(), translationMode);
        } catch (Exception e) {
            log.error("RAG 存储翻译记忆失败: {}", e.getMessage(), e);
        }
    }

    // ==================== 内部辅助方法 ====================

    private List<RagTranslationResponse.RagMatch> toRagMatches(List<VectorSearchResult> results) {
        List<RagTranslationResponse.RagMatch> matches = new ArrayList<>(results.size());
        for (VectorSearchResult r : results) {
            RagTranslationResponse.RagMatch match = new RagTranslationResponse.RagMatch();
            match.setMemoryId(r.memoryId());
            match.setSourceText(r.sourceText());
            match.setTargetText(r.targetText());
            match.setSimilarity(r.similarity());
            matches.add(match);
        }
        return matches;
    }

    /**
     * MySQL 降级方案：查询用户的翻译记忆，用余弦相似度计算匹配度（带模式过滤）
     */
    private List<VectorSearchResult> searchFallback(float[] queryVector, Long userId, String targetLang, List<String> allowedModes) {
        try {
            List<TranslationMemory> memories = translationMemoryService
                    .searchByUserAndLang(userId, SOURCE_LANG_AUTO, targetLang, fallbackMysqlLimit);

            if (memories.isEmpty()) {
                return Collections.emptyList();
            }

            Set<String> modeSet = allowedModes != null ? Set.copyOf(allowedModes) : Collections.emptySet();

            List<VectorSearchResult> results = new ArrayList<>();
            for (TranslationMemory memory : memories) {
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
                    results.add(new VectorSearchResult(
                            memory.getId(), memory.getSourceText(), memory.getTargetText(), similarity));
                }
            }

            results.sort((a, b) -> Double.compare(b.similarity(), a.similarity()));
            return results;
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
     * 质量筛选：返回拒绝原因（如果通过则返回 null）
     */
    private String rejectQuality(String source, String target) {
        if (target == null || target.isBlank()) {
            return "empty_target";
        }
        if (source.trim().equalsIgnoreCase(target.trim())) {
            return "identical_to_source";
        }

        int sourceLen = WHITESPACE.matcher(source).replaceAll("").length();
        int targetLen = WHITESPACE.matcher(target).replaceAll("").length();
        if (sourceLen > 0) {
            double ratio = (double) targetLen / sourceLen;
            if (ratio > 10.0) {
                return "length_ratio_too_high:" + String.format("%.1f", ratio);
            }
            if (ratio < 0.1) {
                return "length_ratio_too_low:" + String.format("%.2f", ratio);
            }
        }

        String[] adKeywords = {"人工智能助手", "Gemini", "GPT-4", "Claude", "ChatGPT",
                "powered by", "generated by", "翻译引擎", "未翻译", "translation pending"};
        String targetLower = target.toLowerCase(Locale.ROOT);
        for (String keyword : adKeywords) {
            if (targetLower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return "ad_keyword:" + keyword;
            }
        }

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
