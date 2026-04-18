package com.yumu.noveltranslator.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 缓存 Key 生成工具类
 * 使用 MD5 算法生成唯一缓存键，避免简单 hashCode() 的碰撞问题
 */
public class CacheKeyUtil {

    private static final String DEFAULT_SOURCE_LANG = "auto";

    /**
     * 构建缓存 Key
     * 格式：MD5(sourceText + "|" + sourceLang + "|" + targetLang + "|" + engine)
     *
     * @param sourceText 原文文本
     * @param targetLang 目标语言
     * @param engine 翻译引擎
     * @return MD5 缓存键
     */
    public static String buildCacheKey(String sourceText, String targetLang, String engine) {
        return buildCacheKey(sourceText, DEFAULT_SOURCE_LANG, targetLang, engine);
    }

    /**
     * 构建缓存 Key（带源语言）
     * 格式：MD5(sourceText + "|" + sourceLang + "|" + targetLang + "|" + engine)
     *
     * @param sourceText 原文文本
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @param engine 翻译引擎
     * @return MD5 缓存键
     */
    public static String buildCacheKey(String sourceText, String sourceLang,
                                        String targetLang, String engine) {
        // 标准化输入
        String normalizedText = normalizeText(sourceText);
        String normalizedSourceLang = normalizeLang(sourceLang);
        String normalizedTargetLang = normalizeLang(targetLang);
        String normalizedEngine = normalizeEngine(engine);

        // 构建原始字符串
        String rawKey = normalizedText + "|" + normalizedSourceLang + "|" +
                       normalizedTargetLang + "|" + normalizedEngine;

        // 计算 MD5
        return md5(rawKey);
    }

    /**
     * 标准化文本：去除首尾空白，压缩内部空白
     */
    private static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        // 去除首尾空白并将连续空白替换为单个空格
        return text.trim().replaceAll("\\s+", " ");
    }

    /**
     * 标准化语言代码：转为小写
     */
    private static String normalizeLang(String lang) {
        if (lang == null || lang.trim().isEmpty()) {
            return DEFAULT_SOURCE_LANG;
        }
        return lang.trim().toLowerCase();
    }

    /**
     * 标准化引擎名称：转为小写
     */
    private static String normalizeEngine(String engine) {
        if (engine == null || engine.trim().isEmpty()) {
            return "auto";
        }
        return engine.trim().toLowerCase();
    }

    /**
     * 计算 MD5 哈希值
     */
    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            return bytesToHex(messageDigest);
        } catch (NoSuchAlgorithmException e) {
            // MD5 算法不存在时，回退到简单哈希（理论上不会发生）
            return Integer.toHexString(input.hashCode());
        }
    }

    /**
     * 字节数组转十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
