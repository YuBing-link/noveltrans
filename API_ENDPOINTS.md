# 三种翻译模式API端点文档

## 📍 API端点配置

所有API端点都在 `src/lib/config.js` 中定义:

```javascript
API_BASE_URL = 'https://127.0.0.1:7341/v1'
TRANSLATION_MODES = {
    WEBPAGE:   '/translate/webpage',    // 整个网页翻译（批量）
    READER:    '/translate/reader',     // 阅读器翻译
    SELECTION: '/translate/selection'   // 选中翻译
}
```

## 🌐 完整URL

| 模式 | 方法 | 完整URL | 说明 |
|------|------|--------|------|
| **模式1: 整个网页翻译** | POST | `https://127.0.0.1:7341/v1/translate/webpage` | 批量翻译，保留DOM结构 |
| **模式2: 阅读器翻译** | POST | `https://127.0.0.1:7341/v1/translate/reader` | 提取并翻译文章内容 |
| **模式3: 选中翻译** | POST | `https://127.0.0.1:7341/v1/translate/selection` | 翻译鼠标选中文本 |

---

## 🌍 模式1: 整个网页翻译API

**用途**: 使用映射表系统批量翻译整个网页，保留DOM结构（支持流式返回）

**触发方式**:
- popup点击"翻译网页"按钮

**工作流程**:
1. content.js分析DOM，生成映射表 (textId → 原文 + 节点信息)
2. 发送映射表到background.js
3. background.js调用本API进行批量翻译
4. 后端**流式返回** {textId → 翻译文本} 映射（每翻译完一块立即发送）
5. content.js根据映射将翻译实时应用到对应DOM节点

**请求**:
```json
POST /v1/translate/webpage
{
  "targetLang": "zh",
  "sourceLang": "auto",
  "engine": "google",
  "textRegistry": [
    {
      "id": "text_a1b2c3",
      "original": "Hello world",
      "context": "This is example context"
    },
    {
      "id": "text_d4e5f6",
      "original": "Another paragraph",
      "context": "More context here"
    }
  ]
}
```

**参数说明**:
- `engine`: 翻译引擎，支持 `google`、`deepl`、`openai`、`baidu`
- `sourceLang`: 源语言（自动检测时为 `auto`）
- `targetLang`: 目标语言代码
- `textRegistry`: 包含要翻译的文本ID和上下文

**响应格式**: Server-Sent Events (SSE) 流式响应

每个翻译块返回一个事件：
```json
data: {"textId":"text_a1b2c3","original":"Hello world","translation":"你好世界"}

data: {"textId":"text_d4e5f6","original":"Another paragraph","translation":"另一个段落"}

data: [DONE]
```

**流式事件说明**:
- 每个 `data:` 行包含一个 JSON 对象，包含单个文本块的翻译结果
- 翻译完成后发送 `data: [DONE]` 标记
- 如果发生错误，发送 `data: ERROR: 错误信息`

**流式响应处理示例**:
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

        // 立即更新对应DOM节点
        const element = document.getElementById(textId);
        if (element) {
            element.textContent = translation;
        }
    }
}
```


---

## 📖 模式2: 阅读器翻译API

**用途**: 提取文章内容并翻译，用于阅读模式

**触发方式**:
- popup点击"阅读模式"按钮

**工作流程**:
1. read.js使用Readability提取文章
2. 发送文章HTML到本API
3. 后端翻译HTML中的文本内容
4. read.js在阅读界面显示翻译后的内容

**请求**:
```json
POST /v1/translate/reader
{
  "content": "<h1>Article Title</h1><p>This is article content...</p><p>More content here</p>",
  "targetLang": "zh",
  "sourceLang": "auto",
  "engine": "google"
}
```

**参数说明**:
- `engine`: 翻译引擎，支持 `google`、`deepl`、`openai`、`baidu`
- `content`: HTML内容
- `targetLang`: 目标语言
- `sourceLang`: 源语言

**响应** (Success):
```json
{
  "success": true,
  "engine": "google",
  "translatedContent": "<h1>文章标题</h1><p>这是文章内容...</p><p>更多内容在这里</p>"
}
```

**响应** (Error):
```json
{
  "success": false,
  "error": "Invalid HTML content",
  "code": "INVALID_CONTENT"
}
```


---

## 🖱️ 模式3: 选中翻译API

**用途**: 翻译用户在网页上选中的文本，包含上下文

**触发方式**:
- 在网页上选中文本
- 点击自动显示的翻译智能按钮

**工作流程**:
1. selection.js检测用户选中文本
2. 显示智能翻译按钮
3. 用户点击后，发送选中文本和上下文到API
4. 后端返回翻译结果
5. 显示在浮窗中

**请求**:
```json
POST /v1/translate/selection
{
  "sourceLang": "auto",
  "targetLang": "zh",
  "engine": "google",
  "context": "Here is some context around the selected text to help with translation"
}
```

**参数说明**:
- `engine`: 翻译引擎，支持 `google`、`deepl`、`openai`、`baidu`
- `sourceLang`: 源语言
- `targetLang`: 目标语言
- `context`: 选中的文本

**响应** (Success):
```json
{
  "success": true,
  "engine": "google",
  "translation": "这里选中的文本"
}
```

**响应** (Error):
```json
{
  "success": false,
  "error": "Empty text provided",
  "code": "INVALID_TEXT"
}
```



---

## 🐛 通用错误响应

所有API都返回一致的错误格式:

```json
{
  "success": false,
  "error": "Error message description",
  "code": "ERROR_CODE",
  "details": {}
}
```

**常见错误码**:
- `INVALID_INPUT`: 输入参数无效
- `SERVICE_ERROR`: 翻译服务错误
- `RATE_LIMIT`: 超过速率限制
- `UNAUTHORIZED`: API密钥无效
- `TIMEOUT`: 请求超时
- `INVALID_HTML`: HTML格式错误（仅模式2）
- `INVALID_TEXT`: 文本为空（仅模式3）



## 🔄 数据流图

```
模式1 (整个网页翻译):
用户点击"翻译网页" 
  ↓
