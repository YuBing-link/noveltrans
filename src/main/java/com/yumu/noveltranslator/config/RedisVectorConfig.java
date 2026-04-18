package com.yumu.noveltranslator.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
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

    @Value("${embedding.openai.model:text-embedding-3-small}")
    private String openaiModel;

    @Value("${embedding.rag.hnsw-m:6}")
    private int hnswM;

    @PostConstruct
    public void initVectorIndex() {
        try {
            // 检查索引是否已存在
            var connection = redisConnectionFactory.getConnection();
            byte[][] args = new byte[][]{"translation_memory_idx".getBytes()};
            Object exists = connection.execute("FT.INFO", args);

            if (exists != null) {
                log.info("Redis 向量索引已存在，跳过创建");
                return;
            }

            // 创建索引
            // OpenAI text-embedding-3-small 默认 1536 维
            int dimension = 1536;
            if (openaiModel.contains("3-small") || openaiModel.contains("3-large")) {
                dimension = 1536;
            }

            connection.execute("FT.CREATE",
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
                    "HNSW".getBytes(), String.valueOf(hnswM).getBytes(),
                    "TYPE".getBytes(), "FLOAT32".getBytes(),
                    "DIM".getBytes(), String.valueOf(dimension).getBytes(),
                    "DISTANCE_METRIC".getBytes(), "COSINE".getBytes()
            );

            log.info("Redis 向量索引创建成功: translation_memory_idx (DIM={})", dimension);
        } catch (Exception e) {
            log.warn("Redis 向量索引创建失败（可能当前 Redis 不支持 RediSearch）: {}", e.getMessage());
        }
    }
}
