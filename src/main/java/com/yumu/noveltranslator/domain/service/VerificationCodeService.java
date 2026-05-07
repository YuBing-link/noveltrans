package com.yumu.noveltranslator.domain.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Verification code generation, storage, and verification domain service.
 * Extracted from EmailVerificationCodeUtil to separate concerns:
 * this handles code lifecycle, while EmailPort handles email delivery.
 */
@Service
@Slf4j
public class VerificationCodeService {

    private Cache<String, String> verificationCodeCache;
    private Cache<String, Long> lastSendTimeCache;

    @Value("${email.verification.code.validity:1}")
    private long validity;

    @Value("${email.verification.code.length:6}")
    private int codeLength;

    private static final Random RANDOM = new Random();

    @PostConstruct
    public void init() {
        verificationCodeCache = Caffeine.newBuilder()
                .expireAfterWrite(validity, TimeUnit.MINUTES)
                .maximumSize(10000)
                .build();
        lastSendTimeCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(10000)
                .build();
    }

    /**
     * Generate a random numeric code and store it in cache.
     * @return the generated code
     */
    public String generateAndStore(String email) {
        String code = generateCode();
        verificationCodeCache.put(email + ":" + code, code);
        lastSendTimeCache.put(email, System.currentTimeMillis());
        log.info("验证码已生成: {}", email);
        return code;
    }

    /**
     * Verify the code for the given email.
     * @return true if valid and not expired
     */
    public boolean verifyCode(String email, String code) {
        String cachedCode = verificationCodeCache.getIfPresent(email + ":" + code);
        if (cachedCode == null) {
            log.warn("验证码已过期或不存在，邮箱: {}", email);
            return false;
        }
        verificationCodeCache.invalidate(email + ":" + code);
        log.info("邮箱验证成功: {}", email);
        return true;
    }

    /**
     * Check if a code was sent recently (within 60 seconds).
     * @return true if rate-limited
     */
    public boolean isRateLimited(String email) {
        Long lastSendTime = lastSendTimeCache.getIfPresent(email);
        if (lastSendTime != null) {
            long elapsed = System.currentTimeMillis() - lastSendTime;
            return elapsed < 60000;
        }
        return false;
    }

    public Long getLastSendTime(String email) {
        return lastSendTimeCache.getIfPresent(email);
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < codeLength; i++) {
            code.append(RANDOM.nextInt(10));
        }
        return code.toString();
    }
}
