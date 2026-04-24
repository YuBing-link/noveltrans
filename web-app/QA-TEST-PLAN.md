# NovelTrans Web App 前端接口测试计划

> 生成时间：2026-04-23
> 项目：`web-app/` — NovelTrans 前端 React 应用
> 分支：`main`

---

## 一、前端接口总览

前端共调用 **50+ 个后端接口**，分布在 11 个 API 模块中：

| 模块 | 文件 | 接口数量 | 功能域 |
|------|------|---------|--------|
| Auth | `src/api/auth.ts` | 8 | 注册、登录、密码重置、Token 刷新 |
| User | `src/api/user.ts` | 3 | 统计、配额、翻译历史 |
| Document | `src/api/documents.ts` | 6 | 文档上传、列表、下载、取消、重试 |
| Translation | `src/api/translate.ts` | 11 | 文本翻译、SSE 流式、文档流式翻译 |
| Glossary | `src/api/glossaries.ts` | 7 | 术语表 CRUD、CSV 导入导出 |
| Collab | `src/api/collab.ts` | 20 | 协作项目、章节、成员、评论、邀请码 |
| API Keys | `src/api/apiKeys.ts` | 4 | API Key 管理 |
| Preferences | `src/api/preferences.ts` | 2 | 用户偏好设置 |
| Platform | `src/api/platform.ts` | 1 | 平台统计 |
| RAG | `src/api/rag.ts` | 1 | RAG 增强翻译 |
| External | `src/api/external.ts` | 4 | 外部翻译引擎 |
| Device | `src/api/device.ts` | 2 | 设备注册、Token 获取 |

---

## 二、前端路由与页面对应关系

| 路由 | 组件 | 是否需登录 | 主要功能 |
|------|------|-----------|---------|
| `/` | `HomePage` | 否 | 文本翻译（SSE 流式） |
| `/login` | `LoginPage` | 否 | 登录表单 |
| `/register` | `RegisterPage` | 否 | 注册 + 邮箱验证 |
| `/forgot-password` | `ForgotPasswordPage` | 否 | 通过邮箱验证码重置密码 |
| `/documents` | `DocumentPage` | 是 | 文档上传、列表、下载、取消、重试 |
| `/history` | `HistoryPage` | 是 | 翻译历史（分页+筛选） |
| `/glossary` | `GlossaryPage` | 是 | 术语表管理、CSV 导入导出 |
| `/collab` | `CollabPage` | 是 | 协作项目管理、邀请码、成员管理 |
| `/collab/workspace` | `CollabWorkspace` | 是 | 章节翻译编辑器（原文+译文+AI翻译） |
| `/user/*` | `UserCenterPage` | 是 | 用户中心（Profile/统计/配额/API Keys/偏好） |
| `/settings` | `SettingsPage` | 是 | 主题切换、修改密码 |
| `/about` | `AboutPage` | 否 | 关于页面 |
| `/help` | `HelpPage` | 否 | 常见问题 |
| `/privacy` | `PrivacyPage` | 否 | 隐私政策 |
| `/terms` | `TermsPage` | 否 | 服务条款 |
| `*` | `NotFoundPage` | 否 | 404 页面 |

---

## 三、核心用户流程梳理

### 3.1 认证流程

```
注册：POST /user/send-code（发验证码）
  → POST /user/register（提交注册信息）
  → Token 保存到 localStorage(authToken)

登录：POST /user/login（邮箱+密码）
  → Token 保存到 localStorage(authToken)

自动登录：AuthContext 从 localStorage 读取 authToken + userInfo
  → 调用 GET /user/profile 验证 Token 有效性
  → 401 自动清除 localStorage 并跳转 /login

忘记密码：POST /user/send-reset-code
  → POST /user/reset-password（邮箱+验证码+新密码）

登出：POST /user/logout → 清除 localStorage
```

### 3.2 文本翻译流程

```
用户输入源文本 → 选择源/目标语言和引擎模式
  → POST /v1/translate/text/stream（SSE 流式返回）
  → 实时显示翻译结果
  → 页面加载时调用 GET /user/quota 检查配额
```

### 3.3 文档翻译流程

```
选择源/目标语言和模式（fast/expert）
  → POST /user/documents/upload（multipart/form-data）
  → GET /user/documents（3 秒轮询，跟踪翻译状态）
  → 完成后 GET /user/documents/:docId/download（下载翻译文件）
  → 失败文档 POST /user/documents/:docId/retry
  → 进行中文档 POST /user/documents/:docId/cancel
```

### 3.4 协作项目流程

```
创建项目：POST /v1/collab/projects
加入项目：输入邀请码 → POST /v1/collab/join?inviteCode=xxx
生成邀请码（OWNER）：POST /v1/collab/projects/:id/invite-code
邀请成员（OWNER）：POST /v1/collab/projects/:id/invite（邮箱邀请）

添加章节（OWNER）：
  - 手动：POST /v1/collab/projects/:id/chapters
  - 上传小说：POST /user/documents/upload?projectId=:id（multipart）

分配译者：PUT /v1/collab/chapters/:id/assign
译者工作台：
  → GET /v1/collab/chapters/:chapterId（获取原文+预填译文）
  → 编辑译文（可调用 AI 翻译辅助：POST /v1/translate/text/stream）
  → 自动保存草稿到 localStorage（30 秒间隔 + 页面卸载时）
  → 提交：PUT /v1/collab/chapters/:id/submit
审校审核：
  → PUT /v1/collab/chapters/:id/review（批准/驳回+评论）
评论功能：
  → GET/POST /v1/collab/chapters/:id/comments（支持原文锚定）
  → PUT/DELETE /v1/collab/comments/:id（解决/删除评论）
```

