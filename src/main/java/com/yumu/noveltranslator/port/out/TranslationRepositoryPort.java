package com.yumu.noveltranslator.port.out;

import com.yumu.noveltranslator.adapter.out.persistence.entity.TranslationHistory;
import com.yumu.noveltranslator.adapter.out.persistence.entity.TranslationTask;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TranslationRepositoryPort {

    // === TranslationTask ===
    Optional<TranslationTask> findTaskByTaskId(String taskId);
    Optional<TranslationTask> findTaskByDocumentId(Long docId);
    List<TranslationTask> findTasksByDocumentId(Long docId);
    List<TranslationTask> findTasksByUserIdAndStatus(Long userId, int offset, int limit);
    List<TranslationTask> findTasksByStatusAndCreateTimeBefore(String status, LocalDateTime cutoff);
    void saveTask(TranslationTask task);
    void updateTask(TranslationTask task);

    // === TranslationHistory ===
    Optional<TranslationHistory> findHistoryByTaskId(String taskId);
    List<TranslationHistory> findHistoryByUserId(Long userId, int offset, int pageSize);
    int countHistoryByUserId(Long userId);
    int countHistoryByUserIdAndType(Long userId, String type);
    Long sumHistorySourceTextLengthByUserId(Long userId);
    int countHistoryByUserIdAfter(Long userId, LocalDateTime startTime);
    long countAllHistory();
    long countHistoryAfter(LocalDateTime startTime);
    int countActiveUsersAfter(LocalDateTime startTime);
    long sumAllHistorySourceTextLength();
    int countDocumentTranslations();
    void saveHistory(TranslationHistory history);
    void updateHistory(TranslationHistory history);
    Optional<TranslationHistory> findHistoryById(Long id);
    void deleteHistory(Long id);

    // === TranslationMemory (also in GlossaryRepositoryPort, delegated) ===
    // Primary access is through GlossaryRepositoryPort
}
