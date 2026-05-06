/**
 * Round 28: Pure Throughput Test — 验证 ADR-008~012 重构后吞吐量
 *
 * 只测 /v1/translate/selection 一个端点，每 VU 一次请求。
 * 使用 API Key 认证（跳过 IP 限频），fast 模式，mock 引擎（costMs=0）。
 *
 * 目标：达到 ADR-011 所述的 ~4000 req/s 吞吐量。
 *
 * Usage:
 *   k6 run load-test/translate-throughput.js -e API_BASE_URL=http://localhost:7341
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

export const options = {
  stages: [
    { duration: '10s', target: 50 },
    { duration: '10s', target: 200 },
    { duration: '10s', target: 500 },
    { duration: '30s', target: 500 },  // 稳定期
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<100'],
    http_req_failed: ['rate<0.01'],
  },
};

const API_BASE = __ENV.API_BASE_URL || 'http://localhost:7341';

// 每 VU 一个不同的 key，避免单 key 限频
const API_KEYS = [];
for (let i = 0; i < 600; i++) {
  API_KEYS.push(`nt_sk_` + `0000000000000000000000000000test${String(i).padStart(3, '0')}`);
}

// 尝试从文件读取真实 keys
function loadApiKeys() {
  try {
    const content = open('./api-keys.txt');
    return content.trim().split('\n').filter(k => k.startsWith('nt_sk_'));
  } catch (e) {
    return null;
  }
}

const REAL_KEYS = loadApiKeys();

const SAMPLE_TEXTS = [
  'Hello world, this is a test sentence for translation.',
  'The quick brown fox jumps over the lazy dog.',
  'In a hole in the ground there lived a hobbit.',
  'It was the best of times, it was the worst of times.',
  'All happy families are alike; each unhappy family is unhappy in its own way.',
];

export default function () {
  const vuId = __VU;

  // 选择 API Key：优先用文件中的真实 keys
  let apiKey;
  if (REAL_KEYS && REAL_KEYS.length > 0) {
    apiKey = REAL_KEYS[(vuId - 1) % REAL_KEYS.length];
  } else {
    apiKey = API_KEYS[(vuId - 1) % API_KEYS.length];
  }

  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${apiKey}`,
  };

  const res = http.post(
    `${API_BASE}/v1/translate/selection`,
    JSON.stringify({
      text: SAMPLE_TEXTS[Math.floor(Math.random() * SAMPLE_TEXTS.length)],
      sourceLang: 'en',
      targetLang: 'zh',
      engine: 'google',
      mode: 'fast',
    }),
    { headers }
  );

  check(res, {
    'status 200': (r) => r.status === 200,
    'has translation': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.translation && body.translation.length > 0;
      } catch (e) {
        return false;
      }
    },
  });
}

export function handleSummary(data) {
  const now = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  const httpDuration = data.metrics.http_req_duration;
  const reqsPerSec = data.metrics.http_reqs ? data.metrics.http_reqs.values.rate : 0;

  console.log(`\n========== Round 28 压测结果 ==========`);
  console.log(`总请求数: ${data.metrics.http_reqs ? data.metrics.http_reqs.values.count : 0}`);
  console.log(`吞吐量: ${reqsPerSec.toFixed(1)} req/s`);
  console.log(`平均延迟: ${httpDuration ? httpDuration.values.avg.toFixed(1) : 'N/A'}ms`);
  console.log(`p95 延迟: ${httpDuration ? httpDuration.values['p(95)'].toFixed(1) : 'N/A'}ms`);
  console.log(`p99 延迟: ${httpDuration ? httpDuration.values['p(99)'].toFixed(1) : 'N/A'}ms`);
  console.log(`错误率: ${data.metrics.http_req_failed ? (data.metrics.http_req_failed.values.rate * 100).toFixed(2) : 'N/A'}%`);
  console.log(`========================================\n`);

  return {
    [`load-test/results/throughput-r28-${now}.json`]: JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: '  ', enableColors: true }),
  };
}
