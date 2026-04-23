package com.yumu.noveltranslator.controller.collab;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.enums.CollabProjectStatus;
import com.yumu.noveltranslator.enums.ProjectMemberRole;
import com.yumu.noveltranslator.security.annotation.RequireProjectAccess;
import com.yumu.noveltranslator.service.ChapterTaskService;
import com.yumu.noveltranslator.service.CollabProjectService;
import com.yumu.noveltranslator.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import com.yumu.noveltranslator.dto.Result;
import java.util.List;

/**
 * 协作项目 Controller
 */
@RestController
@RequestMapping("/v1/collab")
@RequiredArgsConstructor
@Slf4j
public class CollabProjectController {

    private final CollabProjectService collabProjectService;
    private final ChapterTaskService chapterTaskService;

    // ==================== 项目管理 ====================

    @PostMapping("/projects")
    public Result<CollabProjectResponse> createProject(@Valid @RequestBody CreateCollabProjectRequest request) {
        Long userId = SecurityUtil.getRequiredUserId();
        CollabProjectResponse project = collabProjectService.createProject(request, userId);
        return Result.ok(project);
    }

    @GetMapping("/projects")
    public Result<List<CollabProjectResponse>> listProjects() {
        Long userId = SecurityUtil.getRequiredUserId();
        List<CollabProjectResponse> projects = collabProjectService.listByUserId(userId);
        return Result.ok(projects);
    }

    @GetMapping("/projects/{projectId}")
    @RequireProjectAccess
    public Result<CollabProjectResponse> getProject(@PathVariable Long projectId) {
        CollabProjectResponse project = collabProjectService.getProjectById(projectId);
        return Result.ok(project);
    }

    @PutMapping("/projects/{projectId}")
    @RequireProjectAccess
    public Result<CollabProjectResponse> updateProject(@PathVariable Long projectId,
                                                        @Valid @RequestBody CreateCollabProjectRequest request) {
        Long userId = SecurityUtil.getRequiredUserId();
        CollabProjectResponse project = collabProjectService.updateProject(projectId, request, userId);
        return Result.ok(project);
    }

    @PostMapping("/projects/{projectId}/status")
    @RequireProjectAccess(roles = {ProjectMemberRole.OWNER})
    public Result<Void> changeProjectStatus(@PathVariable Long projectId,
                                             @RequestParam String targetStatus) {
        CollabProjectStatus target = CollabProjectStatus.fromValue(targetStatus);
        Long userId = SecurityUtil.getRequiredUserId();
        collabProjectService.changeProjectStatus(projectId, target, userId);
        return Result.ok(null);
    }

    @DeleteMapping("/projects/{projectId}")
    @RequireProjectAccess(roles = {ProjectMemberRole.OWNER})
    public Result<Void> deleteProject(@PathVariable Long projectId) {
        Long userId = SecurityUtil.getRequiredUserId();
        collabProjectService.deleteProject(projectId, userId);
        return Result.ok(null);
    }

    @PostMapping("/projects/{projectId}/invite-code")
    @RequireProjectAccess(roles = {ProjectMemberRole.OWNER})
    public Result<com.yumu.noveltranslator.service.CollabProjectService.InviteCodeResult> generateInviteCode(@PathVariable Long projectId) {
        Long userId = SecurityUtil.getRequiredUserId();
        com.yumu.noveltranslator.service.CollabProjectService.InviteCodeResult result = collabProjectService.generateInviteCode(projectId, userId);
        return Result.ok(result);
    }

    // ==================== 章节管理 ====================

    @PostMapping("/projects/{projectId}/chapters")
    @RequireProjectAccess(roles = {ProjectMemberRole.OWNER})
    public Result<ChapterTaskResponse> createChapter(@PathVariable Long projectId,
                                                      @RequestParam Integer chapterNumber,
                                                      @RequestParam(required = false) String title,
                                                      @RequestParam(required = false) String sourceText) {
        Long userId = SecurityUtil.getRequiredUserId();
        ChapterTaskResponse chapter = chapterTaskService.createChapter(projectId, chapterNumber, title, sourceText, userId);
        return Result.ok(chapter);
    }

    @GetMapping("/projects/{projectId}/chapters")
    @RequireProjectAccess
    public Result<List<ChapterTaskResponse>> listChapters(@PathVariable Long projectId) {
        List<ChapterTaskResponse> chapters = chapterTaskService.listByProjectId(projectId);
        return Result.ok(chapters);
    }
}
