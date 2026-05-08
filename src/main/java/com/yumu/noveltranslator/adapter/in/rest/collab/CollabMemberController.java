package com.yumu.noveltranslator.adapter.in.rest.collab;

import com.yumu.noveltranslator.port.dto.collab.InviteMemberRequest;
import com.yumu.noveltranslator.port.dto.collab.ProjectMemberResponse;
import com.yumu.noveltranslator.port.dto.common.Result;
import com.yumu.noveltranslator.port.dto.common.PageResponse;
import com.yumu.noveltranslator.enums.ProjectMemberRole;
import com.yumu.noveltranslator.adapter.in.security.annotation.RequireProjectAccess;
import com.yumu.noveltranslator.port.in.CollabPort;
import com.yumu.noveltranslator.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 协作成员管理 Controller
 */
@RestController
@RequestMapping("/v1/collab")
@RequiredArgsConstructor
@Slf4j
public class CollabMemberController {

    private final CollabPort collabPort;

    @PostMapping("/projects/{projectId}/invite")
    @RequireProjectAccess(roles = {ProjectMemberRole.OWNER})
    public Result<ProjectMemberResponse> inviteMember(@PathVariable Long projectId,
                                                       @Valid @RequestBody InviteMemberRequest request) {
        Long inviterId = SecurityUtil.getRequiredUserId();
        ProjectMemberResponse member = collabPort.inviteMember(projectId, request, inviterId);
        return Result.ok(member);
    }

    @PostMapping("/join")
    public Result<ProjectMemberResponse> joinByCode(@RequestParam String inviteCode) {
        Long userId = SecurityUtil.getRequiredUserId();
        ProjectMemberResponse member = collabPort.joinByInviteCode(inviteCode, userId);
        return Result.ok(member);
    }

    @GetMapping("/projects/{projectId}/members")
    @RequireProjectAccess
    public Result<PageResponse<ProjectMemberResponse>> listMembers(
            @PathVariable Long projectId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        PageResponse<ProjectMemberResponse> members = collabPort.getMembers(projectId, page, pageSize);
        return Result.ok(members);
    }

    @DeleteMapping("/projects/{projectId}/members/{memberId}")
    @RequireProjectAccess(roles = {ProjectMemberRole.OWNER})
    public Result<Void> removeMember(@PathVariable Long projectId, @PathVariable Long memberId) {
        Long operatorId = SecurityUtil.getRequiredUserId();
        collabPort.removeMember(projectId, memberId, operatorId);
        return Result.ok(null);
    }
}
