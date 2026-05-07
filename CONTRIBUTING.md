# Contributing Guide

Thank you for your interest in NovelTrans!

**Other project documents:**

- [`SETUP.md`](SETUP.md) — Deployment & development environment setup
- [`ARCHITECTURE.md`](ARCHITECTURE.md) — System architecture and data flow
- [`README.md`](README.md) — Project overview

## Git Workflow

1. Create a branch from `main`: `git checkout -b feature/your-feature`
2. Commit changes with conventional commit messages (see below)
3. Push and open a Pull Request against `main`

## Commit Message Conventions

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>
```

Common types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

Examples:
- `feat(api): add rate limiting for external endpoints`
- `fix(cache): correct version stamp in delayed double-delete`
- `docs: update setup guide for new Stripe SDK version`

## Pull Request Process

- **Small, focused changes**: One PR per feature or bug fix
- **Describe the why**: Include context, not just what changed
- **Reference issues**: Link to related issues with `Fixes #N`
- **Pass CI**: All tests and lint checks must pass
- **Request review**: Assign to at least one reviewer

## Code Style

Code formatting is enforced automatically by [Spotless](https://github.com/diffplug/spotless) (Google Java Format for Java, EditorConfig for other files).

```bash
# Format Java code
mvn spotless:apply

# Check formatting (runs in CI)
mvn spotless:check
```

## Testing Requirements

- **Java**: JUnit 5 + Mockito. Target: 80%+ instruction coverage on non-excluded packages.
  ```bash
  mvn verify
  ```
- **Frontend**: Vitest for unit tests, Playwright for E2E.
  ```bash
  cd web-app && npm test
  ```
- **Load testing**: k6 scripts in `load-test/`.
  ```bash
  k6 run load-test/scenarios/login.js
  ```
