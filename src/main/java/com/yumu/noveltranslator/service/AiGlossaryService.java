package com.yumu.noveltranslator.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yumu.noveltranslator.entity.AiGlossary;
import com.yumu.noveltranslator.mapper.AiGlossaryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 术语表服务
 * 负责维护每个小说项目的 AI 提取术语
 * 支持 upsert（同一项目 + 原文术语已存在则更新译文）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiGlossaryService {

    private final AiGlossaryMapper aiGlossaryMapper;

    /**
     * 获取项目所有已确认的术语
     */
    public List<AiGlossary> getProjectGlossary(Long projectId) {
        return aiGlossaryMapper.selectByProjectIdAndStatus(projectId, "confirmed");
    }

    /**
     * 添加术语（upsert）
     * 如果同一项目下 sourceWord 已存在，则更新 targetWord；否则插入新记录
     * AI 提取的术语默认状态为 pending
     */
    public void addTerm(Long projectId, String sourceWord, String targetWord,
                        String context, String entityType, Long chapterId) {
        // 先查找是否已存在
        LambdaQueryWrapper<AiGlossary> query = new LambdaQueryWrapper<>();
        query.eq(AiGlossary::getProjectId, projectId)
             .eq(AiGlossary::getSourceWord, sourceWord);
        AiGlossary existing = aiGlossaryMapper.selectOne(query);

        if (existing != null) {
            // 更新译文（覆盖已有的译法）
            LambdaUpdateWrapper<AiGlossary> update = new LambdaUpdateWrapper<>();
            update.eq(AiGlossary::getId, existing.getId())
                  .set(AiGlossary::getTargetWord, targetWord);
            if (context != null) {
                update.set(AiGlossary::getContext, context);
            }
            if (chapterId != null) {
                update.set(AiGlossary::getChapterId, chapterId);
            }
            aiGlossaryMapper.update(null, update);
            log.debug("AI 术语表 upsert - 更新: projectId={}, sourceWord={}", projectId, sourceWord);
        } else {
            // 插入新术语
            AiGlossary term = new AiGlossary();
            term.setProjectId(projectId);
            term.setSourceWord(sourceWord);
            term.setTargetWord(targetWord);
            term.setContext(context);
            term.setEntityType(entityType);
            term.setChapterId(chapterId);
            term.setConfidence(0.8);
            term.setStatus("pending");
            aiGlossaryMapper.insert(term);
            log.debug("AI 术语表 upsert - 新增: projectId={}, sourceWord={}", projectId, sourceWord);
        }
    }

    /**
     * 批量添加术语
     */
    public void batchAddTerms(Long projectId, List<AiGlossary> terms) {
        if (terms == null || terms.isEmpty()) {
            return;
        }
        for (AiGlossary term : terms) {
            term.setProjectId(projectId);
            if (term.getStatus() == null) {
                term.setStatus("pending");
            }
            if (term.getConfidence() == null) {
                term.setConfidence(0.8);
            }
            addTerm(projectId, term.getSourceWord(), term.getTargetWord(),
                    term.getContext(), term.getEntityType(), term.getChapterId());
        }
        log.info("批量添加 AI 术语: projectId={}, count={}", projectId, terms.size());
    }

    /**
     * 更新术语状态（pending -> confirmed / rejected）
     */
    public boolean updateTermStatus(Long termId, String status) {
        AiGlossary term = aiGlossaryMapper.selectById(termId);
        if (term == null) {
            log.warn("术语不存在: termId={}", termId);
            return false;
        }
        term.setStatus(status);
        return aiGlossaryMapper.updateById(term) > 0;
    }

    /**
     * 删除术语
     */
    public boolean deleteTerm(Long termId) {
        return aiGlossaryMapper.deleteById(termId) > 0;
    }

    /**
     * 获取项目的待确认术语
     */
    public List<AiGlossary> getPendingTerms(Long projectId) {
        return aiGlossaryMapper.selectByProjectIdAndStatus(projectId, "pending");
    }
}
