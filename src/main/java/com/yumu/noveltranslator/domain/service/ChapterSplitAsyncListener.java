package com.yumu.noveltranslator.domain.service;

import com.yumu.noveltranslator.domain.model.CollabChapterTask;
import com.yumu.noveltranslator.domain.model.CollabProject;
import com.yumu.noveltranslator.domain.model.Document;
import com.yumu.noveltranslator.enums.ChapterTaskStatus;
import com.yumu.noveltranslator.enums.CollabProjectStatus;
import com.yumu.noveltranslator.enums.TranslationStatus;
import com.yumu.noveltranslator.domain.event.ChapterSplitEvent;
import com.yumu.noveltranslator.domain.service.CollabStateMachine;
import com.yumu.noveltranslator.port.out.CollaborationRepositoryPort;
import com.yumu.noveltranslator.port.out.DocumentRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 章节拆分异步监听器
 * 接收 ChapterSplitEvent 事件，按批次异步插入章节，完成后激活项目。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChapterSplitAsyncListener {

    private static final int BATCH_SIZE = 50;

    private final CollaborationRepositoryPort collaborationRepository;
    private final DocumentRepositoryPort documentRepository;
    private final CollabStateMachine collabStateMachine;

    @Async("chapterSplitExecutor")
    @EventListener
    public void onChapterSplitEvent(ChapterSplitEvent event) {
        Long projectId = event.getProjectId();
        List<String> chapters = event.getChapters();
        log.info("开始异步插入章节: projectId={}, totalChapters={}", projectId, chapters.size());

        try {
            int totalBatches = (int) Math.ceil((double) chapters.size() / BATCH_SIZE);
            for (int i = 0; i < chapters.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, chapters.size());
                List<String> batch = chapters.subList(i, end);
                int batchIndex = (i / BATCH_SIZE) + 1;
                log.debug("插入章节批次: projectId={}, batch={}/{}", projectId, batchIndex, totalBatches);
                insertChapterBatch(event, batch, i);
            }

            // 所有章节批次插入完成，激活项目
            activateProject(projectId, event.getDocumentId());
            log.info("章节异步插入完成: projectId={}, totalChapters={}", projectId, chapters.size());
        } catch (Exception e) {
            log.error("章节异步插入失败: projectId={}, error={}", projectId, e.getMessage(), e);
        }
    }

    @Transactional
    protected void insertChapterBatch(ChapterSplitEvent event, List<String> batch, int startIndex) {
        Long projectId = event.getProjectId();
        for (int i = 0; i < batch.size(); i++) {
            String chapterText = batch.get(i);
            CollabChapterTask chapter = new CollabChapterTask();
            chapter.setProjectId(projectId);
            chapter.setChapterNumber(startIndex + i + 1);
            chapter.setTitle("第 " + (startIndex + i + 1) + " 章");
            chapter.setSourceText(chapterText);
            chapter.setTargetText(null);
            chapter.setStatus(ChapterTaskStatus.UNASSIGNED.getValue());
            chapter.setProgress(0);
            chapter.setSourceWordCount(chapterText.length());
            collaborationRepository.saveChapterTask(chapter);
        }
        log.debug("批次插入成功: projectId={}, batchStart={}, count={}", projectId, startIndex + 1, batch.size());
    }

    @Transactional
    protected void activateProject(Long projectId, Long documentId) {
        CollabProject project = collaborationRepository.findProjectById(projectId).orElse(null);
        if (project == null) {
            log.error("激活项目失败: 项目不存在, projectId={}", projectId);
            return;
        }

        // 通过状态机将项目从 DRAFT 转换到 ACTIVE
        collabStateMachine.transitionProject(project, CollabProjectStatus.ACTIVE);
        project.setProgress(0);
        collaborationRepository.updateProject(project);

        // 更新文档状态为已完成
        Document doc = documentRepository.findById(documentId).orElse(null);
        if (doc != null) {
            doc.setStatus(TranslationStatus.COMPLETED.getValue());
            doc.setUpdateTime(LocalDateTime.now());
            documentRepository.update(doc);
        }

        log.info("项目已激活: projectId={}", projectId);
    }
}
