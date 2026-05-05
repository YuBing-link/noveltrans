# NovelTrans

A SaaS translation platform for web novel authors and translators — batch-translate long-form content with RAG-powered translation memory, multi-agent collaboration, team workspaces, and Stripe billing.

> [中文版](README.zh.md)

[![CI](https://github.com/YuBing-link/noveltrans/actions/workflows/ci.yml/badge.svg)](https://github.com/YuBing-link/noveltrans/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen?logo=spring)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-blue?logo=react)](https://react.dev/)
[![Coverage](https://img.shields.io/badge/Coverage-86%25-brightgreen)]()

## ✨ Features

- **Translate long-form content** — AI-powered chapter-by-chapter translation with multi-agent collaboration (translator + terminologist + polisher)
- **Reuse past translations** — RAG translation memory using Redis HNSW vectors reduces 60-80% of LLM API calls by semantically matching similar source text
- **Collaborate as a team** — project/workspace management, chapter assignment, review-and-approve workflow
- **Three delivery channels** — React web dashboard, Chrome extension (MV3, 3 translation modes), and external REST API with API-key authentication
- **Monetize with subscriptions** — Stripe Checkout + Billing Portal + Webhook, 3-tier plans (FREE / PRO / MAX) with usage quotas

## 🏆 Technical Highlights

| Area | Implementation |
|------|----------------|
| **Distributed cache consistency** | Version-stamped cache with delayed double-delete, Redis pub/sub for cross-instance invalidation, and **term-aware reverse index for fine-grained glossary cache invalidation** (only affected entries expire, not global) |
| **Virtual threads** | Java 21 virtual threads throughout — async cache eviction, multi-agent fan-out, and HTTP client I/O |
| **Cache chain** | 4-tier: Caffeine L1 (10 min) → Redis L2 (30 min) → MySQL L3 (24 h) → RAG semantic match (permanent) |
| **Webhook idempotency** | 5-layer defense-in-depth (signature → Redis SETNX → DB lastWebhookEventId → DuplicateKeyException → timestamp ordering), with **`invoice.payment_succeeded` as fallback activation path** |
| **Engine resilience** | LLM + MTranServer dual-engine with health check, circuit breaker cooling, and probabilistic routing |
| **RAG vector search** | Redis Stack HNSW index with KNN semantic search, user-scoped isolation, automatic quality filtering |
| **Rate limiting** | **IP-level Redis sliding window** for `/v1/translate/**` + tiered per-user concurrency (anonymous / free / pro / API key) |
| **Async batch processing** | **Event-driven chapter split**: narrow DB transaction + `@Async` batch insert (50/batch) + scheduled compensation task for crash recovery |
| **SSE reliability** | **Redis Stream message replay** — clients reconnect with `lastEventId` to recover missed collaboration events |
| **State machine** | **Driving state machine** — `transitionProject()` and `transitionChapter()` encapsulate validate + set, preventing status bypass |
| **Testing** | 86% instruction / 75% branch coverage with JUnit 5 + Vitest + Playwright + k6 load testing |

## 🛠️ Quick Start

> For detailed deployment steps, local development setup, and troubleshooting, see [`SETUP.md`](SETUP.md).

### Prerequisites

| Dependency | Version | Purpose |
|-----------|---------|---------|
| Docker | 24+ | Container runtime |
| Docker Compose | 2.20+ | Container orchestration |
| npm | 9+ | Frontend build |

### 1. Clone and Configure

```bash
git clone https://github.com/YuBing-link/noveltrans.git
cd noveltrans

cp .env.example .env
# Edit .env with your MySQL, Stripe, and LLM credentials
```

### 2. Build the Frontend

```bash
cd web-app
npm install
npm run build
cd ..
```

### 3. Start All Services

```bash
docker compose up -d
```

Initial startup may take 5-10 minutes (Maven dependency downloads + Ollama model pull).

### 4. Verify

```bash
docker compose ps          # All containers should be healthy
curl http://localhost:7341/health  # Backend health check
```

Open [http://localhost:7341](http://localhost:7341) in your browser.

## 📦 Tech Stack

| Layer | Technology |
|-------|------------|
| **Backend** | Java 21, Spring Boot 3.2, MyBatis-Plus, Undertow, Virtual Threads |
| **Frontend** | React 19, TypeScript, Vite, TailwindCSS 4.2, i18next |
| **Chrome Extension** | Manifest V3, Content Scripts, IndexedDB |
| **Translation Engine** | Python 3.11, FastAPI, OpenAI SDK, AgentScope (multi-agent) |
| **Neural Translation Machine** | MTranServer — lightweight open-source translation engine |
| **Database** | MySQL 8.0, Redis Stack (RediSearch + HNSW vectors) |
| **Embeddings** | Ollama (bge-m3) / OpenAI text-embedding-3-small |
| **Payments** | Stripe Checkout, Billing Portal, Webhooks |
| **Gateway** | Nginx (single entry point, port 7341) |
| **Testing** | JUnit 5, Mockito, Vitest, Playwright, k6 |
| **CI/CD** | GitHub Actions (build, test, Docker Compose smoke test) |

## 🏗️ Project Structure

```
noveltrans/
├── src/main/java/          # Spring Boot backend (Java 21)
├── src/test/java/          # Unit + integration tests (80+ classes)
├── web-app/                # React web dashboard (TypeScript + Vite)
├── extension/              # Chrome browser extension (MV3)
├── services/translate-engine/  # Python translation microservice + multi-agent pipeline
├── nginx/                  # Nginx gateway configuration
├── load-test/              # k6 load testing scripts
├── docker-compose.yml      # Full-stack orchestration (6 containers)
└── .env.example            # Environment variable template
```

## 🔌 API Highlights

All API requests go through Nginx at **port 7341**.

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/user/login` | POST | Public | Email/password login, returns JWT |
| `/api/subscription/checkout` | POST | JWT | Create Stripe Checkout session |
| `/v1/translate/webpage` | POST | Auth | Full-page translation (SSE streaming) |
| `/v1/translate/reader` | POST | Auth | Article extraction + translation |
| `/v1/external/translate` | POST | API Key | External REST API text translation |
| `/api/collab/projects` | POST | JWT | Create team collaboration project |
| `/webhook/stripe` | POST | Webhook Sig | Stripe event handler |

Full API documentation: See `API_DOCUMENTATION.md`

## 🔑 Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `MYSQL_HOST` / `MYSQL_PASSWORD` | MySQL connection | Yes |
| `REDIS_HOST` / `REDIS_PASSWORD` | Redis connection | Yes |
| `JWT_SECRET` | JWT signing key (min 32 chars) | Yes |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP for email verification | Yes |
| `STRIPE_SECRET_KEY` | Stripe secret key (`sk_test_` or `sk_live_`) | Yes |
| `STRIPE_WEBHOOK_SECRET` | Webhook signature verification | Yes |
| `LLM_API_KEY` / `LLM_BASE_URL` | LLM translation engine credentials | Optional |
| `MTRAN_PORT` / `MTRAN_API_KEY` | MTranServer neural translation machine | Optional |
| `EMBEDDING_PROVIDER` | `openai` or `ollama` for RAG vectors | Yes |
| `TRANSLATE_SERVICE_API_KEY` | Internal service-to-service auth | Yes |

See `.env.example` for the full list.

## 📊 Architecture

> For a deep dive into system design, data flow, cache hierarchy, and deployment topology, see [`ARCHITECTURE.md`](ARCHITECTURE.md).

```
                    ┌─────────────────────────────────────┐
                    │         Nginx (port 7341)           │
                    │   SPA + API reverse proxy + CORS    │
                    └──────────┬──────────────────────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
              ▼                ▼                ▼
     ┌────────────┐   ┌────────────┐   ┌─────────────┐
     │  React SPA │   │ Spring Boot│   │  External   │
     │ (web-app)  │   │  (Java 21) │   │   Clients   │
     └────────────┘   └──────┬─────┘   │ (API Key)   │
                             │         └─────────────┘
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
         ┌────────┐   ┌──────────┐   ┌──────────────┐
         │ MySQL  │   │  Redis   │   │ Python FastAPI│
         │  8.0   │   │ (cache + │   │ + MTranServer│
         │        │   │ vectors +│   │ (neural MT)  │
         │        │   │ pub/sub) │   │              │
         └────────┘   └──────────┘   └──────────────┘
```

### Translation Pipeline

```
New Translation Request
  │
  ├── L1: Caffeine local cache (10 min TTL)
  ├── L2: Redis distributed cache (30 min TTL)
  ├── L3: MySQL persistent cache (24h TTL)
  ├── L4: RAG semantic match (permanent) ← vector similarity search
  └── L5: Direct LLM / MTranServer translation (fallback)
```

### Cache Consistency Strategy

```
Data Change (update/delete translation memory)
  │
  ├── Step 1: Clear L1 (Caffeine) + L2 (Redis)  — pre-delete
  ├── Step 2: Bump version number in Redis + publish pub/sub event
  ├── Step 3: Sleep 2s (wait for in-flight writes to complete)
  ├── Step 4: Clear L2 Redis old-version keys + expire L3 old records — post-delete
  └── Step 5: All instances receive pub/sub, flush their L1 local cache

Glossary Change (add/update/delete term)
  │
  ├── On putCache(): extract words (length >= 3) from sourceText
  ├── For each word: SADD glossary:cache_keys:{word} {cacheKey}
  ├── On glossary change: SMEMBERS glossary:cache_keys:{term}
  ├── Delete only affected keys across L1, L2, L3
  └── Avoids global cache stampede — only matching entries invalidated
```

<details>
<summary>Click to expand: RAG Translation Memory</summary>

- **Vector semantic search**: Redis Stack HNSW vector index, encode source text as embeddings, KNN search historical translations
- **Four-tier cache chain**: Caffeine (L1) → Redis (L2) → MySQL (L3) → RAG semantic search (L4) → Translation engine
- **Quality filtering**: Auto-filter empty translations, length anomalies, ad keywords, excessive special characters before storage
- **Dual degradation**: Auto-fallback from Redis KNN to MySQL cosine similarity when Redis search is unavailable
- **User isolation**: KNN queries scoped by `user_id` + `target_lang`, preventing data cross-contamination
</details>

<details>
<summary>Click to expand: Translation Pipeline</summary>

- **Single pipeline component**: `TranslationPipeline` encapsulates four-tier translation logic, eliminating code duplication
- **Strategy pattern**: Configurable stages — `execute()` for full pipeline, `executeFast()` for cache + direct translation only
- **Post-processing**: All translation paths include `fixUntranslatedChinese()` post-processing
- **Quality validation**: Static `isValidTranslation()` and `shouldCache()` methods ensure consistent quality checks
</details>

<details>
<summary>Click to expand: Translation Engine</summary>

- **OpenAI-compatible API**: Supports OpenAI GPT, Claude, local Ollama, DeepSeek, and any compatible endpoint
- **Professional novel translation prompt**: 6 translation principles ensuring literary translation quality
- **Dual-engine fault tolerance**: LLM engine + MTranServer lightweight engine with bidirectional degradation, health check + circuit breaker cooling
- **Probabilistic routing**: Smart engine selection based on historical success rate + response time
</details>

<details>
<summary>Click to expand: Cache Protection</summary>

- **Cache penetration prevention**: Null placeholder + short TTL strategy
- **Cache breakdown prevention**: `ConcurrentHashMap` per-key concurrent locking
- **Cache avalanche prevention**: TTL random jitter
- **Cache consistency**: Version-stamped delayed double-delete + Redis pub/sub cross-instance invalidation
</details>

<details>
<summary>Click to expand: Security</summary>

- **JWT + API Key dual authentication**: Both routes share the same translation pipeline
- **Translation endpoint enforcement**: All `/v1/translate/**` endpoints require valid JWT or API Key
- **API Key management**: `nt_sk_xxxx` prefix + 32 random chars, list display with mask
- **BCrypt password hashing**: User passwords hashed before storage
- **Email verification**: Double verification for registration / password reset
- **IP-level rate limiting**: Redis Sorted Set sliding window (100 req/60s per IP) on all `/v1/translate/**` endpoints, placed before JWT filter in Security filter chain. Skips API Key authenticated requests.
- **Tiered per-user rate limiting**: Different concurrency limits for anonymous / free / pro users
</details>

<details>
<summary>Click to expand: Character Quota System</summary>

| Tier | Monthly Char Pack | Fast (×0.5) | Expert (×1.0) | Team (×2.0) |
|------|----------|-------------|-------------|-------------|
| **Free** | 10,000 | 20k chars original | 10k chars original | 5k chars original |
| **Pro** | 50,000 | 100k chars original | 50k chars original | 25k chars original |
| **Max** | 200,000 | 400k chars original | 200k chars original | 100k chars original |

- **Mode coefficient**: Fast ×0.5 (save quota), Expert ×1.0, Team ×2.0
- **Deduction per request**: Quota pre-checked before translation begins
- **Daily tracking**: `quota_usage` table records daily consumption, monthly aggregation
- **Auto-reset monthly**: Scheduled task clears expired records on the 1st of each month
</details>

## 📈 Performance

Tested with k6 (translation + checkout) and Python multi-threaded HTTP (webhook). Full report: [`load-test/STRESS_TEST_REPORT.md`](load-test/STRESS_TEST_REPORT.md)

### Translation API

| Scenario | Throughput | p95 Latency | Error Rate |
|---|---|---|---|
| Real-world (1s user think time) | **93.7 req/s** | 303 ms | 0% |
| Max throughput (no sleep) | **496.8 req/s** | 302 ms | 0% |

### Stripe Payment Checkout

| Scenario | Throughput | p95 Latency | Error Rate |
|---|---|---|---|
| Real-world (1s user think time) | **16.9 req/s** | 2,572 ms | 0% |
| Max throughput (no sleep) | **23.5 req/s** | 2,093 ms | 0% |

> Checkout latency is dominated by Stripe API round-trip (~2s), not application processing.

### Webhook Idempotency (50 concurrent threads)

| Test | Requests | Duplicate Records | Status |
|---|---|---|---|
| `checkout.session.completed` (50×10) | 500 | 1 | Pass |
| `subscription.updated` (100×10) | 1,000 | 0 | Pass |
| Mixed events (50×5×3 types) | 750 | 2 | Pass |

The mixed-event race condition was fixed across 3 rounds: atomic conditional updates → DuplicateKeyException handling → Redis SETNX cross-event dedup.

## ⚠️ Known Issues

- **Chrome extension translation engine selection not connected** — When you select "Google Translate" or another engine in the Chrome extension popup, all translation still goes through the LLM-based translation engine. The engine selector UI works but the selected engine value is not wired to the actual translation request. This does not affect project functionality.

## 🚀 Future Directions

### API Intelligent Dispatch Gateway

A smart multi-provider API gateway that sits between the translation engine and upstream LLM providers (Claude, GPT-4, DeepSeek, etc.).

**Expert Mode** — single-pass, task-exclusive translation:
- Each translation request occupies 1 API concurrency slot exclusively
- No slot contention — once assigned, the slot is held until task completion

**Collaboration Mode** — multi-chapter, resumable translation:
- Projects maintain a **priority model chain** (e.g., Claude → GPT-4 → DeepSeek)
- Before hitting the primary provider's concurrency ceiling, requests are pre-scheduled
- When the primary model reaches max concurrency, the user is prompted: *queue for style consistency, or fall back to an alternate model?*
- Fallback models are stored as secondary preferences for the project, enabling intelligent degradation
- Style consistency is preserved by minimizing model switches within the same chapter block

**Architecture:**
```
                  Translation Request
                         │
                         ▼
              ┌─────────────────────┐
              │  Dispatch Gateway   │
              │  ┌───────────────┐  │
              │  │ Project Model │  │
              │  │ Priority Chain│  │
              │  └───────┬───────┘  │
              │          │          │
              │  ┌───────▼───────┐  │
              │  │ Concurrency   │  │
              │  │ Pool Manager  │  │
              │  └───────┬───────┘  │
              │          │          │
              │  ┌───────▼───────┐  │
              │  │ Queue + Fallback│ │
              │  │ Strategy       │  │
              │  └───────────────┘  │
              └─────────┬───────────┘
                        │
           ┌────────────┼────────────┐
           ▼            ▼            ▼
      ┌────────┐  ┌────────┐  ┌────────┐
      │ Claude │  │  GPT   │  │DeepSeek│
      │ (max 5)│  │(max 10)│  │(max 20)│
      └────────┘  └────────┘  └────────┘
```

**Key design goals:**
- Per-provider configurable `max_concurrency`, `cost_per_token`, `quality_tier`
- Time-bounded queuing (auto-fallback after timeout, not infinite wait)
- Project-scoped `preferred_models` persistence — once a fallback proves good, it becomes the project's second choice automatically
- Minimum model-switch interval to avoid "style fragmentation" across consecutive chapters

## 🗺️ Roadmap

- [x] User authentication and email verification
- [x] Stripe subscription billing (FREE / PRO / MAX)
- [x] RAG translation memory with Redis HNSW
- [x] Multi-agent collaborative translation
- [x] Team collaboration workspace
- [x] Chrome extension (3 translation modes)
- [x] External REST API with API-key auth
- [x] Cache consistency with version stamp and Redis pub/sub
- [x] CI/CD pipeline (GitHub Actions: build, test, Docker smoke test)
- [ ] API intelligent dispatch gateway
- [ ] WebSocket real-time translation progress
- [ ] Document format support (PDF, EPUB)
- [ ] Machine translation quality scoring dashboard

## 📚 Documentation

| Document | Purpose |
|----------|---------|
| [`SETUP.md`](SETUP.md) | Detailed deployment & local development guide |
| [`API_DOCUMENTATION.md`](API_DOCUMENTATION.md) | Complete REST API reference |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | System architecture, data flow, cache hierarchy, deployment topology |
| [`test-coverage-report.md`](test-coverage-report.md) | JaCoCo test coverage report (86% instruction / 75% branch) |
| [`load-test/STRESS_TEST_REPORT.md`](load-test/STRESS_TEST_REPORT.md) | k6 + Python multi-threaded stress test results |
| [`CODE_STYLE.md`](CODE_STYLE.md) | Coding standards, naming conventions, package structure |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | Git workflow, commit conventions, PR process |

## 🤝 Contributing

Bug fixes, documentation improvements, and new translation engine integrations are welcome.

For setup and conventions, see [`SETUP.md`](SETUP.md), [`CODE_STYLE.md`](CODE_STYLE.md), and [`CONTRIBUTING.md`](CONTRIBUTING.md).

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes
4. Push to the branch
5. Open a Pull Request

For larger changes, please open an issue first to discuss the approach.

## 📄 License

[MIT](LICENSE)

---

**Last updated**: 2026-05-05
