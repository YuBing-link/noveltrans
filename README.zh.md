# NovelTrans

面向网文作者和译者的 SaaS 翻译平台 — 多引擎 AI 编排、RAG 翻译记忆、Stripe 订阅管理、团队协作与多租户数据隔离。

> [English README](README.md)

[![CI](https://github.com/YuBing-link/noveltrans/actions/workflows/ci.yml/badge.svg)](https://github.com/YuBing-link/noveltrans/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-green?logo=spring)](https://spring.io/)
[![Coverage](https://img.shields.io/badge/Coverage-80.5%25-brightgreen)](docs/coverage-report-summary.md)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

## 项目简介

NovelTrans 是一个全栈翻译平台，为网文作者和译者设计。它取代了传统的"复制粘贴到谷歌翻译"工作流，通过理解上下文、保持角色名一致性、并从历史翻译中学习 — 同时通过基于 RAG 的语义复用降低 LLM API 成本。

三个客户端共享同一个后端：
- **React Web 仪表盘** — 类 DeepL 界面，支持实时章节预览
- **Chrome 浏览器扩展 (MV3)** — 三种模式：全文翻译、阅读器模式、选段翻译
- **外部 REST API** — API Key 认证，供第三方集成使用

## 核心特性

- **多引擎 AI 编排** — 在 Python LLM 引擎（FastAPI + OpenAI SDK）和本地轻量引擎（MTranServer）之间做基于优秀率的概率负载均衡，60 秒滚动统计窗口；MTranServer 承担双重角色 — 快速翻译模式下提供即时结果，LLM 引擎降级时自动兜底；双向自动降级，调用方无感知
- **RAG 翻译记忆** — Redis HNSW 向量语义检索（OpenAI `text-embedding-3-small` 或 Ollama `bge-m3` 生成嵌入），相似度 ≥ 0.85 时跳过 LLM 调用，减少重复内容的 API 成本
- **实体一致性管线** — 翻译前提取专有名词 → SHA-256 占位符替换（`__ENT_<hash>__`）→ 对照用户术语表翻译实体 → 译后还原，防止"John"在章节间变成"Jon"
- **多智能体协作 (AgentScope)** — 每章三个 AI 角色（译者 + 术语专家 + 润色师），按小说类型匹配不同 prompt（已实现热血/悬疑/日常三类，可按需扩展）
- **四级翻译管线** — L1: Caffeine → Redis → MySQL 三级缓存；L2: RAG 语义搜索；L3: 实体提取→占位符替换→翻译→还原；L4: AI 引擎调用（含质量校验：广告词检测、长度异常检查、中文字符修复）。每级可短路
- **订阅商业化 (Stripe)** — Checkout + 计费门户 + Webhook + JWT 吊销；三档计划（FREE/PRO/MAX），每用户独立并发 Semaphore + 滑动窗口 TPM 限流 + 月度字数配额（Lua 脚本原子校验）
- **团队协作空间** — 多租户项目管理、章节分配、审核批准流程，`CollabStateMachine` 状态机 + CAS 乐观锁 + 指数退避自动重试
- **三级支付幂等** — Stripe Webhook 签名验签 → Redis SETNX + 24h TTL → MySQL 乐观锁（`atomicUpdateSubscription`），安全处理 Webhook 重投
- **弹性保障** — Resilience4j 断路器、用户级 Semaphore 并发控制、指数退避重试、Token 感知限流器、失败自动退款

## 系统架构

![架构图](docs/architecture.svg)

## 技术栈

| 层级 | 技术 |
|------|------|
| **后端** | Java 21, Spring Boot 3.2.0 (Undertow), 虚拟线程, WebFlux |
| **持久化** | MySQL 8.0, MyBatis-Plus 3.5.5, Flyway 迁移 |
| **安全** | Spring Security + JWT (auth0), BCrypt, API Key 认证 |
| **AI 编排** | AgentScope 1.0.1, TranslationPipeline, MultiAgentTranslationService |
| **向量检索** | Redis Stack 7.4 (HNSW), OpenAI `text-embedding-3-small` / Ollama `bge-m3` |
| **支付** | Stripe SDK 24.20.0 (Checkout, 计费门户, Webhook) |
| **缓存** | Caffeine L1 → Redis L2 → MySQL L3, Redis HNSW 向量索引, 缓存 PubSub |
| **弹性** | Resilience4j 2.2.0 断路器, 用户级 Semaphore, 指数退避重试 |
| **测试** | JUnit 5, Mockito, JaCoCo (80.5% 覆盖率), k6 压测 |
| **基础设施** | Docker Compose (6 容器), Nginx 1.28-alpine, GitHub Actions |
| **前端** | React 19 + TypeScript + Vite (Web), Chrome Extension MV3 |
| **Python 引擎** | FastAPI + OpenAI SDK (多智能体翻译微服务) |

## 关键技术决策

**严格六边形架构 + 应用层。**
领域层是纯 Java — 零框架注解、零 ORM 引用、零 HTTP 类型。所有基础设施访问通过端口接口。Stripe SDK 隔离在 `PaymentPort` 后，Redis 在 `CachePort` 和 `QuotaPort` 后，MySQL 在 `BillingRepositoryPort` 后。安全过滤器在 `SecurityConfig.filterChain()` 中用 `new` 实例化而非 `@Component`，避免 Spring Security CGLIB 代理排序冲突。

**两层用户级限流。**
并发控制：每个用户独立 `Semaphore`（FREE=1, PRO=3, MAX=5）。吞吐控制：滑动窗口 TPM 限流器，每请求估算 token 消耗，失败自动退款。空闲信号量每 30 分钟清理。月度字数配额使用 Lua 脚本原子校验 + INCR，MySQL 兜底。

**基于统计的多引擎路由。**
两个翻译引擎（Python LLM :8000、本地 MTranServer :8989），60 秒滚动统计窗口。引擎的"优秀率"（响应 ≤ 1000ms）决定选中概率。MTranServer 承担双重角色 — 快速翻译模式下提供即时结果，LLM 引擎降级时自动兜底。双向自动降级，调用方无感知。

**三级支付幂等。**
Stripe Webhook 签名验签防伪造。Redis SETNX + 24h TTL 防跨实例重复处理。MySQL 乐观锁 `atomicUpdateSubscription`（`WHERE last_webhook_event_id IS NULL OR last_webhook_event_id != ?`）防单库竞态。

**四级翻译管线。**
L1: Caffeine → Redis → MySQL 三级缓存（精确匹配直接返回）。L2: RAG 语义搜索（HNSW 余弦 ≥ 0.85 跳过 LLM）。L3: 实体提取→占位符替换→对照术语表翻译实体→还原。L4: AI 引擎调用（质量校验：广告词检测、长度异常检查、中文字符修复）。每级可短路。

**协作状态机。**
章节任务通过有限状态机流转，CAS 乐观锁（`UPDATE ... WHERE version = ?`）。冲突时指数退避自动重试。事件驱动的章节拆分通过 `CollabChapterSplitEvent` 异步处理。

## REST API 端点

<details>
<summary>点击展开</summary>

所有端点通过 Nginx 7341 端口访问。外部请求使用 `/api/` 前缀（Nginx 剥离），插件和外部 API 路由使用 `/v1/`（原样传递）。

| 分类 | 后端路径 | 外部路径（经 Nginx） | 认证 |
|------|---------|---------------------|------|
| **认证** | `POST /user/register`, `POST /user/login`, `GET /user/verify` | `POST /api/user/register` 等 | 公开 |
| **用户** | `GET/PUT /user`, `GET /user/preferences`, `PUT /user/password` | `GET /api/user` 等 | JWT |
| **翻译** | `POST /v1/translate/selection`, `POST /v1/translate/reader`, `GET /v1/translate/task/{id}` | `POST /api/v1/translate/selection` 等 | JWT |
| **SSE 流** | `POST /v1/translate/text/stream`, `POST /v1/translate/document/stream` | `POST /api/v1/translate/text/stream` | JWT |
| **文档** | `CRUD /user/documents` | `GET/POST/DELETE /api/user/documents` | JWT |
| **术语表** | `CRUD /user/glossaries`, `POST /user/glossaries/import` | `GET/POST/DELETE /api/user/glossaries` | JWT |
| **订阅** | `POST /subscription/checkout`, `POST /subscription/portal`, `GET /subscription/status` | `POST /api/subscription/checkout` 等 | JWT |
| **协作** | `CRUD /v1/collab/projects`, `POST /v1/collab/chapters`, `POST /v1/collab/comments` | `GET/POST /api/v1/collab/projects` 等 | JWT + 项目权限 |
| **API Key** | `CRUD /user/api-keys` | `GET/POST/DELETE /api/user/api-keys` | JWT |
| **插件** | `POST /v1/translate/premium-reader`, `POST /v1/translate/premium-selection` | `POST /v1/translate/premium-reader` | API Key / JWT |
| **外部** | `POST /v1/external/translate` | `POST /v1/external/translate` | API Key |
| **Webhook** | `POST /webhook/stripe` | `POST /api/webhook/stripe` | Stripe 签名 |
| **管理** | `POST /admin/cache/evict`, `POST /admin/cache/stats` | `POST /api/admin/cache/evict` | 管理员 JWT |
| **平台** | `GET /platform/stats`, `GET /platform/statistics` | `GET /api/platform/stats` | 管理员 JWT |

</details>

## 快速开始

```bash
git clone https://github.com/YuBing-link/noveltrans.git
cd noveltrans
cp .env.example .env
# 编辑 .env，填写 MySQL、Stripe 和 LLM 凭据

docker compose up -d
```

首次启动可能需要 5-10 分钟。打开 [http://localhost:7341](http://localhost:7341)。

详细部署步骤参见 [`SETUP.md`](docs/SETUP.md)。

## 项目结构

```
noveltrans/
├── src/main/java/                  # Spring Boot 后端（Java 21）
│   ├── adapter/in/                 # 入站适配器
│   │   ├── rest/                   # REST 控制器
│   │   │   ├── web/                # Web 仪表盘端点
│   │   │   ├── plugin/             # Chrome 扩展端点
│   │   │   ├── external/           # 外部 API Key 端点
│   │   │   ├── shared/             # 共享翻译端点
│   │   │   ├── collab/             # 协作工作区端点
│   │   │   └── admin/              # 管理端点
│   │   ├── security/               # 认证、授权、限流过滤器
│   │   └── webhook/                # Stripe Webhook 处理器
│   ├── adapter/out/                # 出站适配器
│   │   ├── persistence/            # MyBatis-Plus 仓储实现
│   │   ├── redis/                  # Redis 缓存、PubSub、限流、向量存储
│   │   ├── translate/              # 翻译引擎客户端适配器
│   │   ├── stripe/                 # Stripe SDK 封装
│   │   ├── email/                  # 邮件发送与设备令牌
│   │   └── embedding/              # 向量嵌入生成
│   ├── application/service/        # 用例编排（应用层，14 个服务）
│   ├── domain/                     # 纯业务逻辑、模型、管线
│   ├── port/in/                    # 入站端口接口（16 个用例契约）
│   ├── port/out/                   # 出站端口接口（26 个基础设施契约）
│   └── config/                     # Spring 配置 + 横切关注点
├── src/main/resources/             # 应用配置、SQL 迁移、邮件模板
├── src/test/java/                  # 单元测试 + 集成测试（JaCoCo 80.5%）
├── web-app/                        # React Web 仪表盘（TypeScript + Vite）
├── extension/                      # Chrome 浏览器扩展（MV3）
├── services/translate-engine/      # Python 翻译微服务（FastAPI）
├── nginx/                          # Nginx 网关配置
├── load-test/                      # k6 负载测试脚本
├── docker-compose.yml              # 全栈编排（6 个容器）
└── .env.example                    # 环境变量模板
```

## 未来规划

| 方向 | 当前状态 | 规划路径 |
|------|---------|---------|
| **API 网关路由统一** | `/api/v1/` 和 `/v1/` 双入口共存 | 统一为单一外部前缀 `/api/v1/` |
| **Nginx 高可用** | 单 Nginx 实例 | 双 Nginx + Keepalived/VIP，或云 LB |
| **Stripe Webhook 可靠性** | 同步处理（返回 200 前完成全部 DB 事务） | 立即返回 200，入 Redis Stream/MQ 异步处理 |
| **引擎调用弹性** | Python FastAPI 路径有超时配置但没有断路器 | 添加 Resilience4j 断路器 + 降级，统一 `TranslationEnginePort` |
| **服务高可用** | 所有组件单实例 | Spring Boot 多实例 + 服务发现；MySQL 主从；Redis Sentinel/Cluster |
| **可观测性** | 缺少 APM 和分布式追踪 | Jaeger/SkyWalking 链路追踪；ELK/Loki 日志聚合 |
| **消息队列** | CollabStateMachine 和配额操作同步 | RabbitMQ/RocketMQ 异步处理章节事件、配额审计 |
| **对象存储** | 文档上传存储在本地 | S3/MinIO 用于 PDF/Word 翻译文档 |
| **向量库扩展** | Redis HNSW 满足 <100 万向量 | 超规模后评估 Milvus/Pinecone |
| **插件引擎选择** | 扩展下拉菜单展示多个引擎选项但实际路由不生效 | 按用户真实偏好路由 + 降级，清除虚假选项 |
| **插件 DOM 翻译优化** | 基础文本替换，页面布局可能被破坏 | DOM 结构感知翻译，保留布局、图片和交互元素 |

> 六边形端口/适配器设计确保这些升级不会破坏现有架构。

## 文档

| 文档 | 用途 |
|------|------|
| [`ARCHITECTURE.md`](docs/ARCHITECTURE.md) | 系统架构、组件职责、数据流、缓存层级 |
| [`API_DOCUMENTATION.md`](docs/API_DOCUMENTATION.md) | 完整 REST API 参考，含请求/响应示例 |
| [`SETUP.md`](docs/SETUP.md) | 部署与本地开发指南 |
| [`ADR.md`](docs/ADR.md) | 架构决策记录 |
| [`CONTRIBUTING.md`](docs/CONTRIBUTING.md) | 贡献指南 |
| [`Coverage Report`](docs/coverage/index.html) | JaCoCo 交互式 HTML 覆盖率报告（80.5% 指令覆盖） |
| [`Coverage Summary`](docs/coverage-report-summary.md) | Markdown 覆盖率摘要，含未覆盖最多的类 |
| [`Load Test Report`](load-test/results/) | k6 压测结果 — 200 VU 压力测试 HTML 报告 |

## License

[MIT](LICENSE)
