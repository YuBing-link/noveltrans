package com.yumu.noveltranslator.port.out;

import com.yumu.noveltranslator.domain.model.ApiKey;
import com.yumu.noveltranslator.domain.model.Tenant;
import com.yumu.noveltranslator.domain.model.TokenBlacklist;
import com.yumu.noveltranslator.domain.model.User;
import com.yumu.noveltranslator.domain.model.UserPlanHistory;
import com.yumu.noveltranslator.domain.model.UserPreference;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepositoryPort {

    // === User ===
    Optional<User> findByEmail(String email);
    Optional<User> findById(Long id);
    void save(User user);
    void update(User user);
    int countActiveUsers();

    // === UserPreference ===
    Optional<UserPreference> findPreferenceByUserId(Long userId);
    void savePreference(UserPreference preference);
    void updatePreference(UserPreference preference);

    // === UserPlanHistory ===
    void savePlanHistory(UserPlanHistory history);

    // === Tenant ===
    Optional<Tenant> findTenantById(Long id);
    void saveTenant(Tenant tenant);
    void updateTenant(Tenant tenant);

    // === ApiKey ===
    Optional<ApiKey> findApiKeyByKey(String apiKey);
    Optional<ApiKey> findApiKeyById(Long id);
    List<ApiKey> findApiKeysByUserId(Long userId);
    void saveApiKey(ApiKey apiKey);
    void updateApiKey(ApiKey apiKey);
    void deleteApiKey(Long id);
    void incrementApiKeyUsage(Long id, long usage);

    // === TokenBlacklist ===
    Optional<TokenBlacklist> findBlacklistByToken(String token);
    List<TokenBlacklist> findBlacklistByEmail(String email);
    void deleteExpiredBlacklist();
    void deleteBlacklistByEmail(String email);
    boolean isEmailBlacklisted(String email);
    void insertEmailBlacklist(String email, String reason, LocalDateTime expiresAt, LocalDateTime createdAt);
    void blacklistToken(String token, String email, String reason, LocalDateTime expiresAt);

    // === DeviceToken ===
    void removeDeviceToken(String deviceId);
}
