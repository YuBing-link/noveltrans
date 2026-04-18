package com.yumu.noveltranslator.dto;

import lombok.Data;

/**
 * 分页请求
 */
@Data
public class PageRequest {
    /**
     * 页码，默认 1
     */
    private Integer page = 1;
    /**
     * 每页数量，默认 20
     */
    private Integer pageSize = 20;
}
