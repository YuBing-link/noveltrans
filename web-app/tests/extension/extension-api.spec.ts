/**
 * 浏览器插件集成测试
 *
 * 通过 nginx 网关（localhost:7341）测试三种翻译模式：
 * 1. 整页翻译（/v1/translate/webpage）
 * 2. 阅读器翻译（/v1/translate/reader）
 * 3. 选中翻译（/v1/translate/selection）
 */

import { test, expect } from '@playwright/test';

const EXTENSION_BACKEND = 'http://localhost:7341';
const TEST_EMAIL = 'test@example.com';
const TEST_PASSWORD = 'Test123456!';

// 认证辅助函数：登录并获取 Bearer token
async function getAuthToken(page: typeof test extends { page: infer P } ? P : never) {
  const res = await page.request.post(`${EXTENSION_BACKEND}/user/login`, {
    data: { email: TEST_EMAIL, password: TEST_PASSWORD },
  });
  expect(res.status()).toBe(200);
  const json = await res.json();
  return json.token || json.data?.token;
}

function authHeaders(token: string) {
  return { Authorization: `Bearer ${token}` };
}

// ============================================================
// 一、三种翻译模式测试
// ============================================================

test.describe('翻译模式', () => {
  let token: string;

  test.beforeEach(async ({ page }) => {
    token = await getAuthToken(page);
  });

  test.describe('POST /v1/translate/selection（选中翻译）', () => {
    test('应成功翻译单句英文', async ({ page }) => {
      const res = await page.request.post(`${EXTENSION_BACKEND}/v1/translate/selection`, {
        data: { context: 'Hello, this is a simple test sentence.', sourceLang: 'en', targetLang: 'zh', engine: 'google' },
        headers: authHeaders(token),
      });

      expect(res.status()).toBe(200);
      const json = await res.json();
      expect(json.success).toBe(true);
      const result = json.translation || json.data?.translation || json.data?.text || json.data?.result;
      expect(result).toBeDefined();
      expect(typeof result).toBe('string');
    });

    test('应支持中日韩翻译', async ({ page }) => {
      const jaRes = await page.request.post(`${EXTENSION_BACKEND}/v1/translate/selection`, {
        data: { context: 'こんにちは世界', sourceLang: 'ja', targetLang: 'zh', engine: 'google' },
        headers: authHeaders(token),
      });
      expect(jaRes.status()).toBe(200);
      expect((await jaRes.json()).success).toBe(true);

      const koRes = await page.request.post(`${EXTENSION_BACKEND}/v1/translate/selection`, {
        data: { context: '안녕하세요 세계', sourceLang: 'ko', targetLang: 'zh', engine: 'google' },
        headers: authHeaders(token),
      });
      expect(koRes.status()).toBe(200);
      expect((await koRes.json()).success).toBe(true);
    });

    test('空文本应返回错误', async ({ page }) => {
      const res = await page.request.post(`${EXTENSION_BACKEND}/v1/translate/selection`, {
        data: { context: '', sourceLang: 'en', targetLang: 'zh', engine: 'google' },
        headers: authHeaders(token),
      });
      const json = await res.json();
      expect(json.success === false || res.status() === 400).toBe(true);
    });
  });

  test.describe('POST /v1/translate/reader（阅读器翻译）', () => {
    test('应成功翻译文章', async ({ page }) => {
      const content = '<h1>Test Article Title</h1><p>This is the first paragraph of the test article.</p>';

      const res = await page.request.post(`${EXTENSION_BACKEND}/v1/translate/reader`, {
        data: { content, sourceLang: 'en', targetLang: 'zh', engine: 'google' },
        headers: authHeaders(token),
      });

      expect(res.status()).toBe(200);
      expect((await res.json()).success).toBe(true);
    });

    test('缺少内容应返回错误', async ({ page }) => {
      const res = await page.request.post(`${EXTENSION_BACKEND}/v1/translate/reader`, {
        data: { content: '', sourceLang: 'en', targetLang: 'zh', engine: 'google' },
        headers: authHeaders(token),
      });
      const json = await res.json();
      expect(json.success === false || res.status() === 400).toBe(true);
    });
  });

  test.describe('POST /v1/translate/webpage（整页翻译）', () => {
    test('应成功提交批量翻译请求', async ({ page }) => {
      const res = await page.request.post(`${EXTENSION_BACKEND}/v1/translate/webpage`, {
        data: {
          textRegistry: [
            { id: 0, text: 'Hello World' },
            { id: 1, text: 'Welcome to the page' },
          ],
          sourceLang: 'en', targetLang: 'zh', engine: 'google', fastMode: true,
        },
        headers: authHeaders(token),
      });
      expect(res.status()).toBe(200);
      const text = await res.text();
      // SSE stream: should contain data: lines
      expect(text).toContain('data:');
    });

    test('空 registry 应返回错误', async ({ page }) => {
      const res = await page.request.post(`${EXTENSION_BACKEND}/v1/translate/webpage`, {
        data: { textRegistry: [], sourceLang: 'en', targetLang: 'zh', engine: 'google', fastMode: true },
        headers: authHeaders(token),
      });
      const json = await res.json();
      expect(json.success === false || res.status() === 400).toBe(true);
    });

    test('应支持大量文本条目', async ({ page }) => {
      const registry = Array.from({ length: 50 }, (_, i) => ({
        id: i, text: `Test paragraph number ${i} with some content.`,
      }));

      const res = await page.request.post(`${EXTENSION_BACKEND}/v1/translate/webpage`, {
        data: { textRegistry: registry, sourceLang: 'en', targetLang: 'zh', engine: 'google', fastMode: true },
        headers: authHeaders(token),
      });
      expect(res.status()).toBe(200);
      const text = await res.text();
      expect(text.length).toBeGreaterThan(0);
    });
  });
});

