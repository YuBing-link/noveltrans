package com.yumu.noveltranslator.domain.service;
import com.yumu.noveltranslator.application.service.RagTranslationApplicationService;

import com.yumu.noveltranslator.domain.model.AiGlossary;
import com.yumu.noveltranslator.domain.model.CollabChapterTask;
import com.yumu.noveltranslator.domain.model.CollabProject;
import com.yumu.noveltranslator.domain.model.Document;
import com.yumu.noveltranslator.domain.model.Glossary;
import com.yumu.noveltranslator.domain.model.TranslationTask;
import com.yumu.noveltranslator.enums.ChapterTaskStatus;
import com.yumu.noveltranslator.enums.CollabProjectStatus;
import com.yumu.noveltranslator.enums.TranslationMode;
import com.yumu.noveltranslator.enums.TranslationStatus;
import com.yumu.noveltranslator.port.out.TeamTranslationPort;
import com.yumu.noveltranslator.domain.service.TranslationPipeline;
import com.yumu.noveltranslator.domain.service.CollabStateMachine;
import com.yumu.noveltranslator.port.out.CollaborationRepositoryPort;
import com.yumu.noveltranslator.port.out.DocumentRepositoryPort;
import com.yumu.noveltranslator.port.out.TranslationRepositoryPort;
import com.yumu.noveltranslator.port.out.TranslationCachePort;
import com.yumu.noveltranslator.port.out.GlossaryRepositoryPort;
import com.yumu.noveltranslator.util.ExternalResponseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import com.yumu.noveltranslator.port.out.UserRepositoryPort;

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
    private static final long RETRY_BASE_DELAY_MS = 2000;
    private static final long RETRY_MAX_DELAY_MS = 30000;

    /**
     * 虚拟线程执行器，用于并发翻译章节（统一生命周期管理）
     */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 内存映射跟踪章节重试次数（服务重启后由 init() 从 DB 恢复）
     * 使用 AtomicInteger 保证并发安全的读-改-写
     */
    private final Map<Long, AtomicInteger> retryCounterMap = new ConcurrentHashMap<>();

    private final CollaborationRepositoryPort collabPort;
    private final DocumentRepositoryPort documentPort;
    private final TranslationRepositoryPort translationPort;
    private final TeamTranslationPort teamTranslationPort;
    private final TranslationCachePort cachePort;
    private final EntityConsistencyService entityConsistencyService;
    private final GlossaryRepositoryPort glossaryPort;
    private final RagTranslationApplicationService ragTranslationService;
    private final AiGlossaryService aiGlossaryService;
    private final TranslationPostProcessingService postProcessingService;
    private final CollabStateMachine collabStateMachine;
    private final UserRepositoryPort userRepositoryPort;

    /**
     * 启动时从数据库恢复重试计数
     */
    @PostConstruct
    public void init() {
        try {
            List<CollabChapterTask> chaptersWithRetries = collabPort.findChaptersWithRetryCountGreaterThan(0);
            for (CollabChapterTask chapter : chaptersWithRetries) {
                retryCounterMap.put(chapter.getId(), new AtomicInteger(chapter.getRetryCount()));
            }
            if (!chaptersWithRetries.isEmpty()) {
                log.info("从数据库恢复 {} 个章节的重试计数", chaptersWithRetries.size());
            }
        } catch (Exception e) {
            log.warn("初始化重试计数失败: {}", e.getMessage());
        }
    }

    /**
     * 服务关闭时优雅停止翻译线程池
     */
    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("翻译线程池 30 秒内未关闭，强制执行 shutdownNow");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 启动多 Agent 协作翻译
     */
    public void startMultiAgentTranslation(Long projectId) {
        // 恢复上次中断的翻译（TRANSLATING 状态的章节回退到 UNASSIGNED）
        recoverStuckChapters(projectId);

        List<CollabChapterTask> chapters = collabPort.findChapterTasksByProjectIdAndStatus(
                projectId, ChapterTaskStatus.UNASSIGNED.getValue());

        if (chapters.isEmpty()) {
            log.info("项目没有待翻译的章节: projectId={}", projectId);
            return;
        }

        CollabProject project = collabPort.findProjectById(projectId).orElse(null);
        if (project == null) {
            log.error("项目不存在: projectId={}", projectId);
            return;
        }

        log.info("启动多 Agent 翻译: projectId={}, chapters={}", projectId, chapters.size());

        // 更新关联文档状态为处理中
        if (project.getDocumentId() != null) {
            Document doc = documentPort.findById(project.getDocumentId()).orElse(null);
            if (doc != null) {
                doc.setStatus(TranslationStatus.PROCESSING.getValue());
                doc.setUpdateTime(LocalDateTime.now());
                documentPort.update(doc);
            }
        }

        CountDownLatch latch = new CountDownLatch(chapters.size());
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        Semaphore agentSemaphore = new Semaphore(MAX_CONCURRENT_AGENTS);

        for (CollabChapterTask chapter : chapters) {
            executor.submit(() -> {
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
                    log.error("Agent 翻译章节异常: chapterId={}", chapter.getId(), e);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 异步等待所有 Agent 完成，完成后更新项目进度并拼接完整文档
        executor.submit(() -> {
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
        collabStateMachine.transitionChapter(chapter, ChapterTaskStatus.TRANSLATING);
        chapter.setAssignedTime(LocalDateTime.now());
        chapter.setProgress(50);
        collabPort.updateChapterTask(chapter);

        String sourceLang = project.getSourceLang();
        String targetLang = project.getTargetLang();

        try {
            // 获取 userId（RAG 需要用户上下文）
            Long userId = chapter.getAssigneeId();
            if (userId == null) {
                CollabProject proj = collabPort.findProjectById(chapter.getProjectId()).orElse(null);
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
                        .collect(Collectors.toSet());
                for (Glossary aiTerm : aiGlossaryGlossaryTerms) {
                    if (!userTermKeys.contains(aiTerm.getSourceWord())) {
                        glossaryTerms.add(aiTerm);
                    }
                }
                log.info("AI 术语表注入: {} 个术语（过滤后）", aiGlossaryGlossaryTerms.size());
            }

            // 推导小说类型
            String novelType = deriveNovelType(project);

            // 查询用户等级用于限流和配额
            String userLevel = userRepositoryPort.findById(userId)
                    .map(com.yumu.noveltranslator.domain.model.User::getUserLevel)
                    .orElse(null);

            // 构建 Pipeline 并执行完整管线（L1→L2→L3→L4=Team）
            TranslationPipeline pipeline = new TranslationPipeline(
                    cachePort, ragTranslationService, entityConsistencyService,
                    null, postProcessingService, teamTranslationPort, userId, userLevel, chapter.getProjectId().toString(), glossaryTerms);

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
                // 经 SUBMITTED → REVIEWING 中间状态转入 REJECTED
                collabStateMachine.transitionChapter(chapter, ChapterTaskStatus.SUBMITTED);
                collabStateMachine.transitionChapter(chapter, ChapterTaskStatus.REVIEWING);
                collabStateMachine.transitionChapter(chapter, ChapterTaskStatus.REJECTED);
                chapter.setReviewComment("翻译异常（已重试 " + retryCount + " 次）: " + e.getMessage());
            } else {
                incrementRetryCount(chapter);
                int newRetry = retryCount + 1;
                chapter.setRetryCount(newRetry);
                collabStateMachine.transitionChapter(chapter, ChapterTaskStatus.UNASSIGNED);
                chapter.setReviewComment("翻译异常，等待重试（第 " + newRetry + " 次）: " + e.getMessage());
                // 指数退避，避免立即重试
                sleepWithBackoff(retryCount, e);
            }
            collabPort.updateChapterTask(chapter);
            return false;
        }
    }

    /**
     * 恢复上次中断的翻译：将 TRANSLATING 状态的章节回退到 UNASSIGNED
     */
    private void recoverStuckChapters(Long projectId) {
        List<CollabChapterTask> stuckChapters = collabPort.findChapterTasksByProjectIdAndStatus(
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
            collabStateMachine.transitionChapter(chapter, ChapterTaskStatus.UNASSIGNED);
            chapter.setReviewComment("翻译中断，自动重试（第 " + (retryCount + 2) + " 次）");
            chapter.setRetryCount(retryCount + 1);
            collabPort.updateChapterTask(chapter);
            log.info("章节 {} 回退到 UNASSIGNED，准备重试", chapter.getId());
        }
    }

    /**
     * 获取章节重试次数（原子读）
     */
    private int getRetryCount(CollabChapterTask chapter) {
        AtomicInteger counter = retryCounterMap.get(chapter.getId());
        return counter == null ? 0 : counter.get();
    }

    /**
     * 增加章节重试次数（原子递增）
     */
    private void incrementRetryCount(CollabChapterTask chapter) {
        retryCounterMap.computeIfAbsent(chapter.getId(), k -> new AtomicInteger(0)).incrementAndGet();
    }

    /**
     * 定时清理卡在 TRANSLATING 状态的章节（每 5 分钟执行一次）
     * 使用 CAS 单行更新保证并发安全，不依赖事务
     */
    @Scheduled(fixedRate = 300000)
    public void cleanupStuckChapters() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        List<CollabChapterTask> stuckChapters = collabPort.findChapterTasksByStatusAndUpdateTimeBefore(
                ChapterTaskStatus.TRANSLATING.getValue(), cutoff);

        if (stuckChapters.isEmpty()) {
            return;
        }

        String expectedStatus = ChapterTaskStatus.TRANSLATING.getValue();
        log.info("定时清理: 发现 {} 个卡住超过 30 分钟的章节", stuckChapters.size());
        for (CollabChapterTask chapter : stuckChapters) {
            int retryCount = getRetryCount(chapter);
            if (retryCount >= MAX_RETRY_COUNT) {
                // CAS 直接转到 REJECTED
                int rows = collabPort.casRejectChapter(
                        chapter.getId(), expectedStatus, retryCount,
                        "翻译超时（已重试 " + retryCount + " 次），自动终止");
                if (rows > 0) {
                    chapter.setStatus(ChapterTaskStatus.REJECTED.getValue());
                    chapter.setRetryCount(retryCount);
                    log.info("定时清理: 章节 {} 已达到最大重试次数，标记为 REJECTED", chapter.getId());
                } else {
                    log.debug("定时清理: 章节 {} 状态已被其他线程修改，跳过", chapter.getId());
                }
            } else {
                incrementRetryCount(chapter);
                int newRetry = retryCount + 1;
                String comment = "翻译超时，自动重试（第 " + newRetry + " 次）";
                int rows = collabPort.casResetChapterToUnassigned(
                        chapter.getId(), expectedStatus, newRetry, comment);
                if (rows > 0) {
                    chapter.setStatus(ChapterTaskStatus.UNASSIGNED.getValue());
                    chapter.setRetryCount(newRetry);
                    chapter.setReviewComment(comment);
                    log.info("定时清理: 章节 {} 回退到 UNASSIGNED", chapter.getId());
                } else {
                    // CAS 失败，回退内存计数避免虚高
                    AtomicInteger counter = retryCounterMap.get(chapter.getId());
                    if (counter != null && counter.get() > 0) {
                        counter.decrementAndGet();
                    }
                    log.debug("定时清理: 章节 {} 状态已被其他线程修改，跳过", chapter.getId());
                }
            }
        }
    }

    /**
     * 指数退避等待，避免重试风暴
     */
    private void sleepWithBackoff(int retryCount, Exception e) {
        long delay;
        String msg = e.getMessage();
        boolean isRateLimited = msg != null && (msg.contains("429") || msg.contains("rate limit") || msg.contains("Too Many Requests"));

        if (isRateLimited) {
            delay = Math.max(5000, RETRY_BASE_DELAY_MS * (1L << retryCount));
        } else {
            long baseDelay = RETRY_BASE_DELAY_MS * (1L << retryCount);
            long jitter = (long) (Math.random() * baseDelay * 0.5);
            delay = baseDelay + jitter;
        }
        delay = Math.min(delay, RETRY_MAX_DELAY_MS);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 加载项目所有者的术语表中在原文中出现的词条
     */
    private List<Glossary> loadGlossaryTermsForProject(Long userId, String sourceText) {
        if (userId == null) {
            return List.of();
        }

        try {
            List<Glossary> allTerms = glossaryPort.findActiveGlossaryByUserId(userId);

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
            // 缓存命中：经 SUBMITTED → REVIEWING → APPROVED → COMPLETED 完成自动审核
            collabStateMachine.transitionChapter(chapter, ChapterTaskStatus.SUBMITTED);
            collabStateMachine.transitionChapter(chapter, ChapterTaskStatus.REVIEWING);
            collabStateMachine.transitionChapter(chapter, ChapterTaskStatus.APPROVED);
            chapter.setProgress(100);
            chapter.setCompletedTime(LocalDateTime.now());
            chapter.setSubmittedTime(LocalDateTime.now());
            collabStateMachine.transitionChapter(chapter, ChapterTaskStatus.COMPLETED);
        } else {
            // AI 新翻译：标记为 SUBMITTED（待审校）
            collabStateMachine.transitionChapter(chapter, ChapterTaskStatus.SUBMITTED);
            chapter.setProgress(80);
            chapter.setSubmittedTime(LocalDateTime.now());
        }

        collabPort.updateChapterTask(chapter);
    }

    /**
     * 更新项目整体进度
     */
    private void updateProjectProgress(Long projectId) {
        List<CollabChapterTask> tasks = collabPort.findChapterTasksByProjectId(projectId);
        if (tasks.isEmpty()) return;

        long completed = tasks.stream()
                .filter(t -> ChapterTaskStatus.COMPLETED.getValue().equals(t.getStatus()))
                .count();

        int progress = (int) Math.round((double) completed / tasks.size() * 100);

        CollabProject project = collabPort.findProjectById(projectId).orElse(null);
        if (project != null) {
            project.setProgress(progress);
            if (progress == 100) {
                try {
                    collabStateMachine.transitionProject(project, CollabProjectStatus.COMPLETED);
                } catch (IllegalStateException e) {
                    log.debug("项目无法转换为 COMPLETED 状态（当前状态不允许）: projectId={}", projectId);
                }
            }
            collabPort.updateProject(project);
        }
    }

    /**
     * 所有章节翻译完成后，按 chapterNumber 顺序拼接完整翻译文档
     */
    private void assembleCompleteDocument(Long projectId) {
        // 按 chapterNumber 升序获取所有章节
        List<CollabChapterTask> chapters = collabPort.findChapterTasksByProjectId(projectId);
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
        CollabProject project = collabPort.findProjectById(projectId).orElse(null);
        if (project == null || project.getDocumentId() == null) {
            log.warn("项目未关联 Document，无法保存完整译文: projectId={}", projectId);
            return;
        }

        Document matchedDoc = documentPort.findById(project.getDocumentId()).orElse(null);
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
        TranslationTask task = translationPort.findTaskByDocumentId(matchedDoc.getId()).orElse(null);
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
            translationPort.saveTask(task);
        } else {
            task.setStatus("completed");
            translationPort.updateTask(task);
        }
        log.info("团队模式翻译任务已更新: taskId={}, status=completed", task.getId());

        // 更新 Document 状态为 completed
        matchedDoc.setStatus("completed");
        matchedDoc.setTaskId(task.getTaskId());
        documentPort.update(matchedDoc);
        log.info("团队模式文档状态已更新: documentId={}, status=completed, taskId={}", matchedDoc.getId(), task.getTaskId());
    }
}
