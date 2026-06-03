package com.yumu.noveltranslator.adapter.out.redis;

import com.yumu.noveltranslator.port.out.VectorSearchResult;
import com.yumu.noveltranslator.port.out.VectorStorePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Redis + RediSearch HNSW 向量存储与检索
 * 索引创建见 {@link com.yumu.noveltranslator.config.RedisVectorConfig}
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VectorStorePortAdapter implements VectorStorePort {

    private static final String KEY_PREFIX = "tm:";
    private static final String INDEX_NAME = "translation_memory_idx";

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisConnectionFactory connectionFactory;

    @Override
    public List<VectorSearchResult> vectorSearch(float[] queryVector, Long userId, String targetLang, List<String> allowedModes, int topK) {
        try {
            String modeFilter = buildModeFilter(allowedModes);
            String filterQuery = modeFilter.isEmpty()
                    ? String.format("(@user_id:{%s} @target_lang:{%s})", userId, targetLang)
                    : String.format("(@user_id:{%s} @target_lang:{%s} %s)", userId, targetLang, modeFilter);
            String knnQuery = String.format("%s=>[KNN %d @embedding $query_vector AS score]", filterQuery, topK);
            byte[] queryVectorBytes = floatArrayToBytes(queryVector);

            Object result = connectionFactory.getConnection().execute(
                    "FT.SEARCH",
                    INDEX_NAME.getBytes(StandardCharsets.UTF_8),
                    knnQuery.getBytes(StandardCharsets.UTF_8),
                    "PARAMS".getBytes(StandardCharsets.UTF_8),
                    "2".getBytes(StandardCharsets.UTF_8),
                    "query_vector".getBytes(StandardCharsets.UTF_8),
                    queryVectorBytes,
                    "RETURN".getBytes(StandardCharsets.UTF_8),
                    "4".getBytes(StandardCharsets.UTF_8),
                    "source_text".getBytes(StandardCharsets.UTF_8),
                    "target_text".getBytes(StandardCharsets.UTF_8),
                    "score".getBytes(StandardCharsets.UTF_8),
                    "id".getBytes(StandardCharsets.UTF_8),
                    "SORTBY".getBytes(StandardCharsets.UTF_8),
                    "score".getBytes(StandardCharsets.UTF_8),
                    "ASC".getBytes(StandardCharsets.UTF_8),
                    "LIMIT".getBytes(StandardCharsets.UTF_8),
                    "0".getBytes(StandardCharsets.UTF_8),
                    String.valueOf(topK).getBytes(StandardCharsets.UTF_8),
                    "DIALECT".getBytes(StandardCharsets.UTF_8),
                    "2".getBytes(StandardCharsets.UTF_8)
            );
            return parseSearchResult(result);
        } catch (Exception e) {
            log.warn("Redis vector search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public void storeVector(Long memoryId, String sourceText, String targetText, String sourceLang, String targetLang, Long userId, String translationMode, float[] embedding) {
        try {
            String key = KEY_PREFIX + memoryId;
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("id", memoryId.toString());
            fields.put("source_text", sourceText);
            fields.put("target_text", targetText);
            fields.put("source_lang", sourceLang);
            fields.put("target_lang", targetLang);
            fields.put("user_id", userId.toString());
            if (translationMode != null) {
                fields.put("translation_mode", translationMode);
            }
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                stringRedisTemplate.opsForHash().put(key, entry.getKey(), entry.getValue());
            }
            // Embedding must be stored as binary FLOAT32 bytes for RediSearch HNSW
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] fieldBytes = "embedding".getBytes(StandardCharsets.UTF_8);
            byte[] valueBytes = floatArrayToBytes(embedding);
            try (var conn = connectionFactory.getConnection()) {
                conn.execute("HSET", keyBytes, fieldBytes, valueBytes);
            }
        } catch (Exception e) {
            log.warn("Redis vector store failed: {}", e.getMessage());
        }
    }

    @Override
    public void clearAllVectors() {
        try {
            stringRedisTemplate.delete(stringRedisTemplate.keys(KEY_PREFIX + "*"));
        } catch (Exception e) {
            log.warn("Redis vector clear failed: {}", e.getMessage());
        }
    }

    /**
     * Encode float array to binary FLOAT32 bytes (little-endian) for RediSearch HNSW storage.
     */
    private byte[] floatArrayToBytes(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (float f : vector) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }

    private String buildModeFilter(List<String> allowedModes) {
        if (allowedModes == null || allowedModes.isEmpty()) {
            return "";
        }
        String modes = String.join("|", allowedModes);
        return String.format("(@translation_mode:{%s})", modes);
    }

    @SuppressWarnings("unchecked")
    private List<VectorSearchResult> parseSearchResult(Object result) {
        List<VectorSearchResult> results = new ArrayList<>();
        if (result == null || !(result instanceof List<?> resultList)) {
            return results;
        }

        for (int i = 1; i < resultList.size(); i += 2) {
            Object fieldsObj = (i + 1 < resultList.size()) ? resultList.get(i + 1) : null;
            if (fieldsObj == null) continue;

            Map<String, byte[]> fieldMap = parseFieldArray(fieldsObj);
            if (fieldMap.isEmpty()) continue;

            Long memoryId = parseMemoryId(fieldMap);
            if (memoryId == null) continue;

            String sourceText = new String(fieldMap.getOrDefault("source_text", new byte[0]), StandardCharsets.UTF_8);
            String targetText = new String(fieldMap.getOrDefault("target_text", new byte[0]), StandardCharsets.UTF_8);
            double similarity = parseSimilarity(fieldMap);

            results.add(new VectorSearchResult(memoryId, sourceText, targetText, similarity));
        }
        return results;
    }

    private Long parseMemoryId(Map<String, byte[]> fieldMap) {
        byte[] idBytes = fieldMap.get("id");
        if (idBytes == null) return null;
        try {
            return Long.parseLong(new String(idBytes, StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            log.warn("向量搜索结果 id 解析失败: {}", new String(idBytes, StandardCharsets.UTF_8));
            return null;
        }
    }

    private double parseSimilarity(Map<String, byte[]> fieldMap) {
        byte[] scoreBytes = fieldMap.get("score");
        if (scoreBytes == null) return 0.0;
        try {
            double distance = Double.parseDouble(new String(scoreBytes, StandardCharsets.UTF_8));
            return 1.0 - distance;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, byte[]> parseFieldArray(Object fieldsObj) {
        Map<String, byte[]> map = new LinkedHashMap<>();
        if (fieldsObj instanceof List<?> list) {
            for (int i = 0; i < list.size() - 1; i += 2) {
                if (list.get(i) instanceof byte[] k && list.get(i + 1) instanceof byte[] v) {
                    map.put(new String(k, StandardCharsets.UTF_8), v);
                }
            }
        }
        return map;
    }
}
