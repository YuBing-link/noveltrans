package com.yumu.noveltranslator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 订阅状态响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionStatusResponse {

    private String plan;
    private String status;
    private LocalDateTime periodEnd;
    private Boolean cancelAtPeriodEnd;
}
