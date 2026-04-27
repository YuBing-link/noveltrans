# Contributing Guide

Thank you for your interest in NovelTrans! This document explains how to set up the development environment, contribute code, and follow project conventions.

## Table of Contents

- [Development Environment Setup](#development-environment-setup)
- [Project Structure](#project-structure)
- [Coding Standards](#coding-standards)
- [Git Workflow](#git-workflow)
- [Commit Message Conventions](#commit-message-conventions)
- [Pull Request Process](#pull-request-process)
- [Testing Requirements](#testing-requirements)

---

## Development Environment Setup

### Required Tools

| Tool | Version | Purpose |
|------|---------|---------|
| JDK | 21+ | Java backend development |
| Maven | 3.9+ | Project build |
| MySQL | 8.0+ | Database |
| Redis | 7+ | Cache + Vector store |
| Python | 3.11+ | Translation microservice |
| Docker & Compose | Latest | One-command deployment (recommended) |

### Quick Start

```bash
# 1. Clone the project
git clone https://github.com/your-org/novelTranslator.git
cd novelTranslator

# 2. Start all services with Docker Compose
docker compose up -d

# 3. Verify service health
curl http://localhost:7341/health
```

### Local Development Mode

```bash
# Start only MySQL and Redis
docker compose up -d mysql redis

# Build and start Java backend
mvn clean package -DskipTests
java -jar target/novelTranslator-0.0.1-SNAPSHOT.jar

# Start translation microservice (optional)
pip install fastapi uvicorn openai
python services/translate-engine/translate_server.py
```

---

## Project Structure

```
novelTranslator/
├── src/main/java/com/yumu/noveltranslator/
│   ├── controller/     # REST API controllers (web, plugin, external, shared, collab)
│   ├── service/        # Business logic layer
│   │   └── pipeline/   # TranslationPipeline component
│   ├── mapper/         # MyBatis-Plus data access layer
│   ├── entity/         # Database entity classes
│   ├── dto/            # Data transfer objects
│   ├── config/         # Spring configuration classes
│   ├── security/       # Spring Security + JWT
│   ├── enums/          # Enum definitions
│   └── util/           # Utility classes
├── src/main/resources/
│   ├── application.yaml  # Main configuration file
│   ├── sql/              # Database schema and migration scripts
│   │   ├── schema.sql    # Database initialization script
│   │   ├── ai_glossary.sql
│   │   ├── chapter_entity_map.sql
│   │   └── migration-ai-glossary.sql
│   └── templates/        # Thymeleaf templates
├── web-app/            # Web dashboard (React + TypeScript + Vite)
├── extension/          # Chrome browser extension
├── services/           # Python microservice
│   └── translate-engine/
└── nginx/              # Nginx gateway configuration
```

### Layered Architecture

| Layer | Package Path | Responsibility |
|-------|--------------|----------------|
| Controller | `controller` | Receive HTTP requests, validate parameters, return responses |
| Service | `service` | Core business logic, transaction management, cache coordination |
| Mapper | `mapper` | Database CRUD operations (MyBatis-Plus) |
| Entity | `entity` | Database table mapping objects |
| DTO | `dto` | API request/response data transport objects |

---

## Coding Standards

### Naming Conventions

- **Class names**: PascalCase, e.g., `TranslationService`
- **Method names**: camelCase, e.g., `translateWebpage`
- **Constants**: UPPER_SNAKE_CASE, e.g., `MAX_RETRY_COUNT`
- **Package names**: All lowercase, e.g., `com.yumu.noveltranslator.service`
- **Database tables**: snake_case, e.g., `translation_cache`
- **Environment variables**: UPPER_SNAKE_CASE, e.g., `MYSQL_HOST`

### Comment Standards

- All classes must have Javadoc describing their responsibility
- Public methods must have Javadoc documenting parameters, return values, and exceptions
- Complex business logic should have inline comments explaining "why" (not "what")
- Controller endpoints should use `@Operation` or comments to describe the API purpose

### Code Style

- 4-space indentation (no tabs)
- Maximum 120 characters per line
- Use Lombok to simplify boilerplate (`@Data`, `@Builder`, `@Slf4j`)
- UTF-8 encoding throughout
- Error handling: business exceptions use `ErrorCodeEnum`, never swallow exceptions

### Dependency Injection

- Prefer constructor injection (Lombok `@RequiredArgsConstructor`)
- Avoid `@Autowired` field injection

### Transaction Management

- Service-layer write operations use `@Transactional`
- Read-only queries use `@Transactional(readOnly = true)`

### API Design

- RESTful style, using noun plural forms
- Unified response format with `Result<T>` envelope
- Semantic HTTP status codes: 200 success, 400 bad request, 401 unauthorized, 403 forbidden, 500 server error

---

## Git Workflow

### Branch Strategy

Uses a simplified Git Flow model:

```
main (production)       ───────────────────────────────
                           ↑ merge         ↑ merge
feature/xxx  ───── branch ── develop ── merge ────
```

- `main`: Production branch, only merge via Pull Request
- `feature/<name>`: Feature branch, created from `main`
- `fix/<name>`: Fix branch, created from `main`

### Development Process

```bash
# 1. Create feature branch from main
git checkout main
git pull origin main
git checkout -b feature/glossary-pagination

# 2. Develop and commit
git add <files>
git commit -m "feat: add glossary pagination support"

# 3. Push and create Pull Request
git push origin feature/glossary-pagination
```

---

## Commit Message Conventions

Follows [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>

[optional body]

[optional footer(s)]
```

### Type Values

| Type | Description | Example |
|------|-------------|---------|
| `feat` | New feature | `feat: add document translation` |
| `fix` | Bug fix | `fix: resolve SSE streaming memory leak` |
| `docs` | Documentation changes | `docs: update API documentation` |
| `style` | Code formatting (no functional change) | `style: format code with consistent indentation` |
| `refactor` | Refactoring | `refactor: extract cache logic to separate service` |
| `test` | Test-related | `test: add unit tests for TranslationService` |
| `chore` | Build/tool changes | `chore: update Maven dependencies` |
| `perf` | Performance optimization | `perf: optimize Redis connection pool` |
| `ci` | CI/CD configuration | `ci: add GitHub Actions workflow` |

### Example

```
feat(user): add user preference management API

Add GET/PUT endpoints for user preferences including:
- Default translation engine selection
- Target language preference
- Reading mode settings

Closes #42
```

---

## Pull Request Process

1. **Fork** this project to your GitHub account
2. **Create a feature branch** `git checkout -b feature/your-feature`
3. **Commit changes** following Conventional Commits
4. **Ensure the build passes** `mvn clean package -DskipTests`
5. **Write tests** for new features
6. **Push your branch** `git push origin feature/your-feature`
7. **Create a PR** to the `main` branch
8. **Wait for review** — at least one maintainer must approve before merging

### PR Requirements

- Title in Conventional Commits format
- Description should include:
  - Purpose of the change
  - Affected scope
  - Testing approach
  - Related issue (if any)

---

## Testing Requirements

### Unit Tests

- Service layer core business logic requires unit tests
- Use `@SpringBootTest` for integration tests or `@ExtendWith(MockitoExtension.class)` for mock-based tests
- Target coverage: core Service layer > 70%

### Running Tests

```bash
# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=TranslationServiceTest

# Run a single test method
mvn test -Dtest=TranslationServiceTest#testTranslateWebpage

# Generate coverage report
mvn test jacoco:report
```

---

## Frequently Asked Questions

**Q: How should I handle database schema changes?**

A: Modify the `src/main/resources/sql/schema.sql` file and describe the schema changes in your PR description.

**Q: How do I debug services running in Docker?**

A: Use `docker compose logs -f backend` to view backend logs, or `docker compose exec backend bash` to enter the container.

**Q: How do I switch translation engines during local development?**

A: Modify the `translation.openai.base-url` in `application.yaml` or set the `OPENAI_BASE_URL` environment variable.

---

**Last updated**: 2026-04-27
