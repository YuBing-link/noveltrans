package com.yumu.noveltranslator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 术语项创建/更新请求
 */
@Data
public class GlossaryItemRequest {
    /**
     * 待替换的原词
     */
    @NotBlank(message = "原词不能为空")
    @Size(max = 100, message = "原词长度不能超过 100 个字符")
    private String sourceWord;

    /**
     * 替换后的译词
     */
    @NotBlank(message = "译词不能为空")
    @Size(max = 100, message = "译词长度不能超过 100 个字符")
    private String targetWord;

    /**
     * 备注信息
     */
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;
}
