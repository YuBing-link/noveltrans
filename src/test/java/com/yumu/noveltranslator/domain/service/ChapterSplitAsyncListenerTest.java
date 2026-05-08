package com.yumu.noveltranslator.domain.service;
import com.yumu.noveltranslator.domain.service.ChapterSplitAsyncListener;

import com.yumu.noveltranslator.domain.model.CollabChapterTask;
import com.yumu.noveltranslator.domain.model.CollabProject;
import com.yumu.noveltranslator.domain.model.Document;
import com.yumu.noveltranslator.enums.ChapterTaskStatus;
import com.yumu.noveltranslator.enums.CollabProjectStatus;
import com.yumu.noveltranslator.domain.event.ChapterSplitEvent;
import com.yumu.noveltranslator.domain.service.CollabStateMachine;
import com.yumu.noveltranslator.port.out.CollaborationRepositoryPort;
import com.yumu.noveltranslator.port.out.DocumentRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ChapterSplitAsyncListener 单元测试")
class ChapterSplitAsyncListenerTest {

    @Mock
    private CollaborationRepositoryPort collaborationRepository;

    @Mock
    private DocumentRepositoryPort documentRepository;

    @Mock
    private CollabStateMachine collabStateMachine;

    private ChapterSplitAsyncListener listener;

    @BeforeEach
    void setUp() {
        listener = new ChapterSplitAsyncListener(
                collaborationRepository, documentRepository, collabStateMachine);
    }

    private ChapterSplitEvent createTestEvent(Long projectId, Long documentId, List<String> chapters) {
        return new ChapterSplitEvent(
                projectId, 1L, documentId, "test-doc.txt",
                chapters, "en", "zh");
    }

    @Nested
    @DisplayName("批量插入测试")
    class BatchInsertTests {

        @Test
        @DisplayName("单批次插入所有章节（少于50章）")
        void insertSingleBatch() {
            List<String> chapters = List.of("Chapter 1", "Chapter 2", "Chapter 3");
            ChapterSplitEvent event = createTestEvent(1L, 10L, chapters);

            listener.insertChapterBatch(event, chapters, 0);

            verify(collaborationRepository, times(3)).saveChapterTask(any(CollabChapterTask.class));

            ArgumentCaptor<CollabChapterTask> captor = ArgumentCaptor.forClass(CollabChapterTask.class);
            verify(collaborationRepository, times(3)).saveChapterTask(captor.capture());

            List<CollabChapterTask> inserted = captor.getAllValues();
            assertEquals(3, inserted.size());
            assertEquals("Chapter 1", inserted.get(0).getSourceText());
            assertEquals(1, inserted.get(0).getChapterNumber());
            assertEquals(2, inserted.get(1).getChapterNumber());
            assertEquals(3, inserted.get(2).getChapterNumber());
            assertEquals(ChapterTaskStatus.UNASSIGNED.getValue(), inserted.get(0).getStatus());
        }

        @Test
        @DisplayName("带偏移量的批次插入（第二批次）")
        void insertBatchWithOffset() {
            List<String> chapters = List.of("Chapter 51", "Chapter 52");
            ChapterSplitEvent event = createTestEvent(1L, 10L, List.of());

            // startIndex = 50 means chapter numbers start from 51
            listener.insertChapterBatch(event, chapters, 50);

            ArgumentCaptor<CollabChapterTask> captor = ArgumentCaptor.forClass(CollabChapterTask.class);
            verify(collaborationRepository, times(2)).saveChapterTask(captor.capture());

            List<CollabChapterTask> inserted = captor.getAllValues();
            assertEquals(51, inserted.get(0).getChapterNumber());
            assertEquals(52, inserted.get(1).getChapterNumber());
        }

        @Test
        @DisplayName("空批次不执行插入")
        void emptyBatchNoInsert() {
            ChapterSplitEvent event = createTestEvent(1L, 10L, List.of());

            listener.insertChapterBatch(event, List.of(), 0);

            verify(collaborationRepository, never()).saveChapterTask(any());
        }
    }

    @Nested
    @DisplayName("项目激活测试")
    class ProjectActivationTests {

