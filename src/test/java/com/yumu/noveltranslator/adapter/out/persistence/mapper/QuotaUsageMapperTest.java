package com.yumu.noveltranslator.mapper;

import com.yumu.noveltranslator.entity.QuotaUsage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QuotaUsageMapper 自定义 SQL 集成测试
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QuotaUsageMapperTest {

    @Autowired
    private QuotaUsageMapper quotaUsageMapper;

    @Test
    @Order(1)
    @DisplayName("findByUserIdAndDate - 按用户和日期查询配额使用")
    void findByUserIdAndDate() {
        // 查询今天的记录（可能不存在）
        QuotaUsage usage = quotaUsageMapper.findByUserIdAndDate(1L, LocalDate.now());
        // 可能为 null 或返回已有记录
        if (usage != null) {
            assertEquals(1L, usage.getUserId());
            assertEquals(LocalDate.now(), usage.getUsageDate());
        }
    }

    @Test
    @Order(2)
    @DisplayName("incrementUsage - 增加使用量（INSERT ... ON DUPLICATE KEY UPDATE）")
    void incrementUsage() {
        Long testUserId = 99999L;
        LocalDate today = LocalDate.now();

        // 先查询是否已存在
        QuotaUsage before = quotaUsageMapper.findByUserIdAndDate(testUserId, today);
        long beforeChars = before != null ? before.getCharactersUsed() : 0L;

        // 增加 100 字符
        quotaUsageMapper.incrementUsage(testUserId, today, 100L);

        // 验证增加后的值
        QuotaUsage after = quotaUsageMapper.findByUserIdAndDate(testUserId, today);
        assertNotNull(after);
        assertEquals(beforeChars + 100L, after.getCharactersUsed());

        // 再增加 50 字符，验证累加
        quotaUsageMapper.incrementUsage(testUserId, today, 50L);
        QuotaUsage after2 = quotaUsageMapper.findByUserIdAndDate(testUserId, today);
        assertEquals(beforeChars + 150L, after2.getCharactersUsed());
    }

    @Test
    @Order(3)
    @DisplayName("getMonthlyUsage - 查询月度使用量")
    void getMonthlyUsage() {
        long usage = quotaUsageMapper.getMonthlyUsage(1L, LocalDate.now());
        assertTrue(usage >= 0);
    }
}
