package com.yumu.noveltranslator.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class LoginRateLimiter {

    private final Map<String, AtomicInteger> loginAttempts = new ConcurrentHashMap<>();
    private final Map<String, Long> attemptWindows = new ConcurrentHashMap<>();
    private static final long WINDOW_MS = 60_000L;
    private static final int MAX_ATTEMPTS = 5;

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
}
