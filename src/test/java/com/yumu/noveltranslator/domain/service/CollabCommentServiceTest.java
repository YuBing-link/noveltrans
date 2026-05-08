package com.yumu.noveltranslator.domain.service;
import com.yumu.noveltranslator.exception.BusinessException;
import com.yumu.noveltranslator.domain.service.CollabCommentService;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yumu.noveltranslator.port.dto.collab.CommentResponse;
import com.yumu.noveltranslator.port.dto.collab.CreateCommentRequest;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabChapterTask;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabComment;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabProjectMember;
import com.yumu.noveltranslator.adapter.out.persistence.entity.User;
import com.yumu.noveltranslator.port.out.CollaborationRepositoryPort;
import com.yumu.noveltranslator.port.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CollabCommentServiceTest {

    @Mock
    private CollaborationRepositoryPort collabPort;
    @Mock
    private UserRepositoryPort userPort;

    private CollabCommentService service;

    @BeforeEach
    void setUp() {
        service = new CollabCommentService(collabPort, userPort);
    }

    @Nested
    @DisplayName("创建评论")
    class CreateCommentTests {

        @Test
        void 创建顶级评论成功() {
            CollabChapterTask task = buildTask(1L, 10L);
            when(collabPort.findChapterTaskById(1L)).thenReturn(Optional.of(task));
            when(collabPort.findMemberByProjectAndUser(10L, 1L)).thenReturn(buildMember(10L, 1L));

            CreateCommentRequest req = new CreateCommentRequest();
            req.setSourceText("source");
            req.setTargetText("target");
            req.setContent("评论内容");

            CommentResponse resp = service.createComment(1L, 1L, req);

            assertNotNull(resp);
            assertEquals("source", resp.getSourceText());
            verify(collabPort).saveComment(any(CollabComment.class));
        }

        @Test
        void 章节不存在抛异常() {
            when(collabPort.findChapterTaskById(999L)).thenReturn(Optional.empty());
            CreateCommentRequest req = new CreateCommentRequest();
            assertThrows(BusinessException.class, () -> service.createComment(999L, 1L, req));
        }

        @Test
        void 无权访问抛异常() {
            CollabChapterTask task = buildTask(1L, 10L);
            when(collabPort.findChapterTaskById(1L)).thenReturn(Optional.of(task));
            when(collabPort.findMemberByProjectAndUser(10L, 1L)).thenReturn(null);
            CreateCommentRequest req = new CreateCommentRequest();
            assertThrows(BusinessException.class, () -> service.createComment(1L, 1L, req));
        }
    }

    @Nested
    @DisplayName("获取评论列表")
    class GetCommentsTests {

        @Test
        void 无权访问抛异常() {
            when(collabPort.findChapterTaskById(1L)).thenReturn(Optional.of(buildTask(1L, 10L)));
            when(collabPort.findMemberByProjectAndUser(10L, 1L)).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> service.getCommentsByChapterPage(1L, 1L, 1, 10));
        }

        @Test
        void 有权限返回分页数据() {
            when(collabPort.findChapterTaskById(1L)).thenReturn(Optional.of(buildTask(1L, 10L)));
            when(collabPort.findMemberByProjectAndUser(10L, 1L)).thenReturn(buildMember(10L, 1L));

            Page<CollabComment> commentPage = new Page<>(1, 10);
            CollabComment root = new CollabComment();
            root.setId(1L);
            root.setChapterTaskId(1L);
            root.setUserId(10L);
            root.setContent("根评论");
            commentPage.setRecords(List.of(root));
            commentPage.setTotal(1);
            when(collabPort.findCommentsByChapterTaskIdPage(any(Page.class), eq(1L))).thenReturn(commentPage);
            when(collabPort.findRepliesByParentId(1L)).thenReturn(List.of());

            IPage<CommentResponse> result = service.getCommentsByChapterPage(1L, 1L, 1, 10);

            assertEquals(1, result.getTotal());
        }
    }

    @Nested
    @DisplayName("解决评论")
    class ResolveCommentTests {

        @Test
        void 评论不存在抛异常() {
            when(collabPort.findCommentById(999L)).thenReturn(Optional.empty());
            assertThrows(BusinessException.class, () -> service.resolveComment(999L, 1L));
        }

        @Test
        void 有权解决成功() {
            CollabComment comment = new CollabComment();
            comment.setId(1L);
            comment.setChapterTaskId(5L);
            comment.setResolved(false);
            when(collabPort.findCommentById(1L)).thenReturn(Optional.of(comment));
            when(collabPort.findChapterTaskById(5L)).thenReturn(Optional.of(buildTask(5L, 10L)));
            when(collabPort.findMemberByProjectAndUser(10L, 1L)).thenReturn(buildMember(10L, 1L));

            service.resolveComment(1L, 1L);

            assertTrue(comment.getResolved());
            verify(collabPort).updateComment(comment);
        }
    }

    @Nested
    @DisplayName("删除评论")
    class DeleteCommentTests {

        @Test
        void 评论不存在抛异常() {
            when(collabPort.findCommentById(999L)).thenReturn(Optional.empty());
            assertThrows(BusinessException.class, () -> service.deleteComment(999L, 1L));
        }

        @Test
        void 非创建者删除抛异常() {
            CollabComment comment = new CollabComment();
            comment.setId(1L);
            comment.setChapterTaskId(5L);
            comment.setUserId(99L); // 不是当前用户
            when(collabPort.findCommentById(1L)).thenReturn(Optional.of(comment));
            when(collabPort.findChapterTaskById(5L)).thenReturn(Optional.of(buildTask(5L, 10L)));
            when(collabPort.findMemberByProjectAndUser(10L, 1L)).thenReturn(buildMember(10L, 1L));

            assertThrows(BusinessException.class, () -> service.deleteComment(1L, 1L));
        }

        @Test
        void 创建者删除成功() {
            CollabComment comment = new CollabComment();
            comment.setId(1L);
            comment.setChapterTaskId(5L);
            comment.setUserId(1L);
            when(collabPort.findCommentById(1L)).thenReturn(Optional.of(comment));
            when(collabPort.findChapterTaskById(5L)).thenReturn(Optional.of(buildTask(5L, 10L)));
            when(collabPort.findMemberByProjectAndUser(10L, 1L)).thenReturn(buildMember(10L, 1L));

            service.deleteComment(1L, 1L);

            verify(collabPort).deleteComment(1L);
        }
    }

    @Nested
    @DisplayName("回复评论")
    class ReplyCommentTests {

        @Test
        void 父评论不存在抛异常() {
            CollabChapterTask task = buildTask(1L, 10L);
            when(collabPort.findChapterTaskById(1L)).thenReturn(Optional.of(task));
            when(collabPort.findMemberByProjectAndUser(10L, 1L)).thenReturn(buildMember(10L, 1L));
            when(collabPort.findCommentById(999L)).thenReturn(Optional.empty());

            CreateCommentRequest req = new CreateCommentRequest();
            req.setParentId(999L);
            req.setContent("回复内容");

            assertThrows(BusinessException.class, () -> service.createComment(1L, 1L, req));
        }

        @Test
        void 父评论不属于当前章节抛异常() {
            CollabChapterTask task = buildTask(1L, 10L);
            when(collabPort.findChapterTaskById(1L)).thenReturn(Optional.of(task));
            when(collabPort.findMemberByProjectAndUser(10L, 1L)).thenReturn(buildMember(10L, 1L));

            CollabComment parent = new CollabComment();
            parent.setId(999L);
            parent.setChapterTaskId(88L); // 不属于 chapterTaskId=1
            when(collabPort.findCommentById(999L)).thenReturn(Optional.of(parent));

            CreateCommentRequest req = new CreateCommentRequest();
            req.setParentId(999L);
            req.setContent("回复内容");

            assertThrows(BusinessException.class, () -> service.createComment(1L, 1L, req));
        }
    }

    private CollabChapterTask buildTask(Long id, Long projectId) {
        CollabChapterTask t = new CollabChapterTask();
        t.setId(id);
        t.setProjectId(projectId);
        t.setChapterNumber(1);
        t.setStatus("UNASSIGNED");
        return t;
    }

    private CollabProjectMember buildMember(Long projectId, Long userId) {
        CollabProjectMember m = new CollabProjectMember();
        m.setProjectId(projectId);
        m.setUserId(userId);
        m.setRole("OWNER");
        return m;
    }
}
