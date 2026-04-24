# Stripe 订阅支付集成方案

## 一、概述

本项目已具备多租户架构、用户等级（FREE/PRO/MAX）和配额管理体系，但缺少支付/订阅功能。本方案通过集成 Stripe 实现完整的订阅支付闭环。

**集成模式**: Stripe Checkout + Webhook
- 用户通过 Checkout Session 跳转到 Stripe 支付页完成支付
- Stripe 通过 Webhook 通知后端订阅状态变更
- 后端更新本地数据库并同步 `user.userLevel`

## 二、技术栈

| 组件 | 技术 |
|------|------|
| 框架 | Spring Boot 3.2.0, Java 21 |
| ORM | MyBatis-Plus 3.5.5 |
| 数据库 | MySQL 8.0 |
| 认证 | JWT + API Key |
| 新增依赖 | `com.stripe:stripe-java` (最新稳定版) |

## 三、新增文件清单

### 3.1 配置层

| 文件路径 | 说明 |
|----------|------|
| `src/main/java/.../properties/StripeProperties.java` | 从 `application.yaml` 读取 Stripe 配置 |
| `src/main/java/.../config/StripeConfig.java` | Stripe SDK 初始化配置 |

### 3.2 数据层

| 文件路径 | 说明 |
|----------|------|
| `src/main/java/.../entity/StripeCustomer.java` | 用户 ↔ Stripe Customer 映射 |
| `src/main/java/.../entity/StripeSubscription.java` | 订阅记录 |
| `src/main/java/.../mapper/StripeCustomerMapper.java` | MyBatis-Plus Mapper |
| `src/main/java/.../mapper/StripeSubscriptionMapper.java` | MyBatis-Plus Mapper |
| `src/main/resources/migration-V5.0-stripe-subscription.sql` | 数据库迁移 SQL |

### 3.3 DTO / 枚举层

| 文件路径 | 说明 |
|----------|------|
| `src/main/java/.../dto/CheckoutSessionRequest.java` | 创建 Checkout Session 请求 |
| `src/main/java/.../dto/CheckoutSessionResponse.java` | 返回 checkoutUrl |
| `src/main/java/.../dto/SubscriptionStatusResponse.java` | 当前订阅状态 |
| `src/main/java/.../dto/PortalSessionResponse.java` | Portal 跳转 URL |
| `src/main/java/.../enums/BillingCycle.java` | 计费周期枚举（MONTHLY / YEARLY） |
| `src/main/java/.../enums/SubscriptionPlan.java` | 套餐类型枚举（PRO / MAX） |

### 3.4 业务层

| 文件路径 | 说明 |
|----------|------|
| `src/main/java/.../service/SubscriptionService.java` | 核心订阅业务逻辑 |

### 3.5 控制层

| 文件路径 | 说明 |
|----------|------|
| `src/main/java/.../controller/SubscriptionController.java` | 用户端订阅 API |
| `src/main/java/.../controller/StripeWebhookController.java` | Stripe Webhook 接收端点 |

## 四、需要修改的文件

| 文件 | 修改内容 |
|------|----------|
| `pom.xml` | 添加 `stripe-java` 依赖 |
| `src/main/resources/application.yaml` | 添加 `stripe:` 配置段 |
| `src/main/java/.../security/SecurityPermitAllPaths.java` | 添加 `"/webhook/stripe"` 白名单 |
| `src/main/java/.../enums/ErrorCodeEnum.java` | 添加订阅相关错误码（P0xx 前缀） |

## 五、数据库设计

### 5.1 stripe_customer 表

> **设计说明**：该表仅做 `user_id ↔ stripe_customer_id` 映射，不冗余 `email`（user 表已有）。不引入 `tenant_id`，因为 Stripe Customer 是用户级别概念，不是租户级别。

```sql
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
```

### 5.2 stripe_subscription 表

> **设计说明**：不引入 `tenant_id`。订阅绑定的是用户，不是租户。如需租户维度的账单分析，通过 user 表关联查询即可。

```sql
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
```

> **注意**：`user_plan_history` 表已存在（schema.sql 中定义），无需新建。

## 六、配置设计

### 6.1 application.yaml

