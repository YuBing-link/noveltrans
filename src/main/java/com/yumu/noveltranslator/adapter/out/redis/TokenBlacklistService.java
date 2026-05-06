package com.yumu.noveltranslator.adapter.out.redis;

import com.yumu.noveltranslator.adapter.out.persistence.entity.TokenBlacklist;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.TokenBlacklistMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * JWT Token 黑名单服务。
 * <p>
 * 优化（ADR-008）：黑名单检查走 Redis 缓存，避免热路径查 MySQL。
 * - Redis Set: jwt:blacklist:tokens → 存储被吊销的 token hash
 * - Redis Set: jwt:blacklist:emails → 存储被全局吊销的 email
 * - MySQL 仅用于写入黑名单，读取全部走 Redis
 */
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final TokenBlacklistMapper tokenBlacklistMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @PostConstruct
    public void init() {
        loadBlacklistToRedis();
    }

    private static final String TOKEN_BLACKLIST_KEY = "jwt:blacklist:tokens";
    private static final String EMAIL_BLACKLIST_KEY = "jwt:blacklist:emails";

    /**
     * 检查 JWT token 是否被吊销（Redis 优先，MySQL 兜底）
     */
    public boolean isBlacklisted(String jwtToken) {
        try {
            // Redis 检查
            Boolean isMember = stringRedisTemplate.opsForSet().isMember(TOKEN_BLACKLIST_KEY, jwtToken);
            if (Boolean.TRUE.equals(isMember)) {
                return true;
            }
        } catch (Exception e) {
            // Redis 不可用时降级走 MySQL
            return tokenBlacklistMapper.findByToken(jwtToken) != null;
        }

        // 如果 TokenBlacklistMapper 有数据但 Redis 未同步，兜底查 MySQL
        // 但为了性能，只在启动时做一次同步，运行时仅靠 Redis
        return false;
    }

    /**
     * 检查 email 是否被全局吊销（Redis 优先，MySQL 兜底）
     */
    public boolean isEmailBlacklisted(String email) {
        try {
            Boolean isMember = stringRedisTemplate.opsForSet().isMember(EMAIL_BLACKLIST_KEY, email);
            return Boolean.TRUE.equals(isMember);
        } catch (Exception e) {
            return tokenBlacklistMapper.isEmailBlacklisted(email) != null;
        }
    }

    /**
     * 将 JWT token 加入黑名单（同时写 Redis + MySQL）
     */
    public void blacklistToken(String jwtToken, String email, String reason, LocalDateTime expiresAt) {
        // MySQL
        TokenBlacklist blacklist = new TokenBlacklist();
        blacklist.setToken(jwtToken);
        blacklist.setEmail(email);
        blacklist.setReason(reason);
        blacklist.setExpiresAt(expiresAt);
        blacklist.setCreatedAt(LocalDateTime.now());
        tokenBlacklistMapper.insert(blacklist);

        // Redis
        stringRedisTemplate.opsForSet().add(TOKEN_BLACKLIST_KEY, jwtToken);
    }

    /**
     * 吊销某用户的所有 token（Redis + MySQL）
     */
    public void blacklistAllUserTokens(String email, String reason, LocalDateTime expiresAt) {
        tokenBlacklistMapper.insertEmailBlacklist(email, reason, expiresAt, LocalDateTime.now());
        stringRedisTemplate.opsForSet().add(EMAIL_BLACKLIST_KEY, email);
    }

    /**
     * 从数据库加载所有黑名单到 Redis（应用启动时调用）
     */
    public void loadBlacklistToRedis() {
        try {
            var tokens = tokenBlacklistMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TokenBlacklist>()
                            .gt("expires_at", LocalDateTime.now())
            );
            if (!tokens.isEmpty()) {
                String[] tokenArr = tokens.stream()
                        .map(TokenBlacklist::getToken)
                        .filter(t -> t != null && !t.isEmpty())
                        .toArray(String[]::new);
                if (tokenArr.length > 0) {
                    stringRedisTemplate.opsForSet().add(TOKEN_BLACKLIST_KEY, tokenArr);
                }
                String[] emailArr = tokens.stream()
                        .map(TokenBlacklist::getEmail)
                        .filter(e -> e != null && !e.isEmpty())
                        .toArray(String[]::new);
                if (emailArr.length > 0) {
                    stringRedisTemplate.opsForSet().add(EMAIL_BLACKLIST_KEY, emailArr);
                }
            }
        } catch (Exception e) {
            // 忽略加载失败
        }
    }

    /**
     * 清理过期黑名单条目
     */
    public void cleanupExpiredTokens() {
        tokenBlacklistMapper.deleteExpired();
    }
}
