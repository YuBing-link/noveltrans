package com.yumu.noveltranslator.domain.service;

import com.yumu.noveltranslator.dto.collab.ChapterTaskResponse;
import com.yumu.noveltranslator.dto.common.PageResponse;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabChapterTask;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabProject;
import com.yumu.noveltranslator.adapter.out.persistence.entity.User;
import com.yumu.noveltranslator.enums.CollabProjectStatus;
import com.yumu.noveltranslator.enums.ChapterTaskStatus;
import com.yumu.noveltranslator.enums.ProjectMemberRole;
import com.yumu.noveltranslator.exception.BusinessException;
import com.yumu.noveltranslator.enums.ErrorCodeEnum;
import com.yumu.noveltranslator.port.out.CollaborationRepositoryPort;
import com.yumu.noveltranslator.port.out.UserRepositoryPort;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 章节任务管理服务
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChapterTaskService {

    private final CollaborationRepositoryPort collabPort;
    private final UserRepositoryPort userPort;
    private final CollabStateMachine collabStateMachine;
    private final CollabEventPublisher collabEventPublisher;

    /**
     * 创建章节
     */
    @Transactional
    public ChapterTaskResponse createChapter(Long projectId, Integer chapterNumber, String title, String sourceText, Long creatorId) {
        CollabProject project = collabPort.findProjectById(projectId).orElse(null);
        if (project == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "项目不存在: " + projectId);
        }

        CollabChapterTask task = new CollabChapterTask();
        task.setProjectId(projectId);
        task.setChapterNumber(chapterNumber);
        task.setTitle(title);
        task.setSourceText(sourceText);
        task.setStatus(ChapterTaskStatus.UNASSIGNED.getValue());
        task.setProgress(0);
        if (sourceText != null) {
            task.setSourceWordCount(sourceText.length());
        }
        collabPort.saveChapterTask(task);

        log.info("创建章节: projectId={}, chapterNumber={}", projectId, chapterNumber);
        return toChapterResponse(task);
    }

    /**
     * 获取项目章节列表（分页）
     */
    public PageResponse<ChapterTaskResponse> listByProjectId(Long projectId, int page, int pageSize) {
        IPage<CollabChapterTask> resultPage = collabPort.findChapterTasksByProjectIdPaged(projectId, page, pageSize);

        // 批量加载关联用户，避免 N+1
        Set<Long> userIds = new HashSet<>();
        for (CollabChapterTask task : resultPage.getRecords()) {
            if (task.getAssigneeId() != null) userIds.add(task.getAssigneeId());
            if (task.getReviewerId() != null) userIds.add(task.getReviewerId());
        }
        Map<Long, User> userMap = new HashMap<>();
        for (Long uid : userIds) {
            userPort.findById(uid).ifPresent(u -> userMap.put(uid, u));
        }

        List<ChapterTaskResponse> list = resultPage.getRecords().stream()
                .map(task -> toChapterResponse(task, userMap))
                .collect(Collectors.toList());
        return PageResponse.of(page, pageSize, resultPage.getTotal(), list);
    }

    /**
     * 获取章节详情（含权限检查）
     */
    public ChapterTaskResponse getChapterById(Long chapterId, Long userId) {
        CollabChapterTask task = collabPort.findChapterTaskById(chapterId).orElse(null);
        if (task == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "章节不存在: " + chapterId);
        }
        var member = collabPort.findMemberByProjectAndUser(task.getProjectId(), userId);
        if (member == null) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权访问该章节");
        }
        return toChapterResponse(task);
    }

    /**
     * 分配译者
     */
    @Transactional
    public ChapterTaskResponse assignChapter(Long chapterId, Long assigneeId, Long assignerId) {
        CollabChapterTask task = collabPort.findChapterTaskById(chapterId).orElse(null);
        if (task == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "章节不存在: " + chapterId);
        }
        // 权限校验：分配者必须是项目OWNER
        var assigner = collabPort.findMemberByProjectAndUser(task.getProjectId(), assignerId);
        if (assigner == null || !ProjectMemberRole.OWNER.getValue().equals(assigner.getRole())) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权分配章节，只有项目所有者可以分配");
        }

        collabStateMachine.transitionChapter(task, ChapterTaskStatus.TRANSLATING);

        task.setAssigneeId(assigneeId);
        task.setAssignedTime(LocalDateTime.now());
        task.setProgress(0);
        collabPort.updateChapterTask(task);

        final Long finalProjectId = task.getProjectId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    collabEventPublisher.publishChapterUpdate(finalProjectId, chapterId, String.valueOf(assigneeId), "assigned");
                }
            });
        }

        log.info("分配章节: chapterId={}, assigneeId={}", chapterId, assigneeId);
        return toChapterResponse(task);
    }

    /**
     * 译者提交章节
     */
    @Transactional
    public ChapterTaskResponse submitChapter(Long chapterId, String translatedText) {
        CollabChapterTask task = collabPort.findChapterTaskById(chapterId).orElse(null);
        if (task == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "章节不存在: " + chapterId);
        }

        ChapterTaskStatus current = ChapterTaskStatus.fromValue(task.getStatus());
        // 已提交状态再次提交：译者更新译文（尚未审核），允许幂等覆盖
        if (current == ChapterTaskStatus.SUBMITTED) {
            log.info("译者重新提交已提交的章节: chapterId={}", chapterId);
            task.setTargetText(translatedText);
            task.setSubmittedTime(LocalDateTime.now());
            task.setProgress(100);
            if (translatedText != null) {
                task.setTargetWordCount(translatedText.length());
            }
            collabPort.updateChapterTask(task);
            return toChapterResponse(task);
        }

        collabStateMachine.transitionChapter(task, ChapterTaskStatus.SUBMITTED);

        task.setTargetText(translatedText);
        task.setSubmittedTime(LocalDateTime.now());
        task.setProgress(100);
        if (translatedText != null) {
            task.setTargetWordCount(translatedText.length());
        }
        collabPort.updateChapterTask(task);

        final Long finalProjectId = task.getProjectId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    collabEventPublisher.publishChapterUpdate(finalProjectId, chapterId, String.valueOf(task.getAssigneeId()), "submitted");
                }
            });
        }

        log.info("提交章节: chapterId={}", chapterId);
        return toChapterResponse(task);
    }

    /**
     * 审校审核章节
     */
    @Transactional
    public ChapterTaskResponse reviewChapter(Long chapterId, Boolean approved, String comment, Long reviewerId) {
        CollabChapterTask task = collabPort.findChapterTaskById(chapterId).orElse(null);
        if (task == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "章节不存在: " + chapterId);
        }
        // 权限校验：审核者必须是项目REVIEWER或OWNER
        var reviewer = collabPort.findMemberByProjectAndUser(task.getProjectId(), reviewerId);
        if (reviewer == null) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权访问该项目");
        }
        if (!ProjectMemberRole.REVIEWER.getValue().equals(reviewer.getRole())
                && !ProjectMemberRole.OWNER.getValue().equals(reviewer.getRole())) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权审核该章节，只有审校或项目所有者可以审核");
        }

        // 先转入 REVIEWING 中间状态
        collabStateMachine.transitionChapter(task, ChapterTaskStatus.REVIEWING);

        task.setReviewerId(reviewerId);
        task.setReviewComment(comment);
        task.setReviewedTime(LocalDateTime.now());

        if (approved) {
            collabStateMachine.transitionChapter(task, ChapterTaskStatus.APPROVED);
            task.setCompletedTime(LocalDateTime.now());
            log.info("审核通过: chapterId={}", chapterId);
        } else {
            collabStateMachine.transitionChapter(task, ChapterTaskStatus.REJECTED);
            task.setProgress(0);
            task.setSubmittedTime(null);
            log.info("审核驳回: chapterId={}, reason={}", chapterId, comment);
        }

        collabPort.updateChapterTask(task);

        // 如果 APPROVED，自动转为 COMPLETED
        if (approved) {
            collabStateMachine.transitionChapter(task, ChapterTaskStatus.COMPLETED);
            collabPort.updateChapterTask(task);
            updateProjectProgress(task.getProjectId());
        }

        final Long finalProjectId = task.getProjectId();
        final String action = approved ? "updated" : "submitted";
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    collabEventPublisher.publishChapterUpdate(finalProjectId, chapterId, String.valueOf(reviewerId), action);
                    if (comment != null && !comment.isBlank()) {
                        collabEventPublisher.publishCommentAdded(finalProjectId, chapterId, reviewerId, comment);
                    }
                }
            });
        }

        return toChapterResponse(task);
    }

    /**
     * 获取用户待处理的章节列表（分页）
     */
    public PageResponse<ChapterTaskResponse> listByAssigneeId(Long assigneeId, int page, int pageSize) {
        IPage<CollabChapterTask> resultPage = collabPort.findChapterTasksByAssigneeIdPaged(
                assigneeId,
                List.of(ChapterTaskStatus.TRANSLATING.getValue(), ChapterTaskStatus.SUBMITTED.getValue()),
                page, pageSize);

        Set<Long> userIds = new HashSet<>();
        for (CollabChapterTask task : resultPage.getRecords()) {
            if (task.getAssigneeId() != null) userIds.add(task.getAssigneeId());
            if (task.getReviewerId() != null) userIds.add(task.getReviewerId());
        }
        Map<Long, User> userMap = new HashMap<>();
        for (Long uid : userIds) {
            userPort.findById(uid).ifPresent(u -> userMap.put(uid, u));
        }

        List<ChapterTaskResponse> list = resultPage.getRecords().stream()
                .map(task -> toChapterResponse(task, userMap))
                .collect(Collectors.toList());
        return PageResponse.of(page, pageSize, resultPage.getTotal(), list);
    }

    /**
     * 更新项目整体进度
     */
    private void updateProjectProgress(Long projectId) {
        List<CollabChapterTask> tasks = collabPort.findChapterTasksByProjectId(projectId);
        if (tasks.isEmpty()) {
            return;
        }

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

    private ChapterTaskResponse toChapterResponse(CollabChapterTask task) {
        return toChapterResponse(task, Map.of());
    }

    private ChapterTaskResponse toChapterResponse(CollabChapterTask task, Map<Long, User> userMap) {
        ChapterTaskResponse resp = new ChapterTaskResponse();
        resp.setId(task.getId());
        resp.setChapterNumber(task.getChapterNumber());
        resp.setTitle(task.getTitle());
        resp.setSourceText(task.getSourceText());
        resp.setTranslatedText(task.getTargetText());
        resp.setStatus(task.getStatus());
        resp.setProgress(task.getProgress());
        resp.setAssigneeId(task.getAssigneeId());
        resp.setReviewerId(task.getReviewerId());
        resp.setReviewComment(task.getReviewComment());
        resp.setSourceWordCount(task.getSourceWordCount());
        resp.setTargetWordCount(task.getTargetWordCount());
        resp.setAssignedTime(task.getAssignedTime());
        resp.setSubmittedTime(task.getSubmittedTime());
        resp.setReviewedTime(task.getReviewedTime());
        resp.setCompletedTime(task.getCompletedTime());

        // 设置译员名称
        if (task.getAssigneeId() != null) {
            User assignee = userMap.get(task.getAssigneeId());
            if (assignee == null) {
                assignee = userPort.findById(task.getAssigneeId()).orElse(null);
            }
            if (assignee != null) {
                resp.setAssigneeName(assignee.getUsername());
            }
        }
        // 设置审校名称
        if (task.getReviewerId() != null) {
            User reviewer = userMap.get(task.getReviewerId());
            if (reviewer == null) {
                reviewer = userPort.findById(task.getReviewerId()).orElse(null);
            }
            if (reviewer != null) {
                resp.setReviewerName(reviewer.getUsername());
            }
        }

        return resp;
    }
}
