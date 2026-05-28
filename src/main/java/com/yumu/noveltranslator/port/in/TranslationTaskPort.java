package com.yumu.noveltranslator.port.in;

import com.yumu.noveltranslator.domain.model.Document;
import com.yumu.noveltranslator.domain.model.TranslationHistory;
import com.yumu.noveltranslator.domain.model.TranslationTask;
import com.yumu.noveltranslator.port.dto.entity.TaskStatusResponse;
import com.yumu.noveltranslator.port.dto.entity.TranslationHistoryResponse;
import com.yumu.noveltranslator.port.dto.translation.TranslationResultResponse;

import java.util.List;

public interface TranslationTaskPort {
    TranslationTask createDocumentTask(Long userId, Document doc);
    void startDocumentTranslation(TranslationTask task, Document doc);
    TranslationTask getTaskByTaskId(String taskId);
    TranslationTask getTaskByDocumentId(Long docId);
    boolean cancelTask(String taskId, Long userId);
    boolean deleteHistory(String taskId, Long userId);
    TranslationResultResponse getTranslationResult(String taskId);
    String getDownloadPath(String taskId, Long userId);
    List<TranslationHistory> getTranslationHistory(Long userId, int page, int pageSize, String status);
    int countTranslationHistory(Long userId, String status);
    TaskStatusResponse toTaskStatusResponse(TranslationTask task);
    TranslationHistoryResponse toHistoryResponse(TranslationHistory history);

    /**
     * 基于已上传文档的流式翻译
     * @param eventConsumer 回调，Service 通过它推送翻译事件
     */
    void streamTranslateDocumentById(Long docId, String targetLang, String mode, StreamTranslateEventConsumer eventConsumer);

    /**
     * 基于上传文件的流式翻译
     * @param fileContent 文件字节数组
     * @param fileName 原始文件名（用于判断文件类型）
     */
    void streamTranslateDocument(byte[] fileContent, String fileName, String sourceLang, String targetLang, String mode, StreamTranslateEventConsumer eventConsumer);
}
