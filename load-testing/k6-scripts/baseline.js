import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 100,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<200', 'p(99)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

const SERVICE_URL = __ENV.SERVICE_URL || 'http://localhost:8080';

export default function () {
  // Test health endpoint
  const healthResponse = http.get(`${SERVICE_URL}/health`);
  check(healthResponse, {
    'health check status is 200': (r) => r.status === 200,
    'health check has status UP': (r) => JSON.parse(r.body).status === 'UP',
  });

  sleep(0.5);

  // Test vote endpoint
  const votePayload = JSON.stringify({
    userId: `user-${__VU}-${__ITER}`,
    candidateId: `candidate-${Math.floor(Math.random() * 10)}`,
  });

  const voteResponse = http.post(
    `${SERVICE_URL}/api/vote`,
    votePayload,
    {
      headers: { 'Content-Type': 'application/json' },
    }
  );

  check(voteResponse, {
    'vote status is 200': (r) => r.status === 200,
    'vote response has success': (r) => JSON.parse(r.body).success === true,
  });

  sleep(0.5);
}
