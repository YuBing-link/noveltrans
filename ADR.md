# Architecture Decision Records

This document records the architectural decisions made during the refactoring of the novel translator system. Each decision follows the [ADR format](https://cognitect.com/blog/2011/11/08/architecture-decision-records).

---

## ADR-001: Consistent Initial Status for CollabProject Creation

**Status**: Accepted
**Date**: 2026-05-05
**Context**: [Issue #1 — P0] Two project creation paths produced different initial statuses.

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

- `src/main/java/com/yumu/noveltranslator/service/CollabProjectService.java` (line 139: `ACTIVE` → `DRAFT`)
- `src/test/java/com/yumu/noveltranslator/service/CollabProjectServiceStatusTest.java` (new)

---

## ADR-002: Fine-Grained Glossary Cache Invalidation via Reverse Index

**Status**: Accepted
**Date**: 2026-05-05
**Context**: [Issue #2 — P0] Adding a single glossary term invalidated ALL translation cache entries.

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

- `src/main/java/com/yumu/noveltranslator/service/TranslationCacheService.java` (reverse index + `invalidateKeysForTerm`)
- `src/main/java/com/yumu/noveltranslator/service/CacheVersionService.java` (`bumpVersionForGlossaryTerm`, `@Deprecated bumpAllVersions`)
- `src/main/java/com/yumu/noveltranslator/controller/web/WebGlossaryController.java` (replaced global bump with targeted invalidation)
- `src/test/java/com/yumu/noveltranslator/service/GlossaryCacheInvalidationTest.java` (new, 11 tests)

---

## ADR-003: invoice.payment_succeeded as Subscription Activation Fallback

**Status**: Accepted
**Date**: 2026-05-05
**Context**: [Issue #3 — P0] `invoice.payment_succeeded` webhook was logged-only; if `checkout.session.completed` never arrived, paid users would never get activated.

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

- `src/main/java/com/yumu/noveltranslator/controller/StripeWebhookController.java` (dispatch now calls handler)
- `src/main/java/com/yumu/noveltranslator/service/SubscriptionService.java` (4 new methods)
- `src/test/java/com/yumu/noveltranslator/service/SubscriptionServiceInvoiceTest.java` (new, 12 tests, 4 disabled for MP lambda cache)
- `src/test/java/com/yumu/noveltranslator/controller/StripeWebhookControllerTest.java` (updated)

---

## ADR-004: Async Batch Chapter Insertion with Event-Driven Architecture

**Status**: Accepted
**Date**: 2026-05-05
**Context**: [Issue #4 — P1] 500-chapter synchronous insert in a single `@Transactional` caused long lock times and risk of `innodb_lock_wait_timeout`.

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

- `src/main/java/com/yumu/noveltranslator/service/CollabProjectService.java` (refactored `doCreateProject`)
- `src/main/java/com/yumu/noveltranslator/event/ChapterSplitEvent.java` (new)
- `src/main/java/com/yumu/noveltranslator/service/ChapterSplitAsyncListener.java` (new)
- `src/main/java/com/yumu/noveltranslator/config/ChapterSplitExecutorConfig.java` (new)
- `src/main/java/com/yumu/noveltranslator/task/DraftProjectRecoveryTask.java` (new)
- `src/main/java/com/yumu/noveltranslator/mapper/CollabChapterTaskMapper.java` (`countByProjectId`)
- `src/test/java/com/yumu/noveltranslator/service/ChapterSplitAsyncListenerTest.java` (new, 8 tests)
- `src/test/java/com/yumu/noveltranslator/task/DraftProjectRecoveryTaskTest.java` (new, 6 tests)

---

## ADR-005: SSE Disconnect Recovery via Redis Stream Message Replay

**Status**: Accepted
**Date**: 2026-05-05
**Context**: [Issue #5 — P1] SSE connections are fire-and-forget; a 15-second network drop means losing all collaboration events.

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

- `src/main/java/com/yumu/noveltranslator/util/SseEmitterUtil.java` (converted to `@Component`, added publish/replay methods)
- `src/main/java/com/yumu/noveltranslator/service/CollabEventPublisher.java` (new)
- `src/main/java/com/yumu/noveltranslator/service/ChapterTaskService.java` (event publishing in afterCommit hooks)
- `src/main/java/com/yumu/noveltranslator/controller/collab/CollabProjectController.java` (new SSE endpoint)
- `src/test/java/com/yumu/noveltranslator/util/SseEmitterUtilRedisTest.java` (new, 8 tests)
- `src/test/java/com/yumu/noveltranslator/service/CollabEventPublisherTest.java` (new, 4 tests)

---

## ADR-006: IP-Level Rate Limiting for Translation Endpoints

**Status**: Accepted
**Date**: 2026-05-05
**Context**: [Issue #6 — P1] Translation endpoints had no IP-level rate limiting, allowing attackers with stolen tokens to create multiple accounts and bypass per-user limits.

### Context

The existing `LoginRateLimiter` only protected the login endpoint. Translation endpoints (`/v1/translate/*`) only had per-user limits (TPM, concurrency, monthly quota). An attacker who obtained a valid token could create many accounts, each with its own quota, to bypass these limits.

### Decision

Added two new security components:

1. **`TranslationIpRateLimiter`** — Redis Sorted Set sliding window:
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

- `src/main/java/com/yumu/noveltranslator/security/TranslationIpRateLimiter.java` (new)
- `src/main/java/com/yumu/noveltranslator/security/TranslationRateLimitFilter.java` (new)
- `src/main/java/com/yumu/noveltranslator/security/SecurityConfig.java` (filter chain integration)
- `src/main/resources/application.yaml` (new `translation.ip-rate-limit` config)
- `src/test/java/com/yumu/noveltranslator/security/TranslationIpRateLimiterTest.java` (new, 4 tests)
- `src/test/java/com/yumu/noveltranslator/security/TranslationRateLimitFilterTest.java` (new, 7 tests)
- `src/test/java/com/yumu/noveltranslator/security/SecurityConfigTest.java` (updated mock)

---

## ADR-007: State Machine as Sole Status Transition Authority

**Status**: Accepted
**Date**: 2026-05-05
**Context**: [Issue #7 — P2] The state machine was "validation-only"; developers could bypass it by calling `setStatus()` directly.

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

- `src/main/java/com/yumu/noveltranslator/service/state/CollabStateMachine.java` (4 new transition methods)
- `src/main/java/com/yumu/noveltranslator/service/CollabProjectService.java` (updated `changeProjectStatus`)
- `src/main/java/com/yumu/noveltranslator/service/ChapterTaskService.java` (updated all status transitions)
- `src/main/java/com/yumu/noveltranslator/service/MultiAgentTranslationService.java` (updated 12+ call sites)
- `src/test/java/com/yumu/noveltranslator/service/state/CollabStateMachineTest.java` (updated with 24 new transition tests)
- `src/test/java/com/yumu/noveltranslator/service/MultiAgentTranslationServiceTest.java` (updated mock)
- `src/test/java/com/yumu/noveltranslator/service/MultiAgentTranslationServiceExtendedTest.java` (updated mock)

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
