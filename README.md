# NovelTranslator - Bilingual Novel Translation System

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Python](https://img.shields.io/badge/Python-3.x-blue.svg)](https://www.python.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg)](https://docs.docker.com/compose/)

A full-stack bilingual novel translation system featuring a Chrome Extension, Spring Boot backend, LLM-powered translation microservice, and Nginx gateway. Supports OpenAI-compatible APIs, 3-tier caching, SSE streaming translation, glossary management, and user authentication with JWT.

[中文文档](README.zh.md)

## Architecture

```
┌──────────────┐    ┌──────────────┐    ┌───────────────┐    ┌──────────────────┐    ┌─────────────────┐
│ Chrome       │───▶│   Nginx      │───▶│ Spring Boot   │───▶│ Python / MTran   │───▶│ OpenAI Compatible│
│ Extension    │    │  (Port 7341) │    │ (Port 8080)   │    │ Engine (8000/8989│    │ API / Claude     │
└──────────────┘    └──────────────┘    └───────────────┘    └──────────────────┘    │ / Ollama        │
       │                    │                     │                                    └─────────────────┘
       │             Static/CORS             ┌─────┴──────┐
       │             Reverse Proxy           │ MySQL 8.0  │
       │                                     │ Redis 7    │
       │                                     │ Caffeine   │
       └─────────────────────────────────────┴────────────┘
                        WebSocket / REST API
```

### Component Overview

| Component | Technology | Port | Responsibility |
|-----------|------------|------|----------------|
| Chrome Extension | Manifest V3, Vanilla JS | - | DOM analysis, translation display |
| Nginx Gateway | Nginx 1.28 | 7341 | Reverse proxy, static file serving, CORS |
| Spring Boot Backend | Java 21, Undertow | 8080 | REST API, business logic, auth, caching |
| Translation Engine | Python FastAPI / MTranServer | 8000 / 8989 | LLM translation, fallback chain |
| MySQL | MySQL 8.0 | 3306 | Persistent storage (users, glossaries, history) |
| Redis | Redis 7 | 6379 | Distributed cache (L2), session management |

## Key Features

### 3-Tier Caching Architecture
- **L1 Caffeine** (in-memory, 10 min TTL) -> **L2 Redis** (distributed, 30 min) -> **L3 MySQL** (persistent, 24h)
- **Cache penetration** prevention via null-value placeholder with short TTL
- **Cache breakdown** protection via per-key synchronized locking
- **Cache avalanche** prevention via randomized TTL jitter

### LLM-Powered Translation
- **OpenAI-compatible API**: Works with OpenAI GPT, Claude (via compatibility layer), local Ollama, DeepSeek, and any compatible endpoint
- **Novel translation system prompt**: 6 translation principles ensuring literary translation quality
- **Dual-engine support**: LLM (DeepSeek/OpenAI) + lightweight (MTranServer) with intelligent routing
- **Smart engine fallback**: Health checks, circuit breaker cooldown, multi-level degradation
- **Rate limiting**: Sliding window algorithm, configurable per-user-tier limits

### Backend Engineering
- **SSE streaming translation**: Progressive rendering in the browser for smooth UX with long texts
- **Virtual threads concurrency**: Java 21 Virtual Threads + Semaphore-based per-user rate limiting
- **Undertow server**: High-performance non-blocking web server replacing Tomcat
- **Dual-engine probability routing**: Intelligent traffic routing based on historical success rates
- **Document translation**: Async task-based large document translation with progress tracking

### Browser Extension
- **Three translation modes**: Full-page translation, reader mode, and selection-based translation
- **DOM-aware translation**: Preserves original layout while translating text content
- **Readability integration**: Extracts article content for optimized reading mode translation
- **Smart translation button**: Appears contextually on text selection

### Security
- **JWT authentication**: Spring Security + auth0-jwt, secrets injected via environment variables only
- **BCrypt password hashing**: All user passwords hashed before storage
- **Email verification**: Double verification for registration and password reset
- **Tiered rate limiting**: Anonymous / Free / Pro users with differentiated concurrency limits

### User Features
- **Glossary management**: Create, edit, and delete custom translation terms
- **User preferences**: Configurable translation settings per user
- **Translation history**: Track past translations with pagination
- **User statistics**: Personal usage dashboard with quota tracking
- **Change password**: Secure password update with current password verification

## Project Structure

```
novelTranslator/
├── extension/                    # Chrome Extension (Manifest V3)
│   ├── manifest.json
│   └── src/
│       ├── background/           #   Service worker (message routing, API calls)
│       ├── content/              #   Content scripts (page, reader, selection translation)
│       ├── popup/                #   Extension popup UI
│       ├── options/              #   Settings pages
│       └── lib/                  #   Third-party libs (Readability, DOMPurify)
├── frontend/                     # Web Dashboard (Vanilla HTML/CSS/JS)
│   ├── pages/                    #   Dashboard pages
│   ├── js/                       #   Client-side JavaScript
│   ├── styles/                   #   CSS stylesheets
│   ├── utils/                    #   Utility functions
│   └── config.js                 #   API endpoint configuration
├── src/main/java/                # Spring Boot Backend (Java 21)
│   └── com/yumu/noveltranslator/
│       ├── controller/           #   REST API Controllers
│       ├── service/              #   Business Logic Layer
│       ├── mapper/               #   MyBatis-Plus Data Access
│       ├── entity/               #   Domain Entities
│       ├── dto/                  #   Data Transfer Objects
│       ├── config/               #   Configuration (Redis, Security, Threads)
│       ├── security/             #   Spring Security + JWT
│       ├── enums/                #   Enumerations (errors, engines, phases)
│       └── util/                 #   Utility Classes
├── services/translate-engine/    # Python Translation Microservice
│   └── translate_server.py       #   FastAPI + OpenAI SDK + Fallback Chain
├── nginx/                        # Nginx Gateway Configuration
│   └── nginx.conf
├── docker-compose.yml            # One-Command Docker Deployment
└── pom.xml                       # Maven Build Configuration
```

## Quick Start

### Option 1: Docker Compose (Recommended)

```bash
# Clone the repository
git clone https://github.com/your-org/novelTranslator.git
cd novelTranslator

# Start all services (MySQL, Redis, MTranServer, LLM Engine, Backend, Nginx)
docker compose up -d
```

Access after startup:
- **Web Dashboard**: http://localhost:7341
- **Backend API**: http://localhost:7341/v1
- **Health Check**: http://localhost:7341/health

### Option 2: Manual Setup

#### Prerequisites
- Java 21 (Temurin/OpenJDK)
- Maven 3.9+
- MySQL 8.0
- Redis 7
- Python 3.11+ (for translation microservice)

#### 1. Start MySQL and Redis

```bash
docker run -d --name mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=novel_translator \
  mysql:8.0

docker run -d --name redis -p 6379:6379 redis:7
```

#### 2. Build and Start the Backend

```bash
mvn clean package -DskipTests

export JWT_SECRET="your-secret-key-here"
export MYSQL_HOST=localhost
export REDIS_HOST=localhost
export TRANSLATION_OPENAI_API_KEY="sk-xxx"

java -jar target/novelTranslator-0.0.1-SNAPSHOT.jar
# Backend runs at http://localhost:8080
```

#### 3. Start the Translation Engine

```bash
pip install fastapi uvicorn openai

export OPENAI_API_KEY="sk-xxx"
export OPENAI_BASE_URL="https://api.deepseek.com/v1"
export OPENAI_MODEL="deepseek-chat"

python services/translate-engine/translate_server.py
# Engine runs at http://localhost:8000
```

#### 4. Install the Chrome Extension

1. Open Chrome and navigate to `chrome://extensions/`
2. Enable "Developer mode"
3. Click "Load unpacked" and select the `extension/` directory

## Screenshots

### Web Dashboard
![Web Dashboard](docs/screenshots/dashboard.png)
*The web dashboard provides user management, glossary configuration, and translation statistics.*

### Chrome Extension - Full Page Translation
![Full Page Translation](docs/screenshots/webpage-translation.png)
*The Chrome extension translates entire web pages while preserving the original DOM layout.*

### Reader Mode
![Reader Mode](docs/screenshots/reader-mode.png)
*Reader mode extracts article content and provides a clean reading experience with translated text.*

### Glossary Management
![Glossary Management](docs/screenshots/glossary.png)
*Create and manage custom translation terms for consistent novel terminology.*

## API Documentation

Detailed API documentation is available in the following files:

- [API_ENDPOINTS.md](API_ENDPOINTS.md) - Three translation modes with request/response examples
- [API_DOCUMENTATION.md](API_DOCUMENTATION.md) - Complete backend API reference including user module

### Quick API Reference

#### Translation Endpoints

| Endpoint | Method | Description | Auth |
|----------|--------|-------------|------|
| `/v1/translate/webpage` | POST | Batch webpage translation (SSE streaming) | No |
| `/v1/translate/reader` | POST | Reader mode article translation | No |
| `/v1/translate/selection` | POST | Selected text translation | No |
| `/v1/translate/text` | POST | Plain text translation | No |
| `/v1/translate/document` | POST | Async document translation | Yes |
| `/v1/translate/task/{id}` | GET | Check translation task status | Yes |

#### User Endpoints

| Endpoint | Method | Description | Auth |
|----------|--------|-------------|------|
| `/user/register` | POST | User registration | No |
| `/user/login` | POST | User login | No |
| `/user/refresh` | POST | Refresh JWT token | No |
| `/user/profile` | GET | Get user profile | Yes |
| `/user/glossaries` | GET/POST | List/Create glossary items | Yes |
| `/user/preferences` | GET/PUT | Get/Update user preferences | Yes |
| `/user/stats` | GET | User statistics | Yes |
| `/user/quota` | GET | User quota info | Yes |

## Configuration

### Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `JWT_SECRET` | JWT signing secret key | None | Yes |
| `JWT_EXPIRATION` | Token TTL in milliseconds | 2592000000 (30d) | No |
| `MYSQL_HOST` | MySQL host | localhost | No |
| `MYSQL_PORT` | MySQL port | 3306 | No |
| `MYSQL_DB` | MySQL database name | novel_translator | No |
| `MYSQL_USER` | MySQL username | root | No |
| `MYSQL_PASSWORD` | MySQL password | None | No |
| `REDIS_HOST` | Redis host | localhost | No |
| `REDIS_PORT` | Redis port | 6379 | No |
| `REDIS_PASSWORD` | Redis password | None | No |
| `TRANSLATION_OPENAI_API_KEY` | OpenAI-compatible API key | None | Yes |
| `OPENAI_BASE_URL` | API base URL (microservice) | https://api.openai.com/v1 | No |
| `OPENAI_MODEL` | Translation model (microservice) | gpt-4o-mini | No |
| `MAIL_USERNAME` | Email address (for verification) | None | Email feature |
| `MAIL_PASSWORD` | Email SMTP auth code | None | Email feature |
| `MTRAN_HOST` | MTranServer host | localhost | No |
| `MTRAN_PORT` | MTranServer port | 8989 | No |

## Tech Stack

| Layer | Technology |
|-------|------------|
| **Backend** | Java 21, Spring Boot 3.2.0, Undertow, Spring Security, WebFlux |
| **Database** | MySQL 8.0, MyBatis-Plus 3.5.5 |
| **Cache** | Caffeine (L1), Redis 7 / Lettuce (L2) |
| **Microservice** | Python 3.11, FastAPI, OpenAI SDK, MTranServer |
| **Frontend** | Chrome Extension (Manifest V3), Vanilla JS, CSS3, Thymeleaf |
| **Gateway** | Nginx 1.28 |
| **Build & Deploy** | Maven, Docker Compose |
| **Libraries** | jsoup, fastjson2, Lombok, auth0-jwt, Apache Commons Text |

## Development

See [CONTRIBUTING.md](CONTRIBUTING.md) for development guidelines and contribution process.
See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed architecture documentation.
See [CODE_STYLE.md](CODE_STYLE.md) for coding standards and conventions.

### Building from Source

```bash
mvn clean package -DskipTests
```

### Running Tests

```bash
mvn test
```

## License

This project is licensed under the [MIT License](LICENSE).