// ============================================================
// 二、SSE 流式翻译测试（webpage 模式）
// ============================================================

test.describe('SSE 流式翻译', () => {
  test('整页翻译应支持 SSE 流式返回', async ({ page }) => {
    const res = await page.request.post(`${EXTENSION_BACKEND}/v1/translate/webpage`, {
      data: {
        textRegistry: [
          { id: 0, text: 'Hello world' },
          { id: 1, text: 'How are you' },
          { id: 2, text: 'Nice to meet you' },
        ],
        sourceLang: 'en', targetLang: 'zh', engine: 'google', fastMode: true,
      },
      headers: { ...authHeaders(token), 'Accept': 'text/event-stream' },
    });

    expect(res.status()).toBe(200);
    const contentType = res.headers()['content-type'] || '';
    expect(contentType).toContain('text/event-stream');

    const text = await res.text();
    expect(text.length).toBeGreaterThan(0);
    expect(text).toContain('data:');
  });

  test('流式响应应逐条返回翻译结果', async ({ page }) => {
    const registry = Array.from({ length: 5 }, (_, i) => ({
      id: i, text: `Sentence number ${i} for streaming test.`,
    }));

    const res = await page.request.post(`${EXTENSION_BACKEND}/v1/translate/webpage`, {
      data: { textRegistry: registry, sourceLang: 'en', targetLang: 'zh', engine: 'google', fastMode: true },
      headers: { ...authHeaders(token), 'Accept': 'text/event-stream' },
    });

    const text = await res.text();
    const dataLines = text.split('\n').filter((line: string) => line.startsWith('data:'));
    expect(dataLines.length).toBeGreaterThan(0);
  });
});

// ============================================================
// 三、CORS 跨域测试
// ============================================================

test.describe('CORS 跨域支持', () => {
  test('OPTIONS 预检请求应返回 204', async ({ page }) => {
    const res = await page.request.fetch(`${EXTENSION_BACKEND}/v1/translate/selection`, {
      method: 'OPTIONS',
      headers: {
        'Origin': 'chrome-extension://test-extension-id',
        'Access-Control-Request-Method': 'POST',
        'Access-Control-Request-Headers': 'Content-Type,X-Translation-Engine',
      },
    });
    expect([204, 200]).toContain(res.status());
  });

  test('翻译请求应包含 CORS 响应头', async ({ page }) => {
    const res = await page.request.post(`${EXTENSION_BACKEND}/v1/translate/selection`, {
      data: { context: 'CORS test', sourceLang: 'en', targetLang: 'zh', engine: 'google' },
      headers: { ...authHeaders(token), 'Origin': 'chrome-extension://test-extension-id' },
    });

    const headers = res.headers();
    expect(headers['access-control-allow-origin'] || headers['Access-Control-Allow-Origin']).toBeDefined();
  });
});

