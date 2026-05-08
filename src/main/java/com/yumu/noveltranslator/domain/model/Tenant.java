package com.yumu.noveltranslator.domain.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Tenant {
    private Long id;
    private String name;
    private String status;
    private Integer maxUsers;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
