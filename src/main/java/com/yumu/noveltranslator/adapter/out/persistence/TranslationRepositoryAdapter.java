package com.yumu.noveltranslator.adapter.out.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yumu.noveltranslator.adapter.out.persistence.converter.TranslationConverter;
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
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TranslationRepositoryAdapter implements TranslationRepositoryPort {

    private final TranslationTaskMapper taskMapper;
    private final TranslationHistoryMapper historyMapper;

    @Override
    public Optional<com.yumu.noveltranslator.domain.model.TranslationTask> findTaskByTaskId(String taskId) {
        return Optional.ofNullable(TranslationConverter.toModelTask(taskMapper.findByTaskId(taskId)));
    }

    @Override
    public Optional<com.yumu.noveltranslator.domain.model.TranslationTask> findTaskByDocumentId(Long docId) {
        return Optional.ofNullable(TranslationConverter.toModelTask(taskMapper.findByDocumentId(docId)));
    }

    @Override
    public List<com.yumu.noveltranslator.domain.model.TranslationTask> findTasksByDocumentId(Long docId) {
        return TranslationConverter.toModelListTasks(
                taskMapper.selectList(new LambdaQueryWrapper<TranslationTask>().eq(TranslationTask::getDocumentId, docId)));
    }

    @Override
    public List<com.yumu.noveltranslator.domain.model.TranslationTask> findTasksByUserIdAndStatus(Long userId, int offset, int limit) {
        return taskMapper.findByUserIdAndStatus(userId, offset, limit).stream()
                .map(TranslationConverter::toModelTask)
                .collect(Collectors.toList());
    }

    @Override
    public List<com.yumu.noveltranslator.domain.model.TranslationTask> findTasksByStatusAndCreateTimeBefore(String status, LocalDateTime cutoff) {
        return taskMapper.findByStatusAndCreateTimeBefore(status, cutoff).stream()
                .map(TranslationConverter::toModelTask)
                .collect(Collectors.toList());
    }

    @Override
    public void saveTask(com.yumu.noveltranslator.domain.model.TranslationTask task) {
        var entity = TranslationConverter.toEntityTask(task);
        taskMapper.insert(entity);
        task.setId(entity.getId()); // 回填自增主键
    }

    @Override
    public void updateTask(com.yumu.noveltranslator.domain.model.TranslationTask task) {
        taskMapper.updateById(TranslationConverter.toEntityTask(task));
    }

    @Override
    public Optional<com.yumu.noveltranslator.domain.model.TranslationHistory> findHistoryByTaskId(String taskId) {
        return Optional.ofNullable(TranslationConverter.toModelHistory(historyMapper.findByTaskId(taskId)));
    }

    @Override
    public List<com.yumu.noveltranslator.domain.model.TranslationHistory> findHistoryByUserId(Long userId, int offset, int pageSize) {
        return historyMapper.findByUserId(userId, offset, pageSize).stream()
                .map(TranslationConverter::toModelHistory)
                .collect(Collectors.toList());
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
    public void saveHistory(com.yumu.noveltranslator.domain.model.TranslationHistory history) {
        var entity = TranslationConverter.toEntityHistory(history);
        historyMapper.insert(entity);
        history.setId(entity.getId()); // 回填自增主键
    }

    @Override
    public void updateHistory(com.yumu.noveltranslator.domain.model.TranslationHistory history) {
        historyMapper.updateById(TranslationConverter.toEntityHistory(history));
    }

    @Override
    public Optional<com.yumu.noveltranslator.domain.model.TranslationHistory> findHistoryById(Long id) {
        return Optional.ofNullable(TranslationConverter.toModelHistory(historyMapper.selectById(id)));
    }

    @Override
    public void deleteHistory(Long id) {
        historyMapper.deleteById(id);
    }
}
