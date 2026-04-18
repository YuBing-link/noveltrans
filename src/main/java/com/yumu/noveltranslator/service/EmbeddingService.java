package com.yumu.noveltranslator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Embedding 服务
 * 调用 OpenAI 或 Ollama 的 Embedding API 生成文本向量
 */
@Service
@Slf4j
public class EmbeddingService {

    private final WebClient webClient;

    @Value("${embedding.provider:openai}")
    private String provider;

    @Value("${embedding.openai.api-key:}")
    private String openaiApiKey;

    @Value("${embedding.openai.base-url:https://api.openai.com}")
    private String openaiBaseUrl;

    @Value("${embedding.openai.model:text-embedding-3-small}")
    private String openaiModel;

    @Value("${embedding.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${embedding.ollama.model:bge-m3}")
    private String ollamaModel;

    public EmbeddingService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * 生成文本向量
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }

        try {
            if ("ollama".equals(provider)) {
                return embedWithOllama(text);
            } else {
                return embedWithOpenAI(text);
            }
        } catch (Exception e) {
            log.error("Embedding 生成失败: {}", e.getMessage(), e);
            return new float[0];
        }
    }

    private float[] embedWithOpenAI(String text) {
        if (openaiApiKey == null || openaiApiKey.isBlank()) {
            log.warn("OpenAI API Key 未配置，跳过 Embedding 生成");
            return new float[0];
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", openaiModel);
        requestBody.put("input", text);

        Map<String, Object> response = webClient.post()
                .uri(openaiBaseUrl + "/v1/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + openaiApiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response != null && response.containsKey("data")) {
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) response.get("data");
            if (!dataList.isEmpty()) {
                List<Double> embedding = (List<Double>) dataList.get(0).get("embedding");
                return toFloatArray(embedding);
            }
        }
        return new float[0];
    }

    private float[] embedWithOllama(String text) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", ollamaModel);
        requestBody.put("prompt", text);

        Map<String, Object> response = webClient.post()
                .uri(ollamaBaseUrl + "/api/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response != null && response.containsKey("embedding")) {
            List<Double> embedding = (List<Double>) response.get("embedding");
            return toFloatArray(embedding);
        }
        return new float[0];
    }

    private float[] toFloatArray(List<Double> list) {
        float[] result = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = list.get(i).floatValue();
        }
        return result;
    }

    /**
     * 获取向量维度
     */
    public int getDimension() {
        return "ollama".equals(provider) ? 1024 : 1536;
    }
}
