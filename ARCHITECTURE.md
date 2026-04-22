# 架构说明文档 (ARCHITECTURE.md)

本文档描述 NovelTranslator 系统的架构设计、组件职责、数据流和关键技术决策。

## 目录

- [系统概览](#系统概览)
- [组件职责](#组件职责)
- [数据流图](#数据流图)
- [翻译管线架构](#翻译管线架构)
- [4 级缓存架构](#4-级缓存架构)
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
┌──────────┐                          │                                    │
│ Web App  │                          │                                    │
│ React+TS │──────────────────────────┘                                    │
└──────────┘              ┌─────────┴───────┐                              │
                          │  MySQL 8.0      │                              │
                          │  Redis 7        │                              │
                          │  Caffeine       │                              │
                          └─────────────────┘                              │
└──────────────────────────────────────────────────────────────────────────┘
```

### 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 前端 | Chrome Extension (Manifest V3) + React + TypeScript + Vite | - |
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

### 2. Web 管理端 (`web-app/`)

**职责**: React + TypeScript + Vite 构建的 Web 翻译界面，提供 DeepL 风格的翻译体验。

- 文本翻译、文档上传翻译、翻译历史记录
- 用户登录、配额查询、术语库管理
- 通过 Nginx 网关与后端 API 通信

### 3. Nginx 网关 (`nginx/`)

**职责**: 反向代理、静态文件服务、CORS 处理。

- 监听端口 7341
- 将 `/v1/**` 请求反向代理到后端 `http://backend:8080`
- 将 `/` 请求指向静态文件（Web 管理端构建产物）
- 配置 CORS 头，允许扩展访问

### 4. Spring Boot 后端 (`src/main/java/`)

**职责**: REST API 服务、业务逻辑、认证授权、缓存管理、翻译管线协调。

#### 4.1 Controller 层

| Controller 包 | 职责 |
|---------------|------|
| `controller/web/` | Web 管理端 API（用户、文档、术语、翻译） |
| `controller/plugin/` | 浏览器扩展 API（翻译端点） |
| `controller/external/` | 外部集成 API |
| `controller/shared/` | 共享翻译 API（文本翻译、文档翻译、RAG、任务管理） |

#### 4.2 Service 层

| Service | 职责 |
|---------|------|
| `TranslationService` | 核心翻译逻辑、SSE 流式响应、协调 TranslationPipeline |
| `TranslationTaskService` | 异步文档翻译任务管理、SSE 流式文档翻译 |
| `MultiAgentTranslationService` | 多 Agent 协作翻译（基于协作项目章节） |
| `TranslationPipeline` | **统一翻译管线**：封装四级管线逻辑（缓存 → RAG → 实体一致性 → 直译） |
| `TranslationCacheService` | Caffeine (L1) + Redis (L2) 缓存管理 |
| `RagTranslationService` | RAG 语义检索翻译记忆 |
| `EntityConsistencyService` | 实体一致性翻译（术语表 + 占位符保护） |
| `TranslationPostProcessingService` | 翻译后处理（残留中文检测与修正） |
| `UserLevelThrottledTranslationClient` | 基于用户等级的翻译限流客户端 |
| `UserService` | 用户 CRUD、密码管理、权限检查 |
| `ExternalTranslationService` | 外部翻译引擎调用协调 |
| `QuotaService` | 字符配额管理、档位检查 |

#### 4.3 翻译管线 (`service/pipeline/`)

`TranslationPipeline` 是统一的翻译管线组件，消除了此前三个 Service 类中重复的管线逻辑。

```
┌─────────────────────────────────────────────────────────────┐
│                    TranslationPipeline                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  输入文本                                                     │
│    │                                                         │
│    ▼                                                         │
│  L1: 三级缓存查询 (Caffeine → Redis → MySQL)                  │
│    │ 命中 → 返回                                              │
│    ▼ 未命中                                                   │
│  L2: RAG 语义匹配 (Redis HNSW 向量检索)                       │
│    │ 直接命中 → 后处理 → 缓存 → 返回                           │
│    ▼ 未命中                                                   │
│  L3: 实体一致性翻译 (条件触发: userId != null && 文本超阈值)    │
│    │ 提取实体 → 术语合并 → 占位符 → 翻译 → 实体恢复             │
│    │ 后处理 → 缓存 → 返回                                     │
│    ▼ 未触发/失败                                              │
│  L4: 直译 (Python/MTranServer 轮询)                           │
│    │ 质量校验 (广告关键词检测、长度异常检测)                     │
│    │ 后处理 → 缓存 → TranslationMemory 存储 → 返回             │
│                                                              │
│  executeFast(): 仅 L1 + L4，跳过 RAG 和一致性                  │
└─────────────────────────────────────────────────────────────┘
```

| 方法 | 说明 |
|------|------|
| `execute(text, targetLang, engine)` | 完整四级管线 |
| `executeFast(text, targetLang, engine)` | 快速模式（仅缓存 + 直译） |
| `shouldCache(original, translated)` | 静态方法，判断是否应缓存 |
| `isValidTranslation(text, result)` | 静态方法，校验翻译质量 |

#### 4.4 配置类

| Config | 职责 |
|--------|------|
| `SecurityConfig` | Spring Security 配置，JWT 过滤器链 |
| `RedisConfig` | Redis 连接池、序列化配置 |
| `RedisVectorConfig` | Redis 向量索引配置 |
| `TranslationExecutorConfig` | 虚拟线程执行器配置 |
| `TranslationLimitProperties` | 翻译限流配置属性绑定 |
| `SecurityPermitAllPaths` | 允许匿名访问的路径白名单 |
| `MyMetaObjectHandler` | MyBatis-Plus 自动填充（创建时间、更新时间） |

#### 4.5 安全模块

| 组件 | 职责 |
|------|------|
| `SecurityConfig` | Spring Security 过滤器链配置，翻译端点强制认证 |
| `JwtAuthenticationFilter` | JWT Token 解析和认证过滤器，无效 Token 返回 401 |
| `JwtAuthenticationEntryPoint` | 未认证请求的 401 响应 |
| `ApiKeyAuthenticationFilter` | API Key (`nt_sk_xxxx`) 认证过滤器 |
| `CustomUserDetails` | Spring Security UserDetails 实现 |
| `SecurityPermitAllPaths` | 集中管理白名单路径，供 SecurityConfig 和 JwtAuthenticationFilter 共享 |
| `ProjectAccessAspect` | `@RequireProjectAccess` 注解的 AOP 权限检查 |

#### 4.6 工具类

| Util | 职责 |
|------|------|
| `CacheKeyUtil` | 统一的缓存 Key 生成策略 |
| `SseEmitterUtil` | SSE 事件构建和序列化 |
| `TextCleaningUtil` | 翻译前后文本清洗 |
| `TextSegmentationUtil` | 长文本分段策略 |
| `EmailVerificationCodeUtil` | 邮箱验证码生成与校验 |
| `SecurityUtil` | 当前用户上下文获取 |
| `ExternalResponseUtil` | 外部翻译服务响应解析 + 翻译文件路径构建 |
| `JwtUtils` | JWT Token 生成与验证 |
| `PasswordUtil` | BCrypt 密码加密 |

### 5. 翻译微服务 (`services/translate-engine/`)

**职责**: LLM 翻译引擎调用、回退链管理。

- 基于 FastAPI 构建
- 集成 OpenAI SDK，支持任何 OpenAI 兼容的 API
- 支持 DeepSeek、OpenAI GPT、Claude（兼容层）、Ollama 等
- 健康检查端点 `/health`

### 6. MTranServer (`docker-compose.yml` 中的 `mtran-server`)

**职责**: 轻量级本地翻译服务，作为 LLM 引擎的降级方案。

- 基于 `xxnuo/mtranserver` Docker 镜像
- 端口 8989
- 适用于不需要 LLM 语义理解的直译场景

---

## 数据流图

### 翻译请求完整数据流

```
用户操作 (Chrome Extension / Web App)
    │
    ▼
┌─────────────────────────────────────────────────┐
│ 1. DOM 分析 / 文本输入                             │
│    - 遍历页面 DOM 树                              │
│    - 提取可翻译文本节点                           │
│    - 生成映射表 {textId, original, context}       │
└──────────────────────┬──────────────────────────┘
                       │ HTTP POST /v1/translate/** + Authorization
                       ▼
┌─────────────────────────────────────────────────┐
│ 2. Nginx 反向代理                                 │
│    - 路由到 http://backend:8080                  │
└──────────────────────┬──────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────┐
│ 3. Spring Boot - 安全过滤器链                     │
│    - JwtAuthenticationFilter 验证 JWT Token      │
│    - Token 无效 → 401 响应                       │
│    - ApiKeyAuthenticationFilter 验证 API Key     │
│    - 白名单路径（/health, /user/login）跳过认证    │
└──────────────────────┬──────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────┐
│ 4. Spring Boot - Controller 层                   │
│    - 参数校验 (@Validated)                        │
│    - 配额检查 (QuotaService)                      │
│    - 创建 SseEmitter 用于流式响应                │
└──────────────────────┬──────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────┐
│ 5. Spring Boot - TranslationPipeline             │
│    ┌───────────────────────────────────────┐     │
│    │ L1: 三级缓存查询                       │     │
│    │   Caffeine → Redis → MySQL            │     │
│    │   缓存命中 -> 直接返回                  │     │
│    └───────────────────────────────────────┘     │
│    ┌───────────────────────────────────────┐     │
│    │ L2: RAG 语义匹配                      │     │
│    │   Redis HNSW KNN 搜索                 │     │
│    │   直接命中 -> 后处理 -> 缓存 -> 返回    │     │
│    └───────────────────────────────────────┘     │
│    ┌───────────────────────────────────────┐     │
│    │ L3: 实体一致性翻译 (条件触发)           │     │
│    │   术语表 + 占位符保护                  │     │
│    └───────────────────────────────────────┘     │
│    ┌───────────────────────────────────────┐     │
│    │ L4: 直译                              │     │
│    │   Python / MTranServer 轮询           │     │
│    │   质量校验 + 后处理 + 缓存             │     │
│    └───────────────────────────────────────┘     │
└──────────────────────┬──────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────┐
│ 6. External Translation Service                  │
│    - Python FastAPI (:8000) 或 MTranServer (:8989) │
│    - 调用 OpenAI-compatible API                  │
│    - 应用小说翻译 System Prompt                  │
│    - 返回翻译结果                                │
└──────────────────────┬──────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────┐
│ 7. 翻译结果返回                                   │
│    - SSE 事件: {textId, original, translation}  │
│    - 完成标记: [DONE]                            │
│    - 错误标记: ERROR: message                    │
└──────────────────────┬──────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────┐
│ 8. 前端渲染                                       │
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
    ├── Token 有效
    │   ├── 验证签名和过期时间
    │   ├── 提取用户信息
    │   └── 设置 SecurityContext → 放行
    │
    ├── Token 无效/过期
    │   └── 返回 401 JSON 响应 { "code": 401, "message": "..." }
    │
    └── 无 Token + 需要认证的路径
        └── Spring Security 返回 401
```

---

## 翻译管线架构

### 统一管线设计

此前，翻译管线逻辑在三个 Service 类中重复实现。现已提取为统一的 `TranslationPipeline` 组件。

```
                    ┌─────────────────────┐
                    │  TranslationService │
                    │  TranslationTaskSvc │
                    │  MultiAgentSvc      │
                    └──────────┬──────────┘
                               │ 创建 Pipeline 实例
                               ▼
                    ┌─────────────────────┐
                    │ TranslationPipeline  │
                    │ (唯一管线实现)        │
                    └──────────┬──────────┘
                               │
                  ┌────────────┼────────────┐
                  │            │            │
                  ▼            ▼            ▼
            ┌──────────┐ ┌─────────┐ ┌──────────┐
            │ L1: 缓存  │ │ L2: RAG │ │ L3: 一致性│
            │ L4: 直译  │ │ 后处理  │ │ L4: 直译  │
            │ 质量校验  │ │ 缓存    │ │ 后处理    │
            │ 后处理    │ │         │ │ 缓存      │
            │ 缓存      │ │         │ │ Memory    │
            └──────────┘ └─────────┘ └──────────┘
```

### 管线复用对比

| 代码模式 | 重构前 | 重构后 |
|----------|--------|--------|
| 四级管线实现 | 3 处重复 | `TranslationPipeline.execute()` |
| `shouldCache` 方法 | 3 份拷贝 | `TranslationPipeline.shouldCache()` 静态方法 |
| `isValidTranslation` 方法 | 仅 TranslationService 有 | `TranslationPipeline.isValidTranslation()` 静态方法 |
| `extractTranslatedContent` | 2 份拷贝 | `ExternalResponseUtil.extractDataField()` |
| `buildTranslatedPath` | 2 份拷贝 | `ExternalResponseUtil.buildTranslatedPath()` |
| 后处理集成 | 仅 2/3 Service 有 | 所有路径统一包含 |

---

## 4 级缓存架构

### 架构设计

```
  请求
   │
   ▼
┌─────────┐     未命中      ┌─────────┐     未命中      ┌─────────┐     未命中      ┌─────────┐
│ L1:     │ ──────────────▶ │ L2:     │ ──────────────▶ │ L3:     │ ──────────────▶ │ L4:     │
│ Caffeine│                 │ Redis   │                 │ MySQL   │                 │ RAG     │
│ (本地)  │ ◀────────────── │ (分布式)│ ◀────────────── │ (持久化)│ ◀────────────── │ (语义)  │
└─────────┘   回填          └─────────┘   回填          └─────────┘   回填          └─────────┘
     │ 10 min                 │ 30 min                 │ 24h                  │ 永久
     ▼                        ▼                        ▼                      ▼
```

### 缓存穿透防护

- **问题**: 查询不存在的数据，缓存永远未命中，请求直达数据库
- **方案**: 对 null 结果也缓存，设置较短 TTL（如 1 分钟）

### 缓存击穿防护

- **问题**: 热点 Key 过期瞬间，大量并发请求同时到达数据库
- **方案**: 使用 `ConcurrentHashMap` 对每个缓存 Key 加锁，仅允许一个线程查询数据库

### 缓存雪崩防护

- **问题**: 大量缓存在同一时间过期，导致数据库瞬时压力激增
- **方案**: TTL 添加随机抖动值（如 `baseTTL + random(0, jitter)`）

---

## 翻译引擎架构

### 双引擎设计

```
                    ┌─────────────────────┐
                    │  TranslationPipeline│
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
│            │   (/user/login, /user/register,     │
│            │    /health, /actuator, /swagger)    │
│            │                                     │
│            └── 其他路径 ──▶ 需要认证              │
│                │                                 │
│                ├── JwtAuthenticationFilter       │
│                │   ├── Token 有效 → 放行          │
│                │   └── Token 无效 → 401          │
│                │                                 │
│                └── ApiKeyAuthenticationFilter    │
│                    ├── Key 有效 → 放行            │
│                    └── Key 无效 → 401            │
│                                                  │
│  所有 /v1/translate/** 端点必须认证               │
│                                                  │
└─────────────────────────────────────────────────┘
```

### 白名单路径

| 路径 | 说明 |
|------|------|
| `/user/login` | 用户登录 |
| `/user/register` | 用户注册 |
| `/user/send-code` | 发送验证码 |
| `/user/send-reset-code` | 发送重置密码验证码 |
| `/user/reset-password` | 重置密码 |
| `/user/get-token` | 获取 Token |
| `/health` | 健康检查 |
| `/actuator` | Spring Actuator 监控 |
| `/swagger-ui` | API 文档 |
| `/v3/api-docs` | OpenAPI 规范 |

### 限流策略

| 用户类型 | 最大并发数 |
|---------|-----------|
| 匿名用户 | 3 |
| 免费用户 | 5 |
| 专业用户 | 20 |

- 使用 `Semaphore` 实现每用户并发限制
- 使用 Redis 滑动窗口实现每日配额计数

### 数据安全

- 密码: BCrypt 加密存储，不可逆
- JWT Secret: 仅通过环境变量注入，不硬编码
- 数据库凭证: 通过环境变量配置，不写入配置文件
- 邮箱验证: 6 位随机验证码，1 分钟有效
- **翻译端点强制认证**: 所有 `/v1/translate/**` 需要有效 JWT 或 API Key

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
| `user` | 用户信息 | id, email, password, role, status, user_level |
| `glossary` | 术语库 | id, user_id, source_word, target_word, remark |
| `translation_cache` | 翻译缓存 | id, source_text, target_text, source_lang, target_lang, engine |
| `translation_history` | 翻译历史 | id, user_id, source_text, translated_text, engine, time_cost |
| `translation_task` | 翻译任务 | id, user_id, file_name, status, progress, result |
| `translation_memory` | RAG 翻译记忆 | id, user_id, source_text, embedding_vector, target_text |
| `document` | 文档管理 | id, user_id, name, path, file_type, status, task_id |
| `user_preference` | 用户偏好 | id, user_id, preferred_engine, target_language |
| `quota_usage` | 配额使用 | id, user_id, chars_used, date, mode |
| `api_key` | API Key 管理 | id, user_id, key_hash, name, created_at |

### 数据库 ER 关系

```
user (1) ──── (N) glossary
user (1) ──── (N) translation_history
user (1) ──── (N) translation_task
user (1) ──── (1) user_preference
user (1) ──── (N) quota_usage
user (1) ──── (N) api_key
user (1) ──── (N) document
document (1) ──── (1) translation_task
user (1) ──── (N) translation_memory
```

### 索引设计

- `user`: `email` (UNIQUE) - 登录查询
- `glossary`: `user_id` - 按用户查询术语库
- `translation_cache`: 复合索引 (source_text, source_lang, target_lang) - 缓存命中查询
- `translation_history`: `user_id` - 按用户查询历史
- `translation_memory`: `user_id`, `target_lang` - RAG KNN 检索过滤
- `quota_usage`: `user_id`, `date` - 配额统计

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

### 4. 为什么设计 4 级缓存？

- L1 Caffeine: 本地缓存，零网络延迟，适合热点数据
- L2 Redis: 分布式缓存，多实例共享，避免缓存不一致
- L3 MySQL: 持久化存储，兜底方案，支持长时间缓存
- L4 RAG: 语义相似度匹配，即使原文不完全相同也能找到相似翻译

### 5. 为什么统一翻译管线？

- 重构前四级管线逻辑在 3 个 Service 中重复实现，维护成本高
- 提取为 `TranslationPipeline` 后，所有翻译路径调用同一组件，确保行为一致
- 新增后处理阶段（残留中文修正）只需修改一处
