# NovelTrans Test Coverage Report

> Generated: 2026-05-10 01:41

## Overall Metrics

| Metric | Covered | Total | Coverage |
|--------|---------|-------|----------|
| **Instruction** | 20,409 | 25,342 | **80.5%** |
| Branch | 1,709 | 2,369 | 72.1% |
| Line | 4,318 | 5,333 | 81.0% |
| Complexity | 1,296 | 1,915 | 67.7% |
| Method | 607 | 722 | 84.1% |
| Class | 75 | 81 | 92.6% |

## Top 15 Uncovered Business Classes

| Missed | Total | Coverage | Class |
|--------|-------|----------|-------|
| 650 | 2286 | 71.6% | `com/yumu/noveltranslator/application/service/SubscriptionApplicationService` |
| 470 | 1558 | 69.8% | `com/yumu/noveltranslator/adapter/out/translate/UserLevelThrottledTranslationClient` |
| 423 | 1460 | 71.0% | `com/yumu/noveltranslator/domain/service/MultiAgentTranslationService` |
| 372 | 1341 | 72.3% | `com/yumu/noveltranslator/domain/service/EntityConsistencyService` |
| 275 | 1154 | 76.2% | `com/yumu/noveltranslator/adapter/out/redis/TranslationCacheService` |
| 275 | 1516 | 81.9% | `com/yumu/noveltranslator/application/service/TranslationApplicationService` |
| 263 | 2136 | 87.7% | `com/yumu/noveltranslator/application/service/TranslationTaskApplicationService` |
| 236 | 1632 | 85.5% | `com/yumu/noveltranslator/application/service/CollabProjectApplicationService` |
| 224 | 524 | 57.3% | `com/yumu/noveltranslator/application/service/DocumentApplicationService` |
| 181 | 195 | 7.2% | `com/yumu/noveltranslator/application/service/ApiKeyApplicationService` |
| 167 | 823 | 79.7% | `com/yumu/noveltranslator/application/service/ChapterTaskApplicationService` |
| 129 | 422 | 69.4% | `com/yumu/noveltranslator/domain/service/TranslationPostProcessingService` |
| 112 | 403 | 72.2% | `com/yumu/noveltranslator/util/SseEmitterUtil` |
| 104 | 259 | 59.8% | `com/yumu/noveltranslator/domain/service/ChapterSplitAsyncListener` |
| 100 | 131 | 23.7% | `com/yumu/noveltranslator/domain/service/VerificationCodeService` |

## Excluded Framework Code

The following code is excluded from coverage (infrastructure/mappers/config, no business logic):

- `entity/**` — Data entities (Lombok getters/setters)
- `dto/**` — Request/Response objects
- `domain/model/**` — Domain models (field-only)
- `adapter/out/persistence/converter/**` — Entity ↔ Model converters
- `adapter/out/persistence/*RepositoryAdapter` — MyBatis-Plus CRUD delegation
- `adapter/out/redis/Redis*Adapter`, `Redis*Service` — Pure Redis operation wrappers
- `adapter/in/security/**` — Spring Security filters
- `adapter/in/rest/GlobalExceptionHandler` — Framework exception handling
- `adapter/in/rest/web/Web*Controller` — Thin routing layer
- `adapter/out/stripe/**`, `adapter/out/embedding/**` — External SDK wrappers
- `adapter/out/email/**` — Email sending wrapper
- `domain/event/**` — Domain event data carriers
- `config/**`, `mapper/**`, `enums/**`, `properties/**`, `bootstrap/**`

## Detailed Report

Full interactive HTML report: [JaCoCo Coverage Report](coverage/index.html)
