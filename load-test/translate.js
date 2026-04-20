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

export default function () {
  // Selection Translation
  const selectionRes = http.post(
    `${API_BASE}/v1/translate/selection`,
    JSON.stringify({
      context: 'Hello world, this is a test sentence for translation.',
      sourceLang: 'en',
      targetLang: 'zh',
      engine: 'google',
    }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'SelectionTranslate' },
    }
  );

  check(selectionRes, {
    'selection: status is 200': (r) => r.status === 200,
    'selection: response has success=true': (r) => {
      try {
        return JSON.parse(r.body).success === true;
      } catch {
        return false;
      }
    },
    'selection: response has translation': (r) => {
      try {
        return JSON.parse(r.body).translation && JSON.parse(r.body).translation.length > 0;
      } catch {
        return false;
      }
    },
  });

  sleep(1);
}

export function handleSummary(data) {
  return {
    'load-test-results.json': JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}
