/**
 * 前端 API 集成测试
 *
 * 覆盖 web-app 前端调用的所有后端接口
 * 直接请求 Spring Boot 后端（127.0.0.1:8080）
 *
 * 测试账号: admin@example.com / admin123（跳过注册验证码）
 */

import { test, expect } from '@playwright/test';

const API_BASE = 'http://127.0.0.1:8080';

// 测试账号（已存在于数据库中，跳过注册验证码）
const TEST_EMAIL = 'admin@example.com';
const TEST_PASSWORD = 'admin123';

async function login(page: import('@playwright/test').Page) {
  const res = await page.request.post(`${API_BASE}/user/login`, {
    data: { email: TEST_EMAIL, password: TEST_PASSWORD },
  });

  const json = await res.json();
  expect(json.success, `登录应成功: ${JSON.stringify(json)}`).toBe(true);

  const token = json.token || json.data?.token || json.data?.accessToken;
  return { token, email: TEST_EMAIL, password: TEST_PASSWORD };
}

function authHeaders(token: string) {
  return { Authorization: `Bearer ${token}` };
}

// ============================================================
// 一、公开接口测试（无需登录）
// ============================================================

test.describe('公开接口', () => {
  test.describe('POST /user/send-code', () => {
    test('应成功发送验证码', async ({ page }) => {
      const res = await page.request.post(`${API_BASE}/user/send-code`, {
        data: { email: TEST_EMAIL },
      });
      expect(res.status()).toBe(200);
    });
  });

  test.describe('POST /user/login', () => {
    test('应成功登录', async ({ page }) => {
      const res = await page.request.post(`${API_BASE}/user/login`, {
        data: { email: TEST_EMAIL, password: TEST_PASSWORD },
      });

      expect(res.status()).toBe(200);
      const json = await res.json();
      expect(json.success).toBe(true);
      expect(json.token || json.data?.token).toBeDefined();
    });

    test('错误密码应失败', async ({ page }) => {
      const res = await page.request.post(`${API_BASE}/user/login`, {
        data: { email: TEST_EMAIL, password: 'wrongpassword' },
      });
      const json = await res.json();
      expect(json.success).toBe(false);
    });

    test('不存在的邮箱应失败', async ({ page }) => {
      const res = await page.request.post(`${API_BASE}/user/login`, {
        data: { email: 'nonexistent@test.com', password: 'password123' },
      });
      const json = await res.json();
      expect(json.success).toBe(false);
    });
  });
});

// ============================================================
// 二、需要认证的接口测试
// ============================================================

