package com.yumu.noveltranslator.port.out;

public interface DeviceTokenRegistrationPort {
    void registerToken(String deviceId, String token);
    String getToken(String deviceId);
    void removeToken(String deviceId);
}
