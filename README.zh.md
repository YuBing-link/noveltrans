# NovelTrans

面向网文作者和译者的 SaaS 翻译平台 — 基于 RAG 翻译记忆、多智能体协作翻译、团队协作和 Stripe 计费。

> [English README](README.md)

[![CI](https://github.com/YuBing-link/noveltrans/actions/workflows/ci.yml/badge.svg)](https://github.com/YuBing-link/noveltrans/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![React](https://img.shields.io/badge/React-19-blue?logo=react)](https://react.dev/)
[![Coverage](https://img.shields.io/badge/Coverage-86%25-brightgreen)]()

## 核心特性

- **长篇内容翻译** — AI 驱动的多智能体协作翻译（译者 + 术语专家 + 润色师），专为长篇小说设计
- **翻译记忆复用** — Redis HNSW 向量语义检索，自动匹配相似历史翻译，减少 60-80% 的 LLM API 调用
- **团队协作** — 项目管理、章节分配、审核批准工作流
- **三大交付渠道** — React Web 仪表盘、Chrome 扩展（MV3，三种翻译模式）、外部 REST API（API Key 认证）
- **订阅商业化** — Stripe Checkout + 计费门户 + Webhook，三档计划（FREE / PRO / MAX）带用量配额

## 快速开始

```bash
git clone https://github.com/YuBing-link/noveltrans.git
cd noveltrans
cp .env.example .env
# 编辑 .env，填写 MySQL、Stripe 和 LLM 凭据

cd web-app && npm install && npm run build && cd ..
docker compose up -d
```

首次启动可能需要 5-10 分钟。在浏览器中打开 [http://localhost:7341](http://localhost:7341)。

详细部署步骤参见 [`SETUP.md`](SETUP.md)。

## 技术栈

| 层级 | 技术 |
|------|------|
| **后端** | Java 21, Spring Boot 3.2 (Undertow), MyBatis-Plus, Virtual Threads |
| **前端** | React 19, TypeScript, Vite, TailwindCSS 4.2 |
| **Chrome 扩展** | Manifest V3, Content Scripts, IndexedDB |
| **翻译引擎** | Python 3.11, FastAPI, OpenAI SDK, AgentScope（多智能体） |
| **数据库** | MySQL 8.0, Redis Stack（RediSearch + HNSW 向量） |
| **支付** | Stripe Checkout, 计费门户, Webhook |
| **网关** | Nginx（统一入口，端口 7341） |
| **测试** | JUnit 5, Mockito, Vitest, Playwright, k6 |
| **CI/CD** | GitHub Actions |

## 项目结构

```
noveltrans/
├── src/main/java/          # Spring Boot 后端（Java 21）
├── src/test/java/          # 单元测试 + 集成测试
├── web-app/                # React Web 仪表盘（TypeScript + Vite）
├── extension/              # Chrome 浏览器扩展（MV3）
├── services/translate-engine/  # Python 翻译微服务
├── nginx/                  # Nginx 网关配置
├── load-test/              # k6 负载测试脚本
├── docker-compose.yml      # 全栈编排（6 个容器）
└── .env.example            # 环境变量模板
```

## 文档索引

| 文档 | 说明 |
|------|------|
| [`SETUP.md`](SETUP.md) | 详细部署指南与本地开发配置 |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | 系统架构、数据流、缓存层次、部署拓扑 |
| [`API_DOCUMENTATION.md`](API_DOCUMENTATION.md) | 完整 REST API 文档 |
| [`ADR.md`](ADR.md) | 架构决策记录 |
| [`test-coverage-report.md`](test-coverage-report.md) | JaCoCo 测试覆盖率报告 |
| [`load-test/STRESS_TEST_REPORT.md`](load-test/STRESS_TEST_REPORT.md) | k6 压测结果 |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | Git 工作流、提交规范、PR 流程 |

## 贡献

欢迎提交 Bug 修复、文档改进和新的翻译引擎集成。详见 [`CONTRIBUTING.md`](CONTRIBUTING.md)。

1. Fork 本仓库
2. 创建你的功能分支（`git checkout -b feature/your-feature`）
3. 提交你的修改
4. 推送到分支
5. 发起 Pull Request

较大的改动请先开 Issue 讨论方案。

## 许可证

[MIT](LICENSE)
