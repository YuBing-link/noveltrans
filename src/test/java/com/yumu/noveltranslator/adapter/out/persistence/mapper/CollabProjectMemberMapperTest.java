package com.yumu.noveltranslator.mapper;

import com.yumu.noveltranslator.config.tenant.TenantContext;
import com.yumu.noveltranslator.entity.CollabProjectMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CollabProjectMemberMapper 自定义 SQL 集成测试
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CollabProjectMemberMapperTest {

    @Autowired
    private CollabProjectMemberMapper collabProjectMemberMapper;

    @Test
    @Order(1)
    @DisplayName("selectByProjectId - 按项目查询成员")
    void selectByProjectId() {
        try {
            TenantContext.setBypassTenant(true);
            List<CollabProjectMember> members = collabProjectMemberMapper.selectByProjectId(19L);
            assertNotNull(members);
            assertFalse(members.isEmpty());
            for (CollabProjectMember m : members) {
                assertEquals(19L, m.getProjectId());
                assertEquals(0, m.getDeleted());
            }
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }

    @Test
    @Order(2)
    @DisplayName("selectByInviteCode - 按邀请码查询")
    void selectByInviteCode() {
        try {
            TenantContext.setBypassTenant(true);
            CollabProjectMember member = collabProjectMemberMapper.selectByInviteCode("NON_EXISTENT");
            assertNull(member);
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }

    @Test
    @Order(3)
    @DisplayName("countByProjectIdAndRole - 按项目和角色计数")
    void countByProjectIdAndRole() {
        try {
            TenantContext.setBypassTenant(true);
            int translatorCount = collabProjectMemberMapper.countByProjectIdAndRole(19L, "TRANSLATOR");
            assertTrue(translatorCount >= 0);
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }

    @Test
    @Order(4)
    @DisplayName("selectByProjectAndUser - 按项目和用户查询")
    void selectByProjectAndUser() {
        try {
            TenantContext.setBypassTenant(true);
            CollabProjectMember member = collabProjectMemberMapper.selectByProjectAndUser(19L, 1L);
            assertNotNull(member);
            assertEquals(19L, member.getProjectId());
            assertEquals(1L, member.getUserId());
            assertEquals(0, member.getDeleted());
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }

    @Test
    @Order(5)
    @DisplayName("selectByProjectAndUser - 非成员返回 null")
    void selectByProjectAndUserNonMember() {
        try {
            TenantContext.setBypassTenant(true);
            CollabProjectMember member = collabProjectMemberMapper.selectByProjectAndUser(19L, 999999L);
            assertNull(member);
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }
}
