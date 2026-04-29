# NovelTrans — SaaS Translation Platform

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen?logo=spring)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-blue?logo=react)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5-blue?logo=typescript)](https://www.typescriptlang.org/)
[![Stripe](https://img.shields.io/badge/Stripe-Checkout-635BFF?logo=stripe)](https://stripe.com/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker)](https://docs.docker.com/compose/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

> A production-ready SaaS platform delivering AI-powered content translation across web, browser extension, and external API — with subscription billing, team collaboration, and RAG-powered translation memory.

## At a Glance

| Metric | Value |
|--------|-------|
| **Codebase** | 180+ Java files, 70+ TypeScript files, 12 Python files, 15 JS files |
| **Database** | 16 tables, 50+ REST API endpoints |
| **Services** | 7 Docker containers orchestrated |
| **Testing** | 80+ Java test classes, Playwright E2E, k6 load tests |
| **Architecture** | Nginx gateway + Spring Boot + FastAPI microservice + Chrome extension |

## What It Does

NovelTrans is a **full-stack SaaS translation platform** that delivers AI-powered content translation through three channels:

| Channel | Description |
|---------|-------------|
| **Web App** | React dashboard for document upload, translation history, glossary management, and team projects |
| **Chrome Extension** | In-browser translation with 3 modes: full-page, reader mode, and selection — with DOM-aware layout preservation |
| **External API** | REST API with API-key authentication (`nt_sk_xxxx`) for third-party integrations |

## Key Technical Highlights

### 1. RAG Translation Memory

Semantic vector matching using Redis HNSW index stores embeddings of prior translations. When a new translation request arrives, the system first searches for semantically similar past translations — even when source text isn't identical — dramatically reducing LLM API costs.

```
New Translation Request
  │
  ├── L1: Caffeine local cache (10 min TTL)
  ├── L2: Redis distributed cache (30 min TTL)
  ├── L3: MySQL persistent cache (24h TTL)
  ├── L4: RAG semantic match (permanent) ← vector similarity search
  └── L5: Direct LLM translation (fallback)
```

**Impact**: Repeated or similar translations are served from cache/memory instead of calling the LLM, reducing API costs by an estimated **60-80%**.

### 2. Multi-Agent Collaborative Translation

The Python microservice uses AgentScope to implement a real translation team workflow:

```
Translator Agent ──▶ Terminologist Agent ──▶ Polisher Agent ──▶ Merger Agent
     │                      │                      │                 │
  translates            validates terms       polishes style    assembles final
```

Different novel genres (battle/mystery/daily) use tailored system prompts, producing higher-quality translations than single-model approaches.

### 3. Four-Level Cache with Full Protection

Not just multi-level — the cache system addresses all three classic cache failure modes:

| Problem | Solution |
|---------|----------|
| Cache penetration (non-existent data) | Null result caching with short TTL |
| Cache breakdown (hot key expiry) | ConcurrentHashMap per-key locking |
| Cache avalanche (mass expiry) | TTL random jitter |

### 4. Java 21 Virtual Threads + Tiered Concurrency

Uses Project Loom virtual threads for high-concurrency translation requests, with per-user semaphore-based rate limiting:

| Tier | Concurrent Requests |
|------|-------------------|
| FREE | 1 |
| PRO | 3 |
| MAX | 5 |

### 5. Complete SaaS Commercialization

| Feature | Implementation |
|---------|---------------|
| Subscription billing | Stripe Checkout + Billing Portal + Webhooks |
| Usage quotas | Monthly character limits, daily caps |
| Team collaboration | Project invites, chapter assignment, review workflow |
| API key management | `nt_sk_xxxx` format keys with usage tracking |
| Multi-tenant | Tenant-scoped data isolation via MyBatis-Plus line handler |

## Tech Stack

| Layer | Technology |
|-------|------------|
| **Backend** | Java 21, Spring Boot 3.2, MyBatis-Plus, Undertow |
| **Frontend** | React 19, TypeScript, Vite, TailwindCSS |
| **Chrome Extension** | Manifest V3, Content Scripts, IndexedDB |
| **Microservice** | Python 3.11, FastAPI, OpenAI SDK, AgentScope |
| **Database** | MySQL 8.0, Redis Stack (RediSearch + HNSW vectors) |
| **Embeddings** | Ollama (bge-m3) / OpenAI text-embedding-3-small |
| **Payments** | Stripe Checkout, Billing Portal, Webhooks |
| **Infrastructure** | Docker Compose (7 containers), Nginx |
| **Testing** | JUnit 5, Mockito, Vitest, Playwright, k6 |

## User Tiers

| Tier | Monthly Characters | Daily Limit | Concurrency | Features |
|------|-------------------|-------------|-------------|----------|
| **FREE** | 100,000 | 500 | 1 | Basic translation, glossary |
| **PRO** | 500,000 | 5,000 | 3 | Document translation, advanced engines |
| **MAX** | 2,000,000 | Unlimited | 5 | Team collaboration, API access, priority |

## Quick Start

```bash
git clone <your-repo-url>
cd novelTranslator

cp .env.example .env
# Edit .env with your MySQL, Redis, Stripe, and LLM credentials

docker compose up -d
```

Full deployment guide: See `SETUP.md`

## Project Structure

```
├── src/main/java/        # Spring Boot backend (Java 21)
│   ├── controller/       # REST endpoints (15+ controllers)
│   ├── service/          # Business logic (25+ services)
│   │   └── pipeline/     # Unified TranslationPipeline
│   ├── security/         # JWT auth, API Key filter, AOP permissions
│   ├── config/           # Stripe, Redis, RAG, virtual threads
│   └── mapper/           # MyBatis-Plus data access
├── web-app/              # React frontend (TypeScript + Vite)
│   ├── src/pages/        # 15+ page components
│   ├── src/api/          # 15 API client modules
│   └── src/components/   # Shared UI component library
├── extension/            # Chrome browser extension (MV3)
│   ├── src/background/   # Service worker
│   ├── src/content/      # Content scripts (3 translation modes)
│   └── src/popup/        # Extension popup UI
├── services/translate-engine/  # Python translation microservice
│   └── agents/           # AgentScope multi-agent collaboration
├── nginx/                # Nginx gateway configuration
├── load-test/            # k6 load testing scripts
└── docker-compose.yml    # Full-stack orchestration (7 services)
```

## API Highlights

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/user/login` | POST | Public | Email/password authentication, returns JWT |
| `/subscription/checkout` | POST | JWT | Create Stripe Checkout session |
| `/v1/translate/webpage` | POST | Auth | Full-page translation (SSE streaming) |
| `/v1/translate/reader` | POST | Auth | Article extraction + translation |
| `/v1/external/translate` | POST | API Key | External API text translation |
| `/v1/collab/projects` | POST | JWT | Create team collaboration project |
| `/webhook/stripe` | POST | Webhook Sig | Stripe event handler |

Full API documentation: See `API_DOCUMENTATION.md`

## Documentation

| Document | Description |
|----------|-------------|
| `README.md` | This file — project overview |
| `PORTFOLIO.md` | Upwork portfolio case study — skills, metrics, profile templates |
| `ARCHITECTURE.md` | System architecture, data flow, cache design |
| `API_DOCUMENTATION.md` | Complete REST API reference |
| `CODE_STYLE.md` | Coding standards and conventions |
| `CONTRIBUTING.md` | Developer onboarding guide |
| `SETUP.md` | Deployment and environment configuration |
| `extension/README.md` | Chrome extension documentation |
| `web-app/README.md` | Web application documentation |

## License

MIT
