package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.Document;
import com.yumu.noveltranslator.entity.TranslationHistory;
import com.yumu.noveltranslator.entity.TranslationTask;
import com.yumu.noveltranslator.enums.TranslationStatus;
import com.yumu.noveltranslator.mapper.DocumentMapper;
import com.yumu.noveltranslator.mapper.TranslationHistoryMapper;
import com.yumu.noveltranslator.mapper.TranslationTaskMapper;
import com.yumu.noveltranslator.service.state.TranslationStateMachine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yumu.noveltranslator.util.SseEmitterUtil;
import com.yumu.noveltranslator.util.CacheKeyUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.scheduling.annotation.Scheduled;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 翻译任务服务
 */
@Service
@RequiredArgsConstructor
public class TranslationTaskService {
    private static final Logger log = LoggerFactory.getLogger(TranslationTaskService.class);

    private final TranslationTaskMapper translationTaskMapper;
    private final TranslationHistoryMapper translationHistoryMapper;
    private final DocumentMapper documentMapper;
    private final TranslationStateMachine stateMachine;
    private final UserLevelThrottledTranslationClient userLevelThrottledTranslationClient;
    private final TranslationCacheService cacheService;
    private final RagTranslationService ragTranslationService;
    private final EntityConsistencyService entityConsistencyService;
    private final TranslationPostProcessingService postProcessingService;

    /**
     * 创建文档翻译任务
     */
    public TranslationTask createDocumentTask(Long userId, Document doc) {
        String taskId = generateTaskId();

        TranslationTask task = new TranslationTask();
        task.setTaskId(taskId);
        task.setUserId(userId);
        task.setType("document");
        task.setDocumentId(doc.getId());
        task.setSourceLang(doc.getSourceLang());
        task.setTargetLang(doc.getTargetLang());
        task.setMode(doc.getMode());
        task.setEngine("google");
        task.setStatus(TranslationStatus.PENDING.getValue());
        task.setProgress(0);
        task.setCreateTime(LocalDateTime.now());

        translationTaskMapper.insert(task);

        // 关联文档与任务
        doc.setTaskId(taskId);
        documentMapper.updateById(doc);

        return task;
    }

    /**
     * 启动文档翻译
     */
    public void startDocumentTranslation(TranslationTask task, Document doc) {
        if (task == null || doc == null) {
            return;
        }
        if (!TranslationStatus.PENDING.getValue().equals(task.getStatus()) && !TranslationStatus.FAILED.getValue().equals(task.getStatus())) {
            return;
        }

        // 更新文档状态为处理中
        doc.setStatus(TranslationStatus.PROCESSING.getValue());
        doc.setUpdateTime(LocalDateTime.now());
        documentMapper.updateById(doc);

        updateTaskProgress(task, TranslationStatus.PROCESSING, 10, null);

        executeDocumentTranslation(task, doc);
    }

