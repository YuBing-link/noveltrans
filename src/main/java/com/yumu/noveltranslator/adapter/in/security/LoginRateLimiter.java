package com.yumu.noveltranslator.adapter.in.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Rate limiter for login attempts per IP address. Prevents brute-force attacks. */
@Component
@Slf4j
public class LoginRateLimiter {

    private static final long WINDOW_MS = 60_000L;
    private static final int MAX_ATTEMPTS = 5;

    private final Map<String, AtomicInteger> loginAttempts = new ConcurrentHashMap<>();
    private final Map<String, Long> attemptWindows = new ConcurrentHashMap<>();

    /**
     * Check if a login attempt from the given IP should be allowed.
     * Allows up to 5 attempts per 60-second window per IP.
     * @param ip the client IP address
     * @return true if the attempt is allowed
     */
    public boolean allowLoginAttempt(String ip) {
        long now = System.currentTimeMillis();
        Long windowStart = attemptWindows.get(ip);
        if (windowStart == null || now - windowStart > WINDOW_MS) {
            loginAttempts.put(ip, new AtomicInteger(0));
            attemptWindows.put(ip, now);
        }
        int count = loginAttempts.get(ip).incrementAndGet();
        if (count > MAX_ATTEMPTS) {
            log.warn("IP {} 登录尝试超过限制 ({} 次/分钟)", ip, count);
            return false;
        }
        return true;
    }

    /**
     * 定期清理过期的 IP 记录，防止全局单例长期运行导致内存泄漏。
     */
    @Scheduled(fixedRate = 300_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        int removed = 0;
        for (var it = attemptWindows.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (now - entry.getValue() > WINDOW_MS * 10) {
                it.remove();
                loginAttempts.remove(entry.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            log.info("LoginRateLimiter 清理了 {} 个过期的 IP 登录尝试记录", removed);
        }
    }
}
