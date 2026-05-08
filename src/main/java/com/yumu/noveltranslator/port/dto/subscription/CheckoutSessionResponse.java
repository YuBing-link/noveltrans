package com.yumu.noveltranslator.port.dto.subscription;

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

    /**
     * 为 true 时表示已直接升级订阅，无需前端跳转。
     * checkoutUrl 为 null 且 upgraded 为 true 时，前端应显示"升级成功"而非跳转链接。
     */
    private boolean upgraded;

    public CheckoutSessionResponse(String checkoutUrl) {
        this.checkoutUrl = checkoutUrl;
        this.upgraded = false;
    }
}
