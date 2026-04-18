package com.yumu.noveltranslator.controller;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.enums.CollabProjectStatus;
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
        return Result.ok(project, "200");
    }

    @GetMapping("/projects")
    public Result<List<CollabProjectResponse>> listProjects() {
        Long userId = SecurityUtil.getRequiredUserId();
        List<CollabProjectResponse> projects = collabProjectService.listByUserId(userId);
        return Result.ok(projects, "200");
    }

    @GetMapping("/projects/{projectId}")
    @RequireProjectAccess
    public Result<CollabProjectResponse> getProject(@PathVariable Long projectId) {
        CollabProjectResponse project = collabProjectService.getProjectById(projectId);
        return Result.ok(project, "200");
    }

    @PutMapping("/projects/{projectId}")
    @RequireProjectAccess
    public Result<CollabProjectResponse> updateProject(@PathVariable Long projectId,
                                                        @Valid @RequestBody CreateCollabProjectRequest request) {
        Long userId = SecurityUtil.getRequiredUserId();
        CollabProjectResponse project = collabProjectService.updateProject(projectId, request, userId);
        return Result.ok(project, "200");
    }

    @PatchMapping("/projects/{projectId}/status")
    @RequireProjectAccess(roles = {com.yumu.noveltranslator.enums.ProjectMemberRole.OWNER})
    public Result<Void> changeProjectStatus(@PathVariable Long projectId,
                                             @RequestParam String targetStatus) {
        CollabProjectStatus target = CollabProjectStatus.fromValue(targetStatus);
        Long userId = SecurityUtil.getRequiredUserId();
        collabProjectService.changeProjectStatus(projectId, target, userId);
        return Result.ok(null, "200");
    }

    // ==================== 章节管理 ====================

    @PostMapping("/projects/{projectId}/chapters")
    @RequireProjectAccess(roles = {com.yumu.noveltranslator.enums.ProjectMemberRole.OWNER})
    public Result<ChapterTaskResponse> createChapter(@PathVariable Long projectId,
                                                      @RequestParam Integer chapterNumber,
                                                      @RequestParam(required = false) String title,
                                                      @RequestParam(required = false) String sourceText) {
        Long userId = SecurityUtil.getRequiredUserId();
        ChapterTaskResponse chapter = chapterTaskService.createChapter(projectId, chapterNumber, title, sourceText, userId);
        return Result.ok(chapter, "200");
    }

    @GetMapping("/projects/{projectId}/chapters")
    @RequireProjectAccess
    public Result<List<ChapterTaskResponse>> listChapters(@PathVariable Long projectId) {
        List<ChapterTaskResponse> chapters = chapterTaskService.listByProjectId(projectId);
        return Result.ok(chapters, "200");
    }
}