```yaml
stripe:
  secret-key: ${STRIPE_SECRET_KEY:sk_test_placeholder}
  webhook-secret: ${STRIPE_WEBHOOK_SECRET:whsec_placeholder}
  success-url: ${STRIPE_SUCCESS_URL:http://localhost:3000/subscription/success}
  cancel-url: ${STRIPE_CANCEL_URL:http://localhost:3000/subscription/cancel}
  prices:
    pro:
      monthly-price-id: ${STRIPE_PRO_MONTHLY_PRICE_ID:price_pro_monthly}
      yearly-price-id: ${STRIPE_PRO_YEARLY_PRICE_ID:price_pro_yearly}
    max:
      monthly-price-id: ${STRIPE_MAX_MONTHLY_PRICE_ID:price_max_monthly}
      yearly-price-id: ${STRIPE_MAX_YEARLY_PRICE_ID:price_max_yearly}
```

### 6.2 StripeProperties

```java
@Component
@ConfigurationProperties(prefix = "stripe")
@Getter
@Setter
public class StripeProperties {
    private String secretKey;
    private String webhookSecret;
    private String successUrl;
    private String cancelUrl;
    private Map<String, PlanPrices> prices; // key: "pro", "max"
}

@Getter
@Setter
class PlanPrices {
    private String monthlyPriceId;
    private String yearlyPriceId;
}
```

> **规范对齐**：使用 `@Getter` + `@Setter` 而非 `@Data`，与项目中 `TranslationLimitProperties` 保持一致。

### 6.3 安全说明：success-url / cancel-url

这两个 URL **不从请求参数传入**，而是直接从 `StripeProperties` 配置中读取。用户端调用 `/subscription/checkout` 时只需传 `plan` 和 `billingCycle`，无法控制跳转地址，防止钓鱼攻击。

## 七、API 端点设计

### 7.1 用户端 API（需认证）

| 方法 | 路径 | 说明 | 请求体 | 响应 |
|------|------|------|--------|------|
| POST | `/subscription/checkout` | 创建支付会话 | `{plan, billingCycle}` | `{checkoutUrl}` |
| GET | `/subscription/status` | 获取订阅状态 | - | `{plan, status, periodEnd, cancelAtPeriodEnd}` |
| POST | `/subscription/cancel` | 取消订阅 | - | `{status, cancelAtPeriodEnd}` |
| POST | `/subscription/portal` | 跳转账单管理 | - | `{portalUrl}` |
| GET | `/subscription/history` | 套餐变更历史 | - | `PageResponse<PlanHistory>` |

### 7.2 Webhook（无需认证）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/webhook/stripe` | 接收 Stripe Webhook 事件 |

## 八、核心业务逻辑

### 8.1 创建 Checkout Session

```
输入: userId, plan (SubscriptionPlan.PRO/MAX), billingCycle (BillingCycle.MONTHLY/YEARLY)
流程:
1. 查询 stripe_customer，不存在则调用 Stripe Customer.create() 创建
   - email 从 user 表读取
2. 从 StripeProperties 获取对应 plan + billingCycle 的 priceId
3. 调用 Stripe Session.create():
   - mode: "subscription"
   - customer: stripeCustomerId
   - lineItems: [{price: priceId, quantity: 1}]
   - metadata: {userId, plan, billingCycle}
   - successUrl, cancelUrl 从 StripeProperties 读取（非请求参数）
4. 返回 checkoutUrl

限流保护: 每用户每分钟最多创建 3 个 Session（基于 Redis 或内存计数器）
```

### 8.2 userLevel 同步策略

| 事件 | 操作 |
|------|------|
| checkout.session.completed | 首次订阅，user.userLevel = plan（PRO/MAX），写入 user_plan_history |
| customer.subscription.updated（升级/降级） | user.userLevel = 新 plan，写入 user_plan_history |
| customer.subscription.deleted | user.userLevel = "FREE"，写入 user_plan_history |
| invoice.payment_failed | 记录日志，标记 past_due（不立即降级，见 8.5 宽限期） |
| customer.subscription.updated（变为 trialing） | user.userLevel = plan（试用期间享受付费权益） |
| customer.subscription.updated（trialing → active） | 试用转正式，userLevel 不变 |
| customer.subscription.paused | userLevel 不变，标记 status = "paused"（暂停期间保留权益） |
| customer.subscription.resumed | status 恢复 "active" |

### 8.3 Webhook 幂等性

通过 `stripe_subscription.last_webhook_event_id` 字段控制：
1. Webhook 处理开始时，检查该 subscription 的 `last_webhook_event_id` 是否已等于当前 `event.getId()`
2. 已处理则直接返回 200
3. 处理成功后更新该字段

