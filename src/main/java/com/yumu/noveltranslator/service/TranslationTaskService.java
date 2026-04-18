package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.Document;
import com.yumu.noveltranslator.entity.TranslationHistory;
import com.yumu.noveltranslator.entity.TranslationTask;
import com.yumu.noveltranslator.mapper.DocumentMapper;
import com.yumu.noveltranslator.mapper.TranslationHistoryMapper;
import com.yumu.noveltranslator.mapper.TranslationTaskMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 翻译任务服务
 */
@Service
public class TranslationTaskService {
    private static final Logger log = LoggerFactory.getLogger(TranslationTaskService.class);

    @Autowired
    private TranslationTaskMapper translationTaskMapper;

    @Autowired
    private TranslationHistoryMapper translationHistoryMapper;

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private UserLevelThrottledTranslationClient userLevelThrottledTranslationClient;

    /**
     * 创建文本翻译任务
     */
    public TranslationTask createTextTask(Long userId, TextTranslationRequest request) {
        String taskId = generateTaskId();

        TranslationTask task = new TranslationTask();
        task.setTaskId(taskId);
        task.setUserId(userId);
        task.setType("text");
        task.setSourceLang(request.getSourceLang());
        task.setTargetLang(request.getTargetLang());
        task.setMode(request.getMode());
        task.setEngine("google");
        task.setStatus("pending");
        task.setProgress(0);
        task.setCreateTime(LocalDateTime.now());

        translationTaskMapper.insert(task);

        // 异步执行翻译
        executeTextTranslation(task, request.getText());

        return task;
    }

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
        task.setStatus("pending");
        task.setProgress(0);
        task.setCreateTime(LocalDateTime.now());

        translationTaskMapper.insert(task);

