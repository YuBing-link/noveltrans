package com.yumu.noveltranslator.domain.service;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.annotation.PreDestroy;

/**
 * 翻译后处理服务：检测译文中的残留中文并补充翻译
 */
@Service
@Slf4j
public class TranslationPostProcessingService {

    /** 残留中文检测正则：连续 2+ 个中文字符 */
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\u4e00-\u9fff\u3400-\u4dbf]{2,}");

    /** \u6b8b\u7559\u4e2d\u6587\u7edd\u5bf9\u4e0a\u9650\uff08\u5b57\u7b26\u6570\uff09\uff0c\u8d85\u8fc7\u6b64\u503c\u653e\u5f03\u540e\u5904\u7406\u4ee5\u907f\u514d\u6210\u672c\u5931\u63a7 */
    private static final int MAX_REMEDIAL_CHARS = 500;

    /** \u6b8b\u7559\u4e2d\u6587\u76f8\u5bf9\u4e0a\u9650\uff08\u5360\u8bd1\u6587\u603b\u957f\u5ea6\u7684\u767e\u5206\u6bd4\uff09\uff0c\u8d85\u8fc7\u6b64\u503c\u6807\u8bb0\u4e3a\u7ffb\u8bd1\u5931\u8d25 */
    private static final double MAX_REMEDIAL_RATIO = 0.15;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${translation.python.url:http://llm-engine:8000/translate}")
    private String pythonTranslateUrl;

    @Value("${translation.python.api-key:}")
    private String pythonApiKey;

    /**
     * 检测并修复译文中的残留中文
     * @return 修复后的译文
     */
    public String fixUntranslatedChinese(String sourceText, String translatedText, String targetLang, String engine) {
        // 日语使用 CJK 统一表意文字（汉字），与中文共享 Unicode 范围，
        // 后处理的中文检测会产生大量误报（現代社会、目標、本当 等均为合法日语），直接跳过
        if (isJapaneseTarget(targetLang)) {
            log.debug("[后处理] 目标语言为日语，跳过残留中文检测（避免汉字误报）");
            return translatedText;
        }

        var segments = detectChineseSegments(translatedText);
        if (!segments.isEmpty()) {
            int totalChineseChars = segments.stream().mapToInt(String::length).sum();

            // 成本熔断：残留中文超过阈值时放弃后处理，避免 API 账单击穿
            if (totalChineseChars > MAX_REMEDIAL_CHARS) {
                log.warn("[后处理] 残留中文 {} 字超过绝对上限({})，放弃后处理以避免成本失控", totalChineseChars, MAX_REMEDIAL_CHARS);
                return translatedText;
            }
            if (translatedText.length() > 0 && (double) totalChineseChars / translatedText.length() > MAX_REMEDIAL_RATIO) {
                log.warn("[后处理] 残留中文占比 {:.1f}% 超过阈值({:.0f}%)，译文可能翻译失败，放弃后处理",
                        (double) totalChineseChars / translatedText.length() * 100, MAX_REMEDIAL_RATIO * 100);
                return translatedText;
            }

            log.info("[后处理] 检测到 {} 段残留中文 (共 {} 字): {}", segments.size(), totalChineseChars, segments);

            try {
                String remedied = remediateSegments(segments, targetLang, engine);
                if (remedied != null) {
                    translatedText = applyRemediation(translatedText, segments, remedied);
                    log.info("[后处理] 补救完成，原文长度={}, 修复后长度={}", translatedText.length(), translatedText.length());
                }
            } catch (Exception e) {
                log.warn("[后处理] 补救失败: {}，保留原始译文", e.getMessage());
            }
        }

        // 注意：在逐行翻译场景下（streamTextTranslate），行级结构由 TranslationService 处理
        // 后处理只负责检测残留中文，不做任何结构重排
        return translatedText;
    }

    /**
     * 检测译文中连续的中文字符段
     * 日语目标语言使用排除法：日语使用 CJK 统一表意文字（汉字），需排除常见日语汉字
     */
    private List<String> detectChineseSegments(String text) {
        List<String> segments = new ArrayList<>();
        Matcher matcher = CHINESE_PATTERN.matcher(text);
        while (matcher.find()) {
            segments.add(matcher.group());
        }
        return segments.stream().distinct().toList();
    }

    /**
     * 判断目标语言是否为日语（日语使用 CJK 统一表意文字，与中文共享 Unicode 范围）
     */
    private static boolean isJapaneseTarget(String targetLang) {
        return targetLang != null && (targetLang.equalsIgnoreCase("ja") || targetLang.equalsIgnoreCase("japanese"));
    }

    /**
     * 日语常见独有/高频汉字（简化判断：含假名或日语特有汉字则跳过）
     * 完整策略：如果片段中包含平假名/片假名，则视为日语而非残留中文
     */
    private static final Pattern JAPANESE_KANA = Pattern.compile("[぀-ゟ゠-ヿㇰ-ㇿ]");

    /**
     * 判断一个片段是否应视为日语（非残留中文）
     */
    private boolean isJapaneseSegment(String segment, String targetLang) {
        if (!isJapaneseTarget(targetLang)) {
            return false;
        }
        // 包含假名 → 日语
        if (JAPANESE_KANA.matcher(segment).find()) {
            return true;
        }
        // 日语中纯汉字的片段很难区分，但结合目标语言为日语，
        // 纯汉字片段很可能是正常的日文汉字，不应视为残留中文
        return true;
    }

    /**
     * 调用 LLM 补充翻译残留中文段
     */
    private String remediateSegments(List<String> segments, String targetLang, String engine) throws Exception {
        String sourceText = String.join("\n", segments);

        String baseUrl = pythonTranslateUrl.replace("/translate", "");
        String url = baseUrl + "/translate";

        // 只发送需要翻译的文本，不加额外指令，避免 LLM 混淆指令和待翻译文本
        var body = new LinkedHashMap<String, Object>();
        body.put("text", sourceText);
        body.put("target_lang", targetLang);
        body.put("engine", engine != null ? engine : "openai");
        body.put("fallback", true);

        String jsonBody = JSON.toJSONString(body);
        var reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        if (pythonApiKey != null && !pythonApiKey.isEmpty()) {
            reqBuilder.header("X-Service-Key", pythonApiKey);
        }
        HttpRequest request = reqBuilder.build();

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

        // 过滤：如果返回结果包含 prompt 关键词（LLM 原样返回了指令文本），视为翻译失败
        if (data.contains("以下文本包含") || data.contains("未翻译的中文") || data.contains("请将它们翻译")) {
            throw new RuntimeException("补救翻译返回了指令文本，LLM 未正确翻译");
        }

        return data;
    }

    /**
     * 将补救翻译结果应用到原文
     * 关键修复：使用逐个替换，避免 String.replace() 的全局替换导致内容混乱
     */
    private String applyRemediation(String translatedText, List<String> segments, String remedied) {
        String result = translatedText;
        String[] remediedSegments = remedied.split("\n", -1);

        for (int i = 0; i < segments.size(); i++) {
            String original = segments.get(i);
            String translated = (i < remediedSegments.length) ? remediedSegments[i].trim() : original;
            if (!translated.isBlank()) {
                // 只替换第一次出现，避免全局替换导致内容混乱
                int idx = result.indexOf(original);
                if (idx >= 0) {
                    result = result.substring(0, idx) + translated + result.substring(idx + original.length());
                }
            }
        }
        return result;
    }

    @PreDestroy
    public void shutdown() {
        httpClient.close();
        log.info("TranslationPostProcessingService HTTP client closed");
    }
}
