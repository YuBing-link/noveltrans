package com.yumu.noveltranslator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.security.CustomUserDetails;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.mockito.Mock
    private SubscriptionService subscriptionService;

    private SubscriptionController controller;

    /**
     * 测试用异常处理器，将未捕获异常转为 500 响应
     */
    @RestControllerAdvice
    static class TestExceptionHandler {
        @ExceptionHandler(MethodArgumentNotValidException.class)
        @ResponseStatus(HttpStatus.BAD_REQUEST)
        public Result<Void> handleValidation(MethodArgumentNotValidException e) {
            return Result.error("参数验证失败");
        }

        @ExceptionHandler(Exception.class)
        @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        public Result<Void> handleException(Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @BeforeEach
    void setUp() {
        controller = new SubscriptionController(subscriptionService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new TestExceptionHandler())
            .build();
    }

    private void setupSecurityContext(User user) {
        CustomUserDetails userDetails = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User createTestUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
        user.setUsername("testuser");
        user.setUserLevel("free");
        return user;
    }

    @Nested
    @DisplayName("创建支付会话")
    class CheckoutTests {

        @Test
        void 创建支付会话成功() throws Exception {
            setupSecurityContext(createTestUser());
            CheckoutSessionResponse response = new CheckoutSessionResponse("https://checkout.stripe.com/test-session");
            when(subscriptionService.createCheckoutSession(eq(1L), any(CheckoutSessionRequest.class)))
                .thenReturn(response);

            mockMvc.perform(post("/subscription/checkout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"plan\":\"PRO\",\"billingCycle\":\"monthly\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.checkoutUrl").value("https://checkout.stripe.com/test-session"));
        }

        @Test
        void plan为空返回400() throws Exception {
            setupSecurityContext(createTestUser());

            mockMvc.perform(post("/subscription/checkout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"billingCycle\":\"monthly\"}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        void billingCycle为空返回400() throws Exception {
            setupSecurityContext(createTestUser());

            mockMvc.perform(post("/subscription/checkout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"plan\":\"PRO\"}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        void 未认证用户抛出异常() throws Exception {
            mockMvc.perform(post("/subscription/checkout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"plan\":\"PRO\",\"billingCycle\":\"monthly\"}"))
                .andExpect(status().is5xxServerError());
        }
    }

    @Nested
    @DisplayName("获取订阅状态")
    class StatusTests {

        @Test
        void 获取订阅状态成功_无订阅() throws Exception {
            setupSecurityContext(createTestUser());
            SubscriptionStatusResponse response = new SubscriptionStatusResponse("FREE", "none", null, false);
            when(subscriptionService.getSubscriptionStatus(1L)).thenReturn(response);

            mockMvc.perform(get("/subscription/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.plan").value("FREE"))
                .andExpect(jsonPath("$.data.status").value("none"));
        }

        @Test
        void 获取订阅状态成功_有订阅() throws Exception {
            setupSecurityContext(createTestUser());
            SubscriptionStatusResponse response = new SubscriptionStatusResponse(
                "PRO", "active", LocalDateTime.of(2026, 5, 1, 0, 0), false);
            when(subscriptionService.getSubscriptionStatus(1L)).thenReturn(response);

            mockMvc.perform(get("/subscription/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.plan").value("PRO"))
                .andExpect(jsonPath("$.data.status").value("active"));
        }

        @Test
        void 未认证用户抛出异常() throws Exception {
            mockMvc.perform(get("/subscription/status"))
                .andExpect(status().is5xxServerError());
        }
    }

    @Nested
    @DisplayName("取消订阅")
    class CancelTests {

        @Test
        void 取消订阅成功() throws Exception {
            setupSecurityContext(createTestUser());
            SubscriptionStatusResponse response = new SubscriptionStatusResponse(
                "PRO", "active", LocalDateTime.of(2026, 5, 1, 0, 0), true);
            when(subscriptionService.cancelSubscription(1L)).thenReturn(response);

            mockMvc.perform(post("/subscription/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cancelAtPeriodEnd").value(true));
        }

        @Test
        void 无活跃订阅抛出异常() throws Exception {
            setupSecurityContext(createTestUser());
            when(subscriptionService.cancelSubscription(1L))
                .thenThrow(new RuntimeException("没有可取消的活跃订阅"));

            mockMvc.perform(post("/subscription/cancel"))
                .andExpect(status().is5xxServerError());
        }

        @Test
        void 未认证用户抛出异常() throws Exception {
            mockMvc.perform(post("/subscription/cancel"))
                .andExpect(status().is5xxServerError());
        }
    }

    @Nested
    @DisplayName("跳转账单管理")
    class PortalTests {

        @Test
        void 创建账单管理链接成功() throws Exception {
            setupSecurityContext(createTestUser());
            PortalSessionResponse response = new PortalSessionResponse("https://billing.stripe.com/portal/test");
            when(subscriptionService.createPortalSession(1L)).thenReturn(response);

            mockMvc.perform(post("/subscription/portal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.portalUrl").value("https://billing.stripe.com/portal/test"));
        }

        @Test
        void 无Stripe客户抛出异常() throws Exception {
            setupSecurityContext(createTestUser());
            when(subscriptionService.createPortalSession(1L))
                .thenThrow(new RuntimeException("未找到 Stripe 客户"));

            mockMvc.perform(post("/subscription/portal"))
                .andExpect(status().is5xxServerError());
        }

        @Test
        void 未认证用户抛出异常() throws Exception {
            mockMvc.perform(post("/subscription/portal"))
                .andExpect(status().is5xxServerError());
        }
    }
}
