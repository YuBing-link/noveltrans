package com.yumu.noveltranslator.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import com.stripe.model.Price;
import com.yumu.noveltranslator.entity.StripeCustomer;
import com.yumu.noveltranslator.entity.StripeSubscription;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.entity.UserPlanHistory;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.StripeCustomerMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.StripeSubscriptionMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.UserMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.UserPlanHistoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionServiceInvoiceTest {

    @Mock
    private StripeCustomerMapper stripeCustomerMapper;

    @Mock
    private StripeSubscriptionMapper stripeSubscriptionMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserPlanHistoryMapper userPlanHistoryMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private com.yumu.noveltranslator.util.JwtUtils jwtUtils;

    private SubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
        subscriptionService = new SubscriptionService(
                null, stripeCustomerMapper, stripeSubscriptionMapper,
                userMapper, userPlanHistoryMapper, stringRedisTemplate,
                tokenBlacklistService, jwtUtils);
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

            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(subRecord);

            Invoice invoice = mock(Invoice.class);
            when(invoice.getSubscription()).thenReturn("sub_test123");

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(invoice));

            subscriptionService.handleInvoicePaymentSucceeded(event);

            verify(stripeSubscriptionMapper, never()).update(any(), any());
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

            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(subRecord);

            Invoice invoice = mock(Invoice.class);
            when(invoice.getSubscription()).thenReturn("sub_test123");

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(invoice));

            subscriptionService.handleInvoicePaymentSucceeded(event);

            verify(stripeSubscriptionMapper, never()).update(any(), any());
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
            when(userMapper.selectById(1L)).thenReturn(user);
            when(userMapper.updateById(any(User.class))).thenReturn(1);
            when(userPlanHistoryMapper.insert(any(UserPlanHistory.class))).thenReturn(1);

            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(subRecord);
            // Mock the update for atomic activation (LambdaUpdateWrapper triggers MP table cache issue in tests)
            lenient().when(stripeSubscriptionMapper.update(any(), any())).thenReturn(1);

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
            verify(stripeSubscriptionMapper).update(any(), any());
            // Verify user level was updated
            verify(userMapper).updateById(argThat(u -> "PRO".equals(u.getUserLevel())));
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

            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(subRecord);
            // 原子更新返回0表示被幂等拦截
            lenient().when(stripeSubscriptionMapper.update(any(), any())).thenReturn(0);

            Invoice invoice = mock(Invoice.class);
            when(invoice.getSubscription()).thenReturn("sub_test123");

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(invoice));
            when(event.getId()).thenReturn("evt_same");
            when(event.getCreated()).thenReturn(1700000000L);

            subscriptionService.handleInvoicePaymentSucceeded(event);

            verify(userMapper, never()).selectById(any());
            verify(userMapper, never()).updateById(any());
        }
    }

    // ==================== Orphaned invoice: creates new subscription record ====================

    @Nested
    @DisplayName("Orphaned invoice (no local record)")
    class OrphanedInvoiceTests {

        @Test
        void 无userId时记录警告并返回() {
            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            Invoice invoice = mock(Invoice.class);
            when(invoice.getSubscription()).thenReturn("sub_orphan");
            when(invoice.getMetadata()).thenReturn(Map.of());

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(invoice));

            assertDoesNotThrow(() -> subscriptionService.handleInvoicePaymentSucceeded(event));

            verify(stripeCustomerMapper, never()).selectOne(any());
            verify(stripeSubscriptionMapper, never()).insert(any());
        }

        @Test
        void StripeAPI失败时正常返回() {
            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            Invoice invoice = mock(Invoice.class);
            when(invoice.getSubscription()).thenReturn("sub_orphan");
            when(invoice.getMetadata()).thenReturn(Map.of("userId", "1"));

            StripeCustomer existingCustomer = new StripeCustomer();
            existingCustomer.setId(1L);
            existingCustomer.setUserId(1L);
            existingCustomer.setStripeCustomerId("cus_test123");
            when(stripeCustomerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingCustomer);

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(invoice));
            when(event.getId()).thenReturn("evt_orphan_api_err");

            try (MockedStatic<Subscription> subStatic = mockStatic(Subscription.class)) {
                subStatic.when(() -> Subscription.retrieve("sub_orphan"))
                        .thenThrow(new com.stripe.exception.ApiConnectionException("Stripe API connection error", null));

                assertDoesNotThrow(() -> subscriptionService.handleInvoicePaymentSucceeded(event));
            }

            verify(stripeSubscriptionMapper, never()).insert(any());
        }

        @Test
        void 孤立发票创建完整订阅记录() {
            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

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
            // First selectOne for orphan check (returns null), second for customer lookup
            when(stripeCustomerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingCustomer);

            User user = new User();
            user.setId(1L);
            user.setUserLevel("FREE");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(userMapper.updateById(any(User.class))).thenReturn(1);
            when(userPlanHistoryMapper.insert(any(UserPlanHistory.class))).thenReturn(1);

            Subscription stripeSub = mock(Subscription.class);
            when(stripeSub.getId()).thenReturn("sub_orphan123");
            when(stripeSub.getStatus()).thenReturn("active");
            when(stripeSub.getCancelAtPeriodEnd()).thenReturn(false);
            when(stripeSub.getCurrentPeriodStart()).thenReturn(1700000000L);
            when(stripeSub.getCurrentPeriodEnd()).thenReturn(1700100000L);
            when(stripeSub.getMetadata()).thenReturn(Map.of(
                    "userId", "1",
                    "plan", "MAX",
                    "billingCycle", "yearly"
            ));

            SubscriptionItem subItem = mock(SubscriptionItem.class);
            Price price = mock(Price.class);
            when(price.getId()).thenReturn("price_max_yearly");
            when(subItem.getPrice()).thenReturn(price);
            SubscriptionItemCollection items = mock(SubscriptionItemCollection.class);
            when(items.getData()).thenReturn(List.of(subItem));
            when(stripeSub.getItems()).thenReturn(items);

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(invoice));
            when(event.getId()).thenReturn("evt_orphan_create");
            when(event.getCreated()).thenReturn(1700000000L);

            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

            try (MockedStatic<Subscription> subStatic = mockStatic(Subscription.class)) {
                subStatic.when(() -> Subscription.retrieve("sub_orphan123")).thenReturn(stripeSub);

                subscriptionService.handleInvoicePaymentSucceeded(event);
            }

            // Verify insert was called
            verify(stripeSubscriptionMapper).insert(argThat(sub ->
                    sub.getStripeSubscriptionId().equals("sub_orphan123")
                        && sub.getUserId().equals(1L)
                        && sub.getPlan().equals("MAX")
                        && sub.getBillingCycle().equals("yearly")
            ));
            // Verify user level was updated
            verify(userMapper).updateById(argThat(u -> "MAX".equals(u.getUserLevel())));
        }

        @Test
        void 孤立发票无plan时使用默认PRO() {
            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            Invoice invoice = mock(Invoice.class);
            when(invoice.getSubscription()).thenReturn("sub_orphan_noplan");
            when(invoice.getMetadata()).thenReturn(Map.of("userId", "1"));

            StripeCustomer existingCustomer = new StripeCustomer();
            existingCustomer.setId(1L);
            existingCustomer.setUserId(1L);
            existingCustomer.setStripeCustomerId("cus_test123");
            when(stripeCustomerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingCustomer);

            User user = new User();
            user.setId(1L);
            user.setUserLevel("FREE");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(userMapper.updateById(any(User.class))).thenReturn(1);
            when(userPlanHistoryMapper.insert(any(UserPlanHistory.class))).thenReturn(1);

            Subscription stripeSub = mock(Subscription.class);
            when(stripeSub.getId()).thenReturn("sub_orphan_noplan");
            when(stripeSub.getStatus()).thenReturn("active");
            when(stripeSub.getCancelAtPeriodEnd()).thenReturn(false);
            when(stripeSub.getCurrentPeriodStart()).thenReturn(null);
            when(stripeSub.getCurrentPeriodEnd()).thenReturn(null);
            when(stripeSub.getMetadata()).thenReturn(Map.of("userId", "1"));

            SubscriptionItem subItem = mock(SubscriptionItem.class);
            Price price = mock(Price.class);
            when(price.getId()).thenReturn("price_pro_monthly");
            when(subItem.getPrice()).thenReturn(price);
            SubscriptionItemCollection items = mock(SubscriptionItemCollection.class);
            when(items.getData()).thenReturn(List.of(subItem));
            when(stripeSub.getItems()).thenReturn(items);

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(invoice));
            when(event.getId()).thenReturn("evt_orphan_noplan");
            when(event.getCreated()).thenReturn(1700000000L);

            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

            try (MockedStatic<Subscription> subStatic = mockStatic(Subscription.class)) {
                subStatic.when(() -> Subscription.retrieve("sub_orphan_noplan")).thenReturn(stripeSub);

                subscriptionService.handleInvoicePaymentSucceeded(event);
            }

            verify(stripeSubscriptionMapper).insert(argThat(sub ->
                    "PRO".equals(sub.getPlan()) && "monthly".equals(sub.getBillingCycle())
            ));
        }

        @Disabled("需要 Spring 上下文初始化 MyBatis-Plus 实体缓存")
        @Test
        void 并发竞态时双重检查防止重复创建() {
            // First call (orphan check) returns null, but by the time we insert another thread already created it
            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class)))
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
            when(stripeCustomerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingCustomer);

            Subscription stripeSub = mock(Subscription.class);
            when(stripeSub.getId()).thenReturn("sub_race");
            when(stripeSub.getStatus()).thenReturn("active");
            when(stripeSub.getCancelAtPeriodEnd()).thenReturn(false);
            when(stripeSub.getCurrentPeriodStart()).thenReturn(null);
            when(stripeSub.getCurrentPeriodEnd()).thenReturn(null);
            when(stripeSub.getMetadata()).thenReturn(Map.of("userId", "1", "plan", "PRO"));

            SubscriptionItem subItem = mock(SubscriptionItem.class);
            Price price = mock(Price.class);
            when(price.getId()).thenReturn("price_pro_monthly");
            when(subItem.getPrice()).thenReturn(price);
            SubscriptionItemCollection items = mock(SubscriptionItemCollection.class);
            when(items.getData()).thenReturn(List.of(subItem));
            when(stripeSub.getItems()).thenReturn(items);

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(invoice));
            when(event.getId()).thenReturn("evt_race");
            when(event.getCreated()).thenReturn(1700000000L);

            // Simulate duplicate key exception
            when(stripeSubscriptionMapper.insert(any())).thenThrow(
                    new org.springframework.dao.DuplicateKeyException("duplicate")
            );
            // For the re-query path after DuplicateKeyException, the update might be called
            lenient().when(stripeSubscriptionMapper.update(any(), any())).thenReturn(0);

            try (MockedStatic<Subscription> subStatic = mockStatic(Subscription.class)) {
                subStatic.when(() -> Subscription.retrieve("sub_race")).thenReturn(stripeSub);

                assertDoesNotThrow(() -> subscriptionService.handleInvoicePaymentSucceeded(event));
            }
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
            when(userMapper.selectById(1L)).thenReturn(user);
            when(userMapper.updateById(any(User.class))).thenReturn(1);
            when(userPlanHistoryMapper.insert(any(UserPlanHistory.class))).thenReturn(1);

            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(subRecord);
            // 第一次调用成功更新（返回1），后续调用被幂等拦截（返回0）
            // Use lenient() and generic matchers to avoid MyBatis-Plus LambdaUpdateWrapper cache issue
            lenient().when(stripeSubscriptionMapper.update(any(), any()))
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

            // updateUserLevel 里的 markEventProcessed 只允许一次，所以 selectById 只被调用一次
            verify(userMapper, times(1)).selectById(1L);
            verify(userMapper, times(1)).updateById(any(User.class));
            verify(userPlanHistoryMapper, times(1)).insert(any(UserPlanHistory.class));
        }
    }
}
