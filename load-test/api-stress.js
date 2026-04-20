import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 5 },
    { duration: '1m',  target: 20 },
    { duration: '1m',  target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    http_req_failed: ['rate<0.05'],
  },
};

const API_BASE = __ENV.API_BASE_URL || 'http://localhost:8080';

export default function () {
  // Text Translation
  const textRes = http.post(
    `${API_BASE}/v1/translate/text`,
    JSON.stringify({
      text: 'The quick brown fox jumps over the lazy dog.',
      sourceLang: 'en',
      targetLang: 'zh',
      engine: 'google',
    }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'TextTranslate' },
    }
  );

  check(textRes, {
    'text: status 200': (r) => r.status === 200,
    'text: has translated text': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.success === true && body.data && body.data.translatedText;
      } catch {
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
    }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'ReaderTranslate' },
    }
  );

  check(readerRes, {
    'reader: status 200': (r) => r.status === 200,
    'reader: has translated content': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.success === true && body.translatedContent;
      } catch {
        return false;
      }
    },
  });

  sleep(1);
}
