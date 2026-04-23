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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CollabCommentServiceTest {

    @Mock
    private CollabCommentMapper collabCommentMapper;
    @Mock
    private CollabChapterTaskMapper chapterTaskMapper;
    @Mock
    private CollabProjectMemberMapper projectMemberMapper;
    @Mock
    private UserMapper userMapper;

    private CollabCommentService service;

    @BeforeEach
    void setUp() {
        service = spy(new CollabCommentService(collabCommentMapper, chapterTaskMapper,
                projectMemberMapper, userMapper));
        // Mock the save() method from ServiceImpl
        doAnswer(invocation -> {
            CollabComment c = invocation.getArgument(0);
            if (c.getId() == null) c.setId(100L);
            return true;
        }).when(service).save(any(CollabComment.class));
        doAnswer(invocation -> true).when(service).updateById(any(CollabComment.class));
        doAnswer(invocation -> true).when(service).removeById(anyLong());
        // Mock getById() from ServiceImpl (uses collabCommentMapper.selectById)
        doAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return collabCommentMapper.selectById(id);
        }).when(service).getById(anyLong());
    }

    @Nested
    @DisplayName("创建评论")
    class CreateCommentTests {

        @Test
        void 创建顶级评论成功() {
            CollabChapterTask task = buildTask(1L, 10L);
            when(chapterTaskMapper.selectById(1L)).thenReturn(task);
            when(projectMemberMapper.selectByProjectAndUser(10L, 1L)).thenReturn(buildMember(10L, 1L));

            CreateCommentRequest req = new CreateCommentRequest();
            req.setSourceText("source");
            req.setTargetText("target");
            req.setContent("good translation");
            req.setParentId(null);

            CommentResponse result = service.createComment(1L, 1L, req);

            assertNotNull(result);
            assertEquals("good translation", result.getContent());
            assertTrue(result.getReplies().isEmpty());
        }

        @Test
        void 创建回复评论成功() {
            CollabChapterTask task = buildTask(1L, 10L);
            CollabComment parent = new CollabComment();
            parent.setId(50L);
            parent.setChapterTaskId(1L);
            when(chapterTaskMapper.selectById(1L)).thenReturn(task);
            when(projectMemberMapper.selectByProjectAndUser(10L, 1L)).thenReturn(buildMember(10L, 1L));
            when(collabCommentMapper.selectById(50L)).thenReturn(parent);

            CreateCommentRequest req = new CreateCommentRequest();
            req.setSourceText("source");
            req.setTargetText("target");
            req.setContent("reply content");
            req.setParentId(50L);

            CommentResponse result = service.createComment(1L, 1L, req);

            assertNotNull(result);
            assertEquals("reply content", result.getContent());
        }

        @Test
        void 章节不存在抛出异常() {
            when(chapterTaskMapper.selectById(999L)).thenReturn(null);

            CreateCommentRequest req = new CreateCommentRequest();
            assertThrows(IllegalArgumentException.class, () ->
                service.createComment(999L, 1L, req));
        }

        @Test
        void 无项目权限抛出异常() {
            CollabChapterTask task = buildTask(1L, 10L);
            when(chapterTaskMapper.selectById(1L)).thenReturn(task);
            when(projectMemberMapper.selectByProjectAndUser(10L, 1L)).thenReturn(null);

            CreateCommentRequest req = new CreateCommentRequest();
            assertThrows(SecurityException.class, () ->
                service.createComment(1L, 1L, req));
        }

        @Test
        void 父评论不存在抛出异常() {
            CollabChapterTask task = buildTask(1L, 10L);
            when(chapterTaskMapper.selectById(1L)).thenReturn(task);
            when(projectMemberMapper.selectByProjectAndUser(10L, 1L)).thenReturn(buildMember(10L, 1L));
            when(collabCommentMapper.selectById(999L)).thenReturn(null);

            CreateCommentRequest req = new CreateCommentRequest();
            req.setParentId(999L);
            assertThrows(IllegalArgumentException.class, () ->
                service.createComment(1L, 1L, req));
        }

        @Test
        void 父评论不属于该章节抛出异常() {
            CollabChapterTask task = buildTask(1L, 10L);
            CollabComment parent = new CollabComment();
            parent.setId(50L);
            parent.setChapterTaskId(2L); // different chapter
            when(chapterTaskMapper.selectById(1L)).thenReturn(task);
            when(projectMemberMapper.selectByProjectAndUser(10L, 1L)).thenReturn(buildMember(10L, 1L));
            when(collabCommentMapper.selectById(50L)).thenReturn(parent);

            CreateCommentRequest req = new CreateCommentRequest();
            req.setParentId(50L);
            assertThrows(IllegalArgumentException.class, () ->
                service.createComment(1L, 1L, req));
        }
    }

    @Nested
    @DisplayName("获取章节评论")
    class GetCommentsTests {

        @Test
        void 返回评论树() {
            CollabChapterTask task = buildTask(1L, 10L);
            CollabComment root1 = buildComment(1L, 1L, "root1", null);
            CollabComment root2 = buildComment(2L, 1L, "root2", null);
            CollabComment reply1 = buildComment(3L, 2L, "reply1", 1L);

            when(chapterTaskMapper.selectById(1L)).thenReturn(task);
            when(projectMemberMapper.selectByProjectAndUser(10L, 1L)).thenReturn(buildMember(10L, 1L));
            when(collabCommentMapper.selectByChapterTaskId(1L)).thenReturn(List.of(root1, root2));
            when(collabCommentMapper.selectRepliesByParentId(1L)).thenReturn(List.of(reply1));
            when(collabCommentMapper.selectRepliesByParentId(2L)).thenReturn(List.of());
            when(userMapper.selectById(anyLong())).thenReturn(null);

            List<CommentResponse> result = service.getCommentsByChapter(1L, 1L);

            assertEquals(2, result.size());
            assertEquals(1, result.get(0).getReplies().size());
            assertEquals("reply1", result.get(0).getReplies().get(0).getContent());
        }

        @Test
        void 章节不存在抛出异常() {
            when(chapterTaskMapper.selectById(999L)).thenReturn(null);
            assertThrows(IllegalArgumentException.class, () ->
                service.getCommentsByChapter(999L, 1L));
        }

        @Test
        void 无权限抛出异常() {
            CollabChapterTask task = buildTask(1L, 10L);
            when(chapterTaskMapper.selectById(1L)).thenReturn(task);
            when(projectMemberMapper.selectByProjectAndUser(10L, 1L)).thenReturn(null);
            assertThrows(SecurityException.class, () ->
                service.getCommentsByChapter(1L, 1L));
        }
    }

    @Nested
    @DisplayName("解决评论")
    class ResolveCommentTests {

        @Test
        void 标记已解决() {
            CollabComment comment = buildComment(1L, 1L, "content", null);
            CollabChapterTask task = buildTask(1L, 10L);
            doReturn(comment).when(service).getById(1L);
            when(chapterTaskMapper.selectById(1L)).thenReturn(task);
            when(projectMemberMapper.selectByProjectAndUser(10L, 1L)).thenReturn(buildMember(10L, 1L));

            service.resolveComment(1L, 1L);

            assertTrue(comment.getResolved());
        }

        @Test
        void 评论不存在抛出异常() {
            doReturn(null).when(service).getById(1L);
            assertThrows(IllegalArgumentException.class, () ->
                service.resolveComment(1L, 1L));
        }

        @Test
        void 章节不存在抛出异常() {
            CollabComment comment = buildComment(1L, 1L, "content", null);
            doReturn(comment).when(service).getById(1L);
            when(chapterTaskMapper.selectById(1L)).thenReturn(null);
            assertThrows(IllegalArgumentException.class, () ->
                service.resolveComment(1L, 1L));
        }
    }

    @Nested
    @DisplayName("删除评论")
    class DeleteCommentTests {

        @Test
        void 创建者可以删除() {
            CollabComment comment = buildComment(1L, 1L, "content", null);
            CollabChapterTask task = buildTask(1L, 10L);
            doReturn(comment).when(service).getById(1L);
            when(chapterTaskMapper.selectById(1L)).thenReturn(task);
            when(projectMemberMapper.selectByProjectAndUser(10L, 1L)).thenReturn(buildMember(10L, 1L));

            service.deleteComment(1L, 1L);

            verify(service).removeById(1L);
        }

        @Test
        void 不能删除他人评论() {
            CollabComment comment = buildComment(1L, 2L, "content", null);
            CollabChapterTask task = buildTask(1L, 10L);
            doReturn(comment).when(service).getById(1L);
            when(chapterTaskMapper.selectById(1L)).thenReturn(task);
            when(projectMemberMapper.selectByProjectAndUser(10L, 1L)).thenReturn(buildMember(10L, 1L));

            assertThrows(SecurityException.class, () ->
                service.deleteComment(1L, 1L));
        }

        @Test
        void 评论不存在抛出异常() {
            doReturn(null).when(service).getById(1L);
            assertThrows(IllegalArgumentException.class, () ->
                service.deleteComment(1L, 1L));
        }
    }

    private CollabChapterTask buildTask(Long id, Long projectId) {
        CollabChapterTask task = new CollabChapterTask();
        task.setId(id);
        task.setProjectId(projectId);
        return task;
    }

    private CollabProjectMember buildMember(Long projectId, Long userId) {
        CollabProjectMember member = new CollabProjectMember();
        member.setId(1L);
        member.setProjectId(projectId);
        member.setUserId(userId);
        member.setRole("member");
        return member;
    }

    private CollabComment buildComment(Long id, Long userId, String content, Long parentId) {
        CollabComment comment = new CollabComment();
        comment.setId(id);
        comment.setChapterTaskId(1L);
        comment.setUserId(userId);
        comment.setSourceText("source");
        comment.setTargetText("target");
        comment.setContent(content);
        comment.setParentId(parentId);
        comment.setResolved(false);
        comment.setCreateTime(LocalDateTime.now());
        return comment;
    }
}
