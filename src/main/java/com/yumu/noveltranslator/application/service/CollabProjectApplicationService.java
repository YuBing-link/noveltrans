package com.yumu.noveltranslator.application.service;

import com.yumu.noveltranslator.config.tenant.TenantContext;
import com.yumu.noveltranslator.port.dto.collab.CollabProjectResponse;
import com.yumu.noveltranslator.port.dto.collab.CreateCollabProjectRequest;
import com.yumu.noveltranslator.port.dto.collab.InviteMemberRequest;
import com.yumu.noveltranslator.port.dto.collab.ProjectMemberResponse;
import com.yumu.noveltranslator.port.dto.common.PageResponse;
import com.yumu.noveltranslator.port.in.CollabPort;
import com.yumu.noveltranslator.domain.model.CollabChapterTask;
import com.yumu.noveltranslator.domain.model.CollabInviteCode;
import com.yumu.noveltranslator.domain.model.CollabProject;
import com.yumu.noveltranslator.domain.model.CollabProjectMember;
import com.yumu.noveltranslator.domain.model.Document;
import com.yumu.noveltranslator.domain.model.User;
import com.yumu.noveltranslator.enums.ChapterTaskStatus;
import com.yumu.noveltranslator.enums.CollabProjectStatus;
import com.yumu.noveltranslator.enums.ErrorCodeEnum;
import com.yumu.noveltranslator.enums.ProjectMemberRole;
import com.yumu.noveltranslator.enums.TranslationStatus;
import com.yumu.noveltranslator.domain.event.ChapterSplitEvent;
import com.yumu.noveltranslator.port.out.CollaborationRepositoryPort;
import com.yumu.noveltranslator.port.out.DocumentRepositoryPort;
import com.yumu.noveltranslator.port.out.UserRepositoryPort;
import com.yumu.noveltranslator.exception.BusinessException;
import com.yumu.noveltranslator.domain.service.MultiAgentTranslationService;
import com.yumu.noveltranslator.domain.service.CollabStateMachine;
import com.yumu.noveltranslator.domain.service.CollabEventPublisher;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 协作项目管理服务
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CollabProjectApplicationService implements com.yumu.noveltranslator.port.in.CollabPort {

    private final CollaborationRepositoryPort collabPort;
    private final DocumentRepositoryPort documentPort;
    private final UserRepositoryPort userPort;
    private final CollabStateMachine collabStateMachine;
    private final MultiAgentTranslationService multiAgentTranslationService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 创建协作项目，创建者自动成为 OWNER
     */
    @Transactional
    public CollabProjectResponse createProject(CreateCollabProjectRequest request, Long userId) {
        CollabProject project = new CollabProject();
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setOwnerId(userId);
        project.setSourceLang(request.getSourceLang());
        project.setTargetLang(request.getTargetLang());
        project.setStatus(CollabProjectStatus.DRAFT.getValue());
        project.setProgress(0);
        collabPort.saveProject(project);

        // 创建者自动成为 OWNER
        CollabProjectMember owner = new CollabProjectMember();
        owner.setProjectId(project.getId());
        owner.setUserId(userId);
        owner.setRole(ProjectMemberRole.OWNER.getValue());
        owner.setInviteStatus("ACTIVE");
        owner.setJoinedTime(LocalDateTime.now());
        collabPort.saveMember(owner);

        log.info("创建协作项目: projectId={}, name={}, ownerId={}", project.getId(), project.getName(), userId);
        return toProjectResponse(project);
    }

    /**
     * 从上传文档创建协作项目（团队模式）
     */
    @Transactional
    public CollabPort.TeamProjectCreateResult createProjectFromDocument(Long userId, Long documentId, String documentName,
                                                              String filePath, String fileType,
                                                              String sourceLang, String targetLang) {
        String content;
        try {
            content = Files.readString(Paths.get(filePath), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "读取文档失败: " + e.getMessage());
        }

        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAMETER_ERROR, "文档内容为空");
        }

        List<String> chapters = splitIntoChapters(content);

        return doCreateProject(userId, documentId, documentName, chapters, sourceLang, targetLang);
    }

    private CollabPort.TeamProjectCreateResult doCreateProject(Long userId, Long documentId, String documentName,
                                                     List<String> chapters, String sourceLang, String targetLang) {
        CollabProject project = createProjectAndOwner(userId, documentId, documentName, sourceLang, targetLang);

        log.info("团队模式创建项目（窄事务）: projectId={}, docName={}, chapters={}, 将异步插入",
                project.getId(), documentName, chapters.size());

        // 更新文档状态为处理中
        documentPort.findById(documentId).ifPresent(doc -> {
            doc.setStatus(TranslationStatus.PROCESSING.getValue());
            doc.setUpdateTime(LocalDateTime.now());
            documentPort.update(doc);
        });

        eventPublisher.publishEvent(new ChapterSplitEvent(
                project.getId(), userId, documentId, documentName,
                chapters, sourceLang, targetLang));

        return new CollabPort.TeamProjectCreateResult(project.getId(), documentName, chapters.size());
    }

    /**
     * 窄事务：仅创建项目和 OWNER 成员
     */
    @Transactional
    protected CollabProject createProjectAndOwner(Long userId, Long documentId, String documentName,
                                                    String sourceLang, String targetLang) {
        CollabProject project = new CollabProject();
        project.setName(documentName);
        project.setDescription("团队模式自动创建");
        project.setOwnerId(userId);
        project.setDocumentId(documentId);
        project.setSourceLang(sourceLang != null ? sourceLang : "auto");
        project.setTargetLang(targetLang);
        project.setStatus(CollabProjectStatus.DRAFT.getValue());
        project.setProgress(0);
        collabPort.saveProject(project);

        CollabProjectMember owner = new CollabProjectMember();
        owner.setProjectId(project.getId());
        owner.setUserId(userId);
        owner.setRole(ProjectMemberRole.OWNER.getValue());
        owner.setInviteStatus("ACTIVE");
        owner.setJoinedTime(LocalDateTime.now());
        collabPort.saveMember(owner);

        return project;
    }

    /**
     * 将文档章节添加到已有协作项目
     */
    @Transactional
    public int addChaptersToProject(Long userId, Long projectId, Document document) {
        CollabProject project = collabPort.findProjectById(projectId).orElse(null);
        if (project == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "项目不存在: " + projectId);
        }

        // 权限校验
        var member = collabPort.findMemberByProjectAndUser(projectId, userId);
        if (member == null && !project.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权向该项目添加章节");
        }

        String content;
        try {
            content = Files.readString(Paths.get(document.getPath()), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "读取文档失败: " + e.getMessage());
        }

        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAMETER_ERROR, "文档内容为空");
        }

        List<String> chapters = splitIntoChapters(content);

        return doAddChaptersToProject(project, document, chapters);
    }

    @Transactional
    private int doAddChaptersToProject(CollabProject project, Document document, List<String> chapters) {
        Long projectId = project.getId();

        if (project.getDocumentId() == null) {
            project.setDocumentId(document.getId());
            collabPort.updateProject(project);
        }

        List<CollabChapterTask> existingChapters = collabPort.findChapterTasksByProjectId(projectId);
        int nextChapterNumber = existingChapters.stream()
                .mapToInt(CollabChapterTask::getChapterNumber)
                .max()
                .orElse(0) + 1;

        for (String chapterText : chapters) {
            CollabChapterTask chapter = new CollabChapterTask();
            chapter.setProjectId(projectId);
            chapter.setChapterNumber(nextChapterNumber++);
            chapter.setTitle("第 " + chapter.getChapterNumber() + " 章");
            chapter.setSourceText(chapterText);
            chapter.setTargetText(null);
            chapter.setStatus(ChapterTaskStatus.UNASSIGNED.getValue());
            chapter.setProgress(0);
            chapter.setSourceWordCount(chapterText.length());
            collabPort.saveChapterTask(chapter);
        }

        document.setStatus(TranslationStatus.PROCESSING.getValue());
        document.setUpdateTime(java.time.LocalDateTime.now());
        documentPort.update(document);

        log.info("添加章节到项目: projectId={}, docName={}, chapters={}", projectId, document.getName(), chapters.size());
        return chapters.size();
    }

    /**
     * 启动多 Agent 翻译
     */
    public void startMultiAgentTranslation(Long projectId) {
        multiAgentTranslationService.startMultiAgentTranslation(projectId);
    }

    /**
     * 智能章节分割
     */
    private List<String> splitIntoChapters(String content) {
        String[] lines = content.split("\n");
        List<String> chapters = new ArrayList<>();
        StringBuilder currentChapter = new StringBuilder();
        boolean hasChapterTitle = false;

        java.util.regex.Pattern chapterPattern = java.util.regex.Pattern.compile(
                "^\\s*(?:\\*\\*?)?\\s*(?:" +
                        "(?:第\\s*[零一二三四五六七八九十百千\\d]+\\s*(?:章|节|回|卷|篇))" +
                        "|(?:chapter\\s+[ivxlcdm\\d]+\\s*[:：]?\\s*.*)" +
                        "|(?:ch\\.?\\s*\\d+)" +
                        "|(?:part\\s+[ivxlcdm\\d]+\\s*[:：]?\\s*.*)" +
                        "|(?:(?:[一二三四五六七八九十]+)、)" +
                        ")",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );

        for (String line : lines) {
            String trimmed = line.trim();

            if (chapterPattern.matcher(trimmed).find()) {
                if (currentChapter.length() > 0) {
                    String chapterText = currentChapter.toString().trim();
                    if (!chapterText.isEmpty()) {
                        chapters.add(chapterText);
                    }
                    currentChapter = new StringBuilder();
                }
                hasChapterTitle = true;
                currentChapter.append(trimmed).append("\n");
            } else {
                currentChapter.append(line).append("\n");
            }
        }

        if (currentChapter.length() > 0) {
            String chapterText = currentChapter.toString().trim();
            if (!chapterText.isEmpty()) {
                chapters.add(chapterText);
            }
        }

        if (!hasChapterTitle) {
            chapters.clear();
            String[] paragraphs = content.split("\n+");
            StringBuilder current = new StringBuilder();
            int maxCharsPerChapter = 2000;

            for (String p : paragraphs) {
                String pTrimmed = p.trim();
                if (pTrimmed.isEmpty()) continue;
                if (current.length() + pTrimmed.length() > maxCharsPerChapter && current.length() > 0) {
                    chapters.add(current.toString().trim());
                    current = new StringBuilder();
                }
                current.append(pTrimmed).append("\n");
            }
            if (current.length() > 0) {
                chapters.add(current.toString().trim());
            }
        }

        if (chapters.isEmpty()) {
            chapters.add(content.trim());
        }

        log.debug("章节分割: 检测到章节标题={}, 分割出 {} 章", hasChapterTitle, chapters.size());
        return chapters;
    }

    /**
     * 获取项目详情
     */
    public CollabProjectResponse getProjectById(Long projectId) {
        CollabProject project = collabPort.findProjectById(projectId).orElse(null);
        if (project == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "项目不存在: " + projectId);
        }
        return toProjectResponse(project);
    }

    /**
     * 获取用户创建的项目列表
     */
    public List<CollabProjectResponse> listOwnedByUserId(Long userId) {
        List<CollabProject> projects = collabPort.findProjectsByOwnerId(userId);
        Map<Long, User> userMap = batchLoadOwnerUsers(projects);
        return projects.stream()
                .map(p -> toProjectResponse(p, userMap))
                .collect(Collectors.toList());
    }

    /**
     * 获取用户参与的项目列表
     */
    public PageResponse<CollabProjectResponse> listByUserId(Long userId, int page, int pageSize) {
        try {
            TenantContext.setBypassTenant(true);
            List<CollabProject> allProjects = collabPort.findProjectsByMemberUserId(userId);
            long total = allProjects.size();
            int fromIndex = Math.min((page - 1) * pageSize, (int) total);
            int toIndex = Math.min(fromIndex + pageSize, (int) total);
            List<CollabProject> pagedProjects = fromIndex < total
                    ? allProjects.subList(fromIndex, toIndex)
                    : List.of();
            Map<Long, User> userMap = batchLoadOwnerUsers(pagedProjects);
            List<CollabProjectResponse> responseList = pagedProjects.stream()
                    .map(p -> toProjectResponse(p, userMap))
                    .collect(Collectors.toList());
            return PageResponse.of(page, pageSize, total, responseList);
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }

    /**
     * 更新项目信息
     */
    @Transactional
    public CollabProjectResponse updateProject(Long projectId, CreateCollabProjectRequest request, Long userId) {
        CollabProject project = collabPort.findProjectById(projectId).orElse(null);
        if (project == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "项目不存在: " + projectId);
        }

        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setSourceLang(request.getSourceLang());
        project.setTargetLang(request.getTargetLang());
        collabPort.updateProject(project);

        return toProjectResponse(project);
    }

    /**
     * 变更项目状态
     */
    @Transactional
    public void changeProjectStatus(Long projectId, CollabProjectStatus targetStatus, Long userId) {
        CollabProject project = collabPort.findProjectById(projectId).orElse(null);
        if (project == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "项目不存在: " + projectId);
        }

        CollabProjectStatus current = CollabProjectStatus.fromValue(project.getStatus());
        collabStateMachine.transitionProject(project, targetStatus);
        if (targetStatus == CollabProjectStatus.COMPLETED) {
            project.setProgress(100);
        }
        collabPort.updateProject(project);

        log.info("项目状态变更: projectId={}, {} -> {}", projectId, current, targetStatus);
    }

    /**
     * 生成项目邀请码
     */
    @Transactional
    public CollabPort.InviteCodeResult generateInviteCode(Long projectId, Long operatorId) {
        CollabProject project = collabPort.findProjectById(projectId).orElse(null);
        if (project == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "项目不存在: " + projectId);
        }

        String code = generateRandomInviteCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(72);

        CollabInviteCode inviteCode = new CollabInviteCode();
        inviteCode.setProjectId(projectId);
        inviteCode.setCode(code);
        inviteCode.setExpiresAt(expiresAt);
        inviteCode.setUsed(0);
        collabPort.saveInviteCode(inviteCode);

        log.info("生成邀请码: projectId={}, code={}, expiresAt={}", projectId, code, expiresAt);
        return new CollabPort.InviteCodeResult(code, expiresAt);
    }

    private String generateRandomInviteCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder code = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        if (collabPort.findInviteCodeByCode(code.toString()) != null) {
            return generateRandomInviteCode();
        }
        return code.toString();
    }

    /**
     * 邀请成员
     */
    @Transactional
    public ProjectMemberResponse inviteMember(Long projectId, InviteMemberRequest request, Long inviterId) {
        User user = userPort.findByEmail(request.getEmail()).orElse(null);
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "用户不存在: " + request.getEmail());
        }

        var existing = collabPort.findMemberByProjectAndUser(projectId, user.getId());
        if (existing != null) {
            throw new BusinessException(ErrorCodeEnum.INVALID_STATE, "该用户已是项目成员");
        }

        String inviteCode = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        CollabProjectMember member = new CollabProjectMember();
        member.setProjectId(projectId);
        member.setUserId(user.getId());
        member.setRole(request.getRole().getValue());
        member.setInviteCode(inviteCode);
        member.setInviteStatus("INVITED");
        collabPort.saveMember(member);

        log.info("邀请成员: projectId={}, email={}, role={}", projectId, request.getEmail(), request.getRole());
        return toMemberResponse(member, user);
    }

    /**
     * 通过邀请码加入项目
     */
    @Transactional
    public ProjectMemberResponse joinByInviteCode(String inviteCode, Long userId) {
        CollabInviteCode codeRecord = collabPort.findValidInviteCode(inviteCode);
        if (codeRecord == null) {
            CollabInviteCode anyCode = collabPort.findInviteCodeByCode(inviteCode);
            if (anyCode == null) {
                throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "邀请码无效");
            }
            if (anyCode.getUsed() == 1) {
                throw new BusinessException(ErrorCodeEnum.INVALID_STATE, "邀请码已被使用");
            }
            if (anyCode.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw new BusinessException(ErrorCodeEnum.INVALID_STATE, "邀请码已过期");
            }
            throw new BusinessException(ErrorCodeEnum.INVALID_STATE, "邀请码不可用");
        }

        collabPort.markInviteCodeAsUsed(codeRecord.getId());

        try {
            TenantContext.setBypassTenant(true);

            CollabProject project = collabPort.findProjectById(codeRecord.getProjectId()).orElse(null);
            if (project == null) {
                throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "关联项目不存在");
            }
            if (!CollabProjectStatus.ACTIVE.getValue().equals(project.getStatus())) {
                throw new BusinessException(ErrorCodeEnum.INVALID_STATE, "项目当前不可加入");
            }

            var existing = collabPort.findMemberByProjectAndUser(codeRecord.getProjectId(), userId);
            if (existing != null) {
                throw new BusinessException(ErrorCodeEnum.INVALID_STATE, "您已是该项目成员");
            }

            CollabProjectMember member = new CollabProjectMember();
            member.setProjectId(codeRecord.getProjectId());
            member.setUserId(userId);
            member.setRole(ProjectMemberRole.TRANSLATOR.getValue());
            member.setInviteCode(inviteCode);
            member.setInviteStatus("ACTIVE");
            member.setJoinedTime(LocalDateTime.now());
            member.setTenantId(project.getTenantId());
            collabPort.saveMember(member);

            User user = userPort.findById(userId).orElse(null);
            log.info("加入项目: userId={}, projectId={}, inviteCode={}", userId, codeRecord.getProjectId(), inviteCode);
            return toMemberResponse(member, user);
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }

    /**
     * 获取项目成员列表（分页）
     */
    public PageResponse<ProjectMemberResponse> getMembers(Long projectId, int page, int pageSize) {
        List<CollabProjectMember> allMembers;
        try {
            TenantContext.setBypassTenant(true);
            allMembers = collabPort.findMembersByProjectId(projectId);
        } finally {
            TenantContext.setBypassTenant(false);
        }
        long total = allMembers.size();
        int fromIndex = Math.min((page - 1) * pageSize, (int) total);
        int toIndex = Math.min(fromIndex + pageSize, (int) total);
        List<CollabProjectMember> pagedMembers = fromIndex < total
                ? allMembers.subList(fromIndex, toIndex)
                : List.of();

        Set<Long> userIds = new HashSet<>();
        for (CollabProjectMember m : pagedMembers) {
            if (m.getUserId() != null) userIds.add(m.getUserId());
        }
        Map<Long, User> userMap = new HashMap<>();
        for (Long uid : userIds) {
            userPort.findById(uid).ifPresent(u -> userMap.put(uid, u));
        }

        List<ProjectMemberResponse> responseList = pagedMembers.stream()
                .map(m -> toMemberResponse(m, userMap.get(m.getUserId())))
                .collect(Collectors.toList());
        return PageResponse.of(page, pageSize, total, responseList);
    }

    /**
     * 移除成员
     */
    @Transactional
    public void removeMember(Long projectId, Long memberId, Long operatorId) {
        var member = collabPort.findMemberById(memberId).orElse(null);
        if (member == null || !member.getProjectId().equals(projectId)) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "成员不存在");
        }

        if (ProjectMemberRole.OWNER.getValue().equals(member.getRole())) {
            throw new BusinessException(ErrorCodeEnum.INVALID_STATE, "所有者不能直接移除，需先转让项目");
        }

        member.setInviteStatus("REMOVED");
        collabPort.updateMember(member);
    }

    /**
     * 删除项目
     */
    @Transactional
    public void deleteProject(Long projectId, Long userId) {
        CollabProject project = collabPort.findProjectById(projectId).orElse(null);
        if (project == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "项目不存在");
        }
        if (!project.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "只有项目所有者可以删除项目");
        }

        // 级联逻辑删除：评论 → 章节 → 成员 → 项目
        List<CollabChapterTask> chapters = collabPort.findChapterTasksByProjectId(projectId);
        for (CollabChapterTask chapter : chapters) {
            collabPort.deleteCommentsByChapterTaskId(chapter.getId());
        }
        collabPort.deleteChapterTasksByProjectId(projectId);
        collabPort.deleteMembersByProjectId(projectId);
        collabPort.deleteProject(projectId);

        log.info("删除协作项目: projectId={}, name={}, ownerId={}", projectId, project.getName(), userId);
    }

    /**
     * 计算项目进度
     */
    public int getProjectProgress(Long projectId) {
        return 0;
    }

    private CollabProjectResponse toProjectResponse(CollabProject project) {
        return toProjectResponse(project, Map.of());
    }

    private CollabProjectResponse toProjectResponse(CollabProject project, Map<Long, User> userMap) {
        CollabProjectResponse resp = new CollabProjectResponse();
        resp.setId(project.getId());
        resp.setName(project.getName());
        resp.setDescription(project.getDescription());
        resp.setOwnerId(project.getOwnerId());
        resp.setSourceLang(project.getSourceLang());
        resp.setTargetLang(project.getTargetLang());
        resp.setStatus(project.getStatus());
        resp.setProgress(project.getProgress());
        resp.setCreateTime(project.getCreateTime());
        resp.setUpdateTime(project.getUpdateTime());

        User owner = userMap.get(project.getOwnerId());
        if (owner == null) {
            owner = userPort.findById(project.getOwnerId()).orElse(null);
        }
        if (owner != null) {
            resp.setOwnerName(owner.getUsername());
        }

        int memberCount = collabPort.countMembersByProjectId(project.getId());
        resp.setMemberCount(memberCount);

        return resp;
    }

    private Map<Long, User> batchLoadOwnerUsers(List<CollabProject> projects) {
        Set<Long> userIds = new HashSet<>();
        for (CollabProject p : projects) {
            if (p.getOwnerId() != null) userIds.add(p.getOwnerId());
        }
        Map<Long, User> userMap = new HashMap<>();
        for (Long uid : userIds) {
            userPort.findById(uid).ifPresent(u -> userMap.put(uid, u));
        }
        return userMap;
    }

    private ProjectMemberResponse toMemberResponse(CollabProjectMember member, User user) {
        ProjectMemberResponse resp = new ProjectMemberResponse();
        resp.setId(member.getId());
        resp.setUserId(member.getUserId());
        resp.setRole(member.getRole());
        resp.setInviteStatus(member.getInviteStatus());
        resp.setJoinedTime(member.getJoinedTime());
        if (user != null) {
            resp.setUsername(user.getUsername());
            resp.setEmail(user.getEmail());
            resp.setAvatar(user.getAvatar());
        }
        return resp;
    }

}
