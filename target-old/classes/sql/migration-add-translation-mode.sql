-- ==================== 翻译记忆库增加 translation_mode 字段 ====================
-- 目的：向量匹配按模式层级过滤（FAST 可读全部，EXPERT 可读 EXPERT+TEAM，TEAM 仅读 TEAM）

ALTER TABLE `translation_memory`
    ADD COLUMN `translation_mode` VARCHAR(20) DEFAULT NULL COMMENT '翻译模式：fast/expert/team'
    AFTER `source_engine`;

-- 已有存量数据默认归为 fast 模式
UPDATE `translation_memory` SET `translation_mode` = 'fast' WHERE `translation_mode` IS NULL;
