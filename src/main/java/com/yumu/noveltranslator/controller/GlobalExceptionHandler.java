package com.yumu.noveltranslator.controller;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.yumu.noveltranslator.dto.Result;
import com.yumu.noveltranslator.enums.ErrorCodeEnum;
import com.yumu.noveltranslator.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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
        return Result.error(ErrorCodeEnum.PARAMETER_ERROR, message);
    }

    // 捕获 JWT 验证异常
    @ExceptionHandler(JWTVerificationException.class)
    public Result<Object> handleJwtException(JWTVerificationException e) {
        if (e instanceof TokenExpiredException) {
            return Result.error(ErrorCodeEnum.TOKEN_EXPIRED);
        } else if (e instanceof SignatureVerificationException) {
            return Result.error(ErrorCodeEnum.TOKEN_SIGNATURE_ERROR);
        } else {
            return Result.error(ErrorCodeEnum.TOKEN_INVALID);
        }
    }

    // 捕获 BusinessException
    @ExceptionHandler(BusinessException.class)
    public Result<Object> handleBusinessException(BusinessException e) {
        log.debug("业务异常: {}", e.getMessage());
        return Result.error(e.getErrorCode(), e.getMessage());
    }

    // 捕获其他异常（比如系统异常）
    @ExceptionHandler(Exception.class)
    public Result<Object> handleException(Exception e) {
        if (e instanceof IllegalArgumentException || e instanceof IllegalStateException) {
            log.debug("客户端参数错误: {}", e.getMessage());
            return Result.error(ErrorCodeEnum.PARAMETER_ERROR, e.getMessage());
        }
        log.error("服务器内部错误", e);
        return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
    }

    // 不支持的请求方法
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Object> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        log.debug("不支持的请求方法: {}", e.getMessage());
        return Result.error(ErrorCodeEnum.METHOD_NOT_ALLOWED, e.getMessage());
    }

    // 文件上传大小超限
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Object> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.debug("文件上传大小超过限制");
        return Result.error(ErrorCodeEnum.PAYLOAD_TOO_LARGE);
    }

    // 请求体不可读（JSON 解析失败等）
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.debug("请求体格式错误: {}", e.getMessage());
        return Result.error(ErrorCodeEnum.PARAMETER_ERROR, "请求格式不正确");
    }
}