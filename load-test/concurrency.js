import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 100,
  duration: '2m',
  thresholds: {
    http_req_duration: ['p(95)<200'],
    http_req_failed: ['rate<0.001'],
  },
};

const API_BASE = __ENV.API_BASE_URL || 'http://localhost:8080';

export default function () {
  // Health check - simple endpoint
  const res = http.get(`${API_BASE}/actuator/health`, {
    tags: { name: 'HealthCheck' },
  });

  check(res, {
    'health: status 200': (r) => r.status === 200,
    'health: response is UP': (r) => {
      try {
        return JSON.parse(r.body).status === 'UP';
      } catch {
        return false;
      }
    },
  });

  sleep(0.1);
}
