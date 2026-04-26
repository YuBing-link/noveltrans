package com.yumu.noveltranslator.util;

import org.apache.commons.text.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.util.regex.Pattern;

/**
 * 文本清洗工具类
 * 提供文本清洗、格式化、规范化等功能
 * 注意：不处理HTML标签（由Jsoup或其他工具处理）
 * 重要：清洗后的文本保持与原文相同的展示形式，不删除标点符号、换行、空格等格式
 * 重要：不会删除网页标签（如 <div>, <p> 等）
 */
public class TextCleaningUtil {

    // 零宽字符和特殊不可见字符（不影响显示但可能造成问题）
    // 零宽空格 \u200B、零宽非连接符 \u200C、零宽连接符 \u200D
    // 双向控制字符 \u200E-\u200F, \u202A-\u202E
    // BOM和非法字符 \uFEFF, \uFFFE, \uFFFF
    private static final Pattern SPECIAL_INVISIBLE_CHARS = Pattern.compile(
            "[\\u200B\\u200C\\u200D\\u200E\\u200F\\uFEFF\\uFFFE\\uFFFF\\u202A-\\u202E]+"
    );

    // 非打印控制字符（ASCII 0-31）
    // 注意：换行符(\n)、回车符(\r)、制表符(\t)会被保留
    // 移除的是真正有害的控制字符：\x00-\x08, \x0B, \x0C, \x0E-\x1F
    private static final Pattern CONTROL_CHARS = Pattern.compile(
            "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]+"
    );

    // 不合法的Unicode字符（代理对错误的字符）
    private static final Pattern INVALID_UNICODE = Pattern.compile(
            "[\\uD800-\\uDFFF]"
    );

    // 连续的零宽空格（多个连续的零宽字符）
    private static final Pattern MULTIPLE_ZERO_WIDTH = Pattern.compile(
            "(\\u200B){2,}|(\\u200C){2,}|(\\u200D){2,}"
    );

    // 用于检测不可见字符的模式（组合）
    private static final Pattern INVISIBLE_CHARS = Pattern.compile(
            "[\\p{Cntrl}\\p{Cf}]"
    );

    /**
     * 清洗文本：仅移除真正有害的不可见字符，保持原文的展示形式
     * 保留所有标点符号、空格、换行、制表符、网页标签等格式字符
     *
     * @param text 待清洗的文本
     * @return 清洗后的文本
     */
    public static String cleanText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // 1. 移除零宽字符和特殊不可见字符
        text = removeSpecialInvisibleChars(text);

        // 2. 移除非法控制字符
        text = removeControlChars(text);

        // 3. 移除不合法的Unicode字符
        text = removeInvalidUnicode(text);

        // 4. 移除多个连续的零宽字符
        text = removeMultipleZeroWidthChars(text);

