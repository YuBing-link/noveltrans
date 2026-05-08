# Architecture Guide (ARCHITECTURE.md)

This document describes the system architecture, component responsibilities, data flow, and key technical decisions of NovelTrans.

**Related documents:**

- [README.md](README.md) — Project overview and quick start
- [SETUP.md](SETUP.md) — Deployment guide
- [CONTRIBUTING.md](CONTRIBUTING.md) — Contributing guide and code style (Spotless)
- [test-coverage-report.md](test-coverage-report.md) — Test coverage report

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

#### 4.1 Hexagonal Architecture Package Structure

The backend follows strict hexagonal architecture with dependency flowing inward: `adapter` → `port` → `domain`. The `adapter` and `port` packages depend on `domain`, but `domain` has zero external dependencies.

```
src/main/java/com/yumu/noveltranslator/
├── adapter/in/          Inbound adapters (drivers)
│   ├── rest/            REST controllers (Web, Plugin, External, Shared, Admin, Collab)
│   ├── security/        JWT/API-Key filters, SecurityUserDetails, rate limiters
│   ├── service/         SSE event streaming adapters
│   └── webhook/         Stripe webhook handler
├── adapter/out/         Outbound adapters (driven)
│   ├── persistence/     MyBatis-Plus repositories, entities, mappers, converters
│   ├── redis/           Redis cache, pub/sub, rate limiter, token blacklist
│   ├── translate/       LLM engine clients (Python, MTranServer)
│   ├── email/           Email service, device token management
│   ├── stripe/          Stripe client adapter
│   └── embedding/       Vector embedding adapter
├── port/in/             Inbound port interfaces (use cases)
│   ├── AuthPort, UserPort, DocumentPort, GlossaryPort
│   ├── TranslationTaskPort, TranslatePort, RagTranslationPort
│   ├── CollabPort, ChapterTaskPort, CollabCommentPort
│   ├── SubscriptionPort, WebhookPort, ApiKeyPort
│   ├── CacheAdminPort, DeviceTokenPort
├── port/out/            Outbound port interfaces (infrastructure contracts)
│   ├── UserRepositoryPort, DocumentRepositoryPort, GlossaryRepositoryPort
│   ├── TranslationRepositoryPort, CollaborationRepositoryPort
│   ├── TranslationEnginePort, EmailPort, StripePort
│   ├── CachePort, EmbeddingPort, DeviceTokenPort
├── port/dto/            Data Transfer Objects (request/response)
├── domain/model/        Domain entities (User, Document, Glossary, etc.)
├── domain/service/      Domain services (business logic)
├── domain/event/        Domain events (CollabChapterSplitEvent, etc.)
├── domain/util/         Domain utilities (state machines, validators)
├── config/              Spring configuration classes
├── properties/          @ConfigurationProperties bindings
├── bootstrap/           Application startup initialization
├── task/                @Scheduled tasks (recovery, cleanup)
├── exception/           Global exception handler
└── enums/               Enumerations
```

#### 4.2 Inbound Adapters (`adapter/in/`)

| Adapter | Responsibility |
|---------|----------------|
| `rest/web/` | Web dashboard API — `WebUserController`, `WebDocumentController`, `WebGlossaryController`, `WebTranslationController` |
| `rest/plugin/` | Chrome extension API — `PluginTranslationController` |
| `rest/external/` | External API key authenticated endpoints — `ExternalTranslationController`, `ExternalApiKeyController` |
| `rest/shared/` | Shared translation endpoints — `SharedTranslationController` |
| `rest/admin/` | Admin management endpoints — `AdminCacheController` |
| `rest/collab/` | Collaboration workspace — `CollabProjectController`, `CollabChapterTaskController`, `CollabCommentController` |
| `rest/web/stripe/` | Stripe subscription management — `WebSubscriptionController` |
| `security/` | `JwtAuthenticationFilter`, `ApiKeyAuthenticationFilter`, `TranslationRateLimitFilter`, `CustomUserDetails`, `LoginRateLimiter`, `ProjectAccessAspect` |
| `service/` | SSE event streaming — `TranslationSseService` |
| `webhook/` | Stripe webhook handler — `StripeWebhookController` |

