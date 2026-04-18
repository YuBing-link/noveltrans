# 贡献指南 (Contributing Guide)

感谢你对 NovelTranslator 项目的关注！本文档说明如何参与项目开发、提交代码以及遵循的规范。

## 目录

- [开发环境搭建](#开发环境搭建)
- [项目结构](#项目结构)
- [编码规范](#编码规范)
- [Git 工作流](#git-工作流)
- [提交信息规范](#提交信息规范)
- [Pull Request 流程](#pull-request-流程)
- [测试要求](#测试要求)

---

## 开发环境搭建

### 必需工具

| 工具 | 版本要求 | 用途 |
|------|----------|------|
| JDK | 21+ | Java 后端开发 |
| Maven | 3.9+ | 项目构建 |
| MySQL | 8.0+ | 数据库 |
| Redis | 7+ | 缓存 |
| Python | 3.11+ | 翻译微服务 |
| Docker & Compose | 最新版 | 一键部署（推荐） |

### 快速启动

```bash
# 1. 克隆项目
git clone https://github.com/your-org/novelTranslator.git
cd novelTranslator

# 2. 使用 Docker Compose 启动所有服务
docker compose up -d

# 3. 验证服务健康状态
curl http://localhost:7341/health
```

### 本地开发模式

```bash
# 仅启动 MySQL 和 Redis
docker compose up -d mysql redis

# 构建并启动 Java 后端
mvn clean package -DskipTests
java -jar target/novelTranslator-0.0.1-SNAPSHOT.jar

# 启动翻译微服务（可选）
pip install fastapi uvicorn openai
python services/translate-engine/translate_server.py
```

---

## 项目结构

```
novelTranslator/
├── src/main/java/com/yumu/noveltranslator/
│   ├── controller/     # REST API 控制器层
│   ├── service/        # 业务逻辑层
│   ├── mapper/         # MyBatis-Plus 数据访问层
│   ├── entity/         # 数据库实体类
│   ├── dto/            # 数据传输对象
│   ├── config/         # Spring 配置类
│   ├── security/       # Spring Security + JWT
│   ├── enums/          # 枚举定义
│   └── util/           # 工具类
├── src/main/resources/
│   ├── application.yaml  # 主配置文件
│   ├── schema.sql        # 数据库初始化脚本
│   └── templates/        # Thymeleaf 模板
├── extension/          # Chrome 扩展
├── frontend/           # Web 管理界面
├── services/           # Python 微服务
└── nginx/              # Nginx 网关配置
```

### 分层架构说明

| 层级 | 包路径 | 职责 |
|------|--------|------|
| Controller | `controller` | 接收 HTTP 请求、参数校验、返回响应 |
| Service | `service` | 核心业务逻辑、事务管理、缓存协调 |
| Mapper | `mapper` | 数据库 CRUD 操作（MyBatis-Plus） |
| Entity | `entity` | 数据库表映射对象 |
| DTO | `dto` | API 请求/响应数据传输对象 |

---

## 编码规范

### 命名规范

- **类名**: PascalCase，如 `TranslationService`
- **方法名**: camelCase，如 `translateWebpage`
- **常量**: UPPER_SNAKE_CASE，如 `MAX_RETRY_COUNT`
- **包名**: 全小写，如 `com.yumu.noveltranslator.service`
- **数据库表**: snake_case，如 `translation_cache`
- **环境变量**: UPPER_SNAKE_CASE，如 `MYSQL_HOST`

### 注释规范

- 所有类必须有 Javadoc 注释，说明类的职责
- 公共方法必须有 Javadoc，说明参数、返回值、异常
- 复杂业务逻辑需有行内注释解释 "为什么"（而非 "是什么"）
- Controller 层的接口使用 `@Operation` 或注释说明接口用途

### 代码风格

- 使用 4 空格缩进（不使用 Tab）
- 每行不超过 120 字符
- 使用 Lombok 简化 getter/setter/构造器（`@Data`, `@Builder`, `@Slf4j`）
- 统一使用 UTF-8 编码
- 异常处理：业务异常使用自定义 `ErrorCodeEnum`，不允许吞掉异常

### 依赖注入

- 优先使用构造函数注入（Lombok `@RequiredArgsConstructor`）
- 避免使用 `@Autowired` 字段注入

### 事务管理

- Service 层写操作使用 `@Transactional`
- 只读查询使用 `@Transactional(readOnly = true)`

### API 设计规范

- RESTful 风格，使用名词复数形式
- 统一响应格式使用 `Result<T>` 包装
- HTTP 状态码语义化：200 成功、400 参数错误、401 未认证、403 无权限、500 服务端错误

---

## Git 工作流

### 分支策略

采用 Git Flow 简化模型：

```
main (生产)          ───────────────────────────────
                        ↑ merge         ↑ merge
feature/xxx  ───── branch ── develop ── merge ────
```

- `main`: 生产分支，只能通过 Pull Request 合并
- `feature/<name>`: 功能分支，从 `main` 创建
- `fix/<name>`: 修复分支，从 `main` 创建

### 开发流程

```bash
# 1. 从 main 创建功能分支
git checkout main
git pull origin main
git checkout -b feature/glossary-pagination

# 2. 开发并提交
git add <files>
git commit -m "feat: add glossary pagination support"

# 3. 推送并创建 Pull Request
git push origin feature/glossary-pagination
```

---

## 提交信息规范

采用 [Conventional Commits](https://www.conventionalcommits.org/) 规范：

```
<type>(<scope>): <description>

[optional body]

[optional footer(s)]
```

### Type 类型

| Type | 说明 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat: add document translation` |
| `fix` | Bug 修复 | `fix: resolve SSE streaming memory leak` |
| `docs` | 文档变更 | `docs: update API documentation` |
| `style` | 代码格式（不影响功能） | `style: format code with consistent indentation` |
| `refactor` | 重构 | `refactor: extract cache logic to separate service` |
| `test` | 测试相关 | `test: add unit tests for TranslationService` |
| `chore` | 构建/工具变更 | `chore: update Maven dependencies` |
| `perf` | 性能优化 | `perf: optimize Redis connection pool` |
| `ci` | CI/CD 配置变更 | `ci: add GitHub Actions workflow` |

### 示例

```
feat(user): add user preference management API

Add GET/PUT endpoints for user preferences including:
- Default translation engine selection
- Target language preference
- Reading mode settings

Closes #42
```

---

## Pull Request 流程

1. **Fork** 本项目到你的 GitHub 账号
2. **创建功能分支** `git checkout -b feature/your-feature`
3. **提交变更** 遵循 Conventional Commits 规范
4. **确保构建通过** `mvn clean package -DskipTests`
5. **编写测试** 新功能需包含单元测试
6. **推送分支** `git push origin feature/your-feature`
7. **创建 PR** 到主仓库的 `main` 分支
8. **等待 Review** 至少一位维护者审核通过后合并

### PR 要求

- 标题使用 Conventional Commits 格式
- 描述中说明：
  - 本次变更的目的
  - 影响的范围
  - 测试方式
  - 关联的 Issue（如有）

---

## 测试要求

### 单元测试

- Service 层核心业务逻辑需有单元测试
- 使用 `@SpringBootTest` 集成测试或 `@ExtendWith(MockitoExtension.class)` Mock 测试
- 测试覆盖率目标：核心 Service 层 > 70%

### 运行测试

```bash
# 运行所有测试
mvn test

# 运行单个测试类
mvn test -Dtest=TranslationServiceTest

# 运行单个测试方法
mvn test -Dtest=TranslationServiceTest#testTranslateWebpage

# 生成覆盖率报告
mvn test jacoco:report
```

---

## 常见问题

**Q: 数据库表结构变更如何处理？**

A: 修改 `src/main/resources/schema.sql` 文件，并在 PR 描述中说明变更内容。

**Q: 如何调试 Docker 中的服务？**

A: 使用 `docker compose logs -f backend` 查看后端日志，或 `docker compose exec backend bash` 进入容器。

**Q: 本地开发时如何切换翻译引擎？**

A: 通过修改 `application.yaml` 中的 `translation.openai.base-url` 环境变量，或设置 `OPENAI_BASE_URL` 环境变量。