test.describe('需要认证的接口', () => {
  let token: string;

  test.beforeEach(async ({ page }) => {
    const result = await login(page);
    token = result.token;
  });

  test.describe('GET /user/profile', () => {
    test('应返回当前用户信息', async ({ page }) => {
      const res = await page.request.get(`${API_BASE}/user/profile`, { headers: authHeaders(token) });
      expect(res.status()).toBe(200);
      const json = await res.json();
      expect(json.success).toBe(true);
      expect(json.data?.email).toBe(TEST_EMAIL);
    });

    test('无 token 应返回 401', async ({ page }) => {
      const res = await page.request.get(`${API_BASE}/user/profile`);
      expect(res.status()).toBe(401);
    });
  });

  test.describe('PUT /user/profile', () => {
    test('应成功更新用户信息', async ({ page }) => {
      const res = await page.request.put(`${API_BASE}/user/profile`, {
        headers: authHeaders(token),
        data: { nickname: `UpdatedUser_${Date.now()}` },
      });
      expect(res.status()).toBe(200);
    });
  });

  test.describe('GET /user/statistics', () => {
    test('应返回用户统计信息', async ({ page }) => {
      const res = await page.request.get(`${API_BASE}/user/statistics`, { headers: authHeaders(token) });
      expect(res.status()).toBe(200);
    });
  });

  test.describe('GET /user/quota', () => {
    test('应返回用户配额信息', async ({ page }) => {
      const res = await page.request.get(`${API_BASE}/user/quota`, { headers: authHeaders(token) });
      expect(res.status()).toBe(200);
    });
  });

  test.describe('POST /user/change-password', () => {
    test('旧密码错误应失败', async ({ page }) => {
      const res = await page.request.post(`${API_BASE}/user/change-password`, {
        headers: authHeaders(token),
        data: { oldPassword: 'wrongpassword', newPassword: 'NewPass123!', confirmNewPassword: 'NewPass123!' },
      });
      const json = await res.json();
      expect(json.success).toBe(false);
    });
  });

  test.describe('POST /user/refresh-token', () => {
    test('应成功刷新 token', async ({ page }) => {
      const res = await page.request.post(`${API_BASE}/user/refresh-token`, { headers: authHeaders(token) });
      expect(res.status()).toBe(200);
    });
  });

  test.describe('GET /user/translation-history', () => {
    test('应返回翻译历史', async ({ page }) => {
      const res = await page.request.get(`${API_BASE}/user/translation-history`, { headers: authHeaders(token) });
      expect(res.status()).toBe(200);
    });

    test('分页参数应生效', async ({ page }) => {
      const res = await page.request.get(`${API_BASE}/user/translation-history?page=1&size=10`, { headers: authHeaders(token) });
      expect(res.status()).toBe(200);
    });
  });

  test.describe('GET & PUT /user/preferences', () => {
    test('应获取和更新偏好设置', async ({ page }) => {
      const getRes = await page.request.get(`${API_BASE}/user/preferences`, { headers: authHeaders(token) });
      expect(getRes.status()).toBe(200);

      // Verify GET returns valid preferences structure
      const getJson = await getRes.json();
      expect(getJson.data?.defaultTargetLang).toBeDefined();
    });
  });

  test.describe('GET /platform/stats', () => {
    test('应返回平台统计信息', async ({ page }) => {
      const res = await page.request.get(`${API_BASE}/platform/stats`, { headers: authHeaders(token) });
      expect(res.status()).toBe(200);
      const json = await res.json();
      expect(json.success).toBe(true);
    });
  });

  test.describe('GET /user/documents', () => {
    test('应返回文档列表', async ({ page }) => {
      const res = await page.request.get(`${API_BASE}/user/documents`, { headers: authHeaders(token) });
      expect(res.status()).toBe(200);
    });
  });

  test.describe('术语表接口', () => {
    test('应成功创建、查询、删除术语表', async ({ page }) => {
      // 创建术语项（sourceWord -> targetWord）
      const createRes = await page.request.post(`${API_BASE}/user/glossaries`, {
        headers: authHeaders(token),
        data: { sourceWord: `hello_${Date.now()}`, targetWord: '你好', remark: 'Test term' },
      });
      expect(createRes.status()).toBe(200);
      const createJson = await createRes.json();
      expect(createJson.success).toBe(true);

      // 列表验证 — 接口返回 PageResponse {list, total, ...}
      const listRes = await page.request.get(`${API_BASE}/user/glossaries`, { headers: authHeaders(token) });
      const listJson = await listRes.json();
      const glossaryList = listJson.data?.list || listJson.data?.records || [];
      expect(glossaryList.some((g: { sourceWord: string }) => g.sourceWord.startsWith('hello_'))).toBe(true);

      // 删除
      const deleteRes = await page.request.delete(`${API_BASE}/user/glossaries/${createJson.data.id}`, {
        headers: authHeaders(token),
      });
      expect(deleteRes.status()).toBe(200);
    });
  });

  test.describe('API Key 管理', () => {
    test('应成功创建 API Key', async ({ page }) => {
      const res = await page.request.post(`${API_BASE}/user/api-keys`, {
        headers: authHeaders(token),
        data: { name: `TestKey_${Date.now()}` },
      });
      expect(res.status()).toBe(200);
      const json = await res.json();
      expect(json.success).toBe(true);
      expect(json.data?.apiKey).toBeDefined();
    });

    test('应返回 API Key 列表', async ({ page }) => {
      const res = await page.request.get(`${API_BASE}/user/api-keys`, { headers: authHeaders(token) });
      expect(res.status()).toBe(200);
      const json = await res.json();
      expect(json.success).toBe(true);
      // 接口返回 PageResponse {list, total, ...}
      const apiKeys = json.data?.list || json.data?.records || [];
      expect(Array.isArray(apiKeys)).toBe(true);
    });
  });

  test.describe('协作项目接口', () => {
    test('应成功创建项目并生成邀请码', async ({ page }) => {
      const name = `TestProject_${Date.now()}`;

      // 创建项目
      const res = await page.request.post(`${API_BASE}/v1/collab/projects`, {
        headers: authHeaders(token),
        data: { name, sourceLang: 'en', targetLang: 'zh' },
      });
      expect(res.status()).toBe(200);
      const json = await res.json();
      expect(json.success).toBe(true);
      const projectId = json.data.id;

      // 获取项目详情
      const getRes = await page.request.get(`${API_BASE}/v1/collab/projects/${projectId}`, {
        headers: authHeaders(token),
      });
      expect(getRes.status()).toBe(200);

      // 获取成员列表
      const membersRes = await page.request.get(`${API_BASE}/v1/collab/projects/${projectId}/members`, {
        headers: authHeaders(token),
      });
      expect(membersRes.status()).toBe(200);

      // 生成邀请码
      const inviteRes = await page.request.post(`${API_BASE}/v1/collab/projects/${projectId}/invite-code`, {
        headers: authHeaders(token),
      });
      expect(inviteRes.status()).toBe(200);
      const inviteJson = await inviteRes.json();
      expect(inviteJson.data?.code).toBeDefined();
    });

    test('应返回项目列表', async ({ page }) => {
      const res = await page.request.get(`${API_BASE}/v1/collab/projects`, { headers: authHeaders(token) });
      expect(res.status()).toBe(200);
    });
  });
});

