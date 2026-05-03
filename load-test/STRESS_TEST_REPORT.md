# NovelTrans — Performance & Stress Test Report

**Date**: 2026-05-04
**Environment**: Local Docker (MySQL 8.0, Redis, Java 21 Spring Boot + Undertow, Nginx)
**Translation Engine**: Mock (MTranServer + LLM Engine)
**Payment**: Stripe Test Mode
**Last Updated**: 2026-05-04 — Round 3 (cross-event idempotency fix via Redis SETNX)

---

## 1. Translation API

### Configuration

| Parameter | Value |
|---|---|
| Translation Engine | MTranServer Mock + LLM Engine Mock (`TRANSLATION_MOCK=true`) |
| Test Framework | k6 v0.52.0 |
| Endpoints | `POST /v1/translate/selection`, `POST /v1/translate/reader` |
| Concurrency | Ramp-up: 10 → 50 → 100 VUs over 4m30s |

### Results — With `sleep(1)` (Simulated Real-World User Behavior)

| Metric | Value |
|---|---|
| Total Iterations | 12,679 |
| Total Requests | 25,358 |
| Throughput | **93.7 req/s** |
| p95 Latency | **302.59 ms** |
| p90 Latency | 160.45 ms |
| Median Latency | 10.05 ms |
| Max Latency | 339.07 ms |
| Error Rate | 0% |

### Results — Without `sleep` (Maximum Throughput)

| Metric | Value |
|---|---|
| Total Iterations | 67,068 |
| Total Requests | 134,136 |
| Throughput | **496.8 req/s** |
| p95 Latency | **302.13 ms** |
| p90 Latency | 200.57 ms |
| Median Latency | 91.07 ms |
| Max Latency | 487.92 ms |
| Error Rate | 0% |

### k6 Threshold Validation

| Threshold | Target | Result | Status |
|---|---|---|---|
| p95 Duration | < 1000 ms | 302.13 ms | Pass |
| Error Rate | < 5% | 0% | Pass |

### k6 Checks

| Check | sleep(1) | No Sleep |
|---|---|---|
| selection: status 200 | 12,679 / 12,679 | 67,068 / 67,068 |
| selection: has translation | 12,679 / 12,679 | 67,068 / 67,068 |
| reader: status 200 | 12,679 / 12,679 | 67,068 / 67,068 |
| reader: has content | 12,679 / 12,679 | 67,068 / 67,068 |

### Analysis

- Median latency of 91 ms under no-sleep indicates fast core processing. The p95/p90 spread (200–302 ms) is attributed to Redis cache lookups and mtran-server network IO within the Java layer (auth, rate limiting, cache, translation dispatch).
- The `sleep(1)` version caps throughput at 93.7 req/s due to the per-iteration pause. Removing sleep reveals the actual system ceiling at **496.8 req/s** (5.3x improvement).
- Zero errors at 496 req/s confirms the rate limiter and semaphore mechanisms hold under sustained load.

---

## 2. Payment Webhook Idempotency

### 2.1 Methodology

Stripe does not expose an API for concurrent webhook replay. `stripe trigger` is sequential, and `stripe listen` is a single-connection tunnel. To simulate Stripe's worst-case retry behavior (multiple identical `event_id` arriving concurrently), the test constructs payloads locally and sends them via Python multi-threaded HTTP.

**Real-world dimensions of this test:**

| Dimension | Detail |
|---|---|
| Signature Algorithm | HMAC-SHA256, identical to Stripe's official webhook signing. Verified by backend `Webhook.constructEvent()` |
| Event ID Format | Stripe official format: `evt_1<ConnectAccountId><Hash>` |
| Stripe Entities | All IDs (subscription, customer, session, price) are real objects created on the Stripe platform |
| HTTP Concurrency | Python `threading` sends concurrent POSTs directly to the backend Undertow server |

### Architecture

```
webhook_idempotency.py (Test Script)
├── 50 threads → build payload → HMAC-SHA256 sign
│       ↑ Stripe event_id format    ↑ Stripe signing algorithm
│       └──────────┬───────────────┘
│                  ▼
│           POST /webhook/stripe
│                  ▼
StripeWebhookController.java (Backend)
├── 1. Webhook.constructEvent() → HMAC-SHA256 verification
├── 2. SubscriptionService.handleXxx() → business logic + idempotency check
├── 3. MyBatis-Plus → MySQL → atomic UPDATE (WHERE last_webhook_event_id IS NULL)
└── 4. Redis SETNX → per-event_id dedup on updateUserLevel (prevents cross-event interference)
```

### Test Parameters

| Parameter | Value |
|---|---|
| Stripe Environment | Test Mode |
| Webhook Secret | `whsec_<redacted>` (Stripe CLI) |
| Subscription ID | `sub_<test_id>` |
| Customer ID | `cus_<test_id>` |
| Tool | Python 3.x multi-threaded + HMAC-SHA256 signing |

