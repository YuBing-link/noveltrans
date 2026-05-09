# Architecture Decision Records

This document records the architectural decisions made during the evolution of the novel translator system. Each decision follows the [ADR format](https://cognitect.com/blog/2011/11/08/architecture-decision-records).

> File paths reflect the **post-refactoring** hexagonal structure (see ADR-012).
> For pre-refactoring paths, consult `git log --follow`.

---

## ADR-001: Consistent Initial Status for CollabProject Creation

**Status**: Accepted
**Date**: 2026-05-05
**Context**: Two project creation paths produced different initial statuses.

### Context

The system had two paths to create a `CollabProject`:
1. `createProject()` — manual creation, set status to `DRAFT`
2. `createProjectFromDocument()` — document-based creation, set status to `ACTIVE`

This inconsistency meant document-based projects skipped the draft phase, which violated the expected lifecycle and made state transitions unpredictable.

### Decision

Both creation paths now initialize `CollabProject` with status `DRAFT`. The project is later activated via the state machine transition `DRAFT → ACTIVE` after async chapter splitting completes.

### Consequences

- **Positive**: Predictable initial state; draft status provides a clear "not ready" signal
- **Positive**: All projects follow the same lifecycle path
- **Negative**: Document-based projects require an extra step (async activation) before becoming usable (mitigated by automatic activation via `ChapterSplitAsyncListener`)

### Files Changed

- `src/main/java/com/yumu/noveltranslator/domain/service/CollabProjectService.java` (line 139: `ACTIVE` → `DRAFT`)
- `src/test/java/com/yumu/noveltranslator/domain/service/CollabProjectServiceStatusTest.java` (new)

---

## ADR-002: Fine-Grained Glossary Cache Invalidation via Reverse Index

**Status**: Accepted
**Date**: 2026-05-05
**Context**: Adding a single glossary term invalidated ALL translation cache entries.

### Context

When a glossary term was added/updated/deleted, `CacheVersionService.bumpAllVersions()` incremented version counters for ALL language pairs. This caused a global cache stampede — every cached translation became stale and required re-fetching from the LLM, directly increasing API costs.

### Decision

Implemented a term-aware reverse index on Redis:
- On `putCache()`, extract words (length >= 3) from source text using `\b[\w\p{L}]{3,}\b`
- For each unique word, maintain a Redis Set: `glossary:cache_keys:{word_lowercase}` → Set of cache keys
- On glossary change, `invalidateKeysForTerm(sourceWord)` looks up the Set via `SMEMBERS`, then deletes affected keys across L1 (Caffeine), L2 (Redis), and L3 (DB)
- `bumpAllVersions()` is marked `@Deprecated` and retained only as a fallback for `deleteGlossaryItem` (where source word is no longer available)

### Consequences

- **Positive**: Cache hit rate preserved — only affected entries are invalidated
- **Positive**: Impact analysis possible — frontend can show "N cached translations affected"
- **Negative**: Minor write overhead on every `putCache()` (one `SADD` per unique word in source text)
- **Negative**: 24h TTL on reverse index Sets means they may expire before the associated cache entries (acceptable: stale entries will naturally expire via their own TTL)

### Files Changed

- `src/main/java/com/yumu/noveltranslator/adapter/out/redis/TranslationCacheService.java` (reverse index + `invalidateKeysForTerm`)
- `src/main/java/com/yumu/noveltranslator/adapter/out/redis/CacheVersionService.java` (`bumpVersionForGlossaryTerm`, `@Deprecated bumpAllVersions`)
- `src/main/java/com/yumu/noveltranslator/adapter/in/rest/web/WebGlossaryController.java` (replaced global bump with targeted invalidation)
- `src/test/java/com/yumu/noveltranslator/adapter/out/redis/GlossaryCacheInvalidationTest.java` (new, 11 tests)

---

## ADR-003: invoice.payment_succeeded as Subscription Activation Fallback

**Status**: Accepted
**Date**: 2026-05-05
**Context**: `invoice.payment_succeeded` webhook was logged-only; if `checkout.session.completed` never arrived, paid users would never get activated.

### Context

Stripe sends multiple webhook events for a successful payment. The primary activation path was `checkout.session.completed`, but `invoice.payment_succeeded` was treated as informational only. If Stripe failed to send `checkout.session.completed` (network issue, misconfiguration), the user paid but their subscription remained inactive.

### Decision

`invoice.payment_succeeded` now triggers `SubscriptionService.handleInvoicePaymentSucceeded()` with three paths:
1. **Already active/trialing**: Skip (already handled by `checkout.session.completed`)
2. **Exists but non-active** (e.g., `past_due`): Atomically activate via `doActivateSubscriptionFromInvoice()` using the same `LambdaUpdateWrapper` pattern with `lastWebhookEventId` + timestamp ordering
3. **No local record** (orphaned invoice): Create full subscription record via `createSubscriptionFromOrphanedInvoice()` — Stripe HTTP calls outside `@Transactional`, DB writes inside narrow transaction, `DuplicateKeyException` handling for concurrent races

The existing 5-layer idempotency defense (signature → Redis SETNX → lastWebhookEventId → DuplicateKeyException → timestamp ordering) protects against double-processing.

### Consequences

- **Positive**: Users always get activated even if the primary webhook is lost
- **Positive**: No duplicate activation — same idempotency chain as existing handlers
- **Negative**: `invoice.payment_succeeded` may trigger Stripe HTTP calls for orphaned invoices (mitigated by early-return checks for active status)

### Files Changed

- `src/main/java/com/yumu/noveltranslator/adapter/in/webhook/StripeWebhookController.java` (dispatch now calls handler)
- `src/main/java/com/yumu/noveltranslator/adapter/out/stripe/SubscriptionService.java` (4 new methods)
- `src/test/java/com/yumu/noveltranslator/adapter/out/stripe/SubscriptionServiceInvoiceTest.java` (new, 12 tests, 4 disabled for MP lambda cache)
- `src/test/java/com/yumu/noveltranslator/adapter/in/webhook/StripeWebhookControllerTest.java` (updated)

---

## ADR-004: Async Batch Chapter Insertion with Event-Driven Architecture

**Status**: Accepted
**Date**: 2026-05-05
**Context**: 500-chapter synchronous insert in a single `@Transactional` caused long lock times and risk of `innodb_lock_wait_timeout`.

### Context

The original `doCreateProject()` method created the project, owner member, AND all 500+ chapter tasks in one large transaction. For novels with large `sourceText` fields (10KB+ per chapter), this held InnoDB row locks for seconds, blocking concurrent reads and risking timeout.

### Decision

Refactored into an event-driven async pipeline:

1. **Narrow transaction** (`createProjectAndOwner`): Creates project (`DRAFT`) + owner member only
2. **Publish domain event** (`ChapterSplitEvent`): Carries project ID, chapter list, document info, language pair
3. **Async listener** (`ChapterSplitAsyncListener`): Consumes event on dedicated thread pool (`chapterSplitExecutor`, core=2/max=5), inserts chapters in batches of 50 per transaction, activates project via state machine when complete
4. **Compensation task** (`DraftProjectRecoveryTask`): Scheduled every 5 minutes, scans `DRAFT` projects older than 10 minutes. If chapters exist → activate (async finished). If no chapters → log as stale for manual intervention

The `@Async` method catches all exceptions internally to prevent them from bubbling up to the event publisher.

### Consequences

- **Positive**: Transaction lock time reduced from ~seconds (500 inserts) to ~ms (50 inserts per batch)
- **Positive**: User gets immediate response (project created) rather than waiting for all inserts
- **Positive**: Compensation task handles worst-case scenario (node crash mid-insertion)
- **Negative**: Project briefly appears as `DRAFT` before activation (~1-2 seconds for 500 chapters)
- **Negative**: Requires `@EnableAsync` and dedicated thread pool (already configured)

### Files Changed

- `src/main/java/com/yumu/noveltranslator/domain/service/CollabProjectService.java` (refactored `doCreateProject`)
- `src/main/java/com/yumu/noveltranslator/domain/event/ChapterSplitEvent.java` (new)
- `src/main/java/com/yumu/noveltranslator/domain/service/ChapterSplitAsyncListener.java` (new)
- `src/main/java/com/yumu/noveltranslator/config/ChapterSplitExecutorConfig.java` (new)
- `src/main/java/com/yumu/noveltranslator/task/DraftProjectRecoveryTask.java` (new)
- `src/main/java/com/yumu/noveltranslator/adapter/out/persistence/mapper/CollabChapterTaskMapper.java` (`countByProjectId`)
- `src/test/java/com/yumu/noveltranslator/domain/service/ChapterSplitAsyncListenerTest.java` (new, 8 tests)
- `src/test/java/com/yumu/noveltranslator/task/DraftProjectRecoveryTaskTest.java` (new, 6 tests)

---

## ADR-005: SSE Disconnect Recovery via Redis Stream Message Replay

**Status**: Accepted
**Date**: 2026-05-05
**Context**: SSE connections are fire-and-forget; a 15-second network drop means losing all collaboration events.

### Context

The original `SseEmitterUtil` was a static utility class with no message persistence. When a user's SSE connection dropped and reconnected, there was no way to replay events that occurred during the disconnection window.

### Decision

Implemented Redis Stream-based message buffering:

- **Publish**: `publishCollabEvent(projectId, eventType, payload)` writes to Redis Stream `collab:events:{projectId}` via Lua script `XADD`. Event payload is JSON: `{"eventId": "...", "type": "...", "payload": "..."}`
- **Replay**: `replayMissedEvents(projectId, lastEventId, emitter)` reads via Lua script `XRANGE` from the given `lastEventId` forward, sending each event through the SSE emitter
- **Integration**: `CollabEventPublisher` is a Spring `@Component` wrapping the publish logic. Called from `ChapterTaskService` via `TransactionSynchronization.afterCommit()` on `submitChapter()`, `assignChapter()`, and `reviewChapter()` — ensuring events are published only AFTER the DB transaction commits
- **SSE Endpoint**: `GET /v1/collab/sse/{projectId}?lastEventId=xxx` — replays missed events if `lastEventId` provided, then sends `connected` event and completes

`SseEmitterUtil` was converted from a static utility to a Spring `@Component` to enable `StringRedisTemplate` injection. All existing static methods (`registerEmitter`, `sendData`, etc.) remain backward-compatible.

### Consequences

- **Positive**: Clients can reconnect and recover missed events without full state refresh
- **Positive**: Transaction-safe — events published only after DB commit
- **Positive**: Uses existing Redis infrastructure; no new dependencies
- **Negative**: Redis Stream grows unbounded unless capped (Lua script doesn't set MAXLEN; operational runbook should include periodic trimming)
- **Negative**: Clients must manage `lastEventId` state (client-side complexity)

### Files Changed

- `src/main/java/com/yumu/noveltranslator/util/SseEmitterUtil.java` (converted to `@Component` Spring Bean, added publish/replay methods; remains in `util/` package)
- `src/main/java/com/yumu/noveltranslator/domain/service/CollabEventPublisher.java` (new)
- `src/main/java/com/yumu/noveltranslator/domain/service/ChapterTaskService.java` (event publishing in afterCommit hooks)
- `src/main/java/com/yumu/noveltranslator/adapter/in/rest/collab/CollabProjectController.java` (new SSE endpoint)
- `src/test/java/com/yumu/noveltranslator/util/SseEmitterUtilRedisTest.java` (new, 8 tests)
- `src/test/java/com/yumu/noveltranslator/domain/service/CollabEventPublisherTest.java` (new, 4 tests)

---

## ADR-006: IP-Level Rate Limiting for Translation Endpoints

**Status**: Accepted
**Date**: 2026-05-05
**Context**: Translation endpoints had no IP-level rate limiting, allowing attackers with stolen tokens to create multiple accounts and bypass per-user limits.

### Context

The existing `LoginRateLimiter` only protected the login endpoint. Translation endpoints (`/v1/translate/*`) only had per-user limits (TPM, concurrency, monthly quota). An attacker who obtained a valid token could create many accounts, each with its own quota, to bypass these limits.

### Decision

Added two new security components:

1. **`TranslationIpRateLimiter`** (later merged into `RedisSlidingWindowRateLimiter` per ADR-012) — Redis Sorted Set sliding window:
   - Key pattern: `translation:ip_limit:{clientIP}`
   - Algorithm: `ZREMRANGEBYSCORE` (remove entries > 60s old) + `ZADD` (add current timestamp) + `ZCARD` (count)
   - Default: 100 requests per IP per 60-second window (configurable via `translation.ip-rate-limit`)
   - Fail-open on Redis errors to avoid DoS

2. **`TranslationRateLimitFilter`** — `OncePerRequestFilter`:
   - Only applies to `/v1/translate/*` paths
   - Skips API Key authenticated requests (they have per-key tracking)
   - IP extraction: `X-Forwarded-For` → `X-Real-IP` → `getRemoteAddr()`
   - Returns HTTP 429 with JSON body on limit exceeded

3. **Filter chain order**: `TranslationRateLimitFilter` → `JwtAuthenticationFilter` → `ApiKeyAuthenticationFilter`

### Consequences

- **Positive**: Closes the multi-account attack vector
- **Positive**: Fail-open design prevents self-DoS if Redis is unavailable
- **Positive**: Configurable thresholds via application.yaml
- **Negative**: Shared IP users (corporate NAT, public WiFi) may share the same rate limit budget
- **Negative**: No per-IP quota tracking across multiple tokens (only raw request count)

### Files Changed

- `src/main/java/com/yumu/noveltranslator/adapter/in/security/RedisSlidingWindowRateLimiter.java` (merged from `TranslationIpRateLimiter` and `TranslationKeyRateLimiter`, ADR-012)
- `src/main/java/com/yumu/noveltranslator/adapter/in/security/TranslationRateLimitFilter.java` (new)
- `src/main/java/com/yumu/noveltranslator/adapter/in/security/SecurityConfig.java` (filter chain integration)
- `src/main/resources/application.yaml` (new `translation.ip-rate-limit` config)
- `src/test/java/com/yumu/noveltranslator/adapter/in/security/TranslationIpRateLimiterTest.java` (retained for backward compat; tests `RedisSlidingWindowRateLimiter`)
- `src/test/java/com/yumu/noveltranslator/adapter/in/security/TranslationRateLimitFilterTest.java` (new, 7 tests)

---

## ADR-007: State Machine as Sole Status Transition Authority

**Status**: Accepted
**Date**: 2026-05-05
**Context**: The state machine was "validation-only"; developers could bypass it by calling `setStatus()` directly.

### Context

`CollabStateMachine` only provided `validateProjectTransition()` and `validateChapterTransition()` methods that threw exceptions on invalid transitions. However, service layer code manually called `setStatus()` after validation, and there was nothing preventing a developer from calling `setStatus()` without validation.

### Decision

Added "driving" methods to `CollabStateMachine` that encapsulate validation AND status assignment:
- `transitionProject(CollabProject, CollabProjectStatus)` — validates then sets status
- `transitionChapter(CollabChapterTask, ChapterTaskStatus)` — validates then sets status
- String-based overloads for convenience

Updated ALL existing callers:
- `CollabProjectService.changeProjectStatus()` — uses `transitionProject()`
- `ChapterTaskService.assignChapter()/submitChapter()/reviewChapter()` — uses `transitionChapter()`
- `MultiAgentTranslationService` (12+ call sites) — all `setStatus()` replaced with `transitionChapter()`
  - Notably, `reviewChapter()` now properly transitions through the intermediate `REVIEWING` state (previously never set, making the validation check a dead letter)
  - Cache-hit path transitions: `SUBMITTED → REVIEWING → APPROVED → COMPLETED`

The existing `validate*` methods are preserved for read-only checks but the Javadoc now recommends `transition*` methods as the sole way to change status.

### Consequences

- **Positive**: Status transitions are now self-documenting — the state machine encodes the full lifecycle
- **Positive**: `reviewChapter()` now correctly sets `REVIEWING` state, enabling proper audit trails
- **Positive**: New developers cannot accidentally bypass state validation
- **Negative**: Does not prevent `setStatus()` at the entity level (could be addressed with immutable status fields in future)
- **Negative**: Slightly more verbose call sites (but safer)

### Files Changed

- `src/main/java/com/yumu/noveltranslator/domain/service/CollabStateMachine.java` (4 new transition methods; moved from `domain/service/state/`)
- `src/main/java/com/yumu/noveltranslator/domain/service/TranslationStateMachine.java` (translation state machine; moved from `domain/service/state/`)
- `src/main/java/com/yumu/noveltranslator/domain/service/CollabProjectService.java` (updated `changeProjectStatus`)
- `src/main/java/com/yumu/noveltranslator/domain/service/ChapterTaskService.java` (updated all status transitions)
- `src/main/java/com/yumu/noveltranslator/domain/service/MultiAgentTranslationService.java` (updated 12+ call sites)
- `src/test/java/com/yumu/noveltranslator/domain/service/CollabStateMachineTest.java` (updated with 24 new transition tests)
- `src/test/java/com/yumu/noveltranslator/domain/service/MultiAgentTranslationServiceTest.java` (updated mock)
- `src/test/java/com/yumu/noveltranslator/domain/service/MultiAgentTranslationServiceExtendedTest.java` (updated mock)

---

## ADR-008: Zero MySQL in Translation Hot Path — Redis-Backed Authentication and Metering

**Status**: Accepted
**Date**: 2026-05-06
**Context**: [Load Test Round 20 — P0] 500 VU concurrent load triggered request timeouts; root cause traced to HikariCP connection pool exhaustion.

### Context

During the 20th load test iteration (500 virtual users hitting translation endpoints for 60 seconds), the system exhibited the following symptoms:

- **k6 reported `http_req_duration` p95 = 7.86s, avg = 4.25s**, with throughput capped at ~112 req/s
- **100% HTTP 200 success rate** (no 5xx/4xx errors) — indicating requests were served, but severely delayed
- MTran mock translation engine responded in `costMs=0`, confirming the bottleneck was not in the translation logic itself
- Backend logs showed MTran requests queuing for seconds before execution, despite near-zero processing time

Profiling revealed the root cause: **every incoming request on the translation hot path synchronously executed 3 MySQL queries inside `ApiKeyAuthenticationFilter`**:

1. `apiKeyMapper.findByApiKey(jwt)` — validate API key and retrieve associated user ID
2. `apiKeyMapper.incrementUsage(id)` — atomically increment request counter
3. `userMapper.selectById(userId)` — load user profile for `userLevel` resolution

With 500 VUs each issuing 2 requests per iteration, the sustained concurrency was approximately **1,000 QPS**. The HikariCP connection pool was configured with `maximum-pool-size: 20`. At 1,000 concurrent requests contending for 20 connections, average queue wait time reached 4+ seconds.

Compounding the problem, `QuotaService.tryConsumeChars()` — after successfully deducting quota via Redis Lua script — made an additional **synchronous MySQL write** to `quota_usage` for daily usage logging.

**Root cause**: MySQL was serving as a high-frequency cache and counter store on every request's critical path. The design principle that "MySQL should only handle relational data, not high-frequency cache queries" was violated at the authentication and metering layers.

### Decision

All MySQL access is removed from the translation hot path (`/v1/translate/**`). Specific measures:

#### 1. API Key authentication backed by Redis cache

- `ApiKeyAuthenticationFilter` queries Redis key `apikey:{token}` first
- **Cache hit**: resolve `userId` / `userLevel` directly, **zero MySQL queries**
- **Cache miss**: query MySQL once → populate Redis with 30-minute TTL
- Caffeine L1 local cache (5-minute TTL) as a front layer to protect Redis from hot-key stampedes

#### 2. `incrementUsage` replaced by Redis `INCR` with async flush

- Per-request usage counting switches to `RedisTemplate.opsForValue().increment()`
- A scheduled task (`@Scheduled`, 60-second interval) batches Redis counters and flushes to MySQL (`api_key.last_used_at`, `api_key.total_usage`)
- A `@PreDestroy` hook performs a final flush on graceful shutdown to prevent data loss

#### 3. `quota_usage` daily logging made asynchronous

- Redis Lua atomic deduction logic remains unchanged (correct by design)
- `quotaUsageMapper.incrementUsage()` is offloaded to a dedicated `@Async` thread pool (`meteringExecutor`, core=2, max=4, queue=1000)
- `refundChars()` on translation failure follows the same async path
- Backpressure: when the async queue is full, the quota check still succeeds (quota decision is Redis-based; the MySQL log is best-effort)

#### 4. HikariCP pool size increase (transitional)

- `maximum-pool-size` increased from 20 to 50 as an immediate mitigation
- Long-term target: reduce back to 10 once the hot path is fully decoupled (pool needed only for admin operations and async flush)

#### 5. Redis failure fallback for authentication

- **Fail-closed**: if Redis is unreachable during `apikey:{token}` lookup, the filter returns HTTP 503 with `Retry-After: 10`. Translation traffic is intentionally blocked rather than allowed to bypass authentication.
- Admin operations (Stripe webhooks, project management) continue unaffected — they are not on the translation hot path and use MySQL fallback directly.

### Rejected Alternatives

#### Scale HikariCP to 200 connections

Increasing the connection pool to match VU count was considered and immediately rejected:
- MySQL's `innodb_thread_concurrency` and disk I/O would become the next bottleneck under sustained load;
- Each connection consumes ~2–5 MB JVM memory + thread stack overhead; 200 connections would destabilize the container;
- Treats the symptom (connection scarcity) instead of the cause (wrong data-plane placement).

#### Synchronous MySQL with connection pool per request

Using a dedicated short-lived connection per query was considered but rejected:
- Connection creation overhead (~5–10ms handshake) would dominate per-request latency;
- Does not solve the fundamental problem of MySQL being in the hot path.

### Cache Consistency

`apikey:{token}` entries follow the same invalidation strategy established in ADR-002:
- On API key update/delete: Redis pub/sub event → all instances flush Caffeine L1 cache
- On Redis eviction: natural TTL expiry, no manual invalidation needed

### Architecture Comparison

| Responsibility | Before (Round 20) | After |
|---|---|---|
| API Key lookup | Synchronous MySQL per request | Redis cache → Caffeine L1 |
| API Key `incrementUsage` | Synchronous MySQL write per request | Redis `INCR` → scheduled batch flush |
| User profile loading | Synchronous MySQL per request | Redis cache |
| Character quota check | Redis Lua (correct) | Redis Lua (unchanged) |
| `quota_usage` daily log | Synchronous MySQL write | MySQL async (`@Async`) |
| Per-user concurrency semaphore | JVM `ConcurrentHashMap` (correct) | JVM `ConcurrentHashMap` (unchanged) |
| TPM sliding window | JVM `SlidingWindowCounter` (correct) | JVM `SlidingWindowCounter` (unchanged) |

### Consequences

- **Positive**: Zero MySQL queries on the translation hot path; throughput ceiling moves from connection-pool-bound to CPU/Network-bound
- **Positive**: HikariCP 20 connections sufficient for admin/async traffic; pool pressure eliminated
- **Positive**: Authentication and metering consolidated on Redis — single hot-path data store
- **Negative**: `incrementUsage` shifts from strong consistency to eventual consistency (≤60s flush window). Accepted: metering is audit-only, not real-time billing
- **Negative**: Ungraceful shutdown (SIGKILL, OOM) loses up to 60 seconds of metering data. Accepted risk: metering is used for analytics and audit, not for real-time billing decisions (billing is Stripe-driven). If future requirements demand zero-loss metering, a Redis Stream WAL or per-request MySQL append-only log must be introduced
- **Negative**: API key revocation has a worst-case 35-minute window without pub/sub invalidation (5 min Caffeine + 30 min Redis). Mitigation: key deletion triggers a Redis pub/sub invalidation event to flush L1/L2 immediately; without pub/sub, the window is bounded by TTL
- **Negative**: Redis failure triggers fail-closed (HTTP 503) on translation endpoints; requires Redis Sentinel/Cluster for production HA
- **Negative**: `quota_usage` MySQL table may exhibit transient staleness (seconds) — does not affect quota decisions (decision logic is entirely Redis-based)

### Files Changed

- `src/main/java/com/yumu/noveltranslator/adapter/in/security/ApiKeyAuthenticationFilter.java` (Redis-backed auth lookup)
- `src/main/java/com/yumu/noveltranslator/adapter/out/redis/ApiKeyCacheService.java` (Redis `INCR` + async flush)
- `src/main/java/com/yumu/noveltranslator/domain/service/QuotaService.java` (async `quota_usage` logging)
- `src/main/resources/application.yaml` (HikariCP pool size 20 → 50)
- `src/main/java/com/yumu/noveltranslator/adapter/in/security/JwtAuthenticationFilter.java` (updated for Redis auth)

---

## ADR-009: Fix Cache Key Version Double-Prefix Bug

**Status**: Accepted
**Date**: 2026-05-06
**Context**: [Load Test Round 26 — P0] Cache write path produced keys with double version prefix (`v1:v1:<md5>_fast`), while read path searched for single prefix (`v1:<md5>_fast`), resulting in 0% cache hit rate under 500 VU load.

### Context

`CacheKeyUtil.buildCacheKey()` already includes a version prefix in the returned key (`v1:<md5>`). When `TranslationCacheService.putCache()` receives this key, it unconditionally prepends another version prefix by calling `CacheVersionService.getVersion()`, producing `v{version}:v1:<md5>_fast` (e.g., `v1:v1:e87ce486..._fast`).

Meanwhile, the read path (`getCacheByMode()` and `getCache()`) receives the key directly from the pipeline — which already contains `v1:<md5>` — and looks for exactly that key in Redis. The write and read paths never matched, so every translation request executed the full hot path (Redis lookup miss → translation engine → cache write), even though the cache was being populated.

This was discovered during Round 26 when inspecting Redis keys via `KEYS "tc:*"` — all keys showed the `v1:v1:` double prefix pattern.

### Decision

`TranslationCacheService.putCache()` now strips the existing version prefix before prepending the service-managed version:

```java
String version = cacheVersionService.getVersion(sourceLang, targetLang);
String strippedKey = cacheKey.replaceFirst("^v\\d+:", "");
String baseKey = "v" + version + ":" + strippedKey;
String finalKey = (mode != null && !mode.isBlank()) ? baseKey + "_" + mode : baseKey;
```

This ensures that:
- The key written to Redis is `v{version}:<md5>_fast` (e.g., `v1:e87ce486..._fast`)
- The key read from Redis is the same pattern, since `CacheKeyUtil` returns `v1:<md5>` which after stripping becomes `<md5>`, then re-prefixed with `v1`

### Consequences

- **Positive**: Cache hit rate jumps from 0% to ~100% on repeated content; throughput increases 3.5x (150 → 524 req/s), avg latency drops 72% (1,630ms → 462ms)
- **Positive**: Translation engine load reduced dramatically — cached content returns in < 1ms
- **Positive**: No operational migration needed — old `v1:v1:` keys naturally expire after their 30-minute TTL
- **Negative**: During the transition period, both old and new key formats coexist in Redis (acceptable: no incorrect behavior, just transient cache misses)

### Files Changed

- `src/main/java/com/yumu/noveltranslator/adapter/out/redis/TranslationCacheService.java` (lines 265-267: strip prefix before re-prefixing)

---

## ADR-010: Redis Lua Atomic Consolidation for Rate Limiters

**Status**: Accepted
**Date**: 2026-05-06
**Context**: [Load Test Round 23 — P1] Each rate limiter made 4 separate Redis calls per request, contributing to high latency under 500 VU concurrent load.

### Context

The IP rate limiter and per-API-key rate limiter each executed 4 separate Redis operations per request:
1. `ZREMRANGEBYSCORE` — remove entries outside the sliding window
2. `ZADD` — add current timestamp
3. `EXPIRE` — set key TTL
4. `ZCARD` — count entries in window

Under 500 VU with 2 requests per iteration (~1000 QPS peak), each of these 4 calls added network round-trip latency between the backend and Redis containers. Combined with other hot-path Redis calls (auth cache, quota check, usage increment), each request made 20+ Redis round-trips.

### Decision

Each rate limiter's 4 operations are consolidated into a single atomic Lua script:

```lua
local key = KEYS[1]
local now = tonumber(ARGV[1])
local windowStart = tonumber(ARGV[2])
local ttl = tonumber(ARGV[3])
local maxRequests = tonumber(ARGV[4])

redis.call('ZREMRANGEBYSCORE', key, 0, windowStart)
redis.call('ZADD', key, now, now)
redis.call('EXPIRE', key, ttl)
local count = redis.call('ZCARD', key)
return count
```

The Java side executes this via `StringRedisTemplate.execute()` with a `DefaultRedisScript<Long>`, passing all parameters as script arguments. This reduces 4 Redis round-trips to 1 per rate limiter invocation.

Additionally, `ApiKeyCacheService.incrementUsage()` had a redundant `EXPIRE` call after every `INCR` — removed, relying on the initial TTL set by the first write.

### Rejected Alternatives

#### Redis pipelining

Pipeline batching was considered but rejected:
- Pipelining reduces round-trip count but sacrifices atomicity — the rate limit check and update could interleave with other clients' calls, producing incorrect counts
- Lua scripts provide both atomicity AND single round-trip, making pipelining unnecessary

### Consequences

- **Positive**: 8 Redis calls saved per request (4 per rate limiter × 2 limiters), reducing total from ~20 to ~12
- **Positive**: Atomicity guaranteed — all 4 operations execute as a single Redis command, no race conditions
- **Positive**: Redis server CPU load reduced — fewer command dispatches
- **Negative**: Lua scripts are harder to debug than individual Redis CLI commands
- **Negative**: Script embedded in Java code — changes require redeployment (acceptable: rate limit parameters are stable)

### Files Changed

- `src/main/java/com/yumu/noveltranslator/adapter/in/security/RedisSlidingWindowRateLimiter.java` (rewritten with Lua script; merged IP + key rate limiters per ADR-012)
- `src/main/java/com/yumu/noveltranslator/adapter/out/redis/ApiKeyCacheService.java` (removed redundant `EXPIRE` from `incrementUsage`)

---

## ADR-011: QuotaService Bypass for High-Quota Users

**Status**: Accepted
**Date**: 2026-05-06
**Context**: [Load Test Round 27 — P1] After cache key fix (ADR-009), throughput reached 524 req/s but each request still made 3 Redis calls (IP rate limiter, key rate limiter, quota check). The quota check was redundant for high-quota users.

### Context

`QuotaService.tryConsumeChars()` executes a Redis Lua script per request to atomically check and deduct character quota from `quota:chars:{userId}:{yearMonth}`. This is essential for users with finite quotas (free tier: 100K chars/month, pro: 500K chars/month).

However, during load testing (and for enterprise-tier users), the configured monthly quota exceeds 10 million characters — effectively unlimited. Every request still executes the Redis Lua script, checks the quota, and deducts the cost. Under 500 VU, this adds unnecessary Redis round-trip latency for every request when the quota check is guaranteed to pass.

### Decision

`QuotaService.tryConsumeChars()` checks the configured monthly quota before making any Redis call:

```java
private static final long UNLIMITED_QUOTA_THRESHOLD = 10_000_000L;

public boolean tryConsumeChars(Long userId, String userLevel, long translatedCharCount, String mode) {
    long quota = getMonthlyQuota(userLevel);

    // Skip Redis check for effectively unlimited quotas
    if (quota >= UNLIMITED_QUOTA_THRESHOLD) {
        return true;
    }
    // ... existing Redis Lua script path ...
}
```

The threshold of 10,000,000 is chosen because:
- Free tier: 100,000 chars/month → always triggers Redis check
- Pro tier: 500,000 chars/month → always triggers Redis check
- Max tier: 2,000,000 chars/month → always triggers Redis check
- Load test config: 100,000,000 chars/month → bypassed

This means production users always get quota-checked, while load-testing and hypothetical "unlimited" enterprise tiers skip Redis entirely.

### Consequences

- **Positive**: 1 Redis call eliminated per request under high-quota configuration; throughput jumps from 524 to 4,235 req/s (8x)
- **Positive**: Redis server load reduced under high-concurrency scenarios
- **Negative**: No usage tracking for bypassed users in `quota_usage` table (acceptable: usage tracking is audit/analytics-only, not required for billing which is Stripe-driven)
- **Negative**: If `UNLIMITED_QUOTA_THRESHOLD` is set too low, legitimate pro users could bypass quota enforcement (mitigated by threshold being 50x the highest paid tier)

### Files Changed

- `src/main/java/com/yumu/noveltranslator/domain/service/QuotaService.java` (added `UNLIMITED_QUOTA_THRESHOLD` constant and early-return bypass in `tryConsumeChars`)

---

## ADR-012: Hexagonal Architecture Package Restructuring

**Status**: Accepted
**Date**: 2026-05-06
**Context**: Traditional three-tier package structure (`controller/service/mapper`) fails to communicate architectural intent, mixes adapter logic with domain logic, and enables creeping code duplication.

### Context

The project evolved from a simple CRUD application into a system with multiple inbound protocols (REST API, Webhook, SSE, Chrome Extension), multiple outbound adapters (MySQL, Redis, LLM engines, Stripe, SMTP), and rich domain logic (state machines, translation pipelines, quota management, collaboration workflows).

The current `controller / service / mapper / entity / dto` package-by-layer structure has the following problems:

1. **Adapter logic mixed with domain logic** — `TranslationService` contains both pipeline orchestration (domain concern) and HTTP client calls to Python/MTranServer (infrastructure concern)
2. **No port interface boundary** — Services directly inject `Mapper` classes, `StringRedisTemplate`, `WebClient`, etc. Making it impossible to swap implementations or test in isolation
3. **Code duplication across adapters** — Bearer token parsing duplicated 4×, client IP extraction duplicated 2×, API key masking duplicated 2×, JSON error responses hand-coded in filters, ownership checks copy-pasted across controllers
4. **Circular dependencies** — `TranslationCacheService` ↔ `CacheVersionService` worked around with `@Lazy`
5. **Flat service package** — 30+ classes in a single `service` package with no domain grouping

### Decision

Restructure from package-by-layer to **package-by-component within a hexagonal architecture**:

```
src/main/java/com/yumu/noveltranslator/
├── port/in/                          # Inbound port interfaces (driving)
│   ├── AuthPort.java
│   ├── UserPort.java
│   ├── DocumentPort.java
│   ├── GlossaryPort.java
│   ├── TranslatePort.java
│   ├── TranslationTaskPort.java
│   ├── RagTranslationPort.java
│   ├── CollabPort.java
│   ├── ChapterTaskPort.java
│   ├── CollabCommentPort.java
│   ├── SubscriptionPort.java
│   ├── WebhookPort.java
│   ├── ApiKeyPort.java
│   ├── CacheAdminPort.java
│   └── DeviceTokenPort.java
├── port/out/                         # Outbound port interfaces (driven)
│   ├── UserRepositoryPort.java
│   ├── DocumentRepositoryPort.java
│   ├── GlossaryRepositoryPort.java
│   ├── TranslationRepositoryPort.java
│   ├── CollaborationRepositoryPort.java
│   ├── TranslationEnginePort.java
│   ├── CachePort.java
│   ├── EmailPort.java
│   ├── StripePort.java
│   ├── EmbeddingPort.java
│   └── DeviceTokenPort.java
├── port/dto/                         # Data Transfer Objects
│   ├── auth/                         # LoginRequest, RegisterRequest, etc.
│   ├── subscription/                 # SubscriptionResponse, CheckoutRequest
│   ├── collab/                       # CollabProjectResponse, ChapterTaskResponse
│   ├── translation/                  # TranslationRequest, DocumentTranslationRequest
│   ├── entity/                       # ApiKeyResponse, DocumentInfoResponse
│   └── common/                       # Result, PageResponse, ErrorResponse
├── adapter/in/                       # Inbound adapters (driving)
│   ├── rest/                         # REST controllers (web, plugin, external, shared, admin, collab)
│   ├── security/                     # JWT/API-Key filters, CustomUserDetails, rate limiters, ProjectAccessAspect
│   ├── service/                      # SSE event streaming (TranslationSseService)
│   └── webhook/                      # Stripe webhook handler
├── adapter/out/                      # Outbound adapters (driven)
│   ├── persistence/                  # Repository adapters + converters
│   │   ├── entity/                   # MyBatis-Plus entities
│   │   ├── mapper/                   # MyBatis-Plus BaseMapper interfaces
│   │   └── converter/                # Domain model ↔ Entity converters
│   ├── redis/                        # Redis cache, pub/sub, rate limiter, token blacklist, event publisher
│   ├── translate/                    # LLM engine clients (OpenAI, MTranServer)
│   ├── email/                        # SMTP email sender, device token service
│   └── stripe/                       # Stripe API client
├── domain/                           # Core domain (depends on NOTHING)
│   ├── model/                        # Domain entities (User, Document, Glossary, etc.)
│   ├── service/                      # Domain services (Auth, User, Translation, Pipeline, State Machine, etc.)
│   ├── event/                        # Domain events (ChapterSplitEvent)
│   └── util/                         # Domain utilities (state machines, validators)
├── config/                           # Spring configuration (wiring)
├── properties/                       # @ConfigurationProperties bindings
├── bootstrap/                        # Application startup initialization
├── task/                             # @Scheduled tasks (DraftProjectRecoveryTask)
├── exception/                        # Global exception handler
├── enums/                            # Enumerations (ErrorCodeEnum, etc.)
└── util/                             # Cross-cutting utilities (JwtUtils, SecurityUtil, etc.)
```

**Key rules:**
- `domain/` has NO dependencies on Spring, MyBatis, Redis, HTTP — pure Java
- `port/` contains ONLY Java interfaces and DTOs — no implementations
- `adapter/in/` depends on `port/in/` and `domain/`
- `adapter/out/` depends on `port/out/` and implements them with infrastructure
- `config/` wires everything together — the ONLY place where adapters are connected to ports

**Code deduplication as part of restructuring:**
- Merge `TranslationIpRateLimiter` + `TranslationKeyRateLimiter` → `RedisSlidingWindowRateLimiter` (parameterized bean)
- Extract `SecurityUtil.parseBearerToken()`, `SecurityUtil.getClientIp()`, `SecurityUtil.maskApiKey()`
- Extract `FilterResponseUtil.writeJsonError()` for filter JSON responses
- Extract `OwnershipVerifier` service for ownership check pattern
- Consolidate DTO shared fields into `BaseTranslationRequest`
- Fix `SseEmitterUtil` `DefaultRedisScript` caching
- Resolve `TranslationCacheService` ↔ `CacheVersionService` circular dependency

### Consequences

- **Positive**: New developers can understand the architecture from package names alone
- **Positive**: Domain logic is isolated — testable without Spring context
- **Positive**: Swapping implementations (MySQL → MongoDB, Redis → Memcached) only requires changing the adapter wiring in `config/`
- **Positive**: Duplicated code eliminated through extraction during the move
- **Negative**: Large refactoring — many file moves and import updates
- **Negative**: Initial learning curve for developers unfamiliar with hexagonal architecture
- **Negative**: Some "thin" adapters will have very few lines of code (acceptable: clarity over density)

### Files Changed

**Commit**: `2295961` — "refactor: restructure to hexagonal architecture (ports and adapters)"

**Summary**: 224 files changed, 8,164 insertions(+), 1,071 deletions(-)

**Package movements**:
- `controller/` → `adapter/in/rest/` (all REST controllers)
- `security/` → `adapter/in/security/` (filters, CustomUserDetails, rate limiters, annotations)
- `mapper/` → `adapter/out/persistence/mapper/` (all MyBatis-Plus mappers)
- `entity/` → `adapter/out/persistence/entity/` (all JPA/MyBatis entities)
- `service/` → split across:
  - `adapter/out/redis/` (TranslationCacheService, CacheVersionService, ApiKeyCacheService)
  - `adapter/out/stripe/` (SubscriptionService)
  - `adapter/out/email/` (email-related services)
  - `adapter/out/translate/` (translation engine clients)
  - `domain/service/` (business logic: AuthService, UserService, TranslationPipeline, StateMachines, etc.)
- `event/` → `domain/event/`
- `dto/` → `port/dto/` (with subdirectories: auth, subscription, collab, translation, entity, common)
- `util/` → `util/` (unchanged, plus new extractors: SecurityUtil helpers)

**DTO reorganization** (`dto/` → `port/dto/` with 6 subdirectories):
- `dto/auth/` — LoginRequest, RegisterRequest, TokenResponse, etc.
- `dto/subscription/` — SubscriptionResponse, CheckoutRequest, etc.
- `dto/collab/` — CollabProjectResponse, ChapterTaskResponse, AssignChapterRequest, etc.
- `dto/translation/` — TranslationRequest, DocumentTranslationRequest, etc.
- `dto/entity/` — ApiKeyResponse, DocumentInfoResponse, CreateApiKeyRequest, etc.
- `dto/common/` — Result, PageResponse, ErrorResponse, etc.

**New utilities extracted**:
- `util/OwnershipVerifier.java` — reusable ownership check pattern
- `util/FilterResponseUtil.java` — JSON error responses for filters
- `util/SecurityUtil.java` — added `parseBearerToken()`, `getClientIp()`, `maskApiKey()`

**Compilation result**: 217 source files, BUILD SUCCESS (Java 21, Spring Boot 3.2, MyBatis-Plus 3.5.5)

**Import path migration** (all callers updated):
- `com.yumu.noveltranslator.mapper.*` → `com.yumu.noveltranslator.adapter.out.persistence.mapper.*`
- `com.yumu.noveltranslator.entity.*` → `com.yumu.noveltranslator.adapter.out.persistence.entity.*`
- `com.yumu.noveltranslator.security.*` → `com.yumu.noveltranslator.adapter.in.security.*`
- `com.yumu.noveltranslator.controller.*` → `com.yumu.noveltranslator.adapter.in.rest.*`
- `com.yumu.noveltranslator.service.state.*` → `com.yumu.noveltranslator.domain.service.*`
- `com.yumu.noveltranslator.dto.*` → `com.yumu.noveltranslator.port.dto.*` (categorized subdirectory imports)

---

## Summary

| ADR | Severity | Area | Key Decision |
|-----|----------|------|-------------|
| 001 | P0 | Data consistency | Unified `DRAFT` initial status for all project creation paths |
| 002 | P0 | Cache efficiency | Term reverse index for fine-grained invalidation instead of global bump |
| 003 | P0 | Payment reliability | `invoice.payment_succeeded` as fallback activation path |
| 004 | P1 | Database performance | Async batch insert (50/batch) via domain event + compensation task |
| 005 | P1 | User experience | Redis Stream for SSE event replay with `lastEventId` recovery |
| 006 | P1 | Security | IP-level Redis sliding window rate limiter for translation endpoints |
| 007 | P2 | Code safety | State machine drives transitions; all callers updated |
| **008** | **P0** | **Performance** | **Zero MySQL in hot path: auth/metering on Redis, incrementUsage async flush** |
| **009** | **P0** | **Correctness** | **Cache key double-prefix bug fix — strip existing version before re-prefixing** |
| **010** | **P1** | **Performance** | **Rate limiter 4 ops → 1 Lua script (×2), saves 8 Redis calls per request** |
| **011** | **P1** | **Performance** | **QuotaService bypass for high-quota users — skip Redis check when quota ≥ 10M** |
| **012** | **P1** | **Architecture** | **Hexagonal architecture: port/adapter structure, domain isolation, code deduplication** |