> **Note**: Filter classes (`JwtAuthenticationFilter`, `ApiKeyAuthenticationFilter`, `SecurityHeadersFilter`, `TranslationRateLimitFilter`) are **not** annotated with `@Component`. They are created with `new` in `SecurityConfig.filterChain()` to avoid Spring Security's CGLIB proxy ordering conflicts.

#### 4.3 Outbound Adapters (`adapter/out/`)

| Adapter | Responsibility |
|---------|----------------|
| `persistence/` | Repository adapters implementing `port/out` interfaces — each adapter wraps MyBatis-Plus `Mapper` operations and converts between domain models ↔ entities |
| `persistence/entity` | JPA-annotated entity classes (`User`, `Document`, `Glossary`, `TranslationTask`, etc.) |
| `persistence/Mapper` | MyBatis-Plus BaseMapper interfaces (`UserMapper`, `DocumentMapper`, `GlossaryMapper`, etc.) |
| `persistence/Converter` | Model ↔ Entity converters (`UserConverter`, `GlossaryConverter`, `TranslationConverter`, `CollabConverter`) |
| `redis/` | Redis operations — `TranslationCacheService`, `CacheVersionService`, `TokenBlacklistService`, `RedisRateLimiter`, `CollabEventPublisher` |
| `translate/` | Translation engine clients — `OpenAiTranslationEngine`, `MTranTranslationEngine`, engine round-robin routing |
| `email/` | Email sending and device token management — `EmailService`, `DeviceTokenService` |
| `stripe/` | Stripe SDK wrapper — `StripeSubscriptionAdapter` |
| `embedding/` | Vector embedding generation — `OllamaEmbeddingAdapter` |

#### 4.4 Inbound Ports (`port/in/`)

Inbound port interfaces define use cases that inbound adapters call. Each interface is implemented by a domain service in `domain/service/`.

| Port Interface | Implemented By | Responsibility |
|---------------|----------------|----------------|
| `AuthPort` | `AuthService` | Login, register, password change/reset, logout, token refresh |
| `UserPort` | `UserService` | User profile, preferences, glossary CRUD, quota, statistics |
| `DocumentPort` | `DocumentService` | Document upload, cancel, delete, status update |
| `GlossaryPort` | *(via UserService)* | Glossary import/export, batch operations |
| `TranslationTaskPort` | `TranslationTaskService` | Async document translation, history, task management |
| `TranslatePort` | `TranslationService` | Real-time text translation (SSE streaming) |
| `RagTranslationPort` | `RagTranslationService` | RAG-based translation with vector memory |
| `CollabPort` | `CollabProjectService` | Collaboration project CRUD, invite codes, member management |
| `ChapterTaskPort` | `ChapterTaskService` | Chapter assignment, submission, review (state machine) |
| `CollabCommentPort` | *(via CollabPort)* | Chapter comments and replies |
| `SubscriptionPort` | `SubscriptionService` | Stripe subscription lifecycle, checkout, portal |
| `WebhookPort` | `StripeWebhookService` | Stripe webhook event processing |
| `ApiKeyPort` | `ApiKeyService` | API key CRUD, usage tracking |
| `CacheAdminPort` | `CacheAdminService` | Cache management (clear, version bump) |
| `DeviceTokenPort` | `DeviceTokenService` | Device token management |

#### 4.5 Outbound Ports (`port/out/`)

Outbound port interfaces define infrastructure contracts. Each interface is implemented by an adapter in `adapter/out/`.

| Port Interface | Implemented By | Responsibility |
|---------------|----------------|----------------|
| `UserRepositoryPort` | `UserMapperAdapter` | User, preference, tenant, API key, blacklist CRUD |
| `DocumentRepositoryPort` | `DocumentRepositoryAdapter` | Document CRUD, status updates |
| `GlossaryRepositoryPort` | `GlossaryRepositoryAdapter` | Glossary, AI glossary, chapter entity map, translation memory CRUD |
| `TranslationRepositoryPort` | `TranslationRepositoryAdapter` | Translation task, history, cache CRUD |
| `CollaborationRepositoryPort` | `CollaborationRepositoryAdapter` | Collab project, member, chapter task, comment, invite code CRUD |
| `TranslationEnginePort` | `TranslateEngineAdapter` | LLM engine invocation (OpenAI-compatible API) |
| `CachePort` | Redis/Caffeine services | 4-level cache operations, pub/sub, version management |
| `EmailPort` | `EmailService` | Email verification code sending |
| `StripePort` | Stripe adapter | Stripe API operations |
| `EmbeddingPort` | Embedding adapter | Vector embedding generation for RAG |
| `DeviceTokenPort` | `DeviceTokenService` | Device token storage |

