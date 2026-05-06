package com.yumu.noveltranslator.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SecurityPermitAllPaths 测试")
class SecurityPermitAllPathsTest {

    @Test
    void login路径允许() {
        assertTrue(SecurityPermitAllPaths.isPermitted("/user/login"));
    }

    @Test
    void register路径允许() {
        assertTrue(SecurityPermitAllPaths.isPermitted("/user/register"));
    }

    @Test
    void health路径允许() {
        assertTrue(SecurityPermitAllPaths.isPermitted("/health"));
    }

    @Test
    void swagger路径允许() {
        assertTrue(SecurityPermitAllPaths.isPermitted("/swagger-ui"));
        assertTrue(SecurityPermitAllPaths.isPermitted("/v3/api-docs"));
    }

    @Test
    void 前缀匹配允许() {
        assertTrue(SecurityPermitAllPaths.isPermitted("/user/login/extra"));
        assertTrue(SecurityPermitAllPaths.isPermitted("/actuator/health"));
    }

    @Test
    void null返回false() {
        assertFalse(SecurityPermitAllPaths.isPermitted(null));
    }

    @Test
    void 非白名单路径拒绝() {
        assertFalse(SecurityPermitAllPaths.isPermitted("/user/profile"));
        assertFalse(SecurityPermitAllPaths.isPermitted("/api/users"));
        assertFalse(SecurityPermitAllPaths.isPermitted("/admin"));
    }

    @Test
    void 部分前缀不匹配() {
        assertFalse(SecurityPermitAllPaths.isPermitted("/user"));
        assertFalse(SecurityPermitAllPaths.isPermitted("/healthcheck"));
    }
}
