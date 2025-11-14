import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const voteErrors = new Counter('vote_errors');
const voteSuccessRate = new Rate('vote_success_rate');

export const options = {
  stages: [
    { duration: '10s', target: 100 },    // Warm up
    { duration: '30s', target: 100 },    // Stable
    { duration: '10s', target: 5000 },   // Spike to 5000 VUs
    { duration: '1m', target: 5000 },    // Hold spike
    { duration: '10s', target: 100 },    // Recovery
    { duration: '20s', target: 100 },    // Stable recovery
    { duration: '10s', target: 0 },      // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000', 'p(99)<5000'],
    http_req_failed: ['rate<0.05'],
  },
};

const SERVICE_URL = __ENV.SERVICE_URL || 'http://localhost:8080';

export default function () {
  const payload = JSON.stringify({
    userId: `user-${__VU}-${__ITER}`,
    candidateId: `candidate-${Math.floor(Math.random() * 10)}`,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const response = http.post(`${SERVICE_URL}/api/vote`, payload, params);

  const success = check(response, {
    'status is 200': (r) => r.status === 200,
  });

  voteSuccessRate.add(success);

  if (!success) {
    voteErrors.add(1);
  }

  sleep(0.05);
}
