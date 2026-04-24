package com.yumu.noveltranslator.controller;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.util.SecurityUtil;
import com.yumu.noveltranslator.service.SubscriptionService;
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

    private final SubscriptionService subscriptionService;

    /**
     * 验证支付结果（前端回调时主动查询 Stripe session 状态）
     */
    @GetMapping("/verify")
    public Result<PaymentVerificationResponse> verify(@RequestParam String session_id) {
        Long userId = SecurityUtil.getRequiredUserId();
        return Result.ok(subscriptionService.verifyCheckoutSession(session_id, userId));
    }

    /**
     * 创建支付会话
     */
    @PostMapping("/checkout")
    public Result<CheckoutSessionResponse> checkout(@RequestBody @Valid CheckoutSessionRequest request) {
        Long userId = SecurityUtil.getRequiredUserId();
        return Result.ok(subscriptionService.createCheckoutSession(userId, request));
    }

    /**
     * 获取订阅状态
     */
    @GetMapping("/status")
    public Result<SubscriptionStatusResponse> status() {
        Long userId = SecurityUtil.getRequiredUserId();
        return Result.ok(subscriptionService.getSubscriptionStatus(userId));
    }

    /**
     * 取消订阅
     */
    @PostMapping("/cancel")
    public Result<SubscriptionStatusResponse> cancel() {
        Long userId = SecurityUtil.getRequiredUserId();
        return Result.ok(subscriptionService.cancelSubscription(userId));
    }

    /**
     * 跳转账单管理
     */
    @PostMapping("/portal")
    public Result<PortalSessionResponse> portal() {
        Long userId = SecurityUtil.getRequiredUserId();
        return Result.ok(subscriptionService.createPortalSession(userId));
    }
}
