package com.yumu.noveltranslator.controller;

import com.stripe.model.Event;
import com.yumu.noveltranslator.config.tenant.TenantContext;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.mapper.UserMapper;
import com.yumu.noveltranslator.properties.StripeProperties;
import com.yumu.noveltranslator.service.SubscriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * StripeWebhookController 补充测试
 * 覆盖现有测试未覆盖的分支：extractUserId 多种对象类型、
 * extractUserId 异常路径、deserializeUnsafe fallback、
 * processEvent 返回结果验证
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StripeWebhookController 补充测试")
class StripeWebhookControllerExtendedTest {

    private StripeWebhookController controller;
    private SubscriptionService subscriptionService;
    private StripeProperties stripeProperties;
    private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        subscriptionService = mock(SubscriptionService.class);
        stripeProperties = mock(StripeProperties.class);
        userMapper = mock(UserMapper.class);
        controller = new StripeWebhookController(subscriptionService, stripeProperties, userMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ============ extractUserId 补充分支 ============

    @Nested
    @DisplayName("extractUserId - 多种对象类型")
    class ExtractUserIdTypeTests {

        @Test
        void 从Subscription的metadata提取userId() {
            com.stripe.model.Subscription sub = mock(com.stripe.model.Subscription.class);
            when(sub.getMetadata()).thenReturn(Map.of("userId", "42"));

            Event event = mock(Event.class);
            var deserializer = mock(com.stripe.model.EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(sub));
            when(event.getType()).thenReturn("customer.subscription.updated");

            String result = controller.processEvent(event);

            verify(userMapper).selectById(42L);
        }

        @Test
        void 从Invoice的metadata提取userId() {
            com.stripe.model.Invoice invoice = mock(com.stripe.model.Invoice.class);
            when(invoice.getMetadata()).thenReturn(Map.of("userId", "99"));

            Event event = mock(Event.class);
            var deserializer = mock(com.stripe.model.EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(invoice));
            when(event.getType()).thenReturn("invoice.payment_failed");

            controller.processEvent(event);

            verify(userMapper).selectById(99L);
        }

        @Test
        void 对象无metadata时不查询用户() {
            com.stripe.model.Subscription sub = mock(com.stripe.model.Subscription.class);
            when(sub.getMetadata()).thenReturn(null);

            Event event = mock(Event.class);
            var deserializer = mock(com.stripe.model.EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(sub));
            when(event.getType()).thenReturn("customer.subscription.updated");

            controller.processEvent(event);

            verify(userMapper, never()).selectById(any());
        }

        @Test
        void 解析异常时不抛出继续处理() {
            Event event = mock(Event.class);
            var deserializer = mock(com.stripe.model.EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenThrow(new RuntimeException("parse error"));
            when(event.getType()).thenReturn("checkout.session.completed");

            // 不应抛出异常
            String result = controller.processEvent(event);
            assertEquals("{}", result);
        }
    }

    // ============ deserializeUnsafe fallback ============

    @Nested
    @DisplayName("extractUserId - deserializeUnsafe fallback")
    class DeserializeUnsafeFallbackTests {

        @Test
        void getObject为空时尝试deserializeUnsafe() {
            com.stripe.model.checkout.Session session = mock(com.stripe.model.checkout.Session.class);
            when(session.getMetadata()).thenReturn(Map.of("userId", "77"));

            Event event = mock(Event.class);
            var deserializer = mock(com.stripe.model.EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.empty());
            try {
                when(deserializer.deserializeUnsafe()).thenReturn(session);
            } catch (Exception e) {
                // deserializeUnsafe may throw, that's fine
            }
            when(event.getType()).thenReturn("checkout.session.completed");

            // 即使 deserializeUnsafe 可能失败，也不应抛出异常
            assertDoesNotThrow(() -> controller.processEvent(event));
        }
    }

    // ============ processEvent 返回值 ============

    @Nested
    @DisplayName("processEvent - 返回值验证")
    class ProcessEventReturnTests {

        @Test
        void 成功处理返回空JSON() {
            Event event = mock(Event.class);
            when(event.getType()).thenReturn("invoice.payment_succeeded");
            when(event.getDataObjectDeserializer()).thenReturn(mock(com.stripe.model.EventDataObjectDeserializer.class));

            String result = controller.processEvent(event);

            assertEquals("{}", result);
        }

        @Test
        void 未处理事件也返回空JSON() {
            Event event = mock(Event.class);
            when(event.getType()).thenReturn("unknown.event.type");
            when(event.getDataObjectDeserializer()).thenReturn(mock(com.stripe.model.EventDataObjectDeserializer.class));

            String result = controller.processEvent(event);

            assertEquals("{}", result);
        }
    }

    // ============ 租户上下文边界 ============

    @Nested
    @DisplayName("租户上下文 - 边界情况")
    class TenantContextEdgeTests {

        @Test
        void 用户无tenantId时不设置上下文() {
            Event event = mock(Event.class);
            when(event.getType()).thenReturn("checkout.session.completed");

            var mockDataDeserializer = mock(com.stripe.model.EventDataObjectDeserializer.class);
            var mockSession = mock(com.stripe.model.checkout.Session.class);
            when(mockSession.getMetadata()).thenReturn(Map.of("userId", "42"));
            when(mockDataDeserializer.getObject()).thenReturn(java.util.Optional.of(mockSession));
            when(event.getDataObjectDeserializer()).thenReturn(mockDataDeserializer);

            User userWithoutTenant = new User();
            userWithoutTenant.setId(42L);
            userWithoutTenant.setTenantId(null);
            when(userMapper.selectById(42L)).thenReturn(userWithoutTenant);

            controller.processEvent(event);

            verify(userMapper).selectById(42L);
            // TenantContext should remain unset or cleared
            assert TenantContext.getTenantId() == null;
        }

        @Test
        void 用户查询返回null时不设置上下文() {
            Event event = mock(Event.class);
            when(event.getType()).thenReturn("checkout.session.completed");

            var mockDataDeserializer = mock(com.stripe.model.EventDataObjectDeserializer.class);
            var mockSession = mock(com.stripe.model.checkout.Session.class);
            when(mockSession.getMetadata()).thenReturn(Map.of("userId", "42"));
            when(mockDataDeserializer.getObject()).thenReturn(java.util.Optional.of(mockSession));
            when(event.getDataObjectDeserializer()).thenReturn(mockDataDeserializer);

            when(userMapper.selectById(42L)).thenReturn(null);

            controller.processEvent(event);

            verify(userMapper).selectById(42L);
            assert TenantContext.getTenantId() == null;
        }
    }
}
