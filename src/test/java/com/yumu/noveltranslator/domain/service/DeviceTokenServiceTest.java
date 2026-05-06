package com.yumu.noveltranslator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DeviceTokenService 测试")
class DeviceTokenServiceTest {

    private DeviceTokenService service;

    @BeforeEach
    void setUp() {
        service = new DeviceTokenService();
    }

    @Test
    void registerAndGetToken() {
        service.registerToken("device-1", "token-abc");
        assertEquals("token-abc", service.getToken("device-1"));
    }

    @Test
    void removeToken() {
        service.registerToken("device-1", "token-abc");
        service.removeToken("device-1");
        assertNull(service.getToken("device-1"));
    }

    @Test
    void isDeviceLoggedInTrue() {
        service.registerToken("device-1", "token-abc");
        assertTrue(service.isDeviceLoggedIn("device-1"));
    }

    @Test
    void isDeviceLoggedInFalse() {
        assertFalse(service.isDeviceLoggedIn("unknown-device"));
    }

    @Test
    void unknownDeviceReturnsNull() {
        assertNull(service.getToken("unknown-device"));
    }

    @Test
    void overwriteExistingToken() {
        service.registerToken("device-1", "token-old");
        service.registerToken("device-1", "token-new");
        assertEquals("token-new", service.getToken("device-1"));
    }
}
