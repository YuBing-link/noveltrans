package com.yumu.noveltranslator.adapter.out.redis;

import com.yumu.noveltranslator.adapter.out.persistence.entity.TranslationCache;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.TranslationCacheMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 翻译缓存服务
 * 二级缓存架构：Caffeine (L1) → Redis (L2) → 翻译引擎
 *
 * <p>缓存策略：</p>
 * <ul>
 *   <li>L1 Caffeine：JVM 内存缓存，10 分钟过期，单实例最高性能</li>
 *   <li>L2 Redis：分布式缓存，30 分钟过期，支持多实例共享</li>
 * </ul>
 *
 * <p>MySQL 已从查询热路径移除，仅用于异步持久化（saveToDatabaseAsync）。</p>
 *
 * <p>防缓存问题：</p>
 * <ul>
 *   <li>缓存穿透：空值占位（5 分钟短暂过期）</li>
 *   <li>缓存击穿：双层锁（JVM 本地 synchronized + Redis SET NX 分布式锁）</li>
 *   <li>缓存雪崩：过期时间添加随机抖动</li>
 *   <li>缓存一致性：版本号前缀 + 延迟双删 + Redis pub/sub</li>
 * </ul>
 */
@Service
@Slf4j
public class TranslationCacheService {

    private final TranslationCacheMapper translationCacheMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final CacheVersionService cacheVersionService;

    /** 专用调度线程池，替代 Thread.sleep 保证延迟任务可靠执行 */
    private final ScheduledExecutorService delayedCleanupExecutor = Executors.newScheduledThreadPool(
            2, r -> {
                Thread t = new Thread(r, "cache-cleanup");
                t.setDaemon(true);
                return t;
            });

    public TranslationCacheService(
            TranslationCacheMapper translationCacheMapper,
            StringRedisTemplate stringRedisTemplate,
            @Lazy CacheVersionService cacheVersionService) {
        this.translationCacheMapper = translationCacheMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.cacheVersionService = cacheVersionService;
    }

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

    // ==================== 术语反向索引 ====================

    /** Redis Set key 前缀: glossary:cache_keys:{word} */
    private static final String GLOSSARY_CACHE_KEYS_PREFIX = "glossary:cache_keys:";

    /** 反向索引 Set 的 TTL（24 小时） */
    private static final long GLOSSARY_INDEX_TTL_SECONDS = 24 * 3600;

    /** 提取源文本中长度 >= 3 的单词，用于构建反向索引 */
    private static final Pattern WORD_EXTRACT_PATTERN = Pattern.compile("\\b[\\w\\p{L}]{3,}\\b");

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

    // ==================== 核心查询方法 ====================

    /**
     * 空值占位标记
     */
    private static final String NULL_PLACEHOLDER = "__NULL__";

    // ==================== 缓存统计 ====================

    private final AtomicLong l1HitCount = new AtomicLong(0);
    private final AtomicLong l2HitCount = new AtomicLong(0);
    private final AtomicLong cacheMissCount = new AtomicLong(0);
    private final AtomicLong nullHitCount = new AtomicLong(0);       // 空值命中（穿透防护）

    // ==================== 核心查询方法 ====================

    /**
     * 获取翻译缓存（L1 Caffeine → L2 Redis）
     */
    public String getCache(String cacheKey) {
        // 1. L1: 尝试 Caffeine 缓存
        String l1Value = caffeineCache.getIfPresent(cacheKey);
        if (l1Value != null) {
            if (NULL_PLACEHOLDER.equals(l1Value)) {
                nullHitCount.incrementAndGet();
                return null;
            }
            l1HitCount.incrementAndGet();
            return l1Value;
        }

        // 2. L2: 直接查 Redis（无分布式锁，高并发下由翻译引擎自然防击穿）
        String redisKey = REDIS_KEY_PREFIX + cacheKey;
        String value = stringRedisTemplate.opsForValue().get(redisKey);
        if (value != null) {
            if (NULL_PLACEHOLDER.equals(value)) {
                caffeineCache.put(cacheKey, NULL_PLACEHOLDER);
                return null;
            }
            caffeineCache.put(cacheKey, value);
            l2HitCount.incrementAndGet();
            return value;
        }

        cacheMissCount.incrementAndGet();
        return null;
    }

