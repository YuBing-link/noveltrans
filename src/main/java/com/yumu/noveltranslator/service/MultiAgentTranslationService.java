package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.entity.CollabChapterTask;
import com.yumu.noveltranslator.entity.CollabProject;
import com.yumu.noveltranslator.entity.Document;
import com.yumu.noveltranslator.entity.TranslationTask;
import com.yumu.noveltranslator.enums.ChapterTaskStatus;
import com.yumu.noveltranslator.enums.CollabProjectStatus;
import com.yumu.noveltranslator.enums.TranslationStatus;
import com.yumu.noveltranslator.mapper.CollabChapterTaskMapper;
import com.yumu.noveltranslator.mapper.CollabProjectMapper;
import com.yumu.noveltranslator.mapper.DocumentMapper;
import com.yumu.noveltranslator.mapper.TranslationTaskMapper;
import com.yumu.noveltranslator.service.pipeline.TranslationPipeline;
import com.yumu.noveltranslator.util.ExternalResponseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多 Agent 协作翻译服务
 * 使用 AgentScope 框架协调多个 Agent 并行翻译不同章节
 *
 * 接入能力：三级缓存 + RAG + 实体一致性 + 术语表 + 自动审核 + 章节拼接
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MultiAgentTranslationService {

    private static final int MAX_CONCURRENT_AGENTS = 5;

    private final CollabChapterTaskMapper chapterTaskMapper;
    private final CollabProjectMapper collabProjectMapper;
    private final DocumentMapper documentMapper;
    private final TranslationTaskMapper translationTaskMapper;
    private final UserLevelThrottledTranslationClient translationClient;
    private final TranslationCacheService cacheService;
    private final RagTranslationService ragTranslationService;
    private final EntityConsistencyService entityConsistencyService;
    private final TranslationPostProcessingService postProcessingService;

    /**
     * 启动多 Agent 协作翻译
     */
    public void startMultiAgentTranslation(Long projectId) {
        List<CollabChapterTask> chapters = chapterTaskMapper.selectByProjectIdAndStatus(
                projectId, ChapterTaskStatus.UNASSIGNED.getValue());

        if (chapters.isEmpty()) {
            log.info("项目没有待翻译的章节: projectId={}", projectId);
            return;
        }

        CollabProject project = collabProjectMapper.selectById(projectId);
        if (project == null) {
            log.error("项目不存在: projectId={}", projectId);
            return;
        }

        log.info("启动多 Agent 翻译: projectId={}, chapters={}", projectId, chapters.size());

        // 更新关联文档状态为处理中
        if (project.getDocumentId() != null) {
            Document doc = documentMapper.selectById(project.getDocumentId());
            if (doc != null) {
                doc.setStatus(TranslationStatus.PROCESSING.getValue());
                doc.setUpdateTime(LocalDateTime.now());
                documentMapper.updateById(doc);
            }
        }

        CountDownLatch latch = new CountDownLatch(chapters.size());
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        Semaphore agentSemaphore = new Semaphore(MAX_CONCURRENT_AGENTS);

        for (CollabChapterTask chapter : chapters) {
            Thread.startVirtualThread(() -> {
                try {
                    agentSemaphore.acquire();
                    try {
                        boolean success = translateChapter(chapter, project);
                        if (success) {
                            completed.incrementAndGet();
                        } else {
                            failed.incrementAndGet();
                        }
                        log.info("Agent 翻译完成章节: chapterId={}, progress={}/{}",
                                chapter.getId(), completed.get(), chapters.size());
                    } finally {
                        agentSemaphore.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failed.incrementAndGet();
                    log.error("翻译章节被中断: chapterId={}", chapter.getId());
                } catch (Exception e) {
                    failed.incrementAndGet();
                    log.error("Agent 翻译章节失败: chapterId={}, error={}", chapter.getId(), e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // 异步等待所有 Agent 完成，完成后更新项目进度并拼接完整文档
        Thread.startVirtualThread(() -> {
            try {
                latch.await();
                log.info("多 Agent 翻译全部完成: projectId={}, success={}, failed={}",
                        projectId, completed.get(), failed.get());
                updateProjectProgress(projectId);
                assembleCompleteDocument(projectId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("等待翻译完成被中断: projectId={}", projectId);
            }
        });
    }

    /**
     * 单个 Agent 翻译一个章节
     * 接入三级缓存 → RAG → 实体一致性 → Python 翻译
     * 翻译完成直接标记 COMPLETED
     *
     * @return true 翻译成功, false 翻译失败
     */
    private boolean translateChapter(CollabChapterTask chapter, CollabProject project) {
        Long chapterId = chapter.getId();
        String sourceText = chapter.getSourceText();

        if (sourceText == null || sourceText.trim().isEmpty()) {
            log.warn("章节内容为空，跳过: chapterId={}", chapterId);
            return false;
        }

        // 更新章节状态为翻译中
        chapter.setStatus(ChapterTaskStatus.TRANSLATING.getValue());
        chapter.setAssignedTime(LocalDateTime.now());
        chapter.setProgress(50);
        chapterTaskMapper.updateById(chapter);

        String targetLang = project.getTargetLang();
        String engine = "google";

        try {
            // 获取 userId：优先使用章节负责人，否则使用项目所有者
            Long userId = chapter.getAssigneeId();
            if (userId == null) {
                CollabProject proj = collabProjectMapper.selectById(chapter.getProjectId());
                if (proj != null) {
                    userId = proj.getOwnerId();
                }
            }
            String docId = "project_" + chapter.getProjectId() + "_chapter_" + chapter.getId();

            TranslationPipeline pipeline = new TranslationPipeline(
                    cacheService,
                    ragTranslationService,
                    entityConsistencyService,
                    translationClient,
                    postProcessingService,
                    userId,
                    docId
            );

            String translated = pipeline.execute(sourceText, targetLang, engine);

            if (translated == null || translated.trim().isEmpty()) {
                throw new RuntimeException("翻译返回结果为空");
            }

            // 直接标记完成
            chapter.setTargetText(translated);
            chapter.setTargetWordCount(translated.length());
            chapter.setProgress(100);
            chapter.setStatus(ChapterTaskStatus.COMPLETED.getValue());
            chapter.setCompletedTime(LocalDateTime.now());
            chapterTaskMapper.updateById(chapter);

            log.info("章节翻译完成: chapterId={}, 原文{}字, 译文{}字",
                    chapterId, sourceText.length(), translated.length());
            return true;

        } catch (Exception e) {
            log.error("章节翻译异常: chapterId={}, error={}", chapterId, e.getMessage(), e);
            chapter.setStatus(ChapterTaskStatus.UNASSIGNED.getValue());
            chapter.setReviewComment("翻译异常: " + e.getMessage());
            chapterTaskMapper.updateById(chapter);
            return false;
        }
    }

    /**
     * 更新项目整体进度
     */
    private void updateProjectProgress(Long projectId) {
        List<CollabChapterTask> tasks = chapterTaskMapper.selectByProjectId(projectId);
        if (tasks.isEmpty()) return;

        long completed = tasks.stream()
                .filter(t -> ChapterTaskStatus.COMPLETED.getValue().equals(t.getStatus()))
                .count();

        int progress = (int) Math.round((double) completed / tasks.size() * 100);

        CollabProject project = collabProjectMapper.selectById(projectId);
        if (project != null) {
            project.setProgress(progress);
            if (progress == 100) {
                project.setStatus(CollabProjectStatus.COMPLETED.getValue());
            }
            collabProjectMapper.updateById(project);
        }
    }

    /**
     * 所有章节翻译完成后，按 chapterNumber 顺序拼接完整翻译文档
     */
    private void assembleCompleteDocument(Long projectId) {
        // 按 chapterNumber 升序获取所有章节
        List<CollabChapterTask> chapters = chapterTaskMapper.selectByProjectId(projectId);
        if (chapters.isEmpty()) return;

        chapters.sort(Comparator.comparingInt(CollabChapterTask::getChapterNumber));

        // 拼接完整译文（不添加合成章节标记，保留原文结构）
        StringBuilder fullText = new StringBuilder();
        for (CollabChapterTask chapter : chapters) {
            if (chapter.getTargetText() != null && !chapter.getTargetText().isEmpty()) {
                fullText.append(chapter.getTargetText());
                if (!chapter.getTargetText().endsWith("\n")) {
                    fullText.append("\n");
                }
            }
        }

        if (fullText.length() == 0) return;

        // 通过项目关联的 documentId 直接获取 Document
        CollabProject project = collabProjectMapper.selectById(projectId);
        if (project == null || project.getDocumentId() == null) {
            log.warn("项目未关联 Document，无法保存完整译文: projectId={}", projectId);
            return;
        }

        Document matchedDoc = documentMapper.selectById(project.getDocumentId());
        if (matchedDoc == null) {
            log.warn("关联的 Document 不存在: documentId={}", project.getDocumentId());
            return;
        }

        // 保存完整翻译文档到文件
        String translatedPath = ExternalResponseUtil.buildTranslatedPath(matchedDoc.getPath());
        try {
            Path path = Paths.get(translatedPath);
            Files.createDirectories(path.getParent());
            Files.write(path, fullText.toString().getBytes(StandardCharsets.UTF_8));
            log.info("完整翻译文档已保存: path={}, size={} bytes", translatedPath, fullText.length());
        } catch (IOException e) {
            log.error("保存完整翻译文档失败: {}", e.getMessage(), e);
        }

        // 创建/更新 TranslationTask
        TranslationTask task = translationTaskMapper.findByDocumentId(matchedDoc.getId());
        if (task == null) {
            task = new TranslationTask();
            task.setTaskId("task_" + System.currentTimeMillis() + "_" + projectId);
            task.setDocumentId(matchedDoc.getId());
            task.setUserId(project.getOwnerId());
            task.setSourceLang(project.getSourceLang());
            task.setTargetLang(project.getTargetLang());
            task.setType("document");
            task.setMode("team");
            task.setStatus("completed");
            translationTaskMapper.insert(task);
        } else {
            task.setStatus("completed");
            translationTaskMapper.updateById(task);
        }
        log.info("团队模式翻译任务已更新: taskId={}, status=completed", task.getId());

        // 更新 Document 状态为 completed
        matchedDoc.setStatus("completed");
        matchedDoc.setTaskId(task.getTaskId());
        documentMapper.updateById(matchedDoc);
        log.info("团队模式文档状态已更新: documentId={}, status=completed, taskId={}", matchedDoc.getId(), task.getTaskId());
    }
}
