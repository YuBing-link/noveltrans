package com.yumu.noveltranslator.application.service;
import com.yumu.noveltranslator.exception.BusinessException;
import com.yumu.noveltranslator.application.service.ChapterTaskApplicationService;
import com.yumu.noveltranslator.domain.service.CollabEventPublisher;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yumu.noveltranslator.port.dto.collab.ChapterTaskResponse;
import com.yumu.noveltranslator.port.dto.common.PageResponse;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabChapterTask;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabProject;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabProjectMember;
import com.yumu.noveltranslator.adapter.out.persistence.entity.User;
import com.yumu.noveltranslator.enums.ChapterTaskStatus;
import com.yumu.noveltranslator.enums.ProjectMemberRole;
import com.yumu.noveltranslator.port.out.CollaborationRepositoryPort;
import com.yumu.noveltranslator.port.out.UserRepositoryPort;
import com.yumu.noveltranslator.domain.service.CollabStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChapterTaskServiceTest {

    @Mock
    private CollaborationRepositoryPort collabPort;

    @Mock
    private UserRepositoryPort userPort;

    @Mock
    private CollabStateMachine collabStateMachine;

    @Mock
    private CollabEventPublisher collabEventPublisher;

    private ChapterTaskApplicationService chapterTaskService;

    @BeforeEach
    void setUp() {
        chapterTaskService = new ChapterTaskApplicationService(
                collabPort, userPort, collabStateMachine, collabEventPublisher);
    }

    @Nested
    @DisplayName("创建章节")
    class CreateChapterTests {

        @Test
        void 创建成功返回正确的字段() {
            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setName("测试项目");
            when(collabPort.findProjectById(1L)).thenReturn(Optional.of(project));

            ChapterTaskResponse response = chapterTaskService.createChapter(1L, 1, "第一章", "原文", 10L);

            assertNotNull(response);
            assertEquals(1, response.getChapterNumber());
            assertEquals("第一章", response.getTitle());
            assertEquals("原文", response.getSourceText());
            verify(collabPort).saveChapterTask(any());
        }

        @Test
        void 项目不存在抛异常() {
            when(collabPort.findProjectById(999L)).thenReturn(Optional.empty());
            assertThrows(BusinessException.class,
                    () -> chapterTaskService.createChapter(999L, 1, "标题", "原文", 10L));
        }
    }

    @Nested
    @DisplayName("获取章节详情")
    class GetChapterByIdTests {

        @Test
        void 章节不存在抛异常() {
            when(collabPort.findChapterTaskById(999L)).thenReturn(Optional.empty());
            assertThrows(BusinessException.class,
                    () -> chapterTaskService.getChapterById(999L, 1L));
        }

        @Test
        void 无权访问抛异常() {
            CollabChapterTask task = new CollabChapterTask();
            task.setId(1L);
            task.setProjectId(10L);
            when(collabPort.findChapterTaskById(1L)).thenReturn(Optional.of(task));
            when(collabPort.findMemberByProjectAndUser(10L, 1L)).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> chapterTaskService.getChapterById(1L, 1L));
        }

        @Test
        void 有权限返回详情() {
            CollabChapterTask task = new CollabChapterTask();
            task.setId(1L);
            task.setProjectId(10L);
            task.setChapterNumber(1);
            task.setTitle("第一章");
            when(collabPort.findChapterTaskById(1L)).thenReturn(Optional.of(task));
            when(collabPort.findMemberByProjectAndUser(10L, 1L)).thenReturn(buildMember(10L, 1L));

            ChapterTaskResponse response = chapterTaskService.getChapterById(1L, 1L);

            assertEquals(1L, response.getId());
        }
    }

    @Nested
    @DisplayName("项目章节列表")
    class ListByProjectIdTests {

        @Test
        void 返回分页数据() {
            CollabChapterTask task = new CollabChapterTask();
            task.setId(1L);
            task.setProjectId(10L);
            task.setChapterNumber(1);
            task.setStatus(ChapterTaskStatus.UNASSIGNED.getValue());

            Page<CollabChapterTask> page = new Page<>(1, 10);
            page.setRecords(List.of(task));
            page.setTotal(1);
            when(collabPort.findChapterTasksByProjectIdPaged(10L, 1, 10)).thenReturn(page);

            PageResponse<ChapterTaskResponse> result = chapterTaskService.listByProjectId(10L, 1, 10);

            assertEquals(1, result.getTotal());
            assertEquals(1, result.getList().size());
        }
    }

    @Nested
    @DisplayName("分配章节")
    class AssignChapterTests {

        @Test
        void 非OWNER分配抛异常() {
            CollabChapterTask task = new CollabChapterTask();
            task.setId(1L);
            task.setProjectId(10L);
            when(collabPort.findChapterTaskById(1L)).thenReturn(Optional.of(task));

            CollabProjectMember member = new CollabProjectMember();
            member.setRole(ProjectMemberRole.TRANSLATOR.getValue());
            when(collabPort.findMemberByProjectAndUser(10L, 5L)).thenReturn(member);

            assertThrows(BusinessException.class,
                    () -> chapterTaskService.assignChapter(1L, 2L, 5L));
        }
    }

    @Nested
    @DisplayName("用户待处理章节")
    class ListByAssigneeIdTests {

        @Test
        void 返回分页数据() {
            CollabChapterTask task = new CollabChapterTask();
            task.setId(1L);
            task.setAssigneeId(1L);
            task.setStatus(ChapterTaskStatus.TRANSLATING.getValue());

            Page<CollabChapterTask> page = new Page<>(1, 10);
            page.setRecords(List.of(task));
            page.setTotal(1);
            when(collabPort.findChapterTasksByAssigneeIdPaged(eq(1L), anyList(), eq(1), eq(10))).thenReturn(page);

            PageResponse<ChapterTaskResponse> result = chapterTaskService.listByAssigneeId(1L, 1, 10);

            assertEquals(1, result.getTotal());
        }
    }

    @Nested
    @DisplayName("提交章节")
    class SubmitChapterTests {

        @Test
        void 提交成功更新译文和状态() {
            CollabChapterTask task = new CollabChapterTask();
            task.setId(1L);
            task.setProjectId(1L);
            task.setStatus(ChapterTaskStatus.TRANSLATING.getValue());
            when(collabPort.findChapterTaskById(1L)).thenReturn(Optional.of(task));

            // 模拟状态机更新状态
            doAnswer(inv -> {
                CollabChapterTask t = inv.getArgument(0);
                ChapterTaskStatus target = inv.getArgument(1);
                t.setStatus(target.getValue());
                return null;
            }).when(collabStateMachine).transitionChapter(any(), any(ChapterTaskStatus.class));

            ChapterTaskResponse resp = chapterTaskService.submitChapter(1L, "翻译后的内容");

            assertNotNull(resp);
            assertEquals("翻译后的内容", resp.getTranslatedText());
            assertEquals(ChapterTaskStatus.SUBMITTED.getValue(), resp.getStatus());
            assertEquals(100, resp.getProgress());
            assertEquals(6, resp.getTargetWordCount());
            assertNotNull(resp.getSubmittedTime());
        }

        @Test
        void 章节不存在抛出异常() {
            when(collabPort.findChapterTaskById(999L)).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () ->
                    chapterTaskService.submitChapter(999L, "译文"));
        }
    }

    @Nested
    @DisplayName("审校章节")
    class ReviewChapterTests {

        @Test
        void 审核通过更新状态为已完成() {
            CollabChapterTask task = new CollabChapterTask();
            task.setId(1L);
            task.setProjectId(1L);
            task.setStatus(ChapterTaskStatus.SUBMITTED.getValue());
            when(collabPort.findChapterTaskById(1L)).thenReturn(Optional.of(task));
            when(collabPort.findChapterTasksByProjectId(1L)).thenReturn(List.of(task));
            when(collabPort.findProjectById(1L)).thenReturn(Optional.of(new CollabProject()));

            CollabProjectMember reviewer = new CollabProjectMember();
            reviewer.setRole(ProjectMemberRole.REVIEWER.getValue());
            when(collabPort.findMemberByProjectAndUser(1L, 3L)).thenReturn(reviewer);

            // 模拟状态机更新状态
            doAnswer(inv -> {
                CollabChapterTask t = inv.getArgument(0);
                ChapterTaskStatus target = inv.getArgument(1);
                t.setStatus(target.getValue());
                return null;
            }).when(collabStateMachine).transitionChapter(any(), any(ChapterTaskStatus.class));

            ChapterTaskResponse resp = chapterTaskService.reviewChapter(1L, true, "很好", 3L);

            assertNotNull(resp);
            assertEquals(ChapterTaskStatus.COMPLETED.getValue(), resp.getStatus());
            assertEquals(3L, resp.getReviewerId());
            assertEquals("很好", resp.getReviewComment());
            assertNotNull(resp.getReviewedTime());
            assertNotNull(resp.getCompletedTime());
        }

        @Test
        void 审核驳回更新状态为已拒绝() {
            CollabChapterTask task = new CollabChapterTask();
            task.setId(1L);
            task.setProjectId(1L);
            task.setStatus(ChapterTaskStatus.SUBMITTED.getValue());
            when(collabPort.findChapterTaskById(1L)).thenReturn(Optional.of(task));

            CollabProjectMember reviewer = new CollabProjectMember();
            reviewer.setRole(ProjectMemberRole.REVIEWER.getValue());
            when(collabPort.findMemberByProjectAndUser(1L, 3L)).thenReturn(reviewer);

            doAnswer(inv -> {
                CollabChapterTask t = inv.getArgument(0);
                ChapterTaskStatus target = inv.getArgument(1);
                t.setStatus(target.getValue());
                return null;
            }).when(collabStateMachine).transitionChapter(any(), any(ChapterTaskStatus.class));

            ChapterTaskResponse resp = chapterTaskService.reviewChapter(1L, false, "需要修改", 3L);

            assertNotNull(resp);
            assertEquals(ChapterTaskStatus.REJECTED.getValue(), resp.getStatus());
            assertEquals(0, resp.getProgress());
            assertNull(resp.getSubmittedTime());
        }

        @Test
        void 章节不存在抛出异常() {
            when(collabPort.findChapterTaskById(999L)).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () ->
                    chapterTaskService.reviewChapter(999L, true, "ok", 3L));
        }
    }

    private CollabProjectMember buildMember(Long projectId, Long userId) {
        CollabProjectMember m = new CollabProjectMember();
        m.setProjectId(projectId);
        m.setUserId(userId);
        m.setRole(ProjectMemberRole.OWNER.getValue());
        return m;
    }
}