    /**
     * 异步执行文档翻译（使用虚拟线程）
     * 接入三级缓存 + RAG + 实体一致性
     */
    private void executeDocumentTranslation(TranslationTask task, Document doc) {
        Thread.startVirtualThread(() -> {
            try {
                // 读取文件内容
                String content = readDocumentContent(doc.getPath(), doc.getFileType());
                if (content == null || content.trim().isEmpty()) {
                    updateTaskProgress(task, TranslationStatus.FAILED, 0, "文件内容为空");
                    return;
                }

                // 按段落分割翻译（每 3000 字符一批）
                updateTaskProgress(task, TranslationStatus.PROCESSING, 20, null);
                StringBuilder translatedContent = new StringBuilder();
                String[] paragraphs = content.split("(?<=\n)");
                int total = paragraphs.length;
                int batchStart = 0;
                String targetLang = task.getTargetLang();
                String engine = task.getEngine();
                Long userId = task.getUserId();
                String docId = "doc_" + doc.getId();

                while (batchStart < total) {
                    StringBuilder batch = new StringBuilder();
                    int batchEnd = batchStart;
                    while (batchEnd < total && batch.length() + paragraphs[batchEnd].length() <= 1500) {
                        batch.append(paragraphs[batchEnd]);
                        batchEnd++;
                    }

                    if (batch.length() == 0) {
                        batch.append(paragraphs[batchStart]);
                        batchEnd = batchStart + 1;
                    }

                    String batchText = batch.toString();
                    String translated;
                    try {
                        if ("fast".equals(doc.getMode())) {
                            translated = translateBatchFastMode(batchText, targetLang);
                        } else {
                            translated = translateBatchWithCacheAndRag(batchText, targetLang, engine, userId, docId);
                        }
                    } catch (Exception e) {
                        log.warn("翻译批次失败 [{}/{}]，保留原文: {}", batchStart, total, e.getMessage());
                        translated = batchText; // fallback to original text
                    }
                    if (translated != null && !translated.isEmpty()) {
                        translatedContent.append(translated);
                    }
                    batchStart = batchEnd;

                    int progress = 20 + (int) ((batchStart * 80.0) / total);
                    updateTaskProgress(task, TranslationStatus.PROCESSING, progress, null);
                }

                // 保存翻译结果到文件
                String translatedPath = buildTranslatedPath(doc.getPath());
                Files.write(Paths.get(translatedPath), translatedContent.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

                // 更新文档状态为已完成
                doc.setStatus(TranslationStatus.COMPLETED.getValue());
                doc.setCompletedTime(LocalDateTime.now());
                doc.setUpdateTime(LocalDateTime.now());
                documentMapper.updateById(doc);

                updateTaskProgress(task, TranslationStatus.COMPLETED, 100, null);
                saveTranslationHistory(task, content, translatedContent.toString());

            } catch (Exception e) {
                log.error("文档翻译失败: {}", e.getMessage(), e);
                updateTaskProgress(task, TranslationStatus.FAILED, 0, "翻译失败：" + e.getMessage());
            }
        });
    }

    /**
     * 带三级缓存+RAG+实体一致性的批量翻译
     */
    private String translateBatchWithCacheAndRag(String text, String targetLang, String engine, Long userId, String docId) {
        log.info("[翻译批次] textLength={}, targetLang={}, engine={}", text.length(), targetLang, engine);
        String cacheKey = CacheKeyUtil.buildCacheKey(text, targetLang, engine);

        // 1. L1/L2/L3 缓存查询
        String cached = cacheService.getCache(cacheKey);
        if (cached != null) {
            log.info("[翻译批次] 缓存命中");
            log.debug("文档翻译缓存命中");
            return cached;
        }
        log.info("[翻译批次] 缓存未命中，继续");

        // 2. RAG 查询
        RagTranslationResponse ragResult = ragTranslationService.searchSimilar(text, targetLang, engine);
        if (ragResult.isDirectHit()) {
            log.info("文档翻译 RAG 直接命中, similarity={}", ragResult.getSimilarity());
            String result = postProcessingService.fixUntranslatedChinese(text, ragResult.getTranslation(), targetLang, engine);
            cacheService.putCache(cacheKey, text, result, "auto", targetLang, engine);
            return result;
        }

        // 3. 长文本使用实体一致性（术语表+占位符保护）
        boolean useConsistency = entityConsistencyService.shouldUseConsistency(text);
        log.info("[翻译批次] 是否使用一致性={}", useConsistency);
        if (useConsistency) {
            log.info("[翻译批次] 进入实体一致性翻译");
            ConsistencyTranslationResult consistencyResult =
                    entityConsistencyService.translateWithConsistency(text, targetLang, engine, userId, docId);
            log.info("[翻译批次] 一致性结果: applied={}, textNull={}",
                    consistencyResult.isConsistencyApplied(),
                    consistencyResult.getTranslatedText() == null);
            if (consistencyResult.isConsistencyApplied() && consistencyResult.getTranslatedText() != null) {
                String result = postProcessingService.fixUntranslatedChinese(text, consistencyResult.getTranslatedText(), targetLang, engine);
                if (shouldCacheResult(text, result)) {
                    cacheService.putCache(cacheKey, text, result, "auto", targetLang, engine);
                }
                ragTranslationService.storeTranslationMemory(text, result, targetLang, engine);
                return result;
            }
        }

        // 4. 兜底：直接调用翻译服务
        log.info("[翻译批次] 调用翻译客户端");
        String result = userLevelThrottledTranslationClient.translate(text, targetLang, engine, false);
        log.info("[翻译批次] 翻译客户端返回: length={}", result != null ? result.length() : "null");
        String translation = extractTranslatedContent(result);
        log.info("翻译结果: translation={}, result_length={}", translation == null ? "null" : "not-null", result != null ? result.length() : 0);
        if (translation == null || translation.isBlank()) {
            log.error("翻译服务返回空响应，原文长度：{}，响应内容前100字符：{}", text.length(), result != null && result.length() > 100 ? result.substring(0, 100) : result);
            throw new RuntimeException("翻译服务返回空响应，原文长度：" + text.length());
        }
        // 翻译后处理：检测并补救残留中文
        translation = postProcessingService.fixUntranslatedChinese(text, translation, targetLang, engine);
        if (shouldCacheResult(text, translation)) {
            cacheService.putCache(cacheKey, text, translation, "auto", targetLang, engine);
            ragTranslationService.storeTranslationMemory(text, translation, targetLang, engine);
        }
        return translation;
    }

    /**
     * 快速模式批量翻译（直连 MTranServer，跳过 RAG/一致性/轮询）
     */
    private String translateBatchFastMode(String text, String targetLang) {
        String cacheKey = CacheKeyUtil.buildCacheKey(text, targetLang, "mtran");
        String cached = cacheService.getCache(cacheKey);
        if (cached != null) {
            log.info("[快速模式] 缓存命中");
            return cached;
        }

        log.info("[快速模式] 直连 MTranServer, textLength={}", text.length());
        String result = userLevelThrottledTranslationClient.translate(text, targetLang, "google", false, true);
        String translation = extractTranslatedContent(result);
        if (translation == null || translation.isBlank()) {
            log.warn("[快速模式] 翻译结果为空，保留原文");
            translation = text;
        }
        log.info("[快速模式] 翻译完成, translationLength={}", translation.length());
        if (shouldCacheResult(text, translation)) {
            cacheService.putCache(cacheKey, text, translation, "auto", targetLang, "mtran");
        }
        return translation;
    }

    /**
     * 判断是否应该缓存翻译结果
     */
    private boolean shouldCacheResult(String original, String translated) {
        if (original == null || translated == null) return false;
        String o = original.trim();
        String t = translated.trim();
        return !o.equals(t) && !o.equalsIgnoreCase(t);
    }

    /**
     * 构建翻译文件路径（在扩展名前插入 _translated）
     */
    private String buildTranslatedPath(String originalPath) {
        int lastDot = originalPath.lastIndexOf('.');
        if (lastDot > 0) {
            return originalPath.substring(0, lastDot) + "_translated" + originalPath.substring(lastDot);
        }
        return originalPath + "_translated";
    }

    /**
     * 读取文档内容
     */
    private String readDocumentContent(String filePath, String fileType) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IOException("文件不存在: " + filePath);
        }

