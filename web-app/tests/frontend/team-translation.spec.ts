/**
 * 团队协作翻译模式 E2E 测试
 *
 * 覆盖 team 模式下的选中翻译接口（POST /v1/translate/selection）
 * 直接请求 Spring Boot 后端（127.0.0.1:8080）
 *
 * 测试用例：
 * 1. Team 模式翻译 - 验证 mode=team 能成功响应
 * 2. 普通模式与 Team 模式对比 - 验证两种模式均返回翻译结果
 * 3. Team 模式实体翻译 - 验证实体（entities）处理
 * 4. 错误处理 - 缺少 text 参数应返回错误
 * 5. Team 模式下中英翻译 - 验证实际翻译发生
 */

import { test, expect } from '@playwright/test';

const API_BASE = 'http://127.0.0.1:8080';

// ============================================================
// Team 模式翻译核心接口测试
// ============================================================

test.describe('Team 模式翻译', () => {
  test.describe('POST /v1/translate/selection (mode=team)', () => {

    // --- 测试 1: Team 模式翻译 ---
    test('应成功进行 team 模式翻译', async ({ page }) => {
      test.setTimeout(60000);
      const res = await page.request.post(`${API_BASE}/v1/translate/selection`, {
        data: {
          text: 'Hello, this is a test sentence for team translation.',
          sourceLang: 'en',
          targetLang: 'zh',
          engine: 'google',
          mode: 'team',
        },
        timeout: 60000,
      });

      expect(res.status()).toBe(200);
      const json = await res.json();
      expect(json.success, `Team 模式翻译应成功: ${JSON.stringify(json)}`).toBe(true);
    });

    // --- 测试 2: 普通模式与 Team 模式对比 ---
    test('普通模式和 team 模式均应返回翻译结果', async ({ page }) => {
      const testText = 'The quick brown fox jumps over the lazy dog.';

      // 普通模式（fast）
      const normalRes = await page.request.post(`${API_BASE}/v1/translate/selection`, {
        data: {
          text: testText,
          sourceLang: 'en',
          targetLang: 'zh',
          engine: 'google',
          mode: 'fast',
        },
        timeout: 30000,
      });

      expect(normalRes.status()).toBe(200);
      const normalJson = await normalRes.json();
      expect(normalJson.success, '普通模式应成功').toBe(true);
      expect(normalJson.translation || normalJson.data?.translation, '普通模式应返回翻译文本').toBeDefined();

      // Team 模式
      const teamRes = await page.request.post(`${API_BASE}/v1/translate/selection`, {
        data: {
          text: testText,
          sourceLang: 'en',
          targetLang: 'zh',
          engine: 'google',
          mode: 'team',
        },
        timeout: 60000,
      });

      expect(teamRes.status()).toBe(200);
      const teamJson = await teamRes.json();
      expect(teamJson.success, 'Team 模式应成功').toBe(true);
      expect(teamJson.translation || teamJson.data?.translation, 'Team 模式应返回翻译文本').toBeDefined();
    });

    // --- 测试 3: Team 模式实体翻译 ---
    test('team 模式应正确处理实体翻译', async ({ page }) => {
      const res = await page.request.post(`${API_BASE}/v1/translate/selection`, {
        data: {
          text: 'Alice visited Paris and met Bob at the Eiffel Tower.',
          sourceLang: 'en',
          targetLang: 'zh',
          engine: 'google',
          mode: 'team',
        },
        timeout: 60000,
      });

      expect(res.status()).toBe(200);
      const json = await res.json();
      expect(json.success, '实体翻译应成功').toBe(true);

      // 验证返回中包含翻译文本
      const translation = json.translation || json.data?.translation;
      expect(translation, '实体翻译应返回翻译结果').toBeDefined();
      expect(typeof translation, '翻译结果应为字符串').toBe('string');
      expect(translation.length, '翻译结果不应为空').toBeGreaterThan(0);
    });

    // --- 测试 4: 错误处理 - 缺少 text 参数 ---
    test('缺少 text 参数应返回错误', async ({ page }) => {
      const res = await page.request.post(`${API_BASE}/v1/translate/selection`, {
        data: {
          sourceLang: 'en',
          targetLang: 'zh',
          engine: 'google',
          mode: 'team',
        },
      });

      const json = await res.json();
      expect(
        json.success === false || res.status() === 400,
        '缺少 text 参数应返回错误或 400 状态码'
      ).toBe(true);
    });

    test('空 text 参数应返回错误', async ({ page }) => {
      const res = await page.request.post(`${API_BASE}/v1/translate/selection`, {
        data: {
          text: '',
          sourceLang: 'en',
          targetLang: 'zh',
          engine: 'google',
          mode: 'team',
        },
      });

      const json = await res.json();
      expect(
        json.success === false || res.status() === 400,
        '空 text 参数应返回错误或 400 状态码'
      ).toBe(true);
    });

    // --- 测试 5: Team 模式中文到英文翻译 ---
    test('team 模式下中文到英文翻译应成功', async ({ page }) => {
      const res = await page.request.post(`${API_BASE}/v1/translate/selection`, {
        data: {
          text: '这是一个中文到英文的翻译测试。',
          sourceLang: 'zh',
          targetLang: 'en',
          engine: 'google',
          mode: 'team',
        },
        timeout: 60000,
      });

      expect(res.status()).toBe(200);
      const json = await res.json();
      expect(json.success, '中译英 team 模式应成功').toBe(true);

      const translation = json.translation || json.data?.translation;
      expect(translation, '应返回翻译结果').toBeDefined();
      expect(typeof translation, '翻译结果应为字符串').toBe('string');
      expect(translation.length, '翻译结果不应为空').toBeGreaterThan(0);
      // 验证翻译结果包含英文字母（说明实际发生了翻译）
      expect(/[a-zA-Z]/.test(translation), '翻译结果应包含英文字符').toBe(true);
    });

    // --- 附加测试: Team 模式长文本 ---
    test('team 模式应处理较长文本', async ({ page }) => {
      const longText = 'Once upon a time, in a land far away, there lived a king and a queen. '
        + 'They had a beautiful daughter who was known throughout the kingdom for her wisdom and kindness. '
        + 'Many princes came from distant lands to seek her hand in marriage.';

      const res = await page.request.post(`${API_BASE}/v1/translate/selection`, {
        data: {
          text: longText,
          sourceLang: 'en',
          targetLang: 'zh',
          engine: 'google',
          mode: 'team',
        },
        timeout: 60000,
      });

      expect(res.status()).toBe(200);
      const json = await res.json();
      expect(json.success, '长文本 team 模式应成功').toBe(true);
    });

    // --- 附加测试: Team 模式使用 context 字段别名 ---
    test.fixme('team 模式应支持 context 字段别名', async ({ page }) => {
      test.skip(true, 'Flaky: Playwright browser context closed under parallel workers');
      const res = await page.request.post(`${API_BASE}/v1/translate/selection`, {
        data: {
          context: 'Hello world using context field.',
          sourceLang: 'en',
          targetLang: 'zh',
          engine: 'google',
          mode: 'team',
        },
        timeout: 60000,
      });

      expect(res.status()).toBe(200);
      const json = await res.json();
      expect(json.success, 'context 字段别名应被正确识别').toBe(true);
    });
  });
});
