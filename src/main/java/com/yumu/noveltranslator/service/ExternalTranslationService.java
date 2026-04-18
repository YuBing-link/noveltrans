package com.yumu.noveltranslator.service;

import com.alibaba.fastjson2.JSONObject;
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

    public ExternalTranslationService(
            @Value("${mtran.server.host:localhost}") String host,
            @Value("${mtran.server.port:8989}") int port) {
        this.webClient = WebClient.builder()
                .baseUrl("http://%s:%d".formatted(host, port))
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(256 * 1024))
                .build();
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
    public JSONObject translate(String from, String to, String text, boolean html) {
        JSONObject requestBody = new JSONObject();
        requestBody.put("from", from);
        requestBody.put("to", to);
        requestBody.put("text", text);
        requestBody.put("html", html);

        try {
            // 增加超时时间到 60 秒，因为翻译服务可能需要更长时间处理
            String responseBody = webClient.post()
                    .uri("/translate")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(60));

            return JSONObject.parseObject(responseBody);
        } catch (WebClientResponseException e) {
            log.error("外部翻译引擎调用失败，状态码：{}，响应体：{}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("外部翻译引擎调用失败 (HTTP " + e.getStatusCode() + "): " + e.getMessage(), e);
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
            // 检查是否是超时错误
            if (errorMsg.contains("Timeout") || errorMsg.contains("timeout") || errorMsg.contains("timed out")) {
                log.error("外部翻译引擎响应超时（>60 秒），请检查服务性能或网络连接。文本长度：{}", text != null ? text.length() : 0, e);
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
}
