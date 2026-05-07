package com.yumu.noveltranslator.adapter.out.persistence;

import com.yumu.noveltranslator.adapter.out.email.DeviceTokenService;
import com.yumu.noveltranslator.adapter.out.redis.TokenBlacklistService;
import com.yumu.noveltranslator.adapter.out.persistence.entity.ApiKey;
import com.yumu.noveltranslator.adapter.out.persistence.entity.Tenant;
import com.yumu.noveltranslator.adapter.out.persistence.entity.TokenBlacklist;
import com.yumu.noveltranslator.adapter.out.persistence.entity.User;
import com.yumu.noveltranslator.adapter.out.persistence.entity.UserPlanHistory;
import com.yumu.noveltranslator.adapter.out.persistence.entity.UserPreference;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.ApiKeyMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.TenantMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.TokenBlacklistMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.UserMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.UserPlanHistoryMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.UserPreferenceMapper;
import com.yumu.noveltranslator.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserMapperAdapter implements UserRepositoryPort {

    private final UserMapper userMapper;
    private final UserPreferenceMapper userPreferenceMapper;
    private final UserPlanHistoryMapper userPlanHistoryMapper;
    private final TenantMapper tenantMapper;
    private final ApiKeyMapper apiKeyMapper;
    private final TokenBlacklistMapper tokenBlacklistMapper;
    private final TokenBlacklistService tokenBlacklistService;
    private final DeviceTokenService deviceTokenService;

    @Override
    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(userMapper.findByEmail(email));
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id));
    }

    @Override
    public void save(User user) {
        userMapper.insert(user);
    }

    @Override
    public void update(User user) {
        userMapper.updateById(user);
    }

    @Override
    public int countActiveUsers() {
        return userMapper.countActiveUsers();
    }

    @Override
    public Optional<UserPreference> findPreferenceByUserId(Long userId) {
        return Optional.ofNullable(userPreferenceMapper.findByUserId(userId));
    }

    @Override
    public void savePreference(UserPreference preference) {
        userPreferenceMapper.insert(preference);
    }

    @Override
    public void updatePreference(UserPreference preference) {
        userPreferenceMapper.updateById(preference);
    }

    @Override
    public void savePlanHistory(UserPlanHistory history) {
        userPlanHistoryMapper.insert(history);
    }

    @Override
    public Optional<Tenant> findTenantById(Long id) {
        return Optional.ofNullable(tenantMapper.selectById(id));
    }

    @Override
    public void saveTenant(Tenant tenant) {
        tenantMapper.insert(tenant);
    }

    @Override
    public void updateTenant(Tenant tenant) {
        tenantMapper.updateById(tenant);
    }

    @Override
    public Optional<ApiKey> findApiKeyByKey(String apiKey) {
        return Optional.ofNullable(apiKeyMapper.findByApiKey(apiKey));
    }

    @Override
    public List<ApiKey> findApiKeysByUserId(Long userId) {
        return apiKeyMapper.findByUserId(userId);
    }

    @Override
    public void incrementApiKeyUsage(Long id, long usage) {
        apiKeyMapper.incrementUsage(id, usage);
    }

    @Override
    public Optional<TokenBlacklist> findBlacklistByToken(String token) {
        return Optional.ofNullable(tokenBlacklistMapper.findByToken(token));
    }

    @Override
    public List<TokenBlacklist> findBlacklistByEmail(String email) {
        return tokenBlacklistMapper.findByEmail(email);
    }

    @Override
    public void deleteExpiredBlacklist() {
        tokenBlacklistMapper.deleteExpired();
    }

    @Override
    public void deleteBlacklistByEmail(String email) {
        tokenBlacklistMapper.deleteByUserEmail(email);
    }

    @Override
    public boolean isEmailBlacklisted(String email) {
        return tokenBlacklistMapper.isEmailBlacklisted(email) != null;
    }

    @Override
    public void insertEmailBlacklist(String email, String reason, LocalDateTime expiresAt, LocalDateTime createdAt) {
        tokenBlacklistMapper.insertEmailBlacklist(email, reason, expiresAt, createdAt);
    }

    @Override
    public void blacklistToken(String token, String email, String reason, LocalDateTime expiresAt) {
        tokenBlacklistService.blacklistToken(token, email, reason, expiresAt);
    }

    @Override
    public void removeDeviceToken(String deviceId) {
        deviceTokenService.removeToken(deviceId);
    }
}
