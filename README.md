# NovelTrans — Multi-Tenant SaaS Translation Platform

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen?logo=spring)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-blue?logo=react)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5-blue?logo=typescript)](https://www.typescriptlang.org/)
[![Stripe](https://img.shields.io/badge/Stripe-Checkout-635BFF?logo=stripe)](https://stripe.com/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker)](https://docs.docker.com/compose/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

> A production-ready SaaS platform delivering AI-powered content translation across web, desktop, and browser extension — with subscription billing, team collaboration, and external API access.

## Live Demo

- **Web App**: _Update with your deployment URL before publishing_
- **Chrome Extension**: Available in `extension/` directory — load as unpacked extension in Chrome
- **API Documentation**: See `API_DOCUMENTATION.md`

---

## What It Does

NovelTrans is a full-stack SaaS platform that provides **multi-language content translation** through three delivery channels:

| Channel | Description |
|---------|-------------|
| **Web App** | React dashboard for document upload, translation history, glossary management, and team projects |
| **Chrome Extension** | In-browser translation with 3 modes: full-page, reader mode, and selection — with DOM-aware layout preservation |
| **External API** | REST API with API-key authentication (`nt_sk_xxxx`) for third-party integrations |

## Business Features

- **Tiered subscription plans** (FREE / PRO / MAX) with Stripe Checkout + Billing Portal + Webhooks
- **Usage-based quotas** — monthly character limits, daily caps, and per-tier concurrency controls
- **Team collaboration workspace** — invite members, assign chapters, review & approve workflow
- **API key management** — users generate keys for programmatic access with usage tracking
- **Email verification** — secure registration with time-limited OTP codes
- **Multi-tenant architecture** — tenant-scoped data isolation and routing
- **RAG translation memory** — Redis HNSW vector search for semantic matching of prior translations

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                        Users                                 │
│  ┌──────────┐    ┌──────────────┐    ┌───────────────────┐   │
│  │ Web App  │    │ Chrome Ext.  │    │ External API Keys │   │
│  │ (React)  │    │ (JS)         │    │ (REST)            │   │
│  └────┬─────┘    └──────┬───────┘    └─────────┬─────────┘   │
│       │                 │                       │             │
└───────┼─────────────────┼───────────────────────┼─────────────┘
        │                 │                       │
        ▼                 ▼                       ▼
┌───────────────────────────────────────────────────────────────┐
│                    Nginx Gateway (Port 7341)                   │
│   TLS termination · CORS · static files · API proxy           │
└──────────────────────────┬────────────────────────────────────┘
                           │
                           ▼
┌───────────────────────────────────────────────────────────────┐
│              Spring Boot Backend (Port 8080)                   │
│                                                                │
│  ┌────────────┐  ┌─────────────┐  ┌────────────────────────┐ │
│  │ REST APIs  │  │ JWT + API   │  │ Stripe Webhook Handler │ │
│  │ (15+ ctrl) │  │ Key Auth    │  │ (subscription events)  │ │
│  └──────┬─────┘  └──────┬──────┘  └────────────┬───────────┘ │
│         │               │                       │              │
│  ┌──────▼───────────────▼───────────────────────▼───────────┐ │
│  │              Service Layer                                │ │
│  │ TranslationPipeline · MultiAgentTranslationService        │ │
│  │ SubscriptionService · AuthService · DocumentService       │ │
│  └──────────────────────────┬───────────────────────────────┘ │
└─────────────────────────────┼─────────────────────────────────┘
                              │ HTTP
                              ▼
┌───────────────────────────────────────────────────────────────┐
│           Translation Microservices                           │
│                                                                │
│  ┌──────────────────────┐  ┌────────────────────────────┐    │
│  │ Python LLM Engine    │  │ MTranServer                │    │
│  │ FastAPI (Port 8000)  │  │ Lightweight (Port 8989)    │    │
│  │ OpenAI-compatible    │  │ Free engines fallback       │    │
│  └──────────────────────┘  └────────────────────────────┘    │
└───────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌───────────────────────────────────────────────────────────────┐
│                    Data Layer                                  │
│                                                                │
│  MySQL 8.0  ·  Redis Stack (cache + HNSW vector store)       │
│  ·  Caffeine (L1 local cache)  ·  RAG Translation Memory     │
└───────────────────────────────────────────────────────────────┘
```

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| **Backend** | Java 21, Spring Boot 3.2, MyBatis-Plus, Undertow |
| **Frontend** | React 19, TypeScript, Vite, TailwindCSS, Lucide Icons |
| **Chrome Extension** | Manifest V3, content scripts, messaging API |
| **Microservice** | Python 3.11, FastAPI, OpenAI SDK |
| **Database** | MySQL 8.0, Redis Stack (RediSearch + HNSW vectors) |
| **Payments** | Stripe Checkout, Billing Portal, Webhooks |
| **Infrastructure** | Docker Compose, Nginx reverse proxy |
| **Testing** | JUnit 5, Mockito, Spring Mock, Vitest, Playwright |

---

## Key Technical Decisions

### 1. Four-Level Translation Pipeline
Requests flow through L1 Caffeine cache → L2 Redis cache → L3 MySQL fallback → L4 RAG semantic match → direct LLM translation. This balances speed, cost, and quality across user tiers.

### 2. Dual Translation Engines
Python FastAPI LLM engine (high-quality, OpenAI-compatible) runs alongside MTranServer (lightweight free engines: Google, MyMemory, Libre). The pipeline routes based on availability and user tier.

### 3. Virtual Thread Concurrency
Java 21 virtual threads handle high-concurrency translation requests without thread-pool exhaustion. Per-user semaphores enforce concurrency limits: free=1, pro=3, max=5.

### 4. SSE Streaming for UX
Long-running translations stream results via Server-Sent Events, giving users real-time progress instead of loading spinners.

### 5. RAG Translation Memory
Redis HNSW vector index stores embeddings of prior translations. Semantic similarity matching finds near-matches even when source text isn't identical, reducing LLM API costs.

---

## User Tiers

| Tier | Monthly Characters | Daily Limit | Concurrency | Features |
|------|-------------------|-------------|-------------|----------|
| **FREE** | 100,000 | 500 | 1 | Basic translation, glossary |
| **PRO** | 500,000 | 5,000 | 3 | Document translation, advanced engines |
| **MAX** | 2,000,000 | Unlimited | 5 | Team collaboration, API access, priority |

Translation modes have character cost multipliers: Fast (0.5x), Expert (1.0x), Team (2.0x).

---

## Getting Started

```bash
# Clone the repository
git clone <your-repo-url>
cd novelTranslator

# Configure environment
cp .env.example .env
# Edit .env with your MySQL, Redis, Stripe, and LLM credentials

# Start all services
docker compose up -d

# Or run backend locally
mvn clean package -DskipTests
java -jar target/novelTranslator-0.0.1-SNAPSHOT.jar
```

---

## Project Structure

```
├── src/main/java/        # Spring Boot backend
│   ├── controller/       # REST endpoints (web, collab, external, plugin, shared)
│   ├── service/          # Business logic and orchestration
│   │   └── pipeline/     # TranslationPipeline component
│   ├── security/         # JWT auth, API key filter, tenant interceptor
│   ├── config/           # Stripe, Redis, RAG, tenant context
│   ├── entity/           # Domain models
│   ├── dto/              # Request/response objects
│   └── mapper/           # MyBatis-Plus data access
├── src/main/resources/
│   ├── sql/              # Database schema and migration scripts
│   └── application.yaml  # Spring Boot configuration
├── web-app/              # React frontend (TypeScript + Vite)
│   ├── src/pages/        # Page components
│   ├── src/api/          # API client and type definitions
│   └── src/components/   # Shared UI components
├── extension/            # Chrome browser extension
│   ├── src/background/   # Service worker
│   ├── src/content/      # Content scripts (webpage, reader, selection)
│   └── src/popup/        # Extension popup UI
├── services/translate-engine/  # Python translation microservice
├── nginx/                # Nginx gateway configuration
└── docker-compose.yml    # Full-stack orchestration
```

---

## API Highlights

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/user/login` | POST | Public | Email/password authentication, returns JWT |
| `/user/register` | POST | Public | Registration with email verification |
| `/subscription/checkout` | POST | JWT | Create Stripe Checkout session |
| `/subscription/verify` | GET | JWT | Verify payment status by session ID |
| `/subscription/status` | GET | JWT | Get current subscription state |
| `/v1/translate/webpage` | POST | Auth | Full-page translation (SSE streaming) |
| `/v1/translate/reader` | POST | Auth | Article extraction + translation |
| `/v1/translate/selection` | POST | Auth | Selected text translation |
| `/v1/external/translate` | POST | API Key | External API text translation |
| `/v1/collab/projects` | POST | JWT | Create team collaboration project |
| `/webhook/stripe` | POST | Webhook Sig | Stripe event handler |

---

## License

MIT
