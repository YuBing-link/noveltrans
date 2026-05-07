package com.yumu.noveltranslator.adapter.in.rest.web;

import com.yumu.noveltranslator.dto.common.Result;
import com.yumu.noveltranslator.dto.subscription.PaymentVerificationResponse;
import com.yumu.noveltranslator.dto.subscription.CheckoutSessionResponse;
import com.yumu.noveltranslator.dto.subscription.CheckoutSessionRequest;
import com.yumu.noveltranslator.dto.subscription.SubscriptionStatusResponse;
import com.yumu.noveltranslator.dto.subscription.PortalSessionResponse;
import com.yumu.noveltranslator.util.SecurityUtil;
import com.yumu.noveltranslator.port.in.SubscriptionPort;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户端订阅 API
 */
@RestController
@RequestMapping("/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionPort subscriptionPort;

    /**
     * 验证支付结果（前端回调时主动查询 Stripe session 状态）
     */
    @GetMapping("/verify")
    public Result<PaymentVerificationResponse> verify(@RequestParam String session_id) {
        Long userId = SecurityUtil.getRequiredUserId();
        return Result.ok(subscriptionPort.verifyCheckoutSession(session_id, userId));
    }

    /**
     * 创建支付会话
     */
    @PostMapping("/checkout")
    public Result<CheckoutSessionResponse> checkout(@RequestBody @Valid CheckoutSessionRequest request) {
        Long userId = SecurityUtil.getRequiredUserId();
        return Result.ok(subscriptionPort.createCheckoutSession(userId, request));
    }

    /**
     * 获取订阅状态
     */
    @GetMapping("/status")
    public Result<SubscriptionStatusResponse> status() {
        Long userId = SecurityUtil.getRequiredUserId();
        return Result.ok(subscriptionPort.getSubscriptionStatus(userId));
    }

    /**
     * 取消订阅
     */
    @PostMapping("/cancel")
    public Result<SubscriptionStatusResponse> cancel() {
        Long userId = SecurityUtil.getRequiredUserId();
        return Result.ok(subscriptionPort.cancelSubscription(userId));
    }

    /**
     * 跳转账单管理
     */
    @PostMapping("/portal")
    public Result<PortalSessionResponse> portal() {
        Long userId = SecurityUtil.getRequiredUserId();
        return Result.ok(subscriptionPort.createPortalSession(userId));
    }
}
