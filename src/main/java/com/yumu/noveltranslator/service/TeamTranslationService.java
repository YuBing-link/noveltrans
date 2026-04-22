package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.TeamTranslateRequest;
import com.yumu.noveltranslator.dto.TeamTranslateResponse;
import com.yumu.noveltranslator.entity.Glossary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 翻译团队服务
 * 调用 Python 侧的 /translate-team 端点，使用 AI 多角色协作进行小说章节翻译
 */
@Service
@Slf4j
public class TeamTranslationService {

    private final WebClient webClient;

    public TeamTranslationService(
            @Value("${translate-server.host:localhost}") String host,
            @Value("${translate-server.port:8000}") int port) {
        this.webClient = WebClient.builder()
                .baseUrl("http://%s:%d".formatted(host, port))
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(512 * 1024))
                .build();
    }

    /**
     * 调用 AI 翻译团队进行章节翻译
     *
     * @param text         待翻译的章节原文
     * @param novelType    小说类型
     * @param sourceLang   源语言
     * @param targetLang   目标语言
     * @param glossaryTerms 术语表词条列表
     * @return 翻译后的文本
     */
    public String translateChapter(String text, String novelType, String sourceLang,
                                   String targetLang, List<Glossary> glossaryTerms) {

        TeamTranslateRequest request = buildRequest(text, novelType, sourceLang, targetLang, glossaryTerms);

        log.info("调用 AI 翻译团队: sourceLang={}, targetLang={}, novelType={}, textLength={}, glossaryCount={}",
                sourceLang, targetLang, novelType, text != null ? text.length() : 0,
                glossaryTerms != null ? glossaryTerms.size() : 0);

        try {
            String responseBody = webClient.post()
                    .uri("/translate-team")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(300));

            TeamTranslateResponse response = parseResponse(responseBody);

            if (response.getCode() != 200) {
                log.error("AI 翻译团队返回错误码: code={}", response.getCode());
                throw new RuntimeException("AI 翻译团队翻译失败，错误码: " + response.getCode());
            }

            log.info("AI 翻译团队调用成功: chunkCount={}, costMs={}ms",
                    response.getChunkCount(), response.getCostMs());

            return response.getData();

        } catch (WebClientResponseException e) {
            log.error("AI 翻译团队调用失败，状态码：{}，响应体：{}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("AI 翻译团队调用失败 (HTTP " + e.getStatusCode() + "): " + e.getMessage(), e);
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
            if (errorMsg.contains("Timeout") || errorMsg.contains("timeout") || errorMsg.contains("timed out")) {
                log.error("AI 翻译团队响应超时（>300 秒），文本长度：{}", text != null ? text.length() : 0, e);
                throw new RuntimeException("AI 翻译团队响应超时：" + e.getMessage(), e);
            }
            if (e.getCause() instanceof java.net.ConnectException
                    || errorMsg.contains("Connection refused")
                    || errorMsg.contains("connect timed out")) {
                log.error("AI 翻译团队连接失败，请确认服务已启动在 http://localhost:{}，错误：{}",
                        8000, e.getMessage(), e);
                throw new RuntimeException("无法连接到 AI 翻译团队服务：" + e.getMessage(), e);
            }
            log.error("AI 翻译团队翻译异常，文本长度：{}", text != null ? text.length() : 0, e);
            throw new RuntimeException("AI 翻译团队翻译失败：" + e.getMessage(), e);
        }
    }

    /**
     * 调用 AI 翻译团队进行章节翻译（带占位符保护）
     *
     * @param text              待翻译的章节原文（可能包含 [{1}], [{2}] 等占位符）
     * @param novelType         小说类型
     * @param sourceLang        源语言
     * @param targetLang        目标语言
     * @param glossaryTerms     术语表词条列表
     * @param placeholderMap    占位符 → 翻译后实体名的映射
     * @return 翻译后的文本（占位符已还原为翻译后的实体名）
     */
    public String translateChapterWithPlaceholders(String text, String novelType, String sourceLang,
                                                   String targetLang, List<Glossary> glossaryTerms,
                                                   Map<String, String> placeholderMap) {

        TeamTranslateRequest request = buildRequest(text, novelType, sourceLang, targetLang, glossaryTerms);

        // Attach placeholders to the request
        if (placeholderMap != null && !placeholderMap.isEmpty()) {
            request.setPlaceholders(placeholderMap);
            log.info("占位符保护: 发送 {} 个占位符映射", placeholderMap.size());
        } else {
            log.info("占位符保护: 无占位符映射");
        }

        log.info("调用 AI 翻译团队(带占位符): sourceLang={}, targetLang={}, novelType={}, textLength={}, glossaryCount={}, placeholderCount={}",
                sourceLang, targetLang, novelType, text != null ? text.length() : 0,
                glossaryTerms != null ? glossaryTerms.size() : 0,
                placeholderMap != null ? placeholderMap.size() : 0);

        try {
            String responseBody = webClient.post()
                    .uri("/translate-team")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(300));

            TeamTranslateResponse response = parseResponse(responseBody);

            if (response.getCode() != 200) {
                log.error("AI 翻译团队返回错误码: code={}", response.getCode());
                throw new RuntimeException("AI 翻译团队翻译失败，错误码: " + response.getCode());
            }

            log.info("AI 翻译团队调用成功(带占位符): chunkCount={}, costMs={}ms",
                    response.getChunkCount(), response.getCostMs());

            String translatedText = response.getData();

            // Restore placeholders in the translated text
            if (placeholderMap != null && !placeholderMap.isEmpty()) {
                translatedText = restorePlaceholdersInText(translatedText, placeholderMap);
            }

            return translatedText;

        } catch (WebClientResponseException e) {
            log.error("AI 翻译团队调用失败，状态码：{}，响应体：{}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("AI 翻译团队调用失败 (HTTP " + e.getStatusCode() + "): " + e.getMessage(), e);
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
            if (errorMsg.contains("Timeout") || errorMsg.contains("timeout") || errorMsg.contains("timed out")) {
                log.error("AI 翻译团队响应超时（>300 秒），文本长度：{}", text != null ? text.length() : 0, e);
                throw new RuntimeException("AI 翻译团队响应超时：" + e.getMessage(), e);
            }
            if (e.getCause() instanceof java.net.ConnectException
                    || errorMsg.contains("Connection refused")
                    || errorMsg.contains("connect timed out")) {
                log.error("AI 翻译团队连接失败，请确认服务已启动在 http://localhost:{}，错误：{}",
                        8000, e.getMessage(), e);
                throw new RuntimeException("无法连接到 AI 翻译团队服务：" + e.getMessage(), e);
            }
            log.error("AI 翻译团队翻译异常，文本长度：{}", text != null ? text.length() : 0, e);
            throw new RuntimeException("AI 翻译团队翻译失败：" + e.getMessage(), e);
        }
    }

    /**
     * 在翻译后的文本中还原占位符
     * 按占位符字符串长度降序替换，避免 [{1}] 匹配到 [{10}] 中的部分子串
     * 如果某个占位符在响应中未找到，会记录警告日志
     *
     * @param text           翻译后的文本
     * @param placeholderMap 占位符 → 翻译后实体名的映射
     * @return 还原占位符后的文本
     */
    private String restorePlaceholdersInText(String text, Map<String, String> placeholderMap) {
        if (text == null || text.isEmpty() || placeholderMap == null || placeholderMap.isEmpty()) {
            return text;
        }

        String result = text;

        // Sort placeholders by key length descending to avoid partial matches
        List<Map.Entry<String, String>> sortedEntries = new HashMap<>(placeholderMap).entrySet().stream()
                .sorted(Map.Entry.<String, String>comparingByKey(Comparator.comparingInt(String::length).reversed()))
                .collect(Collectors.toList());

        int restoredCount = 0;
        int notFoundCount = 0;

        for (Map.Entry<String, String> entry : sortedEntries) {
            String placeholder = entry.getKey();
            String translatedEntity = entry.getValue();

            if (result.contains(placeholder)) {
                result = result.replace(placeholder, translatedEntity);
                restoredCount++;
                log.debug("占位符还原: '{}' -> '{}'", placeholder, translatedEntity);
            } else {
                notFoundCount++;
                log.warn("占位符还原: 响应中未找到占位符 '{}' (映射到 '{}')，AI 可能未正确翻译该实体",
                        placeholder, translatedEntity);
            }
        }

        log.info("占位符还原完成: 总数={}, 已还原={}, 未找到={}",
                sortedEntries.size(), restoredCount, notFoundCount);

        return result;
    }

    /**
     * 将请求参数构建为 TeamTranslateRequest
     */
    private TeamTranslateRequest buildRequest(String text, String novelType, String sourceLang,
                                              String targetLang, List<Glossary> glossaryTerms) {
        TeamTranslateRequest request = new TeamTranslateRequest();
        request.setText(text);
        request.setNovelType(novelType);
        request.setSourceLang(sourceLang);
        request.setTargetLang(targetLang);

        if (glossaryTerms != null && !glossaryTerms.isEmpty()) {
            List<TeamTranslateRequest.GlossaryTerm> terms = glossaryTerms.stream()
                    .map(g -> new TeamTranslateRequest.GlossaryTerm(
                            g.getSourceWord(),
                            g.getTargetWord(),
                            g.getRemark()))
                    .collect(Collectors.toList());
            request.setGlossaryTerms(terms);
        }

        return request;
    }

    /**
     * 解析响应 JSON 为 TeamTranslateResponse
     */
    private TeamTranslateResponse parseResponse(String responseBody) {
        try {
            return com.alibaba.fastjson2.JSON.parseObject(responseBody, TeamTranslateResponse.class);
        } catch (Exception e) {
            log.error("解析 AI 翻译团队响应失败: {}", responseBody, e);
            TeamTranslateResponse fallback = new TeamTranslateResponse();
            fallback.setCode(-1);
            fallback.setData(responseBody);
            return fallback;
        }
    }
}
