package com.yumu.noveltranslator.adapter.out.redis;

import com.yumu.noveltranslator.port.out.BillingRepositoryPort;
import com.yumu.noveltranslator.port.out.QuotaPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 基于 Redis Lua 脚本的配额操作实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisQuotaAdapter implements QuotaPort {

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

    private final StringRedisTemplate stringRedisTemplate;
    private final BillingRepositoryPort billingPort;

    @SuppressWarnings("unchecked")
    private final DefaultRedisScript<List<Long>> consumeScript = new DefaultRedisScript<>(
            CONSUME_SCRIPT, (Class) List.class);
    private final DefaultRedisScript<Long> refundScript = new DefaultRedisScript<>(
            REFUND_SCRIPT, Long.class);

    @Override
    public boolean tryConsumeChars(String key, long quota, long cost, int ttl) {
        try {
            @SuppressWarnings("unchecked")
            List<Long> result = (List<Long>) stringRedisTemplate.execute(
                    consumeScript,
                    List.of(key),
                    String.valueOf(quota),
                    String.valueOf(cost),
                    String.valueOf(ttl));

            return result != null && result.get(0) == 1;
        } catch (Exception e) {
            log.error("Redis 配额消费失败: key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public long refundChars(String key, long refundAmount) {
        try {
            Long result = stringRedisTemplate.execute(
                    refundScript,
                    List.of(key),
                    String.valueOf(refundAmount));
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Redis 配额退款失败: key={}, error={}", key, e.getMessage());
            return 0;
        }
    }

    @Override
    @Async
    public void incrementDailyUsage(Long userId, LocalDate date, long cost) {
        try {
            billingPort.incrementQuotaUsage(userId, date, cost);
        } catch (Exception e) {
            log.warn("异步写入每日配额用量失败: userId={}, cost={}, error={}", userId, cost, e.getMessage());
        }
    }

    @Override
    @Async
    public void decrementDailyUsage(Long userId, LocalDate date, long amount) {
        try {
            billingPort.decrementQuotaUsage(userId, date, amount);
        } catch (Exception e) {
            log.warn("异步扣减每日配额用量失败: userId={}, amount={}, error={}", userId, amount, e.getMessage());
        }
    }
}
