package com.yumu.noveltranslator.application.service;
import com.yumu.noveltranslator.util.JwtUtils;
import com.yumu.noveltranslator.application.service.SubscriptionApplicationService;
import com.yumu.noveltranslator.port.out.TokenRevocationPort;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stripe.exception.StripeException;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import com.stripe.model.Price;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.yumu.noveltranslator.port.out.payment.PaymentSessionInfo;
import com.yumu.noveltranslator.port.out.payment.SubscriptionInfo;
import com.yumu.noveltranslator.port.out.payment.SubscriptionUpdateRequest;
import com.yumu.noveltranslator.port.dto.subscription.CheckoutSessionRequest;
import com.yumu.noveltranslator.port.dto.subscription.CheckoutSessionResponse;
import com.yumu.noveltranslator.port.dto.subscription.PaymentVerificationResponse;
import com.yumu.noveltranslator.port.dto.subscription.SubscriptionStatusResponse;
import com.yumu.noveltranslator.domain.model.StripeCustomer;
import com.yumu.noveltranslator.domain.model.StripeSubscription;
import com.yumu.noveltranslator.domain.model.User;
import com.yumu.noveltranslator.domain.model.UserPlanHistory;
import com.yumu.noveltranslator.port.out.BillingRepositoryPort;
import com.yumu.noveltranslator.port.out.UserRepositoryPort;
import com.yumu.noveltranslator.properties.StripeProperties;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SubscriptionService 补充测试 - verifyCheckoutSession、upgradeSubscription、
 * cancelSubscription 成功路径
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SubscriptionService 二次补充测试")
class SubscriptionServiceSecondExtendedTest {

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

    private SubscriptionApplicationService service;

