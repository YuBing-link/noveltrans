package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.RagTranslationResponse;
import com.yumu.noveltranslator.entity.AiGlossary;
import com.yumu.noveltranslator.entity.CollabChapterTask;
import com.yumu.noveltranslator.entity.CollabProject;
import com.yumu.noveltranslator.entity.Document;
import com.yumu.noveltranslator.entity.Glossary;
import com.yumu.noveltranslator.entity.TranslationTask;
import com.yumu.noveltranslator.enums.ChapterTaskStatus;
import com.yumu.noveltranslator.enums.CollabProjectStatus;
import com.yumu.noveltranslator.enums.TranslationMode;
import com.yumu.noveltranslator.enums.TranslationStatus;
import com.yumu.noveltranslator.mapper.CollabChapterTaskMapper;
import com.yumu.noveltranslator.mapper.CollabProjectMapper;
import com.yumu.noveltranslator.mapper.DocumentMapper;
import com.yumu.noveltranslator.mapper.GlossaryMapper;
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
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 多 Agent 协作翻译服务
 * 使用 AI 翻译团队（Agentscope 多 Agent）进行章节翻译
 *
 * 架构：Java = 编排器（缓存 + 实体一致性 + 占位符保护），Python = AI 翻译外包
 * 接入能力：三级缓存 + 实体一致性 + 术语表 + 占位符保护 + 章节拼接
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MultiAgentTranslationService {

    private static final int MAX_CONCURRENT_AGENTS = 5;
    private static final int MAX_RETRY_COUNT = 3;

    /**
     * 内存映射跟踪章节重试次数（服务重启后重置）
     */
    private static final Map<Long, Integer> retryCounterMap = new ConcurrentHashMap<>();

    private final CollabChapterTaskMapper chapterTaskMapper;
    private final CollabProjectMapper collabProjectMapper;
    private final DocumentMapper documentMapper;
    private final TranslationTaskMapper translationTaskMapper;
    private final TeamTranslationService teamTranslationService;
    private final TranslationCacheService cacheService;
    private final EntityConsistencyService entityConsistencyService;
    private final GlossaryMapper glossaryMapper;
    private final RagTranslationService ragTranslationService;
    private final AiGlossaryService aiGlossaryService;
    private final TranslationPostProcessingService postProcessingService;

    /**
     * 启动多 Agent 协作翻译
     */
    public void startMultiAgentTranslation(Long projectId) {
        // 恢复上次中断的翻译（TRANSLATING 状态的章节回退到 UNASSIGNED）
        recoverStuckChapters(projectId);

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
     * 走 TranslationPipeline 完整管线（L1缓存→L2 RAG→L3实体一致性→L4团队翻译）
     * 翻译完成标记为 SUBMITTED（待审校状态）
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

        String sourceLang = project.getSourceLang();
        String targetLang = project.getTargetLang();

        try {
            // 获取 userId（RAG 需要用户上下文）
            Long userId = chapter.getAssigneeId();
            if (userId == null) {
                CollabProject proj = collabProjectMapper.selectById(chapter.getProjectId());
                if (proj != null) {
                    userId = proj.getOwnerId();
                }
            }

            // 加载术语表
            List<Glossary> glossaryTerms = loadGlossaryTermsForProject(userId, sourceText);

            // 加载 AI 术语表（仅已确认的术语，优先级低于用户术语表）
            List<AiGlossary> aiGlossaryTerms = aiGlossaryService.getProjectGlossary(project.getId());
            List<Glossary> aiGlossaryGlossaryTerms = aiGlossaryTerms.stream()
                    .filter(t -> sourceText.contains(t.getSourceWord()))
                    .map(t -> {
                        Glossary g = new Glossary();
                        g.setSourceWord(t.getSourceWord());
                        g.setTargetWord(t.getTargetWord());
                        return g;
                    })
                    .toList();

            // 合并：用户术语表优先，AI 术语表补充
            if (!aiGlossaryGlossaryTerms.isEmpty()) {
                Set<String> userTermKeys = glossaryTerms.stream()
                        .map(Glossary::getSourceWord)
                        .collect(java.util.stream.Collectors.toSet());
                for (Glossary aiTerm : aiGlossaryGlossaryTerms) {
                    if (!userTermKeys.contains(aiTerm.getSourceWord())) {
                        glossaryTerms.add(aiTerm);
                    }
                }
                log.info("AI 术语表注入: {} 个术语（过滤后）", aiGlossaryGlossaryTerms.size());
            }

            // 推导小说类型
            String novelType = deriveNovelType(project);

            // 构建 Pipeline 并执行完整管线（L1→L2→L3→L4=Team）
            TranslationPipeline pipeline = new TranslationPipeline(
                    cacheService, ragTranslationService, entityConsistencyService,
                    null, postProcessingService, teamTranslationService, userId, chapter.getProjectId().toString());

            String translated = pipeline.executeTeam(
                    sourceText, sourceLang, targetLang, TranslationMode.TEAM, novelType, glossaryTerms);

            if (translated == null || translated.trim().isEmpty()) {
                throw new RuntimeException("AI 翻译团队返回结果为空");
            }

            // 维护 AI 术语表：提取实体并保存
            try {
                List<String> entities = entityConsistencyService.extractEntitiesSegmented(sourceText, targetLang);
                if (!entities.isEmpty()) {
                    Map<String, String> entityTranslations = entityConsistencyService.translateEntities(entities, targetLang);
                    String contextExcerpt = sourceText.length() > 200 ? sourceText.substring(0, 200) : sourceText;
                    for (Map.Entry<String, String> entry : entityTranslations.entrySet()) {
                        aiGlossaryService.addTerm(
                                project.getId(),
                                entry.getKey(),
                                entry.getValue(),
                                contextExcerpt,
                                null,
                                chapter.getId()
                        );
                    }
                    log.info("AI 术语表维护完成: chapterId={}, 提取{}个实体", chapterId, entityTranslations.size());
                }
            } catch (Exception e) {
                log.warn("AI 术语表维护失败: {}", e.getMessage());
            }

            // 标记为 SUBMITTED（待审校）
            applyTranslationResult(chapter, sourceText, translated, false);

            log.info("章节翻译完成（待审校）: chapterId={}, 原文{}字, 译文{}字",
                    chapterId, sourceText.length(), translated.length());
            return true;

        } catch (Exception e) {
            log.error("章节翻译异常: chapterId={}, error={}", chapterId, e.getMessage(), e);
            int retryCount = getRetryCount(chapter);
            if (retryCount >= MAX_RETRY_COUNT) {
                log.error("章节 {} 已达到最大重试次数 {}，停止自动重试", chapterId, MAX_RETRY_COUNT);
                chapter.setStatus(ChapterTaskStatus.REJECTED.getValue());
                chapter.setReviewComment("翻译异常（已重试 " + retryCount + " 次）: " + e.getMessage());
            } else {
                chapter.setStatus(ChapterTaskStatus.UNASSIGNED.getValue());
                chapter.setReviewComment("翻译异常，等待重试（第 " + (retryCount + 1) + " 次）: " + e.getMessage());
            }
            chapterTaskMapper.updateById(chapter);
            return false;
        }
    }

    /**
     * 恢复上次中断的翻译：将 TRANSLATING 状态的章节回退到 UNASSIGNED
     */
    private void recoverStuckChapters(Long projectId) {
        List<CollabChapterTask> stuckChapters = chapterTaskMapper.selectByProjectIdAndStatus(
                projectId, ChapterTaskStatus.TRANSLATING.getValue());

        if (stuckChapters.isEmpty()) {
            return;
        }

        log.info("发现 {} 个翻译中断的章节，回退到待翻译状态", stuckChapters.size());
        for (CollabChapterTask chapter : stuckChapters) {
            // 检查重试次数
            int retryCount = getRetryCount(chapter);
            if (retryCount >= MAX_RETRY_COUNT) {
                log.warn("章节 {} 已重试 {} 次，跳过自动翻译", chapter.getId(), retryCount);
                continue;
            }
            // 增加重试计数并回退状态
            incrementRetryCount(chapter);
            chapter.setStatus(ChapterTaskStatus.UNASSIGNED.getValue());
            chapter.setReviewComment("翻译中断，自动重试（第 " + (retryCount + 1) + " 次）");
            chapterTaskMapper.updateById(chapter);
            log.info("章节 {} 回退到 UNASSIGNED，准备重试", chapter.getId());
        }
    }

    /**
     * 获取章节重试次数
     */
    private int getRetryCount(CollabChapterTask chapter) {
        return retryCounterMap.getOrDefault(chapter.getId(), 0);
    }

    /**
     * 增加章节重试次数
     */
    private void incrementRetryCount(CollabChapterTask chapter) {
        retryCounterMap.merge(chapter.getId(), 1, Integer::sum);
    }

    /**
     * 构建基础缓存键：SHA256(sourceText)[:16] + "_" + targetLang（不含模式标签）
     */
    private String buildBaseCacheKey(String sourceText, String targetLang) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sourceText.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) { // 8 bytes = 16 hex chars
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString() + "_" + targetLang;
        } catch (Exception e) {
            // fallback: 使用 hashCode
            log.warn("SHA-256 计算失败，使用 fallback cacheKey: {}", e.getMessage());
            return Math.abs(sourceText.hashCode()) + "_" + targetLang;
        }
    }

    /**
     * 构建缓存键：SHA256(sourceText)[:16] + "_" + targetLang
     * @deprecated 使用 {@link #buildBaseCacheKey(String, String)} 替代
     */
    @Deprecated
    private String buildCacheKey(String sourceText, String targetLang) {
        return buildBaseCacheKey(sourceText, targetLang);
    }

    /**
     * 加载项目所有者的术语表中在原文中出现的词条
     */
    private List<Glossary> loadGlossaryTermsForProject(Long userId, String sourceText) {
        if (userId == null) {
            return List.of();
        }

        try {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Glossary> query =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            query.eq(Glossary::getUserId, userId);
            List<Glossary> allTerms = glossaryMapper.selectList(query);

            // 过滤只保留在原文中出现的术语
            return allTerms.stream()
                    .filter(term -> term.getSourceWord() != null && sourceText.contains(term.getSourceWord()))
                    .toList();
        } catch (Exception e) {
            log.warn("加载术语表失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 从项目信息推导小说类型
     */
    private String deriveNovelType(CollabProject project) {
        if (project == null) {
            return "daily";
        }

        String description = project.getDescription();
        if (description == null || description.isBlank()) {
            return "daily";
        }

        // 简单的关键词匹配推导类型
        String lower = description.toLowerCase();
        if (lower.contains("fantasy") || lower.contains("奇幻") || lower.contains("玄幻")) {
            return "fantasy";
        }
        if (lower.contains("romance") || lower.contains("言情") || lower.contains("恋爱")) {
            return "romance";
        }
        if (lower.contains("scifi") || lower.contains("科幻")) {
            return "scifi";
        }
        if (lower.contains("mystery") || lower.contains("悬疑") || lower.contains("推理")) {
            return "mystery";
        }
        if (lower.contains("horror") || lower.contains("恐怖")) {
            return "horror";
        }
        if (lower.contains("daily") || lower.contains("日常") || lower.contains("生活")) {
            return "daily";
        }

        return "daily";
    }

    /**
     * 应用翻译结果到章节实体
     */
    private void applyTranslationResult(CollabChapterTask chapter, String sourceText, String translated, boolean fromCache) {
        chapter.setTargetText(translated);
        chapter.setTargetWordCount(translated.length());
        chapter.setSourceWordCount(sourceText.length());

        if (fromCache) {
            // 缓存命中直接标记为已完成（跳过审校流程）
            chapter.setProgress(100);
            chapter.setStatus(ChapterTaskStatus.COMPLETED.getValue());
            chapter.setCompletedTime(LocalDateTime.now());
        } else {
            // AI 新翻译：标记为 SUBMITTED（待审校）
            chapter.setProgress(80);
            chapter.setStatus(ChapterTaskStatus.SUBMITTED.getValue());
            chapter.setSubmittedTime(LocalDateTime.now());
        }

        chapterTaskMapper.updateById(chapter);
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
