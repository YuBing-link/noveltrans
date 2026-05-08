package com.yumu.noveltranslator.application.service;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.yumu.noveltranslator.domain.model.StripeCustomer;
import com.yumu.noveltranslator.domain.model.StripeSubscription;
import com.yumu.noveltranslator.domain.model.User;
import com.yumu.noveltranslator.domain.model.UserPlanHistory;
import com.yumu.noveltranslator.port.out.BillingRepositoryPort;
import com.yumu.noveltranslator.port.out.TokenRevocationPort;
import com.yumu.noveltranslator.port.out.UserRepositoryPort;
import com.yumu.noveltranslator.port.out.payment.SubscriptionInfo;
import com.yumu.noveltranslator.properties.StripeProperties;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionServiceInvoiceTest {

    @Mock
    private StripeProperties stripeProperties;

    @Mock
    private BillingRepositoryPort billingPort;

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private TokenRevocationPort tokenRevocationPort;

    @Mock
    private com.yumu.noveltranslator.port.out.PaymentPort paymentPort;

    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private SubscriptionApplicationService subscriptionService;

    @BeforeAll
    static void initMybatisPlusCache() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, StripeSubscription.class);
    }

    @BeforeEach
    void setUp() {
        subscriptionService = new SubscriptionApplicationService(
                stripeProperties, billingPort, userRepositoryPort,
                stringRedisTemplate, tokenRevocationPort,
                paymentPort, transactionManager);
    }

    // ==================== Fallback activation: existing record, non-active status ====================

    @Nested
    @DisplayName("Fallback activation (existing record, non-active)")
    class FallbackActivationTests {

        @Test
        void 反序列化失败不抛异常() {
            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> subscriptionService.handleInvoicePaymentSucceeded(event));
        }

        @Test
        void 发票无subscription信息时正常返回() {
            Invoice invoice = mock(Invoice.class);
            when(invoice.getSubscription()).thenReturn(null);

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(invoice));

            assertDoesNotThrow(() -> subscriptionService.handleInvoicePaymentSucceeded(event));
        }

        @Test
        void 已有活跃订阅时跳过() {
            StripeSubscription subRecord = new StripeSubscription();
            subRecord.setId(1L);
            subRecord.setUserId(1L);
            subRecord.setStripeSubscriptionId("sub_test123");
            subRecord.setStatus("active");
            subRecord.setPlan("PRO");

            when(billingPort.findSubscriptionByStripeId("sub_test123")).thenReturn(subRecord);

            Invoice invoice = mock(Invoice.class);
            when(invoice.getSubscription()).thenReturn("sub_test123");

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(invoice));

            subscriptionService.handleInvoicePaymentSucceeded(event);

            verify(billingPort, never()).atomicUpdateSubscription(anyLong(), anyString(), anyString(), anyLong());
            verifyNoInteractions(stringRedisTemplate);
        }

        @Test
        void 已有trialing状态订阅时跳过() {
            StripeSubscription subRecord = new StripeSubscription();
            subRecord.setId(1L);
            subRecord.setUserId(1L);
            subRecord.setStripeSubscriptionId("sub_test123");
            subRecord.setStatus("trialing");
            subRecord.setPlan("PRO");

            when(billingPort.findSubscriptionByStripeId("sub_test123")).thenReturn(subRecord);

            Invoice invoice = mock(Invoice.class);
            when(invoice.getSubscription()).thenReturn("sub_test123");

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(invoice));

            subscriptionService.handleInvoicePaymentSucceeded(event);

            verify(billingPort, never()).atomicUpdateSubscription(anyLong(), anyString(), anyString(), anyLong());
        }

        // 注意：此测试被禁用，因为 MyBatis-Plus LambdaUpdateWrapper 需要 Spring 上下文初始化实体缓存
        // 在纯单元测试环境中无法使用。集成测试（SpringBootTest）会覆盖此路径。
        @Disabled("需要 Spring 上下文初始化 MyBatis-Plus 实体缓存")
        @Test
        void past_due状态订阅被激活() {
            StripeSubscription subRecord = new StripeSubscription();
            subRecord.setId(1L);
            subRecord.setUserId(1L);
            subRecord.setStripeSubscriptionId("sub_test123");
            subRecord.setStatus("past_due");
            subRecord.setPlan("PRO");
            subRecord.setLastWebhookEventId("evt_old");

            User user = new User();
            user.setId(1L);
            user.setUserLevel("FREE");
            when(userRepositoryPort.findById(1L)).thenReturn(Optional.of(user));
            doNothing().when(userRepositoryPort).update(any(User.class));
            doNothing().when(userRepositoryPort).savePlanHistory(any(UserPlanHistory.class));

            when(billingPort.findSubscriptionByStripeId("sub_test123")).thenReturn(subRecord);
            // Mock the atomic update for activation (atomicUpdate replaces updateSubscriptionByWrapper)
            lenient().when(billingPort.atomicUpdateSubscription(anyLong(), anyString(), anyString(), anyLong())).thenReturn(1);

            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class))).thenReturn(true);

            Invoice invoice = mock(Invoice.class);
            when(invoice.getSubscription()).thenReturn("sub_test123");

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(invoice));
            when(event.getId()).thenReturn("evt_invoice_success");
            when(event.getCreated()).thenReturn(1700000000L);

            subscriptionService.handleInvoicePaymentSucceeded(event);

            // Verify the atomic update was called
            verify(billingPort).atomicUpdateSubscription(anyLong(), anyString(), anyString(), anyLong());
            // Verify user level was updated
            verify(userRepositoryPort).update(argThat(u -> "PRO".equals(u.getUserLevel())));
        }

        @Disabled("需要 Spring 上下文初始化 MyBatis-Plus 实体缓存")
        @Test
        void 幂等检查防止重复处理() {
            StripeSubscription subRecord = new StripeSubscription();
            subRecord.setId(1L);
            subRecord.setUserId(1L);
            subRecord.setStripeSubscriptionId("sub_test123");
            subRecord.setStatus("past_due");
            subRecord.setPlan("PRO");
            subRecord.setLastWebhookEventId("evt_same");

            when(billingPort.findSubscriptionByStripeId("sub_test123")).thenReturn(subRecord);
            // 原子更新返回0表示被幂等拦截
            lenient().when(billingPort.atomicUpdateSubscription(anyLong(), anyString(), anyString(), anyLong())).thenReturn(0);

            Invoice invoice = mock(Invoice.class);
            when(invoice.getSubscription()).thenReturn("sub_test123");

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(invoice));
            when(event.getId()).thenReturn("evt_same");
            when(event.getCreated()).thenReturn(1700000000L);

            subscriptionService.handleInvoicePaymentSucceeded(event);

            verify(userRepositoryPort, never()).update(any());
        }
    }

    // ==================== Orphaned invoice: creates new subscription record ====================

    @Nested
    @DisplayName("Orphaned invoice (no local record)")
    class OrphanedInvoiceTests {

        @Test
        void 无userId时记录警告并返回() {
            when(billingPort.findSubscriptionByStripeId("sub_orphan")).thenReturn(null);

            Invoice invoice = mock(Invoice.class);
            when(invoice.getSubscription()).thenReturn("sub_orphan");
            when(invoice.getMetadata()).thenReturn(Map.of());

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(invoice));

            assertDoesNotThrow(() -> subscriptionService.handleInvoicePaymentSucceeded(event));

            verify(billingPort, never()).saveCustomer(any());
            verify(billingPort, never()).saveSubscription(any());
        }

        @Test
        void StripeAPI失败时正常返回() {
            when(billingPort.findSubscriptionByStripeId("sub_orphan")).thenReturn(null);

            Invoice invoice = mock(Invoice.class);
            when(invoice.getSubscription()).thenReturn("sub_orphan");
            when(invoice.getMetadata()).thenReturn(Map.of("userId", "1"));

            StripeCustomer existingCustomer = new StripeCustomer();
            existingCustomer.setId(1L);
            existingCustomer.setUserId(1L);
            existingCustomer.setStripeCustomerId("cus_test123");
            when(billingPort.findCustomerByUserIdAndNotDeleted(1L)).thenReturn(existingCustomer);

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(invoice));
            when(event.getId()).thenReturn("evt_orphan_api_err");

            when(paymentPort.retrieveSubscription("sub_orphan"))
                    .thenThrow(new com.stripe.exception.ApiConnectionException("Stripe API connection error", null));

            assertDoesNotThrow(() -> subscriptionService.handleInvoicePaymentSucceeded(event));

            verify(billingPort, never()).saveSubscription(any());
        }

        @Test
        void 孤立发票创建完整订阅记录() {
            when(billingPort.findSubscriptionByStripeId("sub_orphan123")).thenReturn(null);

            Invoice invoice = mock(Invoice.class);
            when(invoice.getSubscription()).thenReturn("sub_orphan123");
            when(invoice.getMetadata()).thenReturn(Map.of(
                    "userId", "1",
                    "plan", "MAX",
                    "billingCycle", "yearly"
            ));

            StripeCustomer existingCustomer = new StripeCustomer();
            existingCustomer.setId(1L);
            existingCustomer.setUserId(1L);
            existingCustomer.setStripeCustomerId("cus_test123");
            when(billingPort.findCustomerByUserIdAndNotDeleted(1L)).thenReturn(existingCustomer);

            User user = new User();
            user.setId(1L);
            user.setUserLevel("FREE");
            when(userRepositoryPort.findById(1L)).thenReturn(Optional.of(user));
            doNothing().when(userRepositoryPort).update(any(User.class));
            doNothing().when(userRepositoryPort).savePlanHistory(any(UserPlanHistory.class));

            // SubscriptionInfo from paymentPort.retrieveSubscription
            SubscriptionInfo stripeSubInfo = new SubscriptionInfo(
                    "sub_orphan123", "active",
                    1700000000L, 1700100000L,
                    false, "si_item1", "price_max_yearly", null);
            when(paymentPort.retrieveSubscription("sub_orphan123")).thenReturn(stripeSubInfo);

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(invoice));
            when(event.getId()).thenReturn("evt_orphan_create");
            when(event.getCreated()).thenReturn(1700000000L);

            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

            subscriptionService.handleInvoicePaymentSucceeded(event);

            // Verify insert was called
            verify(billingPort).saveSubscription(argThat(sub ->
                    sub.getStripeSubscriptionId().equals("sub_orphan123")
                        && sub.getUserId().equals(1L)
                        && sub.getPlan().equals("MAX")
                        && sub.getBillingCycle().equals("yearly")
            ));
            // Verify user level was updated
            verify(userRepositoryPort).update(argThat(u -> "MAX".equals(u.getUserLevel())));
        }

        @Test
        void 孤立发票无plan时使用默认PRO() {
            when(billingPort.findSubscriptionByStripeId("sub_orphan_noplan")).thenReturn(null);

            Invoice invoice = mock(Invoice.class);
            when(invoice.getSubscription()).thenReturn("sub_orphan_noplan");
            when(invoice.getMetadata()).thenReturn(Map.of("userId", "1"));

            StripeCustomer existingCustomer = new StripeCustomer();
            existingCustomer.setId(1L);
            existingCustomer.setUserId(1L);
            existingCustomer.setStripeCustomerId("cus_test123");
            when(billingPort.findCustomerByUserIdAndNotDeleted(1L)).thenReturn(existingCustomer);

            User user = new User();
            user.setId(1L);
            user.setUserLevel("FREE");
            when(userRepositoryPort.findById(1L)).thenReturn(Optional.of(user));
            doNothing().when(userRepositoryPort).update(any(User.class));
            doNothing().when(userRepositoryPort).savePlanHistory(any(UserPlanHistory.class));

            // SubscriptionInfo from paymentPort.retrieveSubscription
            SubscriptionInfo stripeSubInfo = new SubscriptionInfo(
                    "sub_orphan_noplan", "active",
                    null, null,
                    false, "si_item2", "price_pro_monthly", null);
            when(paymentPort.retrieveSubscription("sub_orphan_noplan")).thenReturn(stripeSubInfo);

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(invoice));
            when(event.getId()).thenReturn("evt_orphan_noplan");
            when(event.getCreated()).thenReturn(1700000000L);

            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

            subscriptionService.handleInvoicePaymentSucceeded(event);

            verify(billingPort).saveSubscription(argThat(sub ->
                    "PRO".equals(sub.getPlan()) && "monthly".equals(sub.getBillingCycle())
            ));
        }

        @Disabled("需要 Spring 上下文初始化 MyBatis-Plus 实体缓存")
        @Test
        void 并发竞态时双重检查防止重复创建() {
            // First call (orphan check) returns null, but by the time we insert another thread already created it
            when(billingPort.findSubscriptionByStripeId("sub_race"))
                .thenReturn(null)  // orphan check
                .thenReturn(new StripeSubscription()); // re-query after concurrent insert

            Invoice invoice = mock(Invoice.class);
            when(invoice.getSubscription()).thenReturn("sub_race");
            when(invoice.getMetadata()).thenReturn(Map.of(
                    "userId", "1",
                    "plan", "PRO"
            ));

            StripeCustomer existingCustomer = new StripeCustomer();
            existingCustomer.setId(1L);
            existingCustomer.setUserId(1L);
            existingCustomer.setStripeCustomerId("cus_test123");
            when(billingPort.findCustomerByUserIdAndNotDeleted(1L)).thenReturn(existingCustomer);

            // SubscriptionInfo from paymentPort.retrieveSubscription
            SubscriptionInfo stripeSubInfo = new SubscriptionInfo(
                    "sub_race", "active",
                    null, null,
                    false, "si_item3", "price_pro_monthly", null);
            when(paymentPort.retrieveSubscription("sub_race")).thenReturn(stripeSubInfo);

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(invoice));
            when(event.getId()).thenReturn("evt_race");
            when(event.getCreated()).thenReturn(1700000000L);

            // Simulate duplicate key exception
            doThrow(new org.springframework.dao.DuplicateKeyException("duplicate"))
                    .when(billingPort).saveSubscription(any());

            assertDoesNotThrow(() -> subscriptionService.handleInvoicePaymentSucceeded(event));
        }
    }

    // ==================== Idempotency: multiple identical events ====================

    @Nested
    @DisplayName("Idempotency: multiple identical events")
    class IdempotencyTests {

        @Disabled("需要 Spring 上下文初始化 MyBatis-Plus 实体缓存")
        @Test
        void 三次相同事件只处理一次() {
            StripeSubscription subRecord = new StripeSubscription();
            subRecord.setId(1L);
            subRecord.setUserId(1L);
            subRecord.setStripeSubscriptionId("sub_test123");
            subRecord.setStatus("past_due");
            subRecord.setPlan("PRO");
            subRecord.setLastWebhookEventId("evt_old");

            User user = new User();
            user.setId(1L);
            user.setUserLevel("FREE");
            when(userRepositoryPort.findById(1L)).thenReturn(Optional.of(user));
            doNothing().when(userRepositoryPort).update(any(User.class));
            doNothing().when(userRepositoryPort).savePlanHistory(any(UserPlanHistory.class));

            when(billingPort.findSubscriptionByStripeId("sub_test123")).thenReturn(subRecord);
            // 第一次调用成功更新（返回1），后续调用被幂等拦截（返回0）
            lenient().when(billingPort.atomicUpdateSubscription(anyLong(), anyString(), anyString(), anyLong()))
                    .thenReturn(1)
                    .thenReturn(0)
                    .thenReturn(0);

            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

            Invoice invoice = mock(Invoice.class);
            when(invoice.getSubscription()).thenReturn("sub_test123");

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(invoice));
            when(event.getId()).thenReturn("evt_same_idempotent");
            when(event.getCreated()).thenReturn(1700000000L);

            // 发送三次
            subscriptionService.handleInvoicePaymentSucceeded(event);
            subscriptionService.handleInvoicePaymentSucceeded(event);
            subscriptionService.handleInvoicePaymentSucceeded(event);

            // updateUserLevel 里的 markEventProcessed 只允许一次，所以 findById 只被调用一次
            verify(userRepositoryPort, times(1)).findById(1L);
            verify(userRepositoryPort, times(1)).update(any(User.class));
            verify(userRepositoryPort, times(1)).savePlanHistory(any(UserPlanHistory.class));
        }
    }
}
