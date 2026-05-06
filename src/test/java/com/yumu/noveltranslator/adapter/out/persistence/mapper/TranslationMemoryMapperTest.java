package com.yumu.noveltranslator.mapper;

import com.yumu.noveltranslator.config.tenant.TenantContext;
import com.yumu.noveltranslator.entity.TranslationMemory;
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
 * TranslationMemoryMapper 自定义 SQL 集成测试
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TranslationMemoryMapperTest {

    @Autowired
    private TranslationMemoryMapper translationMemoryMapper;

    @Test
    @Order(1)
    @DisplayName("selectTopByUserAndLang - 按用户和语言查询翻译记忆")
    void selectTopByUserAndLang() {
        try {
            TenantContext.setBypassTenant(true);
            List<TranslationMemory> memories = translationMemoryMapper.selectTopByUserAndLang(1L, "ja", "zh", 10);
            assertNotNull(memories);
            for (int i = 1; i < memories.size(); i++) {
                assertTrue(memories.get(i - 1).getUsageCount() >= memories.get(i).getUsageCount());
            }
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }

    @Test
    @Order(2)
    @DisplayName("selectByProjectId - 按项目查询翻译记忆")
    void selectByProjectId() {
        try {
            TenantContext.setBypassTenant(true);
            List<TranslationMemory> memories = translationMemoryMapper.selectByProjectId(19L);
            assertNotNull(memories);
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }
}
