# Architecture Guide (ARCHITECTURE.md)

This document describes the system architecture, component responsibilities, data flow, and key technical decisions of NovelTrans.

## Table of Contents

- [System Overview](#system-overview)
- [Component Responsibilities](#component-responsibilities)
- [Data Flow](#data-flow)
- [Translation Pipeline](#translation-pipeline)
- [4-Level Cache Architecture](#4-level-cache-architecture)
- [Translation Engine Architecture](#translation-engine-architecture)
- [Security Architecture](#security-architecture)
- [Deployment Architecture](#deployment-architecture)
- [Database Design](#database-design)

---

## System Overview

NovelTrans is a full-stack bilingual novel translation system built with the following core components:

```
┌──────────────────────────────────────────────────────────────────────────┐
│                          NovelTrans System Architecture                   │
├──────────┐    ┌──────────┐    ┌────────────┐    ┌──────────────┐         │
│ Chrome   │───▶│  Nginx   │───▶│ Spring Boot│───▶│ Translation  │         │
│ Extension│    │ :7341    │    │  Backend   │    │   Engines    │         │
│          │    │          │    │  :8080     │    │  :8000/:8989 │         │
└──────────┘    └──────────┘    └─────┬──────┘    └──────────────┘         │
┌──────────┐                          │                                    │
│ Web App  │                          │                                    │
│ React+TS │──────────────────────────┘                                    │
└──────────┘              ┌─────────┴───────┐                              │
                          │  MySQL 8.0      │                              │
                          │  Redis Stack    │                              │
                          │  Caffeine       │                              │
                          └─────────────────┘                              │
└──────────────────────────────────────────────────────────────────────────┘
```

### Tech Stack

| Layer | Technology | Version |
|-------|------------|---------|
| Frontend | Chrome Extension (Manifest V3) + React + TypeScript + Vite | React 19 |
| Gateway | Nginx | 1.28 |
| Backend | Spring Boot 3.2.0 + Undertow | Java 21 |
| ORM | MyBatis-Plus | 3.5.5 |
| Database | MySQL | 8.0 |
| Cache | Redis Stack + Caffeine | Redis 7 |
| Microservice | FastAPI + OpenAI SDK | Python 3.11+ |
| Security | Spring Security + JWT (auth0-jwt) | - |
| Build | Maven + Docker Compose | Maven 3.9 |

---

## Component Responsibilities

### 1. Chrome Extension (`extension/`)

**Role**: Browser-side translation entry point, responsible for DOM analysis, text extraction, and translation result injection.

| Module | Responsibility |
|--------|----------------|
| `background/` | Service Worker: message routing, API coordination |
| `content/` | Content Script: DOM traversal, text node mapping, result injection |
| `popup/` | Extension popup UI: engine selection, language config |
| `options/` | Settings page: API key management, preferences |
| `lib/` | Third-party libs: Readability (article extraction), DOMPurify (HTML sanitization) |

**Three Translation Modes**:

- **Full-page** (`/v1/translate/webpage`): DOM traversal generates textId mapping table, batch-sent to backend, SSE streaming receives results and updates nodes progressively
- **Reader mode** (`/v1/translate/reader`): Uses Readability to extract article HTML, translates and displays in a clean reading interface
- **Selection** (`/v1/translate/selection`): Listens for user text selection, calls translation API and shows results in a floating panel

### 2. Web App (`web-app/`)

**Role**: React + TypeScript + Vite built web dashboard, providing DeepL-style translation experience.

- Text translation, document upload & translation, translation history
- User login, quota management, glossary management
- Communicates with backend API through Nginx gateway

### 3. Nginx Gateway (`nginx/`)

**Role**: Reverse proxy, static file serving, CORS handling.

- Listens on port 7341
- Proxies `/v1/**` requests to `http://backend:8080`
- Serves static files from `web-app/dist/` at `/`
- Configures CORS headers for extension access

### 4. Spring Boot Backend (`src/main/java/`)

**Role**: REST API service, business logic, authentication, cache management, translation pipeline orchestration.

#### 4.1 Controller Layer

| Controller Package | Responsibility |
|--------------------|----------------|
| `controller/web/` | Web dashboard API (users, documents, glossaries, translation) |
| `controller/plugin/` | Browser extension API (translation endpoints) |
| `controller/external/` | External integration API (API key authenticated) |
| `controller/shared/` | Shared translation API (text, document, RAG, task management) |
| `controller/collab/` | Collaboration workspace API |
| `controller/web/stripe` | Stripe subscription API |

#### 4.2 Service Layer

| Service | Responsibility |
|---------|----------------|
| `TranslationService` | Core translation logic, SSE streaming, TranslationPipeline orchestration |
| `TranslationTaskService` | Async document translation task management, SSE streaming |
| `MultiAgentTranslationService` | Multi-agent collaborative translation (by project chapters) |
| `TranslationPipeline` | **Unified translation pipeline**: encapsulates 4-level flow (cache → RAG → entity consistency → direct LLM) |
| `TranslationCacheService` | Caffeine (L1) + Redis (L2) cache management |
| `RagTranslationService` | RAG semantic retrieval translation memory |
| `EntityConsistencyService` | Entity consistency (glossary + placeholder protection) |
| `TranslationPostProcessingService` | Post-processing (residual Chinese detection and correction) |
| `UserLevelThrottledTranslationClient` | User-tier-based translation concurrency throttling |
| `UserService` | User CRUD, password management, permission checks |
| `ExternalTranslationService` | External translation engine coordination |
| `QuotaService` | Character quota management, tier checks |
| `SubscriptionService` | Stripe subscription lifecycle |

#### 4.3 Translation Pipeline (`service/pipeline/`)

`TranslationPipeline` is the unified translation pipeline component that eliminates duplicated pipeline logic across services.

```
┌─────────────────────────────────────────────────────────────┐
│                    TranslationPipeline                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Input Text                                                  │
│    │                                                         │
│    ▼                                                         │
│  L1: 3-Level Cache Query (Caffeine → Redis → MySQL)          │
│    │ Hit → return                                            │
│    ▼ Miss                                                    │
│  L2: RAG Semantic Match (Redis HNSW vector search)           │
│    │ Direct hit → post-process → cache → return              │
│    ▼ Miss                                                    │
│  L3: Entity Consistency Translation (conditional)            │
│    │   Trigger: userId != null && text exceeds threshold     │
│    │ Extract entities → merge glossary → placeholders        │
│    │ → translate → entity restore → post-process → cache     │
│    ▼ Not triggered / failed                                  │
│  L4: Direct Translation (Python/MTranServer round-robin)     │
│    │ Quality validation (ad keyword detection, length check) │
│    │ Post-process → cache → TranslationMemory store → return │
│                                                              │
│  executeFast(): L1 + L4 only, skips RAG and consistency      │
└─────────────────────────────────────────────────────────────┘
```

| Method | Description |
|--------|-------------|
| `execute(text, targetLang, engine)` | Full 4-level pipeline |
| `executeFast(text, targetLang, engine)` | Fast mode (cache + direct only) |
| `shouldCache(original, translated)` | Static method: determines if result should be cached |
| `isValidTranslation(text, result)` | Static method: validates translation quality |

#### 4.4 Configuration Classes

| Config | Responsibility |
|--------|----------------|
| `SecurityConfig` | Spring Security filter chain configuration |
| `RedisConfig` | Redis connection pool, serialization |
| `RedisVectorConfig` | Redis HNSW vector index configuration |
| `TranslationExecutorConfig` | Virtual thread executor configuration |
| `TranslationLimitProperties` | Translation limit configuration property binding |
| `SecurityPermitAllPaths` | Anonymous access path whitelist |
| `MyMetaObjectHandler` | MyBatis-Plus auto-fill (created_at, updated_at) |

#### 4.5 Security Module

| Component | Responsibility |
|-----------|----------------|
| `SecurityConfig` | Spring Security filter chain, translation endpoint auth enforcement |
| `JwtAuthenticationFilter` | JWT token parsing and authentication, invalid token returns 401 |
| `JwtAuthenticationEntryPoint` | 401 response for unauthenticated requests |
| `ApiKeyAuthenticationFilter` | API Key (`nt_sk_xxxx`) authentication filter |
| `CustomUserDetails` | Spring Security UserDetails implementation |
| `SecurityPermitAllPaths` | Centralized whitelist paths shared by SecurityConfig and JwtAuthenticationFilter |
| `ProjectAccessAspect` | `@RequireProjectAccess` annotation AOP permission check |

#### 4.6 Utilities

| Utility | Responsibility |
|---------|----------------|
| `CacheKeyUtil` | Unified cache key generation strategy |
| `SseEmitterUtil` | SSE event builder and serialization |
| `TextCleaningUtil` | Pre/post translation text cleaning |
| `TextSegmentationUtil` | Long text segmentation strategy |
| `EmailVerificationCodeUtil` | Email verification code generation and validation |
| `SecurityUtil` | Current user context accessor |
| `ExternalResponseUtil` | External translation service response parsing + translated file path building |
| `JwtUtils` | JWT token generation and validation |
| `PasswordUtil` | BCrypt password encryption |

### 5. Translation Microservice (`services/translate-engine/`)

**Role**: LLM translation engine invocation, fallback chain management.

- FastAPI-based
- Integrates OpenAI SDK, supports any OpenAI-compatible API
- Supports DeepSeek, OpenAI GPT, Claude (compatibility layer), Ollama, etc.
- Health check endpoint at `/health`

### 6. MTranServer (`mtran-server` in docker-compose.yml)

**Role**: Lightweight local translation service, fallback for LLM engine.

- Based on `xxnuo/mtranserver` Docker image
- Port 8989
- Suitable for direct translation scenarios without LLM semantic understanding

---

## Data Flow

### Translation Request Full Data Flow

```
User Action (Chrome Extension / Web App)
    │
    ▼
┌─────────────────────────────────────────────────┐
│ 1. DOM Analysis / Text Input                     │
│    - Traverse page DOM tree                      │
│    - Extract translatable text nodes             │
│    - Generate mapping {textId, original, context}│
└──────────────────────┬──────────────────────────┘
                       │ HTTP POST /v1/translate/** + Authorization
                       ▼
┌─────────────────────────────────────────────────┐
│ 2. Nginx Reverse Proxy                           │
│    - Route to http://backend:8080                │
└──────────────────────┬──────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────┐
│ 3. Spring Boot - Security Filter Chain           │
│    - JwtAuthenticationFilter validates JWT       │
│    - Invalid token → 401                         │
│    - ApiKeyAuthenticationFilter validates API Key│
│    - Whitelist paths skip auth                   │
└──────────────────────┬──────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────┐
│ 4. Spring Boot - Controller Layer                │
│    - Parameter validation (@Validated)            │
│    - Quota check (QuotaService)                   │
│    - Create SseEmitter for streaming response    │
└──────────────────────┬──────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────┐
│ 5. Spring Boot - TranslationPipeline             │
│    ┌───────────────────────────────────────┐     │
│    │ L1: 3-Level Cache Query               │     │
│    │   Caffeine → Redis → MySQL            │     │
│    │   Hit -> return immediately            │     │
│    └───────────────────────────────────────┘     │
│    ┌───────────────────────────────────────┐     │
│    │ L2: RAG Semantic Match                │     │
│    │   Redis HNSW KNN search               │     │
│    │   Hit -> post-process -> cache -> ret │     │
│    └───────────────────────────────────────┘     │
│    ┌───────────────────────────────────────┐     │
│    │ L3: Entity Consistency (conditional)  │     │
│    │   Glossary + placeholder protection   │     │
│    └───────────────────────────────────────┘     │
│    ┌───────────────────────────────────────┐     │
│    │ L4: Direct Translation                │     │
│    │   Python / MTranServer round-robin    │     │
│    │   Quality validation + cache          │     │
│    └───────────────────────────────────────┘     │
└──────────────────────┬──────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────┐
│ 6. External Translation Service                  │
│    - Python FastAPI (:8000) or MTranServer (:8989)│
│    - Calls OpenAI-compatible API                 │
│    - Applies novel translation System Prompt     │
│    - Returns translation result                  │
└──────────────────────┬──────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────┐
│ 7. Translation Result Return                     │
│    - SSE events: {textId, original, translation} │
│    - Done marker: [DONE]                         │
│    - Error marker: ERROR: message                │
└──────────────────────┬──────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────┐
│ 8. Frontend Rendering                            │
│    - Parse SSE events                            │
│    - Locate DOM node by textId                   │
│    - Replace text content                        │
│    - Preserve original DOM structure and style   │
└─────────────────────────────────────────────────┘
```

### User Authentication Flow

```
User Login Request
    │
    ▼
UserController.login()
    │
    ▼
UserService.authenticate()
    │
    ├── Verify email and password (BCrypt)
    │
    ▼
JwtUtils.generateToken()
    │
    ├── payload: userId, email, role
    ├── signature: HMAC-SHA256 (JWT_SECRET)
    └── expiry: 30 days
    │
    ▼
Return { token, refreshToken, user }
    │
    ▼
Subsequent requests include Authorization: Bearer <token>
    │
    ▼
JwtAuthenticationFilter intercepts
    │
    ├── Token valid
    │   ├── Verify signature and expiry
    │   ├── Extract user info
    │   └── Set SecurityContext → allow through
    │
    ├── Token invalid/expired
    │   └── Return 401 JSON response
    │
    └── No token + auth-required path
        └── Spring Security returns 401
```

---

## Translation Pipeline

### Unified Pipeline Design

Previously, translation pipeline logic was duplicated across three Service classes. It has been consolidated into a single `TranslationPipeline` component.

```
                    ┌─────────────────────┐
                    │  TranslationService │
                    │  TranslationTaskSvc │
                    │  MultiAgentSvc      │
                    └──────────┬──────────┘
                               │ Create Pipeline instance
                               ▼
                    ┌─────────────────────┐
                    │ TranslationPipeline  │
                    │ (single impl)        │
                    └──────────┬──────────┘
                               │
                  ┌────────────┼────────────┐
                  │            │            │
                  ▼            ▼            ▼
            ┌──────────┐ ┌─────────┐ ┌──────────┐
            │ L1: Cache │ │ L2: RAG │ │ L3: Cons │
            │ L4: Direct│ │ Post-pro│ │ L4: Dir  │
            │ Quality   │ │ Cache   │ │ Post-pro │
            │ Post-pro  │ │         │ │ Cache    │
            │ Cache     │ │         │ │ Memory   │
            └──────────┘ └─────────┘ └──────────┘
```

### Pipeline Reuse Comparison

| Code Pattern | Before Refactoring | After Refactoring |
|--------------|--------------------|--------------------|
| 4-level pipeline impl | 3 duplicated copies | `TranslationPipeline.execute()` |
| `shouldCache` method | 3 copies | `TranslationPipeline.shouldCache()` static |
| `isValidTranslation` | Only in TranslationService | `TranslationPipeline.isValidTranslation()` static |
| `extractTranslatedContent` | 2 copies | `ExternalResponseUtil.extractDataField()` |
| `buildTranslatedPath` | 2 copies | `ExternalResponseUtil.buildTranslatedPath()` |
| Post-processing | Only 2/3 Services had it | Unified across all paths |

---

## 4-Level Cache Architecture

### Architecture Design

```
  Request
   │
   ▼
┌─────────┐     Miss        ┌─────────┐     Miss        ┌─────────┐     Miss        ┌─────────┐
│ L1:     │ ──────────────▶ │ L2:     │ ──────────────▶ │ L3:     │ ──────────────▶ │ L4:     │
│ Caffeine│                 │ Redis   │                 │ MySQL   │                 │ RAG     │
│ (local) │ ◀────────────── │ (dist)  │ ◀────────────── │ (persist)│ ◀───────────── │ (semant)│
└─────────┘   Backfill      └─────────┘   Backfill      └─────────┘   Backfill      └─────────┘
     │ 10 min                 │ 30 min                 │ 24h                  │ Permanent
     ▼                        ▼                        ▼                      ▼
```

### Cache Penetration Protection

- **Problem**: Queries for non-existent data always miss cache, hitting DB every time
- **Solution**: Cache null results with short TTL (e.g., 1 minute)

### Cache Breakdown Protection

- **Problem**: Hot key expiry causes massive concurrent DB hits
- **Solution**: `ConcurrentHashMap` per-key locking — only one thread queries DB

### Cache Avalanche Protection

- **Problem**: Mass cache expiry at once causes DB pressure spike
- **Solution**: Random jitter on TTL (e.g., `baseTTL + random(0, jitter)`)

---

## Translation Engine Architecture

### Dual Engine Design

```
                    ┌─────────────────────┐
                    │  TranslationPipeline│
                    │  (routing decision)  │
                    └──────────┬──────────┘
                               │
                  ┌────────────┼────────────┐
                  │                         │
                  ▼                         ▼
        ┌─────────────────┐     ┌─────────────────┐
        │ LLM Engine       │     │ MTranServer     │
        │ (DeepSeek/GPT)  │     │ (lightweight)   │
        │ Port: 8000      │     │ Port: 8989      │
        │ Scenario: HQ    │     │ Scenario: Fast  │
        └─────────────────┘     └─────────────────┘
```

### Translation System Prompt

The system applies 6 translation principles for novel content:

1. Accurately understand the original meaning
2. Preserve the source language's stylistic characteristics
3. Conform to target language expression conventions
4. Retain proper nouns from the source
5. Maintain paragraph structure consistency
6. Literary text requires attention to rhetoric and imagery

### Text Segmentation Strategy

- Long texts are split by paragraph to avoid token limits per request
- Each paragraph is translated independently and concatenated
- Virtual threads execute asynchronously for higher throughput

---

## Security Architecture

### Authentication & Authorization

```
┌─────────────────────────────────────────────────┐
│                  Security Filter Chain            │
├─────────────────────────────────────────────────┤
│                                                  │
│  Request → SecurityFilterChain                    │
│            │                                     │
│            ├── Whitelist paths → Pass directly   │
│            │   (/user/login, /user/register,     │
│            │    /health, /actuator, /swagger)    │
│            │                                     │
│            └── Other paths → Auth required        │
│                │                                 │
│                ├── JwtAuthenticationFilter       │
│                │   ├── Valid token → Allow       │
│                │   └── Invalid token → 401       │
│                │                                 │
│                └── ApiKeyAuthenticationFilter    │
│                    ├── Valid key → Allow          │
│                    └── Invalid key → 401         │
│                                                  │
│  All /v1/translate/** endpoints require auth     │
│                                                  │
└─────────────────────────────────────────────────┘
```

### Whitelist Paths

| Path | Description |
|------|-------------|
| `/user/login` | User login |
| `/user/register` | User registration |
| `/user/send-code` | Send verification code |
| `/user/send-reset-code` | Send password reset code |
| `/user/reset-password` | Reset password |
| `/user/get-token` | Get token |
| `/health` | Health check |
| `/actuator` | Spring Actuator monitoring |
| `/swagger-ui` | API documentation |
| `/v3/api-docs` | OpenAPI specification |

### Rate Limiting

| User Type | Max Concurrency |
|-----------|----------------|
| Anonymous | 1 |
| Free | 1 |
| Pro | 3 |
| Max | 5 |

- Per-user concurrency enforced via `Semaphore`
- Daily quota counting via Redis sliding window

### Data Security

- Passwords: BCrypt hashed, irreversible
- JWT Secret: Environment variable only, never hardcoded
- Database credentials: Configured via environment variables
- Email verification: 6-digit random code, time-limited
- **Translation endpoint auth enforced**: All `/v1/translate/**` require valid JWT or API Key

---

## Deployment Architecture

### Docker Compose Deployment

```
┌─────────────────────────────────────────────────────┐
│                  Docker Network                      │
│                                                      │
│  ┌──────────┐    ┌──────────┐    ┌──────────────┐   │
│  │  Nginx   │───▶│ Backend  │───▶│ MTranServer  │   │
│  │  :7341   │    │  :8080   │    │  :8989       │   │
│  └────┬─────┘    └────┬─────┘    └──────────────┘   │
│       │               │                              │
│       │          ┌────┴─────┐    ┌──────────────┐   │
│       │          │ MySQL    │    │ LLM Engine   │   │
│       │          │ :3306    │    │ :8000        │   │
│       │          │ Redis    │    │              │   │
│       │          │ :6379    │    └──────────────┘   │
│       │          │ Ollama (:11434)                  │
│       │          └──────────┘                       │
│       │                                              │
│  ┌────┴─────┐                                       │
│  │ Frontend │ (static file mapping)                  │
│  └──────────┘                                       │
│                                                      │
└─────────────────────────────────────────────────────┘
```

### Port Mapping

| Service | Container Port | Host Port | Description |
|---------|---------------|-----------|-------------|
| Nginx | 7341 | 7341 | Gateway entry point |
| Backend | 8080 | 8080 | Spring Boot |
| MySQL | 3306 | 3307 | Database |
| Redis | 6379 | 6379 | Cache + Vector store |
| MTranServer | 8989 | 8989 | Lightweight translation |
| LLM Engine | 8000 | 8000 | LLM translation |
| Ollama | 11434 | 11434 | Embedding model (GPU) |

---

## Database Design

### Core Tables

| Table | Description | Key Fields |
|-------|-------------|------------|
| `user` | User information | id, email, password, role, status, user_level |
| `glossary` | Glossary terms | id, user_id, source_word, target_word, remark |
| `translation_cache` | Translation cache | id, source_text, target_text, source_lang, target_lang, engine |
| `translation_history` | Translation history | id, user_id, source_text, translated_text, engine, time_cost |
| `translation_task` | Translation tasks | id, user_id, file_name, status, progress, result |
| `translation_memory` | RAG translation memory | id, user_id, source_text, embedding_vector, target_text |
| `document` | Document management | id, user_id, name, path, file_type, status, task_id |
| `user_preference` | User preferences | id, user_id, preferred_engine, target_language |
| `quota_usage` | Quota usage | id, user_id, chars_used, date, mode |
| `api_key` | API key management | id, user_id, key_hash, name, created_at |

### Database ER Relationships

```
user (1) ──── (N) glossary
user (1) ──── (N) translation_history
user (1) ──── (N) translation_task
user (1) ──── (1) user_preference
user (1) ──── (N) quota_usage
user (1) ──── (N) api_key
user (1) ──── (N) document
document (1) ──── (1) translation_task
user (1) ──── (N) translation_memory
```

### Index Design

- `user`: `email` (UNIQUE) — login query
- `glossary`: `user_id` — per-user glossary lookup
- `translation_cache`: composite index (source_text, source_lang, target_lang) — cache hit query
- `translation_history`: `user_id` — per-user history lookup
- `translation_memory`: `user_id`, `target_lang` — RAG KNN retrieval filter
- `quota_usage`: `user_id`, `date` — quota statistics

---

## Key Technical Decisions

### 1. Why Undertow over Tomcat?

- Undertow's XNIO-based non-blocking I/O is better suited for high-concurrency SSE scenarios
- Virtual threads perform better on Undertow
- Lower memory footprint, faster startup

### 2. Why MyBatis-Plus over JPA?

- More flexible SQL control, suitable for complex queries (e.g., paginated translation history)
- Built-in pagination, optimistic locking, and other common features
- Lower learning curve for rapid development

### 3. Why Virtual Threads?

- Java 21's Project Loom reduces thread overhead for blocking I/O to near-zero
- Translation requests involve heavy network I/O (external API calls); virtual threads increase concurrency by an order of magnitude
- Combined with Semaphore for fine-grained per-user rate limiting

### 4. Why 4-Level Cache?

- L1 Caffeine: Local cache, zero network latency, ideal for hot data
- L2 Redis: Distributed cache, shared across instances, prevents inconsistency
- L3 MySQL: Persistent storage, fallback, supports long-term caching
- L4 RAG: Semantic similarity matching — finds translations even when source text isn't identical

### 5. Why Unified Translation Pipeline?

- Before refactoring, the 4-level pipeline logic was duplicated in 3 Services, making maintenance costly
- After extracting to `TranslationPipeline`, all translation paths call the same component, ensuring consistent behavior
- Adding post-processing (residual Chinese correction) only requires a single change
