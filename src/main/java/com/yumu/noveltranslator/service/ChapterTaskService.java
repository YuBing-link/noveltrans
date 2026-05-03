package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.CollabChapterTask;
import com.yumu.noveltranslator.entity.CollabProject;
import com.yumu.noveltranslator.entity.CollabProjectMember;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.enums.CollabProjectStatus;
import com.yumu.noveltranslator.enums.ChapterTaskStatus;
import com.yumu.noveltranslator.enums.ProjectMemberRole;
import com.yumu.noveltranslator.mapper.CollabChapterTaskMapper;
import com.yumu.noveltranslator.mapper.CollabProjectMapper;
import com.yumu.noveltranslator.mapper.CollabProjectMemberMapper;
import com.yumu.noveltranslator.mapper.UserMapper;
import com.yumu.noveltranslator.service.state.CollabStateMachine;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 章节任务管理服务
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChapterTaskService extends ServiceImpl<CollabChapterTaskMapper, CollabChapterTask> {

    private final CollabChapterTaskMapper chapterTaskMapper;
    private final CollabProjectMapper collabProjectMapper;
    private final UserMapper userMapper;
    private final CollabProjectMemberMapper projectMemberMapper;
    private final CollabStateMachine collabStateMachine;

    /**
     * 创建章节
     */
    @Transactional
    public ChapterTaskResponse createChapter(Long projectId, Integer chapterNumber, String title, String sourceText, Long creatorId) {
        CollabProject project = collabProjectMapper.selectById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("项目不存在: " + projectId);
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
        save(task);

        log.info("创建章节: projectId={}, chapterNumber={}", projectId, chapterNumber);
        return toChapterResponse(task);
    }

    /**
     * 获取项目章节列表（分页）
     */
    public PageResponse<ChapterTaskResponse> listByProjectId(Long projectId, int page, int pageSize) {
        LambdaQueryWrapper<CollabChapterTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollabChapterTask::getProjectId, projectId)
               .eq(CollabChapterTask::getDeleted, 0)
               .orderByAsc(CollabChapterTask::getChapterNumber);
        Page<CollabChapterTask> pageParam = new Page<>(page, pageSize);
        Page<CollabChapterTask> resultPage = chapterTaskMapper.selectPage(pageParam, wrapper);

        // 批量加载关联用户，避免 N+1
        java.util.Set<Long> userIds = new java.util.HashSet<>();
        for (CollabChapterTask task : resultPage.getRecords()) {
            if (task.getAssigneeId() != null) userIds.add(task.getAssigneeId());
            if (task.getReviewerId() != null) userIds.add(task.getReviewerId());
        }
        java.util.Map<Long, User> userMap = new java.util.HashMap<>();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            for (User user : users) {
                userMap.put(user.getId(), user);
            }
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
        CollabChapterTask task = getById(chapterId);
        if (task == null) {
            throw new IllegalArgumentException("章节不存在: " + chapterId);
        }
        com.yumu.noveltranslator.entity.CollabProjectMember member = projectMemberMapper.selectByProjectAndUser(task.getProjectId(), userId);
        if (member == null) {
            throw new SecurityException("无权访问该章节");
        }
        return toChapterResponse(task);
    }

    /**
     * 分配译者
     */
    @Transactional
    public ChapterTaskResponse assignChapter(Long chapterId, Long assigneeId, Long assignerId) {
        CollabChapterTask task = getById(chapterId);
        if (task == null) {
            throw new IllegalArgumentException("章节不存在: " + chapterId);
        }
        // 权限校验：分配者必须是项目OWNER
        CollabProjectMember assigner = projectMemberMapper.selectByProjectAndUser(task.getProjectId(), assignerId);
        if (assigner == null || !ProjectMemberRole.OWNER.getValue().equals(assigner.getRole())) {
            throw new SecurityException("无权分配章节，只有项目所有者可以分配");
        }

        ChapterTaskStatus current = ChapterTaskStatus.fromValue(task.getStatus());
        collabStateMachine.validateChapterTransition(current, ChapterTaskStatus.TRANSLATING);

        task.setAssigneeId(assigneeId);
        task.setStatus(ChapterTaskStatus.TRANSLATING.getValue());
        task.setAssignedTime(LocalDateTime.now());
        task.setProgress(0);
        updateById(task);

        log.info("分配章节: chapterId={}, assigneeId={}", chapterId, assigneeId);
        return toChapterResponse(task);
    }

    /**
     * 译者提交章节
     */
    @Transactional
    public ChapterTaskResponse submitChapter(Long chapterId, String translatedText) {
        CollabChapterTask task = getById(chapterId);
        if (task == null) {
            throw new IllegalArgumentException("章节不存在: " + chapterId);
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
            updateById(task);
            return toChapterResponse(task);
        }

        collabStateMachine.validateChapterTransition(current, ChapterTaskStatus.SUBMITTED);

        task.setTargetText(translatedText);
        task.setStatus(ChapterTaskStatus.SUBMITTED.getValue());
        task.setSubmittedTime(LocalDateTime.now());
        task.setProgress(100);
        if (translatedText != null) {
            task.setTargetWordCount(translatedText.length());
        }
        updateById(task);

        log.info("提交章节: chapterId={}", chapterId);
        return toChapterResponse(task);
    }

    /**
     * 审校审核章节
     */
    @Transactional
    public ChapterTaskResponse reviewChapter(Long chapterId, Boolean approved, String comment, Long reviewerId) {
        CollabChapterTask task = getById(chapterId);
        if (task == null) {
            throw new IllegalArgumentException("章节不存在: " + chapterId);
        }
        // 权限校验：审核者必须是项目REVIEWER或OWNER
        CollabProjectMember reviewer = projectMemberMapper.selectByProjectAndUser(task.getProjectId(), reviewerId);
        if (reviewer == null) {
            throw new SecurityException("无权访问该项目");
        }
        if (!ProjectMemberRole.REVIEWER.getValue().equals(reviewer.getRole())
                && !ProjectMemberRole.OWNER.getValue().equals(reviewer.getRole())) {
            throw new SecurityException("无权审核该章节，只有审校或项目所有者可以审核");
        }

        ChapterTaskStatus current = ChapterTaskStatus.fromValue(task.getStatus());
        collabStateMachine.validateChapterTransition(current, ChapterTaskStatus.REVIEWING);

        task.setReviewerId(reviewerId);
        task.setReviewComment(comment);
        task.setReviewedTime(LocalDateTime.now());

        if (approved) {
            ChapterTaskStatus approvedStatus = ChapterTaskStatus.APPROVED;
            collabStateMachine.validateChapterTransition(ChapterTaskStatus.REVIEWING, approvedStatus);
            task.setStatus(ChapterTaskStatus.APPROVED.getValue());
            task.setCompletedTime(LocalDateTime.now());
            log.info("审核通过: chapterId={}", chapterId);
        } else {
            ChapterTaskStatus rejectedStatus = ChapterTaskStatus.REJECTED;
            collabStateMachine.validateChapterTransition(ChapterTaskStatus.REVIEWING, rejectedStatus);
            task.setStatus(ChapterTaskStatus.REJECTED.getValue());
            task.setProgress(0);
            task.setSubmittedTime(null);
            log.info("审核驳回: chapterId={}, reason={}", chapterId, comment);
        }

        updateById(task);

        // 如果 APPROVED，自动转为 COMPLETED
        if (approved) {
            task.setStatus(ChapterTaskStatus.COMPLETED.getValue());
            updateById(task);
            updateProjectProgress(task.getProjectId());
        }

        return toChapterResponse(task);
    }

    /**
     * 获取用户待处理的章节列表（分页）
     */
    public PageResponse<ChapterTaskResponse> listByAssigneeId(Long assigneeId, int page, int pageSize) {
        LambdaQueryWrapper<CollabChapterTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollabChapterTask::getAssigneeId, assigneeId)
               .eq(CollabChapterTask::getDeleted, 0)
               .in(CollabChapterTask::getStatus,
                   ChapterTaskStatus.TRANSLATING.getValue(),
                   ChapterTaskStatus.SUBMITTED.getValue())
               .orderByDesc(CollabChapterTask::getUpdateTime);
        Page<CollabChapterTask> pageParam = new Page<>(page, pageSize);
        Page<CollabChapterTask> resultPage = chapterTaskMapper.selectPage(pageParam, wrapper);
        List<ChapterTaskResponse> list = resultPage.getRecords().stream()
                .map(this::toChapterResponse)
                .collect(Collectors.toList());
        return PageResponse.of(page, pageSize, resultPage.getTotal(), list);
    }

    /**
     * 更新项目整体进度
     */
    private void updateProjectProgress(Long projectId) {
        List<CollabChapterTask> tasks = chapterTaskMapper.selectByProjectId(projectId);
        if (tasks.isEmpty()) {
            return;
        }

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

    private ChapterTaskResponse toChapterResponse(CollabChapterTask task) {
        return toChapterResponse(task, java.util.Map.of());
    }

    private ChapterTaskResponse toChapterResponse(CollabChapterTask task, java.util.Map<Long, User> userMap) {
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
                assignee = userMapper.selectById(task.getAssigneeId());
            }
            if (assignee != null) {
                resp.setAssigneeName(assignee.getUsername());
            }
        }
        // 设置审校名称
        if (task.getReviewerId() != null) {
            User reviewer = userMap.get(task.getReviewerId());
            if (reviewer == null) {
                reviewer = userMapper.selectById(task.getReviewerId());
            }
            if (reviewer != null) {
                resp.setReviewerName(reviewer.getUsername());
            }
        }

        return resp;
    }
}