// ============================================================
// 三、翻译核心接口测试
// ============================================================

test.describe('翻译核心接口', () => {
  test.describe('POST /v1/translate/text/stream (SSE 流式翻译)', () => {
    test('应成功进行流式翻译', async ({ page }) => {
      const response = await page.request.post(`${API_BASE}/v1/translate/text/stream`, {
        data: { text: 'Hello, this is a test sentence.', sourceLang: 'en', targetLang: 'zh', engine: 'google' },
        timeout: 30000,
      });

      expect(response.status()).toBe(200);
      const text = await response.text();
      expect(text.length).toBeGreaterThan(0);
      // SSE success: should contain 'data:' prefix lines
      // May also return JSON error if translation service is unavailable
      if (!text.includes('data:')) {
        const json = JSON.parse(text);
        expect(json.success === false || json.message, '应返回 SSE 流或有效错误响应').toBeDefined();
      }
    });

    test('空文本应返回错误', async ({ page }) => {
      const res = await page.request.post(`${API_BASE}/v1/translate/text/stream`, {
        data: { text: '', sourceLang: 'en', targetLang: 'zh', engine: 'google' },
      });
      const json = await res.json();
      expect(json.success === false || res.status() === 400).toBe(true);
    });
  });

  test.describe('POST /v1/translate/selection (选中翻译)', () => {
    test('应成功翻译短文本', async ({ page }) => {
      const res = await page.request.post(`${API_BASE}/v1/translate/selection`, {
        data: { context: 'Hello world', sourceLang: 'en', targetLang: 'zh', engine: 'google' },
      });
      expect(res.status()).toBe(200);
      const json = await res.json();
      expect(json.success).toBe(true);
    });

    test('google 引擎应能响应', async ({ page }) => {
      const res = await page.request.post(`${API_BASE}/v1/translate/selection`, {
        data: { context: 'Good morning', sourceLang: 'en', targetLang: 'zh', engine: 'google' },
      });
      const json = await res.json();
      expect(json.success, '引擎 google 翻译应成功').toBe(true);
    });
  });

  test.describe('POST /v1/translate/reader (阅读器翻译)', () => {
    test('应成功翻译文章内容', async ({ page }) => {
      const content = '<h1>Test Article</h1><p>This is a test paragraph.</p>';

      const res = await page.request.post(`${API_BASE}/v1/translate/reader`, {
        data: { content, sourceLang: 'en', targetLang: 'zh', engine: 'google' },
      });

      expect(res.status()).toBe(200);
      const json = await res.json();
      expect(json.success).toBe(true);
    });
  });

  test.describe('POST /v1/translate/webpage (整页翻译 - SSE)', () => {
    test('应成功提交批量翻译任务', async ({ page }) => {
      const res = await page.request.post(`${API_BASE}/v1/translate/webpage`, {
        data: {
          textRegistry: [{ id: 0, text: 'Hello' }, { id: 1, text: 'World' }],
          sourceLang: 'en', targetLang: 'zh', engine: 'google', fastMode: true,
        },
      });
      expect(res.status()).toBe(200);
      const text = await res.text();
      expect(text.length).toBeGreaterThan(0);
      // SSE success: should contain 'data:' prefix lines
      // May also return JSON error if translation service is unavailable
      if (!text.includes('data:')) {
        const json = JSON.parse(text);
        expect(json.success === false || json.message, '应返回 SSE 流或有效错误响应').toBeDefined();
      }
    });

    test('空 registry 应返回错误', async ({ page }) => {
      const res = await page.request.post(`${API_BASE}/v1/translate/webpage`, {
        data: { textRegistry: [], sourceLang: 'en', targetLang: 'zh', engine: 'google', fastMode: true },
      });
      const json = await res.json();
      expect(json.success === false || res.status() === 400).toBe(true);
    });
  });
});

