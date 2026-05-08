package com.yumu.noveltranslator.port.in;

import com.yumu.noveltranslator.domain.model.Document;
import com.yumu.noveltranslator.domain.model.TranslationHistory;
import com.yumu.noveltranslator.domain.model.TranslationTask;
import com.yumu.noveltranslator.port.dto.entity.TaskStatusResponse;
import com.yumu.noveltranslator.port.dto.entity.TranslationHistoryResponse;
import com.yumu.noveltranslator.port.dto.translation.TranslationResultResponse;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
    List<TranslationHistory> getTranslationHistory(Long userId, int page, int pageSize, String type);
    int countTranslationHistory(Long userId);
    TaskStatusResponse toTaskStatusResponse(TranslationTask task);
    TranslationHistoryResponse toHistoryResponse(TranslationHistory history);
    SseEmitter streamTranslateDocumentById(Long docId, String targetLang, String mode);
    SseEmitter streamTranslateDocument(MultipartFile file, String sourceLang, String targetLang, String mode);
}
