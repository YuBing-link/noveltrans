package com.yumu.noveltranslator.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Stripe 配置属性
 */
@Component
@ConfigurationProperties(prefix = "stripe")
@Getter
@Setter
public class StripeProperties {

    private String secretKey;
    private String webhookSecret;
    private String successUrl;
    private String cancelUrl;
    private Map<String, PlanPrices> prices;

    @Getter
    @Setter
    public static class PlanPrices {
        private String monthlyPriceId;
        private String yearlyPriceId;
    }
}