### Round 1 — Before Fix (Race Condition Detected)

| Test | Requests | Success | Failed | user_plan_history | Status |
|---|---|---|---|---|---|
| checkout.session.completed (50×10) | 500 | 500 | 0 | 1 | Pass |
| subscription.updated (100×10) | 1,000 | 1,000 | 0 | 0 new | Pass |
| Mixed events (50×5×3) | 750 | 750 | 0 | **123** | **Fail** |

The mixed concurrency test exposed a race condition: concurrent threads passed the non-atomic `lastWebhookEventId` read check simultaneously, each inserting a duplicate `user_plan_history` record (67 FREE→PRO + 56 PRO→FREE).

### Round 2 — After Fix v1 (Atomic Idempotency)

#### `checkout.session.completed` — Pre-Insert Event ID + Two-Phase Atomic Claim

```java
// Set event_id BEFORE insert so the inserted record already carries it
subRecord.setLastWebhookEventId(event.getId());

try {
    stripeSubscriptionMapper.insert(subRecord);
    insertSucceeded = true;
} catch (DuplicateKeyException e) {
    // Another thread already inserted — re-query and claim
    subRecord = stripeSubscriptionMapper.selectOne(...);
}

// Only the DuplicateKeyException path needs atomic claim
// (the inserting thread already owns the event_id via insert)
if (!insertSucceeded) {
    int claimed = stripeSubscriptionMapper.update(null,
        new LambdaUpdateWrapper<StripeSubscription>()
            .eq(StripeSubscription::getId, subRecord.getId())
            .isNull(StripeSubscription::getLastWebhookEventId)
            .set(StripeSubscription::getLastWebhookEventId, event.getId())
    );
    if (claimed == 0) return; // another thread claimed first
}
```

#### `subscription.updated` — Atomic Conditional Update with NULL Handling

```java
int rows = stripeSubscriptionMapper.update(null,
    new LambdaUpdateWrapper<StripeSubscription>()
        .eq(StripeSubscription::getId, subRecord.getId())
        .and(w -> w
            .isNull(StripeSubscription::getLastWebhookEventId)
            .or()
            .ne(StripeSubscription::getLastWebhookEventId, event.getId())
        )
        .set(StripeSubscription::setStatus, newStatus)
        .set(StripeSubscription::setLastWebhookEventId, event.getId())
);
if (rows == 0) return; // already processed
```

#### `subscription.deleted` — Same NULL Fix

```java
int rows = stripeSubscriptionMapper.update(null,
    new LambdaUpdateWrapper<StripeSubscription>()
        .eq(StripeSubscription::getId, subRecord.getId())
        .and(w -> w
            .isNull(StripeSubscription::getLastWebhookEventId)
            .or()
            .ne(StripeSubscription::getLastWebhookEventId, event.getId())
        )
        .set(StripeSubscription::setStatus, "canceled")
        .set(StripeSubscription::setLastWebhookEventId, event.getId())
);
if (rows == 0) return;
```

#### Results

| Test | Requests | Success | Failed | user_plan_history | Final Status |
|---|---|---|---|---|---|
| checkout.session.completed (50×10) | 500 | 500 | 0 | **1** | PRO / canceled |
| subscription.updated (100×10) | 1,000 | 1,000 | 0 | **0 new** | PRO / active |
| Mixed events (50×5×3) | 750 | 750 | 0 | **98** | **Fail** |

Tests 1 & 2 pass. Mixed events still produce 98 duplicate records because different event types (each with unique `event_id`) interfere with each other's idempotency via the shared `last_webhook_event_id` column.

### Round 3 — After Fix v2 (Cross-Event Idempotency via Redis SETNX)

#### `updateUserLevel` — Redis SETNX Event Dedup

All `updateUserLevel` calls now go through a Redis-based dedup layer. Each unique `event_id` can only trigger one user level change, regardless of which webhook handler processes it:

```java
private boolean markEventProcessed(String eventId) {
    String key = "webhook:event_processed:" + eventId;
    Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofHours(24));
    return Boolean.TRUE.equals(success);
}

private void updateUserLevel(Long userId, String newLevel, String reason, String eventId) {
    if (eventId != null && !markEventProcessed(eventId)) {
        log.info("updateUserLevel: event {} already processed, skipping", eventId);
        return;
    }
    updateUserLevel(userId, newLevel, reason);
}
```

#### Final Results

| Test | Requests | Success | Failed | user_plan_history | Final Status |
|---|---|---|---|---|---|
| checkout.session.completed (50×10) | 500 | 500 | 0 | **1** | PRO / canceled |
| subscription.updated (100×10) | 1,000 | 1,000 | 0 | **0 new** | PRO / active |
| Mixed events (50×5×3) | 750 | 750 | 0 | **2** | FREE / canceled |

#### Latency (After Final Fix)