        if ("txt".equals(fileType)) {
            return Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
        } else {
            // DOCX/EPUB/PDF 暂不支持，返回原始文本
            throw new IOException("暂不支持 " + fileType.toUpperCase() + " 格式，仅支持 TXT 文件");
        }
    }

    @Transactional
    protected void updateTaskProgress(TranslationTask task, TranslationStatus status, int progress, String errorMessage) {
        // 如果任务已被取消，不再更新
        TranslationTask current = translationTaskMapper.findByTaskId(task.getTaskId());
        if (current != null && TranslationStatus.FAILED.getValue().equals(current.getStatus()) && "用户取消任务".equals(current.getErrorMessage())) {
            return;
        }
        task.setStatus(status.getValue());
        task.setProgress(progress);
        if (status == TranslationStatus.COMPLETED) {
            task.setCompletedTime(LocalDateTime.now());
        }
        if (errorMessage != null) {
            task.setErrorMessage(errorMessage);
        }
        translationTaskMapper.updateById(task);
    }

    /**
     * 保存翻译历史
     */
    private void saveTranslationHistory(TranslationTask task, String sourceText, String targetText) {
        TranslationHistory history = new TranslationHistory();
        history.setUserId(task.getUserId());
        history.setTaskId(task.getTaskId());
        history.setType(task.getType());
        history.setDocumentId(task.getDocumentId());
        history.setSourceLang(task.getSourceLang());
        history.setTargetLang(task.getTargetLang());
        history.setSourceText(sourceText != null && sourceText.length() > 500
                ? sourceText.substring(0, 500)
                : sourceText);

        // 解析翻译响应，提取实际翻译内容
        String translatedContent = extractTranslatedContent(targetText);
        history.setTargetText(translatedContent != null && translatedContent.length() > 500
                ? translatedContent.substring(0, 500)
                : translatedContent);
        history.setEngine(task.getEngine());
        history.setCreateTime(LocalDateTime.now());

        translationHistoryMapper.insert(history);
    }

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * 从翻译响应中提取实际翻译内容
     * 支持格式: {"translatedContent":"..."} 或 {"code":200,"data":"..."}
     * 使用 JSON 解析自动处理 \n 等转义字符
     */
    private String extractTranslatedContent(String response) {
        if (response == null || response.isEmpty()) {
            return response;
        }
        try {
            if (response.startsWith("{")) {
                JsonNode node = JSON_MAPPER.readTree(response);

                JsonNode contentNode = node.get("translatedContent");
                if (contentNode != null && contentNode.isTextual()) {
                    return contentNode.asText();
                }

                JsonNode dataNode = node.get("data");
                if (dataNode != null && dataNode.isTextual()) {
                    return dataNode.asText();
                }
            }
        } catch (Exception e) {
            log.warn("解析翻译响应失败: {}", e.getMessage());
            return response; // fallback to raw response instead of null
        }
        return response;
    }

    /**
     * 根据任务 ID 获取任务
     */
    public TranslationTask getTaskByTaskId(String taskId) {
        return translationTaskMapper.findByTaskId(taskId);
    }

    /**
     * 根据文档 ID 获取任务
     */
    public TranslationTask getTaskByDocumentId(Long docId) {
        return translationTaskMapper.findByDocumentId(docId);
    }

    /**
     * 取消翻译任务
     */
    public boolean cancelTask(String taskId, Long userId) {
        TranslationTask task = translationTaskMapper.findByTaskId(taskId);
        if (task != null && task.getUserId().equals(userId)) {
            if (TranslationStatus.PENDING.getValue().equals(task.getStatus()) || TranslationStatus.PROCESSING.getValue().equals(task.getStatus())) {
                task.setStatus(TranslationStatus.FAILED.getValue());
                task.setErrorMessage("用户取消任务");
                translationTaskMapper.updateById(task);
                // 同步更新 Document 表状态
                if (task.getDocumentId() != null) {
                    Document doc = documentMapper.selectById(task.getDocumentId());
                    if (doc != null) {
                        doc.setStatus(TranslationStatus.FAILED.getValue());
                        doc.setErrorMessage("用户取消任务");
                        doc.setUpdateTime(LocalDateTime.now());
                        documentMapper.updateById(doc);
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * 删除翻译历史记录（逻辑删除）
     */
    public boolean deleteHistory(String taskId, Long userId) {
        TranslationHistory history = translationHistoryMapper.findByTaskId(taskId);
        if (history != null && history.getUserId().equals(userId)) {
            translationHistoryMapper.deleteById(history.getId());
            return true;
        }
        return false;
    }

    /**
     * 获取翻译结果
     */
    public TranslationResultResponse getTranslationResult(String taskId) {
        TranslationTask task = translationTaskMapper.findByTaskId(taskId);
        if (task == null) {
            return null;
        }

        TranslationResultResponse response = new TranslationResultResponse();
        response.setTaskId(taskId);
        response.setStatus(task.getStatus());
        response.setSourceLang(task.getSourceLang());
        response.setTargetLang(task.getTargetLang());

        String status = task.getStatus();
        if (TranslationStatus.COMPLETED.getValue().equals(status) || TranslationStatus.PROCESSING.getValue().equals(status) || TranslationStatus.PENDING.getValue().equals(status) || TranslationStatus.FAILED.getValue().equals(status)) {
            if (TranslationStatus.COMPLETED.getValue().equals(status)) {
                response.setCompletedTime(task.getCompletedTime() != null
                        ? task.getCompletedTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        : null);
            }

            // 如果是文本翻译，从历史中获取结果
            if ("text".equals(task.getType())) {
                TranslationHistory history = translationHistoryMapper.findByTaskId(task.getTaskId());
                if (history != null) {
                    response.setTranslatedText(history.getTargetText());
                    response.setSourceContent(history.getSourceText());
                }
            } else if ("document".equals(task.getType())) {
                // 文档翻译，读取文件内容
                if (task.getDocumentId() != null) {
                    Document doc = documentMapper.findById(task.getDocumentId());
                    if (doc != null) {
                        response.setTranslatedFilePath(doc.getPath());
                        // 读取原文内容（任何状态都尝试读取）
                        try {
                            Path path = Paths.get(doc.getPath());
                            if (Files.exists(path)) {
                                String content = Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
                                response.setSourceContent(content);
                            }
                        } catch (Exception e) {
                            log.warn("读取原文文件失败: {}", e.getMessage());
                        }
                        // 已完成的文档翻译，读取翻译文件内容
                        if (TranslationStatus.COMPLETED.getValue().equals(status)) {
                            String translatedPath = buildTranslatedPath(doc.getPath());
                            try {
                                Path path = Paths.get(translatedPath);
                                if (Files.exists(path)) {
                                    // 文件内容已经是纯文本翻译结果，不要再次调用 extractTranslatedContent
                                    response.setTranslatedText(Files.readString(path, java.nio.charset.StandardCharsets.UTF_8));
                                }
                            } catch (Exception e) {
                                log.warn("读取翻译文件失败: {}", e.getMessage());
                            }
                        }
                    }
                }
            }
        }

        return response;
    }

    /**
     * 下载翻译结果（返回文件路径）
     */
    public String getDownloadPath(String taskId, Long userId) {
        TranslationTask task = translationTaskMapper.findByTaskId(taskId);
        if (task == null || !task.getUserId().equals(userId)) {
            return null;
        }

        if (TranslationStatus.COMPLETED.getValue().equals(task.getStatus()) && "document".equals(task.getType())) {
            if (task.getDocumentId() != null) {
                Document doc = documentMapper.findById(task.getDocumentId());
                if (doc != null && Files.exists(Paths.get(doc.getPath()))) {
                    return doc.getPath();
                }
            }
        }

        return null;
    }

    /**
     * 获取翻译历史列表（包含进行中的任务）
     */
    public List<TranslationHistory> getTranslationHistory(Long userId, int page, int pageSize, String type) {
        int offset = (page - 1) * pageSize;

        // 查询进行中的任务（pending/processing），这些任务可能还没有历史记录
        List<TranslationHistory> histories = new ArrayList<>();

        // 先查询进行中的任务
        List<TranslationTask> inProgressTasks = translationTaskMapper.findByUserIdAndStatus(userId, offset, pageSize);
        for (TranslationTask task : inProgressTasks) {
            TranslationHistory history = new TranslationHistory();
            history.setUserId(task.getUserId());
            history.setTaskId(task.getTaskId());
            history.setType(task.getType());
            history.setDocumentId(task.getDocumentId());
            history.setSourceLang(task.getSourceLang());
            history.setTargetLang(task.getTargetLang());
            history.setSourceText(null);
            history.setTargetText(null);
            history.setEngine(task.getEngine());
            history.setCreateTime(task.getCreateTime());
            histories.add(history);
        }

        // 再查询已完成的历史记录
        List<TranslationHistory> completedHistories = translationHistoryMapper.findByUserId(userId, offset, pageSize);
        histories.addAll(completedHistories);

        // 去重（按taskId）
        histories = histories.stream()
                .collect(java.util.stream.Collectors.toMap(
                        TranslationHistory::getTaskId,
                        h -> h,
                        (existing, replacement) -> existing
                ))
                .values()
                .stream()
                .sorted((a, b) -> {
                    if (a.getCreateTime() == null) return 1;
                    if (b.getCreateTime() == null) return -1;
                    return b.getCreateTime().compareTo(a.getCreateTime());
                })
                .limit(pageSize)
                .toList();

        if (type != null && !"all".equals(type)) {
            return histories.stream()
                    .filter(h -> type.equals(h.getType()))
                    .toList();
        }

        return histories;
    }

    /**
     * 统计翻译历史总数
     */
    public int countTranslationHistory(Long userId) {
        return translationHistoryMapper.countByUserId(userId);
    }

    /**
     * 生成任务 ID
     */
    private String generateTaskId() {
        return "task_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 转换为 TaskStatusResponse
     */
    public TaskStatusResponse toTaskStatusResponse(TranslationTask task) {
        if (task == null) {
            return null;
        }

        TaskStatusResponse response = new TaskStatusResponse();
        response.setTaskId(task.getTaskId());
        response.setType(task.getType());
        response.setStatus(task.getStatus());
        response.setProgress(task.getProgress());
        response.setSourceLang(task.getSourceLang());
        response.setTargetLang(task.getTargetLang());
        response.setCreateTime(task.getCreateTime() != null
                ? task.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : null);
        response.setCompletedTime(task.getCompletedTime() != null
                ? task.getCompletedTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : null);
        response.setErrorMessage(task.getErrorMessage());

        return response;
    }

    /**
     * 转换为 TranslationHistoryResponse
     */
    public TranslationHistoryResponse toHistoryResponse(TranslationHistory history) {
        if (history == null) {
            return null;
        }

        TranslationHistoryResponse response = new TranslationHistoryResponse();
        response.setId(history.getId());
        response.setTaskId(history.getTaskId());
        response.setType(history.getType());
        response.setSourceLang(history.getSourceLang());
        response.setTargetLang(history.getTargetLang());
        response.setSourceTextPreview(history.getSourceText() != null
                ? history.getSourceText().substring(0, Math.min(100, history.getSourceText().length()))
                : null);
        response.setTargetTextPreview(history.getTargetText() != null
                ? history.getTargetText().substring(0, Math.min(100, history.getTargetText().length()))
                : null);
        response.setCreateTime(history.getCreateTime() != null
                ? history.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : null);

        // 查询关联任务状态
        if (history.getTaskId() != null) {
            TranslationTask task = translationTaskMapper.findByTaskId(history.getTaskId());
            response.setStatus(task != null ? task.getStatus() : TranslationStatus.COMPLETED.getValue());
        } else {
            response.setStatus(TranslationStatus.COMPLETED.getValue());
        }

        // 查询关联文档名称
        if (history.getDocumentId() != null) {
            Document doc = documentMapper.findById(history.getDocumentId());
            if (doc != null) {
                response.setDocumentName(doc.getName());
            }
        }

        // fallback：如果documentName为空，尝试从任务关联的文档获取
        if (response.getDocumentName() == null && history.getTaskId() != null) {
            TranslationTask task = translationTaskMapper.findByTaskId(history.getTaskId());
            if (task != null && task.getDocumentId() != null) {
                Document doc = documentMapper.findById(task.getDocumentId());
                if (doc != null) {
                    response.setDocumentName(doc.getName());
                }
            }
        }

        // 最终fallback：使用任务类型作为名称
        if (response.getDocumentName() == null) {
            response.setDocumentName("document".equals(history.getType()) ? "文档翻译" : "文本翻译");
        }

        return response;
    }

    /**
     * SSE 流式文档翻译（基于已上传文档）
     * 读取磁盘文件 → 分段翻译 → 逐段推送 SSE → 更新文档状态
     */
    public SseEmitter streamTranslateDocumentById(Long docId, String targetLang, String mode) {
        SseEmitter emitter = SseEmitterUtil.createSseEmitter(300_000L);
        ObjectMapper mapper = new ObjectMapper();

        Thread.startVirtualThread(() -> {
            try {
                Document doc = documentMapper.findById(docId);
                if (doc == null) {
                    SseEmitterUtil.sendError(emitter, "文档不存在");
                    SseEmitterUtil.complete(emitter);
                    return;
                }

                // 创建翻译任务
                TranslationTask task = createDocumentTask(doc.getUserId(), doc);
                doc.setStatus(TranslationStatus.PROCESSING.getValue());
                doc.setUpdateTime(LocalDateTime.now());
                documentMapper.updateById(doc);
                updateTaskProgress(task, TranslationStatus.PROCESSING, 10, null);

                // 读取文件内容
                String content = readDocumentContent(doc.getPath(), doc.getFileType());
                if (content == null || content.trim().isEmpty()) {
                    updateTaskProgress(task, TranslationStatus.FAILED, 0, "文件内容为空");
                    SseEmitterUtil.sendError(emitter, "文件内容为空");
                    SseEmitterUtil.complete(emitter);
                    return;
                }

                // 按段落分割翻译
                String[] paragraphs = content.split("(?<=\n)");
                int total = paragraphs.length;
                StringBuilder translatedContent = new StringBuilder();

                for (int i = 0; i < total; i++) {
                    // 检查任务是否已被取消
                    TranslationTask currentTask = translationTaskMapper.findByTaskId(task.getTaskId());
                    if (currentTask != null && TranslationStatus.FAILED.getValue().equals(currentTask.getStatus())) {
                        log.info("翻译任务已被取消，提前退出 [taskId={}]", task.getTaskId());
                        SseEmitterUtil.complete(emitter);
                        return;
                    }

                    String paragraph = paragraphs[i];
                    if (paragraph.trim().isEmpty()) {
                        translatedContent.append(paragraph);
                        continue;
                    }

                    String textId = "seg_" + i;
                    try {
                        String result;
                        if ("expert".equals(mode)) {
                            result = userLevelThrottledTranslationClient.translateWithPython(
                                    paragraph, targetLang, "google");
                        } else {
                            result = userLevelThrottledTranslationClient.translate(
                                    paragraph, targetLang, "google", false, true);
                        }
                        String translation = extractTranslatedContent(result);
                        if (translation == null || translation.isEmpty()) {
                            log.warn("翻译结果为空，保留原文");
                            translation = paragraph;
                        }
                        // 补回原文中的换行符以保持格式对齐
                        if (!translation.endsWith("\n") && !translation.endsWith("\r")) {
                            if (paragraph.endsWith("\r\n")) {
                                translation += "\r\n";
                            } else if (paragraph.endsWith("\n")) {
                                translation += "\n";
                            } else if (paragraph.endsWith("\r")) {
                                translation += "\r";
                            }
                        }
                        translatedContent.append(translation);

                        Map<String, Object> eventData = new HashMap<>();
                        eventData.put("textId", textId);
                        eventData.put("original", paragraph);
                        eventData.put("translation", translation);
                        eventData.put("progress", (int) (((i + 1) * 100.0) / total));

                        SseEmitterUtil.sendData(emitter, mapper.writeValueAsString(eventData));
                    } catch (Exception e) {
                        log.warn("段落翻译失败 [{}]: {}", i, e.getMessage());
                        translatedContent.append(paragraph);
                        Map<String, Object> errorData = new HashMap<>();
                        errorData.put("textId", textId);
                        errorData.put("original", paragraph);
                        errorData.put("translation", paragraph);
                        errorData.put("error", true);
                        errorData.put("progress", (int) (((i + 1) * 100.0) / total));
                        SseEmitterUtil.sendData(emitter, mapper.writeValueAsString(errorData));
                    }

                    int progress = 20 + (int) (((i + 1) * 80.0) / total);
                    updateTaskProgress(task, TranslationStatus.PROCESSING, progress, null);
                }

                // 保存翻译结果到文件
                String translatedPath = buildTranslatedPath(doc.getPath());
                Files.write(Paths.get(translatedPath), translatedContent.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

                // 更新文档和任务状态
                doc.setStatus(TranslationStatus.COMPLETED.getValue());
                doc.setCompletedTime(LocalDateTime.now());
                doc.setUpdateTime(LocalDateTime.now());
                documentMapper.updateById(doc);

                updateTaskProgress(task, TranslationStatus.COMPLETED, 100, null);
                saveTranslationHistory(task, content, translatedContent.toString());

                SseEmitterUtil.sendDone(emitter);
                SseEmitterUtil.complete(emitter);

            } catch (Exception e) {
                log.error("流式文档翻译失败: {}", e.getMessage(), e);
                SseEmitterUtil.sendError(emitter, "翻译失败：" + e.getMessage());
                SseEmitterUtil.complete(emitter);
            }
        });

        return emitter;
    }

    /**
     * SSE 流式文档翻译
     * 读取文件 → 分段翻译 → 逐段推送 SSE
     */
    public SseEmitter streamTranslateDocument(MultipartFile file, String sourceLang, String targetLang, String mode) {
        SseEmitter emitter = SseEmitterUtil.createSseEmitter(300_000L);
        ObjectMapper mapper = new ObjectMapper();

        Thread.startVirtualThread(() -> {
            try {
                String fileName = file.getOriginalFilename();
                String fileType = fileName != null ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase() : "";
                String content = readMultipartFileContent(file, fileType);
                if (content == null || content.trim().isEmpty()) {
                    SseEmitterUtil.sendError(emitter, "文件内容为空");
                    SseEmitterUtil.complete(emitter);
                    return;
                }

                String[] paragraphs = content.split("(?<=\n)");
                int total = paragraphs.length;
                int translated = 0;

                for (int i = 0; i < total; i++) {
                    String paragraph = paragraphs[i];
                    if (paragraph.trim().isEmpty()) {
                        translated++;
                        continue;
                    }

                    String textId = "seg_" + i;
                    try {
                        String result;
                        if ("expert".equals(mode)) {
                            result = userLevelThrottledTranslationClient.translateWithPython(
                                    paragraph, targetLang, "google");
                        } else {
                            result = userLevelThrottledTranslationClient.translate(
                                    paragraph, targetLang, "google", false, true);
                        }

                        // 提取实际翻译内容
                        String translation = extractTranslatedContent(result);
                        if (translation == null || translation.isEmpty()) {
                            log.warn("翻译结果为空，保留原文");
                            translation = paragraph;
                        }
                        // 补回原文中的换行符以保持格式对齐
                        if (!translation.endsWith("\n") && !translation.endsWith("\r")) {
                            if (paragraph.endsWith("\r\n")) {
                                translation += "\r\n";
                            } else if (paragraph.endsWith("\n")) {
                                translation += "\n";
                            } else if (paragraph.endsWith("\r")) {
                                translation += "\r";
                            }
                        }

                        Map<String, Object> eventData = new HashMap<>();
                        eventData.put("textId", textId);
                        eventData.put("original", paragraph);
                        eventData.put("translation", translation);
                        eventData.put("progress", (int) (((i + 1) * 100.0) / total));

                        SseEmitterUtil.sendData(emitter, mapper.writeValueAsString(eventData));
                    } catch (Exception e) {
                        log.warn("段落翻译失败 [{}]: {}", i, e.getMessage());
                        Map<String, Object> errorData = new HashMap<>();
                        errorData.put("textId", textId);
                        errorData.put("original", paragraph);
                        errorData.put("translation", paragraph);
                        errorData.put("error", true);
                        errorData.put("progress", (int) (((i + 1) * 100.0) / total));
                        SseEmitterUtil.sendData(emitter, mapper.writeValueAsString(errorData));
                    }

                    translated++;
                }

                SseEmitterUtil.sendDone(emitter);
                SseEmitterUtil.complete(emitter);

            } catch (Exception e) {
                log.error("流式文档翻译失败: {}", e.getMessage(), e);
                SseEmitterUtil.sendError(emitter, "翻译失败：" + e.getMessage());
                SseEmitterUtil.complete(emitter);
            }
        });

        return emitter;
    }

    /**
     * 读取 MultipartFile 内容
     */
    private String readMultipartFileContent(MultipartFile file, String fileType) throws IOException {
        byte[] bytes = file.getBytes();
        String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

        if ("txt".equals(fileType)) {
            return content;
        } else {
            throw new IOException("暂不支持 " + fileType.toUpperCase() + " 格式，仅支持 TXT 文件");
        }
    }

    /**
     * 定时清理卡死的任务（每 5 分钟执行一次）
     * 将超过 30 分钟仍处于 PENDING/PROCESSING 状态的任务标记为 FAILED
     */
    @Scheduled(fixedRate = 300000)
    public void cleanupStuckTasks() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        List<TranslationTask> stuckTasks = translationTaskMapper.findByStatusAndCreateTimeBefore(
                TranslationStatus.PROCESSING.getValue(), cutoff);
        for (TranslationTask task : stuckTasks) {
            log.warn("清理卡死任务: taskId={}, createTime={}", task.getTaskId(), task.getCreateTime());
            task.setStatus(TranslationStatus.FAILED.getValue());
            task.setErrorMessage("任务超时，自动标记为失败");
            translationTaskMapper.updateById(task);
            // 同步更新文档状态
            if (task.getDocumentId() != null) {
                Document doc = documentMapper.selectById(task.getDocumentId());
                if (doc != null && TranslationStatus.PROCESSING.getValue().equals(doc.getStatus())) {
                    doc.setStatus(TranslationStatus.FAILED.getValue());
                    doc.setErrorMessage("任务超时，自动标记为失败");
                    doc.setUpdateTime(LocalDateTime.now());
                    documentMapper.updateById(doc);
                }
            }
        }
    }

    /**
     * 应用关闭时优雅关闭异步翻译线程
     */
    @PreDestroy
    public void shutdown() {
        log.info("翻译任务服务关闭，等待异步翻译任务完成...");
    }
}
