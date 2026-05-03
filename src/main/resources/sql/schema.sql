-- ============================================================
-- NovelTrans 小说翻译平台 - MySQL 数据库架构
-- 版本：3.0 (完整 schema，含多租户)
-- 最后更新：2026-04-25
-- ============================================================
-- 说明：本文件包含完整的数据库建表语句，新环境直接执行即可，
--       无需再运行 migration 脚本。
-- ============================================================

-- ==================== 租户表 ====================
CREATE TABLE IF NOT EXISTS `tenant` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NOT NULL,
    `status` VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    `max_users` INT NOT NULL DEFAULT 1,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_tenant_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户表';

-- ==================== 用户模块 ====================
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT DEFAULT NULL,
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
    INDEX `idx_user_tenant` (`tenant_id`),
    INDEX `idx_user_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== API Key 表 ====================
CREATE TABLE IF NOT EXISTS `api_keys` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    `user_id` BIGINT NOT NULL,
    `api_key` VARCHAR(64) NOT NULL,
    `name` VARCHAR(50) DEFAULT 'Default',
    `is_active` TINYINT(1) DEFAULT 1,
    `last_used_at` DATETIME DEFAULT NULL,
    `total_usage` BIGINT DEFAULT 0,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_api_key` (`api_key`),
    INDEX `idx_api_tenant_user` (`tenant_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 配额使用记录 ====================
CREATE TABLE IF NOT EXISTS `quota_usage` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    `user_id` BIGINT NOT NULL,
    `usage_date` DATE NOT NULL,
    `characters_used` BIGINT DEFAULT 0,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_quota_tenant_user_date` (`tenant_id`, `user_id`, `usage_date`),
    INDEX `idx_quota_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 用户档位变更历史 ====================
CREATE TABLE IF NOT EXISTS `user_plan_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    `user_id` BIGINT NOT NULL,
    `old_plan` VARCHAR(20) NOT NULL,
    `new_plan` VARCHAR(20) NOT NULL,
    `changed_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `note` VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_plan_tenant_user` (`tenant_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 文档管理模块 ====================
CREATE TABLE IF NOT EXISTS `document` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
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
    INDEX `idx_document_tenant_user` (`tenant_id`, `user_id`),
    INDEX `idx_document_status` (`status`),
    INDEX `idx_document_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 翻译任务模块 ====================
CREATE TABLE IF NOT EXISTS `translation_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
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
    INDEX `idx_task_tenant_user` (`tenant_id`, `user_id`),
    INDEX `idx_task_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 翻译历史模块 ====================
CREATE TABLE IF NOT EXISTS `translation_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
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
    INDEX `idx_history_tenant_user` (`tenant_id`, `user_id`),
    INDEX `idx_history_task` (`task_id`),
    INDEX `idx_history_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 术语表模块 ====================
CREATE TABLE IF NOT EXISTS `glossary` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    `user_id` BIGINT NOT NULL,
    `source_word` VARCHAR(255) NOT NULL,
    `target_word` VARCHAR(255) NOT NULL,
    `remark` TEXT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_glossary_tenant_user` (`tenant_id`, `user_id`),
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
    `mode` VARCHAR(20) DEFAULT 'fast',
    `expire_time` DATETIME NOT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_cache_key` (`cache_key`),
    INDEX `idx_cache_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 翻译记忆库 ====================
CREATE TABLE IF NOT EXISTS `translation_memory` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
    `project_id` BIGINT DEFAULT NULL COMMENT '所属项目ID（可选）',
    `source_lang` VARCHAR(50) NOT NULL COMMENT '源语言',
    `target_lang` VARCHAR(50) NOT NULL COMMENT '目标语言',
    `source_text` TEXT NOT NULL COMMENT '原文片段',
    `target_text` TEXT NOT NULL COMMENT '译文片段',
    `embedding` JSON DEFAULT NULL COMMENT '向量嵌入 (float数组)',
    `usage_count` INT NOT NULL DEFAULT 0 COMMENT '使用次数',
    `source_engine` VARCHAR(50) DEFAULT NULL COMMENT '来源翻译引擎',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    INDEX `idx_memory_tenant_user` (`tenant_id`, `user_id`),
    INDEX `idx_memory_project` (`project_id`),
    INDEX `idx_memory_lang_pair` (`source_lang`, `target_lang`),
    INDEX `idx_memory_usage` (`usage_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='翻译记忆库表';

-- ==================== 协作项目表 ====================
CREATE TABLE IF NOT EXISTS `collab_project` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    `name` VARCHAR(255) NOT NULL COMMENT '项目名称',
    `description` TEXT COMMENT '项目描述',
    `owner_id` BIGINT NOT NULL COMMENT '项目所有者ID',
    `source_lang` VARCHAR(50) NOT NULL COMMENT '源语言',
    `target_lang` VARCHAR(50) NOT NULL COMMENT '目标语言',
    `status` VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT '项目状态: DRAFT/ACTIVE/COMPLETED/ARCHIVED',
    `progress` INT NOT NULL DEFAULT 0 COMMENT '完成进度 0-100',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删除 1=已删除',
    PRIMARY KEY (`id`),
    INDEX `idx_project_tenant_owner` (`tenant_id`, `owner_id`),
    INDEX `idx_project_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='协作项目表';

-- ==================== 项目成员表 ====================
CREATE TABLE IF NOT EXISTS `collab_project_member` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    `project_id` BIGINT NOT NULL COMMENT '所属项目ID',
    `user_id` BIGINT NOT NULL COMMENT '成员用户ID',
    `role` VARCHAR(50) NOT NULL COMMENT '角色: OWNER/TRANSLATOR/REVIEWER',
    `invite_code` VARCHAR(64) DEFAULT NULL COMMENT '邀请码',
    `invite_status` VARCHAR(50) NOT NULL DEFAULT 'ACTIVE' COMMENT '邀请状态: ACTIVE/INVITED/REMOVED',
    `joined_time` DATETIME DEFAULT NULL COMMENT '加入时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_member_project_user` (`project_id`, `user_id`),
    INDEX `idx_member_tenant` (`tenant_id`),
    INDEX `idx_member_project` (`project_id`),
    INDEX `idx_member_user` (`user_id`),
    INDEX `idx_member_invite_code` (`invite_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目成员表';

-- ==================== 章节任务表 ====================
CREATE TABLE IF NOT EXISTS `collab_chapter_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    `project_id` BIGINT NOT NULL COMMENT '所属项目ID',
    `chapter_number` INT NOT NULL COMMENT '章节序号',
    `title` VARCHAR(500) DEFAULT NULL COMMENT '章节标题',
    `source_text` MEDIUMTEXT COMMENT '原文内容',
    `target_text` MEDIUMTEXT COMMENT '译文内容',
    `assignee_id` BIGINT DEFAULT NULL COMMENT '译者用户ID',
    `reviewer_id` BIGINT DEFAULT NULL COMMENT '审校用户ID',
    `status` VARCHAR(50) NOT NULL DEFAULT 'UNASSIGNED' COMMENT '任务状态',
    `review_comment` TEXT COMMENT '审核意见',
    `progress` INT NOT NULL DEFAULT 0 COMMENT '翻译进度 0-100',
    `source_word_count` INT DEFAULT 0 COMMENT '原文词数',
    `target_word_count` INT DEFAULT 0 COMMENT '译文词数',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `assigned_time` DATETIME DEFAULT NULL COMMENT '分配时间',
    `submitted_time` DATETIME DEFAULT NULL COMMENT '提交时间',
    `reviewed_time` DATETIME DEFAULT NULL COMMENT '审核时间',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '自动重试次数',
    `completed_time` DATETIME DEFAULT NULL COMMENT '完成时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    INDEX `idx_chapter_tenant_project` (`tenant_id`, `project_id`),
    INDEX `idx_chapter_assignee` (`assignee_id`),
    INDEX `idx_chapter_status` (`status`),
    INDEX `idx_chapter_project_number` (`project_id`, `chapter_number`),
    INDEX `idx_chapter_status_update` (`status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节任务表';

-- ==================== 协作评论表 ====================
CREATE TABLE IF NOT EXISTS `collab_comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    `chapter_task_id` BIGINT NOT NULL COMMENT '所属章节任务ID',
    `user_id` BIGINT NOT NULL COMMENT '评论者用户ID',
    `source_text` VARCHAR(2000) DEFAULT NULL COMMENT '被评论的原文片段',
    `target_text` VARCHAR(2000) DEFAULT NULL COMMENT '被评论的译文片段',
    `content` TEXT NOT NULL COMMENT '评论内容',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父评论ID（用于回复）',
    `resolved` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已解决 0=未解决 1=已解决',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    INDEX `idx_comment_tenant` (`tenant_id`),
    INDEX `idx_comment_chapter` (`chapter_task_id`),
    INDEX `idx_comment_user` (`user_id`),
    INDEX `idx_comment_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='协作评论表';

-- ==================== 协作邀请码表 ====================
CREATE TABLE IF NOT EXISTS `collab_invite_code` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    `project_id` BIGINT NOT NULL,
    `code` VARCHAR(20) NOT NULL,
    `expires_at` DATETIME NOT NULL,
    `used` TINYINT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_invite_code` (`code`),
    INDEX `idx_invite_tenant` (`tenant_id`),
    INDEX `idx_invite_project` (`project_id`),
    INDEX `idx_invite_expire` (`expires_at`),
    INDEX `idx_invite_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 用户偏好设置 ====================
CREATE TABLE IF NOT EXISTS `user_preferences` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
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
    UNIQUE INDEX `idx_pref_user` (`user_id`),
    INDEX `idx_pref_tenant_user` (`tenant_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== AI 术语表 ====================
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

-- ==================== Stripe 订阅模块 ====================
CREATE TABLE IF NOT EXISTS `stripe_customer` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '本地用户ID',
    `stripe_customer_id` VARCHAR(255) NOT NULL COMMENT 'Stripe Customer ID (cus_xxx)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_stripe_customer_user` (`user_id`),
    UNIQUE INDEX `uk_stripe_customer_stripe_id` (`stripe_customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Stripe 客户映射表';

CREATE TABLE IF NOT EXISTS `stripe_subscription` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '本地用户ID',
    `stripe_customer_id` VARCHAR(255) NOT NULL COMMENT 'Stripe Customer ID',
    `stripe_subscription_id` VARCHAR(255) NOT NULL COMMENT 'Stripe Subscription ID (sub_xxx)',
    `plan` VARCHAR(50) NOT NULL COMMENT '本地套餐: PRO, MAX',
    `status` VARCHAR(50) NOT NULL COMMENT 'active, past_due, canceled, unpaid, trialing, paused',
    `stripe_price_id` VARCHAR(255) DEFAULT NULL,
    `billing_cycle` VARCHAR(50) NOT NULL DEFAULT 'monthly' COMMENT 'monthly, yearly',
    `current_period_start` DATETIME DEFAULT NULL,
    `current_period_end` DATETIME DEFAULT NULL,
    `cancel_at_period_end` TINYINT NOT NULL DEFAULT 0,
    `canceled_at` DATETIME DEFAULT NULL,
    `last_webhook_event_id` VARCHAR(255) DEFAULT NULL COMMENT '幂等控制',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_stripe_sub_stripe_id` (`stripe_subscription_id`),
    INDEX `idx_stripe_sub_user` (`user_id`),
    INDEX `idx_stripe_sub_customer` (`stripe_customer_id`),
    INDEX `idx_stripe_sub_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Stripe 订阅记录表';