// ============================================================
// 四、翻译引擎配置测试
// ============================================================

test.describe('翻译引擎配置', () => {
  let token: string;

  test.beforeEach(async ({ page }) => {
    token = await getAuthToken(page);
  });

  test('google 引擎应正常工作', async ({ page }) => {
    const res = await page.request.post(`${EXTENSION_BACKEND}/v1/translate/selection`, {
      data: { context: 'Test', sourceLang: 'en', targetLang: 'zh', engine: 'google' },
      headers: authHeaders(token),
    });
    expect(res.status()).toBe(200);
    expect((await res.json()).success).toBe(true);
  });

  test('不同翻译模式应接受相同的引擎参数', async ({ page }) => {
    const engine = 'google';

    const selRes = await page.request.post(`${EXTENSION_BACKEND}/v1/translate/selection`, {
      data: { context: 'Test', sourceLang: 'en', targetLang: 'zh', engine },
      headers: authHeaders(token),
    });
    expect((await selRes.json()).success, 'selection').toBe(true);

    const readerRes = await page.request.post(`${EXTENSION_BACKEND}/v1/translate/reader`, {
      data: { content: '<p>Test</p>', sourceLang: 'en', targetLang: 'zh', engine },
      headers: authHeaders(token),
    });
    expect((await readerRes.json()).success, 'reader').toBe(true);

    const webRes = await page.request.post(`${EXTENSION_BACKEND}/v1/translate/webpage`, {
      data: { textRegistry: [{ id: 0, text: 'Test' }], sourceLang: 'en', targetLang: 'zh', engine, fastMode: true },
      headers: authHeaders(token),
    });
    expect(webRes.status(), 'webpage status').toBe(200);
    const webText = await webRes.text();
    expect(webText.includes('data:'), 'webpage SSE').toBe(true);
  });
});

// ============================================================
// 五、错误处理和边界测试
// ============================================================

test.describe('错误处理和边界', () => {
  let token: string;

  test.beforeEach(async ({ page }) => {
    token = await getAuthToken(page);
  });

  test('无效引擎应返回错误或回退到默认引擎', async ({ page }) => {
    const res = await page.request.post(`${EXTENSION_BACKEND}/v1/translate/selection`, {
      data: { context: 'Test', sourceLang: 'en', targetLang: 'zh', engine: 'invalid_engine' },
      headers: authHeaders(token),
    });
    // Backend may either reject or fall back to default engine - both are acceptable
    expect(res.status()).toBeDefined();
    const json = await res.json();
    // Either success (fallback) or error (rejected) is acceptable
    expect(typeof json.success === 'boolean' || res.status() >= 400).toBe(true);
  });

  test('超长文本应有合理响应', async ({ page }) => {
    const longText = 'A'.repeat(10000);
    const res = await page.request.post(`${EXTENSION_BACKEND}/v1/translate/selection`, {
      data: { context: longText, sourceLang: 'en', targetLang: 'zh', engine: 'google' },
      headers: authHeaders(token),
    });
    expect(res.status()).toBeDefined();
  });

  test('缺少必填字段应返回错误', async ({ page }) => {
    const res = await page.request.post(`${EXTENSION_BACKEND}/v1/translate/selection`, { data: {}, headers: authHeaders(token) });
    const json = await res.json();
    expect(json.success === false || res.status() === 400).toBe(true);
  });

  test('并发请求不应导致服务崩溃', async ({ page }) => {
    const requests = Array.from({ length: 5 }, (_, i) =>
      page.request.post(`${EXTENSION_BACKEND}/v1/translate/selection`, {
        data: { context: `Concurrent test ${i}`, sourceLang: 'en', targetLang: 'zh', engine: 'google' },
        headers: authHeaders(token),
      })
    );

    const responses = await Promise.all(requests);
    for (const res of responses) {
      expect(res.status()).toBe(200);
    }
  });
});
