# Setup & Deployment Guide

> This document describes the deployment process and environment configuration for NovelTrans.

## Prerequisites

| Dependency | Version | Purpose |
|------------|---------|---------|
| Docker | 24+ | Container runtime |
| Docker Compose | 2.20+ | Container orchestration |
| JDK | 21+ | Local development (optional; Docker includes Maven) |
| Maven | 3.9+ | Local build (optional) |
| Python | 3.11+ | Local translation microservice (optional) |
| MySQL | 8.0+ | Local development (included in Docker) |
| Redis | 7+ | Local development (included in Docker) |

---

## Option 1: Docker Compose One-Command Deploy (Recommended)

### 1. Clone the Repository

```bash
git clone <your-repo-url>
cd novelTranslator
```

### 2. Configure Environment Variables

```bash
cp .env.example .env
```

Edit `.env` and fill in the required configuration:

```bash
# ===== Database =====
MYSQL_USER=root
MYSQL_PASSWORD=123456
MYSQL_DB=novel_translator

# ===== Redis =====
REDIS_PASSWORD=
REDIS_DB=0

# ===== JWT =====
JWT_SECRET=<your-random-secret-string>

# ===== Email Service =====
MAIL_USERNAME=your_email@example.com
MAIL_PASSWORD=your_smtp_authorization_code

# ===== LLM Translation Engine =====
LLM_API_KEY=<your-openai-or-compatible-api-key>
LLM_BASE_URL=https://api.openai.com/v1
LLM_MODEL=gpt-4o

# ===== MTran Lightweight Engine =====
MTRAN_API_KEY=<your-mtran-api-key>
MTRAN_PORT=8989

# ===== Embedding Vector Model =====
# Options: ollama (local) or openai (cloud)
EMBEDDING_PROVIDER=ollama
EMBEDDING_OPENAI_API_KEY=
EMBEDDING_OPENAI_BASE_URL=

# ===== Stripe Payments =====
STRIPE_SECRET_KEY=sk_live_xxxx or sk_test_xxxx
STRIPE_WEBHOOK_SECRET=whsec_xxxx
STRIPE_SUCCESS_URL=http://localhost:7341/subscription/success
STRIPE_CANCEL_URL=http://localhost:7341/subscription/cancel
STRIPE_PRO_MONTHLY_PRICE_ID=price_xxxx
STRIPE_PRO_YEARLY_PRICE_ID=price_xxxx
STRIPE_MAX_MONTHLY_PRICE_ID=price_xxxx
STRIPE_MAX_YEARLY_PRICE_ID=price_xxxx

# ===== Inter-Service Authentication =====
TRANSLATE_SERVICE_API_KEY=<your-internal-service-api-key>
```

### 3. Build the Frontend

```bash
cd web-app
npm install
npm run build
cd ..
```

### 4. Start All Services

```bash
docker compose up -d
```

Initial startup may take 5-10 minutes (Maven downloading dependencies, Ollama pulling the model).

### 5. Verify Services

```bash
# Check all container status
docker compose ps

# Access health check
curl http://localhost:7341/health

# Open the web app
open http://localhost:7341
```

### 6. View Logs

```bash
# All services
docker compose logs -f

# Individual services
docker compose logs -f backend
docker compose logs -f llm-engine
docker compose logs -f nginx
```

---

## Option 2: Local Development Mode

### 1. Start Infrastructure

```bash
docker compose up -d mysql redis
```

### 2. Initialize the Database

```bash
mysql -h 127.0.0.1 -P 3307 -u root -p < src/main/resources/schema.sql
```

### 3. Build and Start the Backend

```bash
mvn clean package -DskipTests
java -jar target/novelTranslator-0.0.1-SNAPSHOT.jar
```

Or run `NovelTranslatorApplication.java` directly from your IDE.

### 4. Start the Translation Microservice (Optional)

```bash
cd services/translate-engine
pip install -r requirements.txt
python translate_server.py
```

### 5. Start the Frontend Dev Server

```bash
cd web-app
npm install
npm run dev
```

---

## Service Ports

| Service | Port | Protocol | Purpose |
|---------|------|----------|---------|
| Nginx | 7341 | HTTP | Gateway entry point |
| Backend | 8080 | HTTP | Spring Boot |
| MySQL | 3307→3306 | TCP | Database |
| Redis | 6379 | TCP | Cache + vector store |
| MTranServer | 8989 | HTTP | Lightweight translation service |
| LLM Engine | 8000 | HTTP | Python FastAPI |
| Ollama | 11434 | HTTP | Embedding model |

---

## Container Dependencies

```
Nginx (7341)
  └── Backend (8080)
        ├── MySQL (3306)      ← health check
        ├── Redis (6379)      ← health check
        ├── MTranServer (8989) ← startup
        ├── Ollama (11434)    ← startup
        └── LLM Engine (8000)  ← startup
```

---

## Production Checklist

### Security Hardening

- [ ] Replace all default passwords and secrets
- [ ] Enable HTTPS (Nginx TLS configuration)
- [ ] Remove development proxy settings (HTTP_PROXY, etc.)
- [ ] Enforce Stripe Webhook signature verification
- [ ] Enable MySQL password strength policy
- [ ] Set Redis authentication password

### Performance Tuning

- [ ] Adjust MySQL connection pool size
- [ ] Configure Redis memory limit and eviction policy
- [ ] Tune Spring Boot Undertow thread pool
- [ ] Enable CDN for frontend static assets
- [ ] Configure log rotation and size limits

### Monitoring

- [ ] Integrate APM monitoring (e.g., New Relic, Datadog)
- [ ] Enable Spring Boot Actuator monitoring endpoints
- [ ] Set up translation failure rate alerts
- [ ] Monitor LLM API call costs

---

## Troubleshooting

### Q: Maven dependency downloads are slow?

Use a proxy or configure a mirror. Edit `maven-settings.xml` to add mirror repositories.

### Q: Ollama model download failed?

Verify network connectivity, or switch to OpenAI cloud Embedding:

```bash
EMBEDDING_PROVIDER=openai
EMBEDDING_OPENAI_API_KEY=your-key
EMBEDDING_OPENAI_BASE_URL=https://api.openai.com/v1
```

### Q: Backend throws database connection errors on startup?

Confirm the MySQL container is healthy:

```bash
docker compose ps mysql
docker compose logs mysql
```

### Q: Translation returns empty or errors?

Check the LLM Engine logs:

```bash
docker compose logs llm-engine
```

Verify `LLM_API_KEY` and `LLM_BASE_URL` are correctly configured.

### Q: How do I test Stripe Webhooks locally?

Use the Stripe CLI to forward events:

```bash
stripe listen --forward-to localhost:7341/webhook/stripe
```

---

**Last updated**: 2026-04-29
