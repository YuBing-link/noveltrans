package com.yumu.noveltranslator.service;

import com.alibaba.fastjson2.JSON;
import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.util.CacheKeyUtil;
import com.yumu.noveltranslator.util.ExternalResponseUtil;
import com.yumu.noveltranslator.util.SseEmitterUtil;
import com.yumu.noveltranslator.util.TextCleaningUtil;
import com.yumu.noveltranslator.util.TextSegmentationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 翻译服务类
 * 提供三种翻译模式：选中文本翻译、阅读器翻译、网页翻译
 *
 * 优化特性：
 * - 三级缓存：内存缓存 → 数据库缓存 → 翻译服务
 * - 改进的缓存 Key：使用 MD5 避免碰撞
 * - 全局线程池复用
 * - 批量翻译优化
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TranslationService {

    private static final String DEFAULT_TARGET_LANG = "zh";
    private static final String DEFAULT_ENGINE = "auto";

    // 依赖注入
    private final UserLevelThrottledTranslationClient userLevelThrottledTranslationClient;
    private final TranslationCacheService cacheService;

    /**
     * 选中文本翻译
     * @param req 翻译请求
     * @return 翻译响应
     */
    public SelectionTranslateResponse selectionTranslate(SelectionTranslationRequest req) {
        String combined = req.getContext() != null ? req.getContext() : req.getText();
        if (combined == null || combined.trim().isEmpty()) {
            return new SelectionTranslateResponse(false, req.getEngine(), "内容为空");
        }

        String engine = req.getEngine() == null ? DEFAULT_ENGINE : req.getEngine();
        String target = req.getTargetLang() == null ? DEFAULT_TARGET_LANG : req.getTargetLang();

        try {
            String result = translateWithCache(combined, target, engine);
            if (result == null || result.trim().isEmpty()) {
                return new SelectionTranslateResponse(false, engine, "翻译失败：返回结果为空");
            }
            return new SelectionTranslateResponse(true, engine, result);
        } catch (Exception e) {
            log.error("选中文本翻译失败：{}", e.getMessage());
            return new SelectionTranslateResponse(false, engine, "翻译失败：" + e.getMessage());
        }
    }

    /**
     * 带缓存的翻译方法（三级缓存查询）
     * 支持引擎降级：远程引擎失败时自动降级到本地引擎
     *
     * 缓存策略：
     * - 仅当翻译成功完成且译文与原文不一致时才加入缓存
     */
    private String translateWithCache(String text, String target, String engine) {
        // 生成 MD5 缓存 Key
        String cacheKey = CacheKeyUtil.buildCacheKey(text, target, engine);

        // 1. 尝试内存缓存
        String cached = cacheService.getCache(cacheKey);
        if (cached != null) {
            log.debug("缓存命中 [{}]", cacheKey.substring(0, Math.min(16, cacheKey.length())));
            return cached;
        }

        // 2. 缓存未命中，调用翻译服务（支持降级）
        String rawJson = userLevelThrottledTranslationClient.translate(text, target, engine, false);
        String result = ExternalResponseUtil.extractDataField(rawJson);

        // 检查翻译是否失败：result 为 null 表示翻译服务返回错误或空数据
        if (result == null) {
            throw new RuntimeException("翻译服务返回错误：" + rawJson);
        }

        // 校验翻译结果是否有效（非广告文案、长度合理）
        if (!isValidTranslation(text, result)) {
            throw new RuntimeException("翻译结果无效：返回内容非翻译文本");
        }

        // 3. 仅当翻译成功且译文与原文不一致时才缓存
        if (shouldCache(text, result)) {
            cacheService.putCache(cacheKey, text, result, "auto", target, engine);
        } else {
            log.debug("译文与原文一致，跳过缓存：{}", cacheKey.substring(0, Math.min(16, cacheKey.length())));
        }

        return result;
    }

    /**
     * 判断是否应该缓存翻译结果
     * 仅当译文与原文不一致时才缓存
     */
    private boolean shouldCache(String original, String translated) {
        if (original == null || translated == null) {
            return false;
        }
        // 去除首尾空白后比较，避免仅空格差异
        String cleanOriginal = original.trim();
        String cleanTranslated = translated.trim();

        // 如果译文与原文相同，则不缓存
        if (cleanOriginal.equals(cleanTranslated)) {
            return false;
        }

        // 忽略大小写比较，避免仅大小写差异
        if (cleanOriginal.equalsIgnoreCase(cleanTranslated)) {
            return false;
        }

        return true;
    }

    /**
     * 校验翻译结果是否有效
     * 检测非翻译内容（如广告文案、系统提示等）
     */
    private boolean isValidTranslation(String text, String result) {
        if (text == null || result == null) {
            return false;
        }
        // 检测明显的广告/系统提示关键词
        String[] adKeywords = {"人工智能助手", "生成式人工智能", "体验生成式", "获取写作", "Gemini", "Google AI"};
        for (String keyword : adKeywords) {
            if (result.contains(keyword)) {
                log.warn("翻译结果包含广告关键词：{}", keyword);
                return false;
            }
        }
        // 检测译文长度异常（超过原文 10 倍）
        if (result.length() > text.length() * 10) {
            log.warn("翻译结果长度异常：原文 {} 字符，译文 {} 字符", text.length(), result.length());
            return false;
        }
        return true;
    }

    /**
     * 阅读器翻译 - 支持并行翻译长文本
     * 默认使用 MTranServer 进行翻译，html 模式启用
     * @param req 翻译请求
     * @return 翻译响应
     */
    public ReaderTranslateResponse readerTranslate(ReaderTranslateRequest req) {
        String content = req.getContent();
        if (content == null || content.trim().isEmpty()) {
            return new ReaderTranslateResponse(false, req.getEngine(), "没有收到内容");
        }

        String target = req.getTargetLang() == null ? DEFAULT_TARGET_LANG : req.getTargetLang();
        String engine = req.getEngine() == null ? DEFAULT_ENGINE : req.getEngine();

        // 文本分段
        List<String> segments = TextSegmentationUtil.segmentByTextEngine(content, engine);
        if (segments.isEmpty()) {
            return new ReaderTranslateResponse(false, engine, "段落为空");
        }

        // 并行翻译所有段落（复用全局线程池）
        // 阅读器模式默认使用 MTranServer，html=true
        List<String> translatedSegments = translateSegmentsInParallel(segments, target, engine, true);

        // 合并翻译结果
        String combinedResult = String.join("", translatedSegments);
        log.info("阅读器翻译完成，原文长度：{}，译文长度：{}", content.length(), combinedResult.length());
        return new ReaderTranslateResponse(true, engine, combinedResult);
    }

    /**
     * 并行翻译文本段落（使用虚拟线程并发翻译）
     *
     * 优化策略：
     * 1. 先尝试从缓存获取每个段落
     * 2. 对缓存未命中的段落使用虚拟线程并发翻译
     * 3. 翻译结果写回缓存
     *
     * @param segments 待翻译的文本段落
     * @param target 目标语言
     * @param engine 翻译引擎
     * @param html 是否启用 HTML 翻译模式（仅对 MTranServer 有效）
     */
    private List<String> translateSegmentsInParallel(List<String> segments, String target, String engine,
                                                      boolean html) {
        List<String> results = new ArrayList<>(segments.size());
        List<int[]> indexesToTranslate = new ArrayList<>();

        // 1. 先尝试从缓存获取
        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            String cacheKey = CacheKeyUtil.buildCacheKey(segment, target, engine);
            String cached = cacheService.getCache(cacheKey);

            if (cached != null) {
                results.add(cached);
                log.debug("阅读器缓存命中：索引 {}", i);
            } else {
                results.add(null); // 占位，稍后填充
                indexesToTranslate.add(new int[]{i});
            }
        }

        // 2. 使用虚拟线程并发翻译缓存未命中的段落
        if (!indexesToTranslate.isEmpty()) {
            List<Runnable> tasks = new ArrayList<>();
            List<String> tempResults = new ArrayList<>(indexesToTranslate.size());

            for (int[] idx : indexesToTranslate) {
                int i = idx[0];
                tempResults.add(null);
            }

            for (int j = 0; j < indexesToTranslate.size(); j++) {
                int i = indexesToTranslate.get(j)[0];
                final int index = i;
                final int taskIndex = j;
                String segment = segments.get(i);

                tasks.add(() -> {
                    try {
                        // 阅读器模式使用 MTranServer 翻译
                        String rawJson = userLevelThrottledTranslationClient.translate(segment, target, engine,  html);
                        String translation = ExternalResponseUtil.extractDataField(rawJson);

                        if (translation != null && !translation.trim().isEmpty()) {
                            // 校验翻译结果是否有效
                            if (!isValidTranslation(segment, translation)) {
                                log.warn("阅读器翻译结果无效，使用原文兜底：索引 {}", index);
                                tempResults.set(taskIndex, segment);
                            } else if (shouldCache(segment, translation)) {
                                String cacheKey = CacheKeyUtil.buildCacheKey(segment, target, engine);
                                cacheService.putCache(cacheKey, segment, translation, "auto", target, engine);
                            } else {
                                log.debug("阅读器译文与原文一致，跳过缓存：索引 {}", index);
                            }
                            tempResults.set(taskIndex, translation);
                        } else {
                            // 翻译返回为空或失败，使用原文兜底
                            log.warn("翻译返回为空，使用原文兜底：索引 {}", index);
                            tempResults.set(taskIndex, segment);
                        }
                    } catch (Exception e) {
                        // 所有引擎都失败，使用原文兜底
                        log.error("翻译段落失败（使用原文兜底）：索引 {}, 错误：{}", index, e.getMessage());
                        tempResults.set(taskIndex, segment);
                    }
                });
            }

            // 使用虚拟线程并发执行所有翻译任务
            List<Thread> threads = new ArrayList<>();
            for (Runnable task : tasks) {
                Thread thread = Thread.startVirtualThread(task);
                threads.add(thread);
            }

            // 等待所有线程完成
            for (Thread thread : threads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("等待翻译线程被中断");
                }
            }

            // 填充翻译结果
            for (int j = 0; j < indexesToTranslate.size(); j++) {
                int i = indexesToTranslate.get(j)[0];
                String translation = tempResults.get(j);
                if (translation != null) {
                    results.set(i, translation);
                }
            }
        }

        // 3. 清理 null 值（理论上不会出现，因为失败时已用原文兜底）
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i) == null) {
                // 兜底：如果还有 null 值，使用原文填充
                results.set(i, segments.get(i));
                log.warn("发现未处理的 null 值，使用原文填充：索引 {}", i);
            }
        }

        return results;
    }

    /**
     * 网页翻译流式版本 - 并发翻译（虚拟线程 + 信号量限流）
     */
    public SseEmitter webpageTranslateStream(WebpageTranslateRequest req) {
        SseEmitter emitter = SseEmitterUtil.createSseEmitter(300000L); // 5 分钟超时

        Thread.startVirtualThread(() -> {
            try {
                String target = req.getTargetLang() == null ? DEFAULT_TARGET_LANG : req.getTargetLang();
                String engine = req.getEngine() == null ? DEFAULT_ENGINE : req.getEngine();
                var items = req.getTextRegistry();
                int totalCount = items.size();

                // 限制并发数，避免超过 DeepSeek 限流（10 req/s）和用户级别并发限制
                int maxConcurrency = 5;
                var semaphore = new java.util.concurrent.Semaphore(maxConcurrency);
                var barrier = new java.util.concurrent.CountDownLatch(totalCount);

                for (WebpageTranslateRequest.TextItem item : items) {
                    Thread.startVirtualThread(() -> {
                        try {
                            semaphore.acquire();
                            String id = item.getId() == null ? "" : item.getId();
                            String original = item.getOriginal() == null ? "" : item.getOriginal();
                            String cleanText = TextCleaningUtil.cleanText(original);

                            String cacheKey = CacheKeyUtil.buildCacheKey(cleanText, target, engine);
                            String extracted = cacheService.getCache(cacheKey);

                            if (extracted == null) {
                                // fastMode=true（默认）使用 MTranServer，fastMode=false（专家模式）使用 DeepSeek
                                boolean fastMode = req.getFastMode() == null || req.getFastMode();
                                String rawJson = userLevelThrottledTranslationClient.translate(cleanText, target, engine, false, fastMode);
                                extracted = ExternalResponseUtil.extractDataField(rawJson);
                                if (extracted != null && !extracted.trim().isEmpty()) {
                                    if (isValidTranslation(cleanText, extracted) && shouldCache(cleanText, extracted)) {
                                        cacheService.putCache(cacheKey, cleanText, extracted, "auto", target, engine);
                                    } else if (!isValidTranslation(cleanText, extracted)) {
                                        log.warn("网页翻译结果无效，使用原文兜底：ID={}", id);
                                        extracted = original;
                                    }
                                }
                            } else {
                                log.debug("网页翻译缓存命中：ID={}", id);
                            }

                            if (extracted == null) {
                                log.warn("翻译失败，使用原文兜底：ID={}", id);
                                extracted = original;
                            }

                            Map<String, Object> eventData = new HashMap<>();
                            eventData.put("textId", id);
                            eventData.put("original", original);
                            eventData.put("translation", extracted);
                            SseEmitterUtil.sendData(emitter, JSON.toJSONString(eventData));
                        } catch (Exception e) {
                            log.error("翻译失败 - ID: {}, 错误: {}", item.getId(), e.getMessage());
                            Map<String, Object> errorData = new HashMap<>();
                            errorData.put("textId", item.getId());
                            errorData.put("original", item.getOriginal());
                            errorData.put("translation", item.getOriginal());
                            SseEmitterUtil.sendData(emitter, JSON.toJSONString(errorData));
                        } finally {
                            semaphore.release();
                            barrier.countDown();
                        }
                    });
                }

                // 等待所有翻译完成
                barrier.await();

                SseEmitterUtil.sendDone(emitter);
                SseEmitterUtil.complete(emitter);

            } catch (Exception e) {
                log.error("网页翻译失败：{}", e.getMessage());
                SseEmitterUtil.sendError(emitter, "翻译失败：" + e.getMessage());
                SseEmitterUtil.complete(emitter);
            }
        });

        return emitter;
    }

    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        return cacheService.getCacheStats();
    }
}
