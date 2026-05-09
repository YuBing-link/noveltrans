import http from 'k6/http';
import { check, sleep } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const API_BASE = __ENV.API_BASE_URL || 'http://localhost:7341';

const USER_TOKENS = __ENV.USER_TOKENS
  ? JSON.parse(__ENV.USER_TOKENS)
  : [
      'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEsImVtYWlsIjoidGVzdEB0ZXN0LmNvbSIsInRlbmFudElkIjoxLCJpYXQiOjE3NzgxNjU3NDIsImV4cCI6MTc3ODQyNDk0Mn0.1hNH9IO6__z3NjteSTc6JnrI93Kodw2XE47cO78N0sA',
      'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjIsImVtYWlsIjoicGF5dGVzdEBzdHJpcGUuY29tIiwidGVuYW50SWQiOjIsImlhdCI6MTc3ODE2NTc0MiwiZXhwIjoxNzc4NDI0OTQyfQ.MbLbcTmeOlO_fF5u5i545lVbrEFjupJJbsZOAP1CAvQ',
      'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjMsImVtYWlsIjoibG9hZHRlc3QxQHRlc3QuY29tIiwidGVuYW50SWQiOjMsImlhdCI6MTc3ODE2NTc0MiwiZXhwIjoxNzc4NDI0OTQyfQ.xBEHpyNKdbSyWrLdm_p2INyKICFl1v9u0dfSMplBQts',
    ];

const SAMPLE_TEXTS = [
  'Hello world',
  'Good morning',
  'Thank you',
  'How are you',
  'Nice to meet you',
  'See you later',
  'Have a nice day',
];

function getRandomToken() {
  return USER_TOKENS[Math.floor(Math.random() * USER_TOKENS.length)];
}

function authHeaders() {
  return {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${getRandomToken()}`,
  };
}

function translateSelection() {
  const res = http.post(
    `${API_BASE}/v1/translate/selection`,
    JSON.stringify({
      text: SAMPLE_TEXTS[Math.floor(Math.random() * SAMPLE_TEXTS.length)],
      sourceLang: 'en',
      targetLang: 'zh',
      engine: 'mtran',
      mode: 'fast',
    }),
    { headers: authHeaders(), tags: { name: 'translate_selection' } }
  );

  check(res, {
    'translation 200': (r) => r.status === 200,
    'translation has result': (r) => {
      try { return JSON.parse(r.body).success === true; } catch (e) { return false; }
    },
  });
}

function translateReader() {
  const res = http.post(
    `${API_BASE}/v1/translate/reader`,
    JSON.stringify({
      content: SAMPLE_TEXTS[Math.floor(Math.random() * SAMPLE_TEXTS.length)],
      sourceLang: 'en',
      targetLang: 'zh',
      mode: 'normal',
    }),
    { headers: authHeaders(), tags: { name: 'translate_reader' } }
  );

  check(res, {
    'reader 200': (r) => r.status === 200,
    'reader has content': (r) => {
      try { return JSON.parse(r.body).success === true; } catch (e) { return false; }
    },
  });
}

export const options = {
  stages: [
    { duration: '10s', target: 50 },
    { duration: '20s', target: 100 },
    { duration: '20s', target: 200 },
    { duration: '20s', target: 200 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<5000'],
    http_req_failed: ['rate<0.05'],
  },
};

export default function () {
  translateSelection();
  translateReader();
}

export function handleSummary(data) {
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
  return {
    [`load-test/results/normal-${timestamp}.json`]: JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: '  ', enableColors: true }),
  };
}
