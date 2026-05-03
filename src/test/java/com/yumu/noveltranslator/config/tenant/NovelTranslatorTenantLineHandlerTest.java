package com.yumu.noveltranslator.config.tenant;

import net.sf.jsqlparser.expression.LongValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NovelTranslatorTenantLineHandler 测试")
class NovelTranslatorTenantLineHandlerTest {

    private NovelTranslatorTenantLineHandler handler;

    @BeforeEach
    void setUp() {
        handler = new NovelTranslatorTenantLineHandler();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("getTenantId")
    class GetTenantIdTests {

        @Test
        void 有租户ID返回对应LongValue() {
            TenantContext.setTenantId(42L);
            var result = handler.getTenantId();
            assertInstanceOf(LongValue.class, result);
            assertEquals(42L, result.getValue());
        }

        @Test
        void 无租户ID返回0L() {
            TenantContext.clear();
            var result = handler.getTenantId();
            assertInstanceOf(LongValue.class, result);
            assertEquals(0L, result.getValue());
        }
    }

    @Nested
    @DisplayName("getTenantIdColumn")
    class GetTenantIdColumnTests {

        @Test
        void 返回tenant_id列名() {
            assertEquals("tenant_id", handler.getTenantIdColumn());
        }
    }

    @Nested
    @DisplayName("ignoreTable")
    class IgnoreTableTests {

        @Test
        void 绕过租户时所有表都忽略() {
            TenantContext.setBypassTenant(true);
            assertTrue(handler.ignoreTable("any_table"));
            assertTrue(handler.ignoreTable("user"));
        }

        @Test
        void 不绕过租户时忽略白名单表() {
            TenantContext.setBypassTenant(false);
            assertTrue(handler.ignoreTable("translation_cache"));
            assertTrue(handler.ignoreTable("email_verification_code"));
            assertTrue(handler.ignoreTable("user"));
            assertTrue(handler.ignoreTable("collab_invite_code"));
            assertTrue(handler.ignoreTable("api_keys"));
            assertTrue(handler.ignoreTable("stripe_customer"));
            assertTrue(handler.ignoreTable("stripe_subscription"));
            assertTrue(handler.ignoreTable("user_plan_history"));
        }

        @Test
        void 不绕过租户时业务表不忽略() {
            TenantContext.setBypassTenant(false);
            assertFalse(handler.ignoreTable("document"));
            assertFalse(handler.ignoreTable("translation_task"));
            assertFalse(handler.ignoreTable("collab_project"));
            assertFalse(handler.ignoreTable("glossary"));
        }
    }
}
