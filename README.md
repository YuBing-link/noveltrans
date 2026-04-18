# NovelTranslator - Bilingual Novel Translation System

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Python](https://img.shields.io/badge/Python-3.11-blue.svg)](https://www.python.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)
[![Redis Stack](https://img.shields.io/badge/Redis%20Stack-7-red.svg)](https://redis.io/docs/latest/develop/data-structures/search/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg)](https://docs.docker.com/compose/)

A full-stack bilingual novel translation system featuring a Chrome Extension, Spring Boot backend, LLM-powered translation microservice, and Nginx gateway. Supports three translation modes, four-tier caching, RAG semantic translation memory, entity-consistent translation, collaborative translation workflows, and SSE streaming responses.

[中文文档](README.zh.md)

## Architecture

```
┌──────────────┐     ┌──────────────┐     ┌────────────────┐     ┌───────────────────┐     ┌──────────────────┐
│ Chrome Ext    │────▶│  Nginx GW    │────▶│ Spring Boot    │────▶│ Python / MTran    │────▶│ OpenAI Compat.    │
│ Manifest V3   │     │  Port 7341   │     │ Port 8080      │     │ Engine 8k / 8989  │     │ / Claude / Ollama │
└──────────────┘     └──────────────┘     └───────┬────────┘     └───────────────────┘     └──────────────────┘
                                  │
                    ┌─────────────┼──────────────┐
                    ▼             ▼              ▼
                MySQL 8.0    Redis Stack 7    Caffeine
                (Persistent)  (HNSW Vectors)  (In-Process)
```

## Key Features

### RAG Translation Memory
- **Vector Semantic Search**: Redis Stack HNSW vector index encodes source text into 1536-dim Embedding vectors, KNN search against translation memory
- **Four-Tier Cache Chain**: Caffeine (L1) → Redis (L2) → MySQL (L3) → RAG Semantic Retrieval (L4) → Translation Engine
- **Quality Filtering**: Auto-rejects empty translations, length anomalies, ad keywords, and excessive special characters before storage
- **Dual Fallback Strategy**: When Redis KNN is unavailable, automatically degrades to MySQL cosine similarity calculation
- **User Isolation**: KNN queries filter by `user_id` + `target_lang` to prevent data cross-contamination

### Translation Engine
- **OpenAI-Compatible API**: Works with OpenAI GPT, Claude (compatibility layer), local Ollama, DeepSeek, and any compatible endpoint
- **Novel Translation Prompt**: 6 translation principles ensuring literary translation quality
- **Dual-Engine Fault Tolerance**: LLM engine + MTranServer lightweight engine with bidirectional fallback, health checks, and cooldown isolation
- **Probabilistic Round-Robin Routing**: Intelligent engine selection based on historical success rates and response times

### Four-Tier Caching
| Tier | Component | TTL | Purpose |
|------|-----------|-----|---------|
| L1 | Caffeine | 10 min | In-process hot cache |
| L2 | Redis | 30 min | Distributed cache |
| L3 | MySQL | 24 h | Persistent cache |
| L4 | RAG Vector Retrieval | Permanent | Semantic similarity matching |

- **Penetration Prevention**: Null placeholder + short TTL expiration
- **Breakdown Prevention**: `ConcurrentHashMap` per-key concurrent locking
- **Avalanche Prevention**: Randomized TTL jitter on all expiration times

### Backend Engineering
- **SSE Streaming Translation**: Progressive browser-side rendering for smooth UX with long texts
- **Virtual Thread Concurrency**: Java 21 Virtual Threads + Semaphore per-user rate limiting
- **Entity Consistency Translation**: Long text auto-extracts named entities → merges glossary → placeholder replacement → translation → entity restoration, ensuring proper noun consistency
- **Async Document Translation**: Large file async tasks + progress tracking
- **Undertow Server**: High-performance non-blocking web server

### Browser Extension
- **Full-Page Translation**: DOM traversal analysis → text registry → SSE streaming write-back → in-place replacement, preserving page layout
- **Reader Mode**: Mozilla Readability integration extracts article body into a clean reading view
- **Selection Translation**: Floating tooltip for instant translation of selected text
- **Client-Side Cache**: IndexedDB + memory dual-tier cache + request deduplication

### Collaborative Translation
- **Project State Machine**: DRAFT → ACTIVE → COMPLETED → ARCHIVED, enforced state transition validation
- **Chapter Workflow**: Create chapters → assign translator → submit translation → review approve/reject
- **Role-Based Access**: OWNER / TRANSLATOR / REVIEWER, custom `@RequireProjectAccess` annotation
- **Comment System**: Threaded replies + resolve marking
- **Invitation System**: UUID-based invite codes for project joining

### Security
- **JWT + API Key Authentication**: Dual auth methods — Spring Security + auth0-jwt for JWT, `ApiKeyAuthenticationFilter` for `nt_sk_xxxx` format API keys. Both auth methods share the same translation pipeline
- **API Key Management**: Generate, list, reset, and delete API keys via `/user/api-keys` endpoints. Keys use `nt_sk_` prefix with 32 random alphanumeric characters, masked in list view
- **BCrypt Password Hashing**: All user passwords hashed before storage
- **Email Verification**: Double verification for registration and password reset
- **Tiered Rate Limiting**: Anonymous (3) / Free (5) / Pro (20) differentiated concurrency limits

### Character Quota System
- **Three-Tier Plans**: Free (10K chars/month), Pro (50K chars/month), Max (200K chars/month)
- **Mode Multipliers**: Fast mode ×0.5 (saves quota), Expert mode ×1.0, Team mode ×2.0
- **Per-Request Consumption**: `cost = ceil(translated_chars × mode_multiplier)`, deducted before translation starts
- **Daily Tracking Table**: `quota_usage` table tracks daily consumption aggregated monthly
- **Monthly Auto-Reset**: Cron task runs on 1st of each month to clean up expired usage records
- **Document Upload Estimate**: Pre-checks quota based on file size before processing

| Plan | Monthly Chars | Fast (×0.5) | Expert (×1.0) | Team (×2.0) |
|------|---------------|-------------|---------------|-------------|
| **Free** | 10,000 | 20K source chars | 10K source chars | 5K source chars |
| **Pro** | 50,000 | 100K source chars | 50K source chars | 25K source chars |
| **Max** | 200,000 | 400K source chars | 200K source chars | 100K source chars |

## Quick Start

### Option 1: Docker Compose (Recommended)

```bash
git clone https://github.com/your-org/novelTranslator.git
cd novelTranslator

# Start all services (MySQL, Redis, MTranServer, LLM Engine, Backend, Nginx)
docker compose up -d
```

After startup:
- **Web Dashboard**: http://localhost:7341
- **Backend API**: http://localhost:7341/v1
- **Health Check**: http://localhost:7341/health

### Option 2: Manual Setup

#### Prerequisites
- Java 21 (Temurin / OpenJDK)
- Maven 3.9+
- MySQL 8.0
- Redis 7 (Redis Stack recommended)
- Python 3.11+ (translation microservice)

#### 1. Start MySQL and Redis

```bash
docker run -d --name mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=novel_translator \
  mysql:8.0

docker run -d --name redis-stack -p 6379:6379 redis/redis-stack-server:latest
```

#### 2. Start the Backend

```bash
export JWT_SECRET="your-secret-key-here"
export MYSQL_HOST=localhost
export REDIS_HOST=localhost

mvn clean package -DskipTests
java -jar target/novelTranslator-0.0.1-SNAPSHOT.jar
# Backend runs at http://localhost:8080
```

#### 3. Start the Translation Engine

```bash
pip install fastapi uvicorn openai

export OPENAI_API_KEY="sk-xxx"
export OPENAI_BASE_URL="https://api.deepseek.com/v1"
export OPENAI_MODEL="deepseek-chat"

python services/translate-engine/translate_server.py
# Engine runs at http://localhost:8000
```

#### 4. Install the Chrome Extension

1. Open Chrome and navigate to `chrome://extensions/`
2. Enable "Developer mode"
3. Click "Load unpacked" and select the `extension/` directory

## Project Structure

```
novelTranslator/
├── extension/                    # Chrome Extension (Manifest V3)
│   ├── manifest.json
│   └── src/
│       ├── background/           #   Service Worker (message routing, API calls)
│       ├── content/              #   Content scripts (page, reader, selection translation)
│       ├── popup/                #   Popup UI
│       ├── options/              #   Settings pages
│       └── lib/                  #   Third-party libs (Readability, DOMPurify)
├── frontend/                     # Web Dashboard (Vanilla HTML/CSS/JS)
│   ├── pages/                    #   Pages (home, translation, user center, collab)
│   ├── js/                       #   Business logic (API client, auth, translation)
│   ├── styles/                   #   Styles (responsive, dark mode, animations)
│   ├── utils/                    #   Utility functions
│   └── config.js                 #   API endpoint configuration
├── src/main/java/                # Spring Boot Backend (Java 21)
│   └── com/yumu/noveltranslator/
│       ├── controller/           #   REST API Controllers
│       ├── service/              #   Business Logic Layer
│       ├── mapper/               #   MyBatis-Plus Data Access
│       ├── entity/               #   Domain Entities
│       ├── dto/                  #   Data Transfer Objects
│       ├── config/               #   Configuration (Redis vectors, Security, Threads)
│       ├── security/             #   Spring Security + JWT
│       ├── enums/                #   Enumerations (errors, engines, status)
│       └── util/                 #   Utility Classes
├── services/translate-engine/    # Python Translation Microservice
│   └── translate_server.py       #   FastAPI + OpenAI SDK + Fallback Chain
├── nginx/                        # Nginx Gateway Configuration
│   └── nginx.conf
├── docker-compose.yml            # Docker Compose Deployment
└── pom.xml                       # Maven Build Configuration
```

## API Documentation

- [API_ENDPOINTS.md](API_ENDPOINTS.md) - Three translation modes with request/response examples
- [API_DOCUMENTATION.md](API_DOCUMENTATION.md) - Complete backend API reference

### Quick API Reference

| Endpoint | Method | Description | Auth |
|----------|--------|-------------|------|
| `/v1/translate/webpage` | POST | Batch webpage translation (SSE streaming) | No |
| `/v1/translate/reader` | POST | Reader mode article translation | No |
| `/v1/translate/selection` | POST | Selected text translation | No |
| `/v1/translate/text` | POST | Plain text translation | No |
| `/v1/translate/document` | POST | Async document translation | Yes |
| `/v1/translate/rag` | POST | RAG semantic translation memory lookup | Yes |
| `/user/register` | POST | User registration | No |
| `/user/login` | POST | User login | No |
| `/user/profile` | GET/PUT | Get/update user profile | Yes |
| `/user/quota` | GET | Get character quota usage | Yes |
| `/user/api-keys` | GET/POST | List/create API keys | Yes |
| `/user/api-keys/{id}` | DELETE | Delete API key | Yes |
| `/user/api-keys/{id}/reset` | POST | Reset (regenerate) API key | Yes |
| `/user/glossaries` | GET/POST | List/Create glossary items | Yes |
| `/user/preferences` | GET/PUT | Get/update user preferences | Yes |
| `/v1/collab/projects` | GET/POST | Collaborative project management | Yes |

## Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `JWT_SECRET` | JWT signing secret key | None | Yes |
| `MYSQL_HOST` | MySQL host | localhost | No |
| `REDIS_HOST` | Redis host | localhost | No |
| `TRANSLATION_OPENAI_API_KEY` | Backend translation API key | None | Yes |
| `OPENAI_API_KEY` | Microservice API key | None | Yes |
| `OPENAI_BASE_URL` | API base URL | https://api.openai.com | No |
| `OPENAI_MODEL` | Translation model | gpt-4o-mini | No |
| `EMBEDDING_PROVIDER` | Embedding provider | openai | No |
| `EMBEDDING_OPENAI_API_KEY` | Embedding API key | None | RAG requires |
| `MTRAN_HOST` | MTranServer host | localhost | No |
| `MTRAN_PORT` | MTranServer port | 8989 | No |

## Tech Stack

| Layer | Technology |
|-------|------------|
| **Backend** | Java 21, Spring Boot 3.2.0, Undertow, Spring Security, WebFlux |
| **Database** | MySQL 8.0, MyBatis-Plus 3.5.5 |
| **Cache** | Caffeine (L1), Redis Stack 7 / Lettuce (L2), MySQL (L3) |
| **Vector Retrieval** | RediSearch HNSW, OpenAI text-embedding-3-small (1536 dim) |
| **Microservice** | Python 3.11, FastAPI, OpenAI SDK, MTranServer |
| **Frontend** | Chrome Extension (Manifest V3), Vanilla JS, CSS3, Thymeleaf |
| **Gateway** | Nginx 1.28 |
| **Build & Deploy** | Maven, Docker Compose |

## License

This project is licensed under the [MIT License](LICENSE).
