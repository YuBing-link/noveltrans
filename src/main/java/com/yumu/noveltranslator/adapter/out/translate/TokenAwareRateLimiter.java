package com.yumu.noveltranslator.adapter.out.translate;

import com.yumu.noveltranslator.properties.TranslationLimitProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Token-aware 速率限制：按用户 TPM（tokens per minute）配额限流，
 * 防止单个用户耗尽全局 LLM 算力池。
 */
@Service
@Slf4j
public class TokenAwareRateLimiter {

    private static final int WINDOW_SECONDS = 60;

    private final TranslationLimitProperties limitProperties;
    private final ConcurrentHashMap<String, SlidingWindowCounter> userCounters = new ConcurrentHashMap<>();

    public TokenAwareRateLimiter(TranslationLimitProperties limitProperties) {
        this.limitProperties = limitProperties;
    }

    /**
     * 估算文本的 token 数量。
     * CJK 字符约 0.5 token/字，西方字符约 1.3 token/字。
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int cjkCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCJK(c)) cjkCount++;
        }
        int westernCount = text.length() - cjkCount;
        return (int) Math.ceil(cjkCount * 0.5 + westernCount * 1.3);
    }

    private static boolean isCJK(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)   // CJK 统一表意文字
            || (c >= 0x3400 && c <= 0x4DBF)   // CJK 扩展 A
            || (c >= 0x3040 && c <= 0x309F)   // 平假名
            || (c >= 0x30A0 && c <= 0x30FF)   // 片假名
            || (c >= 0xAC00 && c <= 0xD7AF);  // 韩文谚文
    }

    public boolean tryConsume(String userId, String userLevel, int tokenCount) {
        int tpmLimit = getTpmLimit(userLevel);
        SlidingWindowCounter counter = userCounters.computeIfAbsent(userId, k -> new SlidingWindowCounter());
        return counter.tryConsume(tokenCount, tpmLimit);
    }

    public void refund(String userId, int tokenCount) {
        SlidingWindowCounter counter = userCounters.get(userId);
        if (counter != null) {
            counter.refund(tokenCount);
        }
    }

    private int getTpmLimit(String userLevel) {
        if (userLevel == null) return limitProperties.getAnonymousTpmLimit();
        return switch (userLevel.toLowerCase()) {
            case "max", "premium" -> limitProperties.getMaxTpmLimit();
            case "pro" -> limitProperties.getProTpmLimit();
            case "anonymous" -> limitProperties.getAnonymousTpmLimit();
            default -> limitProperties.getFreeTpmLimit();
        };
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanupIdleCounters() {
        long now = System.currentTimeMillis();
        int removed = 0;
        for (var it = userCounters.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (now - entry.getValue().getLastAccessTime() > 300_000) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.info("TokenAwareRateLimiter 清理了 {} 个空闲计数器", removed);
        }
    }

    /**
     * 单用户 60 秒滑动窗口 token 计数器。
     * 使用 60 个桶（每秒一个）实现 O(1) 过期清理。
     */
    private static class SlidingWindowCounter {
        private final int[] buckets = new int[WINDOW_SECONDS];
        private final long[] timestamps = new long[WINDOW_SECONDS];
        private final AtomicInteger totalInWindow = new AtomicInteger(0);
        private volatile long lastAccessTime;

        SlidingWindowCounter() {
            this.lastAccessTime = System.currentTimeMillis();
        }

        synchronized boolean tryConsume(int cost, int limit) {
            long now = System.currentTimeMillis();
            lastAccessTime = now;
            evictExpired(now);
            int current = totalInWindow.get();
            if (current + cost > limit) {
                return false;
            }
            int bucketIdx = (int) ((now / 1000) % WINDOW_SECONDS);
            buckets[bucketIdx] += cost;
            timestamps[bucketIdx] = now / 1000;
            totalInWindow.addAndGet(cost);
            return true;
        }

        synchronized void refund(int cost) {
            totalInWindow.updateAndGet(v -> Math.max(0, v - cost));
        }

        private void evictExpired(long nowMillis) {
            long nowSec = nowMillis / 1000;
            int total = 0;
            for (int i = 0; i < WINDOW_SECONDS; i++) {
                if (nowSec - timestamps[i] < WINDOW_SECONDS) {
                    total += buckets[i];
                } else {
                    buckets[i] = 0;
                    timestamps[i] = 0;
                }
            }
            totalInWindow.set(total);
        }

        long getLastAccessTime() {
            return lastAccessTime;
        }
    }
}
