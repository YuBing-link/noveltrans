package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.config.tenant.TenantContext;
import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.CollabInviteCode;
import com.yumu.noveltranslator.entity.CollabProject;
import com.yumu.noveltranslator.entity.CollabProjectMember;
import com.yumu.noveltranslator.enums.CollabProjectStatus;
import com.yumu.noveltranslator.mapper.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CollabProjectService 集成测试
 * 测试协作项目管理、邀请码、成员加入等核心流程
 * 注意：由于测试环境没有认证上下文，租户上下文通过 TenantContext 手动设置
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CollabProjectServiceIntegrationTest {

    @Autowired
    private CollabProjectService collabProjectService;

    @Autowired
    private CollabProjectMapper collabProjectMapper;

    @Autowired
    private CollabProjectMemberMapper collabProjectMemberMapper;

    @Autowired
    private CollabInviteCodeMapper collabInviteCodeMapper;

    private static Long createdProjectId;
    private static String inviteCode;

    @AfterAll
    static void cleanup() {
        TenantContext.clear();
    }

    @Test
    @Order(1)
    @DisplayName("创建协作项目")
    void createProject() {
        // 设置 fulltest 的租户
        TenantContext.setTenantId(103L);
        try {
            CreateCollabProjectRequest request = new CreateCollabProjectRequest();
            request.setName("Integration Test Project");
            request.setDescription("Test project for mapper coverage");
            request.setSourceLang("en");
            request.setTargetLang("zh");

            CollabProjectResponse response = collabProjectService.createProject(request, 103L);

            assertNotNull(response);
            assertNotNull(response.getId());
            assertEquals("Integration Test Project", response.getName());
            assertEquals(CollabProjectStatus.DRAFT.getValue(), response.getStatus());

            createdProjectId = response.getId();

            // 更新项目状态为 ACTIVE，以便后续测试加入功能
            com.yumu.noveltranslator.entity.CollabProject project = new com.yumu.noveltranslator.entity.CollabProject();
            project.setId(createdProjectId);
            project.setStatus(CollabProjectStatus.ACTIVE.getValue());
            collabProjectMapper.updateById(project);
        } finally {
            TenantContext.setTenantId(null);
        }
    }

    @Test
    @Order(2)
    @DisplayName("查询项目详情")
    void getProjectById() {
        assertNotNull(createdProjectId);
        TenantContext.setTenantId(103L);
        try {
            CollabProjectResponse response = collabProjectService.getProjectById(createdProjectId);
            assertNotNull(response);
            assertEquals(createdProjectId, response.getId());
        } finally {
            TenantContext.setTenantId(null);
        }
    }

    @Test
    @Order(3)
    @DisplayName("生成邀请码")
    void generateInviteCode() {
        assertNotNull(createdProjectId);
        TenantContext.setTenantId(103L);
        try {
            CollabProjectService.InviteCodeResult result = collabProjectService.generateInviteCode(createdProjectId, 103L);
            assertNotNull(result);
            assertNotNull(result.code());
            assertNotNull(result.expiresAt());
            assertTrue(result.expiresAt().isAfter(LocalDateTime.now()));

            inviteCode = result.code();
        } finally {
            TenantContext.setTenantId(null);
        }
    }

    @Test
    @Order(4)
    @DisplayName("邀请码查询 - 跨租户有效")
    void inviteCodeQuery() {
        assertNotNull(inviteCode);
        try {
            TenantContext.setBypassTenant(true);
            CollabInviteCode codeRecord = collabInviteCodeMapper.selectByValidCode(inviteCode);
            assertNotNull(codeRecord);
            assertEquals(inviteCode, codeRecord.getCode());
            assertEquals(createdProjectId, codeRecord.getProjectId());
            assertEquals(0, codeRecord.getUsed());
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }

    @Test
    @Order(5)
    @DisplayName("用户通过邀请码加入项目")
    void joinProject() {
        assertNotNull(inviteCode);
        // 加入操作需要 bypass 租户过滤（跨租户）
        TenantContext.setBypassTenant(true);
        ProjectMemberResponse response = collabProjectService.joinByInviteCode(inviteCode, 1L);
        assertNotNull(response);
        assertEquals(1L, response.getUserId());
        // joinByInviteCode 内部会在 finally 中清除 bypass，需要重新设置
        TenantContext.setBypassTenant(true);
        CollabProjectMember member = collabProjectMemberMapper.selectByProjectAndUser(createdProjectId, 1L);
        assertNotNull(member);
        assertEquals(createdProjectId, member.getProjectId());
        assertEquals(1L, member.getUserId());
        TenantContext.setBypassTenant(false);
    }

    @Test
    @Order(6)
    @DisplayName("重复加入项目 - 抛出异常")
    void joinProjectDuplicate() {
        assertNotNull(inviteCode);
        try {
            TenantContext.setBypassTenant(true);
            assertThrows(IllegalArgumentException.class, () -> {
                collabProjectService.joinByInviteCode(inviteCode, 1L);
            });
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }

    @Test
    @Order(7)
    @DisplayName("用户参与的项目列表 - 包含加入的项目")
    void listByUserId() {
        try {
            TenantContext.setBypassTenant(true);
            List<CollabProjectResponse> projects = collabProjectService.listByUserId(1L);
            assertNotNull(projects);
            assertFalse(projects.isEmpty());
            boolean found = projects.stream().anyMatch(p -> p.getId().equals(createdProjectId));
            assertTrue(found);
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }

    @Test
    @Order(8)
    @DisplayName("项目所有者查询 - 包含自己创建的项目")
    void listByOwnerId() {
        try {
            TenantContext.setBypassTenant(true);
            List<CollabProject> projects = collabProjectMapper.selectByOwnerId(103L);
            assertNotNull(projects);
            assertFalse(projects.isEmpty());
            boolean found = projects.stream().anyMatch(p -> p.getId().equals(createdProjectId));
            assertTrue(found);
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }

    @Test
    @Order(9)
    @DisplayName("更新项目信息")
    void updateProject() {
        assertNotNull(createdProjectId);
        TenantContext.setTenantId(103L);
        try {
            CreateCollabProjectRequest request = new CreateCollabProjectRequest();
            request.setName("Updated Project Name");
            request.setDescription("Updated description");
            request.setSourceLang("en");
            request.setTargetLang("ja");

            CollabProjectResponse response = collabProjectService.updateProject(createdProjectId, request, 103L);
            assertNotNull(response);
            assertEquals("Updated Project Name", response.getName());
            assertEquals("ja", response.getTargetLang());
        } finally {
            TenantContext.setTenantId(null);
        }
    }

    @Test
    @Order(10)
    @DisplayName("获取项目成员列表")
    void getMembers() {
        assertNotNull(createdProjectId);
        TenantContext.setTenantId(103L);
        try {
            List<ProjectMemberResponse> members = collabProjectService.getMembers(createdProjectId);
            assertNotNull(members);
            assertTrue(members.size() >= 2);
        } finally {
            TenantContext.setTenantId(null);
        }
    }

    @Test
    @Order(11)
    @DisplayName("无效邀请码 - 抛出异常")
    void joinWithInvalidInviteCode() {
        try {
            TenantContext.setBypassTenant(true);
            assertThrows(IllegalArgumentException.class, () -> {
                collabProjectService.joinByInviteCode("INVALID_CODE_XYZ", 1L);
            });
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }

    @Test
    @Order(12)
    @DisplayName("不存在的项目 - 抛出异常")
    void getNonExistentProject() {
        TenantContext.setTenantId(103L);
        try {
            assertThrows(IllegalArgumentException.class, () -> {
                collabProjectService.getProjectById(999999L);
            });
        } finally {
            TenantContext.setTenantId(null);
        }
    }
}