#### 4.6 Domain Layer (`domain/`)

The core business logic layer with zero external dependencies.

| Package | Responsibility |
|---------|----------------|
| `domain/model/` | Domain entities: `User`, `Document`, `Glossary`, `TranslationTask`, `TranslationHistory`, `TranslationCache`, `CollabProject`, `CollabProjectMember`, `CollabChapterTask`, `CollabComment`, `ApiKey`, `Tenant`, `UserPreference`, `UserPlanHistory`, `TokenBlacklist`, `AiGlossary`, `ChapterEntityMap`, `TranslationMemory` |
| `domain/service/` | Business logic implementations: `AuthService`, `UserService`, `DocumentService`, `TranslationService`, `TranslationTaskService`, `RagTranslationService`, `CollabProjectService`, `ChapterTaskService`, `SubscriptionService`, `ApiKeyService`, `CacheAdminService`, `QuotaService`, `TranslationPipeline` |
| `domain/event/` | Domain events: `CollabChapterSplitEvent` (async chapter splitting) |
| `domain/util/` | Domain utilities: state machines, validators |

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
│    │ L4: AI Engine Call                │     │
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
WebUserController.login()          ← adapter/in (inbound adapter)
    │ delegates to
    ▼
AuthPort.login()                   ← port/in (inbound port interface)
    │ implemented by
    ▼
AuthService.login()                ← domain/service (domain service)
    │
    ├── Verify email and password (BCrypt)
    │   via UserRepositoryPort      ← port/out (outbound port interface)
    │   implemented by UserMapperAdapter  ← adapter/out (outbound adapter)
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

Previously, translation pipeline logic was duplicated across three domain services. It has been consolidated into a single `TranslationPipeline` component in `domain/service/`.

```
                    ┌─────────────────────┐
                    │  TranslationService │
                    │  TranslationTaskSvc │
                    │  RagTranslationSvc  │
                    └──────────┬──────────┘
                               │ Create Pipeline instance
                               ▼
                    ┌─────────────────────┐
                    │ TranslationPipeline  │
                    │ (domain service)     │
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

### Cache Consistency Strategy

When translation memory data is updated or deleted, a **version-stamped delayed double-delete** strategy ensures cross-instance cache consistency:

```
Data Change (update/delete)
  │
  ├── Step 1: Pre-delete — clear L1 (Caffeine) + L2 (Redis) for this language pair
  ├── Step 2: Bump version number in Redis + publish pub/sub event
  ├── Step 3: Sleep 2s (wait for in-flight writes to complete)
  ├── Step 4: Post-delete — clear L2 Redis old-version keys + expire L3 old records
  └── Step 5: All instances receive pub/sub event, flush their L1 local cache
