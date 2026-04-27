# Code Style Guide (CODE_STYLE.md)

This document defines the coding standards, naming conventions, comment guidelines, and package structure for NovelTrans. All contributors should follow these conventions.

## Table of Contents

- [Naming Conventions](#naming-conventions)
- [Comment Guidelines](#comment-guidelines)
- [Package Structure](#package-structure)
- [Code Organization](#code-organization)
- [Exception Handling](#exception-handling)
- [Logging Standards](#logging-standards)
- [Configuration Management](#configuration-management)
- [Code Review Checklist](#code-review-checklist)

---

## Naming Conventions

### Java Naming

| Element | Rule | Example |
|---------|------|---------|
| Class name | PascalCase | `TranslationService`, `UserController` |
| Interface name | PascalCase, no `I` prefix | `UserService` (not `IUserService`) |
| Method name | camelCase | `translateWebpage()`, `getUserById()` |
| Variable name | camelCase | `targetLang`, `textRegistry` |
| Constant | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT`, `DEFAULT_PAGE_SIZE` |
| Package name | Lowercase, dot-separated | `com.yumu.noveltranslator.service` |
| Enum value | UPPER_SNAKE_CASE | `GOOGLE`, `DEEPL`, `OPENAI` |

### Database Naming

| Element | Rule | Example |
|---------|------|---------|
| Table name | snake_case, lowercase | `user`, `translation_cache`, `glossary` |
| Column name | snake_case, lowercase | `user_id`, `source_word`, `create_time` |
| Index name | `idx_tablename_column` | `idx_user_email`, `idx_glossary_user_id` |
| Foreign key name | `fk_tablename_referenced_table` | `fk_glossary_user` |

### File Naming

| Type | Rule | Example |
|------|------|---------|
| Controller | `<Module>Controller.java` | `TranslateController.java` |
| Service | `<Module>Service.java` | `TranslationService.java` |
| Mapper | `<Entity>Mapper.java` | `UserMapper.java` |
| Entity | Entity name.java | `User.java`, `Glossary.java` |
| DTO | `<Operation><Entity>Request/Response.java` | `LoginRequest.java`, `TranslationResultResponse.java` |
| Config | `<Module>Config.java` | `RedisConfig.java`, `SecurityConfig.java` |
| Util | `<Feature>Util.java` | `JwtUtils.java`, `CacheKeyUtil.java` |
| Enum | `<Feature>Enum.java` | `ErrorCodeEnum.java`, `TranslationEngine.java` |

---

## Comment Guidelines

### Class-Level Javadoc

Every class must have Javadoc documenting its responsibility:

```java
/**
 * Translation service layer
 * <p>
 * Handles core translation logic including 3-level cache lookup,
 * SSE streaming response, external translation engine coordination,
 * and text segmentation.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class TranslationService {
    // ...
}
```

### Method-Level Javadoc

Public methods require Javadoc with parameter and return descriptions:

```java
/**
 * Execute full-page batch translation
 * <p>
 * Receives a DOM mapping table, translates each segment, and streams
 * results back via SSE. Prioritizes 3-level cache lookup before
 * falling back to external translation engines.
 * </p>
 *
 * @param request translation request containing target language, text mapping, etc.
 * @return SseEmitter for streaming translation results
 * @throws BusinessException when no translation engine is available
 */
public SseEmitter translateWebpage(WebpageTranslateRequest request) {
    // ...
}
```

### Inline Comments

- Comments explain "why", not "what"
- Complex business logic must be commented
- Use `//` for single-line or `/* */` for multi-line

```java
// Cache breakdown protection: intern() ensures same key uses same lock object
synchronized (cacheKey.intern()) {
    // Double-check: query cache again after acquiring lock
    String cached = cacheService.get(cacheKey);
    if (cached != null) {
        return cached;
    }
    // Cache miss, query database
    String result = queryDatabase(originalText);
    cacheService.put(cacheKey, result);
    return result;
}
```

### DTO Field Comments

Each field in Lombok `@Data` classes should have a comment:

```java
@Data
public class WebpageTranslateRequest {
    /** Target language code (zh, en, ja, ...) */
    @NotBlank(message = "Target language cannot be empty")
    private String targetLang;

    /** Source language code, auto for auto-detect */
    private String sourceLang;

    /** Text mapping table */
    @NotEmpty(message = "Text mapping cannot be empty")
    private List<TextTranslationRequest> textRegistry;
}
```

---

## Package Structure

```
com.yumu.noveltranslator/
├── config/           # Spring configuration classes
├── controller/       # REST controllers
│   ├── web/          # Web dashboard endpoints
│   ├── plugin/       # Browser extension endpoints
│   ├── external/     # External API endpoints
│   └── shared/       # Shared translation endpoints
├── dto/              # Data transfer objects
├── entity/           # Database entities
├── enums/            # Enum definitions
├── mapper/           # MyBatis-Plus data access
├── security/         # Spring Security + JWT
├── service/          # Business logic layer
│   └── pipeline/     # TranslationPipeline component
└── util/             # Utility classes
```

### Package Responsibilities

| Package | Responsibility | Dependencies |
|---------|----------------|--------------|
| `config` | Spring bean config, property binding | None |
| `controller` | HTTP request handling, validation | -> dto, service |
| `service` | Core business logic, transactions | -> mapper, dto, entity, util |
| `mapper` | Database CRUD interfaces | -> entity |
| `entity` | Database table mappings | None |
| `dto` | API data transport | None |
| `security` | Authentication/authorization | -> util, entity |
| `enums` | Business enums | None |
| `util` | General utilities | None |

### Dependency Flow

```
controller -> service -> mapper -> entity
     ↓           ↓
    dto         dto
     ↓           ↓
    util        util
```

- **No circular dependencies**: controllers never directly depend on mappers
- **Unidirectional**: upper layers depend on lower layers, never reversed
- **DTO independence**: DTOs have no dependencies on business packages
- **Utility independence**: utilities have no dependencies on business packages

---

## Code Organization

### Class Member Order

```java
public class ExampleService {

    // 1. Constants
    private static final int MAX_RETRY = 3;

    // 2. Dependency-injected fields (final + @RequiredArgsConstructor)
    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    // 3. Public methods
    public User getUserById(Long id) { ... }

    // 4. Package-private methods
    void internalMethod() { ... }

    // 5. Private methods
    private String buildCacheKey(Long id) { ... }

    // 6. Inner classes (if any)
    private static class InnerClass { ... }
}
```

### Controller Method Structure

```java
@PostMapping("/translate/webpage")
public SseEmitter translateWebpage(@Validated @RequestBody WebpageTranslateRequest request) {
    // 1. Validation handled by @Validated
    // 2. Delegate to service
    return translationService.translateWebpage(request);
}
```

### Service Method Structure

```java
public SseEmitter translateWebpage(WebpageTranslateRequest request) {
    // 1. Pre-processing (logging, parameter conversion)
    log.info("Starting webpage translation, targetLang={}", request.getTargetLang());

    // 2. Core business logic
    SseEmitter emitter = new SseEmitter(TIMEOUT);

    // 3. Async execution
    translationExecutor.execute(() -> {
        try {
            doTranslate(emitter, request);
        } catch (Exception e) {
            handleTranslationError(emitter, e);
        }
    });

    // 4. Return
    return emitter;
}
```

---

## Exception Handling

### Unified Response Format

All APIs return a consistent `Result<T>` envelope:

```java
@Data
public class Result<T> {
    private String code;    // Business status code
    private String msg;     // Error message
    private T data;         // Response data

    public static <T> Result<T> success(T data) { ... }
    public static <T> Result<T> error(ErrorCodeEnum error) { ... }
    public static <T> Result<T> error(ErrorCodeEnum error, String msg) { ... }
}
```

### Error Code Enum

All error codes are centralized in `ErrorCodeEnum`:

```java
@Getter
@AllArgsConstructor
public enum ErrorCodeEnum {
    SUCCESS("200", "Success"),
    BAD_REQUEST("400", "Bad request"),
    UNAUTHORIZED("401", "Unauthorized"),
    FORBIDDEN("403", "Forbidden"),
    USER_NOT_FOUND("1001", "User not found"),
    EMAIL_ALREADY_REGISTERED("1002", "Email already registered"),
    INVALID_VERIFICATION_CODE("1003", "Invalid verification code"),
    TRANSLATION_ENGINE_ERROR("2001", "Translation engine error"),
    RATE_LIMIT_EXCEEDED("3001", "Rate limit exceeded"),
    INTERNAL_ERROR("5000", "Internal server error");

    private final String code;
    private final String message;
}
```

### Global Exception Handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return Result.error(ErrorCodeEnum.BAD_REQUEST, message);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("Unexpected error", e);
        return Result.error(ErrorCodeEnum.INTERNAL_ERROR);
    }
}
```

---

## Logging Standards

### Log Levels

| Level | Use Case | Example |
|-------|----------|---------|
| ERROR | System errors, service unavailable | Translation engine failure, DB disconnect |
| WARN | Potential issues, fallback behavior | Cache miss fallback, missing API key |
| INFO | Key business events, startup/shutdown | Service started, translation task started/completed |
| DEBUG | Debug info, detailed parameters | Cache hit details, request parameters |

### Logging Format

```java
@Slf4j
public class TranslationService {

    public void translate() {
        log.info("Translation started, sourceLang={}, targetLang={}, engine={}",
                sourceLang, targetLang, engine);

        try {
            log.debug("Cache hit for key={}", cacheKey);
        } catch (Exception e) {
            log.error("Translation failed, sourceLang={}, targetLang={}",
                    sourceLang, targetLang, e);
            throw new BusinessException(ErrorCodeEnum.TRANSLATION_ENGINE_ERROR);
        }
    }
}
```

### Logging Rules

- Use `@Slf4j` (Lombok) for logging declarations
- Never use `System.out.println()` or `e.printStackTrace()`
- Never log sensitive information (passwords, tokens)
- Use parameterized logging `{}` instead of string concatenation

---

## Configuration Management

### Sensitive Information

- **Never** hardcode keys, passwords, or tokens in code or config files
- All sensitive configuration must be injected via environment variables
- Use `${ENV_VAR:default}` syntax; sensitive items have no default value

```yaml
spring:
  datasource:
    password: ${MYSQL_PASSWORD:}  # no default
jwt:
  secret: ${JWT_SECRET}           # no default
```

### Configuration Property Classes

- Use `@ConfigurationProperties` for binding
- Class names end with `Properties` or `Config`

### Environment Distinction

| Environment | Configuration | Description |
|-------------|---------------|-------------|
| Development | `application.yaml` | Default config, local MySQL/Redis |
| Production | Environment variables + `application.yaml` | All secrets via env vars |

---

## Code Review Checklist

Before submitting a PR, verify:

- [ ] All public classes and methods have Javadoc
- [ ] Naming follows conventions (PascalCase/camelCase/UPPER_SNAKE_CASE)
- [ ] No `System.out.println()`, using `@Slf4j`
- [ ] No hardcoded sensitive information
- [ ] Exceptions use `ErrorCodeEnum` codes
- [ ] Controller methods use `@Validated` for validation
- [ ] Service write operations have `@Transactional`
- [ ] Constructor injection used (no `@Autowired` field injection)
- [ ] New code has corresponding unit tests
- [ ] Commit messages follow Conventional Commits format
