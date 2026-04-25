package com.yumu.noveltranslator.controller.collab;

import com.yumu.noveltranslator.dto.CommentResponse;
import com.yumu.noveltranslator.dto.CreateCommentRequest;
import com.yumu.noveltranslator.service.CollabCommentService;
import com.yumu.noveltranslator.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import com.yumu.noveltranslator.dto.Result;
import com.yumu.noveltranslator.dto.PageResponse;

@RestController
@RequestMapping("/v1/collab")
@RequiredArgsConstructor
@Slf4j
public class CollabCommentController {

    private final CollabCommentService collabCommentService;

    @PostMapping("/chapters/{chapterTaskId}/comments")
    public Result<CommentResponse> createComment(@PathVariable Long chapterTaskId,
                                                   @Valid @RequestBody CreateCommentRequest request) {
        Long userId = SecurityUtil.getRequiredUserId();
        CommentResponse comment = collabCommentService.createComment(chapterTaskId, userId, request);
        return Result.ok(comment);
    }

    @GetMapping("/chapters/{chapterTaskId}/comments")
    public Result<PageResponse<CommentResponse>> listComments(
            @PathVariable Long chapterTaskId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = SecurityUtil.getRequiredUserId();
        com.baomidou.mybatisplus.core.metadata.IPage<CommentResponse> iPage =
            collabCommentService.getCommentsByChapterPage(chapterTaskId, userId, page, size);
        PageResponse<CommentResponse> response = PageResponse.of(
            (int) iPage.getCurrent(), (int) iPage.getSize(), iPage.getTotal(), iPage.getRecords());
        return Result.ok(response);
    }

    @PutMapping("/comments/{commentId}/resolve")
    public Result<Void> resolveComment(@PathVariable Long commentId) {
        Long userId = SecurityUtil.getRequiredUserId();
        collabCommentService.resolveComment(commentId, userId);
        return Result.ok(null);
    }

    @DeleteMapping("/comments/{commentId}")
    public Result<Void> deleteComment(@PathVariable Long commentId) {
        Long userId = SecurityUtil.getRequiredUserId();
        collabCommentService.deleteComment(commentId, userId);
        return Result.ok(null);
    }
}