    @BeforeEach
    void setUp() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        service = new SubscriptionApplicationService(
                stripeProperties, billingPort, userRepositoryPort, stringRedisTemplate,
                tokenRevocationPort, paymentPort, transactionManager);
    }

    // ============ verifyCheckoutSession ============

    @Nested
    @DisplayName("verifyCheckoutSession")
    class VerifyCheckoutSessionTests {

        @Test
        void 空sessionId返回错误() {
            PaymentVerificationResponse resp = service.verifyCheckoutSession(null, 1L);
            assertFalse(resp.isPaid());
            assertEquals("缺少 session_id 参数", resp.getMessage());
        }

        @Test
        void 空白sessionId返回错误() {
            PaymentVerificationResponse resp = service.verifyCheckoutSession("   ", 1L);
            assertFalse(resp.isPaid());
        }

        @Test
        void userId不匹配返回错误() {
            when(paymentPort.retrieveCheckoutSession("cs_test"))
                    .thenReturn(new PaymentSessionInfo("cs_test", "paid", "sub_test", Map.of("userId", "999", "plan", "pro")));

            PaymentVerificationResponse resp = service.verifyCheckoutSession("cs_test", 1L);

            assertFalse(resp.isPaid());
            assertEquals("支付会话不属于当前用户", resp.getMessage());
        }

        @Test
        void paid且本地已处理返回成功() {
            when(paymentPort.retrieveCheckoutSession("cs_test"))
                    .thenReturn(new PaymentSessionInfo("cs_test", "paid", "sub_123", Map.of("userId", "1", "plan", "PRO")));

            StripeSubscription localSub = new StripeSubscription();
            localSub.setId(1L);
            localSub.setStatus("active");
            when(billingPort.findSubscriptionByStripeId("sub_123")).thenReturn(localSub);

            PaymentVerificationResponse resp = service.verifyCheckoutSession("cs_test", 1L);

            assertTrue(resp.isPaid());
            assertEquals("支付成功，订阅已激活", resp.getMessage());
            assertEquals("active", resp.getStatus());
        }

        @Test
        void paid但本地未处理返回pending() {
            when(paymentPort.retrieveCheckoutSession("cs_test"))
                    .thenReturn(new PaymentSessionInfo("cs_test", "paid", "sub_123", Map.of("userId", "1", "plan", "PRO")));

            when(billingPort.findSubscriptionByStripeId("sub_123")).thenReturn(null);

            PaymentVerificationResponse resp = service.verifyCheckoutSession("cs_test", 1L);

            assertTrue(resp.isPaid());
            assertEquals("pending", resp.getStatus());
            assertEquals("支付已确认，订阅正在激活中", resp.getMessage());
        }

        @Test
        void unpaid返回失败() {
            when(paymentPort.retrieveCheckoutSession("cs_test"))
                    .thenReturn(new PaymentSessionInfo("cs_test", "unpaid", null, Map.of("userId", "1", "plan", "PRO")));

            PaymentVerificationResponse resp = service.verifyCheckoutSession("cs_test", 1L);

            assertFalse(resp.isPaid());
            assertEquals("unpaid", resp.getStatus());
            assertEquals("支付尚未完成", resp.getMessage());
        }

        @Test
        void noPaymentRequired返回失败() {
            when(paymentPort.retrieveCheckoutSession("cs_test"))
                    .thenReturn(new PaymentSessionInfo("cs_test", "no_payment_required", null, Map.of("userId", "1", "plan", "PRO")));

            PaymentVerificationResponse resp = service.verifyCheckoutSession("cs_test", 1L);

            assertFalse(resp.isPaid());
            assertEquals("no_payment_required", resp.getStatus());
        }

        @Test
        void stripe异常返回失败() {
            when(paymentPort.retrieveCheckoutSession("cs_test"))
                    .thenThrow(new RuntimeException("Stripe error"));

            PaymentVerificationResponse resp = service.verifyCheckoutSession("cs_test", 1L);

            assertFalse(resp.isPaid());
            assertEquals("无法验证支付状态", resp.getMessage());
        }
    }

    // ============ cancelSubscription 成功路径 ============

    @Nested
    @DisplayName("cancelSubscription 成功路径")
    class CancelSubscriptionSuccessTests {

        @Test
        void 成功取消订阅() {
            StripeSubscription sub = new StripeSubscription();
            sub.setId(1L);
            sub.setUserId(1L);
            sub.setStripeSubscriptionId("sub_cancel");
            sub.setPlan("PRO");
            sub.setStatus("active");
            sub.setCurrentPeriodEnd(LocalDateTime.of(2026, 7, 1, 0, 0));
            sub.setCancelAtPeriodEnd(false);
            when(billingPort.findActiveSubscriptionByUserId(1L)).thenReturn(sub);
            when(paymentPort.updateSubscription(eq("sub_cancel"), any()))
                    .thenReturn(new SubscriptionInfo("sub_cancel", "active", null, null, true, null, null, null));

            SubscriptionStatusResponse resp = service.cancelSubscription(1L);

            assertNotNull(resp);
            assertTrue(resp.getCancelAtPeriodEnd());
            verify(billingPort).updateSubscription(argThat(s ->
                    Boolean.TRUE.equals(s.getCancelAtPeriodEnd())));
        }

        @Test
        void trialing状态可以取消() {
            StripeSubscription sub = new StripeSubscription();
            sub.setId(1L);
            sub.setUserId(1L);
            sub.setStripeSubscriptionId("sub_trial_cancel");
            sub.setPlan("MAX");
            sub.setStatus("trialing");
            sub.setCurrentPeriodEnd(LocalDateTime.of(2026, 8, 1, 0, 0));
            when(billingPort.findActiveSubscriptionByUserId(1L)).thenReturn(sub);
            when(paymentPort.updateSubscription(eq("sub_trial_cancel"), any()))
                    .thenReturn(new SubscriptionInfo("sub_trial_cancel", "trialing", null, null, true, null, null, null));

            SubscriptionStatusResponse resp = service.cancelSubscription(1L);

            assertNotNull(resp);
            assertTrue(resp.getCancelAtPeriodEnd());
        }

        @Test
        void stripe取消异常抛出RuntimeException() {
            StripeSubscription sub = new StripeSubscription();
            sub.setId(1L);
            sub.setUserId(1L);
            sub.setStripeSubscriptionId("sub_err");
            sub.setStatus("active");
            when(billingPort.findActiveSubscriptionByUserId(1L)).thenReturn(sub);
            when(paymentPort.updateSubscription(eq("sub_err"), any()))
                    .thenThrow(new RuntimeException("Stripe API error"));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> service.cancelSubscription(1L));
            assertEquals("取消订阅失败", ex.getMessage());
        }
    }

    // ============ upgradeSubscription 间接测试 (via createCheckoutSession) ============

    @Nested
    @DisplayName("upgradeSubscription 间接测试")
    class UpgradeSubscriptionIndirectTests {

        @Test
        void 已有活跃订阅时升级而不是创建() throws StripeException {
            StripeCustomer existing = new StripeCustomer();
            existing.setUserId(1L);
            existing.setStripeCustomerId("cus_test");
            when(billingPort.findCustomerByUserIdAndNotDeleted(1L)).thenReturn(existing);

            StripeSubscription existingSub = new StripeSubscription();
            existingSub.setId(5L);
            existingSub.setUserId(1L);
            existingSub.setStripeSubscriptionId("sub_existing");
            existingSub.setPlan("PRO");
            existingSub.setStatus("active");
            existingSub.setStripePriceId("price_pro_monthly");
            existingSub.setBillingCycle("monthly");
            existingSub.setCurrentPeriodStart(LocalDateTime.of(2026, 1, 1, 0, 0));
            existingSub.setCurrentPeriodEnd(LocalDateTime.of(2026, 2, 1, 0, 0));
            existingSub.setCancelAtPeriodEnd(false);
            when(billingPort.findActiveSubscriptionByUserId(1L))
                    .thenReturn(existingSub);

            StripeProperties.PlanPrices maxPrices = new StripeProperties.PlanPrices();
            maxPrices.setMonthlyPriceId("price_max_monthly");
            maxPrices.setYearlyPriceId("price_max_yearly");
            when(stripeProperties.getPrices()).thenReturn(Map.of("max", maxPrices));
            when(stripeProperties.getSuccessUrl()).thenReturn("https://example.com/success");
            when(stripeProperties.getCancelUrl()).thenReturn("https://example.com/cancel");

            User user = new User();
            user.setId(1L);
            user.setUserLevel("PRO");
            when(userRepositoryPort.findById(1L)).thenReturn(Optional.of(user));
            doNothing().when(userRepositoryPort).update(any(User.class));
            doNothing().when(userRepositoryPort).savePlanHistory(any(UserPlanHistory.class));

            when(paymentPort.retrieveSubscription("sub_existing")).thenReturn(
                    new SubscriptionInfo("sub_existing", "active", 1700000000L, 1702592000L, false, "si_test", "price_pro_monthly", null));
            when(paymentPort.updateSubscription(eq("sub_existing"), any(SubscriptionUpdateRequest.class))).thenReturn(
                    new SubscriptionInfo("sub_existing", "active", 1700000000L, 1702592000L, false, "si_test", "price_max_monthly", null));

            CheckoutSessionRequest req = new CheckoutSessionRequest();
            req.setPlan("MAX");
            req.setBillingCycle("monthly");

            CheckoutSessionResponse resp = service.createCheckoutSession(1L, req);

            assertNull(resp.getCheckoutUrl());
            verify(billingPort, never()).saveSubscription(any());
            verify(billingPort).updateSubscription(any());
        }
    }
}
