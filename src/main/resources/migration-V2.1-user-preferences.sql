-- ============================================================
-- 迁移：用户偏好设置表
-- 版本：2.1
-- 日期：2026-04-18
-- ============================================================

CREATE TABLE IF NOT EXISTS `user_preferences` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `default_engine` VARCHAR(50) DEFAULT 'google',
    `default_target_lang` VARCHAR(50) DEFAULT 'zh',
    `enable_glossary` TINYINT(1) DEFAULT 1,
    `default_glossary_id` BIGINT DEFAULT NULL,
    `enable_cache` TINYINT(1) DEFAULT 1,
    `auto_translate_selection` TINYINT(1) DEFAULT 1,
    `font_size` INT DEFAULT 14,
    `theme_mode` VARCHAR(50) DEFAULT 'light',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_prefs_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
