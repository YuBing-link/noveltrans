package com.yumu.noveltranslator.adapter.out.persistence.mapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.DocumentMapper;

import com.yumu.noveltranslator.config.tenant.TenantContext;
import com.yumu.noveltranslator.adapter.out.persistence.entity.Document;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DocumentMapper 自定义 SQL 集成测试
 */
@Disabled("Requires real MySQL — Spring context fails in CI due to sqlSessionTemplate initialization")
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DocumentMapperTest {

    @Autowired
    private DocumentMapper documentMapper;

    @Test
    @Order(1)
    @DisplayName("findByUserId - 按用户查询文档")
    void findByUserId() {
        try {
            TenantContext.setBypassTenant(true);
            List<Document> docs = documentMapper.findByUserId(1L);
            assertNotNull(docs);
            for (Document doc : docs) {
                assertEquals(1L, doc.getUserId());
                assertEquals(0, doc.getDeleted());
            }
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }

    @Test
    @Order(2)
    @DisplayName("findById - 按 ID 查询")
    void findById() {
        try {
            TenantContext.setBypassTenant(true);
            Document doc = documentMapper.findById(999999L);
            assertNull(doc);
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }

    @Test
    @Order(3)
    @DisplayName("findByIdAndUserId - 按 ID 和用户查询")
    void findByIdAndUserId() {
        try {
            TenantContext.setBypassTenant(true);
            Document doc = documentMapper.findByIdAndUserId(999999L, 1L);
            assertNull(doc);
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }

    @Test
    @Order(4)
    @DisplayName("updateDeletedStatus - 软删除状态更新")
    void updateDeletedStatus() {
        try {
            TenantContext.setBypassTenant(true);
            int rows = documentMapper.updateDeletedStatus(999999L);
            assertEquals(0, rows);
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }
}
