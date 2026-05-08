package com.yumu.noveltranslator.domain.service;

import com.yumu.noveltranslator.port.out.BillingRepositoryPort;
import com.yumu.noveltranslator.port.out.QuotaPort;
import com.yumu.noveltranslator.properties.TranslationLimitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * 字符配额服务
 * <p>
 * 纯业务逻辑，Redis 操作通过 QuotaPort 抽象。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final BillingRepositoryPort billingPort;
    private final TranslationLimitProperties limitProperties;
    private final QuotaPort quotaPort;

    /**
     * 获取用户月度配额（原始值）
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
     * 获取剩余可用字符数（月度配额 - 已用）
     */
    public long getRemainingChars(Long userId, String userLevel) {
        long quota = getMonthlyQuota(userLevel);
        long used = getUsedThisMonth(userId);
        return Math.max(0, quota - used);
    }

    /**
     * 查询本月已用字符数（从 MySQL，用于用户面板展示，最终一致性可接受）
     */
    public long getUsedThisMonth(Long userId) {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        return billingPort.getMonthlyQuotaUsage(userId, monthStart);
    }

    /**
     * 构建 Redis 配额 key: quota:chars:{userId}:{yearMonth}
     */
    private String quotaKey(Long userId) {
        return "quota:chars:" + userId + ":" + YearMonth.now();
    }

    /** 当配额超过此阈值时视为无限，跳过 Redis 检查 */
    private static final long UNLIMITED_QUOTA_THRESHOLD = 10_000_000L;

    /**
     * 尝试消耗字符（Lua 脚本原子检查 + INCR，无需分布式锁）
     * @return 是否成功扣减
     */
    public boolean tryConsumeChars(Long userId, String userLevel, long translatedCharCount, String mode) {
        double multiplier = getModeMultiplier(mode);
        long cost = (long) Math.ceil(translatedCharCount * multiplier);
        long quota = getMonthlyQuota(userLevel);

        // 如果配额超过阈值，视为无限，跳过 Redis 调用
        if (quota >= UNLIMITED_QUOTA_THRESHOLD) {
            return true;
        }

        String key = quotaKey(userId);
        // 本月剩余天数 + 10 天缓冲作为 Redis key 过期时间
        int ttl = (int) (ChronoUnit.DAYS.between(LocalDate.now(), YearMonth.now().atEndOfMonth()) + 10);

        if (quotaPort.tryConsumeChars(key, quota, cost, ttl)) {
            quotaPort.incrementDailyUsage(userId, LocalDate.now(), cost);
            return true;
        }

        log.warn("字符配额不足: userId={}, cost={}", userId, cost);
        // Redis 故障时回退到 MySQL 查询，避免 DoS
        return fallbackConsumeChars(userId, userLevel, cost);
    }

    /**
     * Redis 不可用时的降级方案
     */
    private boolean fallbackConsumeChars(Long userId, String userLevel, long cost) {
        long quota = getMonthlyQuota(userLevel);
        long used = billingPort.getMonthlyQuotaUsage(userId, LocalDate.now().withDayOfMonth(1));
        if (quota - used < cost) {
            return false;
        }
        quotaPort.incrementDailyUsage(userId, LocalDate.now(), cost);
        return true;
    }

    /**
     * 退款：返还字符配额（翻译失败时调用）
     */
    public void refundChars(Long userId, long chars, String mode) {
        if (chars <= 0) return;
        double multiplier = getModeMultiplier(mode);
        long refundAmount = (long) Math.ceil(chars * multiplier);

        String key = quotaKey(userId);
        quotaPort.refundChars(key, refundAmount);
        quotaPort.decrementDailyUsage(userId, LocalDate.now(), refundAmount);
    }
}
