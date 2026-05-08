package com.yumu.noveltranslator.port.in;

import java.util.Map;

/**
 * Device token management use-case port (plugin authentication).
 */
public interface DeviceTokenPort {
    void registerToken(String deviceId, String token);
    String getToken(String deviceId);
    void removeToken(String deviceId);

    String generateAndRegisterToken(String deviceId, Long userId, String email, Long tenantId);
    Map<String, String> verifyAndGetUserInfo(String deviceId);
}
