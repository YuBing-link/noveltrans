package com.yumu.noveltranslator.mapper;

import com.yumu.noveltranslator.config.tenant.TenantContext;
import com.yumu.noveltranslator.entity.TranslationTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TranslationTaskMapper 自定义 SQL 集成测试
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TranslationTaskMapperTest {

    @Autowired
    private TranslationTaskMapper translationTaskMapper;

    @Test
    @Order(1)
    @DisplayName("findByTaskId - 按任务 ID 查询")
    void findByTaskId() {
        try {
            TenantContext.setBypassTenant(true);
            TranslationTask task = translationTaskMapper.findByTaskId("NON_EXISTENT_TASK");
            assertNull(task);
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }

    @Test
    @Order(2)
    @DisplayName("findByDocumentId - 按文档 ID 查询")
    void findByDocumentId() {
        try {
            TenantContext.setBypassTenant(true);
            TranslationTask task = translationTaskMapper.findByDocumentId(999999L);
            assertNull(task);
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }

    @Test
    @Order(3)
    @DisplayName("findByUserIdAndStatus - 按用户和状态查询")
    void findByUserIdAndStatus() {
        try {
            TenantContext.setBypassTenant(true);
            List<TranslationTask> tasks = translationTaskMapper.findByUserIdAndStatus(1L, 0, 10);
            assertNotNull(tasks);
            for (TranslationTask task : tasks) {
                assertEquals(1L, task.getUserId());
                assertTrue(task.getStatus().equals("pending") || task.getStatus().equals("processing"));
            }
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }

    @Test
    @Order(4)
    @DisplayName("findByStatusAndCreateTimeBefore - 按状态和创建时间查询")
    void findByStatusAndCreateTimeBefore() {
        try {
            TenantContext.setBypassTenant(true);
            List<TranslationTask> tasks = translationTaskMapper.findByStatusAndCreateTimeBefore(
                "pending", LocalDateTime.now().plusDays(1));
            assertNotNull(tasks);
            for (TranslationTask task : tasks) {
                assertEquals("pending", task.getStatus());
                assertTrue(task.getCreateTime().isBefore(LocalDateTime.now().plusDays(1)));
            }
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }
}
