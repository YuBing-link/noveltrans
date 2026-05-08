package com.yumu.noveltranslator.port.in;

import com.yumu.noveltranslator.domain.model.Document;
import com.yumu.noveltranslator.dto.collab.CollabProjectResponse;
import com.yumu.noveltranslator.dto.collab.CreateCollabProjectRequest;
import com.yumu.noveltranslator.dto.collab.InviteMemberRequest;
import com.yumu.noveltranslator.dto.collab.ProjectMemberResponse;
import com.yumu.noveltranslator.dto.common.PageResponse;
import com.yumu.noveltranslator.enums.CollabProjectStatus;

import java.util.List;

public interface CollabPort {
    CollabProjectResponse createProject(CreateCollabProjectRequest request, Long userId);
    TeamProjectCreateResult createProjectFromDocument(Long userId, Long documentId, String documentName,
                                                      String filePath, String fileType,
                                                      String sourceLang, String targetLang);
    int addChaptersToProject(Long userId, Long projectId, Document document);
    void startMultiAgentTranslation(Long projectId);
    CollabProjectResponse getProjectById(Long projectId);
    List<CollabProjectResponse> listOwnedByUserId(Long userId);
    PageResponse<CollabProjectResponse> listByUserId(Long userId, int page, int pageSize);
    CollabProjectResponse updateProject(Long projectId, CreateCollabProjectRequest request, Long userId);
    void changeProjectStatus(Long projectId, CollabProjectStatus targetStatus, Long userId);
    InviteCodeResult generateInviteCode(Long projectId, Long operatorId);
    ProjectMemberResponse inviteMember(Long projectId, InviteMemberRequest request, Long inviterId);
    ProjectMemberResponse joinByInviteCode(String inviteCode, Long userId);
    PageResponse<ProjectMemberResponse> getMembers(Long projectId, int page, int pageSize);
    void removeMember(Long projectId, Long memberId, Long operatorId);
    void deleteProject(Long projectId, Long userId);
    int getProjectProgress(Long projectId);

    record TeamProjectCreateResult(Long projectId, String documentName, int chapterCount) {}
    record InviteCodeResult(String code, java.time.LocalDateTime expiresAt) {}
}
