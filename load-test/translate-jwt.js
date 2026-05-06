/**
 * Translation API Load Test using JWT Authentication
 *
 * Each VU logs in with a unique user (email/password) to get a JWT token,
 * then uses it for translation requests. Simulates real-world multi-user
 * load without API key overhead.
 *
 * Usage:
 *   k6 run load-test/translate-jwt.js \
 *     -e API_BASE_URL=http://localhost:7341 \
 *     -e USER_COUNT=100
 */
import http from 'k6/http';
import { check } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

export const options = {
  stages: [
    { duration: '30s', target: 10 },
    { duration: '1m',  target: 50 },
    { duration: '2m',  target: 100 },
    { duration: '1m',  target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    http_req_failed: ['rate<0.05'],
  },
};

const API_BASE = __ENV.API_BASE_URL || 'http://localhost:7341';
const USER_COUNT = parseInt(__ENV.USER_COUNT || '100');
const PASSWORD = 'JwtTest@2026';

const SAMPLE_TEXTS = [
  'Hello world, this is a test sentence for translation.',
  'The quick brown fox jumps over the lazy dog.',
  'In a hole in the ground there lived a hobbit.',
  'It was the best of times, it was the worst of times.',
  'All happy families are alike; each unhappy family is unhappy in its own way.',
];

function login(userId) {
  const email = `jwtloadtest${userId}@test.com`;
  const res = http.post(
    `${API_BASE}/api/auth/login`,
    JSON.stringify({ email, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (res.status === 200) {
    try {
      const body = JSON.parse(res.body);
      return body.data?.token;
    } catch (e) {
      return null;
    }
  }
  return null;
}

export default function () {
  const vuId = __VU;
  const userId = ((vuId - 1) % USER_COUNT) + 1;

  const token = login(userId);
  check(token, {
    'login: has token': (t) => t !== null,
  });

  if (!token) return;

  const authHeaders = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`,
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
    [`load-test/results/translate-jwt-${now}.json`]: JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: '  ', enableColors: true }),
  };
}
