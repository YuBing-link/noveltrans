package com.yumu.noveltranslator.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 翻译后处理服务：检测译文中的残留中文并补充翻译
 */
@Service
@Slf4j
public class TranslationPostProcessingService {

    /** 残留中文检测正则：连续 2+ 个中文字符 */
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\u4e00-\u9fff\u3400-\u4dbf]{2,}");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${translation.python.url:http://llm-engine:8000/translate}")
    private String pythonTranslateUrl;

    /**
     * 检测并修复译文中的残留中文
     * @return 修复后的译文
     */
    public String fixUntranslatedChinese(String sourceText, String translatedText, String targetLang, String engine) {
        var segments = detectChineseSegments(translatedText);
        if (segments.isEmpty()) {
            log.debug("[后处理] 未检测到残留中文");
            return translatedText;
        }

        log.info("[后处理] 检测到 {} 段残留中文: {}", segments.size(), segments);

        try {
            String remedied = remediateSegments(segments, targetLang, engine);
            if (remedied != null) {
                String result = applyRemediation(translatedText, segments, remedied);
                log.info("[后处理] 补救完成，原文长度={}, 修复后长度={}", translatedText.length(), result.length());
                return result;
            }
        } catch (Exception e) {
            log.warn("[后处理] 补救失败: {}，保留原始译文", e.getMessage());
        }

        return translatedText;
    }

    /**
     * 检测译文中连续的中文字符段
     */
    private java.util.List<String> detectChineseSegments(String text) {
        java.util.List<String> segments = new java.util.ArrayList<>();
        Matcher matcher = CHINESE_PATTERN.matcher(text);
        while (matcher.find()) {
            segments.add(matcher.group());
        }
        return segments.stream().distinct().toList();
    }

    /**
     * 调用 LLM 补充翻译残留中文段
     */
    private String remediateSegments(java.util.List<String> segments, String targetLang, String engine) throws Exception {
        String sourceText = String.join("\n", segments);
        String prompt = "以下文本包含未翻译的中文内容，请将它们翻译为" + targetLang + "，返回翻译后的完整文本，保持原有换行：\n" + sourceText;

        String baseUrl = pythonTranslateUrl.replace("/translate", "");
        String url = baseUrl + "/translate";

        var body = new LinkedHashMap<String, Object>();
        body.put("text", prompt);
        body.put("target_lang", targetLang);
        body.put("engine", engine != null ? engine : "openai");
        body.put("fallback", true);

        String jsonBody = JSON.toJSONString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();
        if (responseBody == null || responseBody.isBlank()) {
            throw new RuntimeException("Python服务返回空响应体, status=" + response.statusCode());
        }

        JSONObject json = JSON.parseObject(responseBody);
        String data = json.getString("data");
        if (data == null || data.isBlank()) {
            throw new RuntimeException("补救翻译返回空数据");
        }
        return data;
    }

    /**
     * 将补救翻译结果应用到原文
     */
    private String applyRemediation(String translatedText, java.util.List<String> segments, String remedied) {
        String result = translatedText;
        String[] remediedSegments = remedied.split("\n", -1);

        for (int i = 0; i < segments.size(); i++) {
            String original = segments.get(i);
            String translated = (i < remediedSegments.length) ? remediedSegments[i].trim() : original;
            if (!translated.isBlank()) {
                result = result.replace(original, translated);
            }
        }
        return result;
    }
}
