package com.yumu.noveltranslator.dto;

import lombok.Data;
import java.util.List;

/**
 * 实体提取响应
 */
@Data
public class EntityExtractionResponse {
    private int code;
    private List<String> entities;
}
