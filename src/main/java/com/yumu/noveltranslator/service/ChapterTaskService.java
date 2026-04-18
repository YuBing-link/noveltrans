package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.CollabChapterTask;
import com.yumu.noveltranslator.entity.CollabProject;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.enums.ChapterTaskStatus;
import com.yumu.noveltranslator.mapper.CollabChapterTaskMapper;
import com.yumu.noveltranslator.mapper.CollabProjectMapper;
import com.yumu.noveltranslator.mapper.UserMapper;
import com.yumu.noveltranslator.service.state.CollabStateMachine;
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
     * 获取项目章节列表
     */
    public List<ChapterTaskResponse> listByProjectId(Long projectId) {
        return chapterTaskMapper.selectByProjectId(projectId)
                .stream()
                .map(this::toChapterResponse)
                .collect(Collectors.toList());
    }

    /**
     * 获取章节详情
     */
    public ChapterTaskResponse getChapterById(Long chapterId) {
        CollabChapterTask task = getById(chapterId);
        if (task == null) {
            throw new IllegalArgumentException("章节不存在: " + chapterId);
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
     * 获取用户待处理的章节列表
     */
    public List<ChapterTaskResponse> listByAssigneeId(Long assigneeId) {
        return chapterTaskMapper.selectByAssigneeId(assigneeId)
                .stream()
                .map(this::toChapterResponse)
                .collect(Collectors.toList());
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
                project.setStatus("COMPLETED");
            }
            collabProjectMapper.updateById(project);
        }
    }

    private ChapterTaskResponse toChapterResponse(CollabChapterTask task) {
        ChapterTaskResponse resp = new ChapterTaskResponse();
        resp.setId(task.getId());
        resp.setChapterNumber(task.getChapterNumber());
        resp.setTitle(task.getTitle());
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
            User assignee = userMapper.selectById(task.getAssigneeId());
            if (assignee != null) {
                resp.setAssigneeName(assignee.getUsername());
            }
        }
        // 设置审校名称
        if (task.getReviewerId() != null) {
            User reviewer = userMapper.selectById(task.getReviewerId());
            if (reviewer != null) {
                resp.setReviewerName(reviewer.getUsername());
            }
        }

        return resp;
    }
}
