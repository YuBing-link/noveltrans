package com.yumu.noveltranslator.adapter.out.redis;

import com.yumu.noveltranslator.port.out.TokenRevocationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 基于 Redis 和 MySQL 的令牌吊销实现。
 * 顺序至关重要：先写黑名单（持久化），再清认证缓存（失效）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTokenRevocationAdapter implements TokenRevocationPort {

    private final TokenBlacklistService tokenBlacklistService;
    private final JwtAuthCacheService jwtAuthCacheService;

    @Override
    public void revokeAllTokens(Long userId, String email, String reason) {
        LocalDateTime expiresAt = LocalDateTime.now(ZoneId.of("UTC")).plusDays(7);

        // Step 1: 写黑名单（MySQL + Redis Set）
        try {
            tokenBlacklistService.blacklistAllUserTokens(email, reason, expiresAt);
            log.info("已吊销用户 {} 的 JWT 令牌 — 黑名单写入成功（原因：{}）", email, reason);
        } catch (Exception e) {
            log.error("吊销用户 JWT 黑名单写入失败 — userId={}, email={}, reason={}. "
                    + "旧 JWT 在缓存过期前仍可使用。请手动处理。", userId, email, reason, e);
        }

        // Step 2: 清除 JWT 认证缓存（L1 Caffeine + L2 Redis + pub/sub）
        // 无论黑名单是否写入成功，都必须执行
        try {
            jwtAuthCacheService.invalidate(userId);
            log.info("已清除用户 {} 的 JWT 认证缓存", userId);
        } catch (Exception e) {
            log.error("清除用户 {} JWT 认证缓存失败 — 缓存中可能仍保留旧等级信息", userId, e);
        }
    }
}
