package com.yumu.noveltranslator.adapter.out.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabChapterTask;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabComment;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabInviteCode;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabProject;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabProjectMember;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.CollabChapterTaskMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.CollabCommentMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.CollabInviteCodeMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.CollabProjectMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.CollabProjectMemberMapper;
import com.yumu.noveltranslator.port.out.CollaborationRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CollaborationRepositoryAdapter implements CollaborationRepositoryPort {

    private final CollabProjectMapper projectMapper;
    private final CollabProjectMemberMapper memberMapper;
    private final CollabChapterTaskMapper chapterTaskMapper;
    private final CollabCommentMapper commentMapper;
    private final CollabInviteCodeMapper inviteCodeMapper;

    @Override
    public List<CollabProject> findProjectsByOwnerId(Long ownerId) {
        return projectMapper.selectByOwnerId(ownerId);
    }

    @Override
    public List<CollabProject> findProjectsByMemberUserId(Long userId) {
        return projectMapper.selectByMemberUserId(userId);
    }

    @Override
    public void saveProject(CollabProject project) {
        projectMapper.insert(project);
    }

    @Override
    public void updateProject(CollabProject project) {
        projectMapper.updateById(project);
    }

    @Override
    public void deleteProject(Long id) {
        projectMapper.deleteById(id);
    }

    @Override
    public Optional<CollabProject> findProjectById(Long id) {
        return Optional.ofNullable(projectMapper.selectById(id));
    }

    @Override
    public List<CollabProjectMember> findMembersByProjectId(Long projectId) {
        return memberMapper.selectByProjectId(projectId);
    }

    @Override
    public CollabProjectMember findMemberByInviteCode(String inviteCode) {
        return memberMapper.selectByInviteCode(inviteCode);
    }

    @Override
    public int countMembersByProjectIdAndRole(Long projectId, String role) {
        return memberMapper.countByProjectIdAndRole(projectId, role);
    }

    @Override
    public CollabProjectMember findMemberByProjectAndUser(Long projectId, Long userId) {
        return memberMapper.selectByProjectAndUser(projectId, userId);
    }

    @Override
    public int countMembersByProjectId(Long projectId) {
        return memberMapper.countByProjectId(projectId);
    }

    @Override
    public void saveMember(CollabProjectMember member) {
        memberMapper.insert(member);
    }

    @Override
    public void updateMember(CollabProjectMember member) {
        memberMapper.updateById(member);
    }

    @Override
    public void deleteMembersByProjectId(Long projectId) {
        memberMapper.delete(new QueryWrapper<CollabProjectMember>().eq("project_id", projectId));
    }

    @Override
    public Optional<CollabProjectMember> findMemberById(Long id) {
        return Optional.ofNullable(memberMapper.selectById(id));
    }

    @Override
    public List<CollabChapterTask> findChapterTasksByProjectId(Long projectId) {
        return chapterTaskMapper.selectByProjectId(projectId);
    }

    @Override
    public List<CollabChapterTask> findChapterTasksByProjectIdAndStatus(Long projectId, String status) {
        return chapterTaskMapper.selectByProjectIdAndStatus(projectId, status);
    }

    @Override
    public IPage<CollabChapterTask> findChapterTasksByProjectIdPaged(Long projectId, int page, int pageSize) {
        LambdaQueryWrapper<CollabChapterTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollabChapterTask::getProjectId, projectId)
               .eq(CollabChapterTask::getDeleted, 0)
               .orderByAsc(CollabChapterTask::getChapterNumber);
        return chapterTaskMapper.selectPage(new Page<>(page, pageSize), wrapper);
    }

    @Override
    public IPage<CollabChapterTask> findChapterTasksByAssigneeIdPaged(Long assigneeId, List<String> statuses, int page, int pageSize) {
        LambdaQueryWrapper<CollabChapterTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollabChapterTask::getAssigneeId, assigneeId)
               .eq(CollabChapterTask::getDeleted, 0)
               .in(CollabChapterTask::getStatus, statuses)
               .orderByDesc(CollabChapterTask::getUpdateTime);
        return chapterTaskMapper.selectPage(new Page<>(page, pageSize), wrapper);
    }

    @Override
    public int countChapterTasksByProjectId(Long projectId) {
        return chapterTaskMapper.countByProjectId(projectId);
    }

    @Override
    public int countChapterTasksByProjectIdAndStatus(Long projectId, String status) {
        return chapterTaskMapper.countByProjectIdAndStatus(projectId, status);
    }

    @Override
    public List<CollabChapterTask> findChapterTasksByAssigneeId(Long assigneeId) {
        return chapterTaskMapper.selectByAssigneeId(assigneeId);
    }

    @Override
    public List<CollabChapterTask> findChapterTasksByStatusAndUpdateTimeBefore(String status, LocalDateTime cutoff) {
        return chapterTaskMapper.findByStatusAndUpdateTimeBefore(status, cutoff);
    }

    @Override
    public void updateChapterTaskRetryCount(Long id, int retryCount) {
        chapterTaskMapper.updateRetryCount(id, retryCount);
    }

    @Override
    public List<CollabChapterTask> findChaptersWithRetryCountGreaterThan(int retryCount) {
        LambdaQueryWrapper<CollabChapterTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.gt(CollabChapterTask::getRetryCount, retryCount)
               .eq(CollabChapterTask::getDeleted, 0);
        return chapterTaskMapper.selectList(wrapper);
    }

    @Override
    public void saveChapterTask(CollabChapterTask task) {
        chapterTaskMapper.insert(task);
    }

    @Override
    public void updateChapterTask(CollabChapterTask task) {
        chapterTaskMapper.updateById(task);
    }

    @Override
    public void deleteChapterTasksByProjectId(Long projectId) {
        chapterTaskMapper.delete(new QueryWrapper<CollabChapterTask>().eq("project_id", projectId));
    }

    @Override
    public Optional<CollabChapterTask> findChapterTaskById(Long id) {
        return Optional.ofNullable(chapterTaskMapper.selectById(id));
    }

    @Override
    public List<CollabComment> findCommentsByChapterTaskId(Long chapterTaskId) {
        return commentMapper.selectByChapterTaskId(chapterTaskId);
    }

    @Override
    public IPage<CollabComment> findCommentsByChapterTaskIdPage(Page<CollabComment> page, Long chapterTaskId) {
        return commentMapper.selectByChapterTaskIdPage(page, chapterTaskId);
    }

    @Override
    public List<CollabComment> findRepliesByParentId(Long parentId) {
        return commentMapper.selectRepliesByParentId(parentId);
    }

    @Override
    public void saveComment(CollabComment comment) {
        commentMapper.insert(comment);
    }

    @Override
    public void updateComment(CollabComment comment) {
        commentMapper.updateById(comment);
    }

    @Override
    public void deleteComment(Long id) {
        commentMapper.deleteById(id);
    }

    @Override
    public void deleteCommentsByChapterTaskId(Long chapterTaskId) {
        commentMapper.delete(new QueryWrapper<CollabComment>().eq("chapter_task_id", chapterTaskId));
    }

    @Override
    public Optional<CollabComment> findCommentById(Long id) {
        return Optional.ofNullable(commentMapper.selectById(id));
    }

    @Override
    public CollabInviteCode findValidInviteCode(String code) {
        return inviteCodeMapper.selectByValidCode(code);
    }

    @Override
    public CollabInviteCode findInviteCodeByCode(String code) {
        return inviteCodeMapper.selectByCode(code);
    }

    @Override
    public void markInviteCodeAsUsed(Long id) {
        inviteCodeMapper.markAsUsed(id);
    }

    @Override
    public void saveInviteCode(CollabInviteCode inviteCode) {
        inviteCodeMapper.insert(inviteCode);
    }
}
