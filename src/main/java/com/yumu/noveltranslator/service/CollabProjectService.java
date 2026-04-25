package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.config.tenant.TenantContext;
import com.yumu.noveltranslator.dto.*;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yumu.noveltranslator.entity.CollabChapterTask;
import com.yumu.noveltranslator.entity.CollabComment;
import com.yumu.noveltranslator.entity.CollabInviteCode;
import com.yumu.noveltranslator.entity.CollabProject;
import com.yumu.noveltranslator.entity.CollabProjectMember;
import com.yumu.noveltranslator.entity.Document;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.enums.ChapterTaskStatus;
import com.yumu.noveltranslator.enums.CollabProjectStatus;
import com.yumu.noveltranslator.enums.ProjectMemberRole;
import com.yumu.noveltranslator.enums.TranslationStatus;
import com.yumu.noveltranslator.mapper.CollabChapterTaskMapper;
import com.yumu.noveltranslator.mapper.CollabCommentMapper;
import com.yumu.noveltranslator.mapper.CollabInviteCodeMapper;
import com.yumu.noveltranslator.mapper.CollabProjectMapper;
import com.yumu.noveltranslator.mapper.CollabProjectMemberMapper;
import com.yumu.noveltranslator.mapper.DocumentMapper;
import com.yumu.noveltranslator.mapper.UserMapper;
import com.yumu.noveltranslator.service.state.CollabStateMachine;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
    private final CollabCommentMapper collabCommentMapper;
    private final CollabInviteCodeMapper collabInviteCodeMapper;
    private final DocumentMapper documentMapper;
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

        // 更新文档状态为处理中
        Document doc = documentMapper.selectById(documentId);
        if (doc != null) {
            doc.setStatus(TranslationStatus.PROCESSING.getValue());
            doc.setUpdateTime(java.time.LocalDateTime.now());
            documentMapper.updateById(doc);
        }

        return new TeamProjectCreateResult(project.getId(), documentName, chapters.size());
    }

    /**
     * 将文档章节添加到已有协作项目
     *
     * @param userId 用户ID
     * @param projectId 目标项目ID
     * @param document 上传的文档
     * @return 添加的章节数量
     */
    @Transactional
    public int addChaptersToProject(Long userId, Long projectId, Document document) {
        CollabProject project = getById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("项目不存在: " + projectId);
        }

        // 权限校验：只有项目所有者或成员才能添加章节
        CollabProjectMember member = collabProjectMemberMapper.selectByProjectAndUser(projectId, userId);
        if (member == null && !project.getOwnerId().equals(userId)) {
            throw new IllegalStateException("无权向该项目添加章节");
        }

        // 读取文档内容
        String content;
        try {
            content = Files.readString(Paths.get(document.getPath()), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("读取文档失败: " + e.getMessage());
        }

        if (content == null || content.trim().isEmpty()) {
            throw new RuntimeException("文档内容为空");
        }

        // 按段落分割
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

        if (chapters.isEmpty()) {
            chapters.add(content.trim());
        }

        // 获取当前最大章节号
        List<CollabChapterTask> existingChapters = collabChapterTaskMapper.selectByProjectId(projectId);
        int nextChapterNumber = existingChapters.stream()
                .mapToInt(CollabChapterTask::getChapterNumber)
                .max()
                .orElse(0) + 1;

        // 创建章节
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
            collabChapterTaskMapper.insert(chapter);
        }

        // 更新文档状态
        document.setStatus(TranslationStatus.PROCESSING.getValue());
        document.setUpdateTime(java.time.LocalDateTime.now());
        documentMapper.updateById(document);

        log.info("添加章节到项目: projectId={}, docName={}, chapters={}", projectId, document.getName(), chapters.size());
        return chapters.size();
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
     * 获取用户参与的项目列表（包括作为成员，可能跨租户）
     */
    public PageResponse<CollabProjectResponse> listByUserId(Long userId, int page, int pageSize) {
        try {
            TenantContext.setBypassTenant(true);
            List<CollabProject> allProjects = collabProjectMapper.selectByMemberUserId(userId);
            long total = allProjects.size();
            int fromIndex = Math.min((page - 1) * pageSize, (int) total);
            int toIndex = Math.min(fromIndex + pageSize, (int) total);
            List<CollabProject> pagedProjects = fromIndex < total
                    ? allProjects.subList(fromIndex, toIndex)
                    : List.of();
            List<CollabProjectResponse> responseList = pagedProjects.stream()
                    .map(this::toProjectResponse)
                    .collect(Collectors.toList());
            return PageResponse.of(page, pageSize, total, responseList);
        } finally {
            TenantContext.setBypassTenant(false);
        }
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
     * 生成项目邀请码（8位随机码，有效期72小时）
     */
    @Transactional
    public InviteCodeResult generateInviteCode(Long projectId, Long operatorId) {
        CollabProject project = getById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("项目不存在: " + projectId);
        }

        String code = generateRandomInviteCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(72);

        CollabInviteCode inviteCode = new CollabInviteCode();
        inviteCode.setProjectId(projectId);
        inviteCode.setCode(code);
        inviteCode.setExpiresAt(expiresAt);
        inviteCode.setUsed(0);
        collabInviteCodeMapper.insert(inviteCode);

        log.info("生成邀请码: projectId={}, code={}, expiresAt={}", projectId, code, expiresAt);
        return new InviteCodeResult(code, expiresAt);
    }

    /**
     * 生成8位随机邀请码（大写字母+数字）
     */
    private String generateRandomInviteCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 去除易混淆字符 I/O/0/1
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder code = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        // 确保唯一性，如果重复则重试
        if (collabInviteCodeMapper.selectByCode(code.toString()) != null) {
            return generateRandomInviteCode();
        }
        return code.toString();
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
     * 通过邀请码加入项目（跨租户操作，需要绕过租户过滤）
     */
    @Transactional
    public ProjectMemberResponse joinByInviteCode(String inviteCode, Long userId) {
        // 查询有效邀请码（未过期、未使用）
        CollabInviteCode codeRecord = collabInviteCodeMapper.selectByValidCode(inviteCode);
        if (codeRecord == null) {
            // 进一步检查是否是已过期或已使用
            CollabInviteCode anyCode = collabInviteCodeMapper.selectByCode(inviteCode);
            if (anyCode == null) {
                throw new IllegalArgumentException("邀请码无效");
            }
            if (anyCode.getUsed() == 1) {
                throw new IllegalArgumentException("邀请码已被使用");
            }
            if (anyCode.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw new IllegalArgumentException("邀请码已过期");
            }
            throw new IllegalArgumentException("邀请码不可用");
        }

        // 标记为已使用
        collabInviteCodeMapper.markAsUsed(codeRecord.getId());

        // 跨租户查询项目和创建成员（邀请码允许不同租户的用户加入同一项目）
        try {
            TenantContext.setBypassTenant(true);

            // 检查项目是否存在且为 ACTIVE
            CollabProject project = getById(codeRecord.getProjectId());
            if (project == null) {
                throw new IllegalArgumentException("关联项目不存在");
            }
            if (!CollabProjectStatus.ACTIVE.getValue().equals(project.getStatus())) {
                throw new IllegalStateException("项目当前不可加入");
            }

            // 检查是否已是成员
            CollabProjectMember existing = collabProjectMemberMapper.selectByProjectAndUser(codeRecord.getProjectId(), userId);
            if (existing != null) {
                throw new IllegalStateException("您已是该项目成员");
            }

            // 创建成员记录（成员的 tenant_id 与项目一致）
            CollabProjectMember member = new CollabProjectMember();
            member.setProjectId(codeRecord.getProjectId());
            member.setUserId(userId);
            member.setRole(ProjectMemberRole.TRANSLATOR.getValue());
            member.setInviteCode(inviteCode);
            member.setInviteStatus("ACTIVE");
            member.setJoinedTime(LocalDateTime.now());
            member.setTenantId(project.getTenantId()); // 成员的租户与项目一致
            collabProjectMemberMapper.insert(member);

            User user = userMapper.selectById(userId);
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
            allMembers = collabProjectMemberMapper.selectByProjectId(projectId);
        } finally {
            TenantContext.setBypassTenant(false);
        }
        long total = allMembers.size();
        int fromIndex = Math.min((page - 1) * pageSize, (int) total);
        int toIndex = Math.min(fromIndex + pageSize, (int) total);
        List<CollabProjectMember> pagedMembers = fromIndex < total
                ? allMembers.subList(fromIndex, toIndex)
                : List.of();
        List<ProjectMemberResponse> responseList = pagedMembers.stream()
                .map(m -> {
                    User user = userMapper.selectById(m.getUserId());
                    return toMemberResponse(m, user);
                })
                .collect(Collectors.toList());
        return PageResponse.of(page, pageSize, total, responseList);
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
     * 删除项目（仅 OWNER 可操作，级联逻辑删除所有关联数据）
     */
    @Transactional
    public void deleteProject(Long projectId, Long userId) {
        CollabProject project = getById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("项目不存在");
        }
        if (!project.getOwnerId().equals(userId)) {
            throw new IllegalStateException("只有项目所有者可以删除项目");
        }

        // 级联逻辑删除：评论 → 章节 → 成员 → 项目
        List<CollabChapterTask> chapters = collabChapterTaskMapper.selectByProjectId(projectId);
        for (CollabChapterTask chapter : chapters) {
            collabCommentMapper.delete(new QueryWrapper<CollabComment>().eq("chapter_task_id", chapter.getId()));
        }
        collabChapterTaskMapper.delete(new QueryWrapper<CollabChapterTask>().eq("project_id", projectId));
        collabProjectMemberMapper.delete(new QueryWrapper<CollabProjectMember>().eq("project_id", projectId));
        removeById(projectId);

        log.info("删除协作项目: projectId={}, name={}, ownerId={}", projectId, project.getName(), userId);
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

    /**
     * 邀请码生成结果
     */
    public record InviteCodeResult(String code, LocalDateTime expiresAt) {}
}
