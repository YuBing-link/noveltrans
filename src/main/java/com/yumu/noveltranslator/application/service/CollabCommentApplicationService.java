package com.yumu.noveltranslator.application.service;

import com.yumu.noveltranslator.port.dto.collab.CommentResponse;
import com.yumu.noveltranslator.port.dto.collab.CreateCommentRequest;
import com.yumu.noveltranslator.domain.model.CollabComment;
import com.yumu.noveltranslator.domain.model.User;
import com.yumu.noveltranslator.enums.ErrorCodeEnum;
import com.yumu.noveltranslator.exception.BusinessException;
import com.yumu.noveltranslator.port.out.CollaborationRepositoryPort;
import com.yumu.noveltranslator.port.out.UserRepositoryPort;
import com.yumu.noveltranslator.port.dto.common.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 协作评论应用服务
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CollabCommentApplicationService implements com.yumu.noveltranslator.port.in.CollabCommentPort {

    private final CollaborationRepositoryPort collabPort;
    private final UserRepositoryPort userPort;

    /**
     * 创建评论
     */
    public CommentResponse createComment(Long chapterTaskId, Long userId, CreateCommentRequest request) {
        var task = collabPort.findChapterTaskById(chapterTaskId);
        if (task.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "章节不存在: " + chapterTaskId);
        }
        var member = collabPort.findMemberByProjectAndUser(task.get().getProjectId(), userId);
        if (member == null) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权访问该项目");
        }

        // 如果是回复，验证父评论是否存在
        if (request.getParentId() != null) {
            var parent = collabPort.findCommentById(request.getParentId());
            if (parent.isEmpty() || !parent.get().getChapterTaskId().equals(chapterTaskId)) {
                throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "父评论不存在");
            }
        }

        CollabComment comment = new CollabComment();
        comment.setChapterTaskId(chapterTaskId);
        comment.setUserId(userId);
        comment.setSourceText(request.getSourceText());
        comment.setTargetText(request.getTargetText());
        comment.setContent(request.getContent());
        comment.setParentId(request.getParentId());
        comment.setResolved(false);
        collabPort.saveComment(comment);

        log.info("创建评论: chapterTaskId={}, userId={}, parentId={}", chapterTaskId, userId, request.getParentId());
        return toCommentResponse(comment, Map.of());
    }

    /**
     * 获取章节评论列表（分页，树形结构）
     */
    public PageResult<CommentResponse> getCommentsByChapterPage(Long chapterTaskId, Long userId, int page, int size) {
        var task = collabPort.findChapterTaskById(chapterTaskId);
        if (task.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "章节不存在: " + chapterTaskId);
        }
        var member = collabPort.findMemberByProjectAndUser(task.get().getProjectId(), userId);
        if (member == null) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权访问该项目");
        }

        // 分页查询根评论
        var rootPage = collabPort.findCommentsByChapterTaskIdPaged(chapterTaskId, page, size);

        // 批量加载回复和用户，避免 N+1
        Set<Long> userIds = new HashSet<>();
        Map<Long, List<CollabComment>> repliesByRoot = new HashMap<>();
        for (CollabComment root : rootPage.getRecords()) {
            userIds.add(root.getUserId());
            List<CollabComment> replies = collabPort.findRepliesByParentId(root.getId());
            repliesByRoot.put(root.getId(), replies);
            for (CollabComment reply : replies) {
                userIds.add(reply.getUserId());
            }
        }

        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (Long uid : userIds) {
                userPort.findById(uid).ifPresent(u -> userMap.put(uid, u));
            }
        }

        List<CommentResponse> responses = rootPage.getRecords().stream()
                .map(root -> {
                    CommentResponse resp = toCommentResponse(root, userMap);
                    List<CommentResponse> replyResponses = repliesByRoot.getOrDefault(root.getId(), List.of()).stream()
                            .map(reply -> toCommentResponse(reply, userMap))
                            .collect(Collectors.toList());
                    resp.setReplies(replyResponses);
                    return resp;
                })
                .collect(Collectors.toList());

        PageResult<CommentResponse> resultPage = new PageResult<>(responses, rootPage.getTotal(), (long) page, (long) size);
        return resultPage;
    }

    /**
     * 标记评论已解决
     */
    public void resolveComment(Long commentId, Long userId) {
        CollabComment comment = collabPort.findCommentById(commentId).orElse(null);
        if (comment == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "评论不存在");
        }
        var task = collabPort.findChapterTaskById(comment.getChapterTaskId());
        if (task.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "章节不存在");
        }
        var member = collabPort.findMemberByProjectAndUser(task.get().getProjectId(), userId);
        if (member == null) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权访问该项目");
        }
        comment.setResolved(true);
        collabPort.updateComment(comment);
    }

    /**
     * 删除评论
     */
    public void deleteComment(Long commentId, Long userId) {
        CollabComment comment = collabPort.findCommentById(commentId).orElse(null);
        if (comment == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "评论不存在");
        }
        var task = collabPort.findChapterTaskById(comment.getChapterTaskId());
        if (task.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "章节不存在");
        }
        var member = collabPort.findMemberByProjectAndUser(task.get().getProjectId(), userId);
        if (member == null) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权访问该项目");
        }
        // 仅创建者可以删除
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权删除他人评论");
        }
        collabPort.deleteComment(commentId);
    }

    private CommentResponse toCommentResponse(CollabComment comment, Map<Long, User> userMap) {
        CommentResponse resp = new CommentResponse();
        resp.setId(comment.getId());
        resp.setUserId(comment.getUserId());
        resp.setSourceText(comment.getSourceText());
        resp.setTargetText(comment.getTargetText());
        resp.setContent(comment.getContent());
        resp.setResolved(comment.getResolved());
        resp.setCreateTime(comment.getCreateTime());

        User user = userMap.get(comment.getUserId());
        if (user != null) {
            resp.setUsername(user.getUsername());
            resp.setAvatar(user.getAvatar());
        }
        resp.setReplies(new ArrayList<>());
        return resp;
    }
}
