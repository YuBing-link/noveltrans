package com.yumu.noveltranslator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建 Checkout Session 请求
 */
@Data
public class CheckoutSessionRequest {

    @NotBlank(message = "套餐类型不能为空")
    private String plan;

    @NotNull(message = "计费周期不能为空")
    private String billingCycle;
}
