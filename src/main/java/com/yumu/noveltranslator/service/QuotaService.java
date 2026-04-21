package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.properties.TranslationLimitProperties;
import com.yumu.noveltranslator.mapper.QuotaUsageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 字符配额服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final QuotaUsageMapper quotaUsageMapper;
    private final TranslationLimitProperties limitProperties;

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
     * 尝试消耗字符
     * @return 是否成功扣减
     */
    public boolean tryConsumeChars(Long userId, String userLevel, long translatedCharCount, String mode) {
        double multiplier = getModeMultiplier(mode);
        long cost = (long) Math.ceil(translatedCharCount * multiplier);

        long remaining = getRemainingChars(userId, userLevel);
        if (remaining < cost) {
            log.warn("字符配额不足: userId={}, remaining={}, cost={}", userId, remaining, cost);
            return false;
        }

        LocalDate today = LocalDate.now();
        quotaUsageMapper.incrementUsage(userId, today, cost);
        return true;
    }
}
