package com.yumu.noveltranslator.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.stereotype.Component;

/**
 * Redis 向量索引配置
 * 应用启动时创建 RediSearch 索引，用于翻译记忆的向量检索
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisVectorConfig {

    private final RedisConnectionFactory redisConnectionFactory;

    @Value("${embedding.provider:openai}")
    private String provider;

    @Value("${embedding.openai.model:text-embedding-3-small}")
    private String openaiModel;

    @Value("${embedding.rag.hnsw-m:6}")
    private int hnswM;

    @PostConstruct
    public void initVectorIndex() {
        try {
            var connection = redisConnectionFactory.getConnection();

            // Check if index already exists using FT._LIST
            Object indexList = null;
            try {
                indexList = ((RedisCallback<Object>) con ->
                        con.execute("FT._LIST")
                ).doInRedis(connection);
            } catch (Exception e) {
                log.debug("FT._LIST not supported: {}", e.getMessage());
            }

            // Check if our index is in the list
            if (indexList instanceof java.util.List<?> list) {
                for (Object item : list) {
                    if (item instanceof byte[] bytes
                            && new String(bytes).equals("translation_memory_idx")) {
                        log.info("Redis 向量索引已存在，跳过创建");
                        connection.close();
                        return;
                    } else if ("translation_memory_idx".equals(String.valueOf(item))) {
                        log.info("Redis 向量索引已存在，跳过创建");
                        connection.close();
                        return;
                    }
                }
            }

            // 创建索引：根据 provider 动态计算维度 (Ollama bge-m3=1024, OpenAI=1536)
            final int dimension = "ollama".equals(provider) ? 1024 : 1536;
            final int m = hnswM;

            ((RedisCallback<Object>) con -> con.execute("FT.CREATE",
                    "translation_memory_idx".getBytes(),
                    "ON".getBytes(), "HASH".getBytes(),
                    "PREFIX".getBytes(), "1".getBytes(), "tm:".getBytes(),
                    "SCHEMA".getBytes(),
                    "source_lang".getBytes(), "TAG".getBytes(),
                    "target_lang".getBytes(), "TAG".getBytes(),
                    "source_text".getBytes(), "TEXT".getBytes(),
                    "target_text".getBytes(), "TEXT".getBytes(),
                    "user_id".getBytes(), "TAG".getBytes(),
                    "embedding".getBytes(), "VECTOR".getBytes(),
                    "HNSW".getBytes(), String.valueOf(m).getBytes(),
                    "TYPE".getBytes(), "FLOAT32".getBytes(),
                    "DIM".getBytes(), String.valueOf(dimension).getBytes(),
                    "DISTANCE_METRIC".getBytes(), "COSINE".getBytes()
            )).doInRedis(connection);

            connection.close();
            log.info("Redis 向量索引创建成功: translation_memory_idx (DIM={})", dimension);
        } catch (Exception e) {
            log.error("Redis 向量索引创建失败: {}", e.getMessage(), e);
        }
    }
}
