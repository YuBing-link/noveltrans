package com.yumu.noveltranslator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.springframework.web.reactive.function.client.WebClient;

/**
 * EmbeddingService 单元测试
 * 使用 Mockito 模拟 WebClient 及其链式调用
 */
class EmbeddingServiceTest {

    private EmbeddingService service;

    @BeforeEach
    void setUp() {
        service = new EmbeddingService(WebClient.builder());
    }

    @Nested
    @DisplayName("toFloatArray 转换")
    class ToFloatArrayTests {

        @Test
        @DisplayName("将 List<Double> 转换为 float 数组")
        void toFloatArray_convertsListToFloatArray() throws Exception {
            // 通过反射调用私有方法 toFloatArray
            Method method = EmbeddingService.class.getDeclaredMethod("toFloatArray", List.class);
            method.setAccessible(true);

            List<Double> input = Arrays.asList(0.1, 0.2, 0.3);
            float[] result = (float[]) method.invoke(service, input);

            assertArrayEquals(new float[]{0.1f, 0.2f, 0.3f}, result, 0.0001f);
        }
    }

    @Nested
    @DisplayName("维度查询")
    class DimensionTests {

        @Test
        @DisplayName("openai 提供商返回 1536 维")
        void getDimension_openai_returns1536() {
            ReflectionTestUtils.setField(service, "provider", "openai");
            assertEquals(1536, service.getDimension());
        }

        @Test
        @DisplayName("ollama 提供商返回 1024 维")
        void getDimension_ollama_returns1024() {
            ReflectionTestUtils.setField(service, "provider", "ollama");
            assertEquals(1024, service.getDimension());
        }

        @Test
        @DisplayName("默认提供商返回 1536 维")
        void getDimension_default_returns1536() {
            ReflectionTestUtils.setField(service, "provider", null);
            assertEquals(1536, service.getDimension());
        }
    }

    @Nested
    @DisplayName("空文本处理")
    class NullBlankTests {

        @Test
        @DisplayName("null 文本返回空数组")
        void embed_null_returnsEmptyArray() {
            float[] result = service.embed(null);
            assertArrayEquals(new float[0], result);
        }

        @Test
        @DisplayName("空白文本返回空数组")
        void embed_blank_returnsEmptyArray() {
            float[] result = service.embed("   ");
            assertArrayEquals(new float[0], result);
        }
    }

    @Nested
    @DisplayName("OpenAI 向量化")
    class OpenAIEmbedTests {

        @Test
        @DisplayName("成功调用 OpenAI API 并解析结果")
        @SuppressWarnings({"unchecked", "rawtypes"})
        void embedWithOpenAI_success() {
            ReflectionTestUtils.setField(service, "provider", "openai");
            ReflectionTestUtils.setField(service, "openaiApiKey", "sk-test-key");
            ReflectionTestUtils.setField(service, "openaiBaseUrl", "https://api.openai.com");
            ReflectionTestUtils.setField(service, "openaiModel", "text-embedding-3-small");

            // 构造模拟响应
            Map<String, Object> responseData = Map.of(
                    "embedding", (Object) Arrays.asList(0.1, 0.2, 0.3, -0.5)
            );
            Map<String, Object> mockResponse = Map.of("data", List.of(responseData));

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
            lenient().doReturn(Mono.just(mockResponse)).when(respSpec).bodyToMono(Map.class);

            ReflectionTestUtils.setField(service, "webClient", mockClient);

            float[] result = service.embed("Hello world");

            assertEquals(4, result.length);
            assertEquals(0.1f, result[0], 0.0001f);
            assertEquals(0.2f, result[1], 0.0001f);
            assertEquals(0.3f, result[2], 0.0001f);
            assertEquals(-0.5f, result[3], 0.0001f);
        }

        @Test
        @DisplayName("API Key 未配置时返回空数组")
        void embedWithOpenAI_noApiKey_returnsEmptyArray() {
            ReflectionTestUtils.setField(service, "provider", "openai");
            ReflectionTestUtils.setField(service, "openaiApiKey", "");

            float[] result = service.embed("test");

            assertArrayEquals(new float[0], result);
        }
    }

