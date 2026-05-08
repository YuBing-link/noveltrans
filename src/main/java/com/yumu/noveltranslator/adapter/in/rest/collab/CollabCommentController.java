package com.yumu.noveltranslator.adapter.in.rest.collab;

import com.yumu.noveltranslator.port.dto.collab.CommentResponse;
import com.yumu.noveltranslator.port.dto.collab.CreateCommentRequest;
import com.yumu.noveltranslator.port.in.CollabCommentPort;
import com.yumu.noveltranslator.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import com.yumu.noveltranslator.port.dto.common.Result;
import com.yumu.noveltranslator.port.dto.common.PageResponse;

@RestController
@RequestMapping("/v1/collab")
@RequiredArgsConstructor
@Slf4j
public class CollabCommentController {

    private final CollabCommentPort collabCommentPort;

    @PostMapping("/chapters/{chapterTaskId}/comments")
    public Result<CommentResponse> createComment(@PathVariable Long chapterTaskId,
                                                   @Valid @RequestBody CreateCommentRequest request) {
        Long userId = SecurityUtil.getRequiredUserId();
        CommentResponse comment = collabCommentPort.createComment(chapterTaskId, userId, request);
        return Result.ok(comment);
    }

    @GetMapping("/chapters/{chapterTaskId}/comments")
    public Result<PageResponse<CommentResponse>> listComments(
            @PathVariable Long chapterTaskId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = SecurityUtil.getRequiredUserId();
        com.yumu.noveltranslator.port.dto.common.PageResult<CommentResponse> pageResult =
            collabCommentPort.getCommentsByChapterPage(chapterTaskId, userId, page, size);
        PageResponse<CommentResponse> response = PageResponse.of(
            (int) pageResult.getCurrent(), (int) pageResult.getSize(), pageResult.getTotal(), pageResult.getRecords());
        return Result.ok(response);
    }

    @PutMapping("/comments/{commentId}/resolve")
    public Result<Void> resolveComment(@PathVariable Long commentId) {
        Long userId = SecurityUtil.getRequiredUserId();
        collabCommentPort.resolveComment(commentId, userId);
        return Result.ok(null);
    }

    @DeleteMapping("/comments/{commentId}")
    public Result<Void> deleteComment(@PathVariable Long commentId) {
        Long userId = SecurityUtil.getRequiredUserId();
        collabCommentPort.deleteComment(commentId, userId);
        return Result.ok(null);
    }
}
