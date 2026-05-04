# Test Coverage Report

> Generated: 2026-05-04
> Tool: JaCoCo 0.8.12
> Test Framework: JUnit 5 + Mockito
> Build: Maven + Spring Boot

**Related documents:**

- [README.md](README.md) — Project overview
- [CONTRIBUTING.md](CONTRIBUTING.md) — Testing requirements
- [load-test/STRESS_TEST_REPORT.md](load-test/STRESS_TEST_REPORT.md) — Performance stress test results

---

## 1. Overview

| Metric | Coverage | Details |
|--------|----------|---------|
| **Instruction Coverage** | **86%** | 3,399 of 25,212 missed |
| **Branch Coverage** | **75%** | 578 of 2,402 missed |
| **Line Coverage** | 87% | 721 of 5,475 missed |
| **Method Coverage** | 92% | 49 of 649 missed |
| **Class Coverage** | 100% | 0 of 69 missed |

| Test Summary | Count |
|-------------|-------|
| Total Tests | **1,627** |
| Passed | 1,626 |
| Failed | 0 |
| Skipped | 1 |

---

## 2. Coverage by Package

| Package | Instruction | Branch |
|---------|------------|--------|
| `service.pipeline` | 98% | 93% |
| `service.state` | 100% | 100% |
| `security.aspect` | 100% | 100% |
| `task` | 100% | n/a |
| `controller.admin` | 100% | n/a |
| `controller.external` | 95% | 84% |
| `controller` | 93% | 89% |
| `security` | 96% | 81% |
| `controller.collab` | 85% | n/a |
| `service` | 85% | 75% |
| `util` | 86% | 76% |
| `controller.web` | 87% | 60% |
| `controller.plugin` | 81% | 70% |
| `controller.shared` | 79% | 90% |

---

## 3. Core Service Coverage

### 3.1 Translation Core

| Service | Instruction | Branch |
|---------|------------|--------|
| TranslationPipeline | 98% | 93% |
| TranslationTaskService | 94% | 83% |
| TranslationCacheService | 92% | 100% |
| TranslationService | 78% | 68% |
| TranslationPostProcessingService | 70% | 54% |

### 3.2 Business Services

| Service | Instruction | Branch |
|---------|------------|--------|
| TeamTranslationService | 96% | 70% |
| EmbeddingService | 98% | 79% |
| CollabCommentService | 93% | 86% |
| CollabProjectService | 93% | 79% |
| UserService | 92% | 79% |
| SubscriptionService | 83% | 78% |
| AuthService | 88% | 75% |
| ChapterTaskService | 87% | 62% |
| MultiAgentTranslationService | 79% | 76% |
| RagTranslationService | 80% | 75% |
| QuotaService | 76% | 75% |
| AiGlossaryService | 77% | 68% |
| ExternalTranslationService | 93% | 68% |
| DocumentService | 72% | 70% |
| EntityConsistencyService | 72% | 61% |
| UserLevelThrottledTranslationClient | 70% | 67% |

### 3.3 Fully Covered

| Service | Instruction | Branch |
|---------|------------|--------|
| DeviceTokenService | 100% | 100% |
| TranslationMemoryService | 100% | 100% |
| EngineAliasRegistry | 100% | 100% |

---

## 4. Newly Added Tests

| Test File | Tests | Target |
|-----------|-------|--------|
| TranslationTaskServiceExtended2Test | 38 | Streaming translation, file upload, error paths |
| TranslationPipelineExtendedTest | 81 | Full 4-level pipeline architecture coverage |
| EntityConsistencyServiceExtendedTest | 83 | Entity extraction, placeholder replacement, consistency translation |
| TranslationServiceExtended2Test | 63 | Engine aliases, quota refunds, XSS sanitization, streaming |
| **Total** | **265** | — |

---

## 5. Weak Modules

The following services have instruction coverage below 80% and should receive additional tests:

| Service | Branch Coverage | Primary Uncovered Areas |
|---------|----------------|------------------------|
| TranslationPostProcessingService | 54% | Complex HTML repair, untranslated Chinese detection edge cases |
| EntityConsistencyService | 61% | Segmented entity extraction failures, degradation paths |
| UserLevelThrottledTranslationClient | 67% | Engine fallback logic, retry paths |
| TranslationService | 68% | Streaming SSE boundaries, webpage translation error recovery |
| ChapterTaskService | 62% | Team chapter task state transitions |
| AiGlossaryService | 68% | AI glossary generation error handling |
| DocumentService | 70% | Complex document format parsing, large file handling |

---

## 6. Test Structure

```
src/test/java/com/yumu/noveltranslator/
├── controller/
│   ├── collab/          # Collaboration project controller tests
│   ├── web/             # Web controller tests
│   ├── plugin/          # Plugin controller tests
│   ├── shared/          # Shared controller tests
│   ├── admin/           # Admin controller tests
│   └── external/        # External API controller tests
├── service/
│   ├── pipeline/        # Translation pipeline tests (98% / 93%)
│   ├── state/           # State machine tests (100% / 100%)
│   ├── TranslationTaskService*.java      (94% / 83%)
│   ├── TranslationService*.java          (78% / 68%)
│   ├── TranslationCacheService*.java     (92% / 100%)
│   ├── SubscriptionService*.java         (83% / 78%)
│   ├── EntityConsistencyService*.java    (72% / 61%)
│   ├── MultiAgentTranslationService*.java (79% / 76%)
│   ├── RagTranslationService*.java       (80% / 75%)
│   ├── QuotaService*.java                (76% / 75%)
│   └── ...
├── security/            # Security & authentication tests
└── util/                # Utility class tests
```
