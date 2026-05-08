package com.yumu.noveltranslator.application.service;

import com.yumu.noveltranslator.port.in.DeviceTokenPort;
import com.yumu.noveltranslator.port.out.DeviceTokenRegistrationPort;
import com.yumu.noveltranslator.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeviceTokenApplicationService implements DeviceTokenPort {

    private final DeviceTokenRegistrationPort deviceTokenRegistrationPort;
    private final JwtUtils jwtUtils;

    @Override
    public String generateAndRegisterToken(String deviceId, Long userId, String email, Long tenantId) {
        String token = jwtUtils.createToken(userId, email, tenantId);
        deviceTokenRegistrationPort.registerToken(deviceId, token);
        return token;
    }

    @Override
    public Map<String, String> verifyAndGetUserInfo(String deviceId) {
        String token = deviceTokenRegistrationPort.getToken(deviceId);
        if (token == null) {
            return null;
        }
        try {
            jwtUtils.verifyToken(token);
            Map<String, String> result = jwtUtils.getUserInfoFromToken(token);
            result.put("token", token);
            return result;
        } catch (Exception e) {
            deviceTokenRegistrationPort.removeToken(deviceId);
            return null;
        }
    }
}
