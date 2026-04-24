package com.yumu.noveltranslator.controller;

import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("GlobalExceptionHandler 测试")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("参数校验异常返回400和错误信息")
    void handleValidationException() {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
            new FieldError("obj", "email", "不能为空"),
            new FieldError("obj", "password", "长度不足")
        ));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        var result = handler.handleValidationException(ex);

        assertFalse(result.isSuccess());
        assertEquals("400", result.getCode());
        assertTrue(result.getMessage().contains("email"));
        assertTrue(result.getMessage().contains("password"));
    }

    @Test
    @DisplayName("TokenExpiredException返回401和过期消息")
    void handleTokenExpiredException() {
        TokenExpiredException ex = new TokenExpiredException("Token expired", Instant.now());

        var result = handler.handleJwtException(ex);

        assertFalse(result.isSuccess());
        assertEquals("401", result.getCode());
        assertTrue(result.getMessage().contains("过期"));
    }

    @Test
    @DisplayName("SignatureVerificationException返回401和签名错误消息")
    void handleSignatureException() {
        SignatureVerificationException ex =
            new SignatureVerificationException(com.auth0.jwt.algorithms.Algorithm.HMAC256("secret"));

        var result = handler.handleJwtException(ex);

        assertFalse(result.isSuccess());
        assertEquals("401", result.getCode());
        assertTrue(result.getMessage().contains("签名"));
    }

    @Test
    @DisplayName("其他JWT异常返回401和通用消息")
    void handleOtherJwtException() {
        com.auth0.jwt.exceptions.JWTVerificationException ex =
            mock(com.auth0.jwt.exceptions.JWTVerificationException.class);
        when(ex.getMessage()).thenReturn("Unknown error");

        var result = handler.handleJwtException(ex);

        assertFalse(result.isSuccess());
        assertEquals("401", result.getCode());
        assertTrue(result.getMessage().contains("失败"));
    }

    @Test
    @DisplayName("通用异常返回500")
    void handleGenericException() {
        Exception ex = new RuntimeException("Something went wrong");

        var result = handler.handleException(ex);

        assertFalse(result.isSuccess());
        assertEquals("500", result.getCode());
        assertEquals("服务器内部错误", result.getMessage());
    }
}
