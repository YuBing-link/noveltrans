package com.yumu.noveltranslator.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yumu.noveltranslator.enums.ErrorCodeEnum;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExternalResponseUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 从原始后端JSON响应中提取主要翻译文本。
     * 后端格式通常为：
     * {“code”：200，“data”：“<h1>...</h1>”，“engine”：“google”,...}
     * 该方法尝试共用键（data、translatedContent、translation）和
     * 在解析失败时，会退回返回原始的原始字符串。
     */
    /**
     * 从原始后端 JSON 响应中提取主要翻译文本
     * 支持的格式：
     * 1. 标准格式：{"success": true, "engine": "google", "translatedContent": "{...}"}
     * 2. 通用格式：{"code": 200, "data": "<h1>...</h1>", "engine": "google",...}
     *
     * @param rawJson 原始 JSON 响应
     * @return 翻译文本，如果失败返回 null
     */
    public static String extractDataField(String rawJson) {
        if (rawJson == null) return null;
        try {
            JsonNode root = MAPPER.readTree(rawJson);
            if (root == null) return null;

            // 1. 优先提取 translatedContent 字段（标准格式）
            if (root.has("translatedContent")) {
                JsonNode translatedContent = root.get("translatedContent");
                if (translatedContent.isTextual()) {
                    return translatedContent.asText();
                }
                return translatedContent.toString();
            }

            // 2. 验证状态码（仅对有 code 字段的格式）
            StatusCodeResult statusResult = validateStatusCode(root);
            if (!statusResult.isValid()) {
                // 状态码不是 200，返回 null 表示失败
                return null;
            }

            // 3. 提取 data 字段
            if (root.has("data")) {
                JsonNode data = root.get("data");
                if (data.isTextual()) return data.asText();
                return data.toString();
            }

            // 4. 如果找不到已知键，尝试返回文本形式
            if (root.isTextual()) return root.asText();

            // 5. 返回原始 JSON 作为兜底
            return rawJson;
        } catch (Exception e) {
            // 解析失败——返回 null 表示失败
            return null;
        }
    }

    /**
     * 验证响应状态码
     * @param root JSON 根节点
     * @return 状态码验证结果
     */
    public static StatusCodeResult validateStatusCode(JsonNode root) {
        if (root.has("code")) {
            JsonNode code = root.get("code");
            int statusCode = code.asInt();
            return new StatusCodeResult(statusCode == 200, statusCode);
        }
        // 没有 code 字段时，假设响应有效（兼容旧格式）
        return new StatusCodeResult(true, 200);
    }

    /**
     * 状态码验证结果类
     */
    public static class StatusCodeResult {
        private final boolean valid;
        private final int statusCode;

        public StatusCodeResult(boolean valid, int statusCode) {
            this.valid = valid;
            this.statusCode = statusCode;
        }

        public boolean isValid() {
            return valid;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }

    public static List<String> processingReader(String content){
        Document document = Jsoup.parse(content);
        List<String> orderedText = new ArrayList<>();
        traverseAllTags(document, orderedText);
        return orderedText;
    }

    private static void traverseAllTags(Node parentNode, List<String> resultList) {
        for (Node childNode : parentNode.childNodes()) {
                String tagContent = childNode.outerHtml();
                resultList.add(tagContent);
                traverseAllTags(childNode, resultList);
        }
    }


    /**
     * 统一错误响应格式：
     * {
     *   "success": false,
     *   "error": "Error message description",
     *   "code": "ERROR_CODE",
     *   "details": { ... }
     * }
     */
    public static String buildErrorResponse(String error, String code, Map<String, Object> details) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("success", false);
            root.put("error", error);
            root.put("code", code);
            ObjectNode detailsNode = root.putObject("details");
            if (details != null) {
                details.forEach((k, v) -> detailsNode.putPOJO(k, v));
            }
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            // 最后兜底，构造极简错误字符串
            return String.format("{\"success\":false,\"error\":\"%s\",\"code\":\"%s\"}", "Serialization failure", "FATAL_ERROR");
        }
    }

    /**
     * 使用枚举构建错误响应
     */
    public static String buildErrorResponse(ErrorCodeEnum errorCodeEnum, Map<String, Object> details) {
        return buildErrorResponse(errorCodeEnum.getMessage(), errorCodeEnum.getCode(), details);
    }

    /**
     * 构建翻译文件路径（在扩展名前插入 _translated）
     * @param originalPath 原始文件路径
     * @return 翻译后的文件路径
     */
    public static String buildTranslatedPath(String originalPath) {
        if (originalPath == null) return null;
        int lastDot = originalPath.lastIndexOf('.');
        if (lastDot > 0) {
            return originalPath.substring(0, lastDot) + "_translated" + originalPath.substring(lastDot);
        }
        return originalPath + "_translated";
    }

    /**
     * 使用枚举构建简单错误响应
     */
    public static String buildSimpleErrorResponse(ErrorCodeEnum errorCodeEnum) {
        return buildErrorResponse(errorCodeEnum.getMessage(), errorCodeEnum.getCode(), null);
    }
}