        @Test
        @DisplayName("正常激活：项目状态转为ACTIVE，文档状态转为COMPLETED")
        void activateProjectNormally() {
            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setStatus(CollabProjectStatus.DRAFT.getValue());
            when(collaborationRepository.findProjectById(1L)).thenReturn(Optional.of(project));

            Document doc = new Document();
            doc.setId(10L);
            doc.setStatus("pending");
            when(documentRepository.findById(10L)).thenReturn(Optional.of(doc));

            doAnswer(invocation -> {
                CollabProject p = invocation.getArgument(0);
                p.setStatus(CollabProjectStatus.ACTIVE.getValue());
                return null;
            }).when(collabStateMachine).transitionProject(project, CollabProjectStatus.ACTIVE);

            listener.activateProject(1L, 10L);

            verify(collabStateMachine).transitionProject(project, CollabProjectStatus.ACTIVE);
            verify(collaborationRepository).updateProject(project);
            assertEquals(CollabProjectStatus.ACTIVE.getValue(), project.getStatus());
            assertEquals(0, project.getProgress());
            verify(documentRepository).update(doc);
        }

        @Test
        @DisplayName("项目不存在时不抛异常")
        void activateProjectNotFound() {
            when(collaborationRepository.findProjectById(99L)).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> listener.activateProject(99L, 10L));

            verify(collaborationRepository).findProjectById(99L);
            verifyNoInteractions(collabStateMachine);
        }

        @Test
        @DisplayName("文档不存在时仍然激活项目")
        void activateProjectWithMissingDocument() {
            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setStatus(CollabProjectStatus.DRAFT.getValue());
            when(collaborationRepository.findProjectById(1L)).thenReturn(Optional.of(project));
            when(documentRepository.findById(10L)).thenReturn(Optional.empty());

            doAnswer(invocation -> {
                CollabProject p = invocation.getArgument(0);
                p.setStatus(CollabProjectStatus.ACTIVE.getValue());
                return null;
            }).when(collabStateMachine).transitionProject(project, CollabProjectStatus.ACTIVE);

            assertDoesNotThrow(() -> listener.activateProject(1L, 10L));

            verify(collabStateMachine).transitionProject(project, CollabProjectStatus.ACTIVE);
            verify(collaborationRepository).updateProject(project);
            verify(documentRepository, never()).update(any());
        }
    }

    @Nested
    @DisplayName("部分批次处理测试")
    class PartialBatchTests {

        @Test
        @DisplayName("恰好50章分为一批")
        void exactBatchSize() {
            List<String> chapters = List.of(
                    "C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10",
                    "C11", "C12", "C13", "C14", "C15", "C16", "C17", "C18", "C19", "C20",
                    "C21", "C22", "C23", "C24", "C25", "C26", "C27", "C28", "C29", "C30",
                    "C31", "C32", "C33", "C34", "C35", "C36", "C37", "C38", "C39", "C40",
                    "C41", "C42", "C43", "C44", "C45", "C46", "C47", "C48", "C49", "C50");

            ChapterSplitEvent event = createTestEvent(1L, 10L, chapters);

            listener.insertChapterBatch(event, chapters, 0);

            verify(collaborationRepository, times(50)).saveChapterTask(any(CollabChapterTask.class));
        }

        @Test
        @DisplayName("51章分为两批（50 + 1）")
        void twoBatchesWithRemainder() {
            List<String> chapters = List.of(
                    "C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10",
                    "C11", "C12", "C13", "C14", "C15", "C16", "C17", "C18", "C19", "C20",
                    "C21", "C22", "C23", "C24", "C25", "C26", "C27", "C28", "C29", "C30",
                    "C31", "C32", "C33", "C34", "C35", "C36", "C37", "C38", "C39", "C40",
                    "C41", "C42", "C43", "C44", "C45", "C46", "C47", "C48", "C49", "C50",
                    "C51");

            ChapterSplitEvent event = createTestEvent(1L, 10L, chapters);

            // First batch: 50 chapters
            List<String> batch1 = chapters.subList(0, 50);
            listener.insertChapterBatch(event, batch1, 0);

            // Second batch: 1 chapter
            List<String> batch2 = chapters.subList(50, 51);
            listener.insertChapterBatch(event, batch2, 50);

            verify(collaborationRepository, times(51)).saveChapterTask(any(CollabChapterTask.class));

            // Verify the last chapter has number 51
            ArgumentCaptor<CollabChapterTask> captor = ArgumentCaptor.forClass(CollabChapterTask.class);
            verify(collaborationRepository, atLeast(1)).saveChapterTask(captor.capture());
            List<CollabChapterTask> allInserted = captor.getAllValues();
            assertTrue(allInserted.stream().anyMatch(c -> c.getChapterNumber() == 51));
        }
    }
}
