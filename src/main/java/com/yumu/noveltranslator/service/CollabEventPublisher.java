package com.yumu.noveltranslator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yumu.noveltranslator.util.SseEmitterUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 协作事件发布器
 *
 * 将协作相关的事件写入 Redis Stream，供 SSE 重放机制消费。
 * 所有方法都应在事务提交后调用，确保数据库写入先于事件发布。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CollabEventPublisher {

    private final SseEmitterUtil sseEmitterUtil;
    private final ObjectMapper objectMapper;

    /**
     * 发布章节状态变更事件
     *
     * @param projectId 项目ID
     * @param chapterId 章节ID
     * @param userId    操作用户ID
     * @param action    操作类型: "updated", "submitted", "assigned"
     */
    public void publishChapterUpdate(Long projectId, Long chapterId, String userId, String action) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chapterId", chapterId);
        payload.put("userId", userId);
        payload.put("action", action);

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("序列化章节事件失败: projectId={}, chapterId={}", projectId, chapterId, e);
            return;
        }

        sseEmitterUtil.publishCollabEvent(String.valueOf(projectId), "chapter." + action, json);
    }

    /**
     * 发布章节评论事件
     *
     * @param projectId     项目ID
     * @param chapterTaskId 章节任务ID
     * @param userId        评论用户ID
     * @param content       评论内容
     */
    public void publishCommentAdded(Long projectId, Long chapterTaskId, Long userId, String content) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chapterTaskId", chapterTaskId);
        payload.put("userId", userId);
        payload.put("content", content);

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("序列化评论事件失败: projectId={}, chapterTaskId={}", projectId, chapterTaskId, e);
            return;
        }

        sseEmitterUtil.publishCollabEvent(String.valueOf(projectId), "comment.added", json);
    }
}
