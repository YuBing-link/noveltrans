package com.yumu.noveltranslator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付结果验证响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentVerificationResponse {

    private boolean paid;
    private String sessionId;
    private String plan;
    private String status;
    private String message;
}
