package com.yumu.noveltranslator.domain.service.state;
import com.yumu.noveltranslator.exception.BusinessException;
import com.yumu.noveltranslator.domain.service.CollabStateMachine;

import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabChapterTask;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabProject;
import com.yumu.noveltranslator.enums.ChapterTaskStatus;
import com.yumu.noveltranslator.enums.CollabProjectStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CollabStateMachine 测试")
class CollabStateMachineTest {

    private CollabStateMachine sm;

    @BeforeEach
    void setUp() {
        sm = new CollabStateMachine();
    }

    // ========== 辅助方法：创建测试实体 ==========

    private CollabProject newProject(CollabProjectStatus status) {
        CollabProject p = new CollabProject();
        p.setId(1L);
        p.setName("test");
        p.setStatus(status.getValue());
        return p;
    }

    private CollabChapterTask newChapter(ChapterTaskStatus status) {
        CollabChapterTask c = new CollabChapterTask();
        c.setId(1L);
        c.setProjectId(1L);
        c.setStatus(status.getValue());
        return c;
    }

    // ========== 原有验证方法测试 ==========

    @Nested
    @DisplayName("项目状态转移（validation-only）")
    class ProjectTransitionTests {

        @Test
        void DRAFT到ACTIVE合法() {
            assertDoesNotThrow(() -> sm.validateProjectTransition(CollabProjectStatus.DRAFT, CollabProjectStatus.ACTIVE));
        }

        @Test
        void ACTIVE到COMPLETED合法() {
            assertDoesNotThrow(() -> sm.validateProjectTransition(CollabProjectStatus.ACTIVE, CollabProjectStatus.COMPLETED));
        }

        @Test
        void COMPLETED到ARCHIVED合法() {
            assertDoesNotThrow(() -> sm.validateProjectTransition(CollabProjectStatus.COMPLETED, CollabProjectStatus.ARCHIVED));
        }

        @Test
        void ARCHIVED到ACTIVE合法() {
            assertDoesNotThrow(() -> sm.validateProjectTransition(CollabProjectStatus.ARCHIVED, CollabProjectStatus.ACTIVE));
        }

        @Test
        void DRAFT到COMPLETED非法() {
            assertThrows(IllegalStateException.class, () -> sm.validateProjectTransition(CollabProjectStatus.DRAFT, CollabProjectStatus.COMPLETED));
        }

        @Test
        void 字符串方法DRAFT到ACTIVE合法() {
            assertDoesNotThrow(() -> sm.validateProjectTransition("DRAFT", "ACTIVE"));
        }

        @Test
        void 字符串方法非法抛出() {
            assertThrows(IllegalStateException.class, () -> sm.validateProjectTransition("DRAFT", "COMPLETED"));
        }
    }

    @Nested
    @DisplayName("章节状态转移（validation-only）")
    class ChapterTransitionTests {

        @Test
        void UNASSIGNED到TRANSLATING合法() {
            assertDoesNotThrow(() -> sm.validateChapterTransition(ChapterTaskStatus.UNASSIGNED, ChapterTaskStatus.TRANSLATING));
        }

        @Test
        void UNASSIGNED到SUBMITTED合法() {
            assertDoesNotThrow(() -> sm.validateChapterTransition(ChapterTaskStatus.UNASSIGNED, ChapterTaskStatus.SUBMITTED));
        }

        @Test
        void TRANSLATING到SUBMITTED合法() {
            assertDoesNotThrow(() -> sm.validateChapterTransition(ChapterTaskStatus.TRANSLATING, ChapterTaskStatus.SUBMITTED));
        }

        @Test
        void SUBMITTED到REVIEWING合法() {
            assertDoesNotThrow(() -> sm.validateChapterTransition(ChapterTaskStatus.SUBMITTED, ChapterTaskStatus.REVIEWING));
        }

        @Test
        void REVIEWING到APPROVED合法() {
            assertDoesNotThrow(() -> sm.validateChapterTransition(ChapterTaskStatus.REVIEWING, ChapterTaskStatus.APPROVED));
        }

        @Test
        void REVIEWING到REJECTED合法() {
            assertDoesNotThrow(() -> sm.validateChapterTransition(ChapterTaskStatus.REVIEWING, ChapterTaskStatus.REJECTED));
        }

        @Test
        void REJECTED到TRANSLATING合法() {
            assertDoesNotThrow(() -> sm.validateChapterTransition(ChapterTaskStatus.REJECTED, ChapterTaskStatus.TRANSLATING));
        }

        @Test
        void TRANSLATING到APPROVED非法() {
            assertThrows(IllegalStateException.class, () -> sm.validateChapterTransition(ChapterTaskStatus.TRANSLATING, ChapterTaskStatus.APPROVED));
        }

