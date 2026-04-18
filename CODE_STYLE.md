# 代码规范文档 (CODE_STYLE.md)

本文档定义 NovelTranslator 项目的编码规范、命名约定、注释规范和包结构说明。所有开发人员应严格遵守此规范。

## 目录

- [命名规范](#命名规范)
- [注释规范](#注释规范)
- [包结构规范](#包结构规范)
- [代码组织](#代码组织)
- [异常处理规范](#异常处理规范)
- [日志规范](#日志规范)
- [配置管理规范](#配置管理规范)

---

## 命名规范

### Java 命名

| 元素 | 规则 | 示例 |
|------|------|------|
| 类名 | PascalCase | `TranslationService`, `UserController` |
| 接口名 | PascalCase，不添加 `I` 前缀 | `UserService`（非 `IUserService`） |
| 方法名 | camelCase | `translateWebpage()`, `getUserById()` |
| 变量名 | camelCase | `targetLang`, `textRegistry` |
| 常量 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT`, `DEFAULT_PAGE_SIZE` |
| 包名 | 全小写，点分隔 | `com.yumu.noveltranslator.service` |
| 枚举值 | UPPER_SNAKE_CASE | `GOOGLE`, `DEEPL`, `OPENAI` |

### 数据库命名

| 元素 | 规则 | 示例 |
|------|------|------|
| 表名 | snake_case，小写 | `user`, `translation_cache`, `glossary` |
| 列名 | snake_case，小写 | `user_id`, `source_word`, `create_time` |
| 索引名 | `idx_表名_列名` | `idx_user_email`, `idx_glossary_user_id` |
| 外键名 | `fk_表名_引用表名` | `fk_glossary_user` |

### 文件命名

| 类型 | 规则 | 示例 |
|------|------|------|
| Controller | `<模块>Controller.java` | `TranslateController.java` |
| Service | `<模块>Service.java` | `TranslationService.java` |
| Mapper | `<实体>Mapper.java` | `UserMapper.java` |
| Entity | 实体名.java | `User.java`, `Glossary.java` |
| DTO | `<操作><实体>Request/Response.java` | `LoginRequest.java`, `TranslationResultResponse.java` |
| Config | `<模块>Config.java` | `RedisConfig.java`, `SecurityConfig.java` |
| Util | `<功能>Util.java` | `JwtUtils.java`, `CacheKeyUtil.java` |
| Enum | `<功能>Enum.java` | `ErrorCodeEnum.java`, `TranslationEngine.java` |

---

## 注释规范

### 类级别 Javadoc

每个类必须有 Javadoc 注释，说明其职责：

```java
/**
 * 翻译服务层
 * <p>
 * 负责核心翻译逻辑，包括 3 级缓存查询、SSE 流式响应、
 * 外部翻译引擎调用协调、文本分段处理等。
 * </p>
 *
 * @author your-name
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
public class TranslationService {
    // ...
}
```

### 方法级别 Javadoc

公共方法必须有 Javadoc，包含参数说明和返回值：

```java
/**
 * 执行网页批量翻译
 * <p>
 * 接收 DOM 映射表，逐段翻译并通过 SSE 流式返回结果。
 * 翻译过程中优先查询 3 级缓存，未命中时调用外部翻译引擎。
 * </p>
 *
 * @param request 翻译请求，包含目标语言、文本映射表等
 * @return SseEmitter 用于流式推送翻译结果
 * @throws BusinessException 当翻译引擎不可用时抛出
 */
public SseEmitter translateWebpage(WebpageTranslateRequest request) {
    // ...
}
```

### 行内注释

- 注释解释 "为什么"（Why），而非 "是什么"（What）
- 复杂业务逻辑必须注释
- 使用 `//` 单行注释或 `/* */` 多行注释

```java
// 缓存击穿防护：使用 intern() 确保相同 Key 使用同一把锁
synchronized (cacheKey.intern()) {
    // 双重检查：获取锁后再次查询缓存
    String cached = cacheService.get(cacheKey);
    if (cached != null) {
        return cached;
    }
    // 缓存未命中，查询数据库
    String result = queryDatabase(originalText);
    cacheService.put(cacheKey, result);
    return result;
}
```

### DTO 字段注释

使用 Lombok `@Data` 时，每个字段应有注释：

```java
@Data
public class WebpageTranslateRequest {
    /** 目标语言代码 (zh, en, ja, ...) */
    @NotBlank(message = "目标语言不能为空")
    private String targetLang;

    /** 源语言代码，auto 表示自动检测 */
    private String sourceLang;

    /** 待翻译文本映射表 */
    @NotEmpty(message = "文本映射表不能为空")
    private List<TextTranslationRequest> textRegistry;
}
```

---

## 包结构规范

```
com.yumu.noveltranslator/
├── config/           # Spring 配置类
│   ├── RedisConfig.java
│   ├── SecurityConfig.java
│   ├── TranslationExecutorConfig.java
│   └── ...
├── controller/       # REST 控制器
│   ├── TranslateController.java
│   ├── UserController.java
│   └── ...
├── dto/              # 数据传输对象
│   ├── request/      # （可选）请求 DTO 子包
│   ├── response/     # （可选）响应 DTO 子包
│   └── ...
├── entity/           # 数据库实体
│   ├── User.java
│   ├── Glossary.java
│   └── ...
├── enums/            # 枚举定义
│   ├── ErrorCodeEnum.java
│   ├── TranslationEngine.java
│   └── ...
├── mapper/           # MyBatis-Plus Mapper
│   ├── UserMapper.java
│   ├── GlossaryMapper.java
│   └── ...
├── security/         # Spring Security 相关
│   ├── JwtAuthenticationFilter.java
│   ├── JwtUtils.java
│   └── ...
├── service/          # 业务逻辑层
│   ├── TranslationService.java
│   ├── UserService.java
│   └── ...
└── util/             # 工具类
    ├── CacheKeyUtil.java
    ├── JwtUtils.java
    └── ...
```

### 包职责说明

| 包 | 职责 | 依赖方向 |
|----|------|----------|
| `config` | Spring Bean 配置、属性绑定 | 无依赖 |
| `controller` | HTTP 请求处理、参数校验 | -> dto, service |
| `service` | 核心业务逻辑、事务管理 | -> mapper, dto, entity, util |
| `mapper` | 数据库操作接口 | -> entity |
| `entity` | 数据库表映射 | 无依赖 |
| `dto` | API 数据传输 | 无依赖 |
| `security` | 认证授权相关 | -> util, entity |
| `enums` | 业务枚举 | 无依赖 |
| `util` | 通用工具 | 无依赖 |

### 依赖方向原则

```
controller -> service -> mapper -> entity
     ↓           ↓
    dto         dto
     ↓           ↓
    util        util
```

- **禁止循环依赖**: controller 不直接依赖 mapper
- **单向依赖**: 上层依赖下层，不允许反向
- **DTO 独立**: DTO 包不依赖任何业务包
- **工具类独立**: util 包不依赖任何业务包

---

## 代码组织

### 类内部结构顺序

```java
public class ExampleService {

    // 1. 常量定义
    private static final int MAX_RETRY = 3;

    // 2. 依赖注入字段（使用 final + @RequiredArgsConstructor）
    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    // 3. 公共方法
    public User getUserById(Long id) { ... }

    // 4. 包级私有方法
    void internalMethod() { ... }

    // 5. 私有方法
    private String buildCacheKey(Long id) { ... }

    // 6. 内部类（如有）
    private static class InnerClass { ... }
}
```

### Controller 方法结构

```java
@PostMapping("/translate/webpage")
public SseEmitter translateWebpage(@Validated @RequestBody WebpageTranslateRequest request) {
    // 1. 参数校验（由 @Validated 完成）
    // 2. 业务处理
    return translationService.translateWebpage(request);
}
```

### Service 方法结构

```java
public SseEmitter translateWebpage(WebpageTranslateRequest request) {
    // 1. 前置处理（日志、参数转换）
    log.info("Starting webpage translation, targetLang={}", request.getTargetLang());

    // 2. 核心业务逻辑
    SseEmitter emitter = new SseEmitter(TIMEOUT);

    // 3. 异步执行
    translationExecutor.execute(() -> {
        try {
            doTranslate(emitter, request);
        } catch (Exception e) {
            handleTranslationError(emitter, e);
        }
    });

    // 4. 返回
    return emitter;
}
```

---

## 异常处理规范

### 统一响应格式

所有 API 返回统一的 `Result<T>` 格式：

```java
@Data
public class Result<T> {
    private String code;    // 业务状态码
    private String msg;     // 错误信息
    private T data;         // 响应数据

    public static <T> Result<T> success(T data) { ... }
    public static <T> Result<T> error(ErrorCodeEnum error) { ... }
    public static <T> Result<T> error(ErrorCodeEnum error, String msg) { ... }
}
```

### 错误码定义

使用 `ErrorCodeEnum` 集中管理错误码：

```java
@Getter
@AllArgsConstructor
public enum ErrorCodeEnum {
    SUCCESS("200", "成功"),
    BAD_REQUEST("400", "请求参数错误"),
    UNAUTHORIZED("401", "未认证"),
    FORBIDDEN("403", "无权限"),
    USER_NOT_FOUND("1001", "用户不存在"),
    EMAIL_ALREADY_REGISTERED("1002", "邮箱已注册"),
    INVALID_VERIFICATION_CODE("1003", "验证码无效"),
    TRANSLATION_ENGINE_ERROR("2001", "翻译引擎错误"),
    RATE_LIMIT_EXCEEDED("3001", "超过速率限制"),
    INTERNAL_ERROR("5000", "服务器内部错误");

    private final String code;
    private final String message;
}
```

### 全局异常处理

使用 `@RestControllerAdvice` 统一拦截异常：

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

## 日志规范

### 日志级别

| 级别 | 使用场景 | 示例 |
|------|----------|------|
| ERROR | 系统异常、服务不可用 | 翻译引擎调用失败、数据库连接断开 |
| WARN | 潜在问题、降级处理 | 缓存未命中降级、API Key 缺失 |
| INFO | 关键业务流程、启动/关闭 | 服务启动、翻译任务开始/完成 |
| DEBUG | 调试信息、详细参数 | 缓存命中详情、请求参数 |

### 日志格式

```java
@Slf4j
public class TranslationService {

    public void translate() {
        log.info("Translation started, sourceLang={}, targetLang={}, engine={}",
                sourceLang, targetLang, engine);

        try {
            // ...
            log.debug("Cache hit for key={}", cacheKey);
        } catch (Exception e) {
            log.error("Translation failed, sourceLang={}, targetLang={}",
                    sourceLang, targetLang, e);
            throw new BusinessException(ErrorCodeEnum.TRANSLATION_ENGINE_ERROR);
        }
    }
}
```

### 日志注意事项

- 使用 `@Slf4j` (Lombok) 声明日志
- 禁止使用 `System.out.println()` 或 `e.printStackTrace()`
- 敏感信息（密码、Token）不打印
- 使用参数化日志 `{}` 而非字符串拼接

---

## 配置管理规范

### 敏感信息处理

- **禁止**在代码或配置文件中硬编码密钥、密码、Token
- 所有敏感配置必须通过环境变量注入
- 使用 `${ENV_VAR:default}` 语法，敏感项不提供默认值

```yaml
spring:
  datasource:
    password: ${MYSQL_PASSWORD:}  # 不提供默认值
jwt:
  secret: ${JWT_SECRET}           # 不提供默认值
```

### 配置类命名

- 使用 `@ConfigurationProperties` 绑定配置
- 配置类以 `Properties` 或 `Config` 结尾

```java
@Data
@Component
@ConfigurationProperties(prefix = "translation.limit")
public class TranslationLimitProperties {
    private int freeDailyLimit = 100;
    private int proDailyLimit = 1000;
    private int freeConcurrencyLimit = 5;
    private int proConcurrencyLimit = 20;
    private int anonymousConcurrencyLimit = 3;
}
```

### 环境区分

| 环境 | 配置文件 | 说明 |
|------|----------|------|
| 开发 | `application.yaml` | 默认配置，连接本地 MySQL/Redis |
| 生产 | 环境变量 + `application.yaml` | 所有敏感信息通过环境变量注入 |

---

## 代码审查检查清单

提交 PR 前，请自查以下项目：

- [ ] 所有公共类和方法有 Javadoc 注释
- [ ] 命名符合规范（PascalCase/camelCase/UPPER_SNAKE_CASE）
- [ ] 无 `System.out.println()`，使用 `@Slf4j`
- [ ] 敏感信息未硬编码
- [ ] 异常使用 `ErrorCodeEnum` 定义的错误码
- [ ] Controller 方法使用 `@Validated` 参数校验
- [ ] Service 写操作有 `@Transactional`
- [ ] 依赖注入使用构造函数（非 `@Autowired` 字段）
- [ ] 新增代码有对应的单元测试
- [ ] 提交信息符合 Conventional Commits 格式
