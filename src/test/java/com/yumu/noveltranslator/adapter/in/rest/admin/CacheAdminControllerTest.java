package com.yumu.noveltranslator.adapter.in.rest.admin;

import com.yumu.noveltranslator.port.dto.common.Result;
import com.yumu.noveltranslator.port.in.CacheAdminPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CacheAdminController 测试")
class CacheAdminControllerTest {

    private MockMvc mockMvc;

    @org.mockito.Mock
    private CacheAdminPort cacheAdminPort;

    private CacheAdminController controller;

    /**
     * 测试用异常处理器，将未捕获异常转为 500 响应
     */
    @RestControllerAdvice
    static class TestExceptionHandler {
        @ExceptionHandler(Exception.class)
        @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        public Result<Void> handleException(Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @BeforeEach
    void setUp() {
        controller = new CacheAdminController(cacheAdminPort);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new TestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("清空缓存成功")
    void clearAllCache_success() throws Exception {
        mockMvc.perform(post("/admin/cache/clear"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").value("缓存已清空"));

        verify(cacheAdminPort).clearAllTranslationCache();
    }

    @Test
    @DisplayName("清空缓存时抛出异常返回500")
    void clearAllCache_throwsException() throws Exception {
        doThrow(new RuntimeException("Redis connection failed")).when(cacheAdminPort).clearAllTranslationCache();

        mockMvc.perform(post("/admin/cache/clear"))
            .andExpect(status().is5xxServerError());

        verify(cacheAdminPort).clearAllTranslationCache();
    }
}
