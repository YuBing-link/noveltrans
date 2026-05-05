package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.entity.TokenBlacklist;
import com.yumu.noveltranslator.mapper.TokenBlacklistMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

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
     * Check if a user's email is globally blacklisted (all tokens revoked).
     * @param email the user's email address
     * @return true if all tokens for this user are revoked
     */
    public boolean isEmailBlacklisted(String email) {
        return tokenBlacklistMapper.isEmailBlacklisted(email) != null;
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
     * Revoke all tokens for a specific user by inserting an email-level blacklist entry.
     * Any JWT with this email will be rejected.
     * @param email the user's email address
     * @param reason the reason for revocation
     * @param expiresAt how long the blanket blacklist should last
     */
    public void blacklistAllUserTokens(String email, String reason, LocalDateTime expiresAt) {
        tokenBlacklistMapper.insertEmailBlacklist(email, reason, expiresAt, LocalDateTime.now());
    }

    /**
     * Remove all expired blacklist entries from the database.
     */
    public void cleanupExpiredTokens() {
        tokenBlacklistMapper.deleteExpired();
    }
}
