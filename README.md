# NovelTrans — Multi-Tenant SaaS Translation Platform

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen?logo=spring)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-blue?logo=react)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5-blue?logo=typescript)](https://www.typescriptlang.org/)
[![Stripe](https://img.shields.io/badge/Stripe-Checkout-635BFF?logo=stripe)](https://stripe.com/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker)](https://docs.docker.com/compose/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)
[![Test Coverage](https://img.shields.io/badge/Coverage-58%25-brightgreen)](pom.xml)

> A production-ready SaaS platform delivering AI-powered content translation across web, desktop, and browser extension — with subscription billing, team collaboration, and external API access.

## Live Demo

- **Web App**: [https://your-domain.com](https://your-domain.com) *(update before publishing)*
- **Chrome Extension**: Available in `extension/` directory
- **API Documentation**: See `API_DOCUMENTATION.md`

---

## What It Does

NovelTrans is a full-stack SaaS platform that provides **multi-language content translation** through three delivery channels:

| Channel | Description |
|---------|-------------|
| **Web App** | React-based dashboard for document upload, translation history, glossary management, and team projects |
| **Chrome Extension** | In-browser translation with 3 modes: full-page, reader mode, and selection — with DOM-aware layout preservation |
| **External API** | REST API with API-key authentication for third-party integrations |

## Business Features

- **Tiered subscription plans** (FREE / PRO / MAX) with Stripe Checkout + Stripe Billing Portal
- **Usage-based quotas** — daily limits, character caps, concurrency controls per user tier
- **Team collaboration workspace** — invite members, assign chapters, review & approve workflow
- **API key management** — users generate keys for programmatic access with usage tracking
- **Email verification** — secure registration with time-limited OTP codes
- **Multi-tenant architecture** — tenant-scoped data isolation and routing

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
│           Python Translation Microservice (Port 8000)          │
│                                                                │
│  FastAPI · Multi-engine fallback (Google, DeepL, Baidu,       │
│  OpenAI, MyMemory, Libre) · AgentScope AI translation team    │
└───────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌───────────────────────────────────────────────────────────────┐
│                    Data Layer                                  │
│                                                                │
│  MySQL 8.0  ·  Redis (cache + vector store)  ·  RAG Memory   │
└───────────────────────────────────────────────────────────────┘
```

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| **Backend** | Java 21, Spring Boot 3.2, MyBatis-Plus, Undertow |
| **Frontend** | React 19, TypeScript, Vite, TailwindCSS, Lucide Icons |
| **Chrome Extension** | Manifest V3, content scripts, messaging API |
| **Microservice** | Python 3.11, FastAPI, AgentScope 1.0 |
| **Database** | MySQL 8.0, Redis Stack (RediSearch) |
| **Payments** | Stripe Checkout, Billing Portal, Webhooks |
| **Infrastructure** | Docker Compose, Nginx reverse proxy |
| **Testing** | JUnit 5, Mockito, Spring Mock |

---

## Key Technical Decisions

### 1. Multi-Engine Translation Fallback
Requests flow through a priority chain (free engines first, paid as fallback), minimizing API costs while maintaining availability.

### 2. Tiered Translation Pipeline
Four-level pipeline (cache → RAG memory → entity consistency → direct LLM) balances speed, cost, and quality across user tiers.

### 3. Virtual Thread Concurrency
Java 21 virtual threads handle high-concurrency translation requests without thread-pool exhaustion.

### 4. SSE Streaming for UX
Long-running translations stream results via Server-Sent Events, giving users real-time progress instead of loading spinners.

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

# Or run locally
mvn clean package -DskipTests
java -jar target/novelTranslator-0.0.1-SNAPSHOT.jar
```

---

## Project Structure

```
├── src/main/java/        # Spring Boot backend
│   ├── controller/       # REST endpoints (web, collab, external, plugin, shared)
│   ├── service/          # Business logic and orchestration
│   ├── security/         # JWT auth, API key filter, tenant interceptor
│   ├── config/           # Stripe, Redis, RAG, tenant context
│   ├── entity/           # Domain models
│   ├── dto/              # Request/response objects
│   └── mapper/           # MyBatis-Plus data access
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
| `/v1/translate/webpage` | POST | Public | Full-page translation (SSE streaming) |
| `/v1/translate/reader` | POST | Public | Article extraction + translation |
| `/v1/translate/selection` | POST | Public | Selected text translation |
| `/v1/external/translate` | POST | API Key | External API text translation |
| `/v1/collab/projects` | POST | JWT | Create team collaboration project |
| `/webhook/stripe` | POST | Webhook Sig | Stripe event handler |

---

## License

MIT
