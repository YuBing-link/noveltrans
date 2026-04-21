package com.yumu.noveltranslator.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每月1号0点清理过期的字符配额使用记录
 */
@Slf4j
@Component
public class MonthlyQuotaResetTask {

    private final JdbcTemplate jdbcTemplate;

    public MonthlyQuotaResetTask(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "0 0 0 1 * ?")
    public void resetMonthlyUsage() {
        log.info("开始执行月度字符配额清理任务");
        try {
            int deleted = jdbcTemplate.update(
                "DELETE FROM quota_usage WHERE usage_date < DATE_SUB(CURDATE(), INTERVAL 3 MONTH)"
            );
            log.info("月度字符配额清理完成，清理了 {} 条过期记录", deleted);
        } catch (Exception e) {
            log.error("月度字符配额清理失败", e);
        }
    }
}
