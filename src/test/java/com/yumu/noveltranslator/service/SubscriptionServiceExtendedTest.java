package com.yumu.noveltranslator.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import com.stripe.model.Price;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SubscriptionService 补充测试
 * 覆盖现有测试未覆盖的成功路径：cancelSubscription 成功、
 * handleCheckoutSessionCompleted 成功、handleSubscriptionUpdated 多状态、
 * handleSubscriptionResumed 成功、getPriceId 错误路径、updateUserLevel 成功
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SubscriptionService 补充测试")
class SubscriptionServiceExtendedTest {

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
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private SubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        subscriptionService = new SubscriptionService(
                stripeProperties, stripeCustomerMapper, stripeSubscriptionMapper,
                userMapper, userPlanHistoryMapper, stringRedisTemplate);
    }

    // ============ cancelSubscription 补充分支 ============

    @Nested
    @DisplayName("cancelSubscription - 补充分支")
    class CancelSubscriptionExtendedTests {

        @Test
        void 已取消的订阅不能再次取消() {
            // cancelSubscription 只查询 status in ("active", "trialing") 的记录
            // 已取消的订阅 status 为 "canceled"，不会被查到
            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> subscriptionService.cancelSubscription(1L));
            assertTrue(ex.getMessage().contains("没有可取消的活跃订阅"));
        }

        @Test
        void past_due状态不能取消() {
            // past_due 不在 in("active", "trialing") 范围内
            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> subscriptionService.cancelSubscription(1L));
            assertTrue(ex.getMessage().contains("没有可取消的活跃订阅"));
        }
    }

    // ============ handleCheckoutSessionCompleted 成功路径 ============

    @Nested
    @DisplayName("handleCheckoutSessionCompleted - 成功路径")
    class HandleCheckoutSessionCompletedSuccessTests {

        @Test
        void 创建新订阅记录并更新用户等级() {
            Session session = mock(Session.class);
            when(session.getId()).thenReturn("cs_test");
            when(session.getMetadata()).thenReturn(Map.of(
                    "userId", "1",
                    "plan", "PRO",
                    "billingCycle", "monthly"
            ));
            when(session.getSubscription()).thenReturn("sub_new");

            User user = new User();
            user.setId(1L);
            user.setUserLevel("FREE");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(userMapper.updateById(any(User.class))).thenReturn(1);
            when(userPlanHistoryMapper.insert(any(UserPlanHistory.class))).thenReturn(1);

            Subscription stripeSub = mock(Subscription.class);
            when(stripeSub.getStatus()).thenReturn("active");
            when(stripeSub.getCancelAtPeriodEnd()).thenReturn(false);
            when(stripeSub.getCurrentPeriodStart()).thenReturn(1700000000L);
            when(stripeSub.getCurrentPeriodEnd()).thenReturn(1702592000L);

            SubscriptionItem subItem = mock(SubscriptionItem.class);
            Price price = mock(Price.class);
            when(price.getId()).thenReturn("price_pro_monthly");
            when(subItem.getPrice()).thenReturn(price);
            SubscriptionItemCollection items = mock(SubscriptionItemCollection.class);
            when(items.getData()).thenReturn(List.of(subItem));
            when(stripeSub.getItems()).thenReturn(items);

            // 已有客户
            StripeCustomer customer = new StripeCustomer();
            customer.setUserId(1L);
            customer.setStripeCustomerId("cus_test");

            try (MockedStatic<Subscription> subStatic = mockStatic(Subscription.class)) {
                subStatic.when(() -> Subscription.retrieve("sub_new")).thenReturn(stripeSub);

                Event event = mock(Event.class);
                EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
                when(event.getDataObjectDeserializer()).thenReturn(deserializer);
                when(deserializer.getObject()).thenReturn(Optional.of(session));
                when(event.getId()).thenReturn("evt_checkout123");

                // stripeCustomerMapper.selectOne 返回已有客户
                when(stripeCustomerMapper.selectOne(any())).thenReturn(customer);
                // stripeSubscriptionMapper.selectOne 返回 null（新订阅）
                when(stripeSubscriptionMapper.selectOne(any())).thenReturn(null);

                subscriptionService.handleCheckoutSessionCompleted(event);

                verify(stripeSubscriptionMapper).insert(argThat(s ->
                        "PRO".equals(s.getPlan()) && "sub_new".equals(s.getStripeSubscriptionId())));
                verify(userMapper).updateById(argThat(u -> "PRO".equals(u.getUserLevel())));
            }
        }

        @Test
        void 已有订阅记录幂等更新() {
            Session session = mock(Session.class);
            when(session.getId()).thenReturn("cs_test2");
            when(session.getMetadata()).thenReturn(Map.of(
                    "userId", "2",
                    "plan", "PRO",
                    "billingCycle", "monthly"
            ));
            when(session.getSubscription()).thenReturn("sub_existing");

            StripeSubscription existingSub = new StripeSubscription();
            existingSub.setId(10L);
            existingSub.setUserId(2L);
            existingSub.setStripeSubscriptionId("sub_existing");
            existingSub.setPlan("PRO");
            existingSub.setStatus("active");

            StripeCustomer customer = new StripeCustomer();
            customer.setUserId(2L);
            customer.setStripeCustomerId("cus_test2");

            Subscription stripeSub = mock(Subscription.class);

            try (MockedStatic<Subscription> subStatic = mockStatic(Subscription.class)) {
                subStatic.when(() -> Subscription.retrieve("sub_existing")).thenReturn(stripeSub);

                Event event = mock(Event.class);
                EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
                when(event.getDataObjectDeserializer()).thenReturn(deserializer);
                when(deserializer.getObject()).thenReturn(Optional.of(session));
                when(event.getId()).thenReturn("evt_checkout456");

                // stripeCustomerMapper.selectOne 返回已有客户
                when(stripeCustomerMapper.selectOne(any())).thenReturn(customer);
                // stripeSubscriptionMapper.selectOne 返回已有订阅
                when(stripeSubscriptionMapper.selectOne(any())).thenReturn(existingSub);

                subscriptionService.handleCheckoutSessionCompleted(event);

                verify(stripeSubscriptionMapper, never()).insert(any());
                verify(stripeSubscriptionMapper).updateById(argThat(s ->
                        "evt_checkout456".equals(s.getLastWebhookEventId())));
            }
        }
    }

    // ============ handleSubscriptionUpdated 多状态 ============

    @Nested
    @DisplayName("handleSubscriptionUpdated - 多状态处理")
    class HandleSubscriptionUpdatedStatusTests {

        private Event buildUpdatedEvent(Subscription stripeSub, String eventId) {
            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(stripeSub));
            when(event.getId()).thenReturn(eventId);
            return event;
        }

        private Subscription buildStripeSub(String id, String status) {
            Subscription stripeSub = mock(Subscription.class);
            when(stripeSub.getId()).thenReturn(id);
            when(stripeSub.getStatus()).thenReturn(status);
            when(stripeSub.getCancelAtPeriodEnd()).thenReturn(false);
            when(stripeSub.getCurrentPeriodStart()).thenReturn(null);
            when(stripeSub.getCurrentPeriodEnd()).thenReturn(null);
            when(stripeSub.getCanceledAt()).thenReturn(null);

            SubscriptionItem subItem = mock(SubscriptionItem.class);
            Price price = mock(Price.class);
            when(price.getId()).thenReturn("price_test");
            when(subItem.getPrice()).thenReturn(price);
            SubscriptionItemCollection items = mock(SubscriptionItemCollection.class);
            when(items.getData()).thenReturn(List.of(subItem));
            when(stripeSub.getItems()).thenReturn(items);
            return stripeSub;
        }

        private StripeSubscription buildLocalSub(String subId, String plan) {
            StripeSubscription subRecord = new StripeSubscription();
            subRecord.setId(1L);
            subRecord.setUserId(1L);
            subRecord.setStripeSubscriptionId(subId);
            subRecord.setStatus("active");
            subRecord.setPlan(plan);
            subRecord.setLastWebhookEventId("evt_old");
            return subRecord;
        }

        @Test
        void past_due状态不降级仅记录() {
            StripeSubscription localSub = buildLocalSub("sub_pastdue", "PRO");
            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(localSub);

            Subscription stripeSub = buildStripeSub("sub_pastdue", "past_due");
            Event event = buildUpdatedEvent(stripeSub, "evt_pastdue");

            subscriptionService.handleSubscriptionUpdated(event);

            verify(stripeSubscriptionMapper).updateById(any());
            verify(userMapper, never()).updateById(any());
        }

        @Test
        void paused状态不更改用户等级() {
            StripeSubscription localSub = buildLocalSub("sub_paused", "PRO");
            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(localSub);

            Subscription stripeSub = buildStripeSub("sub_paused", "paused");
            Event event = buildUpdatedEvent(stripeSub, "evt_paused");

            subscriptionService.handleSubscriptionUpdated(event);

            verify(stripeSubscriptionMapper).updateById(any());
            verify(userMapper, never()).updateById(any());
        }

        @Test
        void trialing状态升级用户等级() {
            User user = new User();
            user.setId(1L);
            user.setUserLevel("FREE");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(userMapper.updateById(any(User.class))).thenReturn(1);
            when(userPlanHistoryMapper.insert(any(UserPlanHistory.class))).thenReturn(1);

            StripeSubscription localSub = buildLocalSub("sub_trialing", "PRO");
            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(localSub);

            Subscription stripeSub = buildStripeSub("sub_trialing", "trialing");
            Event event = buildUpdatedEvent(stripeSub, "evt_trialing");

            subscriptionService.handleSubscriptionUpdated(event);

            verify(userMapper).updateById(argThat(u -> "PRO".equals(u.getUserLevel())));
        }

        @Test
        void unpaid状态降级为FREE() {
            User user = new User();
            user.setId(1L);
            user.setUserLevel("PRO");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(userMapper.updateById(any(User.class))).thenReturn(1);
            when(userPlanHistoryMapper.insert(any(UserPlanHistory.class))).thenReturn(1);

            StripeSubscription localSub = buildLocalSub("sub_unpaid", "PRO");
            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(localSub);

            Subscription stripeSub = buildStripeSub("sub_unpaid", "unpaid");
            Event event = buildUpdatedEvent(stripeSub, "evt_unpaid");

            subscriptionService.handleSubscriptionUpdated(event);

            verify(userMapper).updateById(argThat(u -> "FREE".equals(u.getUserLevel())));
        }

        @Test
        void active状态恢复用户等级() {
            User user = new User();
            user.setId(1L);
            user.setUserLevel("FREE");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(userMapper.updateById(any(User.class))).thenReturn(1);
            when(userPlanHistoryMapper.insert(any(UserPlanHistory.class))).thenReturn(1);

            StripeSubscription localSub = buildLocalSub("sub_reactivated", "PRO");
            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(localSub);

            Subscription stripeSub = buildStripeSub("sub_reactivated", "active");
            Event event = buildUpdatedEvent(stripeSub, "evt_reactivated");

            subscriptionService.handleSubscriptionUpdated(event);

            verify(userMapper).updateById(argThat(u -> "PRO".equals(u.getUserLevel())));
        }
    }

    // ============ handleSubscriptionResumed 成功路径 ============

    @Nested
    @DisplayName("handleSubscriptionResumed - 成功路径")
    class HandleSubscriptionResumedSuccessTests {

        @Test
        void 恢复订阅更新状态() {
            StripeSubscription subRecord = new StripeSubscription();
            subRecord.setId(1L);
            subRecord.setUserId(1L);
            subRecord.setStripeSubscriptionId("sub_resumed");
            subRecord.setStatus("paused");

            Subscription stripeSub = mock(Subscription.class);
            when(stripeSub.getId()).thenReturn("sub_resumed");
            when(stripeSub.getStatus()).thenReturn("active");

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(stripeSub));
            when(event.getId()).thenReturn("evt_resumed");

            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(subRecord);

            subscriptionService.handleSubscriptionResumed(event);

            verify(stripeSubscriptionMapper).updateById(argThat(s ->
                    "active".equals(s.getStatus()) && "evt_resumed".equals(s.getLastWebhookEventId())));
        }
    }

    // ============ getPriceId 错误路径 ============

    @Nested
    @DisplayName("getPriceId - 配置错误")
    class GetPriceIdErrorTests {

        @Test
        void prices配置为null抛出异常() {
            StripeCustomer existing = new StripeCustomer();
            existing.setUserId(1L);
            existing.setStripeCustomerId("cus_test");
            when(stripeCustomerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
            when(stripeProperties.getPrices()).thenReturn(null);

            CheckoutSessionRequest request = new CheckoutSessionRequest();
            request.setPlan("PRO");
            request.setBillingCycle("monthly");

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> subscriptionService.createCheckoutSession(1L, request));
            // getPriceId throws RuntimeException directly (not wrapped)
            assertTrue(ex.getMessage().contains("prices"));
        }

        @Test
        void 套餐价格配置缺失抛出异常() {
            StripeCustomer existing = new StripeCustomer();
            existing.setUserId(1L);
            existing.setStripeCustomerId("cus_test");
            when(stripeCustomerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
            when(stripeProperties.getPrices()).thenReturn(Map.of());

            CheckoutSessionRequest request = new CheckoutSessionRequest();
            request.setPlan("PRO");
            request.setBillingCycle("monthly");

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> subscriptionService.createCheckoutSession(1L, request));
            assertTrue(ex.getMessage().contains("prices configured"));
        }

        @Test
        void 价格ID为空字符串抛出异常() {
            StripeCustomer existing = new StripeCustomer();
            existing.setUserId(1L);
            existing.setStripeCustomerId("cus_test");
            when(stripeCustomerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

            StripeProperties.PlanPrices planPrices = new StripeProperties.PlanPrices();
            planPrices.setMonthlyPriceId("");
            when(stripeProperties.getPrices()).thenReturn(Map.of("pro", planPrices));

            CheckoutSessionRequest request = new CheckoutSessionRequest();
            request.setPlan("PRO");
            request.setBillingCycle("monthly");

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> subscriptionService.createCheckoutSession(1L, request));
            assertTrue(ex.getMessage().contains("price configured"));
        }
    }

    // ============ updateUserLevel 成功路径 ============

    @Nested
    @DisplayName("updateUserLevel - 成功路径")
    class UpdateUserLevelSuccessTests {

        @Test
        void 用户等级从FREE变更为PRO() {
            StripeSubscription subRecord = new StripeSubscription();
            subRecord.setId(1L);
            subRecord.setUserId(1L);
            subRecord.setStripeSubscriptionId("sub_upgrade");
            subRecord.setStatus("active");
            subRecord.setPlan("PRO");
            subRecord.setLastWebhookEventId("evt_old");

            User user = new User();
            user.setId(1L);
            user.setUserLevel("FREE");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(userMapper.updateById(any(User.class))).thenReturn(1);
            when(userPlanHistoryMapper.insert(any(UserPlanHistory.class))).thenReturn(1);

            Subscription stripeSub = mock(Subscription.class);
            when(stripeSub.getId()).thenReturn("sub_upgrade");
            when(stripeSub.getStatus()).thenReturn("active");
            when(stripeSub.getCancelAtPeriodEnd()).thenReturn(false);
            when(stripeSub.getCurrentPeriodStart()).thenReturn(null);
            when(stripeSub.getCurrentPeriodEnd()).thenReturn(null);
            when(stripeSub.getCanceledAt()).thenReturn(null);

            SubscriptionItem subItem = mock(SubscriptionItem.class);
            Price price = mock(Price.class);
            when(price.getId()).thenReturn("price_test");
            when(subItem.getPrice()).thenReturn(price);
            SubscriptionItemCollection items = mock(SubscriptionItemCollection.class);
            when(items.getData()).thenReturn(List.of(subItem));
            when(stripeSub.getItems()).thenReturn(items);

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(stripeSub));
            when(event.getId()).thenReturn("evt_upgrade");

            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(subRecord);

            subscriptionService.handleSubscriptionUpdated(event);

            verify(userMapper).updateById(argThat(u -> "PRO".equals(u.getUserLevel())));
            verify(userPlanHistoryMapper).insert(argThat(h ->
                    "FREE".equals(h.getOldPlan()) && "PRO".equals(h.getNewPlan()) &&
                    h.getNote().contains("active")));
        }

        @Test
        void 用户等级为null时正常处理() {
            User user = new User();
            user.setId(1L);
            user.setUserLevel(null);
            when(userMapper.selectById(1L)).thenReturn(user);
            when(userMapper.updateById(any(User.class))).thenReturn(1);
            when(userPlanHistoryMapper.insert(any(UserPlanHistory.class))).thenReturn(1);

            StripeSubscription subRecord = new StripeSubscription();
            subRecord.setId(1L);
            subRecord.setUserId(1L);
            subRecord.setStripeSubscriptionId("sub_nulllevel");
            subRecord.setStatus("active");
            subRecord.setPlan("PRO");
            subRecord.setLastWebhookEventId("evt_old");

            Subscription stripeSub = mock(Subscription.class);
            when(stripeSub.getId()).thenReturn("sub_nulllevel");
            when(stripeSub.getStatus()).thenReturn("canceled");
            when(stripeSub.getCancelAtPeriodEnd()).thenReturn(false);
            when(stripeSub.getCurrentPeriodStart()).thenReturn(null);
            when(stripeSub.getCurrentPeriodEnd()).thenReturn(null);
            when(stripeSub.getCanceledAt()).thenReturn(null);

            SubscriptionItem subItem = mock(SubscriptionItem.class);
            Price price = mock(Price.class);
            when(price.getId()).thenReturn("price_test");
            when(subItem.getPrice()).thenReturn(price);
            SubscriptionItemCollection items = mock(SubscriptionItemCollection.class);
            when(items.getData()).thenReturn(List.of(subItem));
            when(stripeSub.getItems()).thenReturn(items);

            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(stripeSub));
            when(event.getId()).thenReturn("evt_nulllevel");

            when(stripeSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(subRecord);

            subscriptionService.handleSubscriptionUpdated(event);

            verify(userMapper).updateById(argThat(u -> "FREE".equals(u.getUserLevel())));
            verify(userPlanHistoryMapper).insert(argThat(h ->
                    "UNKNOWN".equals(h.getOldPlan()) && "FREE".equals(h.getNewPlan())));
        }
    }

    // ============ createCheckoutSession / getOrCreateCustomer ============

    @Nested
    @DisplayName("createCheckoutSession - 补充测试")
    class CreateCheckoutSessionTests {

        @Test
        void 已存在StripeCustomer直接复用() {
            StripeCustomer existing = new StripeCustomer();
            existing.setId(1L);
            existing.setUserId(1L);
            existing.setStripeCustomerId("cus_existing");
            when(stripeCustomerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

            when(stripeProperties.getSuccessUrl()).thenReturn("https://example.com/success");
            when(stripeProperties.getCancelUrl()).thenReturn("https://example.com/cancel");
            StripeProperties.PlanPrices prices = new StripeProperties.PlanPrices();
            prices.setMonthlyPriceId("price_pro_monthly");
            prices.setYearlyPriceId("price_pro_yearly");
            when(stripeProperties.getPrices()).thenReturn(Map.of("pro", prices));

            try (MockedStatic<Session> sessionStatic = mockStatic(Session.class)) {
                Session mockSession = mock(Session.class);
                when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/test");
                sessionStatic.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

                CheckoutSessionRequest req = new CheckoutSessionRequest();
                req.setPlan("pro");
                req.setBillingCycle("monthly");

                CheckoutSessionResponse resp = subscriptionService.createCheckoutSession(1L, req);

                assertNotNull(resp);
                assertNotNull(resp.getCheckoutUrl());
                // 验证没有创建新 customer（selectOne 只被调用一次）
                verify(stripeCustomerMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
            }
        }

        @Test
        void StripeCustomer不存在时创建新客户() {
            // 第一次查询返回 null，触发创建
            when(stripeCustomerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            User user = new User();
            user.setId(1L);
            user.setEmail("test@example.com");
            user.setUsername("TestUser");
            when(userMapper.selectById(1L)).thenReturn(user);

            StripeProperties.PlanPrices prices = new StripeProperties.PlanPrices();
            prices.setMonthlyPriceId("price_pro_monthly");
            when(stripeProperties.getPrices()).thenReturn(Map.of("pro", prices));
            when(stripeProperties.getSuccessUrl()).thenReturn("https://example.com/success");
            when(stripeProperties.getCancelUrl()).thenReturn("https://example.com/cancel");

            // 模拟 Stripe Customer 创建
            try (MockedStatic<com.stripe.model.Customer> custStatic = mockStatic(com.stripe.model.Customer.class)) {
                com.stripe.model.Customer mockCust = mock(com.stripe.model.Customer.class);
                when(mockCust.getId()).thenReturn("cus_new123");
                custStatic.when(() -> com.stripe.model.Customer.create(any(com.stripe.param.CustomerCreateParams.class)))
                        .thenReturn(mockCust);

                Session mockSession = mock(Session.class);
                when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/new");

                try (MockedStatic<Session> sessionStatic = mockStatic(Session.class)) {
                    sessionStatic.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

                    CheckoutSessionRequest req = new CheckoutSessionRequest();
                    req.setPlan("pro");
                    req.setBillingCycle("monthly");

                    CheckoutSessionResponse resp = subscriptionService.createCheckoutSession(1L, req);
                    assertNotNull(resp);
                    // 验证创建了 Stripe Customer
                    verify(stripeCustomerMapper).insert(any(StripeCustomer.class));
                }
            }
        }
    }

    // ============ handleCheckoutSessionCompleted - 补充 ============

    @Nested
    @DisplayName("handleCheckoutSessionCompleted - 补充测试")
    class HandleCheckoutSessionCompletedExtendedTests {

        @Test
        void 反序列化失败安全返回() throws EventDataObjectDeserializationException {
            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.empty());
            when(deserializer.deserializeUnsafe()).thenReturn(null);

            // handleCheckoutSessionCompleted 会捕获异常并安全返回，不抛出异常
            assertDoesNotThrow(() -> subscriptionService.handleCheckoutSessionCompleted(event));
        }
    }
}
