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
 *
 * 策略：直接 FT.CREATE，如果返回 "Index already exists" 说明索引已存在（Redis 数据卷持久化场景）。
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
        // 创建索引：根据 provider 动态计算维度 (Ollama bge-m3=1024, OpenAI=1536)
        final int dimension = "ollama".equals(provider) ? 1024 : 1536;
        final int m = hnswM;

        try (var connection = redisConnectionFactory.getConnection()) {
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

            log.info("Redis 向量索引创建成功: translation_memory_idx (DIM={})", dimension);
        } catch (Exception e) {
            if (isIndexAlreadyExists(e)) {
                log.info("Redis 向量索引已存在: translation_memory_idx (DIM={}), 跳过创建", dimension);
            } else {
                log.error("Redis 向量索引创建失败: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 递归检查异常链中是否包含 "Index already exists"
     */
    private boolean isIndexAlreadyExists(Throwable t) {
        if (t == null) return false;
        if (t.getMessage() != null && t.getMessage().contains("Index already exists")) {
            return true;
        }
        return isIndexAlreadyExists(t.getCause());
    }
}
