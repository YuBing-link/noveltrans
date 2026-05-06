CREATE TABLE IF NOT EXISTS `chapter_entity_map` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `chapter_id` BIGINT NOT NULL COMMENT '章节 ID',
  `project_id` BIGINT NOT NULL COMMENT '项目 ID',
  `source_entity` VARCHAR(255) NOT NULL COMMENT '原文实体',
  `target_entity` VARCHAR(255) NOT NULL COMMENT '译文实体',
  `entity_type` VARCHAR(50) DEFAULT NULL COMMENT '实体类型：person/place/skill/organization/other',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_chapter_id` (`chapter_id`),
  INDEX `idx_project_id` (`project_id`),
  UNIQUE KEY `uk_chapter_source` (`chapter_id`, `source_entity`(100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='章节实体映射表';
