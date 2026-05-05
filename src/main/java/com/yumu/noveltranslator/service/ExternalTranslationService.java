package com.yumu.noveltranslator.service;

import com.alibaba.fastjson2.JSONObject;
import com.yumu.noveltranslator.exception.MTranServerUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

/**
 * 外部翻译服务调用类
 * 作为调用外部翻译引擎接口的中转站
 */
@Service
@Slf4j
public class ExternalTranslationService {

    private final WebClient webClient;
    private final boolean mockMode;

    public ExternalTranslationService(
            @Value("${mtran.server.host:localhost}") String host,
            @Value("${mtran.server.port:8989}") int port,
            @Value("${mtran.server.api-key:}") @Nullable String apiKey,
            @Value("${mtran.server.mock:false}") boolean mockMode) {
        this.mockMode = mockMode;
        var builder = WebClient.builder()
                .baseUrl("http://%s:%d".formatted(host, port))
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(256 * 1024));

        // 如果配置了 API Key，添加到请求头
        if (apiKey != null && !apiKey.isEmpty()) {
            builder.defaultHeader("Authorization", "Bearer " + apiKey);
            log.info("MTranServer 认证已启用");
        }

        this.webClient = builder.build();
        log.info("MTranServer 模式: {}", mockMode ? "MOCK（模拟响应）" : "REAL（直连服务）");
    }

    /**
     * 调用外部翻译引擎进行翻译
     *
     * @param from 源语言
     * @param to   目标语言
     * @param text 待翻译文本
     * @return 翻译结果
     * @throws RuntimeException 翻译失败时抛出异常
     */
    @CircuitBreaker(name = "mtranServer", fallbackMethod = "mtranServerFallback")
    public JSONObject translate(String from, String to, String text, boolean html) {
        // Mock 模式：直接返回模拟响应，不发 HTTP 请求
        if (mockMode) {
            return buildMockResponse(from, to, text, html);
        }

        JSONObject requestBody = new JSONObject();
        requestBody.put("from", from);
        requestBody.put("to", to);
        requestBody.put("text", text);
        requestBody.put("html", html);

        try {
            // MTranServer 偶尔响应慢，设置 15 秒超时后降级到 Python 服务
            String responseBody = webClient.post()
                    .uri("/translate")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(15));

            return JSONObject.parseObject(responseBody);
        } catch (WebClientResponseException e) {
            log.error("外部翻译引擎调用失败，状态码：{}，响应体：{}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("外部翻译引擎调用失败 (HTTP " + e.getStatusCode() + "): " + e.getMessage(), e);
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
            // 检查是否是超时错误
            if (errorMsg.contains("Timeout") || errorMsg.contains("timeout") || errorMsg.contains("timed out")) {
                log.error("外部翻译引擎响应超时（>15 秒），降级到 Python 服务。文本长度：{}", text != null ? text.length() : 0, e);
                throw new RuntimeException("外部翻译引擎响应超时，请检查服务性能：" + e.getMessage(), e);
            }
            // 检查是否是连接失败
            if (e.getCause() instanceof java.net.ConnectException || errorMsg.contains("Connection refused") || errorMsg.contains("connect timed out")) {
                log.error("外部翻译引擎连接失败，请确认服务已启动在 http://localhost:{}，错误：{}",
                         System.getProperty("mtran.server.port", "8989"), e.getMessage(), e);
                throw new RuntimeException("无法连接到外部翻译引擎，请确认服务已启动：" + e.getMessage(), e);
            }
            log.error("外部翻译引擎翻译异常，待翻译文本长度：{}", text != null ? text.length() : 0, e);
            throw new RuntimeException("外部翻译引擎翻译失败：" + e.getMessage(), e);
        }
    }

    /**
     * Circuit breaker fallback for MTranServer failures.
     */
    private JSONObject mtranServerFallback(String from, String to, String text, boolean html, Throwable t) {
        log.warn("MTranServer 熔断器触发: {}", t.getMessage());
        throw new MTranServerUnavailableException("MTranServer 不可用（熔断器已打开）: " + t.getMessage(), t);
    }

    /**
     * 构建模拟翻译响应（用于开发/测试，不调用真实 MTranServer）
     */
    private JSONObject buildMockResponse(String from, String to, String text, boolean html) {
        String langLabel = switch (to.toLowerCase()) {
            case "zh", "zh-cn" -> "简体中文";
            case "zh-tw" -> "繁體中文";
            case "ja" -> "日本語";
            case "ko" -> "한국어";
            case "fr" -> "Français";
            case "de" -> "Deutsch";
            case "es" -> "Español";
            case "ru" -> "Русский";
            default -> to;
        };

        String result;
        if (html) {
            result = "[" + langLabel + "] " + text;
        } else {
            String[] lines = text.split("\n");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) sb.append("\n");
                sb.append(lines[i].isEmpty() ? "" : "[" + langLabel + "] " + lines[i]);
            }
            result = sb.toString();
        }

        JSONObject response = new JSONObject();
        response.put("result", result);
        return response;
    }
}
