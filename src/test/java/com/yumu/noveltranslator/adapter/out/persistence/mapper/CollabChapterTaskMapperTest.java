package com.yumu.noveltranslator.mapper;

import com.yumu.noveltranslator.config.tenant.TenantContext;
import com.yumu.noveltranslator.entity.CollabChapterTask;
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
 * CollabChapterTaskMapper 自定义 SQL 集成测试
 * 需要 Docker Compose 数据库环境
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CollabChapterTaskMapperTest {

    @Autowired
    private CollabChapterTaskMapper collabChapterTaskMapper;

    @Test
    @Order(1)
    @DisplayName("selectByProjectId - 按项目 ID 查询章节任务")
    void selectByProjectId() {
        try {
            TenantContext.setBypassTenant(true);
            List<CollabChapterTask> tasks = collabChapterTaskMapper.selectByProjectId(19L);
            assertNotNull(tasks);
            for (CollabChapterTask task : tasks) {
                assertEquals(19L, task.getProjectId());
                assertEquals(0, task.getDeleted());
            }
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }

    @Test
    @Order(2)
    @DisplayName("selectByProjectIdAndStatus - 按项目和状态查询")
    void selectByProjectIdAndStatus() {
        try {
            TenantContext.setBypassTenant(true);
            List<CollabChapterTask> tasks = collabChapterTaskMapper.selectByProjectIdAndStatus(19L, "NON_EXISTENT");
            assertNotNull(tasks);
            assertTrue(tasks.isEmpty());
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }

    @Test
    @Order(3)
    @DisplayName("countByProjectIdAndStatus - 按项目和状态计数")
    void countByProjectIdAndStatus() {
        try {
            TenantContext.setBypassTenant(true);
            int count = collabChapterTaskMapper.countByProjectIdAndStatus(19L, "TRANSLATING");
            assertTrue(count >= 0);
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }

    @Test
    @Order(4)
    @DisplayName("selectByAssigneeId - 按指派人查询")
    void selectByAssigneeId() {
        try {
            TenantContext.setBypassTenant(true);
            List<CollabChapterTask> tasks = collabChapterTaskMapper.selectByAssigneeId(999999L);
            assertNotNull(tasks);
            assertTrue(tasks.isEmpty());
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }
}
