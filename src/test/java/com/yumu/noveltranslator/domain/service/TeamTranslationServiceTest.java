package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.entity.Glossary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.springframework.web.reactive.function.client.WebClient;

class TeamTranslationServiceTest {

    private TeamTranslationService service;

    @BeforeEach
    void setUp() {
        service = new TeamTranslationService("localhost", 8000, "test-api-key");
    }

    @Nested
    @DisplayName("章节翻译")
    class TranslateChapterTests {

        @Test
        void 翻译成功() {
            mockWebClient(Mono.just("""
                {"code": 200, "data": "翻译后的文本", "costMs": 1500.0, "chunkCount": 3}
                """));

            List<Glossary> glossary = List.of(buildGlossary("hello", "你好"));
            String result = service.translateChapter("hello world", "fantasy", "en", "zh", glossary);

            assertEquals("翻译后的文本", result);
        }

        @Test
        void 翻译成功无术语表() {
            mockWebClient(Mono.just("""
                {"code": 200, "data": "translated", "costMs": 500.0, "chunkCount": 1}
                """));

            String result = service.translateChapter("hello", "fantasy", "en", "zh", null);
            assertEquals("translated", result);
        }

        @Test
        void 返回错误码抛出异常() {
            mockWebClient(Mono.just("""
                {"code": 500, "data": "error", "costMs": 100.0}
                """));

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.translateChapter("hello", "fantasy", "en", "zh", List.of()));
            assertTrue(ex.getMessage().contains("错误码"));
        }

        @Test
        void 连接失败抛出异常() {
            mockWebClient(Mono.error(new RuntimeException("Connection refused")));

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.translateChapter("hello", "fantasy", "en", "zh", List.of()));
            assertTrue(ex.getMessage().contains("连接"));
        }

        @Test
        void 超时抛出异常() {
            mockWebClient(Mono.error(new RuntimeException("Request timed out")));

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.translateChapter("hello", "fantasy", "en", "zh", List.of()));
            assertTrue(ex.getMessage().contains("超时"));
        }

        @Test
        void 解析失败返回降级响应() {
            mockWebClient(Mono.just("not json at all"));

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.translateChapter("hello", "fantasy", "en", "zh", List.of()));
            assertTrue(ex.getMessage().contains("错误码"));
        }
    }

    @Nested
    @DisplayName("带占位符的章节翻译")
    class TranslateChapterWithPlaceholdersTests {

        @Test
        void 翻译成功并还原占位符() {
            mockWebClient(Mono.just("""
                {"code": 200, "data": "[{1}] went to the market", "costMs": 2000.0, "chunkCount": 1}
                """));

            Map<String, String> placeholders = Map.of("[{1}]", "张三");
            String result = service.translateChapterWithPlaceholders(
                "hello [{1}]", "fantasy", "en", "zh", null, placeholders);

            assertEquals("张三 went to the market", result);
        }

        @Test
        void 无占位符正常翻译() {
            mockWebClient(Mono.just("""
                {"code": 200, "data": "翻译文本", "costMs": 1000.0, "chunkCount": 1}
                """));

            String result = service.translateChapterWithPlaceholders(
                "hello world", "fantasy", "en", "zh", null, null);
            assertEquals("翻译文本", result);
        }

        @Test
        void 空占位符映射正常翻译() {
            mockWebClient(Mono.just("""
                {"code": 200, "data": "翻译文本", "costMs": 1000.0, "chunkCount": 1}
                """));

            String result = service.translateChapterWithPlaceholders(
                "hello world", "fantasy", "en", "zh", null, Map.of());
            assertEquals("翻译文本", result);
        }

        @Test
        void 占位符按长度降序还原() {
            mockWebClient(Mono.just("""
                {"code": 200, "data": "[{1}] and [{10}] met", "costMs": 1000.0, "chunkCount": 1}
                """));

            Map<String, String> placeholders = Map.of(
                "[{1}]", "Alice",
                "[{10}]", "Bob"
            );
            String result = service.translateChapterWithPlaceholders(
                "text", "fantasy", "en", "zh", null, placeholders);

            assertEquals("Alice and Bob met", result);
        }
    }

    @SuppressWarnings("unchecked")
    private void mockWebClient(Mono<String> responseMono) {
        // Use Mockito's deep stubs with proper type casting via lenient
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

    private Glossary buildGlossary(String source, String target) {
        Glossary g = new Glossary();
        g.setSourceWord(source);
        g.setTargetWord(target);
        g.setRemark("test");
        return g;
    }
}
