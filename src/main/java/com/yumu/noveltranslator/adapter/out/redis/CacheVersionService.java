package com.yumu.noveltranslator.adapter.out.redis;

import com.yumu.noveltranslator.adapter.out.persistence.entity.TranslationCache;
import com.yumu.noveltranslator.domain.event.CacheInvalidationEvent;
import com.yumu.noveltranslator.port.out.CacheVersionPort;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.TranslationCacheMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 缓存版本管理服务
 * 维护每个语言对的缓存版本号，支持版本 bump 和 pub/sub 失效通知。
 *
 * Redis key 格式: translator:cache_version:{sourceLang}:{targetLang}
 * 值: 整数版本号，从 1 开始递增
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheVersionService implements CacheVersionPort {

    private static final String VERSION_KEY_PREFIX = "translator:cache_version:";
    private static final String INVALIDATION_CHANNEL = "translator:cache:invalidation";

    private final StringRedisTemplate stringRedisTemplate;
    @Lazy
    private final TranslationCacheService translationCacheService;
    private final TranslationCacheMapper translationCacheMapper;

    /**
     * 获取当前语言对的缓存版本号
     * 如果从未设置过，初始化为 "1"
     */
    public String getVersion(String sourceLang, String targetLang) {
        String key = buildVersionKey(sourceLang, targetLang);
        String version = stringRedisTemplate.opsForValue().get(key);
        if (version == null) {
            stringRedisTemplate.opsForValue().set(key, "1");
            return "1";
        }
        return version;
    }

    /**
     * 版本号 +1 并发布 pub/sub 失效事件
     * @return 新的版本号
     */
    public String bumpVersionAndPublish(String sourceLang, String targetLang) {
        String key = buildVersionKey(sourceLang, targetLang);
        Long newVersion = stringRedisTemplate.opsForValue().increment(key);
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

    private String buildVersionKey(String sourceLang, String targetLang) {
        return VERSION_KEY_PREFIX + sourceLang + ":" + targetLang;
    }

    /**
     * 为指定语言对 bump 版本号并发布失效事件
     * 用于术语表变更时的细粒度缓存失效
     */
    public String bumpVersionForGlossaryTerm(String sourceLang, String targetLang) {
        log.info("细粒度术语缓存失效: sourceLang={}, targetLang={}", sourceLang, targetLang);
        return bumpVersionAndPublish(sourceLang, targetLang);
    }

    /**
     * 为所有已知语言对 bump 版本号
     * 用于术语表变更等全局影响场景
     *
     * @deprecated 使用 {@link #bumpVersionForGlossaryTerm(String, String)} 进行细粒度失效
     */
    @Deprecated
    public void bumpAllVersions() {
        // 查询数据库中的所有目标语言
        List<String> targetLangs = translationCacheMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TranslationCache>()
                        .select("DISTINCT target_lang")
        ).stream()
                .map(TranslationCache::getTargetLang)
                .filter(lang -> lang != null && !lang.isBlank())
                .distinct()
                .toList();

        // 至少处理一个默认目标语言，避免空列表时无效操作
        if (targetLangs.isEmpty()) {
            log.info("无已知翻译缓存，跳过版本 bump");
            return;
        }

        for (String targetLang : targetLangs) {
            bumpVersionAndPublish("auto", targetLang);
        }

        log.info("所有语言对版本已 bump: {} 个语言对", targetLangs.size());
    }
}
