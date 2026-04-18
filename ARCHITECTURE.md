# 架构说明文档 (ARCHITECTURE.md)

本文档详细描述 NovelTranslator 系统的架构设计、组件职责、数据流和关键技术决策。

## 目录

- [系统概览](#系统概览)
- [组件职责](#组件职责)
- [数据流图](#数据流图)
- [3 级缓存架构](#3-级缓存架构)
- [翻译引擎架构](#翻译引擎架构)
- [安全架构](#安全架构)
- [部署架构](#部署架构)
- [数据库设计](#数据库设计)

---

## 系统概览

NovelTranslator 是一个全栈双语小说翻译系统，由以下核心组件构成：

```
┌──────────────────────────────────────────────────────────────────────────┐
│                          NovelTranslator 系统架构                          │
├──────────┐    ┌──────────┐    ┌────────────┐    ┌──────────────┐         │
│ Chrome   │───▶│  Nginx   │───▶│ Spring Boot│───▶│ Translation  │         │
│ Extension│    │ :7341    │    │  Backend   │    │   Engines    │         │
│          │    │          │    │  :8080     │    │  :8000/:8989 │         │
└──────────┘    └──────────┘    └─────┬──────┘    └──────────────┘         │
                                      │                                    │
                              ┌───────┴───────┐                            │
                              │  MySQL 8.0    │                            │
                              │  Redis 7      │                            │
                              │  Caffeine     │                            │
                              └───────────────┘                            │
└──────────────────────────────────────────────────────────────────────────┘
```

### 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 前端 | Chrome Extension (Manifest V3) + Vanilla JS | - |
| 网关 | Nginx | 1.28 |
| 后端 | Spring Boot 3.2.0 + Undertow | Java 21 |
| ORM | MyBatis-Plus | 3.5.5 |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis + Caffeine | Redis 7 |
| 微服务 | FastAPI + OpenAI SDK | Python 3.11+ |
| 安全 | Spring Security + JWT (auth0-jwt) | - |
| 构建 | Maven + Docker Compose | Maven 3.9 |

---

## 组件职责

### 1. Chrome Extension (`extension/`)

**职责**: 浏览器端翻译入口，负责 DOM 分析、文本提取、翻译结果渲染。

| 模块 | 职责 |
|------|------|
| `background/` | Service Worker，消息路由、API 调用协调 |
| `content/` | Content Script，DOM 遍历、文本节点映射、翻译结果注入 |
| `popup/` | 扩展弹窗 UI，引擎选择、语言配置 |
| `options/` | 设置页面，API Key 管理、偏好配置 |
| `lib/` | 第三方库：Readability（文章提取）、DOMPurify（HTML 净化） |

**三种翻译模式**:

- **全页翻译** (`/v1/translate/webpage`): DOM 遍历生成 textId 映射表，批量发送至后端，SSE 流式接收翻译结果并逐节点更新
- **阅读模式** (`/v1/translate/reader`): 使用 Readability 提取文章 HTML，翻译后在纯净阅读界面展示
- **选词翻译** (`/v1/translate/selection`): 监听用户选中文本事件，调用翻译 API 后在浮窗中展示结果

### 2. Nginx 网关 (`nginx/`)

**职责**: 反向代理、静态文件服务、CORS 处理。

- 监听端口 7341
- 将 `/v1/**` 请求反向代理到后端 `http://backend:8080`
- 将 `/` 请求指向 `frontend/` 静态文件
- 配置 CORS 头，允许扩展访问

### 3. Spring Boot 后端 (`src/main/java/`)

**职责**: REST API 服务、业务逻辑、认证授权、缓存管理。

#### 3.1 Controller 层

| Controller | 职责 |
|------------|------|
| `TranslateController` | 三种翻译模式 API + 文本翻译 + 文档翻译 |
| `UserController` | 用户注册/登录、术语库管理、偏好设置、统计信息 |
| `VerificationController` | 邮箱验证码发送 |
| `DocumentController` | 文档翻译任务管理 |
| `HomeController` | 前端页面渲染（Thymeleaf） |
| `GlobalExceptionHandler` | 全局异常处理，统一错误响应格式 |

#### 3.2 Service 层

| Service | 职责 |
|---------|------|
| `TranslationService` | 核心翻译逻辑、SSE 流式响应、3 级缓存协调 |
| `ExternalTranslationService` | 调用外部翻译引擎（Python 微服务 / MTranServer） |
| `TranslationCacheService` | Caffeine (L1) + Redis (L2) 缓存管理 |
| `TranslationTaskService` | 异步文档翻译任务管理 |
| `UserService` | 用户 CRUD、密码管理、权限检查 |
| `DeviceTokenService` | 设备令牌管理（用于多端登录） |
| `UserLevelThrottledTranslationClient` | 基于用户等级的翻译限流 |

#### 3.3 配置类

| Config | 职责 |
|--------|------|
| `SecurityConfig` | Spring Security 配置，JWT 过滤器链 |
| `RedisConfig` | Redis 连接池、序列化配置 |
| `TranslationExecutorConfig` | 虚拟线程执行器配置 |
| `TranslationLimitProperties` | 翻译限流配置属性绑定 |
| `SecurityPermitAllPaths` | 允许匿名访问的路径白名单 |
| `MyMetaObjectHandler` | MyBatis-Plus 自动填充（创建时间、更新时间） |

#### 3.4 安全模块

| 组件 | 职责 |
|------|------|
| `JwtAuthenticationFilter` | JWT Token 解析和认证过滤器 |
| `JwtAuthenticationEntryPoint` | 未认证请求的 401 响应 |
| `CustomUserDetails` | Spring Security UserDetails 实现 |
| `JwtUtils` | JWT Token 生成与验证 |
| `PasswordUtil` | BCrypt 密码加密 |

#### 3.5 工具类

| Util | 职责 |
|------|------|
| `CacheKeyUtil` | 统一的缓存 Key 生成策略 |
| `SseEmitterUtil` | SSE 事件构建和序列化 |
| `TextCleaningUtil` | 翻译前后文本清洗 |
| `TextSegmentationUtil` | 长文本分段策略 |
| `EmailVerificationCodeUtil` | 邮箱验证码生成与校验 |
| `SecurityUtil` | 当前用户上下文获取 |
| `ExternalResponseUtil` | 外部翻译服务响应解析 |

### 4. 翻译微服务 (`services/translate-engine/`)

**职责**: LLM 翻译引擎调用、回退链管理。

- 基于 FastAPI 构建
- 集成 OpenAI SDK，支持任何 OpenAI 兼容的 API
- 支持 DeepSeek、OpenAI GPT、Claude（兼容层）、Ollama 等
- 健康检查端点 `/health`

### 5. MTranServer (`docker-compose.yml` 中的 `mtran-server`)

**职责**: 轻量级本地翻译服务，作为 LLM 引擎的降级方案。

- 基于 `xxnuo/mtranserver` Docker 镜像
- 端口 8989
- 适用于不需要 LLM 语义理解的直译场景

---

## 数据流图

### 翻译请求完整数据流

```
用户操作 (Chrome Extension)
    │
    ▼
┌─────────────────────────────────────────────────┐
│ 1. DOM 分析                                       │
│    - 遍历页面 DOM 树                              │
│    - 提取可翻译文本节点                           │
│    - 生成映射表 {textId, original, context}       │
└──────────────────────┬──────────────────────────┘
                       │ HTTP POST /v1/translate/webpage
                       ▼
┌─────────────────────────────────────────────────┐
│ 2. Nginx 反向代理                                 │
│    - 路由到 http://backend:8080                  │
└──────────────────────┬──────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────┐
│ 3. Spring Boot - Controller 层                   │
│    - 参数校验 (@Validated)                        │
│    - 用户限流检查 (Semaphore)                     │
│    - 创建 SseEmitter 用于流式响应                │
└──────────────────────┬──────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────┐
│ 4. Spring Boot - Service 层                      │
│    ┌───────────────────────────────────────┐     │
│    │ 4a. 缓存查询 (3 级)                    │     │
│    │   L1: Caffeine (10 min TTL)          │     │
│    │   L2: Redis (30 min TTL)             │     │
│    │   L3: MySQL (持久化, 24h)            │     │
│    │   缓存命中 -> 直接返回                 │     │
│    └───────────────────────────────────────┘     │
│    ┌───────────────────────────────────────┐     │
│    │ 4b. 未命中 -> 调用翻译引擎             │     │
│    │   - 虚拟线程异步执行                    │     │
│    │   - 选择引擎 (LLM / MTran)            │     │
│    │   - 文本分段 (避免 Token 超限)         │     │
│    └───────────────────────────────────────┘     │
│    ┌───────────────────────────────────────┐     │
│    │ 4c. 翻译结果 -> 写入缓存 -> 返回       │     │
│    └───────────────────────────────────────┘     │
└──────────────────────┬──────────────────────────┘
                       │ SSE 流式响应
                       ▼
┌─────────────────────────────────────────────────┐
│ 5. External Translation Service                  │
│    - Python FastAPI (:8000) 或 MTranServer (:8989) │
│    - 调用 OpenAI-compatible API                  │
│    - 应用小说翻译 System Prompt                  │
│    - 返回翻译结果                                │
└──────────────────────┬──────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────┐
│ 6. 翻译结果返回                                   │
│    - SSE 事件: {textId, original, translation}  │
│    - 完成标记: [DONE]                            │
│    - 错误标记: ERROR: message                    │
└──────────────────────┬──────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────┐
│ 7. Chrome Extension 渲染                         │
│    - 解析 SSE 事件                               │
│    - 根据 textId 定位 DOM 节点                   │
│    - 替换文本内容                                │
│    - 保持原始 DOM 结构和样式                     │
└─────────────────────────────────────────────────┘
```

### 用户认证流程

```
用户登录请求
    │
    ▼
UserController.login()
    │
    ▼
UserService.authenticate()
    │
    ├── 验证邮箱和密码 (BCrypt)
    │
    ▼
JwtUtils.generateToken()
    │
    ├── payload: userId, email, role
    ├── 签名: HMAC-SHA256 (JWT_SECRET)
    └── 过期: 30 天
    │
    ▼
返回 { token, refreshToken, user }
    │
    ▼
后续请求携带 Authorization: Bearer <token>
    │
    ▼
JwtAuthenticationFilter 拦截
    │
    ├── 验证 Token 签名和过期时间
    ├── 提取用户信息
    └── 设置 SecurityContext
```

---

## 3 级缓存架构

### 架构设计

```
  请求
   │
   ▼
┌─────────┐     未命中      ┌─────────┐     未命中      ┌─────────┐
│ L1:     │ ──────────────▶ │ L2:     │ ──────────────▶ │ L3:     │
│ Caffeine│                 │ Redis   │                 │ MySQL   │
│ (本地)  │ ◀────────────── │ (分布式)│ ◀────────────── │ (持久化)│
└─────────┘   回填          └─────────┘   回填          └─────────┘
     │ 10 min                 │ 30 min                 │ 24h
     ▼                        ▼                        ▼
```

### 缓存穿透防护

- **问题**: 查询不存在的数据，缓存永远未命中，请求直达数据库
- **方案**: 对 null 结果也缓存，设置较短 TTL（如 1 分钟）

### 缓存击穿防护

- **问题**: 热点 Key 过期瞬间，大量并发请求同时到达数据库
- **方案**: 使用 `synchronized(cacheKey.intern())` 对每个缓存 Key 加锁，仅允许一个线程查询数据库

### 缓存雪崩防护

- **问题**: 大量缓存在同一时间过期，导致数据库瞬时压力激增
- **方案**: TTL 添加随机抖动值（如 `baseTTL + random(0, jitter)`）

---

## 翻译引擎架构

### 双引擎设计

```
                    ┌─────────────────────┐
                    │  TranslationService │
                    │  (路由决策)          │
                    └──────────┬──────────┘
                               │
                  ┌────────────┼────────────┐
                  │                         │
                  ▼                         ▼
        ┌─────────────────┐     ┌─────────────────┐
        │ LLM 引擎         │     │ MTranServer     │
        │ (DeepSeek/GPT)  │     │ (轻量翻译)       │
        │ Port: 8000      │     │ Port: 8989      │
        │ 场景: 高质量翻译  │     │ 场景: 快速直译   │
        └─────────────────┘     └─────────────────┘
```

### 翻译 System Prompt

系统为小说翻译设计了 6 条翻译原则：

1. 准确理解原文含义
2. 保持原文的语言风格特征
3. 符合目标语言的表达习惯
4. 保留原文中的专有名词
5. 保持段落结构一致
6. 文学性文本需注意修辞和意境

### 文本分段策略

- 长文本按段落拆分，避免单次请求 Token 超限
- 每个段落独立翻译后拼接
- 使用虚拟线程异步执行，提升吞吐量

---

## 安全架构

### 认证与授权

```
┌─────────────────────────────────────────────────┐
│                  安全过滤器链                      │
├─────────────────────────────────────────────────┤
│                                                  │
│  请求 ──▶ SecurityFilterChain                    │
│            │                                     │
│            ├── 白名单路径 ──▶ 直接放行            │
│            │   (/v1/translate/**, /user/login,   │
│            │    /user/register, /health, /v1/**) │
│            │                                     │
│            └── 其他路径 ──▶ JwtAuthenticationFilter │
│                              │                   │
│                              ├── Token 有效       │
│                              │   ──▶ 设置         │
│                              │      SecurityCtx   │
│                              │                   │
│                              └── Token 无效/过期  │
│                                  ──▶ 401 响应     │
│                                                  │
└─────────────────────────────────────────────────┘
```

### 限流策略

| 用户类型 | 每日翻译限制 | 最大并发数 |
|---------|-------------|-----------|
| 匿名用户 | 无限制 | 3 |
| 免费用户 | 100 次/天 | 5 |
| 专业用户 | 1000 次/天 | 20 |

- 使用 `Semaphore` 实现每用户并发限制
- 使用 Redis 滑动窗口实现每日配额计数

### 数据安全

- 密码: BCrypt 加密存储，不可逆
- JWT Secret: 仅通过环境变量注入，不硬编码
- 数据库凭证: 通过环境变量配置，不写入配置文件
- 邮箱验证: 6 位随机验证码，1 分钟有效

---

## 部署架构

### Docker Compose 部署

```
┌─────────────────────────────────────────────────────┐
│                  Docker Network                      │
│                                                      │
│  ┌──────────┐    ┌──────────┐    ┌──────────────┐   │
│  │  Nginx   │───▶│ Backend  │───▶│ MTranServer  │   │
│  │  :7341   │    │  :8080   │    │  :8989       │   │
│  └────┬─────┘    └────┬─────┘    └──────────────┘   │
│       │               │                              │
│       │          ┌────┴─────┐    ┌──────────────┐   │
│       │          │ MySQL    │    │ LLM Engine   │   │
│       │          │ :3306    │    │ :8000        │   │
│       │          │ Redis    │    │              │   │
│       │          │ :6379    │    └──────────────┘   │
│       │          └──────────┘                        │
│       │                                              │
│  ┌────┴─────┐                                       │
│  │ Frontend │ (静态文件映射)                          │
│  └──────────┘                                       │
│                                                      │
└─────────────────────────────────────────────────────┘
```

### 端口映射

| 服务 | 容器端口 | 宿主机端口 | 说明 |
|------|---------|-----------|------|
| Nginx | 7341 | 7341 | 网关入口 |
| Backend | 8080 | 8080 | Spring Boot |
| MySQL | 3306 | 3307 | 数据库 |
| Redis | 6379 | 6379 | 缓存 |
| MTranServer | 8989 | 8989 | 轻量翻译引擎 |
| LLM Engine | 8000 | 8000 | LLM 翻译 |

---

## 数据库设计

### 核心表结构

| 表名 | 说明 | 关键字段 |
|------|------|----------|
| `user` | 用户信息 | id, email, password, role, status |
| `glossary` | 术语库 | id, user_id, source_word, target_word, remark |
| `translation_cache` | 翻译缓存 | id, source_text, target_text, source_lang, target_lang, engine |
| `translation_history` | 翻译历史 | id, user_id, source_text, translated_text, engine, time_cost |
| `translation_task` | 翻译任务 | id, user_id, file_name, status, progress, result |
| `user_preference` | 用户偏好 | id, user_id, preferred_engine, target_language, reading_mode |

### 数据库 ER 关系

```
user (1) ──── (N) glossary
user (1) ──── (N) translation_history
user (1) ──── (N) translation_task
user (1) ──── (1) user_preference
```

### 索引设计

- `user`: `email` (UNIQUE) - 登录查询
- `glossary`: `user_id` - 按用户查询术语库
- `translation_cache`: 复合索引 (source_text, source_lang, target_lang) - 缓存命中查询
- `translation_history`: `user_id` - 按用户查询历史

---

## 关键技术决策

### 1. 为什么使用 Undertow 而非 Tomcat？

- Undertow 基于 XNIO 的非阻塞 I/O，更适合高并发 SSE 场景
- 虚拟线程在 Undertow 上有更好的支持
- 内存占用更小，启动更快

### 2. 为什么使用 MyBatis-Plus 而非 JPA？

- 更灵活的 SQL 控制，适合复杂查询（如翻译历史的分页查询）
- 内置分页插件、乐观锁等常用功能
- 学习曲线低，适合快速开发

### 3. 为什么使用虚拟线程？

- Java 21 引入的 Project Loom，将阻塞 I/O 操作的线程开销降至最低
- 翻译请求涉及大量网络 I/O（调用外部 API），虚拟线程可将并发能力提升一个数量级
- 配合 Semaphore 实现精细的每用户限流

### 4. 为什么设计 3 级缓存？

- L1 Caffeine: 本地缓存，零网络延迟，适合热点数据
- L2 Redis: 分布式缓存，多实例共享，避免缓存不一致
- L3 MySQL: 持久化存储，兜底方案，支持长时间缓存