### 8.4 事件冲突处理：checkout.session.completed vs customer.subscription.created

Stripe 在 Checkout Session 完成后会**几乎同时**触发两个事件：
- `checkout.session.completed`
- `customer.subscription.created`

**处理策略**：

| 事件 | 职责 | 重复创建防护 |
|------|------|-------------|
| `checkout.session.completed` | **创建首次订阅记录**（主力事件） | 以 `stripe_subscription_id` 为唯一键，INSERT IGNORE / ON DUPLICATE KEY UPDATE |
| `customer.subscription.created` | **不处理** | 直接返回 200，不做任何操作 |
| `customer.subscription.updated` | 后续订阅变更（升级/降级/暂停） | 按 stripe_subscription_id 更新 |
| `customer.subscription.deleted` | 订阅取消/过期 | 按 stripe_subscription_id 更新 |

> **核心原则**：首次订阅只由 `checkout.session.completed` 处理，`customer.subscription.created` 被忽略以避免重复。

### 8.5 宽限期设计

当 `invoice.payment_failed` 时：
1. 标记 `stripe_subscription.status = "past_due"`
2. **不立即降级** userLevel
3. 宽限期：**7 天**（可配置）
4. 宽限期内用户仍享受 PRO 级别服务
5. 超过宽限期仍未支付 → 收到 `customer.subscription.deleted` 事件 → userLevel 降为 "FREE"

### 8.6 多租户 Webhook 处理

Webhook 来自外部系统，**没有 tenant context**。处理流程如下：

```
POST /webhook/stripe
  |
  +-- 1. 验证 Stripe-Signature 头
  |
  +-- 2. 解析 Event
  |
  +-- 3. 从 metadata 或 subscription 对象中获取 userId
  |
  +-- 4. 查询 user 表获取 tenantId:
  |     Long tenantId = userMapper.selectById(userId).getTenantId();
  |
  +-- 5. 设置租户上下文:
  |     try {
  |         TenantContext.setTenantId(tenantId);
  |         // 处理业务逻辑（创建/更新订阅、更新 userLevel 等）
  |     } finally {
  |         TenantContext.clear();  // 确保清理（对应 TenantCleanupInterceptor 设计）
  |     }
  |
  +-- 6. 返回 200（Stripe 要求 3 秒内响应）
```

> **异步优化**：若业务逻辑耗时超过 3 秒，使用 `ApplicationEventPublisher` 将事件异步分发：Webhook Controller 仅做签名验证 + 事件发布，`@EventListener` 异步处理数据库操作。

## 九、Webhook 事件处理流程

```
POST /webhook/stripe
  |
  +-- 1. 验证 Stripe-Signature 头（Webhook.constructEvent）
  |      失败 -> 400
  |
  +-- 2. 解析 Event，获取 event.getId() 和 event.getType()
  |
  +-- 3. 从 metadata 获取 userId
  |
  +-- 4. 查询 user 表获取 tenantId，设置 TenantContext（见 8.6）
  |
  +-- 5. 根据 type 分发:
  |
  +-- checkout.session.completed          ← 首次订阅（主力）
  |     -> 提取 metadata(userId, plan, billingCycle)
  |     -> 展开 subscription 对象
  |     -> 创建/获取 stripe_customer
  |     -> INSERT IGNORE stripe_subscription (幂等，UK: stripe_subscription_id)
  |     -> 更新 user.userLevel
  |     -> 记录 user_plan_history
  |     -> 更新 last_webhook_event_id
  |
  +-- customer.subscription.created       ← 忽略，避免与 checkout.session.completed 冲突
  |     -> 直接返回 200，不做任何操作
  |
  +-- customer.subscription.updated       ← 后续变更（升级/降级/暂停/试用）
  |     -> 更新 stripe_subscription 状态字段
  |     -> 如 status 变为 active/non-active，同步 userLevel
  |     -> 如变为 paused，保留 userLevel 权益
  |     -> 记录 user_plan_history
  |     -> 更新 last_webhook_event_id
  |
  +-- customer.subscription.deleted       ← 取消/过期
  |     -> 标记 canceled
  |     -> userLevel -> "FREE"
  |     -> 记录 user_plan_history
  |     -> 更新 last_webhook_event_id
  |
  +-- customer.subscription.resumed       ← 暂停后恢复
  |     -> status 恢复 "active"
  |     -> 更新 last_webhook_event_id
  |
  +-- invoice.payment_succeeded
  |     -> 更新 last_webhook_event_id
  |
  +-- invoice.payment_failed
  |     -> 记录日志，标记 status = "past_due"
  |     -> 不立即降级，进入 7 天宽限期（见 8.5）
  |
  +-- 未知事件
        -> 记录日志，返回 200
```

