-- 创建 ai_glossary 表（修复团队模式翻译失败问题）
-- 执行时间：2026-04-25

CREATE TABLE IF NOT EXISTS `ai_glossary` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    `project_id` BIGINT NOT NULL COMMENT '所属项目ID',
    `source_word` VARCHAR(255) NOT NULL COMMENT '原文术语',
    `target_word` VARCHAR(255) NOT NULL COMMENT '译文术语',
    `context` TEXT COMMENT '提取上下文',
    `entity_type` VARCHAR(50) COMMENT '实体类型: person/place/skill/organization/other',
    `chapter_id` BIGINT COMMENT '首次出现的章节ID',
    `confidence` DOUBLE COMMENT 'AI 提取置信度',
    `status` VARCHAR(50) NOT NULL DEFAULT 'pending' COMMENT '状态: pending/confirmed/rejected',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_ai_glossary_tenant` (`tenant_id`),
    INDEX `idx_ai_glossary_project` (`project_id`),
    INDEX `idx_ai_glossary_project_status` (`project_id`, `status`),
    UNIQUE INDEX `idx_ai_glossary_project_source` (`project_id`, `source_word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 术语表';
