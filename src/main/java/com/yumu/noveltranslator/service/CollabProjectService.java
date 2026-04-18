package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.CollabProject;
import com.yumu.noveltranslator.entity.CollabProjectMember;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.enums.CollabProjectStatus;
import com.yumu.noveltranslator.enums.ProjectMemberRole;
import com.yumu.noveltranslator.mapper.CollabProjectMapper;
import com.yumu.noveltranslator.mapper.CollabProjectMemberMapper;
import com.yumu.noveltranslator.mapper.UserMapper;
import com.yumu.noveltranslator.service.state.CollabStateMachine;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final UserMapper userMapper;
    private final CollabStateMachine collabStateMachine;

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
}
