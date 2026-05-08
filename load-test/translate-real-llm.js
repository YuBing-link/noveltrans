/**
 * Real LLM Translation Load Test
 *
 * Tests the actual LLM translation pipeline (DashScope DeepSeek-v4-flash)
 * via /v1/translate/selection endpoint with JWT authentication.
 *
 * This is NOT a mock test — each request invokes the real LLM API.
 * Rate will be throttled by LLM provider latency (typically 200-800ms).
 *
 * Usage:
 *   k6 run load-test/translate-real-llm.js \
 *     -e API_BASE_URL=http://localhost:7341
 *
 * Scenarios:
 *   ramp-up     : 10s → 5 VUs (warmup)
 *   steady      : 60s → 5 VUs (sustained real LLM load)
 *   ramp-down   : 10s → 0 VUs (cooldown)
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const API_BASE = __ENV.API_BASE_URL || 'http://localhost:7341';

// JWT tokens for 10 MAX-level users (userLevel=MAX, no quota restriction)
const USER_TOKENS = [
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjMsImVtYWlsIjoibG9hZHRlc3QxQHRlc3QuY29tIiwidGVuYW50SWQiOjMsImlhdCI6MTc3ODI0MTAxOCwiZXhwIjoxNzgwODMzMDE4fQ.k647tQZDzGG3-sQoofiVc6_VDPQF-SF4z0SGhv-ri9Y',
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjQsImVtYWlsIjoibG9hZHRlc3QyQHRlc3QuY29tIiwidGVuYW50SWQiOjQsImlhdCI6MTc3ODI0MTAxOCwiZXhwIjoxNzgwODMzMDE4fQ.yQE7NM4xB_8HlQH7rYYRJLPs64kpjErAk4xyelejJZk',
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjUsImVtYWlsIjoibG9hZHRlc3QzQHRlc3QuY29tIiwidGVuYW50SWQiOjUsImlhdCI6MTc3ODI0MTAxOCwiZXhwIjoxNzgwODMzMDE4fQ.yFN9xf1nhoNTGd8Xcy_5nfYDoa3Bg-DlWQBfdOjVIJQ',
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjYsImVtYWlsIjoibG9hZHRlc3Q0QHRlc3QuY29tIiwidGVuYW50SWQiOjYsImlhdCI6MTc3ODI0MTAxOCwiZXhwIjoxNzgwODMzMDE4fQ.AWlYP-mCoycOKS9fcT6HkDicuuzuSwjAK8Bt0d746P8',
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjcsImVtYWlsIjoibG9hZHRlc3Q1QHRlc3QuY29tIiwidGVuYW50SWQiOjcsImlhdCI6MTc3ODI0MTAxOCwiZXhwIjoxNzgwODMzMDE4fQ.7xhr0Mve5K-ys4mxDdB_Myf253R8Sdo3sqBrgovzhHA',
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjgsImVtYWlsIjoibG9hZHRlc3Q2QHRlc3QuY29tIiwidGVuYW50SWQiOjgsImlhdCI6MTc3ODI0MTAxOCwiZXhwIjoxNzgwODMzMDE4fQ.PrRTtO533kfVmuy79b8pM9NXlt-AxrlLWJvPBtXiaT4',
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjksImVtYWlsIjoibG9hZHRlc3Q3QHRlc3QuY29tIiwidGVuYW50SWQiOjksImlhdCI6MTc3ODI0MTAxOCwiZXhwIjoxNzgwODMzMDE4fQ.QRX66DD5N2SqDEe3OP-zAIih9HKcMd1KzrKikyy4ehs',
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEwLCJlbWFpbCI6ImxvYWR0ZXN0OEB0ZXN0LmNvbSIsInRlbmFudElkIjoxMCwiaWF0IjoxNzc4MjQxMDE4LCJleHAiOjE3ODA4MzMwMTh9.nzU0dKbzZzz0j5Sq8hPkIbMwSj60gweS19qKVCxQVTU',
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjExLCJlbWFpbCI6ImxvYWR0ZXN0OUB0ZXN0LmNvbSIsInRlbmFudElkIjoxMSwiaWF0IjoxNzc4MjQxMDE4LCJleHAiOjE3ODA4MzMwMTh9.wXJFMmkHog-6PwZXAWyY6hO5gDaAdYRrd3Awypkc7rs',
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEyLCJlbWFpbCI6ImxvYWR0ZXN0MTBAdGVzdC5jb20iLCJ0ZW5hbnRJZCI6MTIsImlhdCI6MTc3ODI0MTAxOCwiZXhwIjoxNzgwODMzMDE4fQ.mm4pyqkRN0gLsVR2tuwnzzayDDb6YpFNbkYLcAraNDA',
];

// Novel-style texts for realistic translation load
const NOVEL_TEXTS = [
  'The morning sun cast long shadows across the ancient courtyard as the young warrior stepped forward, his sword gleaming in the golden light.',
  'She had never seen such a magnificent sight — thousands of cherry blossoms falling like snow, each petal carrying a whispered promise of spring.',
  '"You cannot run from your destiny," the old man said, his voice carrying the weight of centuries. "The prophecy has already chosen you."',
  'The castle stood atop the highest hill, its towers reaching toward the clouds like fingers of a sleeping giant guarding the valley below.',
  'Rain poured down in sheets, turning the cobblestone streets into rivers of reflected lantern light. She pulled her cloak tighter and hurried on.',
  'In the depths of the forest, where sunlight barely penetrated the canopy, a mysterious melody echoed through the ancient trees.',
  'The merchant smiled knowingly, his eyes reflecting the flickering candlelight. "I have exactly what you are looking for," he whispered.',
  'Years of war had left the land scarred and barren, but the people remained unbroken — their hope as resilient as the wildflowers pushing through the ash.',
];

function getToken() {
  return USER_TOKENS[Math.floor(Math.random() * USER_TOKENS.length)];
}

function authHeaders() {
  return {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + getToken(),
  };
}

export const options = {
  stages: [
    { duration: '10s', target: 5 },   // ramp up to 5 concurrent VUs
    { duration: '60s', target: 5 },   // sustained load — real LLM calls
    { duration: '10s', target: 0 },   // ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<5000'],  // LLM is slow — 5s p95 acceptable
    http_req_failed: ['rate<0.05'],      // error rate < 5%
  },
};

export default function () {
  const res = http.post(
    API_BASE + '/v1/translate/selection',
    JSON.stringify({
      text: NOVEL_TEXTS[Math.floor(Math.random() * NOVEL_TEXTS.length)],
      sourceLang: 'en',
      targetLang: 'zh',
      engine: 'ai',        // EXPERT mode → real LLM (DashScope DeepSeek)
      mode: 'normal',
    }),
    { headers: authHeaders(), tags: { name: 'translate_real_llm' } }
  );

  check(res, {
    'llm translate: status 200': (r) => r.status === 200,
    'llm translate: has translation': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.success === true && body.translation && body.translation.length > 0;
      } catch (e) {
        return false;
      }
    },
    'llm translate: engine is expert': (r) => {
      try {
        return JSON.parse(r.body).engine === 'expert';
      } catch (e) {
        return false;
      }
    },
  });

  // Small sleep to avoid hammering the LLM API
  sleep(0.5);
}

export function handleSummary(data) {
  const now = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  return {
    ['load-test/results/translate-real-llm-' + now + '.json']: JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: '  ', enableColors: true }),
  };
}
