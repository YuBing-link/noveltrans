package com.yumu.noveltranslator.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yumu.noveltranslator.adapter.out.persistence.entity.TranslationCache;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.TranslationCacheMapper;
import com.yumu.noveltranslator.adapter.out.redis.TranslationCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 应用启动时预热缓存，从 MySQL 加载最近的翻译记录到 L1/L2，减少冷启动后 LLM 调用。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CacheWarmupRunner implements ApplicationRunner {

    private static final int WARMUP_LIMIT = 5000;

    private final TranslationCacheMapper cacheMapper;
    private final TranslationCacheService cacheService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<TranslationCache> recentCaches = cacheMapper.selectList(
                    new QueryWrapper<TranslationCache>()
                            .isNotNull("target_text")
                            .gt("expire_time", LocalDateTime.now())
                            .orderByDesc("create_time")
                            .last("LIMIT " + WARMUP_LIMIT)
            );

            if (recentCaches.isEmpty()) {
                log.info("缓存预热跳过：无可用记录");
                return;
            }

            List<String> keys = recentCaches.stream()
                    .map(TranslationCache::getCacheKey)
                    .collect(Collectors.toList());

            log.info("开始缓存预热：从 MySQL 加载 {} 条记录", keys.size());
            cacheService.warmupCache(keys);
        } catch (Exception e) {
            log.warn("缓存预热失败，跳过: {}", e.getMessage());
        }
    }
}
