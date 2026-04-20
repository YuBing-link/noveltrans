package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.CollabChapterTask;
import com.yumu.noveltranslator.entity.CollabProject;
import com.yumu.noveltranslator.entity.CollabProjectMember;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.enums.ChapterTaskStatus;
import com.yumu.noveltranslator.enums.CollabProjectStatus;
import com.yumu.noveltranslator.enums.ProjectMemberRole;
import com.yumu.noveltranslator.mapper.CollabChapterTaskMapper;
import com.yumu.noveltranslator.mapper.CollabProjectMapper;
import com.yumu.noveltranslator.mapper.CollabProjectMemberMapper;
import com.yumu.noveltranslator.mapper.UserMapper;
import com.yumu.noveltranslator.service.state.CollabStateMachine;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 协作项目管理服务
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CollabProjectService extends ServiceImpl<CollabProjectMapper, CollabProject> {

    private final CollabProjectMapper collabProjectMapper;
    private final CollabProjectMemberMapper collabProjectMemberMapper;
    private final CollabChapterTaskMapper collabChapterTaskMapper;
    private final UserMapper userMapper;
    private final CollabStateMachine collabStateMachine;
    private final com.yumu.noveltranslator.service.MultiAgentTranslationService multiAgentTranslationService;

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
        save(project);

        // 创建者自动成为 OWNER
        CollabProjectMember owner = new CollabProjectMember();
        owner.setProjectId(project.getId());
        owner.setUserId(userId);
        owner.setRole(ProjectMemberRole.OWNER.getValue());
        owner.setInviteStatus("ACTIVE");
        owner.setJoinedTime(LocalDateTime.now());
        collabProjectMemberMapper.insert(owner);

        log.info("创建协作项目: projectId={}, name={}, ownerId={}", project.getId(), project.getName(), userId);
        return toProjectResponse(project);
    }

    /**
     * 从上传文档创建协作项目（团队模式）
     * 自动按段落拆分章节，创建项目后直接进入 ACTIVE 状态
     *
     * @param userId 用户ID
     * @param documentName 文档名称
     * @param filePath 文档路径
     * @param fileType 文件类型
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @return 项目ID和章节数
     */
    @Transactional
    public TeamProjectCreateResult createProjectFromDocument(Long userId, Long documentId, String documentName,
                                                              String filePath, String fileType,
                                                              String sourceLang, String targetLang) {
        // 读取文档内容并按段落分割
        String content;
        try {
            content = Files.readString(Paths.get(filePath), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("读取文档失败: " + e.getMessage());
        }

        if (content == null || content.trim().isEmpty()) {
            throw new RuntimeException("文档内容为空");
        }

        // 按空行分割段落，每 500 字符或每个自然段作为一个章节
        String[] paragraphs = content.split("\n+");
        List<String> chapters = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String p : paragraphs) {
            String trimmed = p.trim();
            if (trimmed.isEmpty()) continue;
            if (current.length() + trimmed.length() > 500 && current.length() > 0) {
                chapters.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(trimmed).append("\n");
        }
        if (current.length() > 0) {
            chapters.add(current.toString().trim());
        }

        // 至少创建一个章节
        if (chapters.isEmpty()) {
            chapters.add(content.trim());
        }

        // 创建项目
        CollabProject project = new CollabProject();
        project.setName(documentName);
        project.setDescription("团队模式自动创建");
        project.setOwnerId(userId);
        project.setDocumentId(documentId);
        project.setSourceLang(sourceLang != null ? sourceLang : "auto");
        project.setTargetLang(targetLang);
        project.setStatus(CollabProjectStatus.ACTIVE.getValue());
        project.setProgress(0);
        save(project);

        // 创建者成为 OWNER
        CollabProjectMember owner = new CollabProjectMember();
        owner.setProjectId(project.getId());
        owner.setUserId(userId);
        owner.setRole(ProjectMemberRole.OWNER.getValue());
        owner.setInviteStatus("ACTIVE");
        owner.setJoinedTime(LocalDateTime.now());
        collabProjectMemberMapper.insert(owner);

        // 自动创建章节
        for (int i = 0; i < chapters.size(); i++) {
            String chapterText = chapters.get(i);
            CollabChapterTask chapter = new CollabChapterTask();
            chapter.setProjectId(project.getId());
            chapter.setChapterNumber(i + 1);
            chapter.setTitle("第 " + (i + 1) + " 章");
            chapter.setSourceText(chapterText);
            chapter.setTargetText(null);
            chapter.setStatus(ChapterTaskStatus.UNASSIGNED.getValue());
            chapter.setProgress(0);
            chapter.setSourceWordCount(chapterText.length());
            collabChapterTaskMapper.insert(chapter);
        }

        log.info("团队模式创建项目: projectId={}, docName={}, chapters={}", project.getId(), documentName, chapters.size());

        return new TeamProjectCreateResult(project.getId(), documentName, chapters.size());
    }

    /**
     * 启动多 Agent 翻译（在事务外调用，确保数据已提交）
     */
    public void startMultiAgentTranslation(Long projectId) {
        multiAgentTranslationService.startMultiAgentTranslation(projectId);
    }

    /**
     * 获取项目详情
     */
    public CollabProjectResponse getProjectById(Long projectId) {
        CollabProject project = getById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("项目不存在: " + projectId);
        }
        return toProjectResponse(project);
    }

    /**
     * 获取用户创建的项目列表
     */
    public List<CollabProjectResponse> listOwnedByUserId(Long userId) {
        return collabProjectMapper.selectByOwnerId(userId)
                .stream()
                .map(this::toProjectResponse)
                .collect(Collectors.toList());
    }

    /**
     * 获取用户参与的项目列表（包括作为成员）
     */
    public List<CollabProjectResponse> listByUserId(Long userId) {
        return collabProjectMapper.selectByMemberUserId(userId)
                .stream()
                .map(this::toProjectResponse)
                .collect(Collectors.toList());
    }

    /**
     * 更新项目信息（仅 OWNER/REVIEWER）
     */
    @Transactional
    public CollabProjectResponse updateProject(Long projectId, CreateCollabProjectRequest request, Long userId) {
        CollabProject project = getById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("项目不存在: " + projectId);
        }

        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setSourceLang(request.getSourceLang());
        project.setTargetLang(request.getTargetLang());
        updateById(project);

        return toProjectResponse(project);
    }

    /**
     * 变更项目状态
     */
    @Transactional
    public void changeProjectStatus(Long projectId, CollabProjectStatus targetStatus, Long userId) {
        CollabProject project = getById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("项目不存在: " + projectId);
        }

        CollabProjectStatus current = CollabProjectStatus.fromValue(project.getStatus());
        collabStateMachine.validateProjectTransition(current, targetStatus);

        project.setStatus(targetStatus.getValue());
        if (targetStatus == CollabProjectStatus.COMPLETED) {
            project.setProgress(100);
        }
        updateById(project);

        log.info("项目状态变更: projectId={}, {} → {}", projectId, current, targetStatus);
    }

    /**
     * 邀请成员
     */
    @Transactional
    public ProjectMemberResponse inviteMember(Long projectId, InviteMemberRequest request, Long inviterId) {
        User user = userMapper.findByEmail(request.getEmail());
        if (user == null) {
            throw new IllegalArgumentException("用户不存在: " + request.getEmail());
        }

        // 检查是否已是成员
        CollabProjectMember existing = collabProjectMemberMapper.selectByProjectAndUser(projectId, user.getId());
        if (existing != null) {
            throw new IllegalStateException("该用户已是项目成员");
        }

        String inviteCode = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        CollabProjectMember member = new CollabProjectMember();
        member.setProjectId(projectId);
        member.setUserId(user.getId());
        member.setRole(request.getRole().getValue());
        member.setInviteCode(inviteCode);
        member.setInviteStatus("INVITED");
        collabProjectMemberMapper.insert(member);

        log.info("邀请成员: projectId={}, email={}, role={}", projectId, request.getEmail(), request.getRole());
        return toMemberResponse(member, user);
    }

    /**
     * 通过邀请码加入项目
     */
    @Transactional
    public ProjectMemberResponse joinByInviteCode(String inviteCode, Long userId) {
        CollabProjectMember member = collabProjectMemberMapper.selectByInviteCode(inviteCode);
        if (member == null) {
            throw new IllegalArgumentException("邀请码无效");
        }

        member.setInviteStatus("ACTIVE");
        member.setJoinedTime(LocalDateTime.now());
        collabProjectMemberMapper.updateById(member);

        User user = userMapper.selectById(member.getUserId());
        log.info("加入项目: userId={}, projectId={}", userId, member.getProjectId());
        return toMemberResponse(member, user);
    }

    /**
     * 获取项目成员列表
     */
    public List<ProjectMemberResponse> getMembers(Long projectId) {
        List<CollabProjectMember> members = collabProjectMemberMapper.selectByProjectId(projectId);
        return members.stream()
                .map(m -> {
                    User user = userMapper.selectById(m.getUserId());
                    return toMemberResponse(m, user);
                })
                .collect(Collectors.toList());
    }

    /**
     * 移除成员
     */
    @Transactional
    public void removeMember(Long projectId, Long memberId, Long operatorId) {
        CollabProjectMember member = collabProjectMemberMapper.selectById(memberId);
        if (member == null || !member.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("成员不存在");
        }

        // OWNER 不能被移除（除非自己转让）
        if (ProjectMemberRole.OWNER.getValue().equals(member.getRole())) {
            throw new IllegalStateException("所有者不能直接移除，需先转让项目");
        }

        member.setInviteStatus("REMOVED");
        collabProjectMemberMapper.updateById(member);
    }

    /**
     * 计算项目进度（基于章节完成百分比）
     */
    public int getProjectProgress(Long projectId) {
        // 由 ChapterTaskService 提供
        return 0;
    }

    private CollabProjectResponse toProjectResponse(CollabProject project) {
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

        // 设置所有者名称
        User owner = userMapper.selectById(project.getOwnerId());
        if (owner != null) {
            resp.setOwnerName(owner.getUsername());
        }

        // 设置成员数
        int memberCount = collabProjectMemberMapper.selectByProjectId(project.getId()).size();
        resp.setMemberCount(memberCount);

        return resp;
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

    /**
     * 团队模式创建项目返回结果
     */
    public record TeamProjectCreateResult(Long projectId, String documentName, int chapterCount) {}
}
