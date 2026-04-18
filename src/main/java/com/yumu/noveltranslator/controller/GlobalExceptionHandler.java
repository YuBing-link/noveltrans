package com.yumu.noveltranslator.controller;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.yumu.noveltranslator.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

// 全局异常处理器
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 捕获参数校验异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Object> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败：{}", message);
        return Result.error(message, "400");
    }

    // 捕获 JWT 验证异常
    @ExceptionHandler(JWTVerificationException.class)
    public Result<Object> handleJwtException(JWTVerificationException e) {
        if (e instanceof TokenExpiredException) {
            return Result.error("401", "Token 已过期，请重新登录");
        } else if (e instanceof SignatureVerificationException) {
            return Result.error("401", "Token 签名错误，无效凭证");
        } else {
            return Result.error("401", "Token 验证失败：" + e.getMessage());
        }
    }

    // 捕获其他异常（比如系统异常）
    @ExceptionHandler(Exception.class)
    public Result<Object> handleException(Exception e) {
        // 记录日志
        log.error("系统异常", e);
        return Result.error("500", "服务器内部错误");
    }
}