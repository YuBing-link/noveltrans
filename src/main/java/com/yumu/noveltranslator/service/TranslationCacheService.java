package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.entity.TranslationCache;
import com.yumu.noveltranslator.mapper.TranslationCacheMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 翻译缓存服务
 * 三级缓存架构：Caffeine (L1) → Redis (L2) → 数据库 (L3) → 翻译引擎
 *
 * <p>缓存特性：</p>
 * <ul>
 *   <li>L1 Caffeine：内存缓存，10 分钟过期，最高性能</li>
 *   <li>L2 Redis：分布式缓存，30 分钟过期，支持多实例共享</li>
 *   <li>L3 数据库：持久化缓存，24 小时过期，永久存储</li>
 * </ul>
 *
 * <p>防缓存问题：</p>
 * <ul>
 *   <li>缓存穿透：空值占位（5 分钟短暂过期）</li>
 *   <li>缓存击穿：synchronized 对同一 key 加锁</li>
 *   <li>缓存雪崩：过期时间添加随机抖动</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TranslationCacheService {

    private final TranslationCacheMapper translationCacheMapper;
    private final StringRedisTemplate stringRedisTemplate;

    // ==================== L1: Caffeine 内存缓存 ====================

    /**
     * Caffeine 本地缓存，最大 10000 条，写入后 10 分钟过期
     */
    private final Cache<String, String> caffeineCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .recordStats()
            .build();

    // ==================== L2: Redis 配置常量 ====================

    private static final String REDIS_KEY_PREFIX = "translator:cache:";
    private static final long REDIS_CACHE_SECONDS = 30 * 60;          // Redis 缓存 30 分钟
    private static final long REDIS_NULL_SECONDS = 5 * 60;            // 空值占位 5 分钟

    // ==================== L3: 数据库配置常量 ====================

    private static final long DATABASE_CACHE_EXPIRY_HOURS = 24;       // 数据库缓存 24 小时

    // ==================== 抖动配置（防雪崩） ====================

    private static final long REDIS_JITTER_SECONDS = 60;              // Redis 过期时间抖动 ±60 秒
    private static final long DB_JITTER_HOURS = 2;                    // 数据库过期时间抖动 ±2 小时

    // ==================== 模式缓存层级 ====================

    /**
     * 模式缓存层级：查询顺序
     */
    private static final Map<String, List<String>> MODE_CACHE_HIERARCHY = Map.of(
        "fast", List.of("team", "expert", "fast"),
        "expert", List.of("team", "expert"),
        "team", List.of("team")
    );

    // ==================== 缓存击穿防护 ====================

    /**
     * 正在加载中的缓存 key 集合，防止并发查询同一 key
     */
    private final ConcurrentHashMap<String, Object> loadingKeys = new ConcurrentHashMap<>();

    // ==================== 缓存统计 ====================

    private final AtomicLong l1HitCount = new AtomicLong(0);
    private final AtomicLong l2HitCount = new AtomicLong(0);
    private final AtomicLong l3HitCount = new AtomicLong(0);
    private final AtomicLong cacheMissCount = new AtomicLong(0);
    private final AtomicLong nullHitCount = new AtomicLong(0);       // 空值命中（穿透防护）

    /**
     * 空值占位标记
     */
    private static final String NULL_PLACEHOLDER = "__NULL__";

    // ==================== 核心查询方法 ====================

    /**
     * 获取翻译缓存（三级缓存查询）
     * 查询顺序：L1 Caffeine → L2 Redis → L3 数据库 → null
     *
     * @param cacheKey 缓存键
     * @return 缓存的翻译结果，null 表示无缓存
     */
    public String getCache(String cacheKey) {
        String redisKey = REDIS_KEY_PREFIX + cacheKey;

        // 1. L1: 尝试 Caffeine 缓存
        String l1Value = caffeineCache.getIfPresent(cacheKey);
        if (l1Value != null) {
            if (NULL_PLACEHOLDER.equals(l1Value)) {
                nullHitCount.incrementAndGet();
                log.debug("L1 空值命中（防穿透）：{}", cacheKey);
                return null;
            }
            l1HitCount.incrementAndGet();
            log.debug("L1 Caffeine 缓存命中：{}", cacheKey);
            return l1Value;
        }

        // 2. L2: 尝试 Redis 缓存（加锁防击穿）
        String l2Value = loadWithLock(cacheKey, redisKey, () -> {
            String value = stringRedisTemplate.opsForValue().get(redisKey);
            if (value != null) {
                if (NULL_PLACEHOLDER.equals(value)) {
                    // 空值占位，回写 L1
                    caffeineCache.put(cacheKey, NULL_PLACEHOLDER);
                    log.debug("L2 Redis 空值命中（防穿透）：{}", cacheKey);
                    return null;
                }
                // 回写 L1
                caffeineCache.put(cacheKey, value);
                l2HitCount.incrementAndGet();
                log.debug("L2 Redis 缓存命中：{}", cacheKey);
                return value;
            }
            return null;
        });
        if (l2Value != null) {
            return l2Value;
        }

        // 3. L3: 尝试数据库缓存（加锁防击穿）
        String l3Value = loadWithLock(cacheKey, redisKey, () -> {
            TranslationCache dbCache = queryDatabaseCache(cacheKey);
            if (dbCache != null) {
                String targetText = dbCache.getTargetText();
                // 回写 L2
                stringRedisTemplate.opsForValue().set(
                        redisKey,
                        targetText,
                        Duration.ofSeconds(REDIS_CACHE_SECONDS + jitter(REDIS_JITTER_SECONDS))
                );
                // 回写 L1
                caffeineCache.put(cacheKey, targetText);
                l3HitCount.incrementAndGet();
                log.debug("L3 数据库缓存命中：{}", cacheKey);
                return targetText;
            }
            return null;
        });

        if (l3Value == null) {
            cacheMissCount.incrementAndGet();
        }
        return l3Value;
    }

    /**
     * 根据翻译模式获取缓存
     * 按模式层级依次搜索：fast→expert→team, expert→team, team
     *
     * @param cacheKey    基础缓存键（不含模式标签）
     * @param currentMode 当前翻译模式（fast/expert/team）
     * @return 缓存的翻译结果，null 表示无缓存
     */
    public String getCacheByMode(String cacheKey, String currentMode) {
        List<String> modesToSearch = MODE_CACHE_HIERARCHY.getOrDefault(
            currentMode != null ? currentMode : "fast", List.of("fast"));

        for (String mode : modesToSearch) {
            String modeKey = cacheKey + "_" + mode;
            String result = getCache(modeKey);
            if (result != null) {
                log.debug("模式缓存命中: mode={}, key={}", mode, modeKey);
                return result;
            }
        }

        log.debug("模式缓存未命中: currentMode={}, 搜索模式={}", currentMode, modesToSearch);
        return null;
    }

    /**
     * 对同一 key 加锁，防止缓存击穿
     */
    private String loadWithLock(String cacheKey, String redisKey, java.util.function.Supplier<String> loader) {
        // 先检查是否已有线程在加载
        Object lock = loadingKeys.computeIfAbsent(cacheKey, k -> new Object());
        synchronized (lock) {
            try {
                return loader.get();
            } finally {
                loadingKeys.remove(cacheKey, lock);
            }
        }
    }

    /**
     * 从数据库查询缓存
     */
    private TranslationCache queryDatabaseCache(String cacheKey) {
        TranslationCache cache = translationCacheMapper.selectById(cacheKey);
        if (cache == null) {
            // 尝试通过 cacheKey 查询
            cache = translationCacheMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TranslationCache>()
                            .eq("cache_key", cacheKey)
                            .gt("expire_time", LocalDateTime.now())
            );
        }
        return cache;
    }

    // ==================== 核心写入方法 ====================

    /**
     * 保存翻译缓存到 L1 + L2 + L3
     *
     * @param cacheKey   缓存键
     * @param sourceText 源文本
     * @param targetText 目标文本
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @param engine     翻译引擎
     */
    public void putCache(String cacheKey, String sourceText, String targetText,
                         String sourceLang, String targetLang, String engine) {
        String redisKey = REDIS_KEY_PREFIX + cacheKey;

        // 1. 写入 L1 Caffeine
        caffeineCache.put(cacheKey, targetText);

        // 2. 写入 L2 Redis（带随机抖动防雪崩）
        long redisTtl = REDIS_CACHE_SECONDS + jitter(REDIS_JITTER_SECONDS);
        stringRedisTemplate.opsForValue().set(redisKey, targetText, Duration.ofSeconds(redisTtl));

        // 3. 异步写入 L3 数据库（带随机抖动防雪崩）
        saveToDatabaseAsync(cacheKey, sourceText, targetText, sourceLang, targetLang, engine);
    }

    /**
     * 保存翻译缓存到 L1 + L2 + L3（带模式标签）
     *
     * @param cacheKey   基础缓存键（不含模式标签）
     * @param sourceText 源文本
     * @param targetText 目标文本
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @param engine     翻译引擎
     * @param mode       翻译模式（fast/expert/team），为空则不附加模式标签
     */
    public void putCache(String cacheKey, String sourceText, String targetText,
                         String sourceLang, String targetLang, String engine, String mode) {
        String finalKey = (mode != null && !mode.isBlank()) ? cacheKey + "_" + mode : cacheKey;
        putCache(finalKey, sourceText, targetText, sourceLang, targetLang, engine);
    }

    /**
     * 保存空值占位（防缓存穿透）
     *
     * @param cacheKey 缓存键
     */
    public void putNullCache(String cacheKey) {
        String redisKey = REDIS_KEY_PREFIX + cacheKey;

        // L1 写入空值占位
        caffeineCache.put(cacheKey, NULL_PLACEHOLDER);

        // L2 Redis 写入空值占位（短过期）
        stringRedisTemplate.opsForValue().set(redisKey, NULL_PLACEHOLDER, Duration.ofSeconds(REDIS_NULL_SECONDS));

        log.debug("空值占位写入：{}", cacheKey);
    }

    /**
     * 仅保存到 L1 缓存（用于数据库缓存已存在的场景）
     */
    public void putToMemoryCache(String cacheKey, String targetText) {
        caffeineCache.put(cacheKey, targetText);
    }

    // ==================== 异步数据库写入 ====================

    /**
     * 异步保存缓存到数据库（使用虚拟线程，带重试机制）
     */
    private void saveToDatabaseAsync(String cacheKey, String sourceText, String targetText,
                                      String sourceLang, String targetLang, String engine) {
        Thread.startVirtualThread(() -> {
            int maxRetries = 2;
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    TranslationCache cache = new TranslationCache();
                    cache.setCacheKey(cacheKey);
                    cache.setSourceText(sourceText);
                    cache.setTargetText(targetText);
                    cache.setSourceLang(sourceLang);
                    cache.setTargetLang(targetLang);
                    cache.setEngine(engine);
                    // 24 小时 + 随机抖动（0~4 小时），防雪崩
                    long jitterHours = jitter(DB_JITTER_HOURS);
                    cache.setExpireTime(LocalDateTime.now().plusHours(DATABASE_CACHE_EXPIRY_HOURS + jitterHours));

                    translationCacheMapper.insertCache(cache);
                    log.debug("L3 数据库缓存保存成功：{}", cacheKey);
                    break;
                } catch (Exception e) {
                    if (attempt == maxRetries) {
                        log.warn("L3 数据库缓存保存失败（重试 {} 次后放弃）：{} - {}", maxRetries, cacheKey, e.getMessage());
                    } else {
                        try {
                            Thread.sleep(500L * (attempt + 1));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        });
    }

    // ==================== 辅助方法 ====================

    /**
     * 生成随机抖动值，范围为 [0, max)
     * 用于防止缓存雪崩
     */
    private long jitter(long maxSeconds) {
        return ThreadLocalRandom.current().nextLong(maxSeconds);
    }

    /**
     * 检查缓存是否存在（不返回值）
     */
    public boolean hasCache(String cacheKey) {
        return getCache(cacheKey) != null;
    }

    /**
     * 获取缓存命中率统计
     */
    public Map<String, Object> getCacheStats() {
        long total = l1HitCount.get() + l2HitCount.get() + l3HitCount.get() + cacheMissCount.get();
        double hitRate = total > 0 ? (double) (l1HitCount.get() + l2HitCount.get() + l3HitCount.get()) / total * 100 : 0;

        return Map.of(
                "l1Hits", l1HitCount.get(),
                "l2Hits", l2HitCount.get(),
                "l3Hits", l3HitCount.get(),
                "nullHits", nullHitCount.get(),
                "misses", cacheMissCount.get(),
                "hitRate", String.format("%.2f%%", hitRate),
                "totalRequests", total,
                "caffeineStats", caffeineCache.stats().toString()
        );
    }

    /**
     * 清理所有缓存（调试用）
     */
    public void clearAllCache() {
        caffeineCache.invalidateAll();
        log.info("L1 Caffeine 缓存已清空");

        try {
            stringRedisTemplate.delete(
                    stringRedisTemplate.keys(REDIS_KEY_PREFIX + "*")
            );
            log.info("L2 Redis 缓存已清空（前缀：{}）", REDIS_KEY_PREFIX);
        } catch (Exception e) {
            log.warn("L2 Redis 缓存清空失败：{}", e.getMessage());
        }

        try {
            int deleted = translationCacheMapper.delete(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TranslationCache>()
            );
            log.info("L3 数据库缓存已清空，删除 {} 条记录", deleted);
        } catch (Exception e) {
            log.warn("L3 数据库缓存清空失败：{}", e.getMessage());
        }
    }

    /**
     * 清理过期缓存
     * 定期调用此方法清理数据库中的过期缓存
     */
    public void cleanupExpiredCache() {
        try {
            int deleted = translationCacheMapper.delete(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TranslationCache>()
                            .lt("expire_time", LocalDateTime.now())
            );
            log.info("清理过期数据库缓存记录：{} 条", deleted);
        } catch (Exception e) {
            log.error("清理过期数据库缓存失败：{}", e.getMessage());
        }
    }

    /**
     * 预热缓存 - 批量加载热门翻译到 L1
     */
    public void warmupCache(Iterable<String> cacheKeys) {
        int count = 0;
        for (String cacheKey : cacheKeys) {
            TranslationCache dbCache = queryDatabaseCache(cacheKey);
            if (dbCache != null) {
                String value = dbCache.getTargetText();
                caffeineCache.put(cacheKey, value);
                // 同时回写 L2
                String redisKey = REDIS_KEY_PREFIX + cacheKey;
                stringRedisTemplate.opsForValue().setIfAbsent(
                        redisKey,
                        value,
                        Duration.ofSeconds(REDIS_CACHE_SECONDS + jitter(REDIS_JITTER_SECONDS))
                );
                count++;
            }
        }
        log.info("缓存预热完成：加载 {} 条记录到 L1+L2", count);
    }

    /**
     * 应用关闭时清理缓存资源
     */
    @PreDestroy
    public void shutdown() {
        log.info("翻译缓存服务关闭，清理 Caffeine 缓存...");
        caffeineCache.invalidateAll();
        log.info("翻译缓存服务已关闭");
    }
}
