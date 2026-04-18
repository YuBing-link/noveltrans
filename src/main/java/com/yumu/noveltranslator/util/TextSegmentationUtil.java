package com.yumu.noveltranslator.util;

import com.yumu.noveltranslator.enums.TranslationEngine;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本分段工具类
 * 根据不同翻译引擎的字符限制，智能地将长文本拆分为符合要求的片段
 * 支持按句子、段落边界分割，避免破坏语义完整性
 */
public class TextSegmentationUtil {

    // 句子结束标点符号（中英文）
    private static final String SENTENCE_ENDINGS = ".!?。！？…\n\r";

    /**
     * 根据指定的翻译引擎分割文本
     *
     * @param text 待分割的文本
     * @param engineName 翻译引擎名称（如 "google", "deepl", "mymemory", "baidu", "libre", "youdao"）
     * @return 分割后的文本片段列表
     */
    public static List<String> segmentByTextEngine(String text, String engineName) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        int maxChars = TranslationEngine.getMaxChars(engineName);
        return segmentText(text, maxChars);
    }

    /**
     * 按指定字符数限制分割文本
     * 优先在句子边界处分割，其次在段落边界处分割，最后按字符数硬分割
     *
     * @param text 待分割的文本
     * @param maxChars 每段最大字符数
     * @return 分割后的文本片段列表
     */
    public static List<String> segmentText(String text, int maxChars) {
        if (text == null || text.isEmpty() || maxChars <= 0) {
            return new ArrayList<>();
        }

        List<String> segments = new ArrayList<>();

        // 如果文本长度不超过限制，直接返回
        if (text.length() <= maxChars) {
            segments.add(text);
            return segments;
        }

        int startPos = 0;
        while (startPos < text.length()) {
            int endPos = Math.min(startPos + maxChars, text.length());

            // 如果到达文本末尾，添加最后一段
            if (startPos >= text.length()) {
                break;
            }

            // 如果当前段已经很短（接近或等于最大限制），直接截取
            if (endPos - startPos < maxChars) {
                segments.add(text.substring(startPos));
                break;
            }

            // 尝试在句子边界处分割
            int sentenceBreak = findSentenceBreak(text, startPos, endPos);
            if (sentenceBreak > startPos) {
                segments.add(text.substring(startPos, sentenceBreak));
                startPos = sentenceBreak;
                continue;
            }

            // 尝试在段落边界处分割
            int paragraphBreak = findParagraphBreak(text, startPos, endPos);
            if (paragraphBreak > startPos) {
                segments.add(text.substring(startPos, paragraphBreak));
                startPos = paragraphBreak;
                continue;
            }

            // 按单词边界分割，避免在单词中间切断
            int wordBreak = findWordBreak(text, startPos, endPos);
            if (wordBreak > startPos) {
                segments.add(text.substring(startPos, wordBreak));
                startPos = wordBreak;
                continue;
            }

            // 如果以上都找不到合适的分割点，则强制按字符数分割
            segments.add(text.substring(startPos, endPos));
            startPos = endPos;
        }

        return segments;
    }

    /**
     * 查找句子边界分割点
     * 优化：避免重复创建 Pattern 和 Matcher，直接遍历字符查找
     */
    private static int findSentenceBreak(String text, int startPos, int endPos) {
        // 从后往前找句子结束符，优先在最靠近 endPos 的位置分割
        for (int i = endPos - 1; i >= startPos; i--) {
            char c = text.charAt(i);
            if (isSentenceEnding(c)) {
                // 移动到句子结束符之后
                int sentenceEnd = i + 1;
                // 跳过连续的空白字符
                while (sentenceEnd < text.length() &&
                       Character.isWhitespace(text.charAt(sentenceEnd))) {
                    sentenceEnd++;
                }
                return sentenceEnd;
            }
        }
        return -1; // 未找到合适的分割点
    }

    /**
     * 判断字符是否为句子结束符
     */
    private static boolean isSentenceEnding(char c) {
        return SENTENCE_ENDINGS.indexOf(c) >= 0;
    }

    /**
     * 查找段落边界分割点
     */
    private static int findParagraphBreak(String text, int startPos, int endPos) {
        // 查找换行符作为段落边界
        for (int i = endPos - 1; i >= startPos; i--) {
            if (text.charAt(i) == '\n') {
                // 找到换行符后跳过连续的换行符
                int newlineEnd = i + 1;
                while (newlineEnd < text.length() && text.charAt(newlineEnd) == '\n') {
                    newlineEnd++;
                }
                return newlineEnd;
            }
        }
        return -1; // 未找到合适的分割点
    }

    /**
     * 查找单词边界分割点
     */
    private static int findWordBreak(String text, int startPos, int endPos) {
        // 从后往前找空格，避免在单词中间分割
        for (int i = endPos - 1; i >= startPos; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i + 1; // 返回空格后位置，这样不会把单词分开
            }
        }
        return -1; // 未找到合适的分割点
    }

    /**
     * 获取指定引擎的最大字符数
     *
     * @param engineName 翻译引擎名称
     * @return 最大字符数限制
     */
    public static int getMaxCharsForEngine(String engineName) {
        return TranslationEngine.getMaxChars(engineName);
    }

    /**
     * 计算文本的分段数量（预估）
     *
     * @param text 文本内容
     * @param engineName 翻译引擎名称
     * @return 预计分段数量
     */
    public static int estimateSegmentsCount(String text, String engineName) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int maxChars = getMaxCharsForEngine(engineName);
        if (maxChars <= 0) {
            return 1; // 如果没有限制，当作一段
        }

        int baseCount = (int) Math.ceil((double) text.length() / maxChars);

        // 考虑智能分割可能减少段落数量
        List<String> segments = segmentByTextEngine(text, engineName);
        return Math.min(baseCount, segments.size());
    }
}
