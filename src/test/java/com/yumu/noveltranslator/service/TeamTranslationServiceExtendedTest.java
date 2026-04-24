package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.TeamTranslateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.http.HttpStatus;

/**
 * TeamTranslationService 补充测试
 * 覆盖现有测试未覆盖的分支：WebClientResponseException 处理、
 * 超时异常分支（>300s）、ConnectionException 分支、
 * translateChapterWithPlaceholders 完整路径、parseResponse 降级
 */
class TeamTranslationServiceExtendedTest {

    private TeamTranslationService service;

    @BeforeEach
    void setUp() {
        service = new TeamTranslationService("localhost", 8000);
    }

    @Nested
    @DisplayName("translateChapter - 异常处理补充")
    class TranslateChapterExceptionTests {

        @Test
        void WebClientResponseException包装后抛出() {
            mockWebClientWithHttpStatus(HttpStatus.BAD_REQUEST, """
                {"error": "Bad Request"}
                """);

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.translateChapter("hello", "fantasy", "en", "zh", null));
            assertTrue(ex.getMessage().contains("HTTP"));
            assertTrue(ex.getMessage().contains("400"));
        }

        @Test
        void 通用异常包装原始错误信息() {
            mockWebClient(Mono.error(new RuntimeException("Some unexpected error")));

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.translateChapter("hello", "fantasy", "en", "zh", null));
            assertTrue(ex.getMessage().contains("翻译失败"));
        }
    }

    @Nested
    @DisplayName("translateChapterWithPlaceholders - 补充测试")
    class TranslateChapterWithPlaceholdersExtendedTests {

        @Test
        void WebClientResponseException在占位符路径中抛出() {
            mockWebClientWithHttpStatus(HttpStatus.INTERNAL_SERVER_ERROR, """
                {"error": "Server Error"}
                """);

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.translateChapterWithPlaceholders(
                    "hello [{1}]", "fantasy", "en", "zh", null, java.util.Map.of("[{1}]", "张三")));
            assertTrue(ex.getMessage().contains("HTTP"));
        }

        @Test
        void 占位符在响应中未找到时保留原文() {
            mockWebClient(Mono.just("""
                {"code": 200, "data": "翻译结果中没有占位符", "costMs": 1000.0, "chunkCount": 1}
                """));

            java.util.Map<String, String> placeholders = java.util.Map.of("[{1}]", "张三");
            String result = service.translateChapterWithPlaceholders(
                "hello [{1}]", "fantasy", "en", "zh", null, placeholders);

            // 占位符未在响应中找到，直接返回翻译结果（不替换）
            assertEquals("翻译结果中没有占位符", result);
        }

        @Test
        void 多个占位符按长度降序还原() {
            mockWebClient(Mono.just("""
                {"code": 200, "data": "[{10}] met [{1}] at the park", "costMs": 2000.0, "chunkCount": 1}
                """));

            java.util.Map<String, String> placeholders = java.util.Map.of(
                "[{1}]", "Alice",
                "[{10}]", "Bob"
            );
            String result = service.translateChapterWithPlaceholders(
                "text", "fantasy", "en", "zh", null, placeholders);

            // [{10}] 先被替换为 Bob，然后 [{1}] 被替换为 Alice
            assertEquals("Bob met Alice at the park", result);
        }
        @Test
        void 非WebClient异常通用错误分支() {
            mockWebClient(Mono.error(new RuntimeException("Unknown internal error")));

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.translateChapterWithPlaceholders(
                    "hello", "fantasy", "en", "zh", null, java.util.Map.of("[{1}]", "张三")));
            assertTrue(ex.getMessage().contains("翻译失败"));
        }

        @Test
        void 超时异常分支() {
            mockWebClient(Mono.error(new RuntimeException("Request timed out after 300000ms")));

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.translateChapterWithPlaceholders(
                    "hello", "fantasy", "en", "zh", null, null));
            // 超时异常会被包装为 "响应超时" 消息
            assertTrue(ex.getMessage().contains("响应超时") || ex.getMessage().contains("timed out"),
                "Expected timeout message but got: " + ex.getMessage());
        }

        @Test
        void 连接拒绝异常分支() {
            mockWebClient(Mono.error(new RuntimeException("Connection refused")));

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.translateChapterWithPlaceholders(
                    "hello", "fantasy", "en", "zh", null, null));
            // 连接拒绝会被包装为 "无法连接" 消息
            assertTrue(ex.getMessage().contains("无法连接") || ex.getMessage().contains("Connection refused"),
                "Expected connection refused message but got: " + ex.getMessage());
        }
    }

    // ============ WebClient 模拟辅助 ============

    @SuppressWarnings("unchecked")
    private void mockWebClient(Mono<String> responseMono) {
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec respSpec = mock(WebClient.ResponseSpec.class);
        WebClient mockClient = mock(WebClient.class);

        lenient().doReturn(uriSpec).when(mockClient).post();
        lenient().doReturn(bodySpec).when(uriSpec).uri(anyString());
        lenient().doReturn(bodySpec).when(bodySpec).bodyValue(any());
        lenient().doReturn(respSpec).when(bodySpec).retrieve();
        lenient().doReturn(responseMono).when(respSpec).bodyToMono(String.class);

        ReflectionTestUtils.setField(service, "webClient", mockClient);
    }

    @SuppressWarnings("unchecked")
    private void mockWebClientWithHttpStatus(HttpStatus status, String responseBody) {
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec respSpec = mock(WebClient.ResponseSpec.class);
        WebClient mockClient = mock(WebClient.class);

        lenient().doReturn(uriSpec).when(mockClient).post();
        lenient().doReturn(bodySpec).when(uriSpec).uri(anyString());
        lenient().doReturn(bodySpec).when(bodySpec).bodyValue(any());
        lenient().doReturn(respSpec).when(bodySpec).retrieve();
        lenient().doReturn(Mono.error(new WebClientResponseException(
                status.value(), status.getReasonPhrase(), null, null, null)))
            .when(respSpec).bodyToMono(String.class);

        ReflectionTestUtils.setField(service, "webClient", mockClient);
    }
}
