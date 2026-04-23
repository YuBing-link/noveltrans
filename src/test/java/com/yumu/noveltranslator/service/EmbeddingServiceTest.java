package com.yumu.noveltranslator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.springframework.web.reactive.function.client.WebClient;

class EmbeddingServiceTest {

    private EmbeddingService service;

    @BeforeEach
    void setUp() {
        service = new EmbeddingService(WebClient.builder());
    }

    @Nested
    @DisplayName("文本向量化")
    class EmbedTests {

        @Test
        void 空文本返回空数组() {
            float[] result = service.embed(null);
            assertEquals(0, result.length);
        }

        @Test
        void 空白文本返回空数组() {
            float[] result = service.embed("   ");
            assertEquals(0, result.length);
        }

        @Test
        void 无APIKey返回空数组() {
            ReflectionTestUtils.setField(service, "openaiApiKey", null);
            float[] result = service.embed("hello");
            assertEquals(0, result.length);
        }

        @Test
        void 空白APIKey返回空数组() {
            ReflectionTestUtils.setField(service, "openaiApiKey", "   ");
            float[] result = service.embed("hello");
            assertEquals(0, result.length);
        }

        @Test
        void 调用失败返回空数组() {
            mockWebClient(Mono.error(new RuntimeException("API error")));
            float[] result = service.embed("hello world");
            assertEquals(0, result.length);
        }
    }

    @Nested
    @DisplayName("维度查询")
    class DimensionTests {

        @Test
        void openai返回1536() {
            ReflectionTestUtils.setField(service, "provider", "openai");
            assertEquals(1536, service.getDimension());
        }

        @Test
        void ollama返回1024() {
            ReflectionTestUtils.setField(service, "provider", "ollama");
            assertEquals(1024, service.getDimension());
        }
    }

    @Nested
    @DisplayName("Ollama模式")
    class OllamaTests {

        @Test
        void 连接失败返回空数组() {
            ReflectionTestUtils.setField(service, "provider", "ollama");
            ReflectionTestUtils.setField(service, "ollamaBaseUrl", "http://localhost:11434");
            ReflectionTestUtils.setField(service, "ollamaModel", "bge-m3");
            mockWebClient(Mono.error(new RuntimeException("connection refused")));
            float[] result = service.embed("hello");
            assertEquals(0, result.length);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockWebClient(Mono<Map> responseMono) {
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec respSpec = mock(WebClient.ResponseSpec.class);
        WebClient mockClient = mock(WebClient.class);

        lenient().doReturn(uriSpec).when(mockClient).post();
        lenient().doReturn(bodySpec).when(uriSpec).uri(anyString());
        lenient().doReturn(bodySpec).when(bodySpec).contentType(any());
        lenient().doReturn(bodySpec).when(bodySpec).header(anyString(), anyString());
        lenient().doReturn(bodySpec).when(bodySpec).bodyValue(any());
        lenient().doReturn(respSpec).when(bodySpec).retrieve();
        lenient().doReturn(responseMono).when(respSpec).bodyToMono(Map.class);

        ReflectionTestUtils.setField(service, "webClient", mockClient);
    }
}
