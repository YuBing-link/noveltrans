package com.yumu.noveltranslator.task;

import com.yumu.noveltranslator.adapter.out.persistence.mapper.ApiKeyMapper;
import com.yumu.noveltranslator.adapter.out.redis.ApiKeyCacheService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时将 Redis 中累积的 API Key 使用次数回写到 MySQL。
 * 每 60 秒执行一次，服务关闭时执行最后一次 flush。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyUsageFlushTask {

    private final ApiKeyCacheService apiKeyCacheService;
    private final ApiKeyMapper apiKeyMapper;

    @Scheduled(fixedDelay = 60_000)
    public void flush() {
        apiKeyCacheService.flushUsage((apiKeyId, usage) -> {
            apiKeyMapper.incrementUsage(apiKeyId, usage);
            log.debug("flush: apiKeyId={}, usage={}", apiKeyId, usage);
        });
    }

    @PreDestroy
    public void flushOnShutdown() {
        log.info("服务关闭前执行最后一次 API Key usage flush");
        int count = apiKeyCacheService.flushUsage((apiKeyId, usage) -> {
            apiKeyMapper.incrementUsage(apiKeyId, usage);
        });
        log.info("关机 flush 完成: {} 个 key", count);
    }
}
