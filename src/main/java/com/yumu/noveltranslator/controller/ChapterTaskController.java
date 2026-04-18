package com.yumu.noveltranslator.controller;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.enums.ProjectMemberRole;
import com.yumu.noveltranslator.security.annotation.RequireProjectAccess;
import com.yumu.noveltranslator.service.ChapterTaskService;
import com.yumu.noveltranslator.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import com.yumu.noveltranslator.dto.Result;
import java.util.List;

/**
 * 章节任务管理 Controller
 */
@RestController
@RequestMapping("/v1/collab")
@RequiredArgsConstructor
@Slf4j
public class ChapterTaskController {

    private final ChapterTaskService chapterTaskService;

    @PutMapping("/chapters/{chapterId}/assign")
    @RequireProjectAccess(roles = {ProjectMemberRole.OWNER})
    public Result<ChapterTaskResponse> assignChapter(@PathVariable Long chapterId,
                                                       @Valid @RequestBody AssignChapterRequest request) {
        Long assignerId = SecurityUtil.getRequiredUserId();
        ChapterTaskResponse chapter = chapterTaskService.assignChapter(chapterId, request.getAssigneeId(), assignerId);
        return Result.ok(chapter, "200");
    }

    @PutMapping("/chapters/{chapterId}/submit")
    @RequireProjectAccess(roles = {ProjectMemberRole.TRANSLATOR, ProjectMemberRole.OWNER})
    public Result<ChapterTaskResponse> submitChapter(@PathVariable Long chapterId,
                                                       @Valid @RequestBody SubmitChapterRequest request) {
        ChapterTaskResponse chapter = chapterTaskService.submitChapter(chapterId, request.getTranslatedText());
        return Result.ok(chapter, "200");
    }

    @PutMapping("/chapters/{chapterId}/review")
    @RequireProjectAccess(roles = {ProjectMemberRole.REVIEWER, ProjectMemberRole.OWNER})
    public Result<ChapterTaskResponse> reviewChapter(@PathVariable Long chapterId,
                                                       @Valid @RequestBody ReviewChapterRequest request) {
        Long reviewerId = SecurityUtil.getRequiredUserId();
        ChapterTaskResponse chapter = chapterTaskService.reviewChapter(chapterId, request.getApproved(), request.getComment(), reviewerId);
        return Result.ok(chapter, "200");
    }

    @GetMapping("/chapters/{chapterId}")
    @RequireProjectAccess
    public Result<ChapterTaskResponse> getChapter(@PathVariable Long chapterId) {
        ChapterTaskResponse chapter = chapterTaskService.getChapterById(chapterId);
        return Result.ok(chapter, "200");
    }

    @GetMapping("/chapters/my")
    public Result<List<ChapterTaskResponse>> listMyChapters() {
        Long userId = SecurityUtil.getRequiredUserId();
        List<ChapterTaskResponse> chapters = chapterTaskService.listByAssigneeId(userId);
        return Result.ok(chapters, "200");
    }
}
