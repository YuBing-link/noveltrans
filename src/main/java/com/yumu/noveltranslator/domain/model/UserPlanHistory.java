package com.yumu.noveltranslator.domain.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserPlanHistory {
    private Long id;
    private Long userId;
    private String oldPlan;
    private String newPlan;
    private Long tenantId;
    private LocalDateTime changedAt;
    private String note;
}