popup.js 发送 'translateWebPage' 消息
  ↓
content.js 分析DOM → 生成映射表
  ↓
content.js 发送映射表-> background.js
  ↓
background.js 调用 /v1/translate/webpage 翻译映射表
  ↓
后端返回 {textId → 翻译}
  ↓
background.js 主动发送翻译后的映射表-> content.js
  ↓
content.js 应用翻译到页面
  ↓
用户看到翻译后的网页


模式2 (阅读器翻译):
用户点击"阅读模式"
  ↓
popup.js 发送 'activateReaderMode' 消息
  ↓
read.js 提取文章内容
  ↓
background.js 调用 /v1/translate/reader
  ↓
后端返回翻译后的HTML
  ↓
read.js 显示阅读界面
  ↓
用户进入阅读模式


模式3 (选中翻译):
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

**最后更新**: 2026年2月14日

---

## 👤 用户模块 API

### 术语库管理

| 接口 | 方法 | 说明 | 认证 |
|------|------|------|------|
| `/user/glossaries` | GET | 获取术语库列表 | ✅ |
| `/user/glossaries/{id}` | GET | 获取术语库详情 | ✅ |
| `/user/glossaries` | POST | 创建术语项 | ✅ |
| `/user/glossaries/{id}` | PUT | 更新术语项 | ✅ |
| `/user/glossaries/{id}` | DELETE | 删除术语项 | ✅ |
| `/user/glossaries/{id}/terms` | GET | 获取术语列表 | ✅ |

### 用户偏好设置

| 接口 | 方法 | 说明 | 认证 |
|------|------|------|------|
| `/user/preferences` | GET | 获取用户偏好设置 | ✅ |
| `/user/preferences` | PUT | 更新用户偏好设置 | ✅ |

### 平台统计

| 接口 | 方法 | 说明 | 认证 |
|------|------|------|------|
| `/platform/stats` | GET | 获取平台统计信息 | ❌ |

---

### 📚 术语库列表 API

**用途**: 获取当前用户的所有术语项列表

**请求**:
```http
GET /user/glossaries
Authorization: Bearer {token}
```

**响应** (Success):
```json
{
  "code": "200",
  "msg": "success",
  "data": [
    {
      "id": 1,
      "sourceWord": "magic",
      "targetWord": "魔法",
      "remark": "奇幻小说专用术语",
      "createTime": "2026-03-04T10:00:00"
    }
  ]
}
```

---

### 📖 术语库详情 API

**用途**: 获取单个术语项的详细信息

**请求**:
```http
GET /user/glossaries/{id}
Authorization: Bearer {token}
```

**响应** (Success):
```json
{
  "code": "200",
  "msg": "success",
  "data": {
    "id": 1,
    "sourceWord": "magic",
    "targetWord": "魔法",
    "remark": "奇幻小说专用术语",
    "createTime": "2026-03-04T10:00:00"
  }
}
```

---

### ➕ 创建术语项 API

**用途**: 创建新的术语项

**请求**:
```http
POST /user/glossaries
Authorization: Bearer {token}
Content-Type: application/json

{
  "sourceWord": "spiritual energy",
  "targetWord": "灵气",
  "remark": "修仙小说中修炼所需的能量"
}
```

**响应** (Success):
```json
{
  "code": "200",
  "msg": "success",
  "data": {
    "id": 3,
    "sourceWord": "spiritual energy",
    "targetWord": "灵气",
    "remark": "修仙小说中修炼所需的能量",
    "createTime": "2026-03-04T11:00:00"
  }
}
```

---

### ✏️ 更新术语项 API

**用途**: 更新现有术语项

**请求**:
```http
PUT /user/glossaries/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "targetWord": "灵力"
}
```

**响应** (Success):
```json
{
  "code": "200",
  "msg": "success",
  "data": {
    "id": 3,
    "sourceWord": "spiritual energy",
    "targetWord": "灵力",
    "remark": "修仙小说中修炼所需的能量",
    "createTime": "2026-03-04T11:00:00"
  }
}
```

---

### 🗑️ 删除术语项 API

**用途**: 删除指定的术语项

**请求**:
```http
DELETE /user/glossaries/{id}
Authorization: Bearer {token}
```

**响应** (Success):
```json
{
  "code": "200",
  "msg": "success",
  "data": null
}
```


