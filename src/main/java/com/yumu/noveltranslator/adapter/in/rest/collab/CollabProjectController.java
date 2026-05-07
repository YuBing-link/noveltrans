package com.yumu.noveltranslator.adapter.in.rest.collab;

import com.yumu.noveltranslator.dto.collab.CollabProjectResponse;
import com.yumu.noveltranslator.dto.collab.CreateCollabProjectRequest;
import com.yumu.noveltranslator.dto.collab.ChapterTaskResponse;
import com.yumu.noveltranslator.dto.collab.AssignChapterRequest;
import com.yumu.noveltranslator.dto.common.Result;
import com.yumu.noveltranslator.dto.common.PageResponse;
import com.yumu.noveltranslator.enums.CollabProjectStatus;
import com.yumu.noveltranslator.enums.ProjectMemberRole;
import com.yumu.noveltranslator.adapter.in.security.annotation.RequireProjectAccess;
import com.yumu.noveltranslator.domain.service.ChapterTaskService;
import com.yumu.noveltranslator.port.in.CollabPort;
import com.yumu.noveltranslator.util.SseEmitterUtil;
import com.yumu.noveltranslator.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 协作项目 Controller
 */
@RestController
@RequestMapping("/v1/collab")
@RequiredArgsConstructor
@Slf4j
public class CollabProjectController {

    private final CollabPort collabPort;
    private final ChapterTaskService chapterTaskService;
    private final SseEmitterUtil sseEmitterUtil;

    // ==================== 项目管理 ====================

    @PostMapping("/projects")
    public Result<CollabProjectResponse> createProject(@Valid @RequestBody CreateCollabProjectRequest request) {
        Long userId = SecurityUtil.getRequiredUserId();
        CollabProjectResponse project = collabPort.createProject(request, userId);
        return Result.ok(project);
    }

    @GetMapping("/projects")
    public Result<PageResponse<CollabProjectResponse>> listProjects(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        Long userId = SecurityUtil.getRequiredUserId();
        PageResponse<CollabProjectResponse> projects = collabPort.listByUserId(userId, page, pageSize);
        return Result.ok(projects);
    }

    @GetMapping("/projects/{projectId}")
    @RequireProjectAccess
    public Result<CollabProjectResponse> getProject(@PathVariable Long projectId) {
        CollabProjectResponse project = collabPort.getProjectById(projectId);
        return Result.ok(project);
    }

    @PutMapping("/projects/{projectId}")
    @RequireProjectAccess
    public Result<CollabProjectResponse> updateProject(@PathVariable Long projectId,
                                                        @Valid @RequestBody CreateCollabProjectRequest request) {
        Long userId = SecurityUtil.getRequiredUserId();
        CollabProjectResponse project = collabPort.updateProject(projectId, request, userId);
        return Result.ok(project);
    }

    @PostMapping("/projects/{projectId}/status")
    @RequireProjectAccess(roles = {ProjectMemberRole.OWNER})
    public Result<Void> changeProjectStatus(@PathVariable Long projectId,
                                             @RequestParam String targetStatus) {
        CollabProjectStatus target = CollabProjectStatus.fromValue(targetStatus);
        Long userId = SecurityUtil.getRequiredUserId();
        collabPort.changeProjectStatus(projectId, target, userId);
        return Result.ok(null);
    }

    @DeleteMapping("/projects/{projectId}")
    @RequireProjectAccess(roles = {ProjectMemberRole.OWNER})
    public Result<Void> deleteProject(@PathVariable Long projectId) {
        Long userId = SecurityUtil.getRequiredUserId();
        collabPort.deleteProject(projectId, userId);
        return Result.ok(null);
    }

    @PostMapping("/projects/{projectId}/invite-code")
    @RequireProjectAccess(roles = {ProjectMemberRole.OWNER})
    public Result<CollabPort.InviteCodeResult> generateInviteCode(@PathVariable Long projectId) {
        Long userId = SecurityUtil.getRequiredUserId();
        CollabProjectService.InviteCodeResult result = collabPort.generateInviteCode(projectId, userId);
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
    public Result<PageResponse<ChapterTaskResponse>> listChapters(
            @PathVariable Long projectId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        PageResponse<ChapterTaskResponse> chapters = chapterTaskService.listByProjectId(projectId, page, pageSize);
        return Result.ok(chapters);
    }

    // ==================== SSE 事件流 ====================

    /**
     * 协作项目 SSE 事件流端点。
     * 客户端断开后重连时传入 lastEventId 即可补发遗漏事件。
     *
     * @param projectId   项目ID
     * @param lastEventId 上次收到的事件 ID（可选，用于补发）
     */
    @GetMapping(value = "/sse/{projectId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequireProjectAccess
    public SseEmitter collabSse(@PathVariable Long projectId,
                                @RequestParam(required = false) String lastEventId) {
        SseEmitter emitter = SseEmitterUtil.createSseEmitter(null);

        // 如果有 lastEventId，先重放遗漏的事件
        if (lastEventId != null && !lastEventId.isBlank()) {
            sseEmitterUtil.replayMissedEvents(String.valueOf(projectId), lastEventId, emitter);
        }

        // 发送连接确认事件
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(String.format("{\"projectId\":%d}", projectId)));
        } catch (Exception e) {
            log.warn("SSE 连接确认发送失败: projectId={}", projectId);
        }

        // 完成 emitter（客户端应重新建立连接以接收后续实时事件）
        SseEmitterUtil.sendDone(emitter);
        SseEmitterUtil.complete(emitter);

        return emitter;
    }
}
