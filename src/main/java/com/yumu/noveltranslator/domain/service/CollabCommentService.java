package com.yumu.noveltranslator.domain.service;

import com.yumu.noveltranslator.dto.collab.CommentResponse;
import com.yumu.noveltranslator.dto.collab.CreateCommentRequest;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabChapterTask;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabComment;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabProjectMember;
import com.yumu.noveltranslator.adapter.out.persistence.entity.User;
import com.yumu.noveltranslator.enums.ErrorCodeEnum;
import com.yumu.noveltranslator.exception.BusinessException;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.CollabChapterTaskMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.CollabCommentMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.CollabProjectMemberMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.UserMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 协作评论服务
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CollabCommentService extends ServiceImpl<CollabCommentMapper, CollabComment> {

    private final CollabCommentMapper collabCommentMapper;
    private final CollabChapterTaskMapper chapterTaskMapper;
    private final CollabProjectMemberMapper projectMemberMapper;
    private final UserMapper userMapper;

    /**
     * 创建评论
     */
    public CommentResponse createComment(Long chapterTaskId, Long userId, CreateCommentRequest request) {
        CollabChapterTask task = chapterTaskMapper.selectById(chapterTaskId);
        if (task == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "章节不存在: " + chapterTaskId);
        }
        CollabProjectMember member = projectMemberMapper.selectByProjectAndUser(task.getProjectId(), userId);
        if (member == null) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权访问该项目");
        }

        // 如果是回复，验证父评论是否存在
        if (request.getParentId() != null) {
            CollabComment parent = collabCommentMapper.selectById(request.getParentId());
            if (parent == null || !parent.getChapterTaskId().equals(chapterTaskId)) {
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
        save(comment);

        log.info("创建评论: chapterTaskId={}, userId={}, parentId={}", chapterTaskId, userId, request.getParentId());
        return toCommentResponse(comment, Map.of());
    }

    /**
     * 获取章节评论列表（分页，树形结构）
     */
    public IPage<CommentResponse> getCommentsByChapterPage(Long chapterTaskId, Long userId, int page, int size) {
        CollabChapterTask task = chapterTaskMapper.selectById(chapterTaskId);
        if (task == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "章节不存在: " + chapterTaskId);
        }
        CollabProjectMember member = projectMemberMapper.selectByProjectAndUser(task.getProjectId(), userId);
        if (member == null) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权访问该项目");
        }

        // 分页查询根评论
        Page<CollabComment> commentPage = new Page<>(page, size);
        var rootPage = collabCommentMapper.selectByChapterTaskIdPage(commentPage, chapterTaskId);

        // 批量加载回复和用户，避免 N+1
        Set<Long> userIds = new HashSet<>();
        Map<Long, List<CollabComment>> repliesByRoot = new HashMap<>();
        for (CollabComment root : rootPage.getRecords()) {
            userIds.add(root.getUserId());
            List<CollabComment> replies = collabCommentMapper.selectRepliesByParentId(root.getId());
            repliesByRoot.put(root.getId(), replies);
            for (CollabComment reply : replies) {
                userIds.add(reply.getUserId());
            }
        }

        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            for (User user : users) {
                userMap.put(user.getId(), user);
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

        IPage<CommentResponse> resultPage = new Page<>(rootPage.getCurrent(), rootPage.getSize(), rootPage.getTotal());
        resultPage.setRecords(responses);
        return resultPage;
    }

    /**
     * 标记评论已解决
     */
    public void resolveComment(Long commentId, Long userId) {
        CollabComment comment = getById(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "评论不存在");
        }
        CollabChapterTask task = chapterTaskMapper.selectById(comment.getChapterTaskId());
        if (task == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "章节不存在");
        }
        CollabProjectMember member = projectMemberMapper.selectByProjectAndUser(task.getProjectId(), userId);
        if (member == null) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权访问该项目");
        }
        comment.setResolved(true);
        updateById(comment);
    }

    /**
     * 删除评论
     */
    public void deleteComment(Long commentId, Long userId) {
        CollabComment comment = getById(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "评论不存在");
        }
        CollabChapterTask task = chapterTaskMapper.selectById(comment.getChapterTaskId());
        if (task == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "章节不存在");
        }
        CollabProjectMember member = projectMemberMapper.selectByProjectAndUser(task.getProjectId(), userId);
        if (member == null) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权访问该项目");
        }
        // 仅创建者可以删除
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "无权删除他人评论");
        }
        removeById(commentId);
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
