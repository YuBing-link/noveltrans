package com.yumu.noveltranslator.controller;

import com.stripe.model.Event;
import com.yumu.noveltranslator.config.tenant.TenantContext;
import com.yumu.noveltranslator.dto.Result;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Stripe Webhook 控制器测试
 *
 * 由于 Webhook.constructEvent 是 Stripe SDK 的静态方法，难以在单元测试中 mock，
 * 因此本测试采用以下策略：
 * 1. 通过 MockMvc 测试签名验证失败路径（返回 500）
 * 2. 通过直接调用 Controller 的 processEvent/dispatchEvent 方法测试事件分发逻辑
 */
@ExtendWith(MockitoExtension.class)
class StripeWebhookControllerTest {

    private MockMvc mockMvc;

    @org.mockito.Mock
    private SubscriptionService subscriptionService;

    @org.mockito.Mock
    private StripeProperties stripeProperties;

    @org.mockito.Mock
    private UserMapper userMapper;

    private StripeWebhookController controller;

    /**
     * 测试用异常处理器，将未捕获异常转为 500 响应
     */
    @RestControllerAdvice
    static class TestExceptionHandler {
        @ExceptionHandler(Exception.class)
        @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        public Result<Void> handleException(Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @BeforeEach
    void setUp() {
        controller = new StripeWebhookController(subscriptionService, stripeProperties, userMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new TestExceptionHandler())
            .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("Webhook 端点")
    class WebhookEndpointTests {

        @Test
        void 无效签名返回500() throws Exception {
            String payload = "{\"id\":\"evt_test\",\"type\":\"checkout.session.completed\",\"data\":{}}";

            mockMvc.perform(post("/webhook/stripe")
                    .header("Stripe-Signature", "t=123456,v1=invalid_sig")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                .andExpect(status().is5xxServerError());
        }

        @Test
        void 缺少签名头返回500() throws Exception {
            String payload = "{\"id\":\"evt_test\",\"type\":\"checkout.session.completed\",\"data\":{}}";

            mockMvc.perform(post("/webhook/stripe")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                .andExpect(status().is5xxServerError());
        }

        @Test
        void 空请求体返回500() throws Exception {
            // 空请求体导致 Stripe SDK 解析失败，返回 500
            mockMvc.perform(post("/webhook/stripe")
                    .header("Stripe-Signature", "t=123456,v1=some_sig"))
                .andExpect(status().is5xxServerError());
        }
    }

    @Nested
    @DisplayName("事件分发逻辑")
    class EventDispatchTests {

        @Test
        void checkoutSessionCompleted事件调用对应处理方法() {
            Event mockEvent = mock(Event.class);
            when(mockEvent.getType()).thenReturn("checkout.session.completed");

            controller.dispatchEvent(mockEvent);

            verify(subscriptionService).handleCheckoutSessionCompleted(mockEvent);
            verify(subscriptionService, never()).handleSubscriptionUpdated(any());
            verify(subscriptionService, never()).handleSubscriptionDeleted(any());
        }

        @Test
        void subscriptionUpdated事件调用对应处理方法() {
            Event mockEvent = mock(Event.class);
            when(mockEvent.getType()).thenReturn("customer.subscription.updated");

            controller.dispatchEvent(mockEvent);

            verify(subscriptionService).handleSubscriptionUpdated(mockEvent);
            verify(subscriptionService, never()).handleCheckoutSessionCompleted(any());
        }

        @Test
        void subscriptionDeleted事件调用对应处理方法() {
            Event mockEvent = mock(Event.class);
            when(mockEvent.getType()).thenReturn("customer.subscription.deleted");

            controller.dispatchEvent(mockEvent);

            verify(subscriptionService).handleSubscriptionDeleted(mockEvent);
        }

        @Test
        void subscriptionResumed事件调用对应处理方法() {
            Event mockEvent = mock(Event.class);
            when(mockEvent.getType()).thenReturn("customer.subscription.resumed");

            controller.dispatchEvent(mockEvent);

            verify(subscriptionService).handleSubscriptionResumed(mockEvent);
        }

        @Test
        void invoicePaymentFailed事件调用对应处理方法() {
            Event mockEvent = mock(Event.class);
            when(mockEvent.getType()).thenReturn("invoice.payment_failed");

            controller.dispatchEvent(mockEvent);

            verify(subscriptionService).handleInvoicePaymentFailed(mockEvent);
        }

        @Test
        void subscriptionCreated事件被忽略() {
            Event mockEvent = mock(Event.class);
            when(mockEvent.getType()).thenReturn("customer.subscription.created");

            controller.dispatchEvent(mockEvent);

            verifyNoInteractions(subscriptionService);
        }

        @Test
        void invoicePaymentSucceeded事件仅记录不处理() {
            Event mockEvent = mock(Event.class);
            when(mockEvent.getType()).thenReturn("invoice.payment_succeeded");

            controller.dispatchEvent(mockEvent);

            verifyNoInteractions(subscriptionService);
        }

        @Test
        void 未处理的事件类型不调用任何服务方法() {
            Event mockEvent = mock(Event.class);
            when(mockEvent.getType()).thenReturn("charge.succeeded");

            controller.dispatchEvent(mockEvent);

            verifyNoInteractions(subscriptionService);
        }
    }

    @Nested
    @DisplayName("租户上下文设置")
    class TenantContextTests {

        @Test
        void 事件包含userId时查询用户并设置租户上下文() {
            Event mockEvent = mock(Event.class);
            when(mockEvent.getType()).thenReturn("checkout.session.completed");

            // 模拟 Event 包含 userId metadata
            var mockDataDeserializer = mock(com.stripe.model.EventDataObjectDeserializer.class);
            var mockSession = mock(com.stripe.model.checkout.Session.class);
            Map<String, String> metadata = new HashMap<>();
            metadata.put("userId", "42");
            when(mockSession.getMetadata()).thenReturn(metadata);
            when(mockDataDeserializer.getObject()).thenReturn(java.util.Optional.of(mockSession));
            when(mockEvent.getDataObjectDeserializer()).thenReturn(mockDataDeserializer);

            User mockUser = new User();
            mockUser.setId(42L);
            mockUser.setTenantId(1L);
            when(userMapper.selectById(42L)).thenReturn(mockUser);

            controller.processEvent(mockEvent);

            verify(userMapper).selectById(42L);
            verify(subscriptionService).handleCheckoutSessionCompleted(mockEvent);
        }

        @Test
        void 事件无userId时不查询用户() {
            Event mockEvent = mock(Event.class);
            when(mockEvent.getType()).thenReturn("checkout.session.completed");

            var mockDataDeserializer = mock(com.stripe.model.EventDataObjectDeserializer.class);
            var mockSession = mock(com.stripe.model.checkout.Session.class);
            when(mockSession.getMetadata()).thenReturn(Map.of());
            when(mockDataDeserializer.getObject()).thenReturn(java.util.Optional.of(mockSession));
            when(mockEvent.getDataObjectDeserializer()).thenReturn(mockDataDeserializer);

            controller.processEvent(mockEvent);

            verify(userMapper, never()).selectById(any());
        }

        @Test
        void processEvent清理租户上下文() {
            Event mockEvent = mock(Event.class);
            when(mockEvent.getType()).thenReturn("checkout.session.completed");
            when(mockEvent.getDataObjectDeserializer()).thenReturn(mock(com.stripe.model.EventDataObjectDeserializer.class));

            TenantContext.setTenantId(1L);
            controller.processEvent(mockEvent);

            assert TenantContext.getTenantId() == null;
        }
    }
}
