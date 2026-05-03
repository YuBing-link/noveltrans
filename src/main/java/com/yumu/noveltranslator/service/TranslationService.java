package com.yumu.noveltranslator.service;

import com.alibaba.fastjson2.JSON;
import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.enums.TranslationMode;
import com.yumu.noveltranslator.enums.TranslationMode;
import com.yumu.noveltranslator.service.pipeline.TranslationPipeline;
import com.yumu.noveltranslator.util.CacheKeyUtil;
import com.yumu.noveltranslator.util.SseEmitterUtil;
import com.yumu.noveltranslator.util.TextCleaningUtil;
import com.yumu.noveltranslator.util.TextSegmentationUtil;
import com.yumu.noveltranslator.service.pipeline.TranslationPipeline;
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

    /**
     * 语言代码 → 语言名称映射（用于团队翻译 prompt）
     * Python 侧 prompt 使用自然语言名称（如 "请将以下中文文本翻译为日文"）
     */
    private static final Map<String, String> LANGUAGE_NAME_MAP = Map.ofEntries(
            Map.entry("zh", "中文"),
            Map.entry("cn", "中文"),
            Map.entry("en", "英文"),
            Map.entry("ja", "日文"),
            Map.entry("jp", "日文"),
            Map.entry("ko", "韩文"),
            Map.entry("fr", "法文"),
            Map.entry("de", "德文"),
            Map.entry("es", "西班牙文"),
            Map.entry("pt", "葡萄牙文"),
            Map.entry("ru", "俄文"),
            Map.entry("it", "意大利文"),
            Map.entry("ar", "阿拉伯文"),
            Map.entry("th", "泰文"),
            Map.entry("vi", "越南文")
    );

    /**
     * 将语言代码转换为自然语言名称
     * 例如 "zh" → "中文", "ja" → "日文"
     */
    private static String toLanguageName(String code) {
        if (code == null || "auto".equalsIgnoreCase(code)) {
            return "未知";
        }
        return LANGUAGE_NAME_MAP.getOrDefault(code.toLowerCase(), code);
    }

    // 依赖注入
    private final UserLevelThrottledTranslationClient userLevelThrottledTranslationClient;
    private final TranslationCacheService cacheService;
    private final RagTranslationService ragTranslationService;
    private final EntityConsistencyService entityConsistencyService;
    private final TranslationPostProcessingService postProcessingService;
    private final TeamTranslationService teamTranslationService;
    private final QuotaService quotaService;
    private final com.yumu.noveltranslator.mapper.UserMapper userMapper;

    /**
     * 选中文本翻译
     * @param req 翻译请求
     * @return 翻译响应
     */
    public SelectionTranslateResponse selectionTranslate(SelectionTranslationRequest req) {
        String combined = req.getText();
        if (combined == null || combined.trim().isEmpty()) {
            return new SelectionTranslateResponse(false, req.getEngine(), "内容为空");
        }

        TranslationMode mode = EngineAliasRegistry.normalizeToMode(req.getEngine());
        String target = req.getTargetLang() == null ? DEFAULT_TARGET_LANG : req.getTargetLang();

        // 检查字符配额
        Long userId = com.yumu.noveltranslator.util.SecurityUtil.getCurrentUserId().orElse(null);
        if (userId != null) {
            User user = userMapper.selectById(userId);
            if (user != null) {
                String modeName = req.getMode() != null ? req.getMode() : "fast";
                if (!quotaService.tryConsumeChars(userId, user.getUserLevel(), combined.length(), modeName)) {
                    return new SelectionTranslateResponse(false, mode.getName(), "字符配额不足，请升级档位或等待下月重置");
                }
            }
        }

        try {
            TranslationPipeline pipeline = new TranslationPipeline(
                    cacheService, ragTranslationService, entityConsistencyService,
                    userLevelThrottledTranslationClient, postProcessingService, userId, null);
            String result = pipeline.executeFast(combined, target, mode);
            if (result == null || result.trim().isEmpty()) {
                if (userId != null) {
                    String modeName = req.getMode() != null ? req.getMode() : "fast";
                    quotaService.refundChars(userId, combined.length(), modeName);
                }
                return new SelectionTranslateResponse(false, mode.getName(), "翻译失败：返回结果为空");
            }
            // 净化翻译结果，防止恶意 HTML/脚本注入（XSS 防护）
            String sanitized = TextCleaningUtil.sanitizeHtml(result);
            return new SelectionTranslateResponse(true, mode.getName(), sanitized);
        } catch (Exception e) {
            log.error("选中文本翻译失败：{}", e.getMessage(), e);
            if (userId != null) {
                String modeName = req.getMode() != null ? req.getMode() : "fast";
                quotaService.refundChars(userId, combined.length(), modeName);
            }
            return new SelectionTranslateResponse(false, mode.getName(), "翻译失败：服务器内部错误");
        }
    }

    /**
     * 带缓存的翻译方法（四级管线）
     * 支持引擎降级：远程引擎失败时自动降级到本地引擎
     *
     * 缓存策略：
     * - 仅当翻译成功完成且译文与原文不一致时才加入缓存
     */
    private String translateWithCache(String text, String target, String engine) {
        Long userId = com.yumu.noveltranslator.util.SecurityUtil.getCurrentUserId().orElse(null);
        TranslationMode mode = EngineAliasRegistry.normalizeToMode(engine);
        TranslationPipeline pipeline = new TranslationPipeline(
                cacheService, ragTranslationService, entityConsistencyService,
                userLevelThrottledTranslationClient, postProcessingService, userId, null);

        String result = pipeline.execute(text, target, mode);

        if (result == null || result.trim().isEmpty()) {
            throw new RuntimeException("翻译服务返回错误或结果为空");
        }

        return result;
    }

    /**
     * 判断是否应该缓存翻译结果
     * @deprecated 使用 {@link TranslationPipeline#shouldCache(String, String)}
     */
    @Deprecated
    private boolean shouldCache(String original, String translated) {
        return TranslationPipeline.shouldCache(original, translated);
    }

    /**
     * 校验翻译结果是否有效
     * @deprecated 使用 {@link TranslationPipeline#isValidTranslation(String, String)}
     */
    @Deprecated
    private boolean isValidTranslation(String text, String result) {
        return TranslationPipeline.isValidTranslation(text, result);
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
        TranslationMode mode = EngineAliasRegistry.normalizeToMode(req.getEngine());

        // 检查字符配额
        Long userId = com.yumu.noveltranslator.util.SecurityUtil.getCurrentUserId().orElse(null);
        if (userId != null) {
            User user = userMapper.selectById(userId);
            if (user != null) {
                String modeName = req.getMode() != null ? req.getMode() : "fast";
                if (!quotaService.tryConsumeChars(userId, user.getUserLevel(), content.length(), modeName)) {
                    return new ReaderTranslateResponse(false, mode.getName(), "字符配额不足，请升级档位或等待下月重置");
                }
            }
        }

        // 文本分段
        List<String> segments;
        try {
            segments = TextSegmentationUtil.segmentByTextEngine(content, mode.getName());
        } catch (Exception e) {
            log.error("阅读器文本分段失败：{}", e.getMessage(), e);
            if (userId != null) {
                String modeName = req.getMode() != null ? req.getMode() : "fast";
                quotaService.refundChars(userId, content.length(), modeName);
            }
            return new ReaderTranslateResponse(false, mode.getName(), "文本分段失败：服务器内部错误");
        }
        if (segments.isEmpty()) {
            if (userId != null) {
                String modeName = req.getMode() != null ? req.getMode() : "fast";
                quotaService.refundChars(userId, content.length(), modeName);
            }
            return new ReaderTranslateResponse(false, mode.getName(), "段落为空");
        }

        // 并行翻译所有段落（复用全局线程池）
        // 阅读器模式默认使用 MTranServer，html=true
        try {
            List<String> translatedSegments = translateSegmentsInParallel(segments, target, mode, true);
            String rawResult = String.join("", translatedSegments);
            String combinedResult = TextCleaningUtil.sanitizeHtml(rawResult);
            log.info("阅读器翻译完成，原文长度：{}，译文长度：{}", content.length(), combinedResult.length());
            return new ReaderTranslateResponse(true, mode.getName(), combinedResult);
        } catch (Exception e) {
            log.error("阅读器翻译失败：{}", e.getMessage(), e);
            if (userId != null) {
                String modeName = req.getMode() != null ? req.getMode() : "fast";
                quotaService.refundChars(userId, content.length(), modeName);
            }
            return new ReaderTranslateResponse(false, mode.getName(), "翻译失败：服务器内部错误");
        }
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
    private List<String> translateSegmentsInParallel(List<String> segments, String target, TranslationMode mode,
                                                      boolean html) {
        List<String> results = new ArrayList<>(segments.size());
        List<int[]> indexesToTranslate = new ArrayList<>();

        // 1. 先尝试从缓存获取（分层缓存）
        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            String cacheKey = CacheKeyUtil.buildCacheKey(segment, target);
            String cached = cacheService.getCacheByMode(cacheKey, mode.getName());

            if (cached != null) {
                results.add(cached);
                log.debug("阅读器缓存命中：索引 {} mode={}", i, mode.getName());
            } else {
                results.add(null); // 占位，稍后填充
                indexesToTranslate.add(new int[]{i});
            }
        }

        // 2. 使用 Pipeline 快速模式并发翻译缓存未命中的段落
        if (!indexesToTranslate.isEmpty()) {
            Long userId = com.yumu.noveltranslator.util.SecurityUtil.getCurrentUserId().orElse(null);
            TranslationPipeline pipeline = new TranslationPipeline(
                    cacheService, ragTranslationService, entityConsistencyService,
                    userLevelThrottledTranslationClient, postProcessingService, userId, null);

            List<Runnable> tasks = new ArrayList<>();
            List<String> tempResults = new ArrayList<>(indexesToTranslate.size());

            for (int j = 0; j < indexesToTranslate.size(); j++) {
                tempResults.add(null);
            }

            for (int j = 0; j < indexesToTranslate.size(); j++) {
                int i = indexesToTranslate.get(j)[0];
                final int index = i;
                final int taskIndex = j;
                String segment = segments.get(i);

                tasks.add(() -> {
                    try {
                        String translation = pipeline.executeFast(segment, target, mode, html);
                        if (translation != null && !translation.trim().isEmpty()) {
                            tempResults.set(taskIndex, translation);
                        } else {
                            tempResults.set(taskIndex, segment);
                        }
                    } catch (Exception e) {
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

        // 3. 清理 null 值
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i) == null) {
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

        // 计算总字符数
        var items = req.getTextRegistry();
        int totalChars = items.stream()
                .mapToInt(item -> item.getOriginal() != null ? item.getOriginal().length() : 0)
                .sum();

        // 检查字符配额
        Long userId = com.yumu.noveltranslator.util.SecurityUtil.getCurrentUserId().orElse(null);
        String quotaMode = (req.getFastMode() != null && !req.getFastMode()) ? "expert" : "fast";

        if (userId != null) {
            User user = userMapper.selectById(userId);
            if (user != null) {
                if (!quotaService.tryConsumeChars(userId, user.getUserLevel(), totalChars, quotaMode)) {
                    SseEmitterUtil.sendError(emitter, "字符配额不足，请升级档位或等待下月重置");
                    SseEmitterUtil.complete(emitter);
                    return emitter;
                }
            }
        }

        Thread.startVirtualThread(() -> {
            try {
                String target = req.getTargetLang() == null ? DEFAULT_TARGET_LANG : req.getTargetLang();
                TranslationMode mode = EngineAliasRegistry.normalizeToMode(req.getEngine());
                int totalCount = items.size();

                log.info("[SSE流式翻译] 开始处理，文本数量: {}, 引擎: {}, fastMode: {}", totalCount, req.getEngine(), req.getFastMode());

                TranslationPipeline pipeline = new TranslationPipeline(
                        cacheService, ragTranslationService, entityConsistencyService,
                        userLevelThrottledTranslationClient, postProcessingService, userId, null);

                log.info("[SSE流式翻译] Pipeline 初始化完成");

                // 限制并发数，避免超过 DeepSeek 限流（10 req/s）和用户级别并发限制
                int maxConcurrency = 5;
                var semaphore = new java.util.concurrent.Semaphore(maxConcurrency);
                var barrier = new java.util.concurrent.CountDownLatch(totalCount);

                for (WebpageTranslateRequest.TextItem item : items) {
                    // 用 final 局部变量捕获当前迭代的 item，防止 lambda 闭包在多线程环境下共享循环变量
                    final WebpageTranslateRequest.TextItem currentItem = item;
                    Thread.startVirtualThread(() -> {
                        try {
                            semaphore.acquire();
                            String id = currentItem.getId() == null ? "" : currentItem.getId();
                            String original = currentItem.getOriginal() == null ? "" : currentItem.getOriginal();
                            String cleanText = TextCleaningUtil.cleanText(original);

                            // fastMode=true（默认）使用 MTranServer，fastMode=false（专家模式）使用 DeepSeek
                            boolean fastMode = req.getFastMode() == null || req.getFastMode();
                            String extracted = pipeline.executeFast(cleanText, target, mode, !fastMode);

                            if (extracted == null || extracted.trim().isEmpty()) {
                                log.warn("翻译失败，使用原文兜底：ID={}", id);
                                extracted = original;
                            }

                            Map<String, Object> eventData = new HashMap<>(3);
                            // 净化翻译结果，防止恶意 HTML/脚本注入（XSS 防护）
                            String sanitized = TextCleaningUtil.sanitizeHtml(extracted);
                            eventData.put("textId", id);
                            eventData.put("original", original);
                            eventData.put("translation", sanitized);
                            SseEmitterUtil.sendData(emitter, JSON.toJSONString(eventData));
                        } catch (Exception e) {
                            log.error("翻译失败 - ID: {}, 错误: {}", currentItem.getId(), e.getMessage());
                            Map<String, Object> errorData = new HashMap<>(3);
                            errorData.put("textId", currentItem.getId());
                            String orig = currentItem.getOriginal() == null ? "" : currentItem.getOriginal();
                            errorData.put("original", orig);
                            errorData.put("translation", TextCleaningUtil.sanitizeHtml(orig));
                            SseEmitterUtil.sendData(emitter, JSON.toJSONString(errorData));
                        } finally {
                            semaphore.release();
                            barrier.countDown();
                        }
                    });
                }

                // 心跳线程：每 30 秒发送一次心跳，使用注册表检测 emitter 是否仍然活跃
                String emitterId = SseEmitterUtil.registerEmitter(emitter);
                Thread.startVirtualThread(() -> {
                    while (SseEmitterUtil.isEmitterActive(emitterId)) {
                        try {
                            Thread.sleep(30_000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        SseEmitterUtil.sendHeartbeat(emitter);
                    }
                });

                log.info("[SSE流式翻译] 所有翻译线程已启动，等待完成 ({} 个文本)", totalCount);

                // 等待所有翻译完成
                barrier.await();

                log.info("[SSE流式翻译] 所有翻译已完成，发送 [DONE] 信号");

                SseEmitterUtil.sendDone(emitter);
                SseEmitterUtil.complete(emitter);

            } catch (Exception e) {
                // 翻译失败，触发心跳停止
                log.error("网页翻译失败：{}", e.getMessage(), e);
                if (userId != null) {
                    quotaService.refundChars(userId, totalChars, quotaMode);
                }
                SseEmitterUtil.sendError(emitter, "翻译失败：服务器内部错误");
                SseEmitterUtil.complete(emitter);
            }
        });

        return emitter;
    }

    /**
     * 文本流式翻译（SSE）— 适用于长文本的单段流式输出
     * 按段落分段，逐段 SSE 输出
     */
    public SseEmitter streamTextTranslate(SelectionTranslationRequest req) {
        SseEmitter emitter = SseEmitterUtil.createSseEmitter(300000L);

        String text = req.getText();
        if (text == null || text.trim().isEmpty()) {
            SseEmitterUtil.sendError(emitter, "文本不能为空");
            SseEmitterUtil.complete(emitter);
            return emitter;
        }

        TranslationMode mode = EngineAliasRegistry.normalizeToMode(req.getEngine());
        String target = req.getTargetLang() == null ? DEFAULT_TARGET_LANG : req.getTargetLang();
        String modeString = req.getMode() != null ? req.getMode() : "fast";

        // 检查字符配额
        Long userId = com.yumu.noveltranslator.util.SecurityUtil.getCurrentUserId().orElse(null);
        if (userId != null) {
            User user = userMapper.selectById(userId);
            if (user != null) {
                if (!quotaService.tryConsumeChars(userId, user.getUserLevel(), text.length(), modeString)) {
                    SseEmitterUtil.sendError(emitter, "字符配额不足，请升级档位或等待下月重置");
                    SseEmitterUtil.complete(emitter);
                    return emitter;
                }
            }
        }

        Thread.startVirtualThread(() -> {
            try {
                List<String> segments = TextSegmentationUtil.segmentByTextEngine(text, mode.getName());
                if (segments.isEmpty()) {
                    segments = List.of(text);
                }

                TranslationPipeline pipeline = new TranslationPipeline(
                        cacheService, ragTranslationService, entityConsistencyService,
                        userLevelThrottledTranslationClient, postProcessingService, userId, null);

                // 先用 \n\n+ 将全文拆为逻辑段落
                String[] logicalParagraphs = text.split("\n\n+");
                log.info("流式翻译: 全文拆分为 {} 个逻辑段落", logicalParagraphs.length);

                StringBuilder fullResult = new StringBuilder();

                for (int i = 0; i < logicalParagraphs.length; i++) {
                    SseEmitterUtil.sendHeartbeat(emitter);

                    String para = logicalParagraphs[i];
                    if (para.isBlank()) {
                        fullResult.append("\n\n");
                        continue;
                    }

                    // 段落内按 \n 逐行翻译，保持原文单行换行结构
                    String[] lines = para.split("\n", -1);
                    StringBuilder paraResult = new StringBuilder();

                    log.info("[流式翻译] 段落包含 {} 行", lines.length);

                    for (int j = 0; j < lines.length; j++) {
                        String line = lines[j];
                        if (line.isEmpty()) {
                            paraResult.append("\n");
                            continue;
                        }

                        String translated;
                        if ("fast".equals(modeString)) {
                            translated = pipeline.executeFast(line, target, mode);
                        } else {
                            translated = pipeline.execute(line, target, mode);
                        }

                        if (translated == null || translated.isBlank()) {
                            translated = line;
                        }

                        if (j > 0) paraResult.append("\n");
                        paraResult.append(translated);
                    }

                    log.info("[流式翻译] 段落结果行数: {} (与原文一致)", paraResult.toString().split("\n", -1).length);

                    if (i > 0) fullResult.append("\n\n");
                    fullResult.append(paraResult);

                    // 流式发送当前段落结果（含段间分隔符）
                    // §NL§ = 单换行, §NL§§NL§ = 段落分隔
                    String chunk = TextCleaningUtil.sanitizeHtml(paraResult.toString()).replace("\n", "§NL§");
                    if (i > 0) {
                        chunk = "§NL§§NL§" + chunk;
                    }
                    SseEmitterUtil.sendData(emitter, chunk);
                }

                SseEmitterUtil.sendDone(emitter);
                SseEmitterUtil.complete(emitter);

            } catch (Exception e) {
                log.error("文本流式翻译失败：{}", e.getMessage(), e);
                Long currentUserId = userId;
                if (currentUserId != null) {
                    quotaService.refundChars(currentUserId, text.length(), modeString);
                }
                SseEmitterUtil.sendError(emitter, "翻译失败：服务器内部错误");
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
