package com.yumu.noveltranslator.domain.service;
import com.yumu.noveltranslator.application.service.CollabProjectApplicationService;

import com.yumu.noveltranslator.port.in.CollabPort;
import com.yumu.noveltranslator.port.dto.collab.CreateCollabProjectRequest;
import com.yumu.noveltranslator.domain.model.CollabChapterTask;
import com.yumu.noveltranslator.domain.model.CollabProject;
import com.yumu.noveltranslator.domain.model.CollabProjectMember;
import com.yumu.noveltranslator.domain.model.Document;
import com.yumu.noveltranslator.enums.CollabProjectStatus;
import com.yumu.noveltranslator.port.out.CollaborationRepositoryPort;
import com.yumu.noveltranslator.port.out.DocumentRepositoryPort;
import com.yumu.noveltranslator.port.out.UserRepositoryPort;
import com.yumu.noveltranslator.domain.service.MultiAgentTranslationService;
import com.yumu.noveltranslator.domain.service.CollabStateMachine;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CollabProjectServiceStatusTest {

    private CollaborationRepositoryPort collabPort;
    private DocumentRepositoryPort documentPort;
    private UserRepositoryPort userPort;
    private CollabStateMachine collabStateMachine;
    private MultiAgentTranslationService multiAgentTranslationService;

    private CollabProjectApplicationService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        collabPort = mock(CollaborationRepositoryPort.class);
        documentPort = mock(DocumentRepositoryPort.class);
        userPort = mock(UserRepositoryPort.class);
        collabStateMachine = mock(CollabStateMachine.class);
        multiAgentTranslationService = mock(MultiAgentTranslationService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        service = new CollabProjectApplicationService(
                collabPort, documentPort, userPort,
                collabStateMachine, multiAgentTranslationService, eventPublisher);
    }

    @Nested
    @DisplayName("createProject initial status")
    class CreateProjectStatusTests {

        @Test
        @DisplayName("createProject creates project with DRAFT status")
        void createProject_shouldSetDraftStatus() {
            // given
            CreateCollabProjectRequest request = new CreateCollabProjectRequest();
            request.setName("Test Project");
            request.setDescription("Test description");
            request.setSourceLang("en");
            request.setTargetLang("zh");

            // Capture the project being saved by saveProject()
            List<CollabProject> capturedProjects = new ArrayList<>();
            doAnswer(invocation -> {
                CollabProject p = invocation.getArgument(0);
                capturedProjects.add(p);
                // Set ID so the service can continue
                p.setId(1L);
                return null;
            }).when(collabPort).saveProject(any(CollabProject.class));

            // when
            service.createProject(request, 1L);

            // then
            assertFalse(capturedProjects.isEmpty(), "Project should have been saved");
            assertEquals(CollabProjectStatus.DRAFT.getValue(), capturedProjects.get(0).getStatus(),
                    "createProject should set initial status to DRAFT");
        }
    }

    @Nested
    @DisplayName("createProjectFromDocument initial status")
    class CreateProjectFromDocumentStatusTests {

        @Test
        @DisplayName("createProjectFromDocument creates project with DRAFT status (not ACTIVE)")
        void createProjectFromDocument_shouldSetDraftStatus() throws Exception {
            // given - write a temp file with chapter markers
            Path testFile = tempDir.resolve("test-novel.txt");
            String content = """
                    第1章 开始
                    Some chapter one content here.
                    第2章 继续
                    Some chapter two content here.
                    """;
            Files.writeString(testFile, content);

            // mock document lookup
            Document doc = new Document();
            doc.setId(100L);
            doc.setName("test-novel.txt");
            doc.setStatus("UPLOADED");
            when(documentPort.findById(100L)).thenReturn(Optional.of(doc));

            // Capture the project being saved
            List<CollabProject> capturedProjects = new ArrayList<>();
            doAnswer(invocation -> {
                CollabProject p = invocation.getArgument(0);
                capturedProjects.add(p);
                p.setId(1L);
                return null;
            }).when(collabPort).saveProject(any(CollabProject.class));

            doNothing().when(collabPort).saveChapterTask(any(CollabChapterTask.class));

            // when
            CollabPort.TeamProjectCreateResult result = service.createProjectFromDocument(
                    1L, 100L, "test-novel.txt", testFile.toString(), "txt", "en", "zh");

            // then
            assertFalse(capturedProjects.isEmpty(), "Project should have been saved");
            assertEquals(CollabProjectStatus.DRAFT.getValue(), capturedProjects.get(0).getStatus(),
                    "createProjectFromDocument should set initial status to DRAFT, not ACTIVE");
            assertEquals(2, result.chapterCount(), "Should split into 2 chapters");
        }

        @Test
        @DisplayName("createProjectFromDocument with no chapter titles still creates DRAFT")
        void createProjectFromDocument_noChapterTitles_shouldSetDraftStatus() throws Exception {
            // given - plain text without chapter markers
            Path testFile = tempDir.resolve("plain-text.txt");
            String content = """
                    Paragraph one with some text.
                    Paragraph two with more text.
                    Paragraph three ending the story.
                    """;
            Files.writeString(testFile, content);

            Document doc = new Document();
            doc.setId(200L);
            doc.setName("plain-text.txt");
            doc.setStatus("UPLOADED");
            when(documentPort.findById(200L)).thenReturn(Optional.of(doc));

            List<CollabProject> capturedProjects = new ArrayList<>();
            doAnswer(invocation -> {
                CollabProject p = invocation.getArgument(0);
                capturedProjects.add(p);
                p.setId(1L);
                return null;
            }).when(collabPort).saveProject(any(CollabProject.class));

            doNothing().when(collabPort).saveChapterTask(any(CollabChapterTask.class));

            // when
            service.createProjectFromDocument(
                    1L, 200L, "plain-text.txt", testFile.toString(), "txt", null, "zh");

            // then
            assertFalse(capturedProjects.isEmpty(), "Project should have been saved");
            assertEquals(CollabProjectStatus.DRAFT.getValue(), capturedProjects.get(0).getStatus(),
                    "createProjectFromDocument should set initial status to DRAFT regardless of chapter detection");
        }
    }
}
