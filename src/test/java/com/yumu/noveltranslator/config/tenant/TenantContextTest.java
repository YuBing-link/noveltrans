package com.yumu.noveltranslator.config.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TenantContext 测试")
class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("租户 ID")
    class TenantIdTests {

        @Test
        void 设置后获取返回设置值() {
            TenantContext.setTenantId(42L);
            assertEquals(42L, TenantContext.getTenantId());
        }

        @Test
        void 未设置返回null() {
            TenantContext.clear();
            assertNull(TenantContext.getTenantId());
        }

        @Test
        void 清除后返回null() {
            TenantContext.setTenantId(1L);
            TenantContext.clear();
            assertNull(TenantContext.getTenantId());
        }

        @Test
        void 可以覆盖设置() {
            TenantContext.setTenantId(1L);
            TenantContext.setTenantId(2L);
            assertEquals(2L, TenantContext.getTenantId());
        }
    }

    @Nested
    @DisplayName("绕过租户")
    class BypassTenantTests {

        @Test
        void 默认未设置绕过() {
            TenantContext.clear();
            assertFalse(TenantContext.isBypassTenant());
        }

        @Test
        void 设置为true后返回true() {
            TenantContext.setBypassTenant(true);
            assertTrue(TenantContext.isBypassTenant());
        }

        @Test
        void 设置false后返回false() {
            TenantContext.setBypassTenant(true);
            TenantContext.setBypassTenant(false);
            assertFalse(TenantContext.isBypassTenant());
        }

        @Test
        void 清除后返回false() {
            TenantContext.setBypassTenant(true);
            TenantContext.clear();
            assertFalse(TenantContext.isBypassTenant());
        }
    }

    @Nested
    @DisplayName("清除")
    class ClearTests {

        @Test
        void 清除同时重置租户ID和绕过标记() {
            TenantContext.setTenantId(100L);
            TenantContext.setBypassTenant(true);
            TenantContext.clear();

            assertNull(TenantContext.getTenantId());
            assertFalse(TenantContext.isBypassTenant());
        }
    }
}
