# NovelTrans

A SaaS translation platform for web novel authors and translators — batch-translate long-form content with RAG-powered translation memory, multi-agent collaboration, team workspaces, and Stripe billing.

> [中文版](README.zh.md)

[![CI](https://github.com/YuBing-link/noveltrans/actions/workflows/ci.yml/badge.svg)](https://github.com/YuBing-link/noveltrans/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![React](https://img.shields.io/badge/React-19-blue?logo=react)](https://react.dev/)
[![Coverage](https://img.shields.io/badge/Coverage-86%25-brightgreen)]()

## Features

- **Translate long-form content** — AI-powered chapter-by-chapter translation with multi-agent collaboration (translator + terminologist + polisher)
- **Reuse past translations** — RAG translation memory using Redis HNSW vectors reduces 60-80% of LLM API calls
- **Collaborate as a team** — project/workspace management, chapter assignment, review-and-approve workflow
- **Three delivery channels** — React web dashboard, Chrome extension (MV3, 3 translation modes), and external REST API with API-key authentication
- **Monetize with subscriptions** — Stripe Checkout + Billing Portal + Webhook, 3-tier plans (FREE / PRO / MAX)

## Quick Start

```bash
git clone https://github.com/YuBing-link/noveltrans.git
cd noveltrans
cp .env.example .env
# Edit .env with your MySQL, Stripe, and LLM credentials

cd web-app && npm install && npm run build && cd ..
docker compose up -d
```

Initial startup may take 5-10 minutes. Open [http://localhost:7341](http://localhost:7341) to verify.

For detailed setup instructions, see [`SETUP.md`](SETUP.md).

## Tech Stack

| Layer | Technology |
|-------|------------|
| **Backend** | Java 21, Spring Boot 3.2 (Undertow), MyBatis-Plus, Virtual Threads |
| **Frontend** | React 19, TypeScript, Vite, TailwindCSS 4.2 |
| **Chrome Extension** | Manifest V3, Content Scripts, IndexedDB |
| **Translation Engine** | Python 3.11, FastAPI, OpenAI SDK, AgentScope (multi-agent) |
| **Database** | MySQL 8.0, Redis Stack (RediSearch + HNSW vectors) |
| **Payments** | Stripe Checkout, Billing Portal, Webhooks |
| **Gateway** | Nginx (single entry point, port 7341) |
| **Testing** | JUnit 5, Mockito, Vitest, Playwright, k6 |
| **CI/CD** | GitHub Actions |

## Project Structure

```
noveltrans/
├── src/main/java/                  # Spring Boot backend (hexagonal architecture)
│   ├── adapter/in/                 #   Inbound adapters (REST controllers, security)
│   ├── adapter/out/                #   Outbound adapters (persistence, redis, translation)
│   ├── port/in/                    #   Inbound port interfaces (use cases)
│   ├── port/out/                   #   Outbound port interfaces (infrastructure contracts)
│   ├── domain/                     #   Core domain (models, services, events)
│   └── config/                     #   Spring configuration + cross-cutting
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
| [`SETUP.md`](SETUP.md) | Deployment & local development guide |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | System architecture, data flow, cache hierarchy |
| [`API_DOCUMENTATION.md`](API_DOCUMENTATION.md) | Complete REST API reference |
| [`ADR.md`](ADR.md) | Architecture decision records |
| [`test-coverage-report.md`](test-coverage-report.md) | JaCoCo test coverage report |
| [`load-test/STRESS_TEST_REPORT.md`](load-test/STRESS_TEST_REPORT.md) | k6 stress test results |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | Git workflow, commit conventions, PR process |

## Contributing

Bug fixes, documentation improvements, and new translation engine integrations are welcome. See [`CONTRIBUTING.md`](CONTRIBUTING.md) for details.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes
4. Push to the branch
5. Open a Pull Request

For larger changes, please open an issue first.

## License

[MIT](LICENSE)