---

## 四、测试用例清单

### P0：核心流程（必须测）

| ID | 用例名称 | 测试页面 | 操作步骤 | 预期结果 |
|----|---------|---------|---------|---------|
| TC-01 | 用户注册 | `/register` | 输入有效邮箱 → 获取验证码 → 输入验证码+密码+用户名 → 提交 | 注册成功，自动登录，Token 已保存 |
| TC-02 | 用户登录 | `/login` | 输入已注册邮箱+密码 → 提交 | 登录成功，跳转到首页 |
| TC-03 | 401 自动跳转 | 任意需登录页面 | 手动清除 localStorage 中的 Token → 刷新页面 | 自动跳转到 /login |
| TC-04 | 文本翻译（SSE） | `/` | 输入一段日文 → 选择 ja→zh → 点击翻译 | SSE 流式返回翻译结果，实时显示 |
| TC-05 | 文档上传（fast 模式） | `/documents` | 选择 .txt 文件 → 选择语言 → 上传 | 上传成功，文档出现在列表，状态变为处理中 → 完成后可下载 |
| TC-06 | 协作项目创建 | `/collab` | 输入项目名称+描述+语言对 → 创建 | 项目创建成功，出现在项目列表中 |
| TC-07 | 生成邀请码 | `/collab` | 进入项目详情 → 点击生成邀请码 | 显示 8 位邀请码和过期时间 |
| TC-08 | 通过邀请码加入项目 | `/collab` | 输入有效邀请码 → 提交 | 加入成功，出现在项目成员列表中 |
| TC-09 | 协作工作台编辑+提交 | `/collab/workspace` | 打开已分配章节 → 编辑译文 → 点击提交 | 提交成功，章节状态变为 SUBMITTED |
| TC-10 | 审校审核章节 | `/collab/workspace` | 打开已提交章节 → 点击批准/驳回 | 审核成功，状态变为 APPROVED/REJECTED |
| TC-11 | 文档上传到已有项目（带 projectId） | `/collab` | 在已有项目中上传小说文件 | 章节添加到项目中，不创建新项目 |

### P1：重要功能（建议测）

| ID | 用例名称 | 测试页面 | 操作步骤 | 预期结果 |
|----|---------|---------|---------|---------|
| TC-12 | 章节分配 | `/collab` | 选择未分配章节 → 选择译者 → 分配 | 分配成功，译者出现在该章节中 |
| TC-13 | 章节评论 | `/collab/workspace` | 在原文中选中文字 → 添加评论 → 提交 | 评论显示在侧边栏 |
| TC-14 | 术语表 CRUD | `/glossary` | 新建条目 → 编辑 → 删除 | 列表实时更新 |
| TC-15 | 术语表 CSV 导入 | `/glossary` | 下载模板 → 填写 CSV → 上传导入 | 导入成功，条目出现在列表 |
| TC-16 | 翻译历史筛选 | `/history` | 切换 全部/已完成/处理中/失败 标签 | 列表按状态正确筛选 |
| TC-17 | 用户 Profile 更新 | `/user` | 修改用户名 → 保存 | 更新成功，显示新用户名 |
| TC-18 | API Key 管理 | `/user/api-keys` | 创建 Key → 复制 → 重置 → 删除 | 各操作正常 |
| TC-19 | 文档取消翻译 | `/documents` | 对正在处理的文档点击取消 | 取消成功，状态变为取消 |
| TC-20 | 文档重试翻译 | `/documents` | 对失败文档点击重试 | 重试成功，重新进入处理状态 |

### P2：辅助功能（有空测）

| ID | 用例名称 | 测试页面 | 操作步骤 | 预期结果 |
|----|---------|---------|---------|---------|
| TC-21 | 密码找回 | `/forgot-password` | 输入邮箱 → 获取验证码 → 输入新密码 → 提交 | 密码重置成功 |
| TC-22 | 修改密码 | `/settings` | 输入旧密码+新密码 → 提交 | 修改成功 |
| TC-23 | 偏好设置 | `/user/preferences` | 切换术语表启用/缓存启用 → 保存 | 设置生效 |
| TC-24 | 邀请码过期提示 | `/collab` | 使用已过期的邀请码加入 | 提示"邀请码已过期" |
| TC-25 | 邀请码重复使用 | `/collab` | 使用已被使用过的邀请码加入 | 提示"邀请码已被使用" |



---

## 五、测试环境要求

| 项目 | 要求 |
|------|------|
| 后端服务 | Spring Boot 运行在 `http://localhost:8080` |
| 翻译微服务 | Python FastAPI 运行在 `http://localhost:8000` |
| 数据库 | MySQL `novel_translator`，包含 `collab_invite_code` 表 |
| 前端开发服务器 | `cd web-app && npm run dev`（通常 `http://localhost:5173`） |
| 测试账号 | 至少 2 个已注册账号（用于测试协作功能） |
| 测试文件 | .txt / .epub / .docx 小说文件各一份 |

---

## 七、测试优先级说明

- **P0（阻塞发布）**：核心用户路径，不通过则功能不可用
- **P1（建议修复）**：重要辅助功能，影响用户体验但不阻塞核心路径
- **P2（有空处理）**：边缘场景和优化项，可延后处理
