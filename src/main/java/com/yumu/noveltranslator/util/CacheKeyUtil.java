package com.yumu.noveltranslator.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 缓存 Key 生成工具类
 * 使用 MD5 算法生成唯一缓存键，避免简单 hashCode() 的碰撞问题
 * 版本号由 TranslationCacheService 统一管理
 */
public class CacheKeyUtil {

    private static final String DEFAULT_SOURCE_LANG = "auto";

    /**
     * 构建基础缓存 Key（不含引擎名）
     * 仅返回 MD5 hash，版本号由 TranslationCacheService 统一管理
     *
     * @param sourceText 原文文本
     * @param targetLang 目标语言
     * @return MD5 缓存键（无版本号）
     */
    public static String buildCacheKey(String sourceText, String targetLang) {
        return buildCacheKey(sourceText, DEFAULT_SOURCE_LANG, targetLang);
    }

    /**
     * 构建缓存 Key（含源语言，不含引擎名）
     * 仅返回 MD5 hash，版本号由 TranslationCacheService 统一管理
     *
     * @param sourceText 原文文本
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @return MD5 缓存键（无版本号）
     */
    public static String buildCacheKey(String sourceText, String sourceLang, String targetLang) {
        String normalizedText = normalizeText(sourceText);
        String normalizedSourceLang = normalizeLang(sourceLang);
        String normalizedTargetLang = normalizeLang(targetLang);

        String rawKey = normalizedText + "|" + normalizedSourceLang + "|" + normalizedTargetLang;
        return md5(rawKey);
    }

    /**
     * 构建带指定版本号的缓存 Key（已废弃，版本号由 Service 层管理）
     * @deprecated 使用 {@link #buildCacheKey(String, String, String)} 代替
     */
    @Deprecated
    public static String buildCacheKey(String sourceText, String sourceLang, String targetLang, String version) {
        return buildCacheKey(sourceText, sourceLang, targetLang);
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
