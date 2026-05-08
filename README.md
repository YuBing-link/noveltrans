# NovelTrans

A production SaaS backend for AI-powered novel/document translation — multi-engine orchestration, RAG translation memory, Stripe subscription management, team collaboration, and multi-tenant data isolation.

> [中文版](README.zh.md)

[![CI](https://github.com/YuBing-link/noveltrans/actions/workflows/ci.yml/badge.svg)](https://github.com/YuBing-link/noveltrans/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-green?logo=spring)](https://spring.io/)
[![Coverage](https://img.shields.io/badge/Coverage-86%25-brightgreen)]()
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

## Overview

NovelTrans is a full-stack translation platform built for web novel authors and translators. It replaces the traditional "copy-paste into Google Translate" workflow with an intelligent pipeline that understands context, preserves character name consistency, and learns from past translations — all while reducing LLM API costs by 60-80% through RAG-based semantic reuse.

Three client channels share the same backend:
- **React web dashboard** — DeepL-style interface with real-time chapter preview
- **Chrome extension (MV3)** — three modes: full-page, reader mode, text selection
- **External REST API** — API-key authenticated, for third-party integrations

## Features

- **Multi-engine AI orchestration** — Routes translation requests across LLM (Python FastAPI + OpenAI SDK) and local engines (MTranServer) with probability-based load balancing using rolling 60-second performance windows; MTranServer serves dual purpose — fast translation mode for instant results and automatic fallback when LLM degrades; bidirectional failover — calling code never sees which engine served the request
- **RAG translation memory** — Redis HNSW vector search with embedding-based semantic matching (OpenAI `text-embedding-3-small` or Ollama `bge-m3`); skips LLM calls when cosine similarity ≥ 0.85, reducing API costs by 60-80%
- **Entity consistency pipeline** — Extracts proper nouns via Python `/extract-entities`, replaces with SHA-256 placeholders (`__ENT_<hash>__`), translates entities separately against user glossary, then restores — prevents "John" becoming "Jon" across chapters
- **Multi-agent collaboration (AgentScope)** — Three AI agents (translator + terminologist + polisher) per chapter, with genre-specific prompts for fantasy, romance, scifi, mystery, horror, and daily styles
- **4-level translation pipeline** — L1: Caffeine → Redis → MySQL three-tier cache; L2: RAG semantic search; L3: entity extraction → placeholder substitution → translate → restore; L4: direct AI engine call. Each level can short-circuit
- **Subscription monetization (Stripe)** — Checkout + Billing Portal + Webhook + JWT revocation; 3-tier plan (FREE/PRO/MAX) with per-user concurrent semaphore + sliding-window TPM rate limiting + monthly character quota (Lua script atomic check)
- **Team workspaces** — Multi-tenant project management, chapter assignment, review-and-approve workflow with `CollabStateMachine`, CAS-based optimistic locking for status transitions, automatic retry with exponential backoff
- **Three-level payment idempotency** — Stripe webhook signature verification → Redis SETNX + 24h TTL → MySQL optimistic locking (`atomicUpdateSubscription`), safe under concurrent webhook redelivery
- **Resilience** — Resilience4j circuit breakers for engine calls, per-user semaphore concurrency control, exponential backoff retry, token-aware rate limiting with automatic refund on failure

## Architecture

```
┌──────────┐
│ Chrome   │────┐
│ Extension│    │   ┌──────────┐    ┌──────────────────────────────────────────┐
│          │    ├──▶│  Nginx   │───▶│        Spring Boot Backend (Undertow)    │
└──────────┘    │   │ :7341    │    │  ┌────────────────────────────────────┐  │
┌──────────┐    │   │          │    │  │   Application Layer (Use Cases)    │  │
│ Web App  │────┘   │  · /   static│  │   SubscriptionAppService           │  │
│ React+TS │        │  · /api → API│  │   TranslationAppService            │  │
│          │        │  · /v1  → API│  │   AuthAppService                   │  │
└──────────┘        └──────────┘    │  └───────────────┬────────────────────┘  │
                                    │                  │                       │
                                    │  ┌───────────────┴────────────────────┐  │
                                    │  │      Domain Layer (Pure Java)      │  │
                                    │  │   TranslationPipeline (4 levels)   │  │
                                    │  │   EntityConsistencyService         │  │
                                    │  │   MultiAgentTranslationService     │  │
                                    │  │   CollabStateMachine + Quota       │  │
                                    │  └───────────────┬────────────────────┘  │
                                    │                  │                       │
                                    │  ┌───────────────┴────────────────────┐  │
                                    │  │   Port/Out Interfaces              │  │
                                    │  │   (Repository, Cache, Payment)     │  │
                                    │  └───────────────┬────────────────────┘  │
                                    └────────┬─────────┼───────────────────────┘
                                             │         │
             ┌──────────────────┬────────────┼─────────┼──────────┬────────────────────┐
             │                  │            │         │          │                    │
        ┌────┴─────┐     ┌─────┴──────┐     │    ┌────┴─────┐   ┌┴────────────┐
        │  MySQL   │     │    Redis   │     │    │  Python  │   │  MTranServer │
        │ 8.0      │     │  Stack 7.4 │     │    │ FastAPI  │   │  Local :8989 │
        │ Flyway   │     │  HNSW      │     │    │ :8000    │   │  lightweight │
        └──────────┘     └────────────┘     │    └──────────┘   └─────────────┘
        Persistence      Cache + Vectors    │    Multi-agent     Fast mode
                                            │
                                 ┌──────────┴──────────┐          External
                                 │      Stripe         │
                                 │  Checkout / Portal  │
                                 │  Webhook (callback) │
                                 └─────────────────────┘
                                   Subscription & Billing
```

## Tech Stack

| Layer | Technology |
|-------|------------|
| **Backend** | Java 21, Spring Boot 3.2.0 (Undertow), Virtual Threads, WebFlux |
| **Persistence** | MySQL 8.0, MyBatis-Plus 3.5.5, Flyway migrations |
| **Security** | Spring Security + JWT (auth0), BCrypt, API Key auth |
| **AI Orchestration** | AgentScope 1.0.1, TranslationPipeline, MultiAgentTranslationService |
| **Vector Search** | Redis Stack 7.4 (HNSW), OpenAI `text-embedding-3-small` / Ollama `bge-m3` |
| **Payments** | Stripe SDK 24.20.0 (Checkout, Billing Portal, Webhook) |
| **Caching** | Caffeine L1 → Redis L2 → MySQL L3, Redis HNSW vector index, Cache PubSub |
| **Resilience** | Resilience4j 2.2.0 circuit breakers, per-user Semaphore, exponential backoff |
| **Testing** | JUnit 5, Mockito, JaCoCo (86% coverage), k6 load testing |
| **Infrastructure** | Docker Compose (6 containers), Nginx 1.28-alpine, GitHub Actions |
| **Frontend** | React 19 + TypeScript + Vite (web), Chrome Extension MV3 |
| **Python Engine** | FastAPI + OpenAI SDK (multi-agent translation microservice) |

## Key Technical Decisions

**Strict hexagonal architecture with application layer.**
Domain layer is pure Java — zero framework annotations, zero ORM references, zero HTTP types. All infrastructure access goes through port interfaces. Stripe SDK is isolated behind `PaymentPort`; Redis behind `CachePort` and `QuotaPort`; MySQL behind `BillingRepositoryPort`. Security filters are instantiated with `new` in `SecurityConfig.filterChain()` rather than `@Component` to avoid Spring Security's CGLIB proxy ordering conflicts.

**Two-level per-user rate limiting.**
Concurrency: each user gets an independent `Semaphore` (FREE=2, PRO=5, MAX=10). Throughput: sliding-window TPM limiter with estimated token cost per request and automatic refund on failure. Idle semaphores are cleaned every 30 minutes. Monthly character quota uses Lua script atomic check + INCR in Redis with MySQL fallback.

**Statistics-based multi-engine routing.**
Two engines (Python LLM at :8000, local MTranServer at :8989) with rolling 60-second performance windows. Each engine's "excellent rate" (response ≤ 1000ms) determines selection probability. MTranServer serves dual purpose — fast translation mode for quick results and automatic fallback when the LLM engine degrades. Bidirectional failover — calling code never sees which engine served the request.

**Three-level payment idempotency.**
Stripe webhook signature verification rejects forged callbacks. Redis SETNX + 24h TTL prevents duplicate processing across instances. MySQL `atomicUpdateSubscription` with optimistic locking (`WHERE last_webhook_event_id IS NULL OR last_webhook_event_id != ?`) prevents race conditions within a single database.

**4-level translation pipeline.**
L1: Caffeine → Redis → MySQL three-tier cache (exact match returns immediately). L2: RAG semantic search (HNSW cosine ≥ 0.85 skips LLM). L3: entity extraction → placeholder substitution → translate entities against glossary → restore. L4: AI engine call with quality validation (ad keyword detection, length anomaly check, Chinese character fix). Each level can short-circuit.

**Collaboration state machine.**
Chapter tasks transition through a finite state machine with CAS-based optimistic locking (`UPDATE ... WHERE version = ?`). On conflict, automatic retry with exponential backoff. Event-driven chapter splitting via `CollabChapterSplitEvent`.

## REST API Endpoints

<details>
<summary>Click to expand</summary>

All endpoints below are accessed through Nginx at port 7341. External requests use the `/api/` prefix (stripped by Nginx), while plugin and external API routes use `/v1/` (passed through as-is).

| Category | Backend Path | External Path (via Nginx) | Auth |
|----------|-------------|--------------------------|------|
| **Auth** | `POST /user/register`, `POST /user/login`, `GET /user/verify` | `POST /api/user/register`, etc. | Public |
| **User** | `GET/PUT /user`, `GET /user/preferences`, `PUT /user/password`, `GET /user/profile` | `GET /api/user`, etc. | JWT |
| **Translation** | `POST /v1/translate/selection`, `POST /v1/translate/reader`, `GET /v1/translate/task/{id}` | `POST /api/v1/translate/selection`, etc. | JWT |
| **SSE Stream** | `POST /v1/translate/text/stream`, `POST /v1/translate/document/stream` | `POST /api/v1/translate/text/stream` | JWT |
| **Document** | `CRUD /user/documents` | `GET/POST/DELETE /api/user/documents` | JWT |
| **Glossary** | `CRUD /user/glossaries`, `POST /user/glossaries/import` | `GET/POST/DELETE /api/user/glossaries` | JWT |
| **Subscription** | `POST /subscription/checkout`, `POST /subscription/portal`, `GET /subscription/status` | `POST /api/subscription/checkout`, etc. | JWT |
| **Collaboration** | `CRUD /v1/collab/projects`, `POST /v1/collab/chapters`, `POST /v1/collab/comments` | `GET/POST /api/v1/collab/projects`, etc. | JWT + Project Access |
| **API Keys** | `CRUD /user/api-keys` | `GET/POST/DELETE /api/user/api-keys` | JWT |
| **Plugin** | `POST /v1/translate/premium-reader`, `POST /v1/translate/premium-selection` | `POST /v1/translate/premium-reader` | API Key / JWT |
| **External** | `POST /v1/external/translate` | `POST /v1/external/translate` | API Key |
| **Webhook** | `POST /stripe/webhook` | `POST /api/stripe/webhook` | Stripe Signature |
| **Admin** | `POST /admin/cache/evict`, `POST /admin/cache/stats` | `POST /api/admin/cache/evict` | Admin JWT |
| **Platform** | `GET /platform/stats`, `GET /platform/statistics` | `GET /api/platform/stats` | Admin JWT |

</details>

## Quick Start

```bash
git clone https://github.com/YuBing-link/noveltrans.git
cd noveltrans
cp .env.example .env
# Edit .env with your MySQL, Stripe, and LLM credentials

docker compose up -d
```

Initial startup may take 5-10 minutes. Open [http://localhost:7341](http://localhost:7341).

For detailed setup instructions, see [`SETUP.md`](SETUP.md).

## Project Structure

```
noveltrans/
├── src/main/java/                  # Spring Boot backend (Java 21)
│   ├── adapter/in/                 # REST controllers, security filters, webhook
│   ├── adapter/out/                # Persistence, Redis, Stripe, translation, email
│   ├── application/service/        # Use case orchestration (application layer)
│   ├── domain/                     # Pure business logic, models, pipeline
│   ├── port/in/                    # Inbound port interfaces (use case contracts)
│   ├── port/out/                   # Outbound port interfaces (infrastructure contracts)
│   └── config/                     # Spring configuration + cross-cutting
├── src/main/resources/             # Application config, SQL migrations, templates
├── src/test/java/                  # Unit + integration tests
├── web-app/                        # React web dashboard (TypeScript + Vite)
├── extension/                      # Chrome browser extension (MV3)
├── services/translate-engine/      # Python translation microservice
├── nginx/                          # Nginx gateway configuration
├── load-test/                      # k6 load testing scripts
├── docker-compose.yml              # Full-stack orchestration (6 containers)
└── .env.example                    # Environment variable template
```

## Documentation

| Document | Purpose |
|----------|---------|
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | System architecture, component responsibilities, data flow, cache hierarchy |
| [`API_DOCUMENTATION.md`](API_DOCUMENTATION.md) | Full REST API reference with request/response examples |
| [`SETUP.md`](SETUP.md) | Deployment & local development guide |
| [`ADR.md`](ADR.md) | Architecture decision records |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | Contribution guidelines |

## License

[MIT](LICENSE)
