package com.yumu.noveltranslator.controller.collab;

import com.yumu.noveltranslator.dto.CommentResponse;
import com.yumu.noveltranslator.dto.CreateCommentRequest;
import com.yumu.noveltranslator.security.annotation.RequireProjectAccess;
import com.yumu.noveltranslator.service.CollabCommentService;
import com.yumu.noveltranslator.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import com.yumu.noveltranslator.dto.Result;
import java.util.List;

/**
 * 协作评论 Controller
 */
@RestController
@RequestMapping("/v1/collab")
@RequiredArgsConstructor
@Slf4j
public class CollabCommentController {

    private final CollabCommentService collabCommentService;

    @PostMapping("/chapters/{chapterTaskId}/comments")
    @RequireProjectAccess
    public Result<CommentResponse> createComment(@PathVariable Long chapterTaskId,
                                                   @Valid @RequestBody CreateCommentRequest request) {
        Long userId = SecurityUtil.getRequiredUserId();
        CommentResponse comment = collabCommentService.createComment(chapterTaskId, userId, request);
        return Result.ok(comment);
    }

    @GetMapping("/chapters/{chapterTaskId}/comments")
    @RequireProjectAccess
    public Result<List<CommentResponse>> listComments(@PathVariable Long chapterTaskId) {
        List<CommentResponse> comments = collabCommentService.getCommentsByChapter(chapterTaskId);
        return Result.ok(comments, "200");
    }

    @PutMapping("/comments/{commentId}/resolve")
    @RequireProjectAccess
    public Result<Void> resolveComment(@PathVariable Long commentId) {
        Long userId = SecurityUtil.getRequiredUserId();
        collabCommentService.resolveComment(commentId, userId);
        return Result.ok(null);
    }

    @DeleteMapping("/comments/{commentId}")
    @RequireProjectAccess
    public Result<Void> deleteComment(@PathVariable Long commentId) {
        Long userId = SecurityUtil.getRequiredUserId();
        collabCommentService.deleteComment(commentId, userId);
        return Result.ok(null);
    }
}
