/**
 * Stripe Payment Integration Load Test
 *
 * Tests the real Stripe test environment:
 * - Checkout session creation (creates Stripe CheckoutSession)
 * - Subscription status lookup
 * - Billing portal session creation
 * - Subscription cancellation
 *
 * Uses Stripe TEST keys (sk_test_...), not production.
 * Each checkout session costs $0.00 in test mode.
 *
 * Usage:
 *   k6 run load-test/stripe-payment.js \
 *     -e API_BASE_URL=http://localhost:7341
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const API_BASE = __ENV.API_BASE_URL || 'http://localhost:7341';

// JWT for paytest@stripe.com (userId=2)
const PAYMENT_TOKEN = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjIsImVtYWlsIjoicGF5dGVzdEBzdHJpcGUuY29tIiwidGVuYW50SWQiOjIsImlhdCI6MTc3ODI0MTE1MiwiZXhwIjoxNzgwODMzMTUyfQ.Se2VtVb-mNH73hBuXIYsxrIaWWMs0vG0KKNyBBifjXU';

// Stripe price IDs (from .env)
const PRO_MONTHLY = 'price_1TT0hVBQhUFJY5KkNKOHeJqp';
const PRO_YEARLY = 'price_1TT0i0BQhUFJY5KkTj1JSVeV';
const MAX_MONTHLY = 'price_1TT0jTBQhUFJY5Kkv6nsHok3';
const MAX_YEARLY = 'price_1TT0jsBQhUFJY5KkgZdICjBy';

const CHECKOUT_PLANS = [
  { priceId: PRO_MONTHLY, plan: 'PRO', cycle: 'MONTHLY' },
  { priceId: PRO_YEARLY, plan: 'PRO', cycle: 'YEARLY' },
  { priceId: MAX_MONTHLY, plan: 'MAX', cycle: 'MONTHLY' },
  { priceId: MAX_YEARLY, plan: 'MAX', cycle: 'YEARLY' },
];

function authHeaders() {
  return {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + PAYMENT_TOKEN,
  };
}

export const options = {
  stages: [
    { duration: '10s', target: 2 },   // ramp up
    { duration: '60s', target: 3 },   // sustained — Stripe API calls
    { duration: '10s', target: 0 },   // ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<5000'],  // Stripe API can be slow
    http_req_failed: ['rate<0.1'],
  },
};

export default function () {
  // Scenario 1: Create checkout session (calls Stripe API)
  const plan = CHECKOUT_PLANS[Math.floor(Math.random() * CHECKOUT_PLANS.length)];

  const checkoutRes = http.post(
    API_BASE + '/api/subscription/checkout',
    JSON.stringify({
      planType: plan.plan,
      billingCycle: plan.cycle,
    }),
    { headers: authHeaders(), tags: { name: 'checkout_create' } }
  );

  check(checkoutRes, {
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

  // Scenario 2: Check subscription status (fast, Redis-backed)
  const statusRes = http.get(
    API_BASE + '/api/subscription/status',
    { headers: authHeaders(), tags: { name: 'subscription_status' } }
  );

  check(statusRes, {
    'status: 200': (r) => r.status === 200,
  });

  // Scenario 3: Create billing portal session
  const portalRes = http.post(
    API_BASE + '/api/subscription/portal',
    {},
    { headers: authHeaders(), tags: { name: 'billing_portal' } }
  );

  check(portalRes, {
    'portal: 200': (r) => r.status === 200,
  });

  // Scenario 4: Subscription cancellation
  const cancelRes = http.post(
    API_BASE + '/api/subscription/cancel',
    JSON.stringify({ reason: 'load_test' }),
    { headers: authHeaders(), tags: { name: 'subscription_cancel' } }
  );

  check(cancelRes, {
    'cancel: 200': (r) => r.status === 200,
  });

  sleep(1);
}

export function handleSummary(data) {
  const now = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  return {
    ['load-test/results/stripe-payment-' + now + '.json']: JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: '  ', enableColors: true }),
  };
}
