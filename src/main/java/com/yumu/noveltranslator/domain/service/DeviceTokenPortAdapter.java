package com.yumu.noveltranslator.domain.service;

import com.yumu.noveltranslator.adapter.out.email.DeviceTokenService;
import com.yumu.noveltranslator.port.in.DeviceTokenPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeviceTokenPortAdapter implements DeviceTokenPort {

    private final DeviceTokenService deviceTokenService;

    @Override
    public void registerToken(String deviceId, String token) {
        deviceTokenService.registerToken(deviceId, token);
    }

    @Override
    public String getToken(String deviceId) {
        return deviceTokenService.getToken(deviceId);
    }

    @Override
    public void removeToken(String deviceId) {
        deviceTokenService.removeToken(deviceId);
    }
}
