package com.yumu.noveltranslator.adapter.out.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yumu.noveltranslator.adapter.out.persistence.entity.TranslationHistory;
import com.yumu.noveltranslator.adapter.out.persistence.entity.TranslationTask;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.TranslationHistoryMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.TranslationTaskMapper;
import com.yumu.noveltranslator.port.out.TranslationRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TranslationRepositoryAdapter implements TranslationRepositoryPort {

    private final TranslationTaskMapper taskMapper;
    private final TranslationHistoryMapper historyMapper;

    @Override
    public Optional<TranslationTask> findTaskByTaskId(String taskId) {
        return Optional.ofNullable(taskMapper.findByTaskId(taskId));
    }

    @Override
    public Optional<TranslationTask> findTaskByDocumentId(Long docId) {
        return Optional.ofNullable(taskMapper.findByDocumentId(docId));
    }

    @Override
    public List<TranslationTask> findTasksByDocumentId(Long docId) {
        LambdaQueryWrapper<TranslationTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TranslationTask::getDocumentId, docId);
        return taskMapper.selectList(wrapper);
    }

    @Override
    public List<TranslationTask> findTasksByUserIdAndStatus(Long userId, int offset, int limit) {
        return taskMapper.findByUserIdAndStatus(userId, offset, limit);
    }

    @Override
    public List<TranslationTask> findTasksByStatusAndCreateTimeBefore(String status, LocalDateTime cutoff) {
        return taskMapper.findByStatusAndCreateTimeBefore(status, cutoff);
    }

    @Override
    public void saveTask(TranslationTask task) {
        taskMapper.insert(task);
    }

    @Override
    public void updateTask(TranslationTask task) {
        taskMapper.updateById(task);
    }

    @Override
    public Optional<TranslationHistory> findHistoryByTaskId(String taskId) {
        return Optional.ofNullable(historyMapper.findByTaskId(taskId));
    }

    @Override
    public List<TranslationHistory> findHistoryByUserId(Long userId, int offset, int pageSize) {
        return historyMapper.findByUserId(userId, offset, pageSize);
    }

    @Override
    public int countHistoryByUserId(Long userId) {
        return historyMapper.countByUserId(userId);
    }

    @Override
    public int countHistoryByUserIdAndType(Long userId, String type) {
        return historyMapper.countByUserIdAndType(userId, type);
    }

    @Override
    public Long sumHistorySourceTextLengthByUserId(Long userId) {
        return historyMapper.sumSourceTextLengthByUserId(userId);
    }

    @Override
    public int countHistoryByUserIdAfter(Long userId, LocalDateTime startTime) {
        return historyMapper.countByUserIdAfter(userId, startTime);
    }

    @Override
    public long countAllHistory() {
        return historyMapper.countAll();
    }

    @Override
    public long countHistoryAfter(LocalDateTime startTime) {
        return historyMapper.countAfter(startTime);
    }

    @Override
    public int countActiveUsersAfter(LocalDateTime startTime) {
        return historyMapper.countActiveUsersAfter(startTime);
    }

    @Override
    public long sumAllHistorySourceTextLength() {
        return historyMapper.sumAllSourceTextLength();
    }

    @Override
    public int countDocumentTranslations() {
        return historyMapper.countDocumentTranslations();
    }

    @Override
    public void saveHistory(TranslationHistory history) {
        historyMapper.insert(history);
    }

    @Override
    public void updateHistory(TranslationHistory history) {
        historyMapper.updateById(history);
    }

    @Override
    public Optional<TranslationHistory> findHistoryById(Long id) {
        return Optional.ofNullable(historyMapper.selectById(id));
    }

    @Override
    public void deleteHistory(Long id) {
        historyMapper.deleteById(id);
    }
}
