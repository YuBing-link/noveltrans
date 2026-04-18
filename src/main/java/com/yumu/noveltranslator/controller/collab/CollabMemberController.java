package com.yumu.noveltranslator.controller.collab;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.enums.ProjectMemberRole;
import com.yumu.noveltranslator.security.annotation.RequireProjectAccess;
import com.yumu.noveltranslator.service.CollabProjectService;
import com.yumu.noveltranslator.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import com.yumu.noveltranslator.dto.Result;
import java.util.List;

/**
 * 协作成员管理 Controller
 */
@RestController
@RequestMapping("/v1/collab")
@RequiredArgsConstructor
@Slf4j
public class CollabMemberController {

    private final CollabProjectService collabProjectService;

    @PostMapping("/projects/{projectId}/invite")
    @RequireProjectAccess(roles = {ProjectMemberRole.OWNER})
    public Result<ProjectMemberResponse> inviteMember(@PathVariable Long projectId,
                                                       @Valid @RequestBody InviteMemberRequest request) {
        Long inviterId = SecurityUtil.getRequiredUserId();
        ProjectMemberResponse member = collabProjectService.inviteMember(projectId, request, inviterId);
        return Result.ok(member);
    }

    @PostMapping("/join")
    public Result<ProjectMemberResponse> joinByCode(@RequestParam String inviteCode) {
        Long userId = SecurityUtil.getRequiredUserId();
        ProjectMemberResponse member = collabProjectService.joinByInviteCode(inviteCode, userId);
        return Result.ok(member);
    }

    @GetMapping("/projects/{projectId}/members")
    @RequireProjectAccess
    public Result<List<ProjectMemberResponse>> listMembers(@PathVariable Long projectId) {
        List<ProjectMemberResponse> members = collabProjectService.getMembers(projectId);
        return Result.ok(members);
    }

    @DeleteMapping("/projects/{projectId}/members/{memberId}")
    @RequireProjectAccess(roles = {ProjectMemberRole.OWNER})
    public Result<Void> removeMember(@PathVariable Long projectId, @PathVariable Long memberId) {
        Long operatorId = SecurityUtil.getRequiredUserId();
        collabProjectService.removeMember(projectId, memberId, operatorId);
        return Result.ok(null);
    }
}
