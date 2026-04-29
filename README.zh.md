# NovelTrans

面向网文作者和译者的 SaaS 翻译平台 — 基于 RAG 翻译记忆、多智能体协作翻译、团队协作和 Stripe 计费。

> [English README](README.md)

[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen?logo=spring)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-blue?logo=react)](https://react.dev/)

## ✨ 核心特性

- **长篇内容翻译** — AI 驱动的多智能体协作翻译（译者 + 术语专家 + 润色师），专为长篇小说设计
- **翻译记忆复用** — Redis HNSW 向量语义检索，自动匹配相似历史翻译，减少 60-80% 的 LLM API 调用
- **团队协作** — 项目管理、章节分配、审核批准工作流
- **三大交付渠道** — React Web 仪表盘、Chrome 扩展（MV3，三种翻译模式）、外部 REST API（API Key 认证）
- **订阅商业化** — Stripe Checkout + 计费门户 + Webhook，三档计划（FREE / PRO / MAX）带用量配额

## 🛠️ 快速开始

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
| **后端** | Java 21, Spring Boot 3.2, MyBatis-Plus, Undertow |
| **前端** | React 19, TypeScript 5, Vite 8, TailwindCSS 4.2, i18next |
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
├── docker-compose.yml      # 全栈编排（7 个容器）
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
         │        │   │  向量)   │   │ (神经翻译)    │
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
</details>

<details>
<summary>点击展开：安全体系</summary>

- **JWT + API Key 双重认证**：共享同一翻译管线
- **翻译端点强制认证**：所有 `/v1/translate/**` 端点必须携带有效 JWT 或 API Key
- **API Key 管理**：`nt_sk_xxxx` 格式前缀 + 32 位随机字符，列表展示掩码脱敏
- **BCrypt 密码加密**：用户密码哈希存储
- **邮箱验证**：注册/密码重置双重验证
- **分级限流**：匿名用户 / 免费用户 / Pro 用户差异化并发限制
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

## 🗺️ 路线图

- [x] 用户注册和邮箱验证
- [x] Stripe 订阅计费（FREE / PRO / MAX）
- [x] RAG 翻译记忆（Redis HNSW）
- [x] 多智能体协作翻译
- [x] 团队协作工作区
- [x] Chrome 扩展（三种翻译模式）
- [x] 外部 REST API（API Key 认证）
- [ ] WebSocket 实时翻译进度推送
- [ ] 文档格式支持（PDF、EPUB）
- [ ] 机器翻译质量评分看板

## 🤝 贡献

欢迎提交 Bug 修复、文档改进和新的翻译引擎集成。

1. Fork 本仓库
2. 创建你的功能分支（`git checkout -b feature/your-feature`）
3. 提交你的修改
4. 推送到分支
5. 发起 Pull Request

较大的改动请先开 Issue 讨论方案。

## 📄 许可证

[MIT](LICENSE)

---

**最后更新**：2026-04-29