        @Test
        void 字符串方法UNASSIGNED到TRANSLATING合法() {
            assertDoesNotThrow(() -> sm.validateChapterTransition("UNASSIGNED", "TRANSLATING"));
        }

        @Test
        void 字符串方法非法抛出() {
            assertThrows(IllegalStateException.class, () -> sm.validateChapterTransition("UNASSIGNED", "APPROVED"));
        }
    }

    // ========== transitionProject 驱动方法测试 ==========

    @Nested
    @DisplayName("transitionProject 驱动方法")
    class TransitionProjectTests {

        @Test
        void DRAFT转ACTIVE成功并更新实体() {
            CollabProject project = newProject(CollabProjectStatus.DRAFT);
            sm.transitionProject(project, CollabProjectStatus.ACTIVE);
            assertEquals(CollabProjectStatus.ACTIVE.getValue(), project.getStatus());
        }

        @Test
        void ACTIVE转COMPLETED成功并更新实体() {
            CollabProject project = newProject(CollabProjectStatus.ACTIVE);
            sm.transitionProject(project, CollabProjectStatus.COMPLETED);
            assertEquals(CollabProjectStatus.COMPLETED.getValue(), project.getStatus());
        }

        @Test
        void COMPLETED转ARCHIVED成功并更新实体() {
            CollabProject project = newProject(CollabProjectStatus.COMPLETED);
            sm.transitionProject(project, CollabProjectStatus.ARCHIVED);
            assertEquals(CollabProjectStatus.ARCHIVED.getValue(), project.getStatus());
        }

        @Test
        void ARCHIVED转ACTIVE成功并更新实体() {
            CollabProject project = newProject(CollabProjectStatus.ARCHIVED);
            sm.transitionProject(project, CollabProjectStatus.ACTIVE);
            assertEquals(CollabProjectStatus.ACTIVE.getValue(), project.getStatus());
        }

        @Test
        void ACTIVE转DRAFT成功并更新实体() {
            CollabProject project = newProject(CollabProjectStatus.ACTIVE);
            sm.transitionProject(project, CollabProjectStatus.DRAFT);
            assertEquals(CollabProjectStatus.DRAFT.getValue(), project.getStatus());
        }

        @Test
        void DRAFT转COMPLETED非法抛出() {
            CollabProject project = newProject(CollabProjectStatus.DRAFT);
            assertThrows(IllegalStateException.class,
                    () -> sm.transitionProject(project, CollabProjectStatus.COMPLETED));
            // 确保实体状态未被修改
            assertEquals(CollabProjectStatus.DRAFT.getValue(), project.getStatus());
        }

        @Test
        void COMPLETED转DRAFT非法抛出() {
            CollabProject project = newProject(CollabProjectStatus.COMPLETED);
            assertThrows(IllegalStateException.class,
                    () -> sm.transitionProject(project, CollabProjectStatus.DRAFT));
            assertEquals(CollabProjectStatus.COMPLETED.getValue(), project.getStatus());
        }

        @Test
        void 字符串重载DRAFT转ACTIVE() {
            CollabProject project = newProject(CollabProjectStatus.DRAFT);
            sm.transitionProject(project, "ACTIVE");
            assertEquals("ACTIVE", project.getStatus());
        }

        @Test
        void 字符串重载非法抛出() {
            CollabProject project = newProject(CollabProjectStatus.DRAFT);
            assertThrows(IllegalStateException.class,
                    () -> sm.transitionProject(project, "COMPLETED"));
        }
    }

    // ========== transitionChapter 驱动方法测试 ==========

    @Nested
    @DisplayName("transitionChapter 驱动方法")
    class TransitionChapterTests {

        @Test
        void UNASSIGNED转TRANSLATING成功并更新实体() {
            CollabChapterTask chapter = newChapter(ChapterTaskStatus.UNASSIGNED);
            sm.transitionChapter(chapter, ChapterTaskStatus.TRANSLATING);
            assertEquals(ChapterTaskStatus.TRANSLATING.getValue(), chapter.getStatus());
        }

        @Test
        void TRANSLATING转SUBMITTED成功并更新实体() {
            CollabChapterTask chapter = newChapter(ChapterTaskStatus.TRANSLATING);
            sm.transitionChapter(chapter, ChapterTaskStatus.SUBMITTED);
            assertEquals(ChapterTaskStatus.SUBMITTED.getValue(), chapter.getStatus());
        }

        @Test
        void SUBMITTED转REVIEWING成功并更新实体() {
            CollabChapterTask chapter = newChapter(ChapterTaskStatus.SUBMITTED);
            sm.transitionChapter(chapter, ChapterTaskStatus.REVIEWING);
            assertEquals(ChapterTaskStatus.REVIEWING.getValue(), chapter.getStatus());
        }

