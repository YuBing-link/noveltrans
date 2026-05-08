package com.yumu.noveltranslator.domain.service;
import com.yumu.noveltranslator.exception.BusinessException;
import com.yumu.noveltranslator.port.dto.collab.InviteMemberRequest;
import com.yumu.noveltranslator.port.dto.collab.CollabProjectResponse;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabChapterTask;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabInviteCode;
import com.yumu.noveltranslator.adapter.out.persistence.entity.User;
import com.yumu.noveltranslator.port.dto.collab.ProjectMemberResponse;
import com.yumu.noveltranslator.port.dto.collab.CreateCollabProjectRequest;
import com.yumu.noveltranslator.domain.service.CollabProjectService;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabProjectMember;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabProject;
import com.yumu.noveltranslator.port.dto.common.PageResponse;
import com.yumu.noveltranslator.domain.service.MultiAgentTranslationService;

import com.yumu.noveltranslator.config.tenant.TenantContext;
import com.yumu.noveltranslator.enums.ChapterTaskStatus;
import com.yumu.noveltranslator.enums.CollabProjectStatus;
import com.yumu.noveltranslator.enums.ProjectMemberRole;
import com.yumu.noveltranslator.port.out.CollaborationRepositoryPort;
import com.yumu.noveltranslator.port.out.DocumentRepositoryPort;
import com.yumu.noveltranslator.port.out.UserRepositoryPort;
import com.yumu.noveltranslator.domain.service.CollabStateMachine;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CollabProjectService 补充测试")
class CollabProjectServiceExtendedTest {

    @Mock
    private CollaborationRepositoryPort collabPort;
    @Mock
    private DocumentRepositoryPort documentPort;
    @Mock
    private UserRepositoryPort userPort;
    @Mock
    private CollabStateMachine collabStateMachine;
    @Mock
    private MultiAgentTranslationService multiAgentTranslationService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CollabProjectService service;

