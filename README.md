# NovelTrans

A SaaS translation platform for web novel authors and translators — batch-translate long-form content with RAG-powered translation memory, team collaboration, and Stripe billing.

> [中文版](README.zh.md)

[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen?logo=spring)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-blue?logo=react)](https://react.dev/)

## ✨ Features

- **Translate long-form content** — AI-powered chapter-by-chapter translation with multi-agent collaboration (translator + terminologist + polisher)
- **Reuse past translations** — RAG translation memory using Redis HNSW vectors reduces 60-80% of LLM API calls by semantically matching similar source text
- **Collaborate as a team** — project/workspace management, chapter assignment, review-and-approve workflow
- **Three delivery channels** — React web dashboard, Chrome extension (MV3, 3 translation modes), and external REST API with API-key authentication
- **Monetize with subscriptions** — Stripe Checkout + Billing Portal + Webhook, 3-tier plans (FREE / PRO / MAX) with usage quotas

## 🛠️ Quick Start

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
| **Backend** | Java 21, Spring Boot 3.2, MyBatis-Plus, Undertow |
| **Frontend** | React 19, TypeScript 6, Vite 8, TailwindCSS 4.2, i18next |
| **Chrome Extension** | Manifest V3, Content Scripts, IndexedDB |
| **Translation Engine** | Python 3.11, FastAPI, OpenAI SDK, AgentScope (multi-agent) |
| **Neural Translation Machine** | MTranServer — lightweight open-source translation engine |
| **Database** | MySQL 8.0, Redis Stack (RediSearch + HNSW vectors) |
| **Embeddings** | Ollama (bge-m3) / OpenAI text-embedding-3-small |
| **Payments** | Stripe Checkout, Billing Portal, Webhooks |
| **Gateway** | Nginx (single entry point, port 7341) |
| **Testing** | JUnit 5, Mockito, Vitest, Playwright, k6 |

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
         │        │   │ vectors) │   │ (neural MT)  │
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

## ⚠️ Known Issues

- **Chrome extension translation engine not yet connected** — The translation buttons in the Chrome extension (reader mode, webpage mode, selection mode) are currently silently failing: they do nothing and produce no error. The backend translation API endpoints are ready; the extension-side integration is pending.

## 🗺️ Roadmap

- [x] User authentication and email verification
- [x] Stripe subscription billing (FREE / PRO / MAX)
- [x] RAG translation memory with Redis HNSW
- [x] Multi-agent collaborative translation
- [x] Team collaboration workspace
- [x] Chrome extension (3 translation modes)
- [x] External REST API with API-key auth
- [ ] WebSocket real-time translation progress
- [ ] Document format support (PDF, EPUB)
- [ ] Machine translation quality scoring dashboard

## 🤝 Contributing

Bug fixes, documentation improvements, and new translation engine integrations are welcome.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes
4. Push to the branch
5. Open a Pull Request

For larger changes, please open an issue first to discuss the approach.

## 📄 License

[MIT](LICENSE)

---

**Last updated**: 2026-04-29
