package com.yumu.noveltranslator.task;

import com.yumu.noveltranslator.service.TranslationCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时清理过期数据库缓存记录
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CacheCleanupTask {

    private final TranslationCacheService translationCacheService;

    /**
     * 每 30 分钟清理一次数据库中的过期缓存
     */
    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void cleanupExpiredCache() {
        log.info("定时任务触发: 清理过期数据库缓存");
        translationCacheService.cleanupExpiredCache();
    }
}
