package com.yumu.noveltranslator.port.in;

import com.yumu.noveltranslator.port.dto.collab.CommentResponse;
import com.yumu.noveltranslator.port.dto.collab.CreateCommentRequest;
import com.yumu.noveltranslator.port.dto.common.PageResult;

public interface CollabCommentPort {
    CommentResponse createComment(Long chapterTaskId, Long userId, CreateCommentRequest request);
    PageResult<CommentResponse> getCommentsByChapterPage(Long chapterTaskId, Long userId, int page, int size);
    void resolveComment(Long commentId, Long userId);
    void deleteComment(Long commentId, Long userId);
}
