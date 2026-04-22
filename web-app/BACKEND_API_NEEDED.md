# 前端新增接口需求文档

本文档列出前端新增的、需要后端实现的 API 接口。

---

## 1. 生成邀请码

### 接口信息

| 字段 | 值 |
|---|---|
| 方法 | `POST` |
| 路径 | `/v1/collab/projects/{projectId}/invite-code` |
| 认证 | Bearer Token |
| 权限 | 项目 OWNER |

### 请求参数

- `projectId`（路径参数）— 项目 ID

### 响应格式

```json
{
  "success": true,
  "data": {
    "code": "ABC123XY",
    "expiresAt": "2026-04-26T12:00:00"
  }
}
```

### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | string | 邀请码，建议 8 位随机大写字母+数字组合 |
| `expiresAt` | string (ISO 8601) | 过期时间，生成时间 + 3 天（72 小时） |

### 功能说明

- 为指定项目生成一个一次性或可复用的邀请码
- 邀请码有效期为 **3 天（72 小时）**
- 过期后该码不可用，需重新生成
- 建议后端在数据库中存储邀请码记录，包含：项目ID、邀请码、创建时间、过期时间、使用状态
- 前端已有 `joinByCode` 接口（`POST /v1/collab/join?inviteCode=xxx`），后端需确保该接口能校验邀请码的有效性和过期状态

### 对接方式

前端调用代码已写在 `src/api/collab.ts`:

```typescript
generateInviteCode: (projectId: number) =>
  api.post<{ code: string; expiresAt: string }>(`/v1/collab/projects/${projectId}/invite-code`),
```

---

## 3. 上传小说到协作项目（团队模式）

### 接口信息

| 字段 | 值 |
|---|---|
| 方法 | `POST` |
| 路径 | `/user/documents/upload`（已有接口，需扩展支持 `projectId` 参数） |
| 认证 | Bearer Token |
| 请求类型 | `multipart/form-data` |

### 请求参数

| 参数 | 类型 | 必须 | 说明 |
|---|---|---|---|
| `file` | MultipartFile | 是 | 小说文件（.txt / .epub / .docx） |
| `sourceLang` | string | 是 | 源语言代码（如 `ja`） |
| `targetLang` | string | 是 | 目标语言代码（如 `zh`） |
| `mode` | string | 是 | 固定为 `team` |
| `projectId` | number | **是（新增）** | 目标协作项目 ID，指定后将章节拆分关联到该项目而非创建新项目 |

### 响应格式

```json
{
  "success": true,
  "data": {
    "projectId": 1,
    "chapterCount": 25,
    "documentId": 100,
    "documentName": "小说名.txt",
    "message": "团队模式已创建项目，共 25 个章节"
  }
}
```

### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `projectId` | number | 章节拆分后关联的项目 ID（如果传了 `projectId` 参数则返回同一个项目） |
| `chapterCount` | number | 自动拆分的章节数量 |
| `documentId` | number | 上传的文档 ID |
| `documentName` | string | 文档名称 |
| `message` | string | 提示信息 |

### 功能说明

- 当前后端已有 `POST /user/documents/upload` 接口并支持 `mode=team`
- **需要新增**：当请求中包含 `projectId` 时，不再创建新项目，而是将拆分的章节直接关联到已有项目
- 具体逻辑：读取文件 → 按章节拆分 → 为每个章节创建 `ChapterTask` 记录并关联到指定项目 → 返回章节数量

### 前端调用代码

```typescript
uploadNovel: (projectId: number, file: File, sourceLang: string, targetLang: string) => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('sourceLang', sourceLang);
  formData.append('targetLang', targetLang);
  formData.append('mode', 'team');
  formData.append('projectId', String(projectId));
  return api.upload<{ projectId: number; chapterCount: number; documentId: number; documentName: string; message: string }>(
    `/user/documents/upload?projectId=${projectId}`,
    formData,
  );
},
```

---

## 4. 现有接口需确认/补充返回字段

### 2.1 获取章节详情

| 字段 | 值 |
|---|---|
| 方法 | `GET` |
| 路径 | `/v1/collab/chapters/{chapterId}` |
| 前端调用 | `collabApi.getChapter(chapterId)` |

### 需要确认返回的 JSON 中包含以下字段

| 字段 | 类型 | 必须 | 说明 |
|---|---|---|---|
| `sourceText` | string \| null | 是 | 章节原文内容。工作台原文面板依赖此字段显示/编辑原文 |
| `translatedText` | string \| null | 是 | 章节译文内容。工作台编辑器依赖此字段预填已保存的译文 |
| `id` | number | 是 | 章节 ID |
| `chapterNumber` | number | 是 | 章节号 |
| `title` | string | 是 | 章节标题 |
| `status` | string | 是 | 状态（UNASSIGNED/TRANSLATING/SUBMITTED/REVIEWING/APPROVED/REJECTED/COMPLETED） |
| `progress` | number | 是 | 进度百分比 |
| `assigneeId` | number \| null | 是 | 译者用户 ID |
| `assigneeName` | string \| null | 是 | 译者用户名 |
| `reviewerId` | number \| null | 是 | 审者用户 ID |
| `reviewerName` | string \| null | 是 | 审者用户名 |

### 注意事项

当前前端工作台已兼容 `sourceText` 为空的情况——如果后端没返回原文，用户可以在工作台的原文面板手动粘贴。但建议后端在章节创建时存储原文。

---

## 5. 已存在、后端需确保可用的接口

以下接口前端已调用，后端需确认已实现：

| 方法 | 路径 | 功能 | 前端调用方 |
|---|---|---|---|
| `GET` | `/v1/collab/chapters/{chapterTaskId}/comments` | 获取章节评论列表 | 工作台、协作页 |
| `POST` | `/v1/collab/chapters/{chapterTaskId}/comments` | 创建评论（支持 `sourceText` 锚定原文） | 工作台、协作页 |
| `PUT` | `/v1/collab/chapters/{chapterId}/submit` | 提交译文（body: `{ translatedText: string }`） | 工作台提交按钮 |
| `GET` | `/v1/collab/projects/{projectId}/chapters` | 获取章节列表 | 协作页章节表格 |
| `POST` | `/v1/collab/join?inviteCode=xxx` | 通过邀请码加入项目 | 加入项目 Modal |

### 评论接口补充

`POST /v1/collab/chapters/{chapterTaskId}/comments` 请求体支持：

```json
{
  "content": "评论内容",
  "sourceText": "可选：锚定的原文片段"
}
```

---

## 6. 邀请码相关后端逻辑要点

### 4.1 邀请码生成规则

- 建议 8 位大写英文字母 + 数字随机组合，如 `A7XK2M9P`
- 确保唯一性（可加数据库唯一索引或查重）

### 4.2 邀请码校验（加入项目时）

`POST /v1/collab/join?inviteCode=xxx` 需校验：

1. 邀请码是否存在
2. 是否已过期（`expiresAt < now()`）
3. 对应的关联项目是否仍存在且状态为 `ACTIVE`
4. 校验通过后将当前用户加入项目成员表，角色默认 `TRANSLATOR`

### 4.3 过期清理

- 可定时任务清理过期邀请码，或在查询时加 `WHERE expires_at > NOW()` 条件
- 过期邀请码在前端会提示"邀请码已过期"
