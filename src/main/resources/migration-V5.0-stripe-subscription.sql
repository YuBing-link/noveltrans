-- Migration V5.0: Stripe 订阅支付
-- 日期: 2026-04-24
-- 描述: 新增 Stripe 客户映射表和订阅记录表

-- =============================================
-- 1. Stripe 客户映射表（用户 ↔ Stripe Customer）
-- =============================================
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

-- =============================================
-- 2. Stripe 订阅记录表
-- =============================================
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
