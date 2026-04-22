package com.yumu.noveltranslator.service.state;

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

    @Nested
    @DisplayName("项目状态转移")
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
    @DisplayName("章节状态转移")
    class ChapterTransitionTests {

        @Test
        void UNASSIGNED到TRANSLATING合法() {
            assertDoesNotThrow(() -> sm.validateChapterTransition(ChapterTaskStatus.UNASSIGNED, ChapterTaskStatus.TRANSLATING));
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
}