    @BeforeEach
    void setUp() {
        service = new CollabProjectService(
                collabPort, documentPort, userPort,
                collabStateMachine, multiAgentTranslationService, eventPublisher);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ============ deleteProject 测试 ============

    @Nested
    @DisplayName("deleteProject - 删除项目")
    class DeleteProjectTests {

        @Test
        void 项目不存在抛异常() {
            when(collabPort.findProjectById(1L)).thenReturn(Optional.empty());
            assertThrows(BusinessException.class, () ->
                    service.deleteProject(1L, 1L));
        }

        @Test
        void 非所有者抛异常() {
            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setOwnerId(99L);
            when(collabPort.findProjectById(1L)).thenReturn(Optional.of(project));
            assertThrows(BusinessException.class, () ->
                    service.deleteProject(1L, 1L));
        }

        @Test
        void 所有者正常删除() {
            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setName("Test Project");
            project.setOwnerId(1L);
            when(collabPort.findProjectById(1L)).thenReturn(Optional.of(project));
            when(collabPort.findChapterTasksByProjectId(1L)).thenReturn(List.of());

            service.deleteProject(1L, 1L);

            verify(collabPort).deleteMembersByProjectId(1L);
            verify(collabPort).deleteChapterTasksByProjectId(1L);
            verify(collabPort).deleteProject(1L);
        }

        @Test
        void 删除带有章节和评论的项目() {
            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setName("Test Project");
            project.setOwnerId(1L);
            when(collabPort.findProjectById(1L)).thenReturn(Optional.of(project));

            CollabChapterTask chapter = new CollabChapterTask();
            chapter.setId(1L);
            chapter.setProjectId(1L);
            when(collabPort.findChapterTasksByProjectId(1L)).thenReturn(List.of(chapter));

            service.deleteProject(1L, 1L);

            verify(collabPort).deleteCommentsByChapterTaskId(1L);
            verify(collabPort).deleteChapterTasksByProjectId(1L);
            verify(collabPort).deleteMembersByProjectId(1L);
            verify(collabPort).deleteProject(1L);
        }
    }

    // ============ generateInviteCode 测试 ============

    @Nested
    @DisplayName("generateInviteCode - 生成邀请码")
    class GenerateInviteCodeTests {

        @Test
        void 项目不存在抛异常() {
            when(collabPort.findProjectById(1L)).thenReturn(Optional.empty());
            assertThrows(BusinessException.class, () ->
                    service.generateInviteCode(1L, 1L));
        }

        @Test
        void 生成8位邀请码() {
            CollabProject project = new CollabProject();
            project.setId(1L);
            when(collabPort.findProjectById(1L)).thenReturn(Optional.of(project));
            when(collabPort.findInviteCodeByCode(anyString())).thenReturn(null);

            CollabProjectService.InviteCodeResult result = service.generateInviteCode(1L, 1L);

            assertNotNull(result);
            assertEquals(8, result.code().length());
            assertNotNull(result.expiresAt());
        }

        @Test
        void 邀请码不含易混淆字符() {
            CollabProject project = new CollabProject();
            project.setId(1L);
            when(collabPort.findProjectById(1L)).thenReturn(Optional.of(project));
            when(collabPort.findInviteCodeByCode(anyString())).thenReturn(null);

            CollabProjectService.InviteCodeResult result = service.generateInviteCode(1L, 1L);

            assertFalse(result.code().contains("I"));
            assertFalse(result.code().contains("O"));
            assertFalse(result.code().contains("0"));
            assertFalse(result.code().contains("1"));
        }

        @Test
        void 邀请码重复时重新生成() {
            CollabProject project = new CollabProject();
            project.setId(1L);
            when(collabPort.findProjectById(1L)).thenReturn(Optional.of(project));
            // First call returns a duplicate, second returns null (unique)
            when(collabPort.findInviteCodeByCode(anyString()))
                    .thenReturn(new CollabInviteCode())
                    .thenReturn(null);

            CollabProjectService.InviteCodeResult result = service.generateInviteCode(1L, 1L);
            assertNotNull(result);
        }
    }

    // ============ changeProjectStatus 测试 ============

    @Nested
    @DisplayName("changeProjectStatus - 变更项目状态")
    class ChangeStatusTests {

        @Test
        void 项目不存在抛异常() {
            when(collabPort.findProjectById(1L)).thenReturn(Optional.empty());
            assertThrows(BusinessException.class, () ->
                    service.changeProjectStatus(1L, CollabProjectStatus.ACTIVE, 1L));
        }

        @Test
        void 完成状态设置进度100() {
            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setStatus(CollabProjectStatus.ACTIVE.getValue());
            project.setProgress(50);
            when(collabPort.findProjectById(1L)).thenReturn(Optional.of(project));

            service.changeProjectStatus(1L, CollabProjectStatus.COMPLETED, 1L);

            assertEquals(100, project.getProgress());
        }

        @Test
        void 非完成状态不改变进度() {
            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setStatus(CollabProjectStatus.DRAFT.getValue());
            project.setProgress(0);
            when(collabPort.findProjectById(1L)).thenReturn(Optional.of(project));

            service.changeProjectStatus(1L, CollabProjectStatus.ACTIVE, 1L);

            assertEquals(0, project.getProgress());
        }
    }

    // ============ joinByInviteCode 测试 ============

    @Nested
    @DisplayName("joinByInviteCode - 通过邀请码加入")
    class JoinByInviteCodeTests {

        @Test
        void 邀请码无效抛异常() {
            when(collabPort.findValidInviteCode("INVALID")).thenReturn(null);
            when(collabPort.findInviteCodeByCode("INVALID")).thenReturn(null);

            assertThrows(BusinessException.class, () ->
                    service.joinByInviteCode("INVALID", 1L));
        }

        @Test
        void 邀请码已被使用抛异常() {
            when(collabPort.findValidInviteCode("USED")).thenReturn(null);
            CollabInviteCode code = new CollabInviteCode();
            code.setUsed(1);
            when(collabPort.findInviteCodeByCode("USED")).thenReturn(code);

            assertThrows(BusinessException.class, () ->
                    service.joinByInviteCode("USED", 1L));
        }

        @Test
        void 邀请码已过期抛异常() {
            when(collabPort.findValidInviteCode("EXPIRED")).thenReturn(null);
            CollabInviteCode code = new CollabInviteCode();
            code.setUsed(0);
            code.setExpiresAt(LocalDateTime.now().minusDays(1));
            when(collabPort.findInviteCodeByCode("EXPIRED")).thenReturn(code);

            assertThrows(BusinessException.class, () ->
                    service.joinByInviteCode("EXPIRED", 1L));
        }

        @Test
        void 项目不可加入抛异常() {
            CollabInviteCode validCode = new CollabInviteCode();
            validCode.setId(1L);
            validCode.setProjectId(1L);
            validCode.setExpiresAt(LocalDateTime.now().plusDays(1));
            when(collabPort.findValidInviteCode("VALID")).thenReturn(validCode);

            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setStatus(CollabProjectStatus.DRAFT.getValue());
            when(collabPort.findProjectById(1L)).thenReturn(Optional.of(project));

            assertThrows(BusinessException.class, () ->
                    service.joinByInviteCode("VALID", 1L));
        }

        @Test
        void 已是成员抛异常() {
            CollabInviteCode validCode = new CollabInviteCode();
            validCode.setId(1L);
            validCode.setProjectId(1L);
            validCode.setExpiresAt(LocalDateTime.now().plusDays(1));
            when(collabPort.findValidInviteCode("VALID")).thenReturn(validCode);

            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setStatus(CollabProjectStatus.ACTIVE.getValue());
            project.setTenantId(1L);
            when(collabPort.findProjectById(1L)).thenReturn(Optional.of(project));
            when(collabPort.findMemberByProjectAndUser(1L, 1L)).thenReturn(new CollabProjectMember());

            assertThrows(BusinessException.class, () ->
                    service.joinByInviteCode("VALID", 1L));
        }

        @Test
        void 正常加入项目() {
            CollabInviteCode validCode = new CollabInviteCode();
            validCode.setId(1L);
            validCode.setProjectId(1L);
            validCode.setExpiresAt(LocalDateTime.now().plusDays(1));
            when(collabPort.findValidInviteCode("VALID")).thenReturn(validCode);

            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setStatus(CollabProjectStatus.ACTIVE.getValue());
            project.setTenantId(1L);
            when(collabPort.findProjectById(1L)).thenReturn(Optional.of(project));
            when(collabPort.findMemberByProjectAndUser(1L, 1L)).thenReturn(null);

            User user = new User();
            user.setId(1L);
            user.setUsername("testuser");
            when(userPort.findById(1L)).thenReturn(Optional.of(user));

            ProjectMemberResponse result = service.joinByInviteCode("VALID", 1L);

            assertNotNull(result);
            assertEquals("testuser", result.getUsername());
            verify(collabPort).saveMember(argThat(m ->
                    m.getInviteStatus().equals("ACTIVE")));
        }
    }

    // ============ removeMember 测试 ============

    @Nested
    @DisplayName("removeMember - 移除成员")
    class RemoveMemberTests {

        @Test
        void 成员不存在抛异常() {
            when(collabPort.findMemberById(1L)).thenReturn(Optional.empty());
            assertThrows(BusinessException.class, () ->
                    service.removeMember(1L, 1L, 1L));
        }

        @Test
        void 成员不属于该项目抛异常() {
            CollabProjectMember member = new CollabProjectMember();
            member.setId(1L);
            member.setProjectId(99L); // different project
            when(collabPort.findMemberById(1L)).thenReturn(Optional.of(member));
            assertThrows(BusinessException.class, () ->
                    service.removeMember(1L, 1L, 1L));
        }

        @Test
        void 不能移除OWNER() {
            CollabProjectMember member = new CollabProjectMember();
            member.setId(1L);
            member.setProjectId(1L);
            member.setRole(ProjectMemberRole.OWNER.getValue());
            when(collabPort.findMemberById(1L)).thenReturn(Optional.of(member));
            assertThrows(BusinessException.class, () ->
                    service.removeMember(1L, 1L, 1L));
        }

        @Test
        void 正常移除成员() {
            CollabProjectMember member = new CollabProjectMember();
            member.setId(1L);
            member.setProjectId(1L);
            member.setRole(ProjectMemberRole.TRANSLATOR.getValue());
            when(collabPort.findMemberById(1L)).thenReturn(Optional.of(member));

            service.removeMember(1L, 1L, 1L);

            assertEquals("REMOVED", member.getInviteStatus());
        }
    }

    // ============ inviteMember 测试 ============

    @Nested
    @DisplayName("inviteMember - 邀请成员")
    class InviteMemberTests {

        @Test
        void 用户不存在抛异常() {
            when(userPort.findByEmail("nonexistent@test.com")).thenReturn(Optional.empty());
            InviteMemberRequest req = new InviteMemberRequest();
            req.setEmail("nonexistent@test.com");
            req.setRole(ProjectMemberRole.TRANSLATOR);
            assertThrows(BusinessException.class, () ->
                    service.inviteMember(1L, req, 1L));
        }

        @Test
        void 已是成员抛异常() {
            User user = new User();
            user.setId(2L);
            user.setEmail("exist@test.com");
            when(userPort.findByEmail("exist@test.com")).thenReturn(Optional.of(user));
            when(collabPort.findMemberByProjectAndUser(1L, 2L)).thenReturn(new CollabProjectMember());

            InviteMemberRequest req = new InviteMemberRequest();
            req.setEmail("exist@test.com");
            req.setRole(ProjectMemberRole.TRANSLATOR);
            assertThrows(BusinessException.class, () ->
                    service.inviteMember(1L, req, 1L));
        }

        @Test
        void 正常邀请成员() {
            User user = new User();
            user.setId(2L);
            user.setEmail("newuser@test.com");
            user.setUsername("newuser");
            when(userPort.findByEmail("newuser@test.com")).thenReturn(Optional.of(user));
            when(collabPort.findMemberByProjectAndUser(1L, 2L)).thenReturn(null);

            InviteMemberRequest req = new InviteMemberRequest();
            req.setEmail("newuser@test.com");
            req.setRole(ProjectMemberRole.TRANSLATOR);

            ProjectMemberResponse result = service.inviteMember(1L, req, 1L);

            assertNotNull(result);
            assertEquals("newuser", result.getUsername());
            assertEquals("INVITED", result.getInviteStatus());
        }
    }

    // ============ updateProject 测试 ============

    @Nested
    @DisplayName("updateProject - 更新项目")
    class UpdateProjectTests {

        @Test
        void 项目不存在抛异常() {
            when(collabPort.findProjectById(1L)).thenReturn(Optional.empty());
            assertThrows(BusinessException.class, () ->
                    service.updateProject(1L, new CreateCollabProjectRequest(), 1L));
        }

        @Test
        void 正常更新项目信息() {
            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setName("Old Name");
            project.setOwnerId(1L);
            when(collabPort.findProjectById(1L)).thenReturn(Optional.of(project));
            when(userPort.findById(anyLong())).thenReturn(Optional.empty());
            when(collabPort.countMembersByProjectId(1L)).thenReturn(0);

            CreateCollabProjectRequest req = new CreateCollabProjectRequest();
            req.setName("New Name");
            req.setDescription("New Description");
            req.setSourceLang("fr");
            req.setTargetLang("de");

            CollabProjectResponse result = service.updateProject(1L, req, 1L);

            assertEquals("New Name", result.getName());
            assertEquals("fr", result.getSourceLang());
            assertEquals("de", result.getTargetLang());
        }
    }

    // ============ listByUserId / listOwnedByUserId 测试 ============

    @Nested
    @DisplayName("listByUserId / listOwnedByUserId - 列表查询")
    class ListProjectsTests {

        @Test
        void 列出用户拥有的项目() {
            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setName("My Project");
            when(collabPort.findProjectsByOwnerId(1L)).thenReturn(List.of(project));

            List<CollabProjectResponse> results = service.listOwnedByUserId(1L);

            assertEquals(1, results.size());
            assertEquals("My Project", results.get(0).getName());
        }

        @Test
        void 列出用户参与的项目() {
            CollabProject project = new CollabProject();
            project.setId(1L);
            project.setName("Shared Project");
            when(collabPort.findProjectsByMemberUserId(1L)).thenReturn(List.of(project));

            PageResponse<CollabProjectResponse> results = service.listByUserId(1L, 1, 20);

            assertEquals(1, results.getList().size());
        }

        @Test
        void 无参与项目返回空列表() {
            when(collabPort.findProjectsByMemberUserId(1L)).thenReturn(List.of());
            PageResponse<CollabProjectResponse> results = service.listByUserId(1L, 1, 20);
            assertTrue(results.getList().isEmpty());
        }
    }

    // ============ startMultiAgentTranslation 代理测试 ============

    @Nested
    @DisplayName("startMultiAgentTranslation - 代理调用")
    class StartTranslationTests {

        @Test
        void 代理调用multiAgentTranslationService() {
            service.startMultiAgentTranslation(1L);
            verify(multiAgentTranslationService).startMultiAgentTranslation(1L);
        }
    }
}
