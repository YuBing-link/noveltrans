package com.yumu.noveltranslator.domain.service;

import com.yumu.noveltranslator.port.out.BillingRepositoryPort;
import com.yumu.noveltranslator.properties.TranslationLimitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 字符配额服务
 * <p>
 * 使用 Redis Lua 脚本实现原子化的配额检查与扣减，无需分布式锁。
 * MySQL 仅用于每日用量持久化（异步/最佳努力），不参与配额决策。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaService {

    /** Lua 脚本：原子检查配额 + INCR，无需外部锁 */
    private static final String CONSUME_SCRIPT = """
            local key = KEYS[1]
            local quota = tonumber(ARGV[1])
            local cost = tonumber(ARGV[2])
            local ttl   = tonumber(ARGV[3])

            local current = redis.call('GET', key)
            if current == false then
                current = 0
            else
                current = tonumber(current)
            end

            if current + cost > quota then
                return {0, current}
            end

            local new_val = redis.call('INCRBY', key, cost)
            redis.call('EXPIRE', key, ttl)
            return {1, new_val}
            """;

    /** Lua 脚本：原子扣减（退款），防止负数 */
    private static final String REFUND_SCRIPT = """
            local key = KEYS[1]
            local amount = tonumber(ARGV[1])

            local current = redis.call('GET', key)
            if current == false then
                return 0
            end
            current = tonumber(current)

            local new_val = math.max(0, current - amount)
            redis.call('SET', key, new_val)
            return new_val
            """;

    private final BillingRepositoryPort billingPort;
    private final TranslationLimitProperties limitProperties;
    private final StringRedisTemplate stringRedisTemplate;

    @SuppressWarnings("unchecked")
    private final DefaultRedisScript<List<Long>> consumeScript = new DefaultRedisScript<>(
            CONSUME_SCRIPT, (Class) List.class);
    private final DefaultRedisScript<Long> refundScript = new DefaultRedisScript<>(
            REFUND_SCRIPT, Long.class);

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

        try {
            @SuppressWarnings("unchecked")
            List<Long> result = (List<Long>) stringRedisTemplate.execute(
                    consumeScript,
                    List.of(key),
                    String.valueOf(quota),
                    String.valueOf(cost),
                    String.valueOf(ttl));

            if (result != null && result.get(0) == 1) {
                // 配额足够，异步写入 MySQL 日记录（不参与决策）
                incrementDailyUsageAsync(userId, cost);
                return true;
            }

            log.warn("字符配额不足: userId={}, cost={}, used={}",
                    userId, cost, result != null ? result.get(1) : -1);
            return false;
        } catch (Exception e) {
            // Redis 故障时回退到 MySQL 查询，避免 DoS
            log.error("Redis 配额检查失败: userId={}, 回退 MySQL: {}", userId, e.getMessage());
            return fallbackConsumeChars(userId, userLevel, cost);
        }
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
        incrementDailyUsageAsync(userId, cost);
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
        try {
            stringRedisTemplate.execute(
                    refundScript,
                    List.of(key),
                    String.valueOf(refundAmount));

            // 异步扣减 MySQL 记录
            decrementDailyUsageAsync(userId, refundAmount);
        } catch (Exception e) {
            log.error("Redis 退款失败: userId={}, 回退 MySQL: {}", userId, e.getMessage());
            decrementDailyUsageAsync(userId, refundAmount);
        }
    }

    /**
     * 异步写入 MySQL 每日用量记录（不参与配额决策）
     */
    @Async
    protected void incrementDailyUsageAsync(Long userId, long cost) {
        try {
            billingPort.incrementQuotaUsage(userId, LocalDate.now(), cost);
        } catch (Exception e) {
            log.warn("异步写入每日配额用量失败: userId={}, cost={}, error={}", userId, cost, e.getMessage());
        }
    }

    /**
     * 异步扣减 MySQL 每日用量记录
     */
    @Async
    protected void decrementDailyUsageAsync(Long userId, long amount) {
        try {
            billingPort.decrementQuotaUsage(userId, LocalDate.now(), amount);
        } catch (Exception e) {
            log.warn("异步扣减每日配额用量失败: userId={}, amount={}, error={}", userId, amount, e.getMessage());
        }
    }
}
