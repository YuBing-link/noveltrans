package com.yumu.noveltranslator.domain.service;

import com.yumu.noveltranslator.domain.model.AiGlossary;
import com.yumu.noveltranslator.port.out.GlossaryRepositoryPort;
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

    private final GlossaryRepositoryPort glossaryPort;

    /**
     * 获取项目所有已确认的术语
     * 如果表不存在或查询失败，返回空列表（不阻断翻译流程）
     */
    public List<AiGlossary> getProjectGlossary(Long projectId) {
        try {
            return glossaryPort.findAiGlossaryByProjectIdAndStatus(projectId, "confirmed");
        } catch (Exception e) {
            log.warn("获取 AI 术语表失败（可能表未创建）: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 添加术语（upsert）
     * 如果同一项目下 sourceWord 已存在，则更新 targetWord；否则插入新记录
     * AI 提取的术语默认状态为 pending
     */
    public void addTerm(Long projectId, String sourceWord, String targetWord,
                        String context, String entityType, Long chapterId) {
        glossaryPort.upsertAiGlossary(projectId, sourceWord, targetWord, context, entityType, chapterId);
        log.debug("AI 术语表 upsert: projectId={}, sourceWord={}", projectId, sourceWord);
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
        return glossaryPort.findAiGlossaryById(termId).map(term -> {
            term.setStatus(status);
            glossaryPort.updateAiGlossary(term);
            return true;
        }).orElseGet(() -> {
            log.warn("术语不存在: termId={}", termId);
            return false;
        });
    }

    /**
     * 删除术语
     */
    public boolean deleteTerm(Long termId) {
        glossaryPort.deleteAiGlossary(termId);
        return true;
    }

    /**
     * 获取项目的待确认术语
     */
    public List<AiGlossary> getPendingTerms(Long projectId) {
        return glossaryPort.findAiGlossaryByProjectIdAndStatus(projectId, "pending");
    }
}
