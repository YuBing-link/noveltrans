import { defineConfig, devices } from '@playwright/test';

/**
 * NovelTrans 集成测试配置
 *
 * 包含两个测试项目：
 * 1. frontend - Web 前端页面和 API 集成测试
 * 2. extension - 浏览器插件功能测试
 */

const FRONTEND_URL = process.env.FRONTEND_URL || 'http://localhost:5173';
const BACKEND_URL = process.env.BACKEND_URL || 'http://127.0.0.1:8080';
const EXTENSION_BACKEND_URL = process.env.EXTENSION_BACKEND_URL || 'http://localhost:7341';

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [['html', { outputFolder: 'playwright-report' }], ['list']],

  use: {
    baseURL: FRONTEND_URL,
    trace: 'on-first-retry',
    video: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },

  projects: [
    // === 前端 Web 应用测试 ===
    {
      name: 'frontend',
      testMatch: /frontend\/.*\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        channel: 'chrome',
        baseURL: FRONTEND_URL,
      },
    },

    // === 浏览器插件测试 ===
    {
      name: 'extension',
      testMatch: /extension\/.*\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        channel: 'chrome',
        baseURL: EXTENSION_BACKEND_URL,
      },
    },
  ],
});
