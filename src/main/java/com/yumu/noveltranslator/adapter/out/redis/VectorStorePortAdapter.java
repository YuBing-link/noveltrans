package com.yumu.noveltranslator.adapter.out.redis;

import com.yumu.noveltranslator.port.out.VectorStorePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class VectorStorePortAdapter implements VectorStorePort {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public List<Map<String, String>> vectorSearch(float[] queryVector, Long userId, String targetLang, List<String> allowedModes, int topK) {
        try {
            String vectorStr = formatVectorForRedis(queryVector);
            String modeFilter = buildModeFilter(allowedModes);
            String filterQuery = String.format("(@user_id:{%s} @target_lang:{%s} %s)", userId, targetLang, modeFilter);
            String knnQuery = String.format("%s=>[KNN %d @embedding $query_vector AS score]", filterQuery, topK);

            Object result = stringRedisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection ->
                    connection.execute("FT.SEARCH",
                            "translation_memory_idx".getBytes(),
                            knnQuery.getBytes(),
                            "PARAMS".getBytes(), "2".getBytes(),
                            "query_vector".getBytes(), vectorStr.getBytes(),
                            "RETURN".getBytes(), "4".getBytes(),
                            "source_text".getBytes(), "target_text".getBytes(), "score".getBytes(), "id".getBytes(),
                            "SORTBY".getBytes(), "score".getBytes(), "ASC".getBytes(),
                            "LIMIT".getBytes(), "0".getBytes(), String.valueOf(topK).getBytes(),
                            "DIALECT".getBytes(), "2".getBytes()
                    )
            );

            return parseSearchResult(result);
        } catch (Exception e) {
            log.warn("Redis vector search failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public void storeVector(String key, Map<String, String> fields) {
        try {
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                stringRedisTemplate.opsForHash().put(key, entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            log.warn("Redis vector store failed: {}", e.getMessage());
        }
    }

    @Override
    public void clearAllVectors() {
        try {
            stringRedisTemplate.delete(stringRedisTemplate.keys("tm:*"));
        } catch (Exception e) {
            log.warn("Redis vector clear failed: {}", e.getMessage());
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

    private String buildModeFilter(List<String> allowedModes) {
        if (allowedModes == null || allowedModes.isEmpty()) {
            return "(@translation_mode:{*})";
        }
        String modes = String.join("|", allowedModes);
        return String.format("(@translation_mode:{%s})", modes);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> parseSearchResult(Object result) {
        List<Map<String, String>> matches = new ArrayList<>();
        if (result == null || !(result instanceof List<?> resultList)) {
            return matches;
        }

        for (int i = 1; i < resultList.size(); i += 2) {
            Object fieldsObj = (i + 1 < resultList.size()) ? resultList.get(i + 1) : null;
            if (fieldsObj == null) continue;

            Map<String, byte[]> fieldMap = parseFieldArray(fieldsObj);
            if (fieldMap.isEmpty()) continue;

            Map<String, String> match = new LinkedHashMap<>();
            match.put("source_text", new String(fieldMap.getOrDefault("source_text", new byte[0]), StandardCharsets.UTF_8));
            match.put("target_text", new String(fieldMap.getOrDefault("target_text", new byte[0]), StandardCharsets.UTF_8));
            match.put("id", new String(fieldMap.getOrDefault("id", new byte[0]), StandardCharsets.UTF_8));
            byte[] scoreBytes = fieldMap.get("score");
            if (scoreBytes != null) {
                match.put("score", new String(scoreBytes, StandardCharsets.UTF_8));
            }
            matches.add(match);
        }
        return matches;
    }

    @SuppressWarnings("unchecked")
    private Map<String, byte[]> parseFieldArray(Object fieldsObj) {
        Map<String, byte[]> map = new LinkedHashMap<>();
        if (fieldsObj instanceof byte[][] arr && arr.length >= 2) {
            for (int i = 0; i < arr.length - 1; i += 2) {
                map.put(new String(arr[i], StandardCharsets.UTF_8), arr[i + 1]);
            }
        } else if (fieldsObj instanceof List<?> list) {
            for (int i = 0; i < list.size() - 1; i += 2) {
                if (list.get(i) instanceof byte[] k && list.get(i + 1) instanceof byte[] v) {
                    map.put(new String(k, StandardCharsets.UTF_8), v);
                }
            }
        }
        return map;
    }
}