| Test | Avg | p95 | p99 | Total Duration |
|---|---|---|---|---|
| checkout.session.completed | 1,427 ms | 2,098 ms | 2,643 ms | 15.17 s |
| subscription.updated | 135 ms | 169 ms | 177 ms | 1.46 s |
| Mixed events | 534 ms | 1,279 ms | — | 8.41 s |

> `checkout.session.completed` calls `Subscription.retrieve()` on the Stripe API (network-bound, ~1.4s avg). `subscription.updated/deleted` are pure local DB operations (< 177 ms p99). Latency improvements over Round 2 are due to Stripe API response time variation.

### Idempotency Summary

| Check | Expected | Actual | Status |
|---|---|---|---|
| Webhook Signature Verification | All pass | 2,250 / 2,250 | Pass |
| No 500 Errors | Zero | 0 | Pass |
| System Stability | No crash | Stable throughout | Pass |
| checkout.session.completed Idempotency | ≤ 1 history record | 1 | Pass |
| subscription.updated Idempotency | 0 new records | 0 | Pass |
| Mixed Concurrency Idempotency | ≤ 3 history records | 2 | Pass |

> Mixed test: 2 records = `subscription.updated` (FREE→PRO) + `subscription.deleted` (PRO→FREE). `checkout.session.completed` correctly blocked by `IS NULL` claim (record already had `last_webhook_event_id` from test 2).

### Idempotency Guarantee Layers

| Layer | Mechanism | Prevents |
|---|---|---|
| L1: Application | Atomic `UPDATE WHERE last_webhook_event_id IS NULL` conditional update | Duplicate event processing (same event_id) |
| L2: Database | `stripe_subscription_id` UNIQUE index | Concurrent duplicate inserts |
| L3: Transaction | `@Transactional` on all webhook handlers | Partial state on transaction abort |
| L4: Redis SETNX | Per-event_id dedup key with 24h TTL | Cross-event-type interference (different event_id sharing `last_webhook_event_id`) |

---

## 3. Payment Checkout Endpoint

### Configuration

| Parameter | Value |
|---|---|
| Test Framework | k6 v0.52.0 |
| Endpoints | `POST /api/subscription/checkout`, `GET /api/subscription/status` |
| Concurrency | Ramp-up: 5 → 20 → 50 VUs over 4m |

### Results

| Metric | With `sleep(1)` | Without `sleep` |
|---|---|---|
| Total Iterations | 2,027 | 2,832 |
| Total Requests | 4,054 | 5,664 |
| Throughput | 16.9 req/s | **23.5 req/s** |
| p95 Latency | 2,571.55 ms | **2,092.71 ms** |
| p90 Latency | 2,138.86 ms | 1,874.36 ms |
| Median Latency | 747.05 ms | 963.80 ms |
| Max Latency | 3,987.10 ms | 3,831.22 ms |
| Error Rate | 0% | 0% |

### k6 Checks

| Check | sleep(1) | No Sleep |
|---|---|---|
| checkout: status 200 | 2,027 / 2,027 | 2,832 / 2,832 |
| checkout: has checkoutUrl | 2,027 / 2,027 | 2,832 / 2,832 |
| status: 200 | 2,027 / 2,027 | 2,832 / 2,832 |

### Comparison with Translation

| Metric | Translation (no sleep) | Checkout (no sleep) | Reason |
|---|---|---|---|
| Throughput | 496.8 req/s | 23.5 req/s | Stripe API call for each checkout (~2s round-trip) |
| p95 Latency | 302 ms | 2,093 ms | Network IO to Stripe dominates |
| Max Latency | 488 ms | 3,831 ms | Same |

---

## 4. Test Scripts

| Script | Purpose | Stack |
|---|---|---|
| `load-test/translate.js` | Translation API — real-world simulation (sleep) | k6 |
| `load-test/translate-nosleep.js` | Translation API — maximum throughput | k6 |
| `load-test/payment.js` | Checkout endpoint — real-world simulation (sleep) | k6 |
| `load-test/checkout-nosleep.js` | Checkout endpoint — maximum throughput | k6 |
| `load-test/webhook_idempotency.py` | Stripe Webhook concurrency idempotency | Python multi-threading |

### Run

```bash
docker compose up -d backend mysql redis

# Translation (with auth token)
k6 run load-test/translate.js \
  -e API_BASE_URL=http://localhost:7341 \
  -e JWT_TOKEN=<token>

# Webhook idempotency
python3 load-test/webhook_idempotency.py
```

---

## 5. Mock Services

### MTranServer Mock

- **Path**: `services/mtran-mock/`
- **Implementation**: Python `http.server` (stdlib only, zero dependencies)
- **Endpoints**: `POST /translate` → `{"result": "[<lang>] <text>"}`, `GET /health`

### LLM Engine Mock

- **Environment**: `TRANSLATION_MOCK=true`
- **Intercepted**: `/translate`, `/translate-team`, `/extract-entities`, `/translate-with-placeholders`
- **Response**: `{"data": "[<lang>] <text>", "engine": "mock"}`
