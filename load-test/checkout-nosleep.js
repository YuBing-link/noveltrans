import http from 'k6/http';
import { check } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

export const options = {
  stages: [
    { duration: '30s',  target: 5 },
    { duration: '1m',   target: 20 },
    { duration: '2m',   target: 50 },
    { duration: '30s',  target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<3000'],
    http_req_failed: ['rate<0.1'],
  },
};

const API_BASE = __ENV.API_BASE_URL || 'http://localhost:7341';
const TOKEN = __ENV.JWT_TOKEN || '';

const PLANS = [
  { plan: 'PRO', billingCycle: 'monthly' },
  { plan: 'MAX', billingCycle: 'monthly' },
];

const headers = {
  'Content-Type': 'application/json',
  'Authorization': `Bearer ${TOKEN}`,
};

export default function () {
  const plan = PLANS[Math.floor(Math.random() * PLANS.length)];

  const checkoutRes = http.post(
    `${API_BASE}/api/subscription/checkout`,
    JSON.stringify({
      plan: plan.plan,
      billingCycle: plan.billingCycle,
    }),
    {
      headers: headers,
      tags: { name: 'CheckoutSession' },
    }
  );

  const checkoutOk = check(checkoutRes, {
    'checkout: status 200': (r) => r.status === 200,
    'checkout: has checkoutUrl': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.success === true && body.data && body.data.checkoutUrl;
      } catch (e) {
        return false;
      }
    },
  });

  const statusRes = http.get(`${API_BASE}/api/subscription/status`, {
    headers: headers,
    tags: { name: 'SubscriptionStatus' },
  });

  check(statusRes, {
    'status: 200': (r) => r.status === 200,
  });
}

export function handleSummary(data) {
  const now = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  return {
    [`load-test/results/checkout-nosleep-${now}.json`]: JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: '  ', enableColors: true }),
  };
}
