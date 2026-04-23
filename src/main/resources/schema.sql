-- ============================================================
-- NovelTrans 小说翻译平台 - MySQL 数据库架构
-- 版本：2.0 (MySQL)
-- 最后更新：2026-04-17
-- ============================================================

-- ==================== 用户模块 ====================
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `email` VARCHAR(255) NOT NULL,
    `username` VARCHAR(255) DEFAULT NULL,
    `avatar` VARCHAR(512) DEFAULT NULL,
    `password` VARCHAR(255) DEFAULT NULL,
    `api_key` VARCHAR(255) DEFAULT NULL,
    `refresh_token` TEXT DEFAULT NULL,
    `user_level` VARCHAR(50) DEFAULT 'FREE',
    `status` VARCHAR(50) DEFAULT 'ACTIVE',
    `last_login_time` DATETIME DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_user_email` (`email`),
    INDEX `idx_user_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== API Key 表 ====================
CREATE TABLE IF NOT EXISTS `api_keys` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `api_key` VARCHAR(64) NOT NULL,
    `name` VARCHAR(50) DEFAULT 'Default',
    `is_active` TINYINT(1) DEFAULT 1,
    `last_used_at` DATETIME DEFAULT NULL,
    `total_usage` BIGINT DEFAULT 0,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_api_key` (`api_key`),
    INDEX `idx_api_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 配额使用记录 ====================
CREATE TABLE IF NOT EXISTS `quota_usage` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `usage_date` DATE NOT NULL,
    `characters_used` BIGINT DEFAULT 0,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_quota_user_date` (`user_id`, `usage_date`),
    INDEX `idx_quota_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 用户档位变更历史 ====================
CREATE TABLE IF NOT EXISTS `user_plan_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `old_plan` VARCHAR(20) NOT NULL,
    `new_plan` VARCHAR(20) NOT NULL,
    `changed_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `note` VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_plan_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 文档管理模块 ====================
CREATE TABLE IF NOT EXISTS `document` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `path` VARCHAR(1024) NOT NULL,
    `source_lang` VARCHAR(50) DEFAULT NULL,
    `target_lang` VARCHAR(50) NOT NULL,
    `file_type` VARCHAR(50) DEFAULT NULL,
    `file_size` BIGINT DEFAULT NULL,
    `task_id` VARCHAR(255) DEFAULT NULL,
    `status` VARCHAR(50) DEFAULT 'pending',
    `mode` VARCHAR(50) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    `completed_time` DATETIME DEFAULT NULL,
    `error_message` TEXT DEFAULT NULL,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_document_user` (`user_id`),
    INDEX `idx_document_status` (`status`),
    INDEX `idx_document_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 翻译任务模块 ====================
CREATE TABLE IF NOT EXISTS `translation_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_id` VARCHAR(255) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `type` VARCHAR(50) DEFAULT NULL,
    `document_id` BIGINT DEFAULT NULL,
    `source_lang` VARCHAR(50) DEFAULT NULL,
    `target_lang` VARCHAR(50) NOT NULL,
    `mode` VARCHAR(50) DEFAULT NULL,
    `engine` VARCHAR(50) DEFAULT NULL,
    `status` VARCHAR(50) DEFAULT 'pending',
    `progress` INT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    `completed_time` DATETIME DEFAULT NULL,
    `error_message` TEXT DEFAULT NULL,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_task_task_id` (`task_id`),
    INDEX `idx_task_user` (`user_id`),
    INDEX `idx_task_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 翻译历史模块 ====================
CREATE TABLE IF NOT EXISTS `translation_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `task_id` VARCHAR(255) NOT NULL,
    `type` VARCHAR(50) DEFAULT NULL,
    `document_id` BIGINT DEFAULT NULL,
    `source_lang` VARCHAR(50) DEFAULT NULL,
    `target_lang` VARCHAR(50) NOT NULL,
    `source_text` TEXT DEFAULT NULL,
    `target_text` TEXT DEFAULT NULL,
    `engine` VARCHAR(50) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_history_user` (`user_id`),
    INDEX `idx_history_task` (`task_id`),
    INDEX `idx_history_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 术语表模块 ====================
CREATE TABLE IF NOT EXISTS `glossary` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `source_word` VARCHAR(255) NOT NULL,
    `target_word` VARCHAR(255) NOT NULL,
    `remark` TEXT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_glossary_user` (`user_id`),
    UNIQUE INDEX `idx_glossary_user_source` (`user_id`, `source_word`),
    INDEX `idx_glossary_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 翻译缓存模块 ====================
CREATE TABLE IF NOT EXISTS `translation_cache` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `cache_key` VARCHAR(255) NOT NULL,
    `source_text` TEXT DEFAULT NULL,
    `target_text` TEXT DEFAULT NULL,
    `source_lang` VARCHAR(50) DEFAULT NULL,
    `target_lang` VARCHAR(50) NOT NULL,
    `engine` VARCHAR(50) DEFAULT NULL,
    `expire_time` DATETIME NOT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_cache_key` (`cache_key`),
    INDEX `idx_cache_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 协作邀请码表 ====================
CREATE TABLE IF NOT EXISTS `collab_invite_code` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NOT NULL,
    `code` VARCHAR(20) NOT NULL,
    `expires_at` DATETIME NOT NULL,
    `used` TINYINT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_invite_code` (`code`),
    INDEX `idx_invite_project` (`project_id`),
    INDEX `idx_invite_expire` (`expires_at`),
    INDEX `idx_invite_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 用户偏好设置 ====================
CREATE TABLE IF NOT EXISTS `user_preference` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `default_engine` VARCHAR(50) DEFAULT 'google',
    `default_target_lang` VARCHAR(50) DEFAULT 'zh',
    `enable_glossary` TINYINT(1) DEFAULT 1,
    `default_glossary_id` BIGINT DEFAULT NULL,
    `enable_cache` TINYINT(1) DEFAULT 1,
    `auto_translate_selection` TINYINT(1) DEFAULT 1,
    `font_size` INT DEFAULT 14,
    `theme_mode` VARCHAR(20) DEFAULT 'light',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_pref_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