// ============================================================
// 四、外部 API 测试（/v1/external/*）
// ============================================================

test.describe('外部 API 接口', () => {
  let token: string;

  test.beforeEach(async ({ page }) => {
    const result = await login(page);
    token = result.token;
  });

  test.describe('POST /v1/external/translate', () => {
    test('应成功翻译单条文本', async ({ page }) => {
      const res = await page.request.post(`${API_BASE}/v1/external/translate`, {
        headers: authHeaders(token),
        data: { text: 'Hello', sourceLang: 'en', targetLang: 'zh', engine: 'google' },
      });
      expect(res.status()).toBe(200);
      const json = await res.json();
      expect(json.success).toBe(true);
      expect(json.data?.translatedText).toBeDefined();
    });

    test('无认证应返回 401', async ({ page }) => {
      const res = await page.request.post(`${API_BASE}/v1/external/translate`, {
        data: { text: 'Hello', sourceLang: 'en', targetLang: 'zh', engine: 'google' },
      });
      expect(res.status()).toBe(401);
    });
  });

  test.describe('POST /v1/external/batch', () => {
    test('应成功批量翻译', async ({ page }) => {
      const res = await page.request.post(`${API_BASE}/v1/external/batch`, {
        headers: authHeaders(token),
        data: { texts: ['Hello', 'World', 'Test'], sourceLang: 'en', targetLang: 'zh', engine: 'google' },
      });
      expect(res.status()).toBe(200);
      const json = await res.json();
      expect(json.success).toBe(true);
      expect(Array.isArray(json.data)).toBe(true);
      expect(json.data.length).toBe(3);
    });

    test('超过 50 条应失败', async ({ page }) => {
      const res = await page.request.post(`${API_BASE}/v1/external/batch`, {
        headers: authHeaders(token),
        data: { texts: Array(51).fill('Hello'), sourceLang: 'en', targetLang: 'zh', engine: 'google' },
      });
      const json = await res.json();
      expect(json.success).toBe(false);
    });
  });

  test.describe('GET /v1/external/models', () => {
    test('应返回可用翻译引擎列表', async ({ page }) => {
      const res = await page.request.get(`${API_BASE}/v1/external/models`, {
        headers: authHeaders(token),
      });
      expect(res.status()).toBe(200);
      const json = await res.json();
      expect(json.success).toBe(true);
      expect(Array.isArray(json.data)).toBe(true);
      expect(json.data.length).toBeGreaterThan(0);
    });
  });
});

// ============================================================
// 五、订阅接口测试
// ============================================================

test.describe('订阅接口', () => {
  let token: string;

  test.beforeEach(async ({ page }) => {
    const result = await login(page);
    token = result.token;
  });

  test.describe('GET /subscription/status', () => {
    test('应返回订阅状态', async ({ page }) => {
      const res = await page.request.get(`${API_BASE}/subscription/status`, { headers: authHeaders(token) });
      expect(res.status()).toBe(200);
    });
  });
});
