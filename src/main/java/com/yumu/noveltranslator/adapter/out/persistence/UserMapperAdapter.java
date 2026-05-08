package com.yumu.noveltranslator.adapter.out.persistence;

import com.yumu.noveltranslator.adapter.out.email.DeviceTokenService;
import com.yumu.noveltranslator.adapter.out.persistence.converter.UserConverter;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.ApiKeyMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.TenantMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.TokenBlacklistMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.UserMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.UserPlanHistoryMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.UserPreferenceMapper;
import com.yumu.noveltranslator.adapter.out.redis.TokenBlacklistService;
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
    public Optional<com.yumu.noveltranslator.domain.model.User> findByEmail(String email) {
        return Optional.ofNullable(UserConverter.toUserModel(userMapper.findByEmail(email)));
    }

    @Override
    public Optional<com.yumu.noveltranslator.domain.model.User> findById(Long id) {
        return Optional.ofNullable(UserConverter.toUserModel(userMapper.selectById(id)));
    }

    @Override
    public void save(com.yumu.noveltranslator.domain.model.User user) {
        userMapper.insert(UserConverter.toUserEntity(user));
    }

    @Override
    public void update(com.yumu.noveltranslator.domain.model.User user) {
        userMapper.updateById(UserConverter.toUserEntity(user));
    }

    @Override
    public int countActiveUsers() {
        return userMapper.countActiveUsers();
    }

    @Override
    public Optional<com.yumu.noveltranslator.domain.model.UserPreference> findPreferenceByUserId(Long userId) {
        return Optional.ofNullable(UserConverter.toPreferenceModel(userPreferenceMapper.findByUserId(userId)));
    }

    @Override
    public void savePreference(com.yumu.noveltranslator.domain.model.UserPreference preference) {
        userPreferenceMapper.insert(UserConverter.toPreferenceEntity(preference));
    }

    @Override
    public void updatePreference(com.yumu.noveltranslator.domain.model.UserPreference preference) {
        userPreferenceMapper.updateById(UserConverter.toPreferenceEntity(preference));
    }

    @Override
    public void savePlanHistory(com.yumu.noveltranslator.domain.model.UserPlanHistory history) {
        userPlanHistoryMapper.insert(UserConverter.toPlanHistoryEntity(history));
    }

    @Override
    public Optional<com.yumu.noveltranslator.domain.model.Tenant> findTenantById(Long id) {
        return Optional.ofNullable(UserConverter.toTenantModel(tenantMapper.selectById(id)));
    }

    @Override
    public void saveTenant(com.yumu.noveltranslator.domain.model.Tenant tenant) {
        var entity = UserConverter.toTenantEntity(tenant);
        tenantMapper.insert(entity);
        tenant.setId(entity.getId()); // 回填自增主键
    }

    @Override
    public void updateTenant(com.yumu.noveltranslator.domain.model.Tenant tenant) {
        tenantMapper.updateById(UserConverter.toTenantEntity(tenant));
    }

    @Override
    public Optional<com.yumu.noveltranslator.domain.model.ApiKey> findApiKeyByKey(String apiKey) {
        return Optional.ofNullable(UserConverter.toApiKeyModel(apiKeyMapper.findByApiKey(apiKey)));
    }

    @Override
    public Optional<com.yumu.noveltranslator.domain.model.ApiKey> findApiKeyById(Long id) {
        return Optional.ofNullable(UserConverter.toApiKeyModel(apiKeyMapper.selectById(id)));
    }

    @Override
    public List<com.yumu.noveltranslator.domain.model.ApiKey> findApiKeysByUserId(Long userId) {
        return UserConverter.toApiKeyModelList(apiKeyMapper.findByUserId(userId));
    }

    @Override
    public void saveApiKey(com.yumu.noveltranslator.domain.model.ApiKey apiKey) {
        apiKeyMapper.insert(UserConverter.toApiKeyEntity(apiKey));
    }

    @Override
    public void updateApiKey(com.yumu.noveltranslator.domain.model.ApiKey apiKey) {
        apiKeyMapper.updateById(UserConverter.toApiKeyEntity(apiKey));
    }

    @Override
    public void deleteApiKey(Long id) {
        apiKeyMapper.deleteById(id);
    }

    @Override
    public void incrementApiKeyUsage(Long id, long usage) {
        apiKeyMapper.incrementUsage(id, usage);
    }

    @Override
    public Optional<com.yumu.noveltranslator.domain.model.TokenBlacklist> findBlacklistByToken(String token) {
        return Optional.ofNullable(UserConverter.toBlacklistModel(tokenBlacklistMapper.findByToken(token)));
    }

    @Override
    public List<com.yumu.noveltranslator.domain.model.TokenBlacklist> findBlacklistByEmail(String email) {
        return UserConverter.toBlacklistModelList(tokenBlacklistMapper.findByEmail(email));
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
