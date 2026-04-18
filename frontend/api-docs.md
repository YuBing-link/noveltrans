# 网站后端 API 接口文档

> 本文档描述后端服务器提供的所有 REST API 接口。

---

## 📋 目录

- [通用说明](#通用说明)
- [翻译接口](#翻译接口)
- [用户接口](#用户接口)
- [文档管理接口](#文档管理接口)
- [页面路由接口](#页面路由接口)
- [错误码说明](#错误码说明)

---

## 通用说明

### 基础信息

| 项目 | 值                       |
|------|-------------------------|
| 基础 URL | `http://localhost:7341` |
| API 版本 | `v1`                    |
| 数据格式 | JSON                    |
| 字符编码 | UTF-8                   |

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

**基础路径**: `/v1/translate`

### 1. 文本翻译

**接口**: `POST /v1/translate/text`

**认证**: 不需要

**请求体**:
```json
{
  "text": "要翻译的文本内容",
  "sourceLang": "auto",
  "targetLang": "zh",
  "mode": "novel",
  "engine": "google"
}
```

**请求参数说明**:

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| text | string | 是 | - | 待翻译的文本内容 |
| sourceLang | string | 否 | auto | 源语言代码，auto 表示自动检测 |
| targetLang | string | 是 | - | 目标语言代码 |
| mode | string | 否 | novel | 翻译模式：novel(小说), literal(直译), free(意译) |
| engine | string | 否 | - | 翻译引擎：google, deepl, baidu, openai |

**成功响应**:
```json
{
  "success": true,
  "data": {
    "translatedText": "翻译后的文本",
    "detectedLang": "en",
    "targetLang": "zh",
    "engine": "google",
    "costTime": 1250
  },
  "code": "200",
  "message": null
}
```

**响应参数说明**:

| 字段 | 类型 | 说明 |
|------|------|------|
| translatedText | string | 翻译后的文本 |
| detectedLang | string | 检测到的源语言 |
| targetLang | string | 目标语言 |
| engine | string | 使用的翻译引擎 |
| costTime | long | 耗时（毫秒） |

---

### 2. 选中翻译

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

**请求参数说明**:

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| text | string | 是 | - | 选中的待翻译文本 |
| sourceLang | string | 否 | auto | 源语言代码 |
| targetLang | string | 否 | zh | 目标语言代码 |
| engine | string | 否 | - | 翻译引擎 |
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

### 3. 阅读器翻译

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

**请求参数说明**:

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
    "translatedContent": "<h1>文章标题</h1><p>翻译后的内容...</p>"
  },
  "code": "200",
  "message": null
}
```

---

### 4. 网页翻译（流式）

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

**请求参数说明**:

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

**流式事件说明**:

| 事件类型 | 格式 | 说明 |
|----------|------|------|
| 翻译块 | `data: {"textId":"xxx","translation":"..."}` | 单个文本块的翻译结果 |
| 完成标记 | `data: [DONE]` | 全部翻译完成 |
| 错误信息 | `data: ERROR: 错误描述` | 发生错误时返回 |

---

### 5. 文档翻译

**接口**: `POST /v1/translate/document`

**认证**: 需要

**Content-Type**: `multipart/form-data`

**请求参数**:

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| file | file | 是 | - | 要翻译的文档文件 |
| sourceLang | string | 否 | auto | 源语言代码 |
| targetLang | string | 是 | - | 目标语言代码 |
| mode | string | 否 | novel | 翻译模式 |

**成功响应**:
```json
{
  "success": true,
  "data": {
    "taskId": "task_123456",
    "documentId": 1,
    "documentName": "novel.epub",
    "status": "pending",
    "message": "文档上传成功，开始翻译"
  },
  "code": "200",
  "message": null
}
```

---

### 6. 查询翻译任务状态

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

**响应参数说明**:

| 字段 | 类型 | 说明 |
|------|------|------|
| taskId | string | 任务 ID |
| type | string | 任务类型：text, document |
| status | string | 状态：pending, processing, translating, completed, failed |
| progress | integer | 进度百分比 (0-100) |
| sourceLang | string | 源语言 |
| targetLang | string | 目标语言 |
| createTime | string | 创建时间 (ISO 8601) |
| completedTime | string | 完成时间，未完成时为 null |
| errorMessage | string | 错误信息，失败时返回 |

---

### 7. 取消翻译任务

**接口**: `DELETE /v1/translate/task/{taskId}`

**认证**: 需要

**路径参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| taskId | string | 任务 ID |

**成功响应**:
```json
{
  "success": true,
  "data": null,
  "code": "200",
  "message": null
}
```

---

### 8. 获取翻译结果

**接口**: `GET /v1/translate/task/{taskId}/result`

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
    "status": "completed",
    "translatedText": "翻译后的文本内容",
    "translatedFilePath": null,
    "sourceLang": "en",
    "targetLang": "zh",
    "completedTime": "2026-02-24T10:05:00Z"
  },
  "code": "200"
}
```

**响应参数说明**:

| 字段 | 类型 | 说明 |
|------|------|------|
| taskId | string | 任务 ID |
| status | string | 任务状态 |
| translatedText | string | 翻译后的文本（文本翻译） |
| translatedFilePath | string | 翻译后的文件路径（文档翻译） |
| sourceLang | string | 源语言 |
| targetLang | string | 目标语言 |
| completedTime | string | 完成时间 |

---

### 9. 下载翻译结果

**接口**: `GET /v1/translate/task/{taskId}/download`

**认证**: 需要

**路径参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| taskId | string | 任务 ID |

**成功响应**: 二进制文件流

**响应头**:
```
Content-Type: application/octet-stream
Content-Disposition: form-data; filename="translated_task_123456"
```

**状态码**:
- `200 OK`: 下载成功
- `404 Not Found`: 任务不存在或结果不可用
- `401 Unauthorized`: 未认证

---

### 10. 高级翻译（认证用户）

**接口**: `POST /v1/translate/premium-selection`

**认证**: 需要

**说明**: 为认证用户提供的高级选中文本翻译功能，参数和响应与 `/v1/translate/selection` 相同。

---

**接口**: `POST /v1/translate/premium-reader`

**认证**: 需要

**说明**: 为认证用户提供的高级阅读器翻译功能，参数和响应与 `/v1/translate/reader` 相同。

---

## 用户接口

**基础路径**: `/user`

### 1. 发送验证码

**接口**: `POST /user/send-code`

**认证**: 不需要

**请求体**:
```json
{
  "email": "user@example.com"
}
```

**请求参数说明**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | string | 是 | 用户邮箱地址 |

**成功响应**:
```json
{
  "success": true,
  "data": null,
  "code": "200",
  "message": "验证码已发送"
}
```

---

### 2. 用户登录

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

**请求参数说明**:

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

**data 字段说明**:

| 字段 | 类型 | 说明 |
|------|------|------|
| id | integer | 用户 ID |
| email | string | 用户邮箱 |
| username | string | 用户名 |
| avatar | string | 头像 URL |
| userLevel | string | 用户等级：FREE, PRO |
| createTime | string | 注册时间 |

---

### 3. 用户注册

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

**请求参数说明**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | string | 是 | 用户邮箱 |
| password | string | 是 | 用户密码 |
| code | string | 是 | 邮箱验证码 |
| username | string | 否 | 用户名（可选） |
| avatar | string | 否 | 头像 URL（可选） |

**成功响应**: 同登录接口

---

### 4. 获取当前用户信息

**接口**: `GET /user/profile`

**认证**: 需要

**响应**:
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
  "code": "200"
}
```

---

### 5. 更新用户信息

**接口**: `PUT /user/profile`

**认证**: 需要

**请求体**:
```json
{
  "username": "新用户名",
  "avatar": "https://new-avatar-url.com/avatar.jpg"
}
```

**请求参数说明**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 否 | 新用户名 |
| avatar | string | 否 | 新头像 URL |

**成功响应**: 返回更新后的用户信息

---

### 6. 修改密码

**接口**: `POST /user/change-password`

**认证**: 需要

**请求体**:
```json
{
  "oldPassword": "原密码",
  "newPassword": "新密码"
}
```

**请求参数说明**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| oldPassword | string | 是 | 原密码 |
| newPassword | string | 是 | 新密码 |

**成功响应**:
```json
{
  "success": true,
  "data": null,
  "code": "200",
  "message": "密码修改成功"
}
```

---

### 7. 重置密码

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

**请求参数说明**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | string | 是 | 用户邮箱 |
| code | string | 是 | 验证码 |
| newPassword | string | 是 | 新密码 |

**成功响应**:
```json
{
  "success": true,
  "data": null,
  "code": "200",
  "message": "密码重置成功"
}
```

---

### 8. 刷新令牌

**接口**: `POST /user/refresh-token`

**认证**: 不需要

**请求体**:
```json
{
  "refreshToken": "刷新令牌"
}
```

**成功响应**: 返回新的 token

---

### 9. 退出登录

**接口**: `POST /user/logout`

**认证**: 需要

**请求体** (可选):
```json
{
  "refreshToken": "刷新令牌"
}
```

**成功响应**:
```json
{
  "success": true,
  "data": null,
  "code": "200",
  "message": "已退出登录"
}
```

---

### 10. 获取用户统计

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

**响应参数说明**:

| 字段 | 类型 | 说明 |
|------|------|------|
| totalTranslations | integer | 总翻译次数 |
| textTranslations | integer | 文本翻译次数 |
| documentTranslations | integer | 文档翻译次数 |
| totalCharacters | long | 总翻译字符数 |
| totalDocuments | integer | 总翻译文档数 |
| weekTranslations | integer | 本周翻译次数 |
| monthTranslations | integer | 本月翻译次数 |

---

### 11. 获取用户配额

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

**响应参数说明**:

| 字段 | 类型 | 说明 |
|------|------|------|
| userLevel | string | 用户等级：FREE, PRO |
| dailyLimit | integer | 每日翻译限额 |
| usedToday | integer | 今日已用次数 |
| remaining | integer | 剩余次数 |
| concurrencyLimit | integer | 并发限制 |
| canTranslateDocument | boolean | 是否可翻译文档 |

---

### 12. 获取翻译历史

**接口**: `GET /user/translation-history`

**认证**: 需要

**查询参数**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | integer | 否 | 1 | 页码 |
| pageSize | integer | 否 | 20 | 每页数量 |
| type | string | 否 | all | 类型：all, text, document |

**成功响应**:
```json
{
  "success": true,
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 50,
    "totalPages": 3,
    "list": [
      {
        "id": 1,
        "taskId": "task_123456",
        "type": "text",
        "sourceLang": "en",
        "targetLang": "zh",
        "sourceTextPreview": "Hello world",
        "targetTextPreview": "你好世界",
        "createTime": "2026-02-24T10:00:00Z"
      }
    ]
  },
  "code": "200"
}
```

**分页对象说明**:

| 字段 | 类型 | 说明 |
|------|------|------|
| page | integer | 当前页码 |
| pageSize | integer | 每页数量 |
| total | long | 总记录数 |
| totalPages | integer | 总页数 |
| list | array | 数据列表 |

**翻译历史项说明**:

| 字段 | 类型 | 说明 |
|------|------|------|
| id | integer | 历史记录 ID |
| taskId | string | 任务 ID |
| type | string | 类型：text, document |
| sourceLang | string | 源语言 |
| targetLang | string | 目标语言 |
| sourceTextPreview | string | 原文预览（截断） |
| targetTextPreview | string | 译文预览（截断） |
| createTime | string | 创建时间 |

---

### 13. 注册设备 Token

**接口**: `POST /user/register-device`

**认证**: 需要

**请求体**:
```json
{
  "deviceId": "device-unique-id-123"
}
```

**请求参数说明**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| deviceId | string | 是 | 设备唯一标识 |

**成功响应**:
```json
{
  "success": true,
  "data": "设备注册成功",
  "code": "200",
  "message": null
}
```

---

### 14. 获取 Token（设备）

**接口**: `GET /user/get-token/{deviceId}`

**认证**: 不需要

**路径参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| deviceId | string | 设备 ID |

**成功响应**:
```json
{
  "success": true,
  "data": {
    "userId": "1",
    "email": "user@example.com",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  },
  "code": "200"
}
```

---

## 文档管理接口

**基础路径**: `/user/documents`

**认证**: 需要

### 1. 获取文档列表

**接口**: `GET /user/documents`

**查询参数**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | integer | 否 | 1 | 页码 |
| pageSize | integer | 否 | 20 | 每页数量 |
| status | string | 否 | all | 状态：all, pending, processing, completed, failed |

**成功响应**:
```json
{
  "success": true,
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 10,
    "totalPages": 1,
    "list": [
      {
        "id": 1,
        "name": "novel.epub",
        "fileType": "epub",
        "fileSize": 1024000,
        "sourceLang": "en",
        "targetLang": "zh",
        "status": "completed",
        "progress": 100,
        "createTime": "2026-02-24T10:00:00Z",
        "completedTime": "2026-02-24T10:05:00Z",
        "errorMessage": null
      }
    ]
  },
  "code": "200"
}
```

**文档信息说明**:

| 字段 | 类型 | 说明 |
|------|------|------|
| id | integer | 文档 ID |
| name | string | 文档名称 |
| fileType | string | 文件类型 |
| fileSize | long | 文件大小（字节） |
| sourceLang | string | 源语言 |
| targetLang | string | 目标语言 |
| status | string | 状态 |
| progress | integer | 进度 (0-100) |
| createTime | string | 创建时间 |
| completedTime | string | 完成时间 |
| errorMessage | string | 错误信息 |

---

### 2. 获取文档详情

**接口**: `GET /user/documents/{docId}`

**路径参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| docId | integer | 文档 ID |

**成功响应**: 返回单个文档详情对象（格式同上）

---

### 3. 删除文档

**接口**: `DELETE /user/documents/{docId}`

**路径参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| docId | integer | 文档 ID |

**成功响应**:
```json
{
  "success": true,
  "data": null,
  "code": "200",
  "message": null
}
```

---

### 4. 重新翻译

**接口**: `POST /user/documents/{docId}/retry`

**路径参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| docId | integer | 文档 ID |

**成功响应**:
```json
{
  "success": true,
  "data": null,
  "code": "200",
  "message": null
}
```

---

### 5. 上传并翻译文档

**接口**: `POST /user/documents/upload`

**Content-Type**: `multipart/form-data`

**请求参数**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| file | file | 是 | - | 文档文件 |
| sourceLang | string | 否 | auto | 源语言代码 |
| targetLang | string | 是 | - | 目标语言代码 |
| mode | string | 否 | novel | 翻译模式 |

**成功响应**:
```json
{
  "success": true,
  "data": {
    "taskId": "task_123456",
    "documentId": 1,
    "documentName": "novel.epub",
    "status": "pending",
    "message": "文档上传成功，开始翻译"
  },
  "code": "200"
}
```

---

### 6. 下载文档

**接口**: `GET /user/documents/{docId}/download`

**路径参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| docId | integer | 文档 ID |

**成功响应**: 二进制文件流

**响应头**:
```
Content-Type: application/octet-stream
Content-Disposition: form-data; filename="novel.epub"
```

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

## 快速参考

### 翻译流程示例

#### 1. 文本翻译

```bash
# 请求
curl -X POST http://localhost:7341/v1/translate/text \
  -H "Content-Type: application/json" \
  -d '{"text":"Hello World","targetLang":"zh","engine":"google"}'

# 响应
{"success":true,"data":{"translatedText":"你好世界","detectedLang":"en","targetLang":"zh","engine":"google","costTime":1250},"code":"200"}
```

#### 2. 用户登录

```bash
# 请求
curl -X POST http://localhost:7341/user/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password123"}'

# 响应
{"success":true,"data":{"id":1,"email":"user@example.com","username":"用户"},"code":"200","token":"eyJhbGciOi..."}
```

#### 3. 获取用户配额（需要认证）

```bash
# 请求
curl -X GET http://localhost:7341/user/quota \
  -H "Authorization: Bearer eyJhbGciOi..."

# 响应
{"success":true,"data":{"userLevel":"FREE","dailyLimit":50,"usedToday":12,"remaining":38},"code":"200"}
```

---

**文档更新日期**: 2026-02-24
