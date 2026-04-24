package com.yumu.noveltranslator.config;

import com.stripe.Stripe;
import com.yumu.noveltranslator.properties.StripeProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

/**
 * Stripe SDK 初始化配置
 */
@Configuration
@RequiredArgsConstructor
public class StripeConfig {

    private final StripeProperties stripeProperties;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeProperties.getSecretKey();
    }
}
