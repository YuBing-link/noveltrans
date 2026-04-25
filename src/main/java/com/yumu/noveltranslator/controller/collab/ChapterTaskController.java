package com.yumu.noveltranslator.controller.collab;

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
import com.yumu.noveltranslator.dto.PageResponse;
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
    public Result<ChapterTaskResponse> assignChapter(@PathVariable Long chapterId,
                                                       @Valid @RequestBody AssignChapterRequest request) {
        Long assignerId = SecurityUtil.getRequiredUserId();
        ChapterTaskResponse chapter = chapterTaskService.assignChapter(chapterId, request.getAssigneeId(), assignerId);
        return Result.ok(chapter);
    }

    @PutMapping("/chapters/{chapterId}/submit")
    public Result<ChapterTaskResponse> submitChapter(@PathVariable Long chapterId,
                                                       @Valid @RequestBody SubmitChapterRequest request) {
        ChapterTaskResponse chapter = chapterTaskService.submitChapter(chapterId, request.getTranslatedText());
        return Result.ok(chapter);
    }

    @PutMapping("/chapters/{chapterId}/review")
    public Result<ChapterTaskResponse> reviewChapter(@PathVariable Long chapterId,
                                                       @Valid @RequestBody ReviewChapterRequest request) {
        Long reviewerId = SecurityUtil.getRequiredUserId();
        ChapterTaskResponse chapter = chapterTaskService.reviewChapter(chapterId, request.getApproved(), request.getComment(), reviewerId);
        return Result.ok(chapter);
    }

    @GetMapping("/chapters/{chapterId}")
    public Result<ChapterTaskResponse> getChapter(@PathVariable Long chapterId) {
        Long userId = SecurityUtil.getRequiredUserId();
        ChapterTaskResponse chapter = chapterTaskService.getChapterById(chapterId, userId);
        return Result.ok(chapter);
    }

    @GetMapping("/chapters/my")
    public Result<PageResponse<ChapterTaskResponse>> listMyChapters(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        Long userId = SecurityUtil.getRequiredUserId();
        PageResponse<ChapterTaskResponse> chapters = chapterTaskService.listByAssigneeId(userId, page, pageSize);
        return Result.ok(chapters);
    }
}
