package com.yumu.noveltranslator.adapter.out.redis;

import com.yumu.noveltranslator.port.out.VectorSearchResult;
import com.yumu.noveltranslator.port.out.VectorStorePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class VectorStorePortAdapter implements VectorStorePort {

    private static final String KEY_PREFIX = "tm:";
    private static final String INDEX_NAME = "translation_memory_idx";

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisConnectionFactory connectionFactory;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Override
    public List<VectorSearchResult> vectorSearch(float[] queryVector, Long userId, String targetLang, List<String> allowedModes, int topK) {
        try {
            String modeFilter = buildModeFilter(allowedModes);
            String filterQuery = modeFilter.isEmpty()
                    ? String.format("(@user_id:{%s} @target_lang:{%s})", userId, targetLang)
                    : String.format("(@user_id:{%s} @target_lang:{%s} %s)", userId, targetLang, modeFilter);
            String knnQuery = String.format("%s=>[KNN %d @embedding $query_vector AS score]", filterQuery, topK);
            byte[] queryVectorBytes = floatArrayToBytes(queryVector);

            List<Object> args = new ArrayList<>();
            args.add(INDEX_NAME);
            args.add(knnQuery);
            args.add("PARAMS");
            args.add("2");
            args.add("query_vector");
            args.add(queryVectorBytes);
            args.add("RETURN");
            args.add("4");
            args.add("source_text");
            args.add("target_text");
            args.add("score");
            args.add("id");
            args.add("SORTBY");
            args.add("score");
            args.add("ASC");
            args.add("LIMIT");
            args.add("0");
            args.add(String.valueOf(topK));
            args.add("DIALECT");
            args.add("2");

            List<Object> response = executeRawCommand("FT.SEARCH", args);
            return parseSearchResult(response);
        } catch (Exception e) {
            log.warn("Redis vector search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> executeRawCommand(String command, List<Object> args) throws Exception {
        try (Socket socket = new Socket(redisHost, redisPort)) {
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();

            // AUTH if password set
            if (redisPassword != null && !redisPassword.isEmpty()) {
                writeRespCommand(outStream, "AUTH", List.of(redisPassword));
                outStream.writeTo(socket.getOutputStream());
                outStream.reset();
                socket.getOutputStream().flush();
                readRespResponse(in);
            }

            // Build and send RESP command
            writeRespCommand(outStream, command, args);
            outStream.writeTo(socket.getOutputStream());
            socket.getOutputStream().flush();

            // Read and parse response
            return (List<Object>) readRespResponse(in);
        }
    }

    private void writeRespCommand(ByteArrayOutputStream out, String command, List<Object> args) {
        int argc = 1 + args.size();
        writeLine(out, "*" + argc);
        writeBulkBytes(out, command.getBytes(StandardCharsets.UTF_8));
        for (Object arg : args) {
            byte[] data = (arg instanceof byte[] b) ? b : arg.toString().getBytes(StandardCharsets.UTF_8);
            writeBulkBytes(out, data);
        }
    }

    private void writeLine(ByteArrayOutputStream out, String line) {
        try {
            out.write(line.getBytes(StandardCharsets.UTF_8));
            out.write('\r');
            out.write('\n');
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void writeBulkBytes(ByteArrayOutputStream out, byte[] data) {
        try {
            writeLine(out, "$" + data.length);
            out.write(data);
            out.write('\r');
            out.write('\n');
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Object readRespResponse(DataInputStream in) throws Exception {
        int type = in.readByte();
        switch (type) {
            case '+': // Simple String
                return readLine(in);
            case '-': // Error
                throw new RuntimeException("Redis error: " + readLine(in));
            case ':': // Integer
                return Long.parseLong(readLine(in));
            case '$': // Bulk String
                int len = Integer.parseInt(readLine(in));
                if (len < 0) return null;
                byte[] data = new byte[len];
                in.readFully(data);
                in.readByte(); // \r
                in.readByte(); // \n
                return data;
            case '*': // Array
                int count = Integer.parseInt(readLine(in));
                if (count < 0) return null;
                List<Object> list = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    list.add(readRespResponse(in));
                }
                return list;
            case '%': // Map (Redis 6.2+)
                int mapLen = Integer.parseInt(readLine(in));
                Map<Object, Object> map = new LinkedHashMap<>();
                for (int i = 0; i < mapLen; i++) {
                    Object key = readRespResponse(in);
                    Object val = readRespResponse(in);
                    map.put(key, val);
                }
                return map;
            default:
                throw new RuntimeException("Unknown RESP type: " + (char) type);
        }
    }

    private String readLine(DataInputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.readByte()) != '\r') {
            sb.append((char) b);
        }
        in.readByte(); // \n
        return sb.toString();
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