        @Test
        void REVIEWING转APPROVED成功并更新实体() {
            CollabChapterTask chapter = newChapter(ChapterTaskStatus.REVIEWING);
            sm.transitionChapter(chapter, ChapterTaskStatus.APPROVED);
            assertEquals(ChapterTaskStatus.APPROVED.getValue(), chapter.getStatus());
        }

        @Test
        void REVIEWING转REJECTED成功并更新实体() {
            CollabChapterTask chapter = newChapter(ChapterTaskStatus.REVIEWING);
            sm.transitionChapter(chapter, ChapterTaskStatus.REJECTED);
            assertEquals(ChapterTaskStatus.REJECTED.getValue(), chapter.getStatus());
        }

        @Test
        void REJECTED转TRANSLATING成功并更新实体() {
            CollabChapterTask chapter = newChapter(ChapterTaskStatus.REJECTED);
            sm.transitionChapter(chapter, ChapterTaskStatus.TRANSLATING);
            assertEquals(ChapterTaskStatus.TRANSLATING.getValue(), chapter.getStatus());
        }

        @Test
        void APPROVED转COMPLETED成功并更新实体() {
            CollabChapterTask chapter = newChapter(ChapterTaskStatus.APPROVED);
            sm.transitionChapter(chapter, ChapterTaskStatus.COMPLETED);
            assertEquals(ChapterTaskStatus.COMPLETED.getValue(), chapter.getStatus());
        }

        @Test
        void UNASSIGNED转SUBMITTED成功并更新实体() {
            CollabChapterTask chapter = newChapter(ChapterTaskStatus.UNASSIGNED);
            sm.transitionChapter(chapter, ChapterTaskStatus.SUBMITTED);
            assertEquals(ChapterTaskStatus.SUBMITTED.getValue(), chapter.getStatus());
        }

        @Test
        void TRANSLATING转UNASSIGNED成功并更新实体() {
            CollabChapterTask chapter = newChapter(ChapterTaskStatus.TRANSLATING);
            sm.transitionChapter(chapter, ChapterTaskStatus.UNASSIGNED);
            assertEquals(ChapterTaskStatus.UNASSIGNED.getValue(), chapter.getStatus());
        }

        @Test
        void TRANSLATING转APPROVED非法抛出() {
            CollabChapterTask chapter = newChapter(ChapterTaskStatus.TRANSLATING);
            assertThrows(IllegalStateException.class,
                    () -> sm.transitionChapter(chapter, ChapterTaskStatus.APPROVED));
            // 确保实体状态未被修改
            assertEquals(ChapterTaskStatus.TRANSLATING.getValue(), chapter.getStatus());
        }

        @Test
        void COMPLETED无法转出() {
            CollabChapterTask chapter = newChapter(ChapterTaskStatus.COMPLETED);
            assertThrows(IllegalStateException.class,
                    () -> sm.transitionChapter(chapter, ChapterTaskStatus.SUBMITTED));
            assertEquals(ChapterTaskStatus.COMPLETED.getValue(), chapter.getStatus());
        }

        @Test
        void 完整审核流程SUBMITTED到COMPLETED() {
            CollabChapterTask chapter = newChapter(ChapterTaskStatus.SUBMITTED);
            sm.transitionChapter(chapter, ChapterTaskStatus.REVIEWING);
            assertEquals(ChapterTaskStatus.REVIEWING.getValue(), chapter.getStatus());

            sm.transitionChapter(chapter, ChapterTaskStatus.APPROVED);
            assertEquals(ChapterTaskStatus.APPROVED.getValue(), chapter.getStatus());

            sm.transitionChapter(chapter, ChapterTaskStatus.COMPLETED);
            assertEquals(ChapterTaskStatus.COMPLETED.getValue(), chapter.getStatus());
        }

        @Test
        void 完整驳回重译流程SUBMITTED到TRANSLATING() {
            CollabChapterTask chapter = newChapter(ChapterTaskStatus.SUBMITTED);
            sm.transitionChapter(chapter, ChapterTaskStatus.REVIEWING);
            sm.transitionChapter(chapter, ChapterTaskStatus.REJECTED);
            assertEquals(ChapterTaskStatus.REJECTED.getValue(), chapter.getStatus());

            sm.transitionChapter(chapter, ChapterTaskStatus.TRANSLATING);
            assertEquals(ChapterTaskStatus.TRANSLATING.getValue(), chapter.getStatus());
        }

        @Test
        void 字符串重载UNASSIGNED转TRANSLATING() {
            CollabChapterTask chapter = newChapter(ChapterTaskStatus.UNASSIGNED);
            sm.transitionChapter(chapter, "TRANSLATING");
            assertEquals("TRANSLATING", chapter.getStatus());
        }

        @Test
        void 字符串重载非法抛出() {
            CollabChapterTask chapter = newChapter(ChapterTaskStatus.TRANSLATING);
            assertThrows(IllegalStateException.class,
                    () -> sm.transitionChapter(chapter, "COMPLETED"));
        }
    }
}
