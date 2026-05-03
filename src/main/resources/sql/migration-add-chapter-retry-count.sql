-- 为 collab_chapter_task 添加 retry_count 字段，持久化重试次数（解决重启后重试计数丢失问题）
ALTER TABLE `collab_chapter_task`
    ADD COLUMN `retry_count` INT NOT NULL DEFAULT 0 COMMENT '自动重试次数' AFTER `reviewed_time`;

-- 为状态+更新时间建立索引，支持定时任务高效扫描卡住的章节
CREATE INDEX `idx_chapter_status_update` ON `collab_chapter_task` (`status`, `update_time`);
