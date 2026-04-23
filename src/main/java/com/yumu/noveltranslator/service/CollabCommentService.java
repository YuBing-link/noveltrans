package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.CommentResponse;
import com.yumu.noveltranslator.dto.CreateCommentRequest;
import com.yumu.noveltranslator.entity.CollabChapterTask;
import com.yumu.noveltranslator.entity.CollabComment;
import com.yumu.noveltranslator.entity.CollabProjectMember;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.mapper.CollabChapterTaskMapper;
import com.yumu.noveltranslator.mapper.CollabCommentMapper;
import com.yumu.noveltranslator.mapper.CollabProjectMemberMapper;
import com.yumu.noveltranslator.mapper.UserMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
            throw new IllegalArgumentException("章节不存在: " + chapterTaskId);
        }
        CollabProjectMember member = projectMemberMapper.selectByProjectAndUser(task.getProjectId(), userId);
        if (member == null) {
            throw new SecurityException("无权访问该项目");
        }

        // 如果是回复，验证父评论是否存在
        if (request.getParentId() != null) {
            CollabComment parent = collabCommentMapper.selectById(request.getParentId());
            if (parent == null || !parent.getChapterTaskId().equals(chapterTaskId)) {
                throw new IllegalArgumentException("父评论不存在");
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
        return toCommentResponse(comment);
    }

    /**
     * 获取章节评论列表（树形结构）
     */
    public List<CommentResponse> getCommentsByChapter(Long chapterTaskId, Long userId) {
        CollabChapterTask task = chapterTaskMapper.selectById(chapterTaskId);
        if (task == null) {
            throw new IllegalArgumentException("章节不存在: " + chapterTaskId);
        }
        CollabProjectMember member = projectMemberMapper.selectByProjectAndUser(task.getProjectId(), userId);
        if (member == null) {
            throw new SecurityException("无权访问该项目");
        }
        List<CollabComment> rootComments = collabCommentMapper.selectByChapterTaskId(chapterTaskId);

        // 预加载所有回复
        List<CollabComment> allReplies = rootComments.stream()
                .flatMap(c -> collabCommentMapper.selectRepliesByParentId(c.getId()).stream())
                .toList();

        Map<Long, List<CollabComment>> repliesMap = allReplies.stream()
                .collect(Collectors.groupingBy(CollabComment::getParentId));

        return rootComments.stream()
                .map(c -> buildCommentTree(c, repliesMap))
                .collect(Collectors.toList());
    }

    /**
     * 标记评论已解决
     */
    public void resolveComment(Long commentId, Long userId) {
        CollabComment comment = getById(commentId);
        if (comment == null) {
            throw new IllegalArgumentException("评论不存在");
        }
        CollabChapterTask task = chapterTaskMapper.selectById(comment.getChapterTaskId());
        if (task == null) {
            throw new IllegalArgumentException("章节不存在");
        }
        CollabProjectMember member = projectMemberMapper.selectByProjectAndUser(task.getProjectId(), userId);
        if (member == null) {
            throw new SecurityException("无权访问该项目");
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
            throw new IllegalArgumentException("评论不存在");
        }
        CollabChapterTask task = chapterTaskMapper.selectById(comment.getChapterTaskId());
        if (task == null) {
            throw new IllegalArgumentException("章节不存在");
        }
        CollabProjectMember member = projectMemberMapper.selectByProjectAndUser(task.getProjectId(), userId);
        if (member == null) {
            throw new SecurityException("无权访问该项目");
        }
        // 仅创建者可以删除
        if (!comment.getUserId().equals(userId)) {
            throw new SecurityException("无权删除他人评论");
        }
        removeById(commentId);
    }

    private CommentResponse buildCommentTree(CollabComment comment, Map<Long, List<CollabComment>> repliesMap) {
        CommentResponse resp = toCommentResponse(comment);
        List<CollabComment> replies = repliesMap.getOrDefault(comment.getId(), List.of());
        resp.setReplies(replies.stream()
                .map(r -> toCommentResponse(r))
                .collect(Collectors.toList()));
        return resp;
    }

    private CommentResponse toCommentResponse(CollabComment comment) {
        CommentResponse resp = new CommentResponse();
        resp.setId(comment.getId());
        resp.setUserId(comment.getUserId());
        resp.setSourceText(comment.getSourceText());
        resp.setTargetText(comment.getTargetText());
        resp.setContent(comment.getContent());
        resp.setResolved(comment.getResolved());
        resp.setCreateTime(comment.getCreateTime());

        User user = userMapper.selectById(comment.getUserId());
        if (user != null) {
            resp.setUsername(user.getUsername());
            resp.setAvatar(user.getAvatar());
        }
        resp.setReplies(new ArrayList<>());
        return resp;
    }
}
