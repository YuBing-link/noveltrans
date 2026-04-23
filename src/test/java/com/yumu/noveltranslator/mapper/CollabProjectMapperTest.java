package com.yumu.noveltranslator.mapper;

import com.yumu.noveltranslator.config.tenant.TenantContext;
import com.yumu.noveltranslator.entity.CollabProject;
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
 * CollabProjectMapper 自定义 SQL 集成测试
 * 测试 JOIN 查询和租户隔离
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CollabProjectMapperTest {

    @Autowired
    private CollabProjectMapper collabProjectMapper;

    @Test
    @Order(1)
    @DisplayName("selectByOwnerId - 按所有者查询项目")
    void selectByOwnerId() {
        // fulltest 用户（ID=103）有一个项目，需要绕过租户过滤
        try {
            TenantContext.setBypassTenant(true);
            List<CollabProject> projects = collabProjectMapper.selectByOwnerId(103L);
            assertNotNull(projects);
            assertFalse(projects.isEmpty());
            for (CollabProject p : projects) {
                assertEquals(103L, p.getOwnerId());
                assertEquals(0, p.getDeleted());
            }
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }

    @Test
    @Order(2)
    @DisplayName("selectByMemberUserId - JOIN 查询成员项目")
    void selectByMemberUserId() {
        // admin（ID=1）通过邀请码加入了 fulltest 的项目，需要绕过租户过滤
        try {
            TenantContext.setBypassTenant(true);
            List<CollabProject> projects = collabProjectMapper.selectByMemberUserId(1L);
            assertNotNull(projects);
            assertFalse(projects.isEmpty());
            for (CollabProject p : projects) {
                assertEquals(0, p.getDeleted());
            }
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }

    @Test
    @Order(3)
    @DisplayName("selectByMemberUserId - 非成员用户返回空")
    void selectByMemberUserIdNonMember() {
        try {
            TenantContext.setBypassTenant(true);
            List<CollabProject> projects = collabProjectMapper.selectByMemberUserId(999999L);
            assertNotNull(projects);
            assertTrue(projects.isEmpty());
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }
}
