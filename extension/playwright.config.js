// playwright.config.js - Playwright 测试配置

const { defineConfig, devices } = require('@playwright/test');
const path = require('path');

module.exports = defineConfig({
    testDir: './tests/e2e',
    
    // 超时配置
    timeout: 60 * 1000, // 每个测试 60 秒
    expect: {
        timeout: 10000
    },
    
    // 失败重试
    retries: process.env.CI ? 2 : 0,
    
    // 并行执行
    fullyParallel: false, // 扩展测试需要顺序执行
    
    // 报告配置
    reporter: [
        ['html', { outputFolder: 'playwright-report', open: 'always' }],
        ['list']
    ],
    
    use: {
        // 截图和视频
        screenshot: 'only-on-failure',
        video: 'retain-on-failure',
        
        // 跟踪
        trace: 'on-first-retry',
    },
    
    projects: [
        {
            name: 'chromium',
            use: { 
                ...devices['Desktop Chrome'],
            },
        },
    ],
    
    // Web 服务器配置（如果需要启动本地后端）
    // webServer: {
    //     command: 'npm run start:backend',
    //     url: 'http://127.0.0.1:7341',
    //     reuseExistingServer: !process.env.CI,
    // },
});
