package com.yumu.noveltranslator.adapter.in.service;

import com.yumu.noveltranslator.port.in.DeviceTokenPort;
import com.yumu.noveltranslator.port.out.DeviceTokenRegistrationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeviceTokenPortAdapter implements DeviceTokenPort {

    private final DeviceTokenRegistrationPort deviceTokenRegistrationPort;

    @Override
    public void registerToken(String deviceId, String token) {
        deviceTokenRegistrationPort.registerToken(deviceId, token);
    }

    @Override
    public String getToken(String deviceId) {
        return deviceTokenRegistrationPort.getToken(deviceId);
    }

    @Override
    public void removeToken(String deviceId) {
        deviceTokenRegistrationPort.removeToken(deviceId);
    }
}
