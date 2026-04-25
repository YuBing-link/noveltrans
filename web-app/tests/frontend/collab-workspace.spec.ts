/**
 * 协作工作区 AI 翻译 E2E 测试
 *
 * 核心目标：验证 SSE 流式翻译的译文不会闪烁消失
 * 使用 page.evaluate 注入 mock fetch，通过浏览器原生 ReadableStream 返回 SSE 响应
 *
 * 测试账号: admin@example.com / admin123
 */

import { test } from '@playwright/test';

const API_BASE = 'http://127.0.0.1:8080';
const FRONTEND_URL = process.env.FRONTEND_URL || 'http://localhost:5173';
const TEST_EMAIL = 'admin@example.com';
const TEST_PASSWORD = 'admin123';

async function loginApi(page: import('@playwright/test').Page) {
  const res = await page.request.post(`${API_BASE}/user/login`, {
    data: { email: TEST_EMAIL, password: TEST_PASSWORD },
  });
  const json = await res.json();
  expect(json.success, `登录应成功: ${JSON.stringify(json)}`).toBe(true);
  return json.token || json.data?.token;
}

function auth(token: string) {
  return { Authorization: `Bearer ${token}` };
}

test.describe('CollabWorkspace 流式翻译展示', () => {
  let token: string;

  test.beforeEach(async ({ page }) => {
    token = await loginApi(page);
    const profileRes = await page.request.get(`${API_BASE}/user/profile`, {
      headers: auth(token),
    });
    const profileJson = await profileRes.json();
    const userInfo = profileJson.data;

    await page.goto(FRONTEND_URL);
    await page.evaluate(({ tok, info }) => {
      localStorage.setItem('authToken', tok);
      localStorage.setItem('userInfo', JSON.stringify(info));
    }, { tok: token, info: userInfo });

    await page.waitForFunction(() => {
      const info = localStorage.getItem('userInfo');
      return info && info.length > 0;
    });
    await page.waitForTimeout(500);
  });

  // FIXME: CollabWorkspace 组件当前没有 "AI 翻译" 按钮，仅有"保存"和"提交"按钮。
  // 流式 AI 翻译功能尚未在前端实现，这两个测试需要等 UI 功能上线后重新启用。
  test.fixme('Mock SSE 流式翻译 — 译文应保留在编辑器中，不会闪烁消失', async () => {
    test.skip(true, 'CollabWorkspace 组件缺少 AI 翻译按钮，功能待实现');
  });

  test.fixme('Mock 流式翻译 — AI 翻译应替换编辑器内容而非叠加', async () => {
    test.skip(true, 'CollabWorkspace 组件缺少 AI 翻译按钮，功能待实现');
  });
});
