package com.yumu.noveltranslator.adapter.out.redis;

import com.yumu.noveltranslator.domain.event.CacheInvalidationEvent;
import com.yumu.noveltranslator.port.out.CacheVersionPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 缓存版本管理服务
 * 维护全局缓存版本号，支持版本 bump 和 pub/sub 失效通知。
 *
 * Redis key: translator:cache_version:default
 * 值: 整数版本号，从 1 开始递增
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheVersionService implements CacheVersionPort {

    private static final String GLOBAL_VERSION_KEY = "translator:cache_version:default";
    private static final String INVALIDATION_CHANNEL = "translator:cache:invalidation";

    private final StringRedisTemplate stringRedisTemplate;
    @Lazy
    private final TranslationCacheService translationCacheService;

    /**
     * 获取当前语言对的缓存版本号
     * 如果从未设置过，初始化为 "1"
     */
    public String getVersion(String sourceLang, String targetLang) {
        String version = stringRedisTemplate.opsForValue().get(GLOBAL_VERSION_KEY);
        if (version == null) {
            stringRedisTemplate.opsForValue().set(GLOBAL_VERSION_KEY, "1");
            return "1";
        }
        return version;
    }

    /**
     * 版本号 +1 并发布 pub/sub 失效事件
     * @return 新的版本号
     */
    public String bumpVersionAndPublish(String sourceLang, String targetLang) {
        Long newVersion = stringRedisTemplate.opsForValue().increment(GLOBAL_VERSION_KEY);
        String versionStr = String.valueOf(newVersion);

        log.info("缓存版本 bump: sourceLang={}, targetLang={}, newVersion={}", sourceLang, targetLang, versionStr);

        // 清空本地 Caffeine 缓存
        translationCacheService.clearLocalCache();

        // 发布 pub/sub 事件通知其他实例
        CacheInvalidationEvent event = new CacheInvalidationEvent(sourceLang, targetLang, versionStr);
        stringRedisTemplate.convertAndSend(INVALIDATION_CHANNEL, event.serialize());

        return versionStr;
    }

    /**
     * 接收 pub/sub 事件，清空本地 Caffeine 缓存
     */
    public void handleInvalidationEvent(String sourceLang, String targetLang) {
        log.info("收到缓存失效事件: sourceLang={}, targetLang={}", sourceLang, targetLang);
        translationCacheService.clearLocalCache();
    }

    /**
     * 全局版本模式下直接 bump 单一版本号
     * @deprecated 使用 {@link #bumpVersionAndPublish(String, String)} 代替
     */
    @Deprecated
    @Override
    public void bumpAllVersions() {
        log.info("全局版本 bump");
        bumpVersionAndPublish("auto", "auto");
    }
}
