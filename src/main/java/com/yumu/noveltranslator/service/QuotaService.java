package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.properties.TranslationLimitProperties;
import com.yumu.noveltranslator.mapper.QuotaUsageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.concurrent.TimeUnit;

/**
 * 字符配额服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final QuotaUsageMapper quotaUsageMapper;
    private final TranslationLimitProperties limitProperties;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 获取用户月度字符配额
     */
    public long getMonthlyQuota(String userLevel) {
        if (userLevel == null) return limitProperties.getFreeMonthlyChars();
        return switch (userLevel.toLowerCase()) {
            case "max" -> limitProperties.getMaxMonthlyChars();
            case "pro" -> limitProperties.getProMonthlyChars();
            default -> limitProperties.getFreeMonthlyChars();
        };
    }

    /**
     * 获取模式系数
     */
    public double getModeMultiplier(String mode) {
        if (mode == null) return limitProperties.getExpertModeMultiplier();
        return switch (mode.toLowerCase()) {
            case "fast" -> limitProperties.getFastModeMultiplier();
            case "team" -> limitProperties.getTeamModeMultiplier();
            default -> limitProperties.getExpertModeMultiplier();
        };
    }

    /**
     * 查询本月已用字符数
     */
    public long getUsedThisMonth(Long userId) {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        return quotaUsageMapper.getMonthlyUsage(userId, monthStart);
    }

    /**
     * 查询剩余字符数
     */
    public long getRemainingChars(Long userId, String userLevel) {
        long quota = getMonthlyQuota(userLevel);
        long used = getUsedThisMonth(userId);
        return Math.max(0, quota - used);
    }

    /**
     * 尝试消耗字符（带分布式锁防止并发刷量）
     * @return 是否成功扣减
     */
    public boolean tryConsumeChars(Long userId, String userLevel, long translatedCharCount, String mode) {
        double multiplier = getModeMultiplier(mode);
        long cost = (long) Math.ceil(translatedCharCount * multiplier);

        String lockKey = "quota:lock:" + userId + ":" + YearMonth.now();
        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 5, TimeUnit.SECONDS);
            if (acquired == null || !acquired) {
                try {
                    Thread.sleep(50L * (attempt + 1));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                continue;
            }

            try {
                long remaining = getRemainingChars(userId, userLevel);
                if (remaining < cost) {
                    log.warn("字符配额不足: userId={}, remaining={}, cost={}", userId, remaining, cost);
                    return false;
                }

                LocalDate today = LocalDate.now();
                quotaUsageMapper.incrementUsage(userId, today, cost);
                return true;
            } finally {
                stringRedisTemplate.delete(lockKey);
            }
        }

        log.error("无法获取配额锁: userId={}, 重试 {} 次后放弃", userId, maxRetries);
        return false;
    }

    /**
     * 退款：返还字符配额（翻译失败时调用）
     */
    public void refundChars(Long userId, long chars, String mode) {
        if (chars <= 0) return;
        double multiplier = getModeMultiplier(mode);
        long refundAmount = (long) Math.ceil(chars * multiplier);
        LocalDate today = LocalDate.now();
        quotaUsageMapper.decrementUsage(userId, today, refundAmount);
        log.info("配额退款: userId={}, amount={}", userId, refundAmount);
    }
}
