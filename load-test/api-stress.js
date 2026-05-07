import http from 'k6/http';
import { check } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

// ===== 压测场景配置 =====
// 通过 SCENARIOS 环境变量控制：translate, payment, mixed (默认)
const SCENARIO = __ENV.SCENARIOS || 'mixed';
const API_BASE = __ENV.API_BASE_URL || 'http://localhost:7341';

// JWT 配置（与 Spring Boot 一致）
const JWT_SECRET = __ENV.JWT_SECRET || 'noveltrans-jwt-secret-key-2026-min32chars';

// 测试用户 Token 池（需与数据库用户匹配）
const USER_TOKENS = __ENV.USER_TOKENS
  ? JSON.parse(__ENV.USER_TOKENS)
  : [
      'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEsImVtYWlsIjoidGVzdEB0ZXN0LmNvbSIsInRlbmFudElkIjoxLCJpYXQiOjE3NzgxNjU3NDIsImV4cCI6MTc3ODQyNDk0Mn0.1hNH9IO6__z3NjteSTc6JnrI93Kodw2XE47cO78N0sA',
      'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjIsImVtYWlsIjoicGF5dGVzdEBzdHJpcGUuY29tIiwidGVuYW50SWQiOjIsImlhdCI6MTc3ODE2NTc0MiwiZXhwIjoxNzc4NDI0OTQyfQ.MbLbcTmeOlO_fF5u5i545lVbrEFjupJJbsZOAP1CAvQ',
      'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjMsImVtYWlsIjoibG9hZHRlc3QxQHRlc3QuY29tIiwidGVuYW50SWQiOjMsImlhdCI6MTc3ODE2NTc0MiwiZXhwIjoxNzc4NDI0OTQyfQ.xBEHpyNKdbSyWrLdm_p2INyKICFl1v9u0dfSMplBQts',
      'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjQsImVtYWlsIjoibG9hZHRlc3QyQHRlc3QuY29tIiwidGVuYW50SWQiOjQsImlhdCI6MTc3ODE2NTc0MiwiZXhwIjoxNzc4NDI0OTQyfQ.7qDkn4c2FSWqpAOwWx39qTG4zFizEjrVokOd9tBvRX0',
      'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjUsImVtYWlsIjoibG9hZHRlc3QzQHRlc3QuY29tIiwidGVuYW50SWQiOjUsImlhdCI6MTc3ODE2NTc0MiwiZXhwIjoxNzc4NDI0OTQyfQ.Zzsc1SriIBh8mu_BdkNBwQpcOOgkKoa_r43p7FmGTmA',
    ];

const SAMPLE_TEXTS = [
  'Hello world, this is a test sentence for translation.',
  'The quick brown fox jumps over the lazy dog.',
  'She walked down the street and saw a beautiful sunset.',
  'Technology is changing the way we live and work.',
  'Artificial intelligence will transform many industries in the coming decades.',
  'Once upon a time in a distant kingdom, there lived a young princess who dreamed of adventure.',
  'The novel translation engine uses advanced machine learning to produce high-quality translations.',
  'Spring Boot provides a convenient way to build production-ready applications.',
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

// ===== 翻译场景 =====
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
    'translation: status 200': (r) => r.status === 200,
    'translation: has result': (r) => {
      try { return JSON.parse(r.body).success === true; } catch (e) { return false; }
    },
  });
}

function translateReader() {
  const res = http.post(
    `${API_BASE}/v1/translate/reader`,
    JSON.stringify({
      text: SAMPLE_TEXTS[Math.floor(Math.random() * SAMPLE_TEXTS.length)],
      sourceLang: 'en',
      targetLang: 'zh',
      mode: 'normal',
    }),
    { headers: authHeaders(), tags: { name: 'translate_reader' } }
  );

  check(res, {
    'reader: status 200': (r) => r.status === 200,
    'reader: has content': (r) => {
      try { return JSON.parse(r.body).success === true; } catch (e) { return false; }
    },
  });
}

// ===== 支付场景 =====
function checkSubscriptionStatus() {
  const res = http.get(
    `${API_BASE}/api/subscription/status`,
    { headers: authHeaders(), tags: { name: 'subscription_status' } }
  );

  check(res, {
    'subscription status: status 200': (r) => r.status === 200,
  });
}

function createCheckoutSession() {
  const res = http.post(
    `${API_BASE}/api/subscription/checkout`,
    JSON.stringify({
      planType: 'PRO',
      billingCycle: 'MONTHLY',
    }),
    { headers: authHeaders(), tags: { name: 'subscription_checkout' } }
  );

  check(res, {
    'checkout: status 200 or redirect': (r) => r.status === 200 || r.status === 302,
  });
}

function cancelSubscription() {
  const res = http.post(
    `${API_BASE}/api/subscription/cancel`,
    JSON.stringify({
      reason: 'load_test_cancel',
    }),
    { headers: authHeaders(), tags: { name: 'subscription_cancel' } }
  );

  check(res, {
    'cancel: status 200': (r) => r.status === 200,
  });
}

// ===== 场景调度 =====
export const options = {
  stages: [
    { duration: '10s', target: 5 },    // 10秒内升到5并发
    { duration: '20s', target: 10 },   // 20秒内升到10并发
    { duration: '30s', target: 0 },    // 30秒内降到0
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000'],  // 95%请求<2s
    http_req_failed: ['rate<0.1'],      // 错误率<10%（支付调用外部API可能失败）
  },
};

export default function () {
  if (SCENARIO === 'translate') {
    translateSelection();
    translateReader();
  } else if (SCENARIO === 'payment') {
    checkSubscriptionStatus();
    createCheckoutSession();
  } else {
    const rand = Math.random();
    if (rand < 0.5) {
      translateSelection();
    } else if (rand < 0.7) {
      translateReader();
    } else if (rand < 0.9) {
      checkSubscriptionStatus();
    } else {
      createCheckoutSession();
    }
  }
}

export function handleSummary(data) {
  const scenario = SCENARIO === 'translate' ? 'translate' : SCENARIO === 'payment' ? 'payment' : 'mixed';
  return {
    [`load-test-results-${scenario}.json`]: JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: '  ', enableColors: true }),
  };
}
