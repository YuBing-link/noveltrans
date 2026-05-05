package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.entity.TokenBlacklist;
import com.yumu.noveltranslator.mapper.TokenBlacklistMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

/** Service for managing JWT token blacklist and revocation. */
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final TokenBlacklistMapper tokenBlacklistMapper;

    /**
     * Check if a JWT token has been blacklisted.
     * @param jwtToken the JWT token to check
     * @return true if the token is blacklisted
     */
    public boolean isBlacklisted(String jwtToken) {
        return tokenBlacklistMapper.findByToken(jwtToken) != null;
    }

    /**
     * Add a single JWT token to the blacklist.
     * @param jwtToken the JWT token to blacklist
     * @param email the user's email address
     * @param reason the reason for blacklisting
     * @param expiresAt the token's expiration time
     */
    public void blacklistToken(String jwtToken, String email, String reason, LocalDateTime expiresAt) {
        TokenBlacklist blacklist = new TokenBlacklist();
        blacklist.setToken(jwtToken);
        blacklist.setEmail(email);
        blacklist.setReason(reason);
        blacklist.setExpiresAt(expiresAt);
        blacklist.setCreatedAt(LocalDateTime.now());
        tokenBlacklistMapper.insert(blacklist);
    }

    /**
     * Revoke all tokens for a specific user.
     * @param email the user's email address
     * @param reason the reason for revocation
     */
    public void blacklistAllUserTokens(String email, String reason) {
        List<TokenBlacklist> existing = tokenBlacklistMapper.findByEmail(email);
        for (TokenBlacklist entry : existing) {
            entry.setReason(reason);
            tokenBlacklistMapper.updateById(entry);
        }
    }

    /**
     * Remove all expired blacklist entries from the database.
     */
    public void cleanupExpiredTokens() {
        tokenBlacklistMapper.deleteExpired();
    }
}
