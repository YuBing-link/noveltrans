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

import java.nio.file.Files;
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
        history.setTargetText(targetText != null && targetText.length() > 500
                ? targetText.substring(0, 500)
                : targetText);
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
