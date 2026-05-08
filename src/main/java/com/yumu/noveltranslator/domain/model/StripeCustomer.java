package com.yumu.noveltranslator.domain.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class StripeCustomer {
    private Long id;
    private Long userId;
    private String stripeCustomerId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