        return text;
    }

    /**
     * 移除特殊不可见字符（零宽空格、零宽连接符、BOM、双向控制字符等）
     * 这些字符不会在页面上显示，但可能影响文本处理
     *
     * @param text 文本
     * @return 处理后的文本
     */
    public static String removeSpecialInvisibleChars(String text) {
        if (text == null) {
            return null;
        }
        return SPECIAL_INVISIBLE_CHARS.matcher(text).replaceAll("");
    }

    /**
     * 移除非法控制字符（保留换行符、回车符、制表符）
     * 移除的是真正有害的非打印字符（ASCII 0-8, 11, 12, 14-31）
     *
     * @param text 文本
     * @return 处理后的文本
     */
    public static String removeControlChars(String text) {
        if (text == null) {
            return null;
        }
        return CONTROL_CHARS.matcher(text).replaceAll("");
    }

    /**
     * 移除不合法的Unicode字符（代理对错误的字符）
     *
     * @param text 文本
     * @return 处理后的文本
     */
    public static String removeInvalidUnicode(String text) {
        if (text == null) {
            return null;
        }
        return INVALID_UNICODE.matcher(text).replaceAll("");
    }

    /**
     * 移除多个连续的零宽字符
     *
     * @param text 文本
     * @return 处理后的文本
     */
    public static String removeMultipleZeroWidthChars(String text) {
        if (text == null) {
            return null;
        }
        return MULTIPLE_ZERO_WIDTH.matcher(text).replaceAll("");
    }

    /**
     * 白名单 HTML 净化：仅允许安全的排版标签，移除所有恶意标签和属性。
     * 使用 Jsoup Safelist，防止 LLM 返回的 &lt;script&gt;、&lt;iframe&gt; 等在前端渲染为 XSS。
     *
     * 允许标签: p, br, b, strong, i, em, u, s, blockquote, ul, ol, li, h1-h6, hr, pre, code, span, div
     * 允许属性: 安全标签的 class 和 id（不含 onclick、style 等事件/样式属性）
     *
     * @param text 待净化的文本（可能包含任意 HTML）
     * @return 净化后的文本（仅保留安全标签，移除所有危险内容）
     */
    public static String sanitizeHtml(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Safelist safelist = Safelist.relaxed()
                .addTags("hr")
                .removeProtocols("a", "href", "javascript:", "vbscript:");

        return Jsoup.clean(text, safelist);
    }

    /**
     * 转义HTML特殊字符（&amp; &lt; &gt; " '）
     *
     * @param text 文本
     * @return 转义后的文本
     */
    public static String escapeHtml(String text) {
        if (text == null) {
            return null;
        }
        return StringEscapeUtils.escapeHtml4(text);
    }

    /**
     * 反转义HTML实体
     *
     * @param text 转义后的文本
     * @return 反转义后的文本
     */
    public static String unescapeHtml(String text) {
        if (text == null) {
            return null;
        }
        return StringEscapeUtils.unescapeHtml4(text);
    }

    /**
     * 转义XML特殊字符（& < > " '）
     *
     * @param text 文本
     * @return 转义后的文本
     */
    public static String escapeXml(String text) {
        if (text == null) {
            return null;
        }
        return StringEscapeUtils.escapeXml10(text);
    }

    /**
     * 反转义XML实体
     *
     * @param text 转义后的文本
     * @return 反转义后的文本
     */
    public static String unescapeXml(String text) {
        if (text == null) {
            return null;
        }
        return StringEscapeUtils.unescapeXml(text);
    }

    /**
     * 转义CSV特殊字符
     *
     * @param text 文本
     * @return 转义后的文本
     */
    public static String escapeCsv(String text) {
        if (text == null) {
            return null;
        }
        return StringEscapeUtils.escapeCsv(text);
    }

    /**
     * 反转义CSV
     *
     * @param text 转义后的文本
     * @return 反转义后的文本
     */
    public static String unescapeCsv(String text) {
        if (text == null) {
            return null;
        }
        return StringEscapeUtils.unescapeCsv(text);
    }

    /**
     * 转义Java字符串（\ \n \r \t 等）
     *
     * @param text 文本
     * @return 转义后的文本
     */
    public static String escapeJava(String text) {
        if (text == null) {
            return null;
        }
        return StringEscapeUtils.escapeJava(text);
    }

    /**
     * 反转义Java字符串
     *
     * @param text 转义后的文本
     * @return 反转义后的文本
     */
    public static String unescapeJava(String text) {
        if (text == null) {
            return null;
        }
        return StringEscapeUtils.unescapeJava(text);
    }

    /**
     * 转义JSON特殊字符
     *
     * @param text 文本
     * @return 转义后的文本
     */
    public static String escapeJson(String text) {
        if (text == null) {
            return null;
        }
        return StringEscapeUtils.escapeJson(text);
    }

    /**
     * 反转义JSON
     *
     * @param text 转义后的文本
     * @return 反转义后的文本
     */
    public static String unescapeJson(String text) {
        if (text == null) {
            return null;
        }
        return StringEscapeUtils.unescapeJson(text);
    }

    /**
     * 移除文本两端的空白字符（包括全角空格）
     *
     * @param text 文本
     * @return 处理后的文本
     */
    public static String trimFullWidth(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("^[\\s\\u3000]+", "").replaceAll("[\\s\\u3000]+$", "");
    }

    /**
     * 将全角字符转换为半角字符
     *
     * @param text 文本
     * @return 转换后的文本
     */
    public static String fullWidthToHalfWidth(String text) {
        if (text == null) {
            return null;
        }

        StringBuilder result = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c == '\u3000') {
                // 全角空格
                result.append(' ');
            } else if (c >= '\uFF01' && c <= '\uFF5E') {
                // 全角标点和字母数字
                result.append((char) (c - '\uFEE0'));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * 将半角字符转换为全角字符
     *
     * @param text 文本
     * @return 转换后的文本
     */
    public static String halfWidthToFullWidth(String text) {
        if (text == null) {
            return null;
        }

        StringBuilder result = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c == ' ') {
                // 空格
                result.append('\u3000');
            } else if (c >= '!' && c <= '~') {
                // 半角标点和字母数字
                result.append((char) (c + '\uFEE0'));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * 移除BOM（Byte Order Mark）
     *
     * @param text 包含BOM的文本
     * @return 移除BOM后的文本
     */
    public static String removeBom(String text) {
        if (text == null) {
            return null;
        }
        if (text.startsWith("\uFEFF")) {
            return text.substring(1);
        }
        return text;
    }

    /**
     * 规范化所有空白字符（包括制表符、换行符）为单个空格
     *
     * @param text 文本
     * @return 处理后的文本
     */
    public static String normalizeAllWhitespace(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("\\s+", " ");
    }

    /**
     * 清理文本中的多余空格（保留换行）
     *
     * @param text 文本
     * @return 处理后的文本
     */
    public static String cleanupSpaces(String text) {
        if (text == null) {
            return null;
        }
        // 行内多个空格压缩为一个
        String result = text.replaceAll(" +", " ");
        // 移除行首行尾空格（保留换行符）
        result = result.replaceAll("(?m)^[ \\t]+", "");
        result = result.replaceAll("(?m)[ \\t]+$", "");
        return result;
    }

    /**
     * 移除文本中的制表符（\t）
     *
     * @param text 文本
     * @return 处理后的文本
     */
    public static String removeTabs(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("\t", "");
    }

    /**
     * 检查文本是否包含不可见字符
     *
     * @param text 文本
     * @return true 如果包含不可见字符
     */
    public static boolean hasInvisibleChars(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return INVISIBLE_CHARS.matcher(text).find() || SPECIAL_INVISIBLE_CHARS.matcher(text).find();
    }
}