    @Nested
    @DisplayName("Ollama 向量化")
    class OllamaEmbedTests {

        @Test
        @DisplayName("成功调用 Ollama API 并解析结果")
        @SuppressWarnings({"unchecked", "rawtypes"})
        void embedWithOllama_success() {
            ReflectionTestUtils.setField(service, "provider", "ollama");
            ReflectionTestUtils.setField(service, "ollamaBaseUrl", "http://localhost:11434");
            ReflectionTestUtils.setField(service, "ollamaModel", "bge-m3");

            // 构造模拟响应
            Map<String, Object> mockResponse = Map.of(
                    "embedding", (Object) Arrays.asList(1.0, 2.0, -3.0, 4.5)
            );

            WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
            WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
            WebClient.ResponseSpec respSpec = mock(WebClient.ResponseSpec.class);
            WebClient mockClient = mock(WebClient.class);

            lenient().doReturn(uriSpec).when(mockClient).post();
            lenient().doReturn(bodySpec).when(uriSpec).uri(anyString());
            lenient().doReturn(bodySpec).when(bodySpec).contentType(any());
            lenient().doReturn(bodySpec).when(bodySpec).bodyValue(any());
            lenient().doReturn(respSpec).when(bodySpec).retrieve();
            lenient().doReturn(Mono.just(mockResponse)).when(respSpec).bodyToMono(Map.class);

            ReflectionTestUtils.setField(service, "webClient", mockClient);

            float[] result = service.embed("测试文本");

            assertEquals(4, result.length);
            assertEquals(1.0f, result[0], 0.0001f);
            assertEquals(2.0f, result[1], 0.0001f);
            assertEquals(-3.0f, result[2], 0.0001f);
            assertEquals(4.5f, result[3], 0.0001f);
        }

        @Test
        @DisplayName("Ollama 响应为空时返回空数组")
        @SuppressWarnings({"unchecked", "rawtypes"})
        void embedWithOllama_nullResponse_returnsEmptyArray() {
            ReflectionTestUtils.setField(service, "provider", "ollama");
            ReflectionTestUtils.setField(service, "ollamaBaseUrl", "http://localhost:11434");
            ReflectionTestUtils.setField(service, "ollamaModel", "bge-m3");

            WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
            WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
            WebClient.ResponseSpec respSpec = mock(WebClient.ResponseSpec.class);
            WebClient mockClient = mock(WebClient.class);

            lenient().doReturn(uriSpec).when(mockClient).post();
            lenient().doReturn(bodySpec).when(uriSpec).uri(anyString());
            lenient().doReturn(bodySpec).when(bodySpec).contentType(any());
            lenient().doReturn(bodySpec).when(bodySpec).bodyValue(any());
            lenient().doReturn(respSpec).when(bodySpec).retrieve();
            lenient().doReturn(Mono.empty()).when(respSpec).bodyToMono(Map.class);

            ReflectionTestUtils.setField(service, "webClient", mockClient);

            float[] result = service.embed("test");

            assertArrayEquals(new float[0], result);
        }
    }

    @Nested
    @DisplayName("异常处理")
    class ExceptionTests {

        @Test
        @DisplayName("WebClient 异常时返回空数组")
        @SuppressWarnings({"unchecked", "rawtypes"})
        void embed_exception_returnsEmptyArray() {
            ReflectionTestUtils.setField(service, "provider", "openai");
            ReflectionTestUtils.setField(service, "openaiApiKey", "sk-test-key");
            ReflectionTestUtils.setField(service, "openaiBaseUrl", "https://api.openai.com");
            ReflectionTestUtils.setField(service, "openaiModel", "text-embedding-3-small");

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
            lenient().doReturn(Mono.error(new RuntimeException("连接超时"))).when(respSpec).bodyToMono(Map.class);

            ReflectionTestUtils.setField(service, "webClient", mockClient);

            float[] result = service.embed("hello");

            assertArrayEquals(new float[0], result);
        }
    }
}
