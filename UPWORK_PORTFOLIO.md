# Upwork Portfolio — NovelTrans SaaS Platform

> 以下是可以直接贴到 Upwork Portfolio 的项目描述。分三个区域：标题、描述、技术栈。

---

## Project Title（标题，70 字符以内）

**Multi-Tenant SaaS Translation Platform — Stripe Billing + Chrome Extension + AI**

---

## Project Description（正文描述）

I designed and built a production-ready SaaS platform that provides AI-powered content translation across three delivery channels: a React web app, a Chrome browser extension, and a REST API for third-party integrations.

### Business Features Delivered

- **Subscription billing** — Stripe Checkout + Billing Portal with FREE / PRO / MAX tiered plans, usage quotas, and automatic prorations via webhooks
- **Multi-tenant architecture** — tenant-scoped data isolation with row-level routing, ensuring each customer's data stays private
- **Team collaboration** — project-based workspaces where owners invite members, assign translation chapters, and run a review → approve workflow
- **API-as-a-product** — users generate API keys for programmatic access, with per-key usage tracking and rate limiting
- **Email verification** — secure registration with time-limited OTP codes and password reset flow

### Technical Highlights

- **Spring Boot 3.2 backend** (Java 21) with 15+ REST controllers, JWT + API key dual authentication, and tenant context interceptors
- **React + TypeScript frontend** with Vite, TailwindCSS, and a responsive dashboard covering user profiles, translation history, glossary management, subscription status, and team workspaces
- **Chrome Extension** (Manifest V3) supporting full-page translation (DOM-aware), reader mode extraction, and inline selection translation — all communicating with the backend via messaging API
- **Python translation microservice** (FastAPI) with multi-engine fallback: Google → MyMemory → Libre → Baidu → DeepL → OpenAI. Free engines are prioritized automatically to minimize cost
- **Four-level translation pipeline**: L1 cache → L2 RAG memory → L3 entity consistency → L4 direct LLM — balancing speed, cost, and quality across user tiers
- **SSE streaming** for long-running translations, giving users real-time progress instead of loading spinners
- **Stripe webhook handling** with signature verification, idempotent event processing, and automatic subscription state sync
- **Docker Compose** orchestration: MySQL, Redis Stack, Nginx gateway, Python microservice, and Java backend in one command
- **Unit test coverage ~58%** with JUnit 5 and Mockito

### Architecture

```
Users (Web App / Chrome Ext / API Keys)
          │
    Nginx Gateway (TLS, CORS, static files)
          │
    Spring Boot Backend (JWT Auth, Stripe Webhooks, 15+ Controllers)
          │
    Python Translation Microservice (multi-engine fallback)
          │
    MySQL 8.0 + Redis (cache + vector store)
```

### Outcome

A fully functional SaaS product with a complete business loop: sign up → verify email → subscribe via Stripe → use translation features → manage team projects → consume external API. The platform handles multi-tenancy, billing webhooks, rate limiting, and graceful degradation when translation engines fail.

---

## Skills / Tags（Upwork 标签）

`Java` `Spring Boot` `React` `TypeScript` `Stripe API` `Docker` `REST API` `SaaS` `Chrome Extension` `Python` `FastAPI` `MySQL` `Redis` `JWT Authentication` `Webhooks` `Microservices` `TailwindCSS` `Vite` `Nginx` `CI/CD`

---

## Tips for Publishing

1. **Live URL**: 部署后把 `https://your-domain.com` 替换为真实地址
2. **截图**: 截 3-5 张图（首页、仪表盘、订阅页、翻译效果、协作工作台）
3. **Role**: 选择 "Full-Stack Development" 或 "SaaS Development"
4. **Project Cost**: 填 "$2,000–$5,000" 或 "Custom project — estimated value"
5. **Duration**: "1–3 months"
