# NovelTrans

面向网文作者和译者的 SaaS 翻译平台 — 基于 RAG 翻译记忆、多智能体协作翻译、团队协作和 Stripe 计费。

> [English README](README.md)

[![CI](https://github.com/YuBing-link/noveltrans/actions/workflows/ci.yml/badge.svg)](https://github.com/YuBing-link/noveltrans/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen?logo=spring)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-blue?logo=react)](https://react.dev/)
[![Coverage](https://img.shields.io/badge/Coverage-86%25-brightgreen)]()

## ✨ 核心特性

- **长篇内容翻译** — AI 驱动的多智能体协作翻译（译者 + 术语专家 + 润色师），专为长篇小说设计
- **翻译记忆复用** — Redis HNSW 向量语义检索，自动匹配相似历史翻译，减少 60-80% 的 LLM API 调用
- **团队协作** — 项目管理、章节分配、审核批准工作流
- **三大交付渠道** — React Web 仪表盘、Chrome 扩展（MV3，三种翻译模式）、外部 REST API（API Key 认证）
- **订阅商业化** — Stripe Checkout + 计费门户 + Webhook，三档计划（FREE / PRO / MAX）带用量配额

## 🏆 技术亮点

| 领域 | 实现 |
|------|------|
| **分布式缓存一致性** | 版本号 + 延迟双删 + Redis pub/sub 跨实例缓存失效联动；**术语反向索引精准失效**（新增/修改术语只淘汰包含该词的缓存条目，避免全局暴力刷新） |
| **虚拟线程** | Java 21 虚拟线程贯穿全局 — 异步缓存驱逐、多智能体扇出、HTTP 客户端 I/O |
| **四级缓存链路** | Caffeine L1 (10 min) → Redis L2 (30 min) → MySQL L3 (24 h) → RAG 语义匹配（永久） |
| **Webhook 幂等性** | 5 层纵深防御（签名 → Redis SETNX → DB lastWebhookEventId → DuplicateKeyException → 时间戳排序），**`invoice.payment_succeeded` 作为 fallback 激活路径** |
| **引擎弹性容错** | LLM + MTranServer 双引擎，健康检查 + 断路器冷却 + 概率路由 |
| **RAG 向量检索** | Redis Stack HNSW 索引 + KNN 语义搜索，用户隔离，质量自动过滤 |
| **分级限流** | **IP + API Key 双 Redis 滑动窗口**，Lua 原子脚本合并（4 操作 → 1 调用）+ 按用户维度的匿名/免费/Pro/API Key 差异化并发限制 |
| **异步批量处理** | **事件驱动章节拆分**：窄事务 + `@Async` 批量插入（50 章/批）+ 定时补偿任务兜底节点宕机 |
| **SSE 断线重放** | **Redis Stream 消息重放** — 客户端携带 `lastEventId` 重连可追回丢失的协作事件 |
| **状态机驱动** | **驱动型状态机** — `transitionProject()` 和 `transitionChapter()` 封装 validate + set，防止 setStatus 绕过校验 |
| **测试覆盖** | 86% 指令覆盖率 / 75% 分支覆盖率，JUnit 5 + Vitest + Playwright + k6 压测 |

## 🛠️ 快速开始

> 详细部署步骤、本地开发配置和常见问题排查，参见 [`SETUP.md`](SETUP.md)。

### 前置依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| Docker | 24+ | 容器运行时 |
| Docker Compose | 2.20+ | 容器编排 |
| npm | 9+ | 前端构建 |

### 1. 克隆并配置

```bash
git clone https://github.com/YuBing-link/noveltrans.git
cd noveltrans

cp .env.example .env
# 编辑 .env，填写 MySQL、Stripe 和 LLM 凭据
```

### 2. 构建前端

```bash
cd web-app
npm install
npm run build
cd ..
```

### 3. 启动所有服务

```bash
docker compose up -d
```

首次启动可能需要 5-10 分钟（Maven 依赖下载 + Ollama 模型拉取）。

### 4. 验证

```bash
docker compose ps                    # 所有容器应健康运行
curl http://localhost:7341/health    # 后端健康检查
```

在浏览器中打开 [http://localhost:7341](http://localhost:7341)。

## 📦 技术栈

| 层级 | 技术 |
|------|------|
| **后端** | Java 21, Spring Boot 3.2, MyBatis-Plus, Undertow, Virtual Threads |
| **前端** | React 19, TypeScript 6, Vite 8, TailwindCSS 4.2, i18next |
| **Chrome 扩展** | Manifest V3, Content Scripts, IndexedDB |
| **翻译引擎** | Python 3.11, FastAPI, OpenAI SDK, AgentScope（多智能体） |
| **神经翻译机器** | MTranServer — 轻量级开源翻译引擎 |
| **数据库** | MySQL 8.0, Redis Stack（RediSearch + HNSW 向量） |
| **Embedding** | Ollama (bge-m3) / OpenAI text-embedding-3-small |
| **支付** | Stripe Checkout, 计费门户, Webhook |
| **网关** | Nginx（统一入口，端口 7341） |
| **测试** | JUnit 5, Mockito, Vitest, Playwright, k6 |

## 🏗️ 项目结构

```
noveltrans/
├── src/main/java/          # Spring Boot 后端（Java 21）
├── src/test/java/          # 单元测试 + 集成测试（80+ 类）
├── web-app/                # React Web 仪表盘（TypeScript + Vite）
├── extension/              # Chrome 浏览器扩展（MV3）
├── services/translate-engine/  # Python 翻译微服务 + 多智能体管线
├── nginx/                  # Nginx 网关配置
├── load-test/              # k6 负载测试脚本
├── docker-compose.yml      # 全栈编排（6 个容器）
└── .env.example            # 环境变量模板
```

## 🔌 API 速览

所有 API 请求均通过 Nginx **端口 7341** 转发。

| 端点 | 方法 | 认证 | 说明 |
|------|------|------|------|
| `/api/user/login` | POST | 公开 | 邮箱/密码登录，返回 JWT |
| `/api/subscription/checkout` | POST | JWT | 创建 Stripe Checkout 会话 |
| `/v1/translate/webpage` | POST | 已认证 | 网页翻译（SSE 流式） |
| `/v1/translate/reader` | POST | 已认证 | 文章提取 + 翻译 |
| `/v1/external/translate` | POST | API Key | 外部 REST API 文本翻译 |
| `/api/collab/projects` | POST | JWT | 创建团队协作项目 |
| `/webhook/stripe` | POST | Webhook 签名 | Stripe 事件处理 |

完整 API 文档：参见 `API_DOCUMENTATION.md`

## 🔑 环境变量

| 变量 | 说明 | 必填 |
|------|------|------|
| `MYSQL_HOST` / `MYSQL_PASSWORD` | MySQL 连接 | 是 |
| `REDIS_HOST` / `REDIS_PASSWORD` | Redis 连接 | 是 |
| `JWT_SECRET` | JWT 签名密钥（至少 32 字符） | 是 |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP 邮箱验证 | 是 |
| `STRIPE_SECRET_KEY` | Stripe 密钥（`sk_test_` 或 `sk_live_`） | 是 |
| `STRIPE_WEBHOOK_SECRET` | Webhook 签名验证 | 是 |
| `LLM_API_KEY` / `LLM_BASE_URL` | LLM 翻译引擎凭据 | 可选 |
| `MTRAN_PORT` / `MTRAN_API_KEY` | MTranServer 神经翻译机器 | 可选 |
| `EMBEDDING_PROVIDER` | `openai` 或 `ollama`（RAG 向量） | 是 |
| `TRANSLATE_SERVICE_API_KEY` | 内部服务间认证 | 是 |

完整列表参见 `.env.example`。

## 📊 架构

> 系统设计、数据流、缓存层次结构和部署拓扑的详细说明，参见 [`ARCHITECTURE.md`](ARCHITECTURE.md)。

```
                    ┌─────────────────────────────────────┐
                    │       Nginx（端口 7341）             │
                    │   SPA + API 反向代理 + CORS         │
                    └──────────┬──────────────────────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
              ▼                ▼                ▼
     ┌────────────┐   ┌────────────┐   ┌─────────────┐
     │  React SPA │   │ Spring Boot│   │  外部客户端  │
     │ (web-app)  │   │  (Java 21) │   │ (API Key)   │
     └────────────┘   └──────┬─────┘   └─────────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
         ┌────────┐   ┌──────────┐   ┌──────────────┐
         │ MySQL  │   │  Redis   │   │ Python FastAPI│
         │  8.0   │   │ (缓存 +  │   │ + MTranServer│
         │        │   │  向量 +  │   │ (神经翻译)    │
         │        │   │ pub/sub) │   │              │
         └────────┘   └──────────┘   └──────────────┘
```

### 翻译管线

```
新的翻译请求
  │
  ├── L1: Caffeine 本地缓存（10 分钟 TTL）
  ├── L2: Redis 分布式缓存（30 分钟 TTL）
  ├── L3: MySQL 持久化缓存（24 小时 TTL）
  ├── L4: RAG 语义匹配（永久）← 向量相似度搜索
  └── L5: 直接 LLM / MTranServer 翻译（降级）
```

### 缓存一致性策略

```
数据变更（更新/删除翻译记忆）
  │
  ├── 第 1 步：清空 L1 (Caffeine) + L2 (Redis) — 前置删除
  ├── 第 2 步：Redis 中递增版本号 + 发布 pub/sub 事件
  ├── 第 3 步：等待 2 秒（等待进行中的写入完成）
  ├── 第 4 步：清空 L2 Redis 旧版本 key + 过期 L3 旧记录 — 后置删除
  └── 第 5 步：所有实例接收 pub/sub，清空各自 L1 本地缓存

术语库变更（新增/修改/删除术语）
  │
  ├── putCache() 时从 sourceText 提取单词（长度 >= 3）
  ├── 写入前剥离已有版本号前缀再追加当前版本（防止 `v1:v1:<md5>` 双前缀 bug）
  ├── 对每个单词：SADD glossary:cache_keys:{word} {cacheKey}
  ├── 术语变更时：SMEMBERS glossary:cache_keys:{term}
  ├── 只删除受影响的 L1、L2、L3 缓存键
  └── 避免全局缓存抖动 — 只淘汰包含该词的条目
```

### 详细技术说明

<details>
<summary>点击展开：RAG 翻译记忆检索</summary>

- **向量语义检索**：基于 Redis Stack HNSW 向量索引，将翻译原文编码为 Embedding 向量，通过 KNN 搜索历史翻译记忆
- **四级缓存链路**：Caffeine (L1) → Redis (L2) → MySQL (L3) → RAG 语义检索 (L4) → 翻译引擎
- **质量筛选**：入库前自动过滤空译文、长度异常、广告关键词、特殊字符过多等低质量翻译
- **双路降级策略**：Redis KNN 不可用时自动降级为 MySQL 余弦相似度计算
- **用户隔离**：KNN 查询按 `user_id` + `target_lang` 过滤，避免数据串扰
</details>

<details>
<summary>点击展开：统一翻译管线</summary>

- **单一管线组件**：`TranslationPipeline` 封装四级翻译管线逻辑，消除重复代码
- **策略模式**：可配置管线阶段 — `execute()` 执行完整管线，`executeFast()` 仅缓存 + 直译
- **后处理集成**：所有翻译路径均包含 `fixUntranslatedChinese()` 后处理
- **质量校验**：静态 `isValidTranslation()` 和 `shouldCache()` 方法确保一致的质量检查
</details>

<details>
<summary>点击展开：翻译引擎架构</summary>

- **OpenAI 兼容 API**：支持 OpenAI GPT、Claude、本地 Ollama、DeepSeek 等任意兼容端点
- **专业小说翻译 Prompt**：6 条翻译原则，确保文学翻译质量
- **双引擎容错**：LLM 翻译引擎 + MTranServer 轻量引擎双向降级，健康检查 + 冷却隔离
- **概率轮询路由**：基于历史成功率 + 响应时间的智能引擎选择
</details>

<details>
<summary>点击展开：缓存体系防护</summary>

- **缓存穿透防护**：空值占位 + 短暂过期策略
- **缓存击穿防护**：`ConcurrentHashMap` 同 key 并发加锁
- **缓存雪崩防护**：过期时间随机抖动
- **缓存一致性**：版本号 + 延迟双删 + Redis pub/sub 跨实例缓存失效联动
</details>

<details>
<summary>点击展开：安全体系</summary>

- **JWT + API Key 双重认证**：共享同一翻译管线
- **翻译端点强制认证**：所有 `/v1/translate/**` 端点必须携带有效 JWT 或 API Key
- **API Key 管理**：`nt_sk_xxxx` 格式前缀 + 32 位随机字符，列表展示掩码脱敏
- **BCrypt 密码加密**：用户密码哈希存储
- **邮箱验证**：注册/密码重置双重验证
- **IP 级限流**：Redis Sorted Set 滑动窗口（每 IP 60 秒内最多 100 请求），位于安全过滤器链最前方，跳过 API Key 认证的请求。Lua 原子脚本合并 4 个 Redis 操作为 1 次调用。
- **API Key 级限流**：独立的 Redis Sorted Set 滑动窗口，专用于 API Key 认证请求，同样 Lua 原子化，配额可配置。
- **API Key 两级缓存**：Caffeine L1 (5 min) + Redis L2 (30 min) + MySQL 兜底，Redis 异常时 fail-closed（拒绝请求）。
- **分级限流**：匿名用户 / 免费用户 / Pro 用户差异化并发限制。
- **月度字符配额**：Redis Lua 原子检查 + INCR + MySQL 异步备份。高配额用户（≥1000 万字符/月）直接跳过 Redis。
</details>

<details>
<summary>点击展开：字符配额系统</summary>

| 档位 | 月字符包 | 快速 (×0.5) | 专家 (×1.0) | 团队 (×2.0) |
|------|----------|-------------|-------------|-------------|
| **Free** | 10,000 | 等效 2 万字原文 | 等效 1 万字原文 | 等效 5 千字原文 |
| **Pro** | 50,000 | 等效 10 万字原文 | 等效 5 万字原文 | 等效 2.5 万字原文 |
| **Max** | 200,000 | 等效 40 万字原文 | 等效 20 万字原文 | 等效 10 万字原文 |

- **模式系数**：快速模式 ×0.5（节省配额）, 专家模式 ×1.0, 团队模式 ×2.0
- **按请求扣减**：翻译开始前预检查配额
- **按天追踪**：`quota_usage` 表按天记录消耗，按月汇总
- **每月自动重置**：定时任务每月 1 号 0 点清理过期记录
</details>

## 📈 性能表现

使用 k6（翻译 + 支付）和 Python 多线程 HTTP（Webhook）进行压测。完整报告：[`load-test/STRESS_TEST_REPORT.md`](load-test/STRESS_TEST_REPORT.md)

### 翻译 API（API Key，500 VU）

| 轮次 | 场景 | 吞吐量 | 平均延迟 | p95 延迟 | 错误率 |
|------|------|--------|---------|---------|--------|
| 22 | 基线（有问题） | 22.4 req/s | 10,082 ms | — | 28.5% |
| 27 | 优化后（Lua + 缓存修复 + 配额旁路） | **4,235 req/s** | 118 ms | 250 ms | 0% |

**关键优化**：Redis Lua 脚本合并（每请求省 8 次调用）、缓存键双前缀 bug 修复（命中率 0% → ~95%）、高配额 Redis 旁路（省 1 次调用）、Redis 连接池扩容（16 → 256）。

> 从第 22 轮到第 27 轮，吞吐量提升 **25 倍**。完整报告：[`load-test/STRESS_TEST_REPORT.md`](load-test/STRESS_TEST_REPORT.md)

### Stripe 支付结账

| 场景 | 吞吐量 | p95 延迟 | 错误率 |
|------|--------|---------|--------|
| 真实场景（1s 用户思考时间） | **16.9 req/s** | 2,572 ms | 0% |
| 最大吞吐（无 sleep） | **23.5 req/s** | 2,093 ms | 0% |

> 结账延迟主要由 Stripe API 网络往返（约 2s）决定，非应用处理耗时。

### Webhook 幂等性（50 并发线程）

| 测试 | 请求数 | 重复记录 | 状态 |
|------|--------|---------|------|
| `checkout.session.completed` (50×10) | 500 | 1 | 通过 |
| `subscription.updated` (100×10) | 1,000 | 0 | 通过 |
| 混合事件 (50×5×3 种类型) | 750 | 2 | 通过 |

混合事件竞态条件历经 3 轮修复：原子条件更新 → DuplicateKeyException 处理 → Redis SETNX 跨事件去重。

## 🚀 后续发展方向

### API 智能调度网关

在翻译引擎和上游 LLM 提供商（Claude、GPT-4、DeepSeek 等）之间构建智能多提供商 API 网关。

**专家模式** — 一次性完成任务，独占调度：
- 每个翻译请求独占 1 个 API 并发 slot
- 无抢占 —— 一旦分配，slot 被该任务独占直到完成

**协作模式** — 多章节、断点续翻式翻译：
- 每个项目维护**优先级模型链**（如 Claude → GPT-4 → DeepSeek）
- 在主模型达到并发上限前，提前调度请求
- 当主模型满载时，用户被询问：*排队以获得更好的风格一致性，或降级到其他模型？*
- 降级模型被存储为项目的次优先选择，实现智能降级
- 通过限制同一章节块内的模型切换次数，保持翻译风格一致性

**架构设计：**
```
                  翻译请求
                      │
                      ▼
           ┌─────────────────────┐
           │    调度网关          │
           │  ┌───────────────┐  │
           │  │ 项目模型      │  │
           │  │ 优先级链      │  │
           │  └───────┬───────┘  │
           │          │          │
           │  ┌───────▼───────┐  │
           │  │ 并发池管理器   │  │
           │  └───────┬───────┘  │
           │          │          │
           │  ┌───────▼───────┐  │
           │  │ 队列 + 降级策略│  │
           │  └───────────────┘  │
           └─────────┬───────────┘
                      │
       ┌──────────────┼──────────────┐
       ▼              ▼              ▼
  ┌────────┐    ┌────────┐    ┌────────┐
  │ Claude │    │  GPT   │    │DeepSeek│
  │ (max 5)│    │(max 10)│    │(max 20)│
  └────────┘    └────────┘    └────────┘
```

**核心设计目标：**
- 每个提供商可配置 `max_concurrency`、`cost_per_token`、`quality_tier`
- 带超时的排队策略（超时自动降级，不无限等待）
- 项目级别的 `preferred_models` 持久化 — 一旦降级模型表现良好，自动成为项目第二优先选择
- 最小模型切换间隔，避免连续章节出现"风格割裂"

## 🗺️ 路线图

- [x] 用户注册和邮箱验证
- [x] Stripe 订阅计费（FREE / PRO / MAX）
- [x] RAG 翻译记忆（Redis HNSW）
- [x] 多智能体协作翻译
- [x] 团队协作工作区
- [x] Chrome 扩展（三种翻译模式）
- [x] 外部 REST API（API Key 认证）
- [x] 缓存一致性（版本号 + 延迟双删 + Redis pub/sub）
- [x] CI/CD 流水线（GitHub Actions：构建、测试、Docker 冒烟测试）
- [ ] API 智能调度网关
- [ ] WebSocket 实时翻译进度推送
- [ ] 文档格式支持（PDF、EPUB）
- [ ] 机器翻译质量评分看板

## 📚 文档索引

| 文档 | 说明 |
|------|------|
| [`SETUP.md`](SETUP.md) | 详细部署指南与本地开发配置 |
| [`API_DOCUMENTATION.md`](API_DOCUMENTATION.md) | 完整 REST API 文档 |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | 系统架构、数据流、缓存层次、部署拓扑 |
| [`test-coverage-report.md`](test-coverage-report.md) | JaCoCo 测试覆盖率报告（86% 指令 / 75% 分支） |
| [`load-test/STRESS_TEST_REPORT.md`](load-test/STRESS_TEST_REPORT.md) | k6 + Python 多线程压测结果 |
| [`CODE_STYLE.md`](CODE_STYLE.md) | 编码规范、命名约定、包结构 |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | Git 工作流、提交规范、PR 流程 |

## 🤝 贡献

欢迎提交 Bug 修复、文档改进和新的翻译引擎集成。

安装和开发规范参见 [`SETUP.md`](SETUP.md)、[`CODE_STYLE.md`](CODE_STYLE.md) 和 [`CONTRIBUTING.md`](CONTRIBUTING.md)。

1. Fork 本仓库
2. 创建你的功能分支（`git checkout -b feature/your-feature`）
3. 提交你的修改
4. 推送到分支
5. 发起 Pull Request

较大的改动请先开 Issue 讨论方案。

## 📄 许可证

[MIT](LICENSE)

---

**最后更新**：2026-05-06 — 第 27 轮压测（吞吐量提升 25 倍）
