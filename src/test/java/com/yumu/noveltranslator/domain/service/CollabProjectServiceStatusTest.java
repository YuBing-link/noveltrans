package com.yumu.noveltranslator.domain.service;
import com.yumu.noveltranslator.domain.service.CollabProjectService;

import com.yumu.noveltranslator.dto.collab.CreateCollabProjectRequest;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabChapterTask;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabProject;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabProjectMember;
import com.yumu.noveltranslator.adapter.out.persistence.entity.Document;
import com.yumu.noveltranslator.enums.CollabProjectStatus;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.CollabChapterTaskMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.CollabCommentMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.CollabInviteCodeMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.CollabProjectMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.CollabProjectMemberMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.DocumentMapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.UserMapper;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CollabProjectServiceStatusTest {

    private CollabProjectMapper collabProjectMapper;
    private CollabProjectMemberMapper collabProjectMemberMapper;
    private CollabChapterTaskMapper collabChapterTaskMapper;
    private CollabCommentMapper collabCommentMapper;
    private CollabInviteCodeMapper collabInviteCodeMapper;
    private DocumentMapper documentMapper;
    private UserMapper userMapper;
    private CollabStateMachine collabStateMachine;
    private MultiAgentTranslationService multiAgentTranslationService;

    private CollabProjectService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        collabProjectMapper = mock(CollabProjectMapper.class);
        collabProjectMemberMapper = mock(CollabProjectMemberMapper.class);
        collabChapterTaskMapper = mock(CollabChapterTaskMapper.class);
        collabCommentMapper = mock(CollabCommentMapper.class);
        collabInviteCodeMapper = mock(CollabInviteCodeMapper.class);
        documentMapper = mock(DocumentMapper.class);
        userMapper = mock(UserMapper.class);
        collabStateMachine = mock(CollabStateMachine.class);
        multiAgentTranslationService = mock(MultiAgentTranslationService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        service = new CollabProjectService(
                collabProjectMapper, collabProjectMemberMapper, collabChapterTaskMapper,
                collabCommentMapper, collabInviteCodeMapper, documentMapper, userMapper,
                collabStateMachine, multiAgentTranslationService, eventPublisher);
        // ServiceImpl has its own baseMapper field that needs to be set
        ReflectionTestUtils.setField(service, "baseMapper", collabProjectMapper);
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

            // Capture the project being inserted by save() -> baseMapper.insert()
            List<CollabProject> capturedProjects = new ArrayList<>();
            doAnswer(invocation -> {
                capturedProjects.add(invocation.getArgument(0));
                // Set ID so the service can continue
                CollabProject p = invocation.getArgument(0);
                p.setId(1L);
                return 1;
            }).when(collabProjectMapper).insert(any(CollabProject.class));

            // when
            service.createProject(request, 1L);

            // then
            assertFalse(capturedProjects.isEmpty(), "Project should have been inserted");
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
            when(documentMapper.selectById(100L)).thenReturn(doc);

            // Capture the project being inserted
            List<CollabProject> capturedProjects = new ArrayList<>();
            doAnswer(invocation -> {
                capturedProjects.add(invocation.getArgument(0));
                CollabProject p = invocation.getArgument(0);
                p.setId(1L);
                return 1;
            }).when(collabProjectMapper).insert(any(CollabProject.class));

            doAnswer(invocation -> 1).when(collabChapterTaskMapper).insert(any(CollabChapterTask.class));

            // when
            CollabProjectService.TeamProjectCreateResult result = service.createProjectFromDocument(
                    1L, 100L, "test-novel.txt", testFile.toString(), "txt", "en", "zh");

            // then
            assertFalse(capturedProjects.isEmpty(), "Project should have been inserted");
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
            when(documentMapper.selectById(200L)).thenReturn(doc);

            List<CollabProject> capturedProjects = new ArrayList<>();
            doAnswer(invocation -> {
                capturedProjects.add(invocation.getArgument(0));
                CollabProject p = invocation.getArgument(0);
                p.setId(1L);
                return 1;
            }).when(collabProjectMapper).insert(any(CollabProject.class));

            doAnswer(invocation -> 1).when(collabChapterTaskMapper).insert(any(CollabChapterTask.class));

            // when
            service.createProjectFromDocument(
                    1L, 200L, "plain-text.txt", testFile.toString(), "txt", null, "zh");

            // then
            assertFalse(capturedProjects.isEmpty(), "Project should have been inserted");
            assertEquals(CollabProjectStatus.DRAFT.getValue(), capturedProjects.get(0).getStatus(),
                    "createProjectFromDocument should set initial status to DRAFT regardless of chapter detection");
        }
    }
}
