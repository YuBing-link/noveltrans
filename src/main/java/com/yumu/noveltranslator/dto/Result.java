package com.yumu.noveltranslator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private boolean success;
    private T data;
    private String code;
    private String message;

    // 包含JWT Token的数据结构
    private String token;

    public static <T> Result<T> ok(T data) {
        return new Result<>(true, data, "00000", null, null);
    }

    public static <T> Result<T> ok(T data, String code) {
        return new Result<>(true, data, code, null, null);
    }

    public static <T> Result<T> okWithToken(T data, String token) {
        return new Result<>(true, data, "00000", null, token);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(false, null, "B0001", message, null);
    }

    public static <T> Result<T> error(String code, String message) {
        return new Result<>(false, null, code, message, null);
    }

    public static <T> Result<T> error(String message, int httpStatus) {
        return new Result<>(false, null, String.valueOf(httpStatus), message, null);
    }
}
