package com.yumu.noveltranslator.task;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MonthlyQuotaResetTask 单元测试")
class MonthlyQuotaResetTaskTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private MonthlyQuotaResetTask monthlyQuotaResetTask;

    @Nested
    @DisplayName("正常执行测试")
    class NormalExecutionTests {

        @Test
        @DisplayName("正常执行删除记录并记录成功日志")
        void 正常执行删除记录() {
            when(jdbcTemplate.update(anyString())).thenReturn(10);

            assertDoesNotThrow(() -> monthlyQuotaResetTask.resetMonthlyUsage());

            verify(jdbcTemplate).update(
                "DELETE FROM quota_usage WHERE usage_date < DATE_SUB(CURDATE(), INTERVAL 3 MONTH)");
        }

        @Test
        @DisplayName("删除0条记录仍然成功")
        void 删除0条记录仍然成功() {
            when(jdbcTemplate.update(anyString())).thenReturn(0);

            assertDoesNotThrow(() -> monthlyQuotaResetTask.resetMonthlyUsage());

            verify(jdbcTemplate).update(anyString());
        }

        @Test
        @DisplayName("删除大量记录正常工作")
        void 删除大量记录正常工作() {
            when(jdbcTemplate.update(anyString())).thenReturn(100000);

            assertDoesNotThrow(() -> monthlyQuotaResetTask.resetMonthlyUsage());

            verify(jdbcTemplate).update(anyString());
        }
    }

    @Nested
    @DisplayName("异常处理测试")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("SQL异常被捕获不抛出")
        void SQL异常被捕获不抛出() {
            when(jdbcTemplate.update(anyString())).thenThrow(new RuntimeException("Database connection lost"));

            // 不应抛出异常，内部已经catch
            assertDoesNotThrow(() -> monthlyQuotaResetTask.resetMonthlyUsage());
        }

        @Test
        @DisplayName("SQLException被捕获不抛出")
        void SQLException被捕获不抛出() {
            when(jdbcTemplate.update(anyString()))
                .thenThrow(new org.springframework.dao.DataAccessException("SQL error") {});

            assertDoesNotThrow(() -> monthlyQuotaResetTask.resetMonthlyUsage());
        }
    }
}
