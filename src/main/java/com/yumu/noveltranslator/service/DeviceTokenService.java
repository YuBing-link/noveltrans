package com.yumu.noveltranslator.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class DeviceTokenService {

    // 使用 Caffeine 缓存，设置30天过期
    private final Cache<String, String> deviceTokenCache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.DAYS)  // Token 30天过期
            .maximumSize(10000)                   // 最多缓存1万个设备
            .build();

    /**
     * 注册设备 Token
     */
    public void registerToken(String deviceId, String token) {
        deviceTokenCache.put(deviceId, token);
    }

    /**
     * 获取设备 Token
     */
    public String getToken(String deviceId) {
        return deviceTokenCache.getIfPresent(deviceId);
    }

    /**
     * 移除设备 Token
     */
    public void removeToken(String deviceId) {
        deviceTokenCache.invalidate(deviceId);
    }

    /**
     * 检查设备是否已登录
     */
    public boolean isDeviceLoggedIn(String deviceId) {
        return deviceTokenCache.getIfPresent(deviceId) != null;
    }
}
