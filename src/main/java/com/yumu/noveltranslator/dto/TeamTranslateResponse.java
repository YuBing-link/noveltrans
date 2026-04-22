package com.yumu.noveltranslator.dto;

import lombok.Data;

/**
 * AI 翻译团队响应 DTO
 */
@Data
public class TeamTranslateResponse {

    /**
     * 状态码（0 表示成功）
     */
    private int code;

    /**
     * 翻译后的文本
     */
    private String data;

    /**
     * 耗时（毫秒）
     */
    private double costMs;

    /**
     * 小说类型
     */
    private String novelType;

    /**
     * 分块数量
     */
    private int chunkCount;
}
