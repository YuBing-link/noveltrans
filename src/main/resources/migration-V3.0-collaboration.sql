-- Migration V3.0: 协作翻译工作流 + RAG 翻译记忆系统
-- 日期: 2026-04-18
-- 描述: 新增协作项目管理、章节任务分配、协作批注、翻译记忆库

-- =============================================
-- 1. 协作项目表
-- =============================================
CREATE TABLE IF NOT EXISTS `collab_project` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
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
    INDEX `idx_project_owner` (`owner_id`),
    INDEX `idx_project_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='协作项目表';

-- =============================================
-- 2. 项目成员表
-- =============================================
CREATE TABLE IF NOT EXISTS `collab_project_member` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
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
    INDEX `idx_member_project` (`project_id`),
    INDEX `idx_member_user` (`user_id`),
    INDEX `idx_member_invite_code` (`invite_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目成员表';

-- =============================================
-- 3. 章节任务表
-- =============================================
CREATE TABLE IF NOT EXISTS `collab_chapter_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `project_id` BIGINT NOT NULL COMMENT '所属项目ID',
    `chapter_number` INT NOT NULL COMMENT '章节序号',
    `title` VARCHAR(500) DEFAULT NULL COMMENT '章节标题',
    `source_text` MEDIUMTEXT COMMENT '原文内容',
    `target_text` MEDIUMTEXT COMMENT '译文内容',
    `assignee_id` BIGINT DEFAULT NULL COMMENT '译者用户ID',
    `reviewer_id` BIGINT DEFAULT NULL COMMENT '审校用户ID',
    `status` VARCHAR(50) NOT NULL DEFAULT 'UNASSIGNED' COMMENT '任务状态: UNASSIGNED/TRANSLATING/SUBMITTED/REVIEWING/APPROVED/REJECTED/COMPLETED',
    `review_comment` TEXT COMMENT '审核意见',
    `progress` INT NOT NULL DEFAULT 0 COMMENT '翻译进度 0-100',
    `source_word_count` INT DEFAULT 0 COMMENT '原文词数',
    `target_word_count` INT DEFAULT 0 COMMENT '译文词数',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `assigned_time` DATETIME DEFAULT NULL COMMENT '分配时间',
    `submitted_time` DATETIME DEFAULT NULL COMMENT '提交时间',
    `reviewed_time` DATETIME DEFAULT NULL COMMENT '审核时间',
    `completed_time` DATETIME DEFAULT NULL COMMENT '完成时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    INDEX `idx_chapter_project` (`project_id`),
    INDEX `idx_chapter_assignee` (`assignee_id`),
    INDEX `idx_chapter_status` (`status`),
    INDEX `idx_chapter_project_number` (`project_id`, `chapter_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节任务表';

-- =============================================
-- 4. 协作评论表
-- =============================================
CREATE TABLE IF NOT EXISTS `collab_comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
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
    INDEX `idx_comment_chapter` (`chapter_task_id`),
    INDEX `idx_comment_user` (`user_id`),
    INDEX `idx_comment_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='协作评论表';

-- =============================================
-- 5. 翻译记忆库表
-- =============================================
CREATE TABLE IF NOT EXISTS `translation_memory` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
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
    INDEX `idx_memory_user` (`user_id`),
    INDEX `idx_memory_project` (`project_id`),
    INDEX `idx_memory_lang_pair` (`source_lang`, `target_lang`),
    INDEX `idx_memory_usage` (`usage_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='翻译记忆库表';