## 十、实体类设计规范

遵循项目现有规范（参考 `User.java`）：

```java
@Data
@TableName("stripe_customer")
public class StripeCustomer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String stripeCustomerId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}

@Data
@TableName("stripe_subscription")
public class StripeSubscription {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String stripeCustomerId;
    private String stripeSubscriptionId;
    private String plan;
    private String status;
    private String stripePriceId;
    private String billingCycle;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private Boolean cancelAtPeriodEnd;
    private LocalDateTime canceledAt;
    private String lastWebhookEventId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
```

> **注意**：不包含 `tenantId` 字段（见 5.1、5.2 设计说明）。

## 十一、错误码设计

沿用项目现有错误码编号规范，新增 `P0xx` 前缀表示 Payment/订阅相关错误：

| 错误码 | 含义 |
|--------|------|
| `P001` | 订阅不存在 |
| `P002` | 无效的套餐类型 |
| `P003` | 当前已有活跃订阅 |
| `P004` | Stripe 创建客户失败 |
| `P005` | 创建 Checkout Session 失败 |
| `P006` | 创建 Portal Session 失败 |
| `P007` | 无效的计费周期 |

## 十二、安全注意事项

1. **Webhook 路径白名单**: 在 `SecurityPermitAllPaths.java` 中添加 `"/webhook/stripe"`
2. **签名验证**: Webhook Controller 必须使用 `Webhook.constructEvent(payload, sigHeader, webhookSecret)` 验证
3. **Stripe Secret Key**: 通过环境变量 `STRIPE_SECRET_KEY` 注入，不硬编码
4. **Webhook Secret**: 通过环境变量 `STRIPE_WEBHOOK_SECRET` 注入
5. **测试模式**: 使用 `sk_test_*` 和 `whsec_test_*` 前缀的 key
6. **URL 安全**: success-url / cancel-url 从配置读取，**不接受请求参数**，防止钓鱼
7. **Checkout 限流**: `/subscription/checkout` 接口每用户每分钟最多 3 次请求

## 十三、本地测试方法

使用 Stripe CLI 转发 webhook 到本地：

```bash
# 登录 Stripe CLI
stripe login

# 转发 webhook
stripe listen --forward-to localhost:8080/webhook/stripe

# 触发测试事件
stripe trigger checkout.session.completed
```

## 十四、实施顺序

| 阶段 | 内容 |
|------|------|
| Phase 1 | pom.xml 添加 stripe-java 依赖，application.yaml 配置 |
| Phase 2 | 数据库迁移 SQL（V5.0）+ BillingCycle / SubscriptionPlan 枚举 |
| Phase 3 | StripeProperties + StripeConfig |
| Phase 4 | Entity + Mapper（StripeCustomer, StripeSubscription） |
| Phase 5 | SubscriptionService 核心逻辑 |
| Phase 6 | StripeWebhookController（签名验证 + 事件分发 + 多租户上下文） |
| Phase 7 | SubscriptionController（用户端 API + Checkout 限流） |
| Phase 8 | SecurityPermitAllPaths 白名单 + ErrorCodeEnum |
| Phase 9 | 本地测试（Stripe CLI） |

## 十五、潜在挑战及应对

| 挑战 | 应对方案 |
|------|----------|
| **Webhook 顺序**: Stripe 不保证事件顺序 | 依赖 `last_webhook_event_id` 幂等控制 + 以最新事件状态为准 |
| **多租户 Webhook 无 tenant context** | 通过 userId → user 表查询 tenantId → TenantContext.setTenantId()（见 8.6） |
| **Webhook 超时**: Stripe 要求 3 秒内响应 | 复杂处理使用 `ApplicationEventPublisher` 异步分发，Controller 仅做验证 + 发布 |
| **checkout.session.completed 与 customer.subscription.created 冲突** | 仅处理 checkout.session.completed 创建订阅，忽略 customer.subscription.created（见 8.4） |
| **同一用户多设备并发支付** | 以 `stripe_subscription_id` 唯一键 + INSERT IGNORE 保证幂等 |
| **payment_failed 后用户权益** | 7 天宽限期，宽限期内不降级（见 8.5） |
