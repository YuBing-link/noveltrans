package com.yumu.noveltranslator.port.out;

import com.yumu.noveltranslator.domain.model.CollabChapterTask;
import com.yumu.noveltranslator.domain.model.CollabComment;
import com.yumu.noveltranslator.domain.model.CollabInviteCode;
import com.yumu.noveltranslator.domain.model.CollabProject;
import com.yumu.noveltranslator.domain.model.CollabProjectMember;
import com.yumu.noveltranslator.port.dto.common.PageResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CollaborationRepositoryPort {

    // === CollabProject ===
    List<CollabProject> findProjectsByOwnerId(Long ownerId);
    List<CollabProject> findProjectsByMemberUserId(Long userId);
    void saveProject(CollabProject project);
    void updateProject(CollabProject project);
    void deleteProject(Long id);
    Optional<CollabProject> findProjectById(Long id);

    // === CollabProjectMember ===
    List<CollabProjectMember> findMembersByProjectId(Long projectId);
    CollabProjectMember findMemberByInviteCode(String inviteCode);
    int countMembersByProjectIdAndRole(Long projectId, String role);
    CollabProjectMember findMemberByProjectAndUser(Long projectId, Long userId);
    int countMembersByProjectId(Long projectId);
    void saveMember(CollabProjectMember member);
    void updateMember(CollabProjectMember member);
    void deleteMembersByProjectId(Long projectId);
    Optional<CollabProjectMember> findMemberById(Long id);

    // === CollabChapterTask ===
    List<CollabChapterTask> findChapterTasksByProjectId(Long projectId);
    List<CollabChapterTask> findChapterTasksByProjectIdAndStatus(Long projectId, String status);
    PageResult<CollabChapterTask> findChapterTasksByProjectIdPaged(Long projectId, int page, int pageSize);
    PageResult<CollabChapterTask> findChapterTasksByAssigneeIdPaged(Long assigneeId, List<String> statuses, int page, int pageSize);
    int countChapterTasksByProjectId(Long projectId);
    int countChapterTasksByProjectIdAndStatus(Long projectId, String status);
    List<CollabChapterTask> findChapterTasksByAssigneeId(Long assigneeId);
    List<CollabChapterTask> findChapterTasksByStatusAndUpdateTimeBefore(String status, LocalDateTime cutoff);
    void updateChapterTaskRetryCount(Long id, int retryCount);
    List<CollabChapterTask> findChaptersWithRetryCountGreaterThan(int retryCount);
    void saveChapterTask(CollabChapterTask task);
    void updateChapterTask(CollabChapterTask task);
    void deleteChapterTasksByProjectId(Long projectId);
    Optional<CollabChapterTask> findChapterTaskById(Long id);

    // === CollabComment ===
    List<CollabComment> findCommentsByChapterTaskId(Long chapterTaskId);
    PageResult<CollabComment> findCommentsByChapterTaskIdPaged(Long chapterTaskId, int page, int pageSize);
    List<CollabComment> findRepliesByParentId(Long parentId);
    void saveComment(CollabComment comment);
    void updateComment(CollabComment comment);
    void deleteComment(Long id);
    void deleteCommentsByChapterTaskId(Long chapterTaskId);
    Optional<CollabComment> findCommentById(Long id);

    // === CollabInviteCode ===
    CollabInviteCode findValidInviteCode(String code);
    CollabInviteCode findInviteCodeByCode(String code);
    void markInviteCodeAsUsed(Long id);
    void saveInviteCode(CollabInviteCode inviteCode);
}
