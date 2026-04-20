package com.yumu.noveltranslator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yumu.noveltranslator.dto.ConsistencyTranslationResult;
import com.yumu.noveltranslator.dto.RagTranslationResponse;
import com.yumu.noveltranslator.entity.CollabChapterTask;
import com.yumu.noveltranslator.entity.CollabProject;
import com.yumu.noveltranslator.entity.Document;
import com.yumu.noveltranslator.entity.TranslationTask;
import com.yumu.noveltranslator.enums.ChapterTaskStatus;
import com.yumu.noveltranslator.enums.CollabProjectStatus;
import com.yumu.noveltranslator.mapper.CollabChapterTaskMapper;
import com.yumu.noveltranslator.mapper.CollabProjectMapper;
import com.yumu.noveltranslator.mapper.DocumentMapper;
import com.yumu.noveltranslator.mapper.TranslationTaskMapper;
import com.yumu.noveltranslator.util.CacheKeyUtil;
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
    private static final int ENTITY_CONSISTENCY_MIN_LENGTH = 500;

    private final CollabChapterTaskMapper chapterTaskMapper;
    private final CollabProjectMapper collabProjectMapper;
    private final DocumentMapper documentMapper;
    private final TranslationTaskMapper translationTaskMapper;
    private final UserLevelThrottledTranslationClient translationClient;
    private final TranslationCacheService cacheService;
    private final RagTranslationService ragTranslationService;
    private final EntityConsistencyService entityConsistencyService;

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
            String translated;

            // 长文本（>=500字）使用实体一致性管线（含术语表、占位符保护）
            if (sourceText.length() >= ENTITY_CONSISTENCY_MIN_LENGTH) {
                log.info("章节长度超过阈值，启用实体一致性翻译: chapterId={}", chapterId);
                translated = translateWithConsistencyPipeline(sourceText, targetLang, engine, chapter);
            } else {
                // 短文本：查缓存 → RAG → 直接翻译
                translated = translateWithCacheAndRag(sourceText, targetLang, engine, chapter);
            }

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
     * 带实体一致性的翻译管线（长文本）
     */
    private String translateWithConsistencyPipeline(String sourceText, String targetLang, String engine, CollabChapterTask chapter) {
        // 1. 先尝试缓存
        String cacheKey = CacheKeyUtil.buildCacheKey(sourceText, targetLang, engine);
        String cached = cacheService.getCache(cacheKey);
        if (cached != null) {
            log.info("团队模式缓存命中: chapterId={}", chapter.getId());
            return cached;
        }

        // 2. RAG 查询
        RagTranslationResponse ragResult = ragTranslationService.searchSimilar(sourceText, targetLang, engine);
        if (ragResult.isDirectHit()) {
            log.info("团队模式 RAG 直接命中: chapterId={}, similarity={}", chapter.getId(), ragResult.getSimilarity());
            cacheService.putCache(cacheKey, sourceText, ragResult.getTranslation(), "auto", targetLang, engine);
            return ragResult.getTranslation();
        }

        // 3. 实体一致性翻译（加载用户术语表 + 占位符保护）
        Long userId = chapter.getAssigneeId();
        if (userId == null) {
            CollabProject proj = collabProjectMapper.selectById(chapter.getProjectId());
            if (proj != null) userId = proj.getOwnerId();
        }

        if (entityConsistencyService.shouldUseConsistency(sourceText) && userId != null) {
            String docId = "project_" + chapter.getProjectId() + "_chapter_" + chapter.getId();
            ConsistencyTranslationResult consistencyResult =
                    entityConsistencyService.translateWithConsistency(sourceText, targetLang, engine, userId, docId);
            if (consistencyResult.isConsistencyApplied() && consistencyResult.getTranslatedText() != null) {
                String result = consistencyResult.getTranslatedText();
                if (shouldCache(sourceText, result)) {
                    cacheService.putCache(cacheKey, sourceText, result, "auto", targetLang, engine);
                }
                ragTranslationService.storeTranslationMemory(sourceText, result, targetLang, engine);
                return result;
            }
        }

        // 4. 兜底：直接调用 Python 翻译
        return translateWithCacheAndRag(sourceText, targetLang, engine, chapter);
    }

    /**
     * 带缓存和 RAG 的翻译（短文本）
     */
    private String translateWithCacheAndRag(String sourceText, String targetLang, String engine, CollabChapterTask chapter) {
        String cacheKey = CacheKeyUtil.buildCacheKey(sourceText, targetLang, engine);

        // 1. 缓存查询
        String cached = cacheService.getCache(cacheKey);
        if (cached != null) {
            log.info("团队模式缓存命中: chapterId={}", chapter.getId());
            return cached;
        }

        // 2. RAG 查询
        RagTranslationResponse ragResult = ragTranslationService.searchSimilar(sourceText, targetLang, engine);
        if (ragResult.isDirectHit()) {
            log.info("团队模式 RAG 直接命中: chapterId={}", chapter.getId());
            cacheService.putCache(cacheKey, sourceText, ragResult.getTranslation(), "auto", targetLang, engine);
            return ragResult.getTranslation();
        }

        // 3. 直接调用 Python 翻译
        String result = translationClient.translateWithPython(sourceText, targetLang, engine);
        String translation = extractTranslatedContent(result);

        if (translation != null && shouldCache(sourceText, translation)) {
            cacheService.putCache(cacheKey, sourceText, translation, "auto", targetLang, engine);
            ragTranslationService.storeTranslationMemory(sourceText, translation, targetLang, engine);
        }

        return translation != null ? translation : sourceText;
    }

    /**
     * 判断是否应该缓存翻译结果
     */
    private boolean shouldCache(String original, String translated) {
        if (original == null || translated == null) return false;
        String cleanOriginal = original.trim();
        String cleanTranslated = translated.trim();
        if (cleanOriginal.equals(cleanTranslated)) return false;
        if (cleanOriginal.equalsIgnoreCase(cleanTranslated)) return false;
        return true;
    }

    /**
     * 从翻译响应中提取实际内容
     * 支持格式: {"translatedContent":"..."} 或 {"code":200,"data":"..."}
     * 使用 JSON 解析自动处理 \n 等转义字符
     */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private String extractTranslatedContent(String response) {
        if (response == null || response.isEmpty()) {
            return response;
        }
        try {
            if (response.startsWith("{")) {
                JsonNode node = JSON_MAPPER.readTree(response);

                // 优先提取 translatedContent（MTranServer 格式）
                JsonNode contentNode = node.get("translatedContent");
                if (contentNode != null && contentNode.isTextual()) {
                    return contentNode.asText();
                }

                // 兜底提取 data 字段（Python 服务格式）
                JsonNode dataNode = node.get("data");
                if (dataNode != null && dataNode.isTextual()) {
                    return dataNode.asText();
                }
            }
        } catch (Exception e) {
            log.warn("解析翻译响应失败: {}", e.getMessage());
        }
        return response;
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

        // 拼接完整译文
        StringBuilder fullText = new StringBuilder();
        for (CollabChapterTask chapter : chapters) {
            if (chapter.getTargetText() != null && !chapter.getTargetText().isEmpty()) {
                if (chapter.getTitle() != null && !chapter.getTitle().isEmpty()) {
                    fullText.append("\n\n=== ").append(chapter.getTitle()).append(" ===\n\n");
                }
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
        String translatedPath = buildTranslatedPath(matchedDoc.getPath());
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

    private String buildTranslatedPath(String originalPath) {
        int lastDot = originalPath.lastIndexOf('.');
        if (lastDot > 0) {
            return originalPath.substring(0, lastDot) + "_translated" + originalPath.substring(lastDot);
        }
        return originalPath + "_translated";
    }
}
