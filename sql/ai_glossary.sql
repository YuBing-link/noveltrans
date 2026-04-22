CREATE TABLE IF NOT EXISTS `ai_glossary` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `project_id` BIGINT NOT NULL COMMENT '项目 ID（小说）',
  `source_word` VARCHAR(255) NOT NULL COMMENT '原文术语',
  `target_word` VARCHAR(255) NOT NULL COMMENT '译文术语',
  `context` TEXT DEFAULT NULL COMMENT '提取上下文（摘录）',
  `entity_type` VARCHAR(50) DEFAULT NULL COMMENT '实体类型：person/place/skill/organization/other',
  `chapter_id` BIGINT DEFAULT NULL COMMENT '首次出现的章节 ID',
  `confidence` DOUBLE DEFAULT 0.8 COMMENT 'AI 提取置信度',
  `status` VARCHAR(20) DEFAULT 'pending' COMMENT '状态：pending（待确认）/ confirmed（已确认）/ rejected（已拒绝）',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_project_source` (`project_id`, `source_word`(100)),
  INDEX `idx_project_id` (`project_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_chapter_id` (`chapter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 维护的术语表';
