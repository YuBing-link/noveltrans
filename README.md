# NovelTrans

A production SaaS backend for AI-powered novel/document translation — multi-engine orchestration, RAG translation memory, Stripe subscription management, team collaboration, and multi-tenant data isolation.

> [中文版](README.zh.md)

[![CI](https://github.com/YuBing-link/noveltrans/actions/workflows/ci.yml/badge.svg)](https://github.com/YuBing-link/noveltrans/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-green?logo=spring)](https://spring.io/)
[![Coverage](https://img.shields.io/badge/Coverage-80.5%25-brightgreen)](docs/coverage-report-summary.md)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

## Overview

NovelTrans is a full-stack translation platform built for web novel authors and translators. It replaces the traditional "copy-paste into Google Translate" workflow with an intelligent pipeline that understands context, preserves character name consistency, and learns from past translations — all while reducing LLM API costs through RAG-based semantic reuse.

Three client channels share the same backend:
- **React web dashboard** — DeepL-style interface with real-time chapter preview
- **Chrome extension (MV3)** — three modes: full-page, reader mode, text selection
- **External REST API** — API-key authenticated, for third-party integrations

## Features

- **Multi-engine AI orchestration** — Routes translation requests across LLM (Python FastAPI + OpenAI SDK) and local engines (MTranServer) with probability-based load balancing using rolling 60-second performance windows; MTranServer serves dual purpose — fast translation mode for instant results and automatic fallback when LLM degrades; bidirectional failover — calling code never sees which engine served the request
- **RAG translation memory** — Redis HNSW vector search with embedding-based semantic matching (OpenAI `text-embedding-3-small` or Ollama `bge-m3`); skips LLM calls when cosine similarity ≥ 0.85, reducing API costs for repetitive content
- **Entity consistency pipeline** — Extracts proper nouns via Python `/extract-entities`, replaces with SHA-256 placeholders (`__ENT_<hash>__`), translates entities separately against user glossary, then restores — prevents "John" becoming "Jon" across chapters
- **Multi-agent collaboration (AgentScope)** — Three AI agents (translator + terminologist + polisher) per chapter, with genre-matched prompts (battle, mystery, daily and more to add)
- **4-level translation pipeline** — L1: Caffeine → Redis → MySQL three-tier cache; L2: RAG semantic search; L3: entity extraction → placeholder substitution → translate → restore; L4: direct AI engine call. Each level can short-circuit
- **Subscription monetization (Stripe)** — Checkout + Billing Portal + Webhook + JWT revocation; 3-tier plan (FREE/PRO/MAX) with per-user concurrent semaphore + sliding-window TPM rate limiting + monthly character quota (Lua script atomic check)
- **Team workspaces** — Multi-tenant project management, chapter assignment, review-and-approve workflow with `CollabStateMachine`, CAS-based optimistic locking for status transitions, automatic retry with exponential backoff
- **Three-level payment idempotency** — Stripe webhook signature verification → Redis SETNX + 24h TTL → MySQL optimistic locking (`atomicUpdateSubscription`), safe under concurrent webhook redelivery
- **Resilience** — Resilience4j circuit breakers for engine calls, per-user semaphore concurrency control, exponential backoff retry, token-aware rate limiting with automatic refund on failure

## Architecture

![Architecture Diagram](docs/architecture.svg)

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

**Pragmatic hexagonal architecture.**
Domain Models are pure POJOs; Domain Services use Spring `@Service` for practical DI. All infrastructure access goes through port interfaces.


**Two-level per-user rate limiting.**
Concurrency: each user gets an independent `Semaphore` (FREE=1, PRO=3, MAX=5). Throughput: sliding-window TPM limiter with estimated token cost per request and automatic refund on failure. Idle semaphores are cleaned every 30 minutes. Monthly character quota uses Lua script atomic check + INCR in Redis with MySQL fallback.

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

All endpoints below are accessed through Nginx at port 7341. Web dashboard requests use the `/api/` prefix (Nginx strips `/api` before forwarding), plugin and external API routes use `/v1/` (passed through as-is), and Stripe webhooks use `/webhook/` (passed through as-is).

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
| **Webhook** | `POST /webhook/stripe` | `POST /api/webhook/stripe` | Stripe Signature |
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

For detailed setup instructions, see [`SETUP.md`](docs/SETUP.md).

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

## Roadmap

| Area | Current Status | Plan |
|------|---------------|------|
| **API Gateway Routing** | `/api/v1/**` (rewrite) and `/v1/**` (passthrough) coexist | Consolidate to single external prefix (`/api/v1/**` only) |
| **Nginx / Gateway HA** | Single Nginx instance | Dual Nginx + Keepalived/VIP, or cloud LB (ALB/NLB) |
| **Stripe Webhook Reliability** | Synchronous processing (full DB tx before returning 200) | Return 200 immediately, enqueue to Redis Stream / MQ, process async |
| **Engine Call Resilience** | Python FastAPI path has connect/read timeouts but no circuit breaker | Add Resilience4j CB + fallback; unify under `TranslationEnginePort` |
| **Service HA** | All components single-instance | Spring Boot multi-instance + service discovery; MySQL primary-replica; Redis Sentinel/Cluster |
| **Observability** | No APM or distributed tracing | Jaeger/SkyWalking for tracing; ELK/Loki for log aggregation |
| **Message Queue** | CollabStateMachine and Quota operations are synchronous | RabbitMQ/RocketMQ for async chapter events, quota audit |
| **Object Storage** | Document uploads stored locally | S3/MinIO for PDF/Word translation documents |
| **Vector Store Scale** | Redis HNSW sufficient for <1M vectors | Evaluate Milvus/Pinecone if scale exceeds 1M entries |
| **Plugin Engine Selection** | Extension shows multiple engine options but routing ignores user choice | Route by actual user preference with proper fallback; remove fake options |
| **Plugin DOM Translation** | Basic text replacement, page layout may break | Structure-aware DOM translation preserving layout, images, and interactive elements |

> The hexagonal port/adapter design ensures these upgrades remain non-breaking.

## Reports

Live reports hosted on GitHub Pages:

| Report | Description |
|--------|-------------|
| [API Documentation](https://YuBing-link.github.io/noveltrans/api.html) | Interactive OpenAPI spec (ReDoc) — 30+ endpoints |
| [JaCoCo Coverage](https://YuBing-link.github.io/noveltrans/coverage/) | 80.5% instruction coverage, 115 test files |
| [k6 Load Test](https://YuBing-link.github.io/noveltrans/load-test/report.html) | 200 VU stress test with latency/RPS metrics |

> Also available locally: [`docs/openapi.json`](docs/openapi.json) for import into [Swagger Editor](https://editor.swagger.io/) or Postman.

## Documentation

| Document | Purpose |
|----------|---------|
| [`ARCHITECTURE.md`](docs/ARCHITECTURE.md) | System architecture, component responsibilities, data flow, cache hierarchy |
| [`API_DOCUMENTATION.md`](docs/API_DOCUMENTATION.md) | Full REST API reference with request/response examples |
| [`SETUP.md`](docs/SETUP.md) | Deployment & local development guide |
| [`ADR.md`](docs/ADR.md) | Architecture decision records |
| [`CONTRIBUTING.md`](docs/CONTRIBUTING.md) | Contribution guidelines |
| [`Coverage Summary`](docs/coverage-report-summary.md) | Markdown coverage summary with top uncovered classes |

## License

[MIT](LICENSE)
