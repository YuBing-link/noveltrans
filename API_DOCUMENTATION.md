# Backend API Reference

> This document describes all REST API endpoints provided by the NovelTrans backend server.

---

## Table of Contents

- [General](#general)
- [Translation API](#translation-api)
  - [Plugin Translation (Browser Extension)](#plugin-translation-browser-extension)
  - [Shared Translation (Web + Plugin)](#shared-translation-web--plugin)
  - [External API (API Key Access)](#external-api-api-key-access)
  - [RAG Translation Memory](#rag-translation-memory)
- [User API](#user-api)
- [Document Management](#document-management)
- [Glossary Management](#glossary-management)
- [Collaboration Projects](#collaboration-projects)
- [Platform Statistics](#platform-statistics)
- [Page Routes](#page-routes)
- [Error Codes](#error-codes)

---

## General

### Base Information

| Item | Value |
|------|-------|
| Base URL | `http://localhost:7341` |
| API Version | `v1` |
| Data Format | JSON |
| Character Encoding | UTF-8 |

### Common Response Format

All endpoints return a consistent envelope:

```json
{
  "success": true,
  "data": {},
  "code": "200",
  "message": null,
  "token": null
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Whether the request succeeded |
| data | T | Response data, type varies by endpoint |
| code | string | Status code, `"200"` for success |
| message | string | Error message, null on success |
| token | string | JWT Token (login/register endpoints only) |

### Authentication

Endpoints requiring authentication accept one of:

- **JWT Bearer Token**: `Authorization: Bearer <token>`
- **API Key**: `Authorization: Bearer nt_sk_xxxx`

---

## Translation API

### Plugin Translation (Browser Extension)

**Base path**: `/v1/translate`
**Controller**: `PluginTranslateController`

These endpoints serve the browser extension. Authenticated users receive higher quotas.

#### 1. Selection Translation

`POST /v1/translate/selection`

**Auth**: Not required

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| text | string | Yes | - | Selected text to translate |
| sourceLang | string | No | auto | Source language code |
| targetLang | string | No | zh | Target language code |
| engine | string | No | - | Translation engine: google, deepl, baidu, openai, mymemory, libre |
| context | string | No | null | Surrounding context for improved accuracy |

**Success Response**:
```json
{
  "success": true,
  "data": {
    "success": true,
    "engine": "google",
    "translation": "Translation result"
  },
  "code": "200",
  "message": null
}
```

---

#### 2. Reader Translation

`POST /v1/translate/reader`

**Auth**: Not required

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| content | string | Yes | - | Article content in HTML format |
| sourceLang | string | No | auto | Source language code |
| targetLang | string | Yes | - | Target language code |
| engine | string | No | - | Translation engine |

**Success Response**:
```json
{
  "success": true,
  "data": {
    "success": true,
    "engine": "google",
    "translatedContent": "<h1>Translated Title</h1><p>Translated content...</p>"
  },
  "code": "200",
  "message": null
}
```

---

#### 3. Full-Page Translation (SSE Streaming)

`POST /v1/translate/webpage`

**Auth**: Not required

**Content-Type**: `application/json`

**Response**: `text/event-stream` (SSE)

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| targetLang | string | Yes | - | Target language code |
| sourceLang | string | No | auto | Source language code |
| engine | string | No | - | Translation engine |
| textRegistry | array | Yes | - | Text mapping table |
| textRegistry[].id | string | Yes | - | Unique text node identifier |
| textRegistry[].original | string | Yes | - | Original text |
| textRegistry[].context | string | No | null | Context information |

**SSE Event Format**:

| Event | Format | Description |
|-------|--------|-------------|
| Translation block | `data: {"textId":"xxx","translation":"..."}` | Single text block result |
| Done marker | `data: [DONE]` | Translation complete |
| Error | `data: ERROR: description` | Error occurred |

---

#### 4. Text Streaming Translation

`POST /v1/translate/text/stream`

**Auth**: Not required

Same parameters as Selection Translation, returns results via SSE streaming.

---

#### 5. Premium Translation (Authenticated)

`POST /v1/translate/premium-selection` — Auth required. Higher quota selection translation.

`POST /v1/translate/premium-reader` — Auth required. Higher quota reader translation.

Parameters and responses match the non-premium equivalents.

---

### Shared Translation (Web + Plugin)

**Base path**: `/v1/translate`
**Controller**: `SharedTranslateController`

#### 6. Query Translation Task Status

`GET /v1/translate/task/{taskId}` — **Auth**: Not required

```json
{
  "success": true,
  "data": {
    "taskId": "task_123456",
    "type": "document",
    "status": "processing",
    "progress": 45,
    "sourceLang": "en",
    "targetLang": "zh",
    "createTime": "2026-02-24T10:00:00Z"
  },
  "code": "200"
}
```

#### 7. Cancel Translation Task

`DELETE /v1/translate/task/{taskId}` — **Auth**: Required

#### 8. Delete Translation History

`DELETE /v1/translate/history/{taskId}` — **Auth**: Required

#### 9. Get Translation Result

`GET /v1/translate/task/{taskId}/result` — **Auth**: Not required

#### 10. Download Translation Result

`GET /v1/translate/task/{taskId}/download` — **Auth**: Required (binary stream)

---

#### 11. Document Streaming Translation (File Upload)

`POST /v1/translate/document/stream`

**Content-Type**: `multipart/form-data`

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| file | file | Yes | - | Document file |
| sourceLang | string | No | auto | Source language code |
| targetLang | string | Yes | zh | Target language code |
| mode | string | No | fast | Translation mode: fast, expert, team |

**Response**: `text/event-stream`

---

#### 12. Document Streaming Translation (Pre-uploaded)

`POST /v1/translate/document/stream/{docId}` — **Auth**: Required

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| docId | Long | Yes | - | Document ID |
| targetLang | string | Yes | zh | Target language code |
| mode | string | No | fast | Translation mode |

**Response**: `text/event-stream`

---

### External API (API Key Access)

**Base path**: `/v1/external`
**Controller**: `ExternalTranslateController`
**Auth**: `Authorization: Bearer nt_sk_xxxx`

#### 13. Text Translation

`POST /v1/external/translate`

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| text | string | Yes | - | Text to translate |
| sourceLang | string | No | auto | Source language code |
| targetLang | string | Yes | - | Target language code |
| engine | string | No | google | Translation engine |

#### 14. Batch Text Translation

`POST /v1/external/batch`

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| texts | array | Yes | - | Text list (max 50) |
| sourceLang | string | No | auto | Source language code |
| targetLang | string | Yes | - | Target language code |
| engine | string | No | google | Translation engine |

#### 15. List Available Translation Engines

`GET /v1/external/models`

```json
{
  "success": true,
  "data": [
    { "id": "google", "name": "Google Translate", "type": "free" },
    { "id": "mymemory", "name": "MyMemory", "type": "free" },
    { "id": "libre", "name": "LibreTranslate", "type": "free" },
    { "id": "baidu", "name": "Baidu Translate", "type": "api_key" },
    { "id": "deepl", "name": "DeepL", "type": "api_key" },
    { "id": "openai", "name": "OpenAI", "type": "api_key" }
  ],
  "code": "200"
}
```

#### 16. Download Translation (External API)

`GET /v1/external/task/{taskId}/download` — Binary stream

---

### RAG Translation Memory

`POST /v1/translate/rag` — **Auth**: Not required

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| text | string | Yes | Text to query |
| targetLang | string | Yes | Target language code |
| engine | string | No | Translation engine |

---

## User API

**Base path**: `/user`
**Controller**: `WebUserController`

### 1. Send Registration Code

`POST /user/send-code` — Auth not required
```json
{ "email": "user@example.com" }
```

### 2. Send Password Reset Code

`POST /user/send-reset-code` — Auth not required
```json
{ "email": "user@example.com" }
```

### 3. User Login

`POST /user/login` — Auth not required

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| email | string | Yes | User email |
| password | string | Yes | User password |
| from | string | No | Login source: web, extension |

Returns `{ data: { user profile }, token: "JWT" }`.

### 4. User Registration

`POST /user/register` — Auth not required

```json
{
  "email": "user@example.com",
  "password": "password123",
  "code": "123456",
  "username": "username",
  "avatar": "https://..."
}
```

### 5. Get User Profile

`GET /user/profile` — Auth required

### 6. Update User Profile

`PUT /user/profile` — Auth required

```json
{
  "username": "NewUsername",
  "avatar": "https://..."
}
```

### 7. Change Password

`POST /user/change-password` — Auth required

### 8. Reset Password

`POST /user/reset-password` — Auth not required

### 9. Refresh Token

`POST /user/refresh-token` — Auth not required

### 10. Logout

`POST /user/logout` — Auth required

### 11. Get User Statistics

`GET /user/statistics` — Auth required

```json
{
  "success": true,
  "data": {
    "totalTranslations": 150,
    "textTranslations": 120,
    "documentTranslations": 30,
    "totalCharacters": 500000,
    "totalDocuments": 30,
    "weekTranslations": 25,
    "monthTranslations": 100
  }
}
```

### 12. Get User Quota

`GET /user/quota` — Auth required

```json
{
  "success": true,
  "data": {
    "userLevel": "FREE",
    "monthlyChars": 100000,
    "usedThisMonth": 12000,
    "remainingChars": 88000,
    "concurrencyLimit": 1,
    "fastModeEquivalent": 176000,
    "expertModeEquivalent": 88000,
    "teamModeEquivalent": 44000
  }
}
```

### 13. Get Translation History

`GET /user/translation-history` — Auth required

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| page | integer | 1 | Page number |
| pageSize | integer | 20 | Items per page |
| type | string | all | Type: all, text, document |

### 14. Get User Preferences

`GET /user/preferences` — Auth required

### 15. Update User Preferences

`PUT /user/preferences` — Auth required

---

## Glossary Management

**Base path**: `/user/glossaries`
**Controller**: `WebGlossaryController`
**Auth**: Required

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/user/glossaries` | GET | List glossaries |
| `/user/glossaries/{id}` | GET | Glossary detail |
| `/user/glossaries` | POST | Create term |
| `/user/glossaries/{id}` | PUT | Update term |
| `/user/glossaries/{id}` | DELETE | Delete term |
| `/user/glossaries/{id}/terms` | GET | List terms |

---

## Platform Statistics

`GET /platform/stats` — Auth not required

```json
{
  "success": true,
  "data": {
    "totalUsers": 1000,
    "activeUsersToday": 150,
    "activeUsersWeek": 500,
    "activeUsersMonth": 800,
    "totalTranslations": 50000,
    "translationsToday": 1200,
    "totalCharacters": 10000000,
    "totalDocumentTranslations": 500,
    "totalGlossaries": 200,
    "systemStatus": "normal"
  }
}
```

---

## Document Management

**Base path**: `/user/documents`
**Controller**: `WebDocumentController`
**Auth**: Required

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/user/documents` | GET | List documents (page, pageSize, status filters) |
| `/user/documents/{docId}` | GET | Document detail |
| `/user/documents/{docId}` | DELETE | Delete document |
| `/user/documents/{docId}/cancel` | POST | Cancel translation |
| `/user/documents/{docId}/retry` | POST | Retry translation |
| `/user/documents/upload` | POST | Upload document (multipart/form-data) |
| `/user/documents/{docId}/download` | GET | Download translated document |

> **Team mode**: Auto-creates collaboration project and splits chapters. Returns `projectId`.

---

## Collaboration Projects

**Base path**: `/v1/collab`
**Controller**: `CollabProjectController`
**Auth**: Required

### Projects

| Endpoint | Method | Description | Permission |
|----------|--------|-------------|------------|
| `/v1/collab/projects` | POST | Create project | Authenticated |
| `/v1/collab/projects` | GET | User's projects | Authenticated |
| `/v1/collab/projects/{projectId}` | GET | Project detail | Member |
| `/v1/collab/projects/{projectId}` | PUT | Update project | Member |
| `/v1/collab/projects/{projectId}/status` | POST | Change status | Owner |
| `/v1/collab/projects/{projectId}` | DELETE | Delete project | Owner |

### Chapters

| Endpoint | Method | Description | Permission |
|----------|--------|-------------|------------|
| `/v1/collab/projects/{projectId}/chapters` | POST | Create chapter | Owner |
| `/v1/collab/projects/{projectId}/chapters` | GET | Chapter list | Member |

---

## Page Routes

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Home page |
| GET | `/home` | Home page (alternate) |
| GET | `/verification` | Verification code page |
| GET | `/register` | Registration page |

---

## Error Codes

### General

| Code | Description |
|------|-------------|
| 200 | Success |
| 400 | Bad request |
| 401 | Unauthorized |
| 403 | Forbidden |
| 404 | Not found |
| 408 | Timeout |
| 409 | Conflict |
| 500 | Internal error |

### User

| Code | Description |
|------|-------------|
| U001 | User not found |
| U002 | Invalid password |
| U003 | Account locked |
| U004 | Account disabled |
| U005 | Email already registered |
| U006 | Invalid email format |
| U007 | Password too short |
| U008 | Verification code expired or invalid |

### Translation

| Code | Description |
|------|-------------|
| T001 | Translation engine unavailable |
| T002 | Rate limit exceeded |
| T003 | Unsupported language |
| T004 | Translation content empty |
| T005 | Translation failed |

### Email

| Code | Description |
|------|-------------|
| E001 | Email send failed |
| E002 | Invalid email address |
| E003 | Verification code expired |

### Token

| Code | Description |
|------|-------------|
| T101 | Token invalid or expired |
| T102 | Token expired |
| T103 | Token missing |

---

**Last updated**: 2026-04-27
