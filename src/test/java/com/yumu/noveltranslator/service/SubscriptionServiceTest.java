package com.yumu.noveltranslator.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItemCollection;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.StripeCustomer;
import com.yumu.noveltranslator.entity.StripeSubscription;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.entity.UserPlanHistory;
import com.yumu.noveltranslator.mapper.StripeCustomerMapper;
import com.yumu.noveltranslator.mapper.StripeSubscriptionMapper;
import com.yumu.noveltranslator.mapper.UserMapper;
import com.yumu.noveltranslator.mapper.UserPlanHistoryMapper;
import com.yumu.noveltranslator.properties.StripeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private StripeProperties stripeProperties;

    @Mock
    private StripeCustomerMapper stripeCustomerMapper;

    @Mock
    private StripeSubscriptionMapper stripeSubscriptionMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserPlanHistoryMapper userPlanHistoryMapper;

    private SubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
        subscriptionService = new SubscriptionService(
                stripeProperties, stripeCustomerMapper, stripeSubscriptionMapper,
                userMapper, userPlanHistoryMapper);
    }

    // ==================== getSubscriptionStatus ====================

    @Nested
    @DisplayName("getSubscriptionStatus")
    class GetSubscriptionStatusTests {

        @Test
        void 无订阅返回FREE状态() {
            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            SubscriptionStatusResponse response = subscriptionService.getSubscriptionStatus(1L);

            assertNotNull(response);
            assertEquals("FREE", response.getPlan());
            assertEquals("none", response.getStatus());
            assertNull(response.getPeriodEnd());
            assertFalse(response.getCancelAtPeriodEnd());
        }

        @Test
        void 有活跃订阅返回正确状态() {
            StripeSubscription sub = new StripeSubscription();
            sub.setId(1L);
            sub.setUserId(1L);
            sub.setPlan("PRO");
            sub.setStatus("active");
            sub.setCurrentPeriodEnd(LocalDateTime.of(2026, 5, 1, 0, 0));
            sub.setCancelAtPeriodEnd(false);

            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sub);

            SubscriptionStatusResponse response = subscriptionService.getSubscriptionStatus(1L);

            assertNotNull(response);
            assertEquals("PRO", response.getPlan());
            assertEquals("active", response.getStatus());
            assertEquals(LocalDateTime.of(2026, 5, 1, 0, 0), response.getPeriodEnd());
            assertFalse(response.getCancelAtPeriodEnd());
        }

        @Test
        void 已标记取消返回true() {
            StripeSubscription sub = new StripeSubscription();
            sub.setId(1L);
            sub.setUserId(1L);
            sub.setPlan("MAX");
            sub.setStatus("active");
            sub.setCurrentPeriodEnd(LocalDateTime.of(2026, 6, 1, 0, 0));
            sub.setCancelAtPeriodEnd(true);

            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sub);

            SubscriptionStatusResponse response = subscriptionService.getSubscriptionStatus(1L);

            assertTrue(response.getCancelAtPeriodEnd());
            assertEquals("MAX", response.getPlan());
        }

        @Test
        void cancelAtPeriodEnd为null返回false() {
            StripeSubscription sub = new StripeSubscription();
            sub.setId(1L);
            sub.setUserId(1L);
            sub.setPlan("PRO");
            sub.setStatus("active");
            sub.setCurrentPeriodEnd(LocalDateTime.of(2026, 5, 1, 0, 0));
            sub.setCancelAtPeriodEnd(null);

            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sub);

            SubscriptionStatusResponse response = subscriptionService.getSubscriptionStatus(1L);

            assertFalse(response.getCancelAtPeriodEnd());
        }
    }

    // ==================== createCheckoutSession 错误路径 ====================

    @Nested
    @DisplayName("createCheckoutSession 参数校验")
    class CreateCheckoutSessionValidationTests {

        @Test
        void 无效套餐类型抛出异常() {
            CheckoutSessionRequest request = new CheckoutSessionRequest();
            request.setPlan("INVALID");
            request.setBillingCycle("monthly");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> subscriptionService.createCheckoutSession(1L, request));

            assertTrue(ex.getMessage().contains("无效的套餐类型"));
        }

        @Test
        void 无效计费周期抛出异常() {
            CheckoutSessionRequest request = new CheckoutSessionRequest();
            request.setPlan("PRO");
            request.setBillingCycle("INVALID");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> subscriptionService.createCheckoutSession(1L, request));

            assertTrue(ex.getMessage().contains("无效的计费周期"));
        }
    }

    // ==================== createCheckoutSession 成功路径 ====================

    @Nested
    @DisplayName("createCheckoutSession 成功路径")
    class CreateCheckoutSessionSuccessTests {

        @Test
        void 创建新客户并生成CheckoutSession() {
            when(stripeCustomerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            User user = new User();
            user.setId(1L);
            user.setEmail("test@example.com");
            when(userMapper.selectById(1L)).thenReturn(user);

            StripeProperties.PlanPrices planPrices = new StripeProperties.PlanPrices();
            planPrices.setMonthlyPriceId("price_pro_monthly");
            when(stripeProperties.getPrices()).thenReturn(Map.of("pro", planPrices));
            when(stripeProperties.getSuccessUrl()).thenReturn("https://example.com/success");
            when(stripeProperties.getCancelUrl()).thenReturn("https://example.com/cancel");

            try (MockedStatic<Stripe> stripeStatic = mockStatic(Stripe.class);
                 MockedStatic<Session> sessionStatic = mockStatic(Session.class);
                 MockedStatic<com.stripe.model.Customer> customerStatic = mockStatic(com.stripe.model.Customer.class)) {

                com.stripe.model.Customer mockCustomer = mock(com.stripe.model.Customer.class);
                when(mockCustomer.getId()).thenReturn("cus_test123");
                customerStatic.when(() -> com.stripe.model.Customer.create(any(com.stripe.param.CustomerCreateParams.class))).thenReturn(mockCustomer);

                Session mockSession = mock(Session.class);
                when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/test");
                sessionStatic.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

                CheckoutSessionRequest request = new CheckoutSessionRequest();
                request.setPlan("PRO");
                request.setBillingCycle("monthly");

                CheckoutSessionResponse response = subscriptionService.createCheckoutSession(1L, request);

                assertNotNull(response);
                assertEquals("https://checkout.stripe.com/test", response.getCheckoutUrl());
                verify(stripeCustomerMapper).insert(any(StripeCustomer.class));
            }
        }

        @Test
        void 使用已有客户生成CheckoutSession() {
            StripeCustomer existing = new StripeCustomer();
            existing.setId(1L);
            existing.setUserId(1L);
            existing.setStripeCustomerId("cus_existing");
            when(stripeCustomerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(existing);

            StripeProperties.PlanPrices planPrices = new StripeProperties.PlanPrices();
            planPrices.setYearlyPriceId("price_pro_yearly");
            when(stripeProperties.getPrices()).thenReturn(Map.of("pro", planPrices));
            when(stripeProperties.getSuccessUrl()).thenReturn("https://example.com/success");
            when(stripeProperties.getCancelUrl()).thenReturn("https://example.com/cancel");

            try (MockedStatic<Session> sessionStatic = mockStatic(Session.class)) {
                Session mockSession = mock(Session.class);
                when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/test2");
                sessionStatic.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

                CheckoutSessionRequest request = new CheckoutSessionRequest();
                request.setPlan("PRO");
                request.setBillingCycle("yearly");

                CheckoutSessionResponse response = subscriptionService.createCheckoutSession(1L, request);

                assertNotNull(response);
                assertEquals("https://checkout.stripe.com/test2", response.getCheckoutUrl());
                verify(stripeCustomerMapper, never()).insert(any());
            }
        }

        @Test
        void 用户不存在时抛出异常() {
            CheckoutSessionRequest request = new CheckoutSessionRequest();
            request.setPlan("PRO");
            request.setBillingCycle("monthly");

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> subscriptionService.createCheckoutSession(1L, request));

            assertTrue(ex.getMessage().contains("Stripe prices") || ex.getMessage().contains("No price configured"));
        }
    }

    // ==================== cancelSubscription ====================

    @Nested
    @DisplayName("cancelSubscription")
    class CancelSubscriptionTests {

        @Test
        void 没有活跃订阅抛出异常() {
            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> subscriptionService.cancelSubscription(1L));

            assertTrue(ex.getMessage().contains("没有可取消的活跃订阅"));
        }
    }

    // ==================== createPortalSession ====================

    @Nested
    @DisplayName("createPortalSession")
    class CreatePortalSessionTests {

        @Test
        void 客户不存在抛出异常() {
            when(stripeCustomerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> subscriptionService.createPortalSession(1L));

            assertTrue(ex.getMessage().contains("未找到 Stripe 客户"));
        }

        @Test
        void 成功创建PortalSession() {
            StripeCustomer customer = new StripeCustomer();
            customer.setId(1L);
            customer.setUserId(1L);
            customer.setStripeCustomerId("cus_test123");
            when(stripeCustomerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(customer);
            when(stripeProperties.getCancelUrl()).thenReturn("https://example.com/return");

            try (MockedStatic<com.stripe.model.billingportal.Session> portalStatic =
                         mockStatic(com.stripe.model.billingportal.Session.class)) {
                com.stripe.model.billingportal.Session mockPortal =
                        mock(com.stripe.model.billingportal.Session.class);
                when(mockPortal.getUrl()).thenReturn("https://billing.stripe.com/test");
                portalStatic.when(() -> com.stripe.model.billingportal.Session
                        .create(any(com.stripe.param.billingportal.SessionCreateParams.class))).thenReturn(mockPortal);

                PortalSessionResponse response = subscriptionService.createPortalSession(1L);

                assertNotNull(response);
                assertEquals("https://billing.stripe.com/test", response.getPortalUrl());
            }
        }
    }

    // ==================== Webhook: handleCheckoutSessionCompleted ====================

    @Nested
    @DisplayName("Webhook: handleCheckoutSessionCompleted")
    class HandleCheckoutSessionCompletedTests {

        @Test
        void 反序列化失败不抛异常() {
            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> subscriptionService.handleCheckoutSessionCompleted(event));
        }

        @Test
        void 缺少metadata时正常返回() {
            Session session = mock(Session.class);
            when(session.getId()).thenReturn("cs_test123");
            when(session.getMetadata()).thenReturn(Map.of());

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(session));

            assertDoesNotThrow(() -> subscriptionService.handleCheckoutSessionCompleted(event));
        }

        @Test
        void session中没有subscription时正常返回() {
            Session session = mock(Session.class);
            when(session.getId()).thenReturn("cs_test123");
            when(session.getMetadata()).thenReturn(Map.of(
                    "userId", "1",
                    "plan", "PRO",
                    "billingCycle", "monthly"
            ));
            when(session.getSubscription()).thenReturn(null);

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(session));

            assertDoesNotThrow(() -> subscriptionService.handleCheckoutSessionCompleted(event));
        }
    }

    // ==================== Webhook: handleSubscriptionUpdated ====================

    @Nested
    @DisplayName("Webhook: handleSubscriptionUpdated")
    class HandleSubscriptionUpdatedTests {

        @Test
        void 反序列化失败不抛异常() {
            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> subscriptionService.handleSubscriptionUpdated(event));
        }

        @Test
        void 本地无记录时正常返回() {
            Subscription stripeSub = mock(Subscription.class);
            when(stripeSub.getId()).thenReturn("sub_nonexistent");

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(stripeSub));

            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertDoesNotThrow(() -> subscriptionService.handleSubscriptionUpdated(event));
        }

        @Test
        void 幂等检查跳过重复事件() {
            StripeSubscription subRecord = new StripeSubscription();
            subRecord.setId(1L);
            subRecord.setUserId(1L);
            subRecord.setStripeSubscriptionId("sub_test123");
            subRecord.setStatus("active");
            subRecord.setPlan("PRO");
            subRecord.setLastWebhookEventId("evt_test123");

            Subscription stripeSub = mock(Subscription.class);
            when(stripeSub.getId()).thenReturn("sub_test123");

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(stripeSub));
            when(event.getId()).thenReturn("evt_test123");

            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(subRecord);

            assertDoesNotThrow(() -> subscriptionService.handleSubscriptionUpdated(event));

            verify(stripeSubscriptionMapper, never()).updateById(any());
        }

        @Test
        void 状态变为canceled时降级为FREE() {
            StripeSubscription subRecord = new StripeSubscription();
            subRecord.setId(1L);
            subRecord.setUserId(1L);
            subRecord.setStripeSubscriptionId("sub_test123");
            subRecord.setStatus("active");
            subRecord.setPlan("PRO");
            subRecord.setLastWebhookEventId("evt_old");

            User user = new User();
            user.setId(1L);
            user.setUserLevel("PRO");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(userMapper.updateById(any(User.class))).thenReturn(1);
            when(userPlanHistoryMapper.insert(any(UserPlanHistory.class))).thenReturn(1);

            Subscription stripeSub = mock(Subscription.class);
            when(stripeSub.getId()).thenReturn("sub_test123");
            when(stripeSub.getStatus()).thenReturn("canceled");
            when(stripeSub.getCancelAtPeriodEnd()).thenReturn(false);
            when(stripeSub.getCurrentPeriodStart()).thenReturn(null);
            when(stripeSub.getCurrentPeriodEnd()).thenReturn(null);
            when(stripeSub.getCanceledAt()).thenReturn(null);

            com.stripe.model.SubscriptionItem subItem = mock(com.stripe.model.SubscriptionItem.class);
            com.stripe.model.Price price = mock(com.stripe.model.Price.class);
            when(price.getId()).thenReturn("price_test");
            when(subItem.getPrice()).thenReturn(price);
            SubscriptionItemCollection items = mock(SubscriptionItemCollection.class);
            when(items.getData()).thenReturn(List.of(subItem));
            when(stripeSub.getItems()).thenReturn(items);

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(stripeSub));
            when(event.getId()).thenReturn("evt_new123");

            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(subRecord);

            subscriptionService.handleSubscriptionUpdated(event);

            verify(userMapper).updateById(argThat(u -> "FREE".equals(u.getUserLevel())));
            verify(userPlanHistoryMapper).insert(argThat(h ->
                    "PRO".equals(h.getOldPlan()) && "FREE".equals(h.getNewPlan())));
        }
    }

    // ==================== Webhook: handleSubscriptionDeleted ====================

    @Nested
    @DisplayName("Webhook: handleSubscriptionDeleted")
    class HandleSubscriptionDeletedTests {

        @Test
        void 反序列化失败不抛异常() {
            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> subscriptionService.handleSubscriptionDeleted(event));
        }

        @Test
        void 本地无记录时正常返回() {
            Subscription stripeSub = mock(Subscription.class);
            when(stripeSub.getId()).thenReturn("sub_nonexistent");

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(stripeSub));

            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertDoesNotThrow(() -> subscriptionService.handleSubscriptionDeleted(event));
        }

        @Test
        void 幂等检查跳过重复事件() {
            StripeSubscription subRecord = new StripeSubscription();
            subRecord.setId(1L);
            subRecord.setUserId(1L);
            subRecord.setStripeSubscriptionId("sub_test123");
            subRecord.setStatus("active");
            subRecord.setPlan("PRO");
            subRecord.setLastWebhookEventId("evt_test123");

            Subscription stripeSub = mock(Subscription.class);
            when(stripeSub.getId()).thenReturn("sub_test123");

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(stripeSub));
            when(event.getId()).thenReturn("evt_test123");

            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(subRecord);

            assertDoesNotThrow(() -> subscriptionService.handleSubscriptionDeleted(event));

            verify(stripeSubscriptionMapper, never()).updateById(any());
        }

        @Test
        void 删除订阅并降级用户为FREE() {
            StripeSubscription subRecord = new StripeSubscription();
            subRecord.setId(1L);
            subRecord.setUserId(1L);
            subRecord.setStripeSubscriptionId("sub_test123");
            subRecord.setStatus("active");
            subRecord.setPlan("PRO");
            subRecord.setLastWebhookEventId("evt_old");

            User user = new User();
            user.setId(1L);
            user.setUserLevel("PRO");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(userMapper.updateById(any(User.class))).thenReturn(1);
            when(userPlanHistoryMapper.insert(any(UserPlanHistory.class))).thenReturn(1);

            Subscription stripeSub = mock(Subscription.class);
            when(stripeSub.getId()).thenReturn("sub_test123");

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(stripeSub));
            when(event.getId()).thenReturn("evt_deleted123");

            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(subRecord);

            subscriptionService.handleSubscriptionDeleted(event);

            verify(stripeSubscriptionMapper).updateById(argThat(s ->
                    "canceled".equals(s.getStatus()) && "evt_deleted123".equals(s.getLastWebhookEventId())));
            verify(userMapper).updateById(argThat(u -> "FREE".equals(u.getUserLevel())));
            verify(userPlanHistoryMapper).insert(argThat(h ->
                    "subscription.deleted".equals(h.getNote())));
        }
    }

    // ==================== Webhook: handleSubscriptionResumed ====================

    @Nested
    @DisplayName("Webhook: handleSubscriptionResumed")
    class HandleSubscriptionResumedTests {

        @Test
        void 反序列化失败不抛异常() {
            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> subscriptionService.handleSubscriptionResumed(event));
        }

        @Test
        void 本地无记录时正常返回() {
            Subscription stripeSub = mock(Subscription.class);
            when(stripeSub.getId()).thenReturn("sub_nonexistent");

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(stripeSub));

            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertDoesNotThrow(() -> subscriptionService.handleSubscriptionResumed(event));
        }

        @Test
        void 更新订阅状态() {
            StripeSubscription subRecord = new StripeSubscription();
            subRecord.setId(1L);
            subRecord.setUserId(1L);
            subRecord.setStripeSubscriptionId("sub_test123");
            subRecord.setStatus("paused");

            Subscription stripeSub = mock(Subscription.class);
            when(stripeSub.getId()).thenReturn("sub_test123");
            when(stripeSub.getStatus()).thenReturn("active");

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(stripeSub));
            when(event.getId()).thenReturn("evt_resumed123");

            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(subRecord);

            subscriptionService.handleSubscriptionResumed(event);

            verify(stripeSubscriptionMapper).updateById(argThat(s ->
                    "active".equals(s.getStatus()) && "evt_resumed123".equals(s.getLastWebhookEventId())));
        }
    }

    // ==================== Webhook: handleInvoicePaymentFailed ====================

    @Nested
    @DisplayName("Webhook: handleInvoicePaymentFailed")
    class HandleInvoicePaymentFailedTests {

        @Test
        void 反序列化失败不抛异常() {
            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> subscriptionService.handleInvoicePaymentFailed(event));
        }

        @Test
        void 发票无订阅信息时正常返回() {
            com.stripe.model.Invoice invoice = mock(com.stripe.model.Invoice.class);
            when(invoice.getSubscription()).thenReturn(null);

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(invoice));

            assertDoesNotThrow(() -> subscriptionService.handleInvoicePaymentFailed(event));
        }

        @Test
        void 有本地记录时标记为past_due() {
            StripeSubscription subRecord = new StripeSubscription();
            subRecord.setId(1L);
            subRecord.setUserId(1L);
            subRecord.setStripeSubscriptionId("sub_test123");
            subRecord.setStatus("active");

            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(subRecord);

            com.stripe.model.Invoice invoice = mock(com.stripe.model.Invoice.class);
            when(invoice.getSubscription()).thenReturn("sub_test123");

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(invoice));
            when(event.getId()).thenReturn("evt_payment_failed123");

            subscriptionService.handleInvoicePaymentFailed(event);

            verify(stripeSubscriptionMapper).updateById(argThat(s ->
                    "past_due".equals(s.getStatus()) && "evt_payment_failed123".equals(s.getLastWebhookEventId())));
        }

        @Test
        void 无本地记录时仅记录警告() {
            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            com.stripe.model.Invoice invoice = mock(com.stripe.model.Invoice.class);
            when(invoice.getSubscription()).thenReturn("sub_unknown");

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(invoice));

            assertDoesNotThrow(() -> subscriptionService.handleInvoicePaymentFailed(event));

            verify(stripeSubscriptionMapper, never()).updateById(any());
        }
    }

    // ==================== getOrCreateCustomer 间接测试 ====================

    @Nested
    @DisplayName("getOrCreateCustomer 间接测试")
    class GetOrCreateCustomerIndirectTests {

        @Test
        void 返回已有客户() {
            StripeCustomer existing = new StripeCustomer();
            existing.setId(1L);
            existing.setUserId(1L);
            existing.setStripeCustomerId("cus_existing");
            when(stripeCustomerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

            StripeProperties.PlanPrices planPrices = new StripeProperties.PlanPrices();
            planPrices.setMonthlyPriceId("price_pro_monthly");
            when(stripeProperties.getPrices()).thenReturn(Map.of("pro", planPrices));
            when(stripeProperties.getSuccessUrl()).thenReturn("https://example.com/success");
            when(stripeProperties.getCancelUrl()).thenReturn("https://example.com/cancel");

            try (MockedStatic<Session> sessionStatic = mockStatic(Session.class)) {
                Session mockSession = mock(Session.class);
                when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/test");
                sessionStatic.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

                CheckoutSessionRequest request = new CheckoutSessionRequest();
                request.setPlan("PRO");
                request.setBillingCycle("monthly");

                subscriptionService.createCheckoutSession(1L, request);

                verify(stripeCustomerMapper, never()).insert(any());
                verify(userMapper, never()).selectById(anyLong());
            }
        }
    }

    // ==================== updateUserLevel 间接测试 ====================

    @Nested
    @DisplayName("updateUserLevel 间接测试")
    class UpdateUserLevelIndirectTests {

        @Test
        void 用户不存在时不抛异常() {
            StripeSubscription subRecord = new StripeSubscription();
            subRecord.setId(1L);
            subRecord.setUserId(999L);
            subRecord.setStripeSubscriptionId("sub_test123");
            subRecord.setStatus("active");
            subRecord.setPlan("PRO");
            subRecord.setLastWebhookEventId("evt_old");

            Subscription stripeSub = mock(Subscription.class);
            when(stripeSub.getId()).thenReturn("sub_test123");
            when(stripeSub.getStatus()).thenReturn("canceled");
            when(stripeSub.getCancelAtPeriodEnd()).thenReturn(false);
            when(stripeSub.getCurrentPeriodStart()).thenReturn(null);
            when(stripeSub.getCurrentPeriodEnd()).thenReturn(null);
            when(stripeSub.getCanceledAt()).thenReturn(null);

            com.stripe.model.SubscriptionItem subItem = mock(com.stripe.model.SubscriptionItem.class);
            com.stripe.model.Price price = mock(com.stripe.model.Price.class);
            when(price.getId()).thenReturn("price_test");
            when(subItem.getPrice()).thenReturn(price);
            SubscriptionItemCollection items = mock(SubscriptionItemCollection.class);
            when(items.getData()).thenReturn(List.of(subItem));
            when(stripeSub.getItems()).thenReturn(items);

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(stripeSub));
            when(event.getId()).thenReturn("evt_new456");

            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(subRecord);
            when(userMapper.selectById(999L)).thenReturn(null);

            assertDoesNotThrow(() -> subscriptionService.handleSubscriptionUpdated(event));
        }

        @Test
        void 用户等级相同时不更新() {
            StripeSubscription subRecord = new StripeSubscription();
            subRecord.setId(1L);
            subRecord.setUserId(1L);
            subRecord.setStripeSubscriptionId("sub_test123");
            subRecord.setStatus("active");
            subRecord.setPlan("PRO");
            subRecord.setLastWebhookEventId("evt_old");

            User user = new User();
            user.setId(1L);
            user.setUserLevel("PRO");
            when(userMapper.selectById(1L)).thenReturn(user);

            Subscription stripeSub = mock(Subscription.class);
            when(stripeSub.getId()).thenReturn("sub_test123");
            when(stripeSub.getStatus()).thenReturn("active");
            when(stripeSub.getCancelAtPeriodEnd()).thenReturn(false);
            when(stripeSub.getCurrentPeriodStart()).thenReturn(null);
            when(stripeSub.getCurrentPeriodEnd()).thenReturn(null);
            when(stripeSub.getCanceledAt()).thenReturn(null);

            com.stripe.model.SubscriptionItem subItem = mock(com.stripe.model.SubscriptionItem.class);
            com.stripe.model.Price price = mock(com.stripe.model.Price.class);
            when(price.getId()).thenReturn("price_test");
            when(subItem.getPrice()).thenReturn(price);
            SubscriptionItemCollection items = mock(SubscriptionItemCollection.class);
            when(items.getData()).thenReturn(List.of(subItem));
            when(stripeSub.getItems()).thenReturn(items);

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(stripeSub));
            when(event.getId()).thenReturn("evt_new789");

            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(subRecord);

            subscriptionService.handleSubscriptionUpdated(event);

            verify(userMapper, never()).updateById(any());
            verify(userPlanHistoryMapper, never()).insert(any());
        }
    }
}
