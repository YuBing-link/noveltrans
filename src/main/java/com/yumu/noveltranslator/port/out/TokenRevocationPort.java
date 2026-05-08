package com.yumu.noveltranslator.port.out;

/**
 * 端口：吊销用户所有 JWT 令牌（黑名单 + 认证缓存失效）。
 * 订阅降级时调用，确保旧令牌立即失效。
 */
public interface TokenRevocationPort {
    void revokeAllTokens(Long userId, String email, String reason);
}