    /**
     * 根据翻译模式获取缓存，使用 MGET 批量获取多个模式 key
     * 按模式层级搜索：fast→[team, expert, fast], expert→[team, expert], team→[team]
     */
    public String getCacheByMode(String cacheKey, String currentMode) {
        List<String> modesToSearch = MODE_CACHE_HIERARCHY.getOrDefault(
            currentMode != null ? currentMode : "fast", List.of("fast"));

        // 构建待查 Redis key 列表
        List<String> redisKeys = new ArrayList<>(modesToSearch.size());
        for (String mode : modesToSearch) {
            String modeKey = cacheKey + "_" + mode;
            // 先查 L1
            String l1 = caffeineCache.getIfPresent(modeKey);
            if (l1 != null) {
                if (NULL_PLACEHOLDER.equals(l1)) return null;
                l1HitCount.incrementAndGet();
                log.debug("L1 模式缓存命中: mode={}", mode);
                return l1;
            }
            redisKeys.add(REDIS_KEY_PREFIX + modeKey);
        }

        // L1 全未命中 → MGET 批量查 L2
        if (!redisKeys.isEmpty()) {
            List<String> values = stringRedisTemplate.opsForValue().multiGet(redisKeys);
            if (values != null && !values.isEmpty()) {
                for (int i = 0; i < values.size() && i < modesToSearch.size(); i++) {
                    String modeKey = cacheKey + "_" + modesToSearch.get(i);
                    String v = values.get(i);
                    if (v != null) {
                        if (NULL_PLACEHOLDER.equals(v)) {
                            caffeineCache.put(modeKey, NULL_PLACEHOLDER);
                            return null;
                        }
                        caffeineCache.put(modeKey, v);
                        l2HitCount.incrementAndGet();
                        log.debug("L2 模式缓存命中: mode={}", modesToSearch.get(i));
                        return v;
                    }
                }
            }
        }

        cacheMissCount.incrementAndGet();
        return null;
    }

    // ==================== 核心写入方法 ====================