```

Key components:

- **`CacheVersionService`** — Maintains per-language-pair version numbers in Redis, handles `INCR` + event publishing
- **`CachePubSubConfig`** — Redis `MessageListenerContainer` subscribed to `translator:cache:invalidation` channel
- **`TranslationCacheService.delayedDoubleDelete()`** — Spawns a virtual thread for the pre-delete → version bump → sleep → post-delete sequence
- **Version-prefixed Redis keys** — Cache keys include version prefix (`v{N}:<md5>`), so post-delete only removes old-version entries without affecting newly written data. `putCache()` strips any existing prefix before prepending the service version to prevent double-prefix bugs (`v1:v1:<md5>`).

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
│            ├── TranslationRateLimitFilter         │
│            │   └── IP sliding window:             │
│            │       100 req / 60s per IP           │
│            │       (atomic Lua: 4 ops → 1 call)   │
│            │       Skips API Key auth             │
│            │                                     │
│            ├── Whitelist paths → Pass directly   │
│            │   (/user/login, /user/register,     │
│            │    /health, /actuator, /swagger)    │
│            │                                     │
│            └── Other paths → Auth required        │
│                │                                 │
│                ├── ApiKeyAuthenticationFilter   │
│                │   ├── Caffeine L1 (5min)       │
│                │   ├── Redis L2 (30min)         │
│                │   ├── MySQL fallback (rare)    │
│                │   ├── incrementUsage → Redis   │
│                │   │   INCR (async flush)       │
│                │   └── Fail-closed on Redis err  │
│                │                                 │
│                ├── JwtAuthenticationFilter       │
│                │   ├── Valid token → Allow       │
│                │   └── Invalid token → 401       │
│                │                                 │
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

#### IP-Level (Security Filter Layer)

| Parameter | Value |
|-----------|-------|
| Algorithm | Redis Sorted Set sliding window |
| Key | `translation:ip_limit:{clientIP}` |
| Window | 60 seconds |
| Max requests | 100 per window |
| Scope | `/v1/translate/**` only |
| Skip | API Key authenticated requests |
| Failure | Fail-open (Redis error → allow) |
| Lua consolidation | `ZREMRANGEBYSCORE` + `ZADD` + `EXPIRE` + `ZCARD` in single atomic script |

#### Per-API-Key (Security Filter Layer)

| Parameter | Value |
|-----------|-------|
| Algorithm | Redis Sorted Set sliding window (atomic Lua script) |
| Key | `translation:key_limit:{apiKeyId}` |
| Window | 60 seconds |
| Max requests | Configurable via `TRANSLATION_KEY_RATE_LIMIT_MAX_REQUESTS` |
| Scope | API Key authenticated requests only |
| Failure | Fail-open (Redis error → allow) |
| Lua consolidation | 4 separate Redis ops → 1 atomic Lua call |

#### Per-User (Application Layer)

| User Type | Max Concurrency |
|-----------|----------------|
| Anonymous | 1 |
| Free | 1 |
| Pro | 3 |
| Max | 5 |

- Per-user concurrency enforced via `Semaphore`
- Daily quota counting via Redis sliding window
- IP extraction: `X-Forwarded-For` → `X-Real-IP` → `getRemoteAddr()`

#### Monthly Character Quota (Application Layer)

| Parameter | Value |
|-----------|-------|
| Storage | Redis (primary) + MySQL (async backup) |
| Key | `quota:chars:{userId}:{yearMonth}` |
| Algorithm | Lua script: atomic `GET` → check → `INCRBY` → `EXPIRE` |
| TTL | Days remaining in month + 10 days buffer |
| High-quota bypass | Monthly quota ≥ 10,000,000 chars → skip Redis entirely |
| Fallback | Redis unavailable → MySQL `quota_usage` table query |
| Refund | Translation failure → Lua script refunds chars (`math.max(0, current - amount)`) |
| MySQL sync | `@Async` fire-and-forget, non-blocking for quota decision |

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
- L2 RAG: Semantic similarity matching — finds translations even when source text isn't identical

### 5. Why Unified Translation Pipeline?

- Before refactoring, the 4-level pipeline logic was duplicated in 3 Services, making maintenance costly
- After extracting to `TranslationPipeline`, all translation paths call the same component, ensuring consistent behavior
- Adding post-processing (residual Chinese correction) only requires a single change

### 6. Why Redis Lua Scripts for Rate Limiting and Quota?

- Each translation request previously made ~20 Redis calls across rate limiters, API key validation, cache lookups, and quota checks
- Consolidating rate limiter operations (ZREMRANGEBYSCORE + ZADD + EXPIRE + ZCARD) into a single atomic Lua script cuts 6 Redis calls per request (×2 limiters)
- Quota check + INCR also runs as a single Lua script, guaranteeing atomicity without distributed locks
- High-quota users (≥10M chars/month) bypass Redis entirely, eliminating 1 more call per request

### 7. Why Scale Redis Connection Pool to 256?

- Under 500 VU concurrent load, the default Lettuce pool (max-active=16) became a bottleneck — threads blocked waiting for connections
- Scaling to `max-active: 256, max-idle: 128, min-idle: 32` ensures connection availability under high concurrency
- Combined with Undertow `io-threads: 32, worker-threads: 200`, the full pipeline sustains 4,200+ req/s at sub-120ms average latency

---

**Last updated**: 2026-05-08 — Hexagonal architecture migration complete. Components reorganized into `adapter/in`, `adapter/out`, `port/in`, `port/out`, `domain` layers.
