package com.yumu.noveltranslator.dto.subscription;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Checkout Session 响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutSessionResponse {

    private String checkoutUrl;
}
