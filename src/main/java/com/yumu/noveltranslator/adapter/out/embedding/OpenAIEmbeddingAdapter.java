package com.yumu.noveltranslator.adapter.out.embedding;

import com.yumu.noveltranslator.port.out.EmbeddingPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

/**
 * EmbeddingPort adapter supporting OpenAI and Ollama providers.
 */
@Component
@Slf4j
public class OpenAIEmbeddingAdapter implements EmbeddingPort {

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

    public OpenAIEmbeddingAdapter(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Override
    public List<Double> embed(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        float[] result = "ollama".equalsIgnoreCase(provider)
                ? embedWithOllama(text)
                : embedWithOpenAI(text);
        return toDoubleList(result);
    }

    @Override
    public List<List<Double>> embedBatch(List<String> texts) {
        List<List<Double>> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(embed(text));
        }
        return results;
    }

    @CircuitBreaker(name = "embedding", fallbackMethod = "embeddingFallback")
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
                .block(Duration.ofSeconds(10));

        if (response != null && response.containsKey("data")) {
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) response.get("data");
            if (!dataList.isEmpty()) {
                return toFloatArray((List<Double>) dataList.get(0).get("embedding"));
            }
        }
        return new float[0];
    }

    @CircuitBreaker(name = "embedding", fallbackMethod = "embeddingFallback")
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
                .block(Duration.ofSeconds(10));

        if (response != null && response.containsKey("embedding")) {
            return toFloatArray((List<Double>) response.get("embedding"));
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

    private List<Double> toDoubleList(float[] array) {
        List<Double> result = new ArrayList<>(array.length);
        for (float v : array) {
            result.add((double) v);
        }
        return result;
    }

    private float[] embeddingFallback(String text, Throwable t) {
        log.warn("Embedding 服务熔断降级: {}", t.getMessage());
        return new float[0];
    }
}
