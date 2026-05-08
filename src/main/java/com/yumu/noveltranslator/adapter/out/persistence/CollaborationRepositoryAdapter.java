package com.yumu.noveltranslator.adapter.out.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yumu.noveltranslator.adapter.out.persistence.converter.CollabConverter;
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
import com.yumu.noveltranslator.port.dto.common.PageResult;
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
    public List<com.yumu.noveltranslator.domain.model.CollabProject> findProjectsByOwnerId(Long ownerId) {
        return CollabConverter.toProjectModelList(projectMapper.selectByOwnerId(ownerId));
    }

    @Override
    public List<com.yumu.noveltranslator.domain.model.CollabProject> findProjectsByMemberUserId(Long userId) {
        return CollabConverter.toProjectModelList(projectMapper.selectByMemberUserId(userId));
    }

    @Override
    public void saveProject(com.yumu.noveltranslator.domain.model.CollabProject project) {
        projectMapper.insert(CollabConverter.toProjectEntity(project));
    }

    @Override
    public void updateProject(com.yumu.noveltranslator.domain.model.CollabProject project) {
        projectMapper.updateById(CollabConverter.toProjectEntity(project));
    }

    @Override
    public void deleteProject(Long id) {
        projectMapper.deleteById(id);
    }

    @Override
    public Optional<com.yumu.noveltranslator.domain.model.CollabProject> findProjectById(Long id) {
        return Optional.ofNullable(CollabConverter.toProjectModel(projectMapper.selectById(id)));
    }

    @Override
    public List<com.yumu.noveltranslator.domain.model.CollabProjectMember> findMembersByProjectId(Long projectId) {
        return CollabConverter.toMemberModelList(memberMapper.selectByProjectId(projectId));
    }

    @Override
    public com.yumu.noveltranslator.domain.model.CollabProjectMember findMemberByInviteCode(String inviteCode) {
        return CollabConverter.toMemberModel(memberMapper.selectByInviteCode(inviteCode));
    }

    @Override
    public int countMembersByProjectIdAndRole(Long projectId, String role) {
        return memberMapper.countByProjectIdAndRole(projectId, role);
    }

    @Override
    public com.yumu.noveltranslator.domain.model.CollabProjectMember findMemberByProjectAndUser(Long projectId, Long userId) {
        return CollabConverter.toMemberModel(memberMapper.selectByProjectAndUser(projectId, userId));
    }

    @Override
    public int countMembersByProjectId(Long projectId) {
        return memberMapper.countByProjectId(projectId);
    }

    @Override
    public void saveMember(com.yumu.noveltranslator.domain.model.CollabProjectMember member) {
        memberMapper.insert(CollabConverter.toMemberEntity(member));
    }

    @Override
    public void updateMember(com.yumu.noveltranslator.domain.model.CollabProjectMember member) {
        memberMapper.updateById(CollabConverter.toMemberEntity(member));
    }

    @Override
    public void deleteMembersByProjectId(Long projectId) {
        memberMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CollabProjectMember>().eq("project_id", projectId));
    }

    @Override
    public Optional<com.yumu.noveltranslator.domain.model.CollabProjectMember> findMemberById(Long id) {
        return Optional.ofNullable(CollabConverter.toMemberModel(memberMapper.selectById(id)));
    }

    @Override
    public List<com.yumu.noveltranslator.domain.model.CollabChapterTask> findChapterTasksByProjectId(Long projectId) {
        return CollabConverter.toChapterTaskModelList(chapterTaskMapper.selectByProjectId(projectId));
    }

    @Override
    public List<com.yumu.noveltranslator.domain.model.CollabChapterTask> findChapterTasksByProjectIdAndStatus(Long projectId, String status) {
        return CollabConverter.toChapterTaskModelList(chapterTaskMapper.selectByProjectIdAndStatus(projectId, status));
    }

    @Override
    public PageResult<com.yumu.noveltranslator.domain.model.CollabChapterTask> findChapterTasksByProjectIdPaged(Long projectId, int page, int pageSize) {
        LambdaQueryWrapper<CollabChapterTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollabChapterTask::getProjectId, projectId)
               .eq(CollabChapterTask::getDeleted, 0)
               .orderByAsc(CollabChapterTask::getChapterNumber);
        com.baomidou.mybatisplus.core.metadata.IPage<CollabChapterTask> entityPage = chapterTaskMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return new PageResult<>(
            CollabConverter.toChapterTaskModelList(entityPage.getRecords()),
            entityPage.getTotal(),
            entityPage.getCurrent(),
            entityPage.getSize()
        );
    }

    @Override
    public PageResult<com.yumu.noveltranslator.domain.model.CollabChapterTask> findChapterTasksByAssigneeIdPaged(Long assigneeId, List<String> statuses, int page, int pageSize) {
        LambdaQueryWrapper<CollabChapterTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollabChapterTask::getAssigneeId, assigneeId)
               .eq(CollabChapterTask::getDeleted, 0)
               .in(CollabChapterTask::getStatus, statuses)
               .orderByDesc(CollabChapterTask::getUpdateTime);
        com.baomidou.mybatisplus.core.metadata.IPage<CollabChapterTask> entityPage = chapterTaskMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return new PageResult<>(
            CollabConverter.toChapterTaskModelList(entityPage.getRecords()),
            entityPage.getTotal(),
            entityPage.getCurrent(),
            entityPage.getSize()
        );
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
    public List<com.yumu.noveltranslator.domain.model.CollabChapterTask> findChapterTasksByAssigneeId(Long assigneeId) {
        return CollabConverter.toChapterTaskModelList(chapterTaskMapper.selectByAssigneeId(assigneeId));
    }

    @Override
    public List<com.yumu.noveltranslator.domain.model.CollabChapterTask> findChapterTasksByStatusAndUpdateTimeBefore(String status, LocalDateTime cutoff) {
        return CollabConverter.toChapterTaskModelList(chapterTaskMapper.findByStatusAndUpdateTimeBefore(status, cutoff));
    }

    @Override
    public void updateChapterTaskRetryCount(Long id, int retryCount) {
        chapterTaskMapper.updateRetryCount(id, retryCount);
    }

    @Override
    public List<com.yumu.noveltranslator.domain.model.CollabChapterTask> findChaptersWithRetryCountGreaterThan(int retryCount) {
        LambdaQueryWrapper<CollabChapterTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.gt(CollabChapterTask::getRetryCount, retryCount)
               .eq(CollabChapterTask::getDeleted, 0);
        return CollabConverter.toChapterTaskModelList(chapterTaskMapper.selectList(wrapper));
    }

    @Override
    public void saveChapterTask(com.yumu.noveltranslator.domain.model.CollabChapterTask task) {
        chapterTaskMapper.insert(CollabConverter.toChapterTaskEntity(task));
    }

    @Override
    public void updateChapterTask(com.yumu.noveltranslator.domain.model.CollabChapterTask task) {
        chapterTaskMapper.updateById(CollabConverter.toChapterTaskEntity(task));
    }

    @Override
    public void deleteChapterTasksByProjectId(Long projectId) {
        chapterTaskMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CollabChapterTask>().eq("project_id", projectId));
    }

    @Override
    public Optional<com.yumu.noveltranslator.domain.model.CollabChapterTask> findChapterTaskById(Long id) {
        return Optional.ofNullable(CollabConverter.toChapterTaskModel(chapterTaskMapper.selectById(id)));
    }

    @Override
    public List<com.yumu.noveltranslator.domain.model.CollabComment> findCommentsByChapterTaskId(Long chapterTaskId) {
        return CollabConverter.toCommentModelList(commentMapper.selectByChapterTaskId(chapterTaskId));
    }

    @Override
    public PageResult<com.yumu.noveltranslator.domain.model.CollabComment> findCommentsByChapterTaskIdPaged(Long chapterTaskId, int page, int pageSize) {
        LambdaQueryWrapper<CollabComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollabComment::getChapterTaskId, chapterTaskId)
               .eq(CollabComment::getDeleted, 0)
               .orderByAsc(CollabComment::getCreateTime);
        com.baomidou.mybatisplus.core.metadata.IPage<CollabComment> entityPage = commentMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return new PageResult<>(
            CollabConverter.toCommentModelList(entityPage.getRecords()),
            entityPage.getTotal(),
            entityPage.getCurrent(),
            entityPage.getSize()
        );
    }

    @Override
    public List<com.yumu.noveltranslator.domain.model.CollabComment> findRepliesByParentId(Long parentId) {
        return CollabConverter.toCommentModelList(commentMapper.selectRepliesByParentId(parentId));
    }

    @Override
    public void saveComment(com.yumu.noveltranslator.domain.model.CollabComment comment) {
        commentMapper.insert(CollabConverter.toCommentEntity(comment));
    }

    @Override
    public void updateComment(com.yumu.noveltranslator.domain.model.CollabComment comment) {
        commentMapper.updateById(CollabConverter.toCommentEntity(comment));
    }

    @Override
    public void deleteComment(Long id) {
        commentMapper.deleteById(id);
    }

    @Override
    public void deleteCommentsByChapterTaskId(Long chapterTaskId) {
        commentMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CollabComment>().eq("chapter_task_id", chapterTaskId));
    }

    @Override
    public Optional<com.yumu.noveltranslator.domain.model.CollabComment> findCommentById(Long id) {
        return Optional.ofNullable(CollabConverter.toCommentModel(commentMapper.selectById(id)));
    }

    @Override
    public com.yumu.noveltranslator.domain.model.CollabInviteCode findValidInviteCode(String code) {
        return CollabConverter.toInviteCodeModel(inviteCodeMapper.selectByValidCode(code));
    }

    @Override
    public com.yumu.noveltranslator.domain.model.CollabInviteCode findInviteCodeByCode(String code) {
        return CollabConverter.toInviteCodeModel(inviteCodeMapper.selectByCode(code));
    }

    @Override
    public void markInviteCodeAsUsed(Long id) {
        inviteCodeMapper.markAsUsed(id);
    }

    @Override
    public void saveInviteCode(com.yumu.noveltranslator.domain.model.CollabInviteCode inviteCode) {
        inviteCodeMapper.insert(CollabConverter.toInviteCodeEntity(inviteCode));
    }
}