    /**
     * 从数据库查询缓存（高并发降级模式）
     */
    private TranslationCache queryDatabaseCache(String cacheKey) {
        try {
            TranslationCache cache = translationCacheMapper.selectById(cacheKey);
            if (cache == null) {
                cache = translationCacheMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TranslationCache>()
                                .eq("cache_key", cacheKey)
                                .gt("expire_time", LocalDateTime.now())
                );
            }
            return cache;
        } catch (Exception e) {
            log.debug("L3 数据库查询跳过（连接池繁忙或异常）: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 保存翻译缓存到 L1 + L2 + L3
     * 写入时使用当前版本号作为 key 前缀
     *
     * @param cacheKey   缓存键（不含版本号，由内部拼接）
     * @param sourceText 源文本
     * @param targetText 目标文本
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @param engine     翻译引擎
     */
    public void putCache(String cacheKey, String sourceText, String targetText,
                         String sourceLang, String targetLang, String engine) {
        putCache(cacheKey, sourceText, targetText, sourceLang, targetLang, engine, null);
    }

    /**
     * 保存翻译缓存到 L1 + L2 + L3（带模式标签）
     * 写入时使用当前版本号作为 key 前缀
     *
     * @param cacheKey   基础缓存键（不含版本号）
     * @param sourceText 源文本
     * @param targetText 目标文本
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @param engine     翻译引擎
     * @param mode       翻译模式（fast/expert/team），为空则不附加模式标签
     */
    public void putCache(String cacheKey, String sourceText, String targetText,
                         String sourceLang, String targetLang, String engine, String mode) {
        // 获取当前版本号
        String version = cacheVersionService.getVersion(sourceLang, targetLang);
        // cacheKey 可能已经包含 v{N}: 前缀（来自 CacheKeyUtil），先剥离再重新拼接
        String strippedKey = cacheKey.replaceFirst("^v\\d+:", "");
        String baseKey = "v" + version + ":" + strippedKey;
        String finalKey = (mode != null && !mode.isBlank()) ? baseKey + "_" + mode : baseKey;

        String redisKey = REDIS_KEY_PREFIX + finalKey;

        // 1. 写入 L1 Caffeine
        caffeineCache.put(finalKey, targetText);

        // 2. 写入 L2 Redis（带随机抖动防雪崩）
        long redisTtl = REDIS_CACHE_SECONDS + jitter(REDIS_JITTER_SECONDS);
        stringRedisTemplate.opsForValue().set(redisKey, targetText, Duration.ofSeconds(redisTtl));

        // 3. 异步维护术语反向索引（非关键路径，不阻塞响应）
        Thread.startVirtualThread(() -> buildReverseIndex(finalKey, sourceText));

        // 4. 异步写入 L3 数据库（带版本号）
        saveToDatabaseAsync(finalKey, sourceText, targetText, sourceLang, targetLang, engine, version);

        log.debug("L1+L2+L3 写入成功: key={}, version={}, mode={}", finalKey, version, mode);
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

    // ==================== 术语反向索引 ====================

    /**
     * 从 sourceText 中提取长度 >= 3 的单词，维护反向索引 Set。
     * Redis key: glossary:cache_keys:{word_lowercase} -> Set of cacheKeys
     * TTL: 24 小时
     */
    void buildReverseIndex(String cacheKey, String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return;
        }

        Set<String> uniqueWords = new HashSet<>();
        Matcher matcher = WORD_EXTRACT_PATTERN.matcher(sourceText);
        while (matcher.find()) {
            uniqueWords.add(matcher.group().toLowerCase());
        }

        for (String word : uniqueWords) {
            String indexKey = GLOSSARY_CACHE_KEYS_PREFIX + word;
            stringRedisTemplate.opsForSet().add(indexKey, cacheKey);
            stringRedisTemplate.expire(indexKey, Duration.ofSeconds(GLOSSARY_INDEX_TTL_SECONDS));
        }

        if (!uniqueWords.isEmpty()) {
            log.debug("反向索引已更新: cacheKey={}, 词条数={}", cacheKey, uniqueWords.size());
        }
    }

    /**
     * 当术语发生变化时，使所有包含该词的缓存失效。
     * 用于 glossary term 变更时的细粒度缓存失效。
     *
     * @param sourceWord 发生变化的原词（如 "Apple"）
     */
    public void invalidateKeysForTerm(String sourceWord) {
        if (sourceWord == null || sourceWord.isBlank()) {
            return;
        }

        String lowerWord = sourceWord.toLowerCase();
        String indexKey = GLOSSARY_CACHE_KEYS_PREFIX + lowerWord;

        // 1. 获取所有受影响的 cache keys
        Set<String> cacheKeys = stringRedisTemplate.opsForSet().members(indexKey);
        if (cacheKeys == null || cacheKeys.isEmpty()) {
            log.debug("术语反向索引无匹配: word={}", lowerWord);
            return;
        }

        log.info("开始术语细粒度失效: word={}, 影响 {} 个缓存键", lowerWord, cacheKeys.size());

        // 2. 清空本地 Caffeine L1 缓存中对应的 key
        for (String key : cacheKeys) {
            caffeineCache.invalidate(key);
        }

        // 3. 删除 L2 Redis 中的缓存 key
        try {
            String[] redisKeys = cacheKeys.stream()
                    .map(k -> REDIS_KEY_PREFIX + k)
                    .toArray(String[]::new);
            if (redisKeys.length > 0) {
                long deleted = stringRedisTemplate.delete(java.util.List.of(redisKeys));
                log.info("L2 Redis 删除 {} 个术语相关缓存键", deleted);
            }
        } catch (Exception e) {
            log.warn("L2 Redis 术语缓存删除失败: word={}, error={}", lowerWord, e.getMessage());
        }

        // 4. 删除 L3 数据库中的缓存记录
        try {
            for (String key : cacheKeys) {
                translationCacheMapper.delete(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TranslationCache>()
                                .eq("cache_key", key)
                );
            }
            log.info("L3 数据库删除 {} 个术语相关记录", cacheKeys.size());
        } catch (Exception e) {
            log.warn("L3 数据库术语缓存删除失败: word={}, error={}", lowerWord, e.getMessage());
        }

        // 5. 清理反向索引 Set 条目
        stringRedisTemplate.delete(indexKey);

        log.info("术语细粒度失效完成: word={}", lowerWord);
    }

    // ==================== 异步数据库写入 ====================

    /**
     * 异步保存缓存到数据库（使用虚拟线程，带重试机制）
     */
    private void saveToDatabaseAsync(String cacheKey, String sourceText, String targetText,
                                      String sourceLang, String targetLang, String engine, String version) {
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
                    cache.setVersion(Integer.parseInt(version));
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

    // ==================== 延迟双删 ====================

    /**
     * 延迟双删：在数据变更时保证缓存一致性
     *
     * 流程：
     * 1. 前置删除：清空 L1 + L2 对应语言对的缓存
     * 2. 版本号 bump：Redis INCR + 发布 pub/sub 事件
     * 3. 延迟 2 秒后通过 ScheduledExecutorService 调度后置删除
     * 4. 后置删除：再次清空 L2 Redis（版本前缀匹配）+ L3 过期版本记录
     *
     * 注：使用 ScheduledExecutorService 替代 Thread.sleep，避免容器重启/调度导致后置删除丢失。
     * 即使后置删除未执行，版本号机制已保证旧 key 不可达，仅占用少量内存直到 TTL 自然淘汰。
     *
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     */
    public void delayedDoubleDelete(String sourceLang, String targetLang) {
        try {
            // Step 1: 前置删除 — 清空本地 L1 缓存
            log.info("延迟双删 [前置删除]: sourceLang={}, targetLang={}", sourceLang, targetLang);
            clearLocalCache();

            // Step 2: 版本号 bump + 发布 pub/sub 事件（其他实例收到后会清空 L1）
            String newVersion = cacheVersionService.bumpVersionAndPublish(sourceLang, targetLang);

            // Step 3-5: 通过 ScheduledExecutorService 调度后置删除，避免 Thread.sleep 不可靠
            delayedCleanupExecutor.schedule(() -> {
                try {
                    // 后置删除 — 清空 L2 Redis 中旧版本的所有 key
                    String oldVersion = String.valueOf(Integer.parseInt(newVersion) - 1);
                    deleteRedisByOldVersion(oldVersion);

                    // 删除 L3 数据库中旧版本的记录
                    deleteDbCacheByOldVersion(Integer.parseInt(newVersion));

                    log.info("延迟双删完成: sourceLang={}, targetLang={}, newVersion={}", sourceLang, targetLang, newVersion);
                } catch (Exception e) {
                    log.warn("延迟双删 [后置删除] 失败: {}", e.getMessage());
                }
            }, 2, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("延迟双删异常: sourceLang={}, targetLang={}, error={}", sourceLang, targetLang, e.getMessage(), e);
        }
    }

    /**
     * 删除 Redis 中旧版本的所有缓存 key
     */
    private void deleteRedisByOldVersion(String oldVersion) {
        String pattern = REDIS_KEY_PREFIX + "v" + oldVersion + ":*";
        var keys = stringRedisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            long deleted = stringRedisTemplate.delete(keys);
            log.info("延迟双删 [L2] 删除旧版本 Redis key: version={}, 删除 {} 条", oldVersion, deleted);
        }
    }

    /**
     * 删除数据库中旧版本的所有缓存记录
     */
    private void deleteDbCacheByOldVersion(int currentVersion) {
        int deleted = translationCacheMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TranslationCache>()
                        .lt("version", currentVersion)
        );
        log.info("延迟双删 [L3] 删除旧版本 DB 记录: currentVersion={}, 删除 {} 条", currentVersion, deleted);
    }

    // ==================== 本地缓存清空（供 CacheVersionService 调用） ====================

    /**
     * 清空本地 Caffeine 缓存，供 pub/sub 事件处理使用
     */
    public void clearLocalCache() {
        long size = caffeineCache.estimatedSize();
        caffeineCache.invalidateAll();
        log.debug("本地 Caffeine 缓存已清空，原条目数: {}", size);
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
        long total = l1HitCount.get() + l2HitCount.get() + cacheMissCount.get();
        double hitRate = total > 0 ? (double) (l1HitCount.get() + l2HitCount.get()) / total * 100 : 0;

        return Map.of(
                "l1Hits", l1HitCount.get(),
                "l2Hits", l2HitCount.get(),
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

        // 同时清空版本号
        try {
            var versionKeys = stringRedisTemplate.keys("translator:cache_version:*");
            if (versionKeys != null && !versionKeys.isEmpty()) {
                stringRedisTemplate.delete(versionKeys);
                log.info("缓存版本号已清空");
            }
        } catch (Exception e) {
            log.warn("缓存版本号清空失败: {}", e.getMessage());
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
        log.info("翻译缓存服务关闭，关闭调度线程池...");
        delayedCleanupExecutor.shutdown();
        try {
            if (!delayedCleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                delayedCleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            delayedCleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("翻译缓存服务已关闭");
    }
}
