package com.yumu.noveltranslator.adapter.in.rest.collab;

import com.yumu.noveltranslator.port.dto.common.Result;
import com.yumu.noveltranslator.port.dto.common.PageResponse;
import com.yumu.noveltranslator.port.dto.collab.ChapterTaskResponse;
import com.yumu.noveltranslator.port.dto.collab.AssignChapterRequest;
import com.yumu.noveltranslator.port.dto.collab.SubmitChapterRequest;
import com.yumu.noveltranslator.port.dto.collab.ReviewChapterRequest;
import com.yumu.noveltranslator.enums.ProjectMemberRole;
import com.yumu.noveltranslator.adapter.in.security.annotation.RequireProjectAccess;
import com.yumu.noveltranslator.port.in.ChapterTaskPort;
import com.yumu.noveltranslator.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;


import java.util.List;

/**
 * 章节任务管理 Controller
 */
@RestController
@RequestMapping("/v1/collab")
@RequiredArgsConstructor
@Slf4j
public class ChapterTaskController {

    private final ChapterTaskPort chapterTaskPort;

    @PutMapping("/chapters/{chapterId}/assign")
    public Result<ChapterTaskResponse> assignChapter(@PathVariable Long chapterId,
                                                       @Valid @RequestBody AssignChapterRequest request) {
        Long assignerId = SecurityUtil.getRequiredUserId();
        ChapterTaskResponse chapter = chapterTaskPort.assignChapter(chapterId, request.getAssigneeId(), assignerId);
        return Result.ok(chapter);
    }

    @PutMapping("/chapters/{chapterId}/submit")
    public Result<ChapterTaskResponse> submitChapter(@PathVariable Long chapterId,
                                                       @Valid @RequestBody SubmitChapterRequest request) {
        ChapterTaskResponse chapter = chapterTaskPort.submitChapter(chapterId, request.getTranslatedText());
        return Result.ok(chapter);
    }

    @PutMapping("/chapters/{chapterId}/review")
    public Result<ChapterTaskResponse> reviewChapter(@PathVariable Long chapterId,
                                                       @Valid @RequestBody ReviewChapterRequest request) {
        Long reviewerId = SecurityUtil.getRequiredUserId();
        ChapterTaskResponse chapter = chapterTaskPort.reviewChapter(chapterId, request.getApproved(), request.getComment(), reviewerId);
        return Result.ok(chapter);
    }

    @GetMapping("/chapters/{chapterId}")
    public Result<ChapterTaskResponse> getChapter(@PathVariable Long chapterId) {
        Long userId = SecurityUtil.getRequiredUserId();
        ChapterTaskResponse chapter = chapterTaskPort.getChapterById(chapterId, userId);
        return Result.ok(chapter);
    }

    @GetMapping("/chapters/my")
    public Result<PageResponse<ChapterTaskResponse>> listMyChapters(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        Long userId = SecurityUtil.getRequiredUserId();
        PageResponse<ChapterTaskResponse> chapters = chapterTaskPort.listByAssigneeId(userId, page, pageSize);
        return Result.ok(chapters);
    }
}
