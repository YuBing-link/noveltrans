/**
 * Translation API Load Test using API Keys
 * 
 * Each VU uses a different API Key (different userId), avoiding:
 * - IP rate limiting (skipped for API Key requests)
 * - QuotaService lock contention (different userId = different lock)
 * 
 * Usage:
 *   k6 run load-test/translate-apikey.js -e API_BASE_URL=http://localhost:7341 -e API_KEYS_FILE=load-test/api-keys.txt
 */
import http from 'k6/http';
import { check } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

export const options = {
  stages: [
    { duration: '30s', target: 50 },
    { duration: '1m',  target: 200 },
    { duration: '2m',  target: 500 },
    { duration: '1m',  target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    http_req_failed: ['rate<0.05'],
  },
};

const API_BASE = __ENV.API_BASE_URL || 'http://localhost:7341';
const API_KEYS_FILE = __ENV.API_KEYS_FILE || './api-keys.txt';

const SAMPLE_TEXTS = [
  'Hello world, this is a test sentence for translation.',
  'The quick brown fox jumps over the lazy dog.',
  'In a hole in the ground there lived a hobbit.',
  'It was the best of times, it was the worst of times.',
  'All happy families are alike; each unhappy family is unhappy in its own way.',
];

// Read API keys from file
function loadApiKeys() {
  try {
    const content = open(API_KEYS_FILE);
    return content.trim().split('\n');
  } catch (e) {
    console.error(`Failed to load API keys from ${API_KEYS_FILE}: ${e.message}`);
    return [];
  }
}

const API_KEYS = loadApiKeys();

export default function () {
  // Each VU uses a different API key based on VU ID
  const vuId = __VU;
  const apiKey = API_KEYS[(vuId - 1) % API_KEYS.length];
  
  const authHeaders = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${apiKey}`,
  };

  const selectionRes = http.post(
    `${API_BASE}/v1/translate/selection`,
    JSON.stringify({
      text: SAMPLE_TEXTS[Math.floor(Math.random() * SAMPLE_TEXTS.length)],
      sourceLang: 'en',
      targetLang: 'zh',
      engine: 'google',
      mode: 'fast',
    }),
    { headers: authHeaders, tags: { name: 'SelectionTranslate' } }
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

  const readerRes = http.post(
    `${API_BASE}/v1/translate/reader`,
    JSON.stringify({
      content: '<p>This is a test paragraph for reader mode translation.</p>',
      sourceLang: 'en',
      targetLang: 'zh',
      engine: 'google',
      mode: 'fast',
    }),
    { headers: authHeaders, tags: { name: 'ReaderTranslate' } }
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
}

export function handleSummary(data) {
  const now = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  return {
    [`load-test/results/translate-apikey-${now}.json`]: JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: '  ', enableColors: true }),
  };
}