        return task;
    }

    /**
     * 启动文档翻译
     */
    public void startDocumentTranslation(TranslationTask task, Document doc) {
        if (task == null || doc == null) {
            return;
        }
        if (!"pending".equals(task.getStatus()) && !"failed".equals(task.getStatus())) {
            return;
        }

        // 更新文档状态为处理中
        doc.setStatus("processing");
        doc.setUpdateTime(LocalDateTime.now());
        documentMapper.updateById(doc);

        updateTaskProgress(task, "processing", 10, null);

        executeDocumentTranslation(task, doc);
    }

    /**
     * 异步执行文档翻译（使用虚拟线程）
     */
    private void executeDocumentTranslation(TranslationTask task, Document doc) {
        Thread.startVirtualThread(() -> {
            try {
                // 读取文件内容
                String content = readDocumentContent(doc.getPath(), doc.getFileType());
                if (content == null || content.trim().isEmpty()) {
                    updateTaskProgress(task, "failed", 0, "文件内容为空");
                    return;
                }

                // 按段落分割翻译（每 3000 字符一批）
                updateTaskProgress(task, "processing", 20, null);
                StringBuilder translatedContent = new StringBuilder();
                String[] paragraphs = content.split("(?<=\n)");
                int total = paragraphs.length;
                int batchStart = 0;

                while (batchStart < total) {
                    StringBuilder batch = new StringBuilder();
                    int batchEnd = batchStart;
                    while (batchEnd < total && batch.length() + paragraphs[batchEnd].length() <= 3000) {
                        batch.append(paragraphs[batchEnd]);
                        batchEnd++;
                    }

                    if (batch.length() == 0) {
                        // 单段落超长，直接翻译
                        batch.append(paragraphs[batchStart]);
                        batchEnd = batchStart + 1;
                    }

                    String result = userLevelThrottledTranslationClient.translate(
                            batch.toString(),
                            task.getTargetLang(),
                            task.getEngine(),
                            false);

                    translatedContent.append(result);
                    batchStart = batchEnd;

                    int progress = 20 + (int) ((batchStart * 80.0) / total);
                    updateTaskProgress(task, "processing", progress, null);
                }

                // 保存翻译结果到文件
                String translatedPath = doc.getPath().replace(".", "_translated.");
                Files.write(Paths.get(translatedPath), translatedContent.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

                // 更新文档状态为已完成
                doc.setStatus("completed");
                doc.setCompletedTime(LocalDateTime.now());
                doc.setUpdateTime(LocalDateTime.now());
                documentMapper.updateById(doc);

                updateTaskProgress(task, "completed", 100, null);
                saveTranslationHistory(task, content, translatedContent.toString());

            } catch (Exception e) {
                log.error("文档翻译失败: {}", e.getMessage(), e);
                updateTaskProgress(task, "failed", 0, "翻译失败：" + e.getMessage());
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

    /**
     * 异步执行文本翻译（使用虚拟线程）
     */
    private void executeTextTranslation(TranslationTask task, String text) {
        Thread.startVirtualThread(() -> {
            try {
                // 更新状态为处理中
                updateTaskProgress(task, "translating", 50, null);

                // 调用翻译服务
                String result = userLevelThrottledTranslationClient.translate(
                        text,
                        task.getTargetLang(),
                        task.getEngine(),
                        false);

                // 更新状态为完成并保存历史
                updateTaskProgress(task, "completed", 100, null);
                saveTranslationHistory(task, text, result);

            } catch (Exception e) {
                // 更新状态为失败
                updateTaskProgress(task, "failed", 0, "翻译失败：" + e.getMessage());
            }
        });
    }

    @Transactional
    protected void updateTaskProgress(TranslationTask task, String status, int progress, String errorMessage) {
        task.setStatus(status);
        task.setProgress(progress);
        if ("completed".equals(status)) {
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

    /**
     * 从翻译响应中提取实际翻译内容
     */
    private String extractTranslatedContent(String response) {
        if (response == null || response.isEmpty()) {
            return response;
        }
        try {
            // 尝试解析 JSON: {"success":true,"engine":"mtran","translatedContent":"..."}
            if (response.startsWith("{")) {
                int idx = response.indexOf("\"translatedContent\"");
                if (idx >= 0) {
                    int colonIdx = response.indexOf(':', idx + 19);
                    int quoteStart = response.indexOf('"', colonIdx + 1);
                    if (quoteStart >= 0) {
                        int quoteEnd = findUnescapedQuoteEnd(response, quoteStart + 1);
                        if (quoteEnd > quoteStart) {
                            return response.substring(quoteStart + 1, quoteEnd)
                                    .replace("\\n", "\n")
                                    .replace("\\\\", "\\");
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析翻译响应失败: {}", e.getMessage());
        }
        return response;
    }

    /**
     * 查找未转义的引号结束位置
     */
    private int findUnescapedQuoteEnd(String str, int start) {
        int i = start;
        while (i < str.length()) {
            if (str.charAt(i) == '\\') {
                i += 2;
                continue;
            }
            if (str.charAt(i) == '"') {
                return i;
            }
            i++;
        }
        // fallback: find closing quote loosely
        int idx = str.lastIndexOf('"');
        return idx > start ? idx : -1;
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
            if ("pending".equals(task.getStatus()) || "processing".equals(task.getStatus())) {
                task.setStatus("failed");
                task.setErrorMessage("用户取消任务");
                translationTaskMapper.updateById(task);
                return true;
            }
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

        if ("completed".equals(task.getStatus())) {
            response.setCompletedTime(task.getCompletedTime() != null
                    ? task.getCompletedTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    : null);

            // 如果是文本翻译，从历史中获取结果
            if ("text".equals(task.getType())) {
                TranslationHistory history = translationHistoryMapper.findByTaskId(task.getTaskId());
                if (history != null) {
                    response.setTranslatedText(history.getTargetText());
                }
            } else if ("document".equals(task.getType())) {
                // 文档翻译，返回文件路径
                if (task.getDocumentId() != null) {
                    Document doc = documentMapper.findById(task.getDocumentId());
                    if (doc != null) {
                        response.setTranslatedFilePath(doc.getPath());
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

        if ("completed".equals(task.getStatus()) && "document".equals(task.getType())) {
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
     * 获取翻译历史列表
     */
    public List<TranslationHistory> getTranslationHistory(Long userId, int page, int pageSize, String type) {
        int offset = (page - 1) * pageSize;
        List<TranslationHistory> histories = translationHistoryMapper.findByUserId(userId, offset, pageSize);

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

        return response;
    }

    /**
     * 应用关闭时优雅关闭异步翻译线程
     */
    @PreDestroy
    public void shutdown() {
        log.info("翻译任务服务关闭，等待异步翻译任务完成...");
    }
}
