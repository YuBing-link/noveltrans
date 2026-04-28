package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.Document;
import com.yumu.noveltranslator.entity.TranslationHistory;
import com.yumu.noveltranslator.entity.TranslationTask;
import com.yumu.noveltranslator.enums.TranslationMode;
import com.yumu.noveltranslator.enums.TranslationStatus;
import com.yumu.noveltranslator.mapper.DocumentMapper;
import com.yumu.noveltranslator.mapper.TranslationHistoryMapper;
import com.yumu.noveltranslator.mapper.TranslationTaskMapper;
import com.yumu.noveltranslator.service.pipeline.TranslationPipeline;
import com.yumu.noveltranslator.service.state.TranslationStateMachine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yumu.noveltranslator.util.ExternalResponseUtil;
import com.yumu.noveltranslator.util.SseEmitterUtil;
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

                TranslationPipeline pipeline = new TranslationPipeline(
                    cacheService, ragTranslationService, entityConsistencyService,
                    userLevelThrottledTranslationClient, postProcessingService, userId, docId);

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
                            translated = pipeline.executeFast(batchText, targetLang, TranslationMode.FAST);
                        } else {
                            translated = pipeline.execute(batchText, targetLang, TranslationMode.EXPERT);
                        }
                    } catch (Exception e) {
                        log.warn("翻译批次失败 [{}/{}]，保留原文: {}", batchStart, total, e.getMessage());
                        translated = batchText; // fallback to original text
                    }
                    if (translated != null && !translated.isEmpty()) {
                        // 补回原文批次中的换行符以保持格式对齐
                        if (!translated.endsWith("\n") && !translated.endsWith("\r")) {
                            String trailingNewline = getTrailingNewline(batchText);
                            if (!trailingNewline.isEmpty()) {
                                translated += trailingNewline;
                            }
                        }
                        translatedContent.append(translated);
                    }
                    batchStart = batchEnd;

                    int progress = 20 + (int) ((batchStart * 80.0) / total);
                    updateTaskProgress(task, TranslationStatus.PROCESSING, progress, null);
                }

                // 保存翻译结果到文件
                String translatedPath = ExternalResponseUtil.buildTranslatedPath(doc.getPath());
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
        String translatedContent = ExternalResponseUtil.extractDataField(targetText);
        history.setTargetText(translatedContent != null && translatedContent.length() > 500
                ? translatedContent.substring(0, 500)
                : translatedContent);
        history.setEngine(task.getEngine());
        history.setCreateTime(LocalDateTime.now());

        translationHistoryMapper.insert(history);
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
                            String translatedPath = ExternalResponseUtil.buildTranslatedPath(doc.getPath());
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
                            TranslationPipeline pipeline = new TranslationPipeline(
                                    cacheService, ragTranslationService, entityConsistencyService,
                                    userLevelThrottledTranslationClient, postProcessingService, null, null);
                            result = pipeline.execute(paragraph, targetLang, TranslationMode.EXPERT);
                        } else {
                            result = userLevelThrottledTranslationClient.translate(
                                    paragraph, targetLang, "google", false, true);
                        }
                        String translation = ExternalResponseUtil.extractDataField(result);
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
                String translatedPath = ExternalResponseUtil.buildTranslatedPath(doc.getPath());
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
                            TranslationPipeline pipeline = new TranslationPipeline(
                                    cacheService, ragTranslationService, entityConsistencyService,
                                    userLevelThrottledTranslationClient, postProcessingService, null, null);
                            result = pipeline.execute(paragraph, targetLang, TranslationMode.EXPERT);
                        } else {
                            result = userLevelThrottledTranslationClient.translate(
                                    paragraph, targetLang, "google", false, true);
                        }

                        // 提取实际翻译内容
                        String translation = ExternalResponseUtil.extractDataField(result);
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
     * 提取字符串末尾的所有换行符
     * 用于在翻译后恢复原文的格式
     */
    private static String getTrailingNewline(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int end = text.length();
        while (end > 0) {
            char c = text.charAt(end - 1);
            if (c != '\n' && c != '\r') {
                break;
            }
            end--;
        }
        return text.substring(end);
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
