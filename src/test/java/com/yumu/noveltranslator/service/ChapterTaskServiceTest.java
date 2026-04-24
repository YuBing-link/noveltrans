package com.yumu.noveltranslator.service;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yumu.noveltranslator.dto.ChapterTaskResponse;
import com.yumu.noveltranslator.entity.CollabChapterTask;
import com.yumu.noveltranslator.entity.CollabProject;
import com.yumu.noveltranslator.entity.CollabProjectMember;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.enums.ChapterTaskStatus;
import com.yumu.noveltranslator.enums.ProjectMemberRole;
import com.yumu.noveltranslator.mapper.CollabChapterTaskMapper;
import com.yumu.noveltranslator.mapper.CollabProjectMapper;
import com.yumu.noveltranslator.mapper.CollabProjectMemberMapper;
import com.yumu.noveltranslator.mapper.UserMapper;
import com.yumu.noveltranslator.service.state.CollabStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChapterTaskServiceTest {

    @Mock
    private CollabChapterTaskMapper chapterTaskMapper;

    @Mock
    private CollabProjectMapper collabProjectMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CollabProjectMemberMapper projectMemberMapper;

    @Mock
    private CollabStateMachine collabStateMachine;

    private ChapterTaskService chapterTaskService;

    @BeforeEach
    void setUp() {
        chapterTaskService = new ChapterTaskService(
                chapterTaskMapper, collabProjectMapper, userMapper, projectMemberMapper, collabStateMachine);
        // ServiceImpl 需要 baseMapper 才能调用 getById/save/updateById 等方法
        ReflectionTestUtils.setField(chapterTaskService, "baseMapper", chapterTaskMapper);
    }

    @Nested
    @DisplayName("创建章节")
    class CreateChapterTests {

        @Test
        void 创建成功返回正确的字段() {
            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setName("测试项目");
            when(collabProjectMapper.selectById(1L)).thenReturn(project);
            when(chapterTaskMapper.insert(any())).thenReturn(1);

            ChapterTaskResponse resp = chapterTaskService.createChapter(
                    1L, 1, "第一章", "Hello world", 1L);

            assertNotNull(resp);
            assertEquals(1, resp.getChapterNumber());
            assertEquals("第一章", resp.getTitle());
            assertEquals("Hello world", resp.getSourceText());
            assertEquals(ChapterTaskStatus.UNASSIGNED.getValue(), resp.getStatus());
            assertEquals(0, resp.getProgress());
            assertEquals(11, resp.getSourceWordCount());
        }

        @Test
        void 项目不存在抛出异常() {
            when(collabProjectMapper.selectById(999L)).thenReturn(null);

            assertThrows(IllegalArgumentException.class, () ->
                    chapterTaskService.createChapter(999L, 1, "标题", "内容", 1L));
        }

        @Test
        void sourceText为null时字数为null() {
            CollabProject project = new CollabProject();
            project.setId(1L);
            when(collabProjectMapper.selectById(1L)).thenReturn(project);
            when(chapterTaskMapper.insert(any())).thenReturn(1);

            ChapterTaskResponse resp = chapterTaskService.createChapter(
                    1L, 1, "标题", null, 1L);

            assertNotNull(resp);
            assertNull(resp.getSourceWordCount());
        }
    }

    @Nested
    @DisplayName("获取项目章节列表")
    class ListByProjectIdTests {

        @Test
        void 返回章节列表() {
            CollabChapterTask task = new CollabChapterTask();
            task.setId(1L);
            task.setProjectId(1L);
            task.setChapterNumber(1);
            task.setTitle("第一章");
            task.setSourceText("Hello");
            task.setStatus(ChapterTaskStatus.UNASSIGNED.getValue());
            task.setProgress(0);
            task.setSourceWordCount(5);
            when(chapterTaskMapper.selectByProjectId(1L)).thenReturn(List.of(task));

            List<ChapterTaskResponse> result = chapterTaskService.listByProjectId(1L);

            assertEquals(1, result.size());
            assertEquals("第一章", result.get(0).getTitle());
        }

        @Test
        void 空项目返回空列表() {
            when(chapterTaskMapper.selectByProjectId(1L)).thenReturn(List.of());

            List<ChapterTaskResponse> result = chapterTaskService.listByProjectId(1L);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("获取章节详情")
    class GetChapterByIdTests {

        @Test
        void 找到章节返回详情() {
            CollabChapterTask task = new CollabChapterTask();
            task.setId(1L);
            task.setProjectId(1L);
            task.setChapterNumber(1);
            task.setTitle("第一章");
            task.setStatus(ChapterTaskStatus.UNASSIGNED.getValue());
            task.setProgress(0);
            when(chapterTaskMapper.selectById(1L)).thenReturn(task);
            when(projectMemberMapper.selectByProjectAndUser(1L, 1L)).thenReturn(new CollabProjectMember());

            ChapterTaskResponse resp = chapterTaskService.getChapterById(1L, 1L);

            assertNotNull(resp);
            assertEquals(1L, resp.getId());
            assertEquals("第一章", resp.getTitle());
        }

        @Test
        void 章节不存在抛出异常() {
            when(chapterTaskMapper.selectById(999L)).thenReturn(null);

            assertThrows(IllegalArgumentException.class, () ->
                    chapterTaskService.getChapterById(999L, 1L));
        }
    }

    @Nested
    @DisplayName("分配章节")
    class AssignChapterTests {

        @Test
        void 分配成功更新受让人和状态() {
            CollabChapterTask task = new CollabChapterTask();
            task.setId(1L);
            task.setProjectId(1L);
            task.setStatus(ChapterTaskStatus.UNASSIGNED.getValue());
            when(chapterTaskMapper.selectById(1L)).thenReturn(task);
            when(chapterTaskMapper.updateById(any())).thenReturn(1);

            CollabProjectMember owner = new CollabProjectMember();
            owner.setRole(ProjectMemberRole.OWNER.getValue());
            when(projectMemberMapper.selectByProjectAndUser(1L, 1L)).thenReturn(owner);

            ChapterTaskResponse resp = chapterTaskService.assignChapter(1L, 2L, 1L);

            assertNotNull(resp);
            assertEquals(2L, resp.getAssigneeId());
            assertEquals(ChapterTaskStatus.TRANSLATING.getValue(), resp.getStatus());
            assertEquals(0, resp.getProgress());
            assertNotNull(resp.getAssignedTime());
        }

        @Test
        void 章节不存在抛出异常() {
            when(chapterTaskMapper.selectById(999L)).thenReturn(null);

            assertThrows(IllegalArgumentException.class, () ->
                    chapterTaskService.assignChapter(999L, 2L, 1L));
        }

        @Test
        void 无效状态转换抛出异常() {
            CollabChapterTask task = new CollabChapterTask();
            task.setId(1L);
            task.setProjectId(1L);
            task.setStatus(ChapterTaskStatus.COMPLETED.getValue());
            when(chapterTaskMapper.selectById(1L)).thenReturn(task);

            CollabProjectMember owner = new CollabProjectMember();
            owner.setRole(ProjectMemberRole.OWNER.getValue());
            when(projectMemberMapper.selectByProjectAndUser(1L, 1L)).thenReturn(owner);

            doThrow(new IllegalStateException("Invalid transition"))
                    .when(collabStateMachine).validateChapterTransition(
                            ChapterTaskStatus.COMPLETED, ChapterTaskStatus.TRANSLATING);

            assertThrows(IllegalStateException.class, () ->
                    chapterTaskService.assignChapter(1L, 2L, 1L));
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
            when(chapterTaskMapper.selectById(1L)).thenReturn(task);
            when(chapterTaskMapper.updateById(any())).thenReturn(1);

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
            when(chapterTaskMapper.selectById(999L)).thenReturn(null);

            assertThrows(IllegalArgumentException.class, () ->
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
            when(chapterTaskMapper.selectById(1L)).thenReturn(task);
            when(chapterTaskMapper.selectByProjectId(1L)).thenReturn(List.of(task));
            when(chapterTaskMapper.updateById(any())).thenReturn(1);
            when(collabProjectMapper.selectById(1L)).thenReturn(new CollabProject());

            CollabProjectMember reviewer = new CollabProjectMember();
            reviewer.setRole(ProjectMemberRole.REVIEWER.getValue());
            when(projectMemberMapper.selectByProjectAndUser(1L, 3L)).thenReturn(reviewer);

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
            when(chapterTaskMapper.selectById(1L)).thenReturn(task);
            when(chapterTaskMapper.updateById(any())).thenReturn(1);

            CollabProjectMember reviewer = new CollabProjectMember();
            reviewer.setRole(ProjectMemberRole.REVIEWER.getValue());
            when(projectMemberMapper.selectByProjectAndUser(1L, 3L)).thenReturn(reviewer);

            ChapterTaskResponse resp = chapterTaskService.reviewChapter(1L, false, "需要修改", 3L);

            assertNotNull(resp);
            assertEquals(ChapterTaskStatus.REJECTED.getValue(), resp.getStatus());
            assertEquals(0, resp.getProgress());
            assertNull(resp.getSubmittedTime());
        }

        @Test
        void 章节不存在抛出异常() {
            when(chapterTaskMapper.selectById(999L)).thenReturn(null);

            assertThrows(IllegalArgumentException.class, () ->
                    chapterTaskService.reviewChapter(999L, true, "ok", 3L));
        }
    }

    @Nested
    @DisplayName("获取译员待处理章节")
    class ListByAssigneeIdTests {

        @Test
        void 返回已分配章节列表() {
            CollabChapterTask task = new CollabChapterTask();
            task.setId(1L);
            task.setChapterNumber(1);
            task.setTitle("第一章");
            task.setStatus(ChapterTaskStatus.TRANSLATING.getValue());
            task.setProgress(0);
            when(chapterTaskMapper.selectByAssigneeId(2L)).thenReturn(List.of(task));

            List<ChapterTaskResponse> result = chapterTaskService.listByAssigneeId(2L);

            assertEquals(1, result.size());
            assertEquals("第一章", result.get(0).getTitle());
        }

        @Test
        void 没有待处理章节返回空列表() {
            when(chapterTaskMapper.selectByAssigneeId(2L)).thenReturn(List.of());

            List<ChapterTaskResponse> result = chapterTaskService.listByAssigneeId(2L);

            assertTrue(result.isEmpty());
        }
    }
}
