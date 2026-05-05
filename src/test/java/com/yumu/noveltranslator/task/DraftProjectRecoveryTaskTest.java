package com.yumu.noveltranslator.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yumu.noveltranslator.entity.CollabProject;
import com.yumu.noveltranslator.enums.CollabProjectStatus;
import com.yumu.noveltranslator.mapper.CollabChapterTaskMapper;
import com.yumu.noveltranslator.mapper.CollabProjectMapper;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DraftProjectRecoveryTask 单元测试")
class DraftProjectRecoveryTaskTest {

    @Mock
    private CollabProjectMapper collabProjectMapper;

    @Mock
    private CollabChapterTaskMapper collabChapterTaskMapper;

    @Mock
    private CollabStateMachine collabStateMachine;

    private DraftProjectRecoveryTask recoveryTask;

    @BeforeEach
    void setUp() {
        recoveryTask = new DraftProjectRecoveryTask(
                collabProjectMapper, collabChapterTaskMapper, collabStateMachine);
    }

    @Nested
    @DisplayName("DRAFT 项目有章节（应转为 ACTIVE）")
    class DraftWithChaptersTests {

        @Test
        @DisplayName("DRAFT 项目有章节，状态转为 ACTIVE")
        void draftWithChaptersTransitionsToActive() {
            CollabProject project = createDraftProject(1L, LocalDateTime.now().minusMinutes(15));
            when(collabProjectMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(project));
            when(collabChapterTaskMapper.countByProjectId(1L)).thenReturn(10);
            doAnswer(invocation -> {
                CollabProject p = invocation.getArgument(0);
                p.setStatus(CollabProjectStatus.ACTIVE.getValue());
                return null;
            }).when(collabStateMachine).transitionProject(project, CollabProjectStatus.ACTIVE);

            recoveryTask.recoverStaleDraftProjects();

            verify(collabStateMachine).transitionProject(project, CollabProjectStatus.ACTIVE);
            verify(collabProjectMapper).updateById(project);
            assertEquals(CollabProjectStatus.ACTIVE.getValue(), project.getStatus());
        }

        @Test
        @DisplayName("多个 DRAFT 项目有章节，全部转为 ACTIVE")
        void multipleDraftsWithChaptersAllTransition() {
            CollabProject project1 = createDraftProject(1L, LocalDateTime.now().minusMinutes(12));
            CollabProject project2 = createDraftProject(2L, LocalDateTime.now().minusMinutes(20));
            when(collabProjectMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(List.of(project1, project2));
            when(collabChapterTaskMapper.countByProjectId(1L)).thenReturn(50);
            when(collabChapterTaskMapper.countByProjectId(2L)).thenReturn(5);

            doAnswer(invocation -> {
                CollabProject p = invocation.getArgument(0);
                p.setStatus(CollabProjectStatus.ACTIVE.getValue());
                return null;
            }).when(collabStateMachine).transitionProject(any(CollabProject.class), eq(CollabProjectStatus.ACTIVE));

            recoveryTask.recoverStaleDraftProjects();

            verify(collabStateMachine, times(2)).transitionProject(any(CollabProject.class), eq(CollabProjectStatus.ACTIVE));
            verify(collabProjectMapper, times(2)).updateById(any(CollabProject.class));
        }
    }

    @Nested
    @DisplayName("DRAFT 项目无章节（记录为陈旧）")
    class DraftWithoutChaptersTests {

        @Test
        @DisplayName("DRAFT 项目无章节，记录为陈旧但不转换状态")
        void draftWithoutChaptersLoggedAsStale() {
            CollabProject project = createDraftProject(1L, LocalDateTime.now().minusMinutes(15));
            when(collabProjectMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(project));
            when(collabChapterTaskMapper.countByProjectId(1L)).thenReturn(0);

            recoveryTask.recoverStaleDraftProjects();

            verify(collabStateMachine, never()).transitionProject(any(CollabProject.class), any(CollabProjectStatus.class));
            verify(collabStateMachine, never()).transitionProject(any(CollabProject.class), anyString());
            verify(collabProjectMapper, never()).updateById(any());
            // 项目保持 DRAFT 状态
            assertEquals(CollabProjectStatus.DRAFT.getValue(), project.getStatus());
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCaseTests {

        @Test
        @DisplayName("无停滞项目时不执行任何操作")
        void noStaleProjectsDoNothing() {
            when(collabProjectMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

            recoveryTask.recoverStaleDraftProjects();

            verifyNoInteractions(collabChapterTaskMapper);
            verifyNoInteractions(collabStateMachine);
        }

        @Test
        @DisplayName("状态转换异常被捕获，不影响其他项目")
        void transitionExceptionDoesNotAffectOthers() {
            CollabProject project1 = createDraftProject(1L, LocalDateTime.now().minusMinutes(15));
            CollabProject project2 = createDraftProject(2L, LocalDateTime.now().minusMinutes(20));
            when(collabProjectMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(List.of(project1, project2));
            when(collabChapterTaskMapper.countByProjectId(1L)).thenReturn(10);
            when(collabChapterTaskMapper.countByProjectId(2L)).thenReturn(5);

            // project1 转换抛出异常
            doThrow(new IllegalStateException("Invalid transition"))
                    .when(collabStateMachine).transitionProject(project1, CollabProjectStatus.ACTIVE);

            // project2 正常转换
            doAnswer(invocation -> {
                CollabProject p = invocation.getArgument(0);
                p.setStatus(CollabProjectStatus.ACTIVE.getValue());
                return null;
            }).when(collabStateMachine).transitionProject(project2, CollabProjectStatus.ACTIVE);

            // 不应抛出异常
            assertDoesNotThrow(() -> recoveryTask.recoverStaleDraftProjects());

            // project2 仍应被处理
            verify(collabStateMachine).transitionProject(project2, CollabProjectStatus.ACTIVE);
        }

        @Test
        @DisplayName("仅处理超过阈值时间的 DRAFT 项目")
        void onlyProcessProjectsOlderThanThreshold() {
            CollabProject oldProject = createDraftProject(1L, LocalDateTime.now().minusMinutes(15));
            // 查询返回的应该是所有符合条件的旧项目，此处仅一个
            when(collabProjectMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(oldProject));
            when(collabChapterTaskMapper.countByProjectId(1L)).thenReturn(3);

            doAnswer(invocation -> {
                CollabProject p = invocation.getArgument(0);
                p.setStatus(CollabProjectStatus.ACTIVE.getValue());
                return null;
            }).when(collabStateMachine).transitionProject(oldProject, CollabProjectStatus.ACTIVE);

            recoveryTask.recoverStaleDraftProjects();

            verify(collabProjectMapper).selectList(any(QueryWrapper.class));
            verify(collabStateMachine).transitionProject(oldProject, CollabProjectStatus.ACTIVE);
        }
    }

    private CollabProject createDraftProject(Long id, LocalDateTime createTime) {
        CollabProject project = new CollabProject();
        project.setId(id);
        project.setName("Test Project " + id);
        project.setOwnerId(1L);
        project.setStatus(CollabProjectStatus.DRAFT.getValue());
        project.setCreateTime(createTime);
        project.setSourceLang("en");
        project.setTargetLang("zh");
        return project;
    }
}
