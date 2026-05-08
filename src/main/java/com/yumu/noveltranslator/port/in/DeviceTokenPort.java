package com.yumu.noveltranslator.port.in;

/**
 * Device token management use-case port (plugin authentication).
 */
public interface DeviceTokenPort {
    void registerToken(String deviceId, String token);
    String getToken(String deviceId);
    void removeToken(String deviceId);
}
