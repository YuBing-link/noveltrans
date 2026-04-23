-- ============================================================
-- Migration V4.0: Multi-Tenant Architecture
-- ============================================================

-- 1. Create tenant table
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

-- 2. Add tenant_id to user table
ALTER TABLE `user` ADD COLUMN `tenant_id` BIGINT DEFAULT NULL AFTER `id`;
ALTER TABLE `user` ADD INDEX `idx_user_tenant` (`tenant_id`);

-- 3. Add tenant_id to all user-scoped tables
ALTER TABLE `document` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 AFTER `id`;
ALTER TABLE `document` ADD INDEX `idx_document_tenant_user` (`tenant_id`, `user_id`);

ALTER TABLE `translation_task` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 AFTER `id`;
ALTER TABLE `translation_task` ADD INDEX `idx_task_tenant_user` (`tenant_id`, `user_id`);

ALTER TABLE `translation_history` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 AFTER `id`;
ALTER TABLE `translation_history` ADD INDEX `idx_history_tenant_user` (`tenant_id`, `user_id`);

ALTER TABLE `glossary` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 AFTER `id`;
ALTER TABLE `glossary` ADD INDEX `idx_glossary_tenant_user` (`tenant_id`, `user_id`);

ALTER TABLE `api_keys` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 AFTER `id`;
ALTER TABLE `api_keys` ADD INDEX `idx_api_tenant_user` (`tenant_id`, `user_id`);

ALTER TABLE `user_preference` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 AFTER `id`;
ALTER TABLE `user_preference` ADD INDEX `idx_pref_tenant_user` (`tenant_id`, `user_id`);

ALTER TABLE `collab_invite_code` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 AFTER `id`;
ALTER TABLE `collab_invite_code` ADD INDEX `idx_invite_tenant` (`tenant_id`);

ALTER TABLE `translation_memory` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 AFTER `id`;
ALTER TABLE `translation_memory` ADD INDEX `idx_memory_tenant_user` (`tenant_id`, `user_id`);

ALTER TABLE `quota_usage` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 AFTER `id`;
ALTER TABLE `quota_usage` ADD INDEX `idx_quota_tenant_user_date` (`tenant_id`, `user_id`, `usage_date`);

ALTER TABLE `user_plan_history` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 AFTER `id`;
ALTER TABLE `user_plan_history` ADD INDEX `idx_plan_tenant_user` (`tenant_id`, `user_id`);

-- 4. Add tenant_id to project-scoped tables
ALTER TABLE `collab_project` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 AFTER `id`;
ALTER TABLE `collab_project` ADD INDEX `idx_project_tenant_owner` (`tenant_id`, `owner_id`);

ALTER TABLE `collab_project_member` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 AFTER `id`;
ALTER TABLE `collab_project_member` ADD INDEX `idx_member_tenant` (`tenant_id`);

ALTER TABLE `collab_chapter_task` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 AFTER `id`;
ALTER TABLE `collab_chapter_task` ADD INDEX `idx_chapter_tenant_project` (`tenant_id`, `project_id`);

ALTER TABLE `collab_comment` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 AFTER `id`;
ALTER TABLE `collab_comment` ADD INDEX `idx_comment_tenant` (`tenant_id`);

-- ALTER TABLE `ai_glossary` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 AFTER `id`;
-- ALTER TABLE `ai_glossary` ADD INDEX `idx_ai_glossary_tenant` (`tenant_id`);

-- ALTER TABLE `chapter_entity_map` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 AFTER `id`;
-- ALTER TABLE `chapter_entity_map` ADD INDEX `idx_chapter_entity_tenant` (`tenant_id`);

-- 5. Backfill tenant_id for existing data
-- Each existing user gets their id as tenant_id (single-user tenant model)
UPDATE `user` SET tenant_id = id WHERE tenant_id IS NULL;

-- Create tenant records for existing users
INSERT INTO `tenant` (`id`, `name`, `status`, `max_users`, `create_time`, `update_time`)
SELECT id, CONCAT(username, "'s Tenant"), 'ACTIVE', 1, COALESCE(create_time, NOW()), COALESCE(update_time, NOW())
FROM `user`
WHERE NOT EXISTS (SELECT 1 FROM `tenant` t WHERE t.id = `user`.tenant_id);

-- Propagate tenant_id from user to all user-scoped tables
UPDATE `document` d JOIN `user` u ON d.user_id = u.id SET d.tenant_id = u.tenant_id;
UPDATE `translation_task` t JOIN `user` u ON t.user_id = u.id SET t.tenant_id = u.tenant_id;
UPDATE `translation_history` h JOIN `user` u ON h.user_id = u.id SET h.tenant_id = u.tenant_id;
UPDATE `glossary` g JOIN `user` u ON g.user_id = u.id SET g.tenant_id = u.tenant_id;
UPDATE `api_keys` a JOIN `user` u ON a.user_id = u.id SET a.tenant_id = u.tenant_id;
UPDATE `user_preference` p JOIN `user` u ON p.user_id = u.id SET p.tenant_id = u.tenant_id;
UPDATE `translation_memory` m JOIN `user` u ON m.user_id = u.id SET m.tenant_id = u.tenant_id;
UPDATE `quota_usage` q JOIN `user` u ON q.user_id = u.id SET q.tenant_id = u.tenant_id;
UPDATE `user_plan_history` ph JOIN `user` u ON ph.user_id = u.id SET ph.tenant_id = u.tenant_id;

-- Propagate tenant_id from project owner to project-scoped tables
UPDATE `collab_project` cp JOIN `user` u ON cp.owner_id = u.id SET cp.tenant_id = u.tenant_id;
UPDATE `collab_project_member` m JOIN `collab_project` p ON m.project_id = p.id SET m.tenant_id = p.tenant_id;
UPDATE `collab_chapter_task` ct JOIN `collab_project` p ON ct.project_id = p.id SET ct.tenant_id = p.tenant_id;
UPDATE `collab_comment` c JOIN `collab_chapter_task` ct ON c.chapter_task_id = ct.id SET c.tenant_id = ct.tenant_id;
UPDATE `collab_invite_code` ic JOIN `collab_project` p ON ic.project_id = p.id SET ic.tenant_id = p.tenant_id;
-- UPDATE `ai_glossary` ag JOIN `collab_project` p ON ag.project_id = p.id SET ag.tenant_id = p.tenant_id;
-- UPDATE `chapter_entity_map` cem JOIN `collab_project` p ON cem.project_id = p.id SET cem.tenant_id = p.tenant_id;
