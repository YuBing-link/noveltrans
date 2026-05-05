package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.entity.TokenBlacklist;
import com.yumu.noveltranslator.mapper.TokenBlacklistMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TokenBlacklistService {

    @Autowired
    private TokenBlacklistMapper tokenBlacklistMapper;

    public boolean isBlacklisted(String jwtToken) {
        return tokenBlacklistMapper.findByToken(jwtToken) != null;
    }

    public void blacklistToken(String jwtToken, String email, String reason, LocalDateTime expiresAt) {
        TokenBlacklist blacklist = new TokenBlacklist();
        blacklist.setToken(jwtToken);
        blacklist.setEmail(email);
        blacklist.setReason(reason);
        blacklist.setExpiresAt(expiresAt);
        blacklist.setCreatedAt(LocalDateTime.now());
        tokenBlacklistMapper.insert(blacklist);
    }

    public void blacklistAllUserTokens(String email, String reason) {
        List<TokenBlacklist> existing = tokenBlacklistMapper.findByEmail(email);
        for (TokenBlacklist entry : existing) {
            entry.setReason(reason);
            tokenBlacklistMapper.updateById(entry);
        }
    }

    public void cleanupExpiredTokens() {
        tokenBlacklistMapper.deleteExpired();
    }
}
