package com.yumu.noveltranslator.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateApiKeyRequest {
    @NotBlank(message = "API Key 名称不能为空")
    private String name;
}
