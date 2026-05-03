import http from 'k6/http';
import { check, sleep } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

export const options = {
  stages: [
    { duration: '30s', target: 10 },   // 30秒内升到10并发
    { duration: '1m',  target: 50 },   // 1分钟内升到50并发
    { duration: '2m',  target: 100 },  // 2分钟内升到100并发
    { duration: '1m',  target: 0 },    // 1分钟内降到0
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],  // 95%请求<500ms
    http_req_failed: ['rate<0.01'],    // 错误率<1%
  },
};

const API_BASE = __ENV.API_BASE_URL || 'http://localhost:8080';

const SAMPLE_TEXTS = [
  'Hello world, this is a test sentence for translation.',
  'The quick brown fox jumps over the lazy dog.',
  'She walked down the street and saw a beautiful sunset.',
  'Technology is changing the way we live and work.',
  'Artificial intelligence will transform many industries in the coming decades.',
];

export default function () {
  // Selection Translation
  const selectionRes = http.post(
    `${API_BASE}/v1/translate/selection`,
    JSON.stringify({
      text: SAMPLE_TEXTS[Math.floor(Math.random() * SAMPLE_TEXTS.length)],
      sourceLang: 'en',
      targetLang: 'zh',
      engine: 'google',
      mode: 'fast',
    }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'SelectionTranslate' },
    }
  );

  check(selectionRes, {
    'selection: status 200': (r) => r.status === 200,
    'selection: has success=true': (r) => {
      try {
        return JSON.parse(r.body).translation && JSON.parse(r.body).translation.length > 0;
      } catch {
        return false;
      }
    },
  });

  sleep(0.5);
}

export function handleSummary(data) {
  return {
    'load-test-results.json': JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: '  ', enableColors: true }),
  };
}
