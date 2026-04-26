package com.yumu.noveltranslator.service;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("ExternalTranslationService 测试")
class ExternalTranslationServiceTest {

    @Nested
    @DisplayName("translate 测试")
    class TranslateTests {

        @Test
        void 翻译成功返回结果() {
            ExternalTranslationService service = new ExternalTranslationService("localhost", 8000, null);
            mockWebClient(service, Mono.just("{\"translatedText\":\"你好\",\"from\":\"en\",\"to\":\"zh\"}"));

            JSONObject result = service.translate("en", "zh", "Hello", false);

            assertEquals("你好", result.getString("translatedText"));
            assertEquals("en", result.getString("from"));
            assertEquals("zh", result.getString("to"));
        }

        @Test
        void html模式翻译() {
            ExternalTranslationService service = new ExternalTranslationService("localhost", 8000, null);
            mockWebClient(service, Mono.just("{\"translatedText\":\"<b>你好</b>\"}"));

            JSONObject result = service.translate("en", "zh", "<b>Hello</b>", true);

            assertEquals("<b>你好</b>", result.getString("translatedText"));
        }

        @Test
        void http错误码抛出异常() {
            ExternalTranslationService service = new ExternalTranslationService("localhost", 8000, null);
            WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
            WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
            WebClient.ResponseSpec respSpec = mock(WebClient.ResponseSpec.class);
            WebClient mockClient = mock(WebClient.class);

            WebClientResponseException httpError = new WebClientResponseException(
                500, "Internal Server Error", null, null, null);

            lenient().doReturn(uriSpec).when(mockClient).post();
            lenient().doReturn(bodySpec).when(uriSpec).uri(anyString());
            lenient().doReturn(bodySpec).when(bodySpec).bodyValue(any());
            lenient().doReturn(respSpec).when(bodySpec).retrieve();
            lenient().doReturn(Mono.error(httpError)).when(respSpec).bodyToMono(String.class);

            ReflectionTestUtils.setField(service, "webClient", mockClient);

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.translate("en", "zh", "Hello", false));
            assertTrue(ex.getMessage().contains("HTTP"));
        }

        @Test
        void 超时抛出异常() {
            ExternalTranslationService service = new ExternalTranslationService("localhost", 8000, null);
            WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
            WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
            WebClient.ResponseSpec respSpec = mock(WebClient.ResponseSpec.class);
            WebClient mockClient = mock(WebClient.class);

            RuntimeException timeoutEx = new RuntimeException("Request timed out");

            lenient().doReturn(uriSpec).when(mockClient).post();
            lenient().doReturn(bodySpec).when(uriSpec).uri(anyString());
            lenient().doReturn(bodySpec).when(bodySpec).bodyValue(any());
            lenient().doReturn(respSpec).when(bodySpec).retrieve();
            lenient().doReturn(Mono.error(timeoutEx)).when(respSpec).bodyToMono(String.class);

            ReflectionTestUtils.setField(service, "webClient", mockClient);

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.translate("en", "zh", "Hello", false));
            assertTrue(ex.getMessage().contains("超时"));
        }

        @Test
        void 连接失败抛出异常() {
            ExternalTranslationService service = new ExternalTranslationService("localhost", 8000, null);
            WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
            WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
            WebClient.ResponseSpec respSpec = mock(WebClient.ResponseSpec.class);
            WebClient mockClient = mock(WebClient.class);

            RuntimeException connEx = new RuntimeException("Connection refused: connect");

            lenient().doReturn(uriSpec).when(mockClient).post();
            lenient().doReturn(bodySpec).when(uriSpec).uri(anyString());
            lenient().doReturn(bodySpec).when(bodySpec).bodyValue(any());
            lenient().doReturn(respSpec).when(bodySpec).retrieve();
            lenient().doReturn(Mono.error(connEx)).when(respSpec).bodyToMono(String.class);

            ReflectionTestUtils.setField(service, "webClient", mockClient);

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.translate("en", "zh", "Hello", false));
            assertTrue(ex.getMessage().contains("连接") || ex.getMessage().contains("启动"));
        }

        @Test
        void 连接超时抛出异常() {
            ExternalTranslationService service = new ExternalTranslationService("localhost", 8000, null);
            WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
            WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
            WebClient.ResponseSpec respSpec = mock(WebClient.ResponseSpec.class);
            WebClient mockClient = mock(WebClient.class);

            // "connect timed out" contains "timed out" so it matches the timeout branch first
            RuntimeException connEx = new RuntimeException("connect timed out");

            lenient().doReturn(uriSpec).when(mockClient).post();
            lenient().doReturn(bodySpec).when(uriSpec).uri(anyString());
            lenient().doReturn(bodySpec).when(bodySpec).bodyValue(any());
            lenient().doReturn(respSpec).when(bodySpec).retrieve();
            lenient().doReturn(Mono.error(connEx)).when(respSpec).bodyToMono(String.class);

            ReflectionTestUtils.setField(service, "webClient", mockClient);

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.translate("en", "zh", "Hello", false));
            assertTrue(ex.getMessage().contains("超时"));
        }

        @Test
        void 一般异常抛出异常() {
            ExternalTranslationService service = new ExternalTranslationService("localhost", 8000, null);
            WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
            WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
            WebClient.ResponseSpec respSpec = mock(WebClient.ResponseSpec.class);
            WebClient mockClient = mock(WebClient.class);

            RuntimeException genericEx = new RuntimeException("Something went wrong");

            lenient().doReturn(uriSpec).when(mockClient).post();
            lenient().doReturn(bodySpec).when(uriSpec).uri(anyString());
            lenient().doReturn(bodySpec).when(bodySpec).bodyValue(any());
            lenient().doReturn(respSpec).when(bodySpec).retrieve();
            lenient().doReturn(Mono.error(genericEx)).when(respSpec).bodyToMono(String.class);

            ReflectionTestUtils.setField(service, "webClient", mockClient);

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.translate("en", "zh", "Hello", false));
            assertTrue(ex.getMessage().contains("Something went wrong"));
        }

        @Test
        void 空错误消息使用默认消息() {
            ExternalTranslationService service = new ExternalTranslationService("localhost", 8000, null);
            WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
            WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
            WebClient.ResponseSpec respSpec = mock(WebClient.ResponseSpec.class);
            WebClient mockClient = mock(WebClient.class);

            RuntimeException nullMsgEx = new RuntimeException((String) null);

            lenient().doReturn(uriSpec).when(mockClient).post();
            lenient().doReturn(bodySpec).when(uriSpec).uri(anyString());
            lenient().doReturn(bodySpec).when(bodySpec).bodyValue(any());
            lenient().doReturn(respSpec).when(bodySpec).retrieve();
            lenient().doReturn(Mono.error(nullMsgEx)).when(respSpec).bodyToMono(String.class);

            ReflectionTestUtils.setField(service, "webClient", mockClient);

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.translate("en", "zh", "Hello", false));
            // null message falls through to generic handler: "外部翻译引擎翻译失败：" + errorMsg
            // where errorMsg is set to "未知错误" by the null check
            assertTrue(ex.getMessage().contains("未知错误") || ex.getMessage().contains("翻译失败"));
        }
    }

    @SuppressWarnings("unchecked")
    private void mockWebClient(ExternalTranslationService service, Mono<String> responseMono) {
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
}
