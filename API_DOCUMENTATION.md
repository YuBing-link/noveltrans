# 网站后端 API 接口文档

> 本文档描述后端服务器提供的所有 REST API 接口。

---

## 📋 目录

- [通用说明](#通用说明)
- [翻译接口](#翻译接口)
  - [插件翻译（浏览器扩展）](#插件翻译浏览器扩展)
  - [共享翻译（Web + 插件）](#共享翻译web--插件)
  - [外部 API（API Key 调用）](#外部-apiapi-key-调用)
  - [RAG 翻译记忆](#rag-翻译记忆)
- [用户接口](#用户接口)
- [文档管理接口](#文档管理接口)
- [协作项目接口](#协作项目接口)
- [页面路由接口](#页面路由接口)
- [错误码说明](#错误码说明)
- [前端数据流图](#前端数据流图)

---

## 通用说明

### 基础信息

| 项目 | 值 |
|------|------|
| 基础 URL | `http://localhost:7341` |
| API 版本 | `v1` |
| 数据格式 | JSON |
| 字符编码 | UTF-8 |

### 通用响应格式

所有接口统一返回以下格式：

```json
{
  "success": true,
  "data": {},
  "code": "200",
  "message": null,
  "token": null
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| success | boolean | 请求是否成功 |
| data | T | 响应数据，类型因接口而异 |
| code | string | 状态码，"200" 表示成功 |
| message | string | 错误信息，成功时为 null |
| token | string | JWT Token（仅登录/注册接口返回） |

### 认证方式

需要认证的接口需在请求头中携带 JWT Token：

```
Authorization: Bearer <token>
```

---

## 翻译接口

### 插件翻译（浏览器扩展）

**基础路径**: `/v1/translate`
**Controller**: `PluginTranslateController`

以下接口面向浏览器扩展，支持公共访问（认证用户享有更高配额）。

#### 1. 选中文本翻译

**接口**: `POST /v1/translate/selection`

**认证**: 不需要

**请求体**:
```json
{
  "text": "选中的文本",
  "sourceLang": "auto",
  "targetLang": "zh",
  "engine": "google",
  "context": "上下文内容"
}
```

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| text | string | 是 | - | 选中的待翻译文本 |
| sourceLang | string | 否 | auto | 源语言代码 |
| targetLang | string | 否 | zh | 目标语言代码 |
| engine | string | 否 | - | 翻译引擎：google, deepl, baidu, openai, mymemory, libre |
| context | string | 否 | null | 上下文内容，帮助提高翻译准确性 |

**成功响应**:
```json
{
  "success": true,
  "data": {
    "success": true,
    "engine": "google",
    "translation": "翻译结果"
  },
  "code": "200",
  "message": null
}
```

---

#### 2. 阅读器翻译

**接口**: `POST /v1/translate/reader`

**认证**: 不需要

**请求体**:
```json
{
  "content": "<h1>文章标题</h1><p>文章内容...</p>",
  "sourceLang": "auto",
  "targetLang": "zh",
  "engine": "google"
}
```

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| content | string | 是 | - | HTML 格式的文章内容 |
| sourceLang | string | 否 | auto | 源语言代码 |
| targetLang | string | 是 | - | 目标语言代码 |
| engine | string | 否 | - | 翻译引擎 |

**成功响应**:
```json
{
  "success": true,
  "data": {
    "success": true,
    "engine": "google",
    "translatedContent": "<h1>文章标题（已翻译）</h1><p>翻译后的内容...</p>"
  },
  "code": "200",
  "message": null
}
```

---

#### 3. 网页翻译（SSE 流式）

**接口**: `POST /v1/translate/webpage`

**认证**: 不需要

**Content-Type**: `application/json`

**响应类型**: `text/event-stream` (SSE 流式响应)

**请求体**:
```json
{
  "targetLang": "zh",
  "sourceLang": "auto",
  "engine": "google",
  "textRegistry": [
    {
      "id": "text_a1b2c3",
      "original": "Hello world",
      "context": "上下文信息"
    },
    {
      "id": "text_d4e5f6",
      "original": "Another paragraph",
      "context": "更多上下文"
    }
  ]
}
```

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| targetLang | string | 是 | - | 目标语言代码 |
| sourceLang | string | 否 | auto | 源语言代码 |
| engine | string | 否 | - | 翻译引擎 |
| textRegistry | array | 是 | - | 文本映射表 |
| textRegistry[].id | string | 是 | - | 文本节点唯一标识 |
| textRegistry[].original | string | 是 | - | 原始文本 |
| textRegistry[].context | string | 否 | null | 上下文信息 |

**流式响应格式**:

每个事件独立返回，格式如下：

```
data: {"textId":"text_a1b2c3","original":"Hello world","translation":"你好世界"}

data: {"textId":"text_d4e5f6","original":"Another paragraph","translation":"另一个段落"}

data: [DONE]
```

| 事件类型 | 格式 | 说明 |
|----------|------|------|
| 翻译块 | `data: {"textId":"xxx","translation":"..."}` | 单个文本块的翻译结果 |
| 完成标记 | `data: [DONE]` | 全部翻译完成 |
| 错误信息 | `data: ERROR: 错误描述` | 发生错误时返回 |

**前端 SSE 处理示例**:

```javascript
const response = await fetch('http://localhost:8080/v1/translate/webpage', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(requestData)
});

const reader = response.body.getReader();
const decoder = new TextDecoder('utf-8');
let buffer = '';

while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split('\n');
    buffer = lines.pop() || '';

    for (const line of lines) {
        if (!line.startsWith('data: ')) continue;

        const data = line.slice(6).trim();

        if (data === '[DONE]') {
            console.log('翻译完成');
            break;
        }

        if (data.startsWith('ERROR:')) {
            console.error('错误:', data);
            break;
        }

        // 处理单个翻译结果
        const result = JSON.parse(data);
        const { textId, translation } = result;

        // 立即更新对应 DOM 节点
        const element = document.getElementById(textId);
        if (element) {
            element.textContent = translation;
        }
    }
}
```

---

#### 4. 文本流式翻译（SSE）

**接口**: `POST /v1/translate/text/stream`

**认证**: 不需要

**响应类型**: `text/event-stream`

请求参数与「选中文本翻译」相同，但以 SSE 流式方式返回结果，适用于长文本的单段流式输出。

---

#### 5. 高级翻译（认证用户）

**接口**: `POST /v1/translate/premium-selection`

**认证**: 需要

为认证用户提供更高配额的选中文本翻译，参数和响应与 `/v1/translate/selection` 相同。

---

**接口**: `POST /v1/translate/premium-reader`

**认证**: 需要

为认证用户提供更高配额的阅读器翻译，参数和响应与 `/v1/translate/reader` 相同。

---

### 共享翻译（Web + 插件）

**基础路径**: `/v1/translate`
**Controller**: `SharedTranslateController`

#### 6. 查询翻译任务状态

**接口**: `GET /v1/translate/task/{taskId}`

**认证**: 不需要

**路径参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| taskId | string | 任务 ID |

**成功响应**:
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
    "createTime": "2026-02-24T10:00:00Z",
    "completedTime": null,
    "errorMessage": null
  },
  "code": "200"
}
```

---

#### 7. 取消翻译任务

**接口**: `DELETE /v1/translate/task/{taskId}`

**认证**: 需要

---

#### 8. 删除翻译历史记录

**接口**: `DELETE /v1/translate/history/{taskId}`

**认证**: 需要

---

#### 9. 获取翻译结果

**接口**: `GET /v1/translate/task/{taskId}/result`

**认证**: 不需要

---

#### 10. 下载翻译结果

**接口**: `GET /v1/translate/task/{taskId}/download`

**认证**: 需要

返回二进制文件流。

---

#### 11. 文档流式翻译（直接上传文件）

**接口**: `POST /v1/translate/document/stream`

**认证**: 不需要（部分场景可能需要认证）

**Content-Type**: `multipart/form-data`

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| file | file | 是 | - | 要翻译的文档文件 |
| sourceLang | string | 否 | auto | 源语言代码 |
| targetLang | string | 是 | zh | 目标语言代码 |
| mode | string | 否 | fast | 翻译模式：fast, expert, team |

**响应类型**: `text/event-stream` (SSE 流式响应)

---

#### 12. 文档流式翻译（基于已上传文档）

**接口**: `POST /v1/translate/document/stream/{docId}`

**认证**: 需要

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| docId | Long | 是 | - | 已上传的文档 ID |
| targetLang | string | 是 | zh | 目标语言代码 |
| mode | string | 否 | fast | 翻译模式 |

**响应类型**: `text/event-stream`

---

### 外部 API（API Key 调用）

**基础路径**: `/v1/external`
**Controller**: `ExternalTranslateController`
**认证方式**: `Authorization: Bearer nt_sk_xxxx`（API Key）

#### 13. 文本翻译

**接口**: `POST /v1/external/translate`

**认证**: 需要（API Key）

**请求体**:
```json
{
  "text": "要翻译的文本内容",
  "sourceLang": "auto",
  "targetLang": "zh",
  "engine": "google"
}
```

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| text | string | 是 | - | 待翻译的文本内容 |
| sourceLang | string | 否 | auto | 源语言代码 |
| targetLang | string | 是 | - | 目标语言代码 |
| engine | string | 否 | google | 翻译引擎 |

**成功响应**:
```json
{
  "success": true,
  "data": {
    "translatedText": "翻译后的文本",
    "sourceLang": "en",
    "targetLang": "zh",
    "engine": "google",
    "usage": 12
  },
  "code": "200"
}
```

---

#### 14. 批量文本翻译

**接口**: `POST /v1/external/batch`

**认证**: 需要（API Key）

**请求体**:
```json
{
  "texts": ["Hello", "World"],
  "sourceLang": "auto",
  "targetLang": "zh",
  "engine": "google"
}
```

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| texts | array | 是 | - | 待翻译文本列表（最多 50 条） |
| sourceLang | string | 否 | auto | 源语言代码 |
| targetLang | string | 是 | - | 目标语言代码 |
| engine | string | 否 | google | 翻译引擎 |

---

#### 15. 获取可用翻译引擎列表

**接口**: `GET /v1/external/models`

**认证**: 需要（API Key）

**成功响应**:
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

---

#### 16. 下载翻译结果（外部 API）

**接口**: `GET /v1/external/task/{taskId}/download`

**认证**: 需要（API Key）

返回二进制文件流。

---

### RAG 翻译记忆

**基础路径**: `/v1/translate`
**Controller**: `SharedTranslateController`

#### 17. RAG 翻译记忆查询

**接口**: `POST /v1/translate/rag`

**认证**: 不需要

**请求体**:
```json
{
  "text": "要查询的文本",
  "targetLang": "zh",
  "engine": "google"
}
```

---

## 用户接口

**基础路径**: `/user`
**Controller**: `WebUserController`

### 1. 发送注册验证码

**接口**: `POST /user/send-code`

**认证**: 不需要

**请求体**:
```json
{
  "email": "user@example.com"
}
```

---

### 2. 发送重置密码验证码

**接口**: `POST /user/send-reset-code`

**认证**: 不需要

**请求体**:
```json
{
  "email": "user@example.com"
}
```

---

### 3. 用户登录

**接口**: `POST /user/login`

**认证**: 不需要

**请求体**:
```json
{
  "email": "user@example.com",
  "password": "password123",
  "from": "web"
}
```

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| email | string | 是 | - | 用户邮箱 |
| password | string | 是 | - | 用户密码 |
| from | string | 否 | null | 登录来源：web, extension |

**成功响应**:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "email": "user@example.com",
    "username": "用户名",
    "avatar": "https://...",
    "userLevel": "FREE",
    "createTime": "2026-01-01T00:00:00Z"
  },
  "code": "200",
  "message": null,
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

---

### 4. 用户注册

**接口**: `POST /user/register`

**认证**: 不需要

**请求体**:
```json
{
  "email": "user@example.com",
  "password": "password123",
  "code": "123456",
  "username": "用户名",
  "avatar": "https://..."
}
```

**成功响应**: 同登录接口

---

### 5. 获取当前用户信息

**接口**: `GET /user/profile`

**认证**: 需要

---

### 6. 更新用户信息

**接口**: `PUT /user/profile`

**认证**: 需要

**请求体**:
```json
{
  "username": "新用户名",
  "avatar": "https://new-avatar-url.com/avatar.jpg"
}
```

---

### 7. 修改密码

**接口**: `POST /user/change-password`

**认证**: 需要

**请求体**:
```json
{
  "oldPassword": "原密码",
  "newPassword": "新密码"
}
```

---

### 8. 重置密码

**接口**: `POST /user/reset-password`

**认证**: 不需要

**请求体**:
```json
{
  "email": "user@example.com",
  "code": "123456",
  "newPassword": "新密码"
}
```

---

### 9. 刷新令牌

**接口**: `POST /user/refresh-token`

**认证**: 不需要

**请求体**:
```json
{
  "refreshToken": "刷新令牌"
}
```

---

### 10. 退出登录

**接口**: `POST /user/logout`

**认证**: 需要

**请求体** (可选):
```json
{
  "refreshToken": "刷新令牌"
}
```

---

### 11. 获取用户统计

**接口**: `GET /user/statistics`

**认证**: 需要

**响应**:
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
  },
  "code": "200"
}
```

---

### 12. 获取用户配额

**接口**: `GET /user/quota`

**认证**: 需要

**响应**:
```json
{
  "success": true,
  "data": {
    "userLevel": "FREE",
    "dailyLimit": 50,
    "usedToday": 12,
    "remaining": 38,
    "concurrencyLimit": 3,
    "canTranslateDocument": false
  },
  "code": "200"
}
```

---

### 13. 获取翻译历史

**接口**: `GET /user/translation-history`

**认证**: 需要

**查询参数**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | integer | 否 | 1 | 页码 |
| pageSize | integer | 否 | 20 | 每页数量 |
| type | string | 否 | all | 类型：all, text, document |

---

### 14. 获取用户偏好设置

**接口**: `GET /user/preferences`

**认证**: 需要

---

### 15. 更新用户偏好设置

**接口**: `PUT /user/preferences`

**认证**: 需要

---

## 术语库管理接口

**基础路径**: `/user/glossaries`
**Controller**: `WebGlossaryController`
**认证**: 需要

| 接口 | 方法 | 说明 |
|------|------|------|
| `/user/glossaries` | GET | 获取术语库列表 |
| `/user/glossaries/{id}` | GET | 获取术语库详情 |
| `/user/glossaries` | POST | 创建术语项 |
| `/user/glossaries/{id}` | PUT | 更新术语项 |
| `/user/glossaries/{id}` | DELETE | 删除术语项 |
| `/user/glossaries/{id}/terms` | GET | 获取术语列表 |

**创建/更新术语项请求体**:
```json
{
  "sourceWord": "spiritual energy",
  "targetWord": "灵气",
  "remark": "修仙小说中修炼所需的能量"
}
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "sourceWord": "magic",
    "targetWord": "魔法",
    "remark": "奇幻小说专用术语",
    "createTime": "2026-03-04T10:00:00Z"
  },
  "code": "200"
}
```

---

## 平台统计接口

**基础路径**: `/platform`
**Controller**: `WebPlatformController`

### 获取平台统计

**接口**: `GET /platform/stats`

**认证**: 不需要

**响应**:
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
  },
  "code": "200"
}
```

---

## 文档管理接口

**基础路径**: `/user/documents`
**Controller**: `WebDocumentController`
**认证**: 需要

### 1. 获取文档列表

**接口**: `GET /user/documents`

**查询参数**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | integer | 否 | 1 | 页码 |
| pageSize | integer | 否 | 20 | 每页数量 |
| status | string | 否 | all | 状态：all, pending, processing, completed, failed |

---

### 2. 获取文档详情

**接口**: `GET /user/documents/{docId}`

---

### 3. 删除文档

**接口**: `DELETE /user/documents/{docId}`

---

### 4. 取消翻译

**接口**: `POST /user/documents/{docId}/cancel`

---

### 5. 重新翻译

**接口**: `POST /user/documents/{docId}/retry`

---

### 6. 上传文档

**接口**: `POST /user/documents/upload`

**Content-Type**: `multipart/form-data`

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| file | file | 是 | - | 文档文件 |
| sourceLang | string | 否 | auto | 源语言代码 |
| targetLang | string | 是 | - | 目标语言代码 |
| mode | string | 否 | fast | 翻译模式：fast, expert, team |

**成功响应**:
```json
{
  "success": true,
  "data": {
    "taskId": "task_123456",
    "documentId": 1,
    "documentName": "novel.epub",
    "status": "pending",
    "projectId": null,
    "message": "文档上传成功"
  },
  "code": "200"
}
```

> **团队模式（team）**：上传后自动创建协作项目并拆分章节，响应中返回 `projectId`。

---

### 7. 下载文档

**接口**: `GET /user/documents/{docId}/download`

返回翻译后的文档文件（二进制流）。

---

## 协作项目接口

**基础路径**: `/v1/collab`
**Controller**: `CollabProjectController`
**认证**: 需要

### 项目管理

| 接口 | 方法 | 说明 | 权限 |
|------|------|------|------|
| `/v1/collab/projects` | POST | 创建协作项目 | 认证用户 |
| `/v1/collab/projects` | GET | 获取用户参与的项目列表 | 认证用户 |
| `/v1/collab/projects/{projectId}` | GET | 获取项目详情 | 项目成员 |
| `/v1/collab/projects/{projectId}` | PUT | 更新项目信息 | 项目成员 |
| `/v1/collab/projects/{projectId}/status` | POST | 变更项目状态 | Owner |
| `/v1/collab/projects/{projectId}` | DELETE | 删除项目 | Owner |

### 章节管理

| 接口 | 方法 | 说明 | 权限 |
|------|------|------|------|
| `/v1/collab/projects/{projectId}/chapters` | POST | 创建章节 | Owner |
| `/v1/collab/projects/{projectId}/chapters` | GET | 获取项目章节列表 | 项目成员 |

---

## 页面路由接口

以下接口用于返回 HTML 页面，非 API 接口。

| 方法 | 路径 | 说明 | 返回模板 |
|------|------|------|----------|
| GET | `/` | 首页 | index.html |
| GET | `/home` | 首页（备用路径） | index.html |
| GET | `/verification` | 验证码页面 | verification.html |
| GET | `/register` | 注册页面 | verification.html |

---

## 前端数据流图

### 模式1: 网页翻译

```
用户点击"翻译网页"
  ↓
popup.js 发送 'translateWebPage' 消息
  ↓
content.js 分析 DOM → 生成映射表
  ↓
content.js 发送映射表 → background.js
  ↓
background.js 调用 /v1/translate/webpage 翻译映射表
  ↓
后端 SSE 流式返回 {textId → 翻译}
  ↓
background.js 发送翻译后的映射表 → content.js
  ↓
content.js 应用翻译到对应 DOM 节点
  ↓
用户看到翻译后的网页
```

### 模式2: 阅读器翻译

```
用户点击"阅读模式"
  ↓
popup.js 发送 'activateReaderMode' 消息
  ↓
read.js 提取文章内容
  ↓
background.js 调用 /v1/translate/reader
  ↓
后端返回翻译后的 HTML
  ↓
read.js 显示阅读界面
  ↓
用户进入阅读模式
```

### 模式3: 选中翻译

```
用户选中文本
  ↓
selection.js 显示翻译按钮
  ↓
用户点击按钮
  ↓
selection.js 通过 browser.runtime.sendMessage 发送消息到 background.js
  ↓
background.js 调用 /v1/translate/selection
  ↓
后端返回翻译结果
  ↓
background.js 将结果返回给 selection.js
  ↓
selection.js 显示结果
  ↓
用户看到翻译
```

---

## 错误码说明

### 通用错误码

| 错误码 | 说明 |
|--------|------|
| 200 | 操作成功 |
| 400 | 参数错误 |
| 401 | 未授权访问 |
| 403 | 禁止访问 |
| 404 | 资源不存在 |
| 408 | 请求超时 |
| 409 | 资源冲突 |
| 500 | 系统内部错误 |

### 用户相关错误码

| 错误码 | 说明 |
|--------|------|
| U001 | 用户不存在 |
| U002 | 密码错误 |
| U003 | 账户已被锁定 |
| U004 | 账户已被禁用 |
| U005 | 邮箱已被注册 |
| U006 | 邮箱格式不正确 |
| U007 | 密码长度不够 |
| U008 | 验证码错误或已过期 |

### 翻译相关错误码

| 错误码 | 说明 |
|--------|------|
| T001 | 翻译引擎不可用 |
| T002 | 翻译频率限制 |
| T003 | 不支持的语言 |
| T004 | 翻译内容为空 |
| T005 | 翻译失败 |

### 邮件相关错误码

| 错误码 | 说明 |
|--------|------|
| E001 | 邮件发送失败 |
| E002 | 无效的邮箱地址 |
| E003 | 验证码已过期 |

### Token 相关错误码

| 错误码 | 说明 |
|--------|------|
| T101 | 令牌无效或已过期 |
| T102 | 令牌已过期 |
| T103 | 缺少令牌 |

---

**文档更新日期**: 2026-04-22
