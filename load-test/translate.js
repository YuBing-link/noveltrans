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
    http_req_duration: ['p(95)<1000'], // 95%请求<1秒
    http_req_failed: ['rate<0.01'],    // 错误率<1%
  },
};

const API_BASE = __ENV.API_BASE_URL || 'http://localhost:7341';
const TOKEN = __ENV.JWT_TOKEN || '';

const SAMPLE_TEXTS = [
  'Hello world, this is a test sentence for translation.',
  'The quick brown fox jumps over the lazy dog.',
  'In a hole in the ground there lived a hobbit.',
  'It was the best of times, it was the worst of times.',
  'All happy families are alike; each unhappy family is unhappy in its own way.',
];

const authHeaders = {
  'Content-Type': 'application/json',
  'Authorization': `Bearer ${TOKEN}`,
};

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
      headers: authHeaders,
      tags: { name: 'SelectionTranslate' },
    }
  );

  check(selectionRes, {
    'selection: status 200': (r) => r.status === 200,
    'selection: has translation': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.translation && body.translation.length > 0;
      } catch (e) {
        return false;
      }
    },
  });

  // Reader Translation
  const readerRes = http.post(
    `${API_BASE}/v1/translate/reader`,
    JSON.stringify({
      content: '<p>This is a test paragraph for reader mode translation.</p>',
      sourceLang: 'en',
      targetLang: 'zh',
      engine: 'google',
      mode: 'fast',
    }),
    {
      headers: authHeaders,
      tags: { name: 'ReaderTranslate' },
    }
  );

  check(readerRes, {
    'reader: status 200': (r) => r.status === 200,
    'reader: has content': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.translatedContent;
      } catch (e) {
        return false;
      }
    },
  });

  sleep(1);
}

export function handleSummary(data) {
  const now = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  return {
    [`load-test/results/translate-${now}.json`]: JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: '  ', enableColors: true }),
  };
}
