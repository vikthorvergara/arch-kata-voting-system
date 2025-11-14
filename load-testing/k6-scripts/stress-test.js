import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const voteErrors = new Counter('vote_errors');
const voteSuccessRate = new Rate('vote_success_rate');

export const options = {
  stages: [
    { duration: '1m', target: 2000 },    // Ramp up to 2000
    { duration: '3m', target: 5000 },    // Ramp up to 5000
    { duration: '5m', target: 10000 },   // Ramp up to 10000
    { duration: '3m', target: 10000 },   // Hold at 10000
    { duration: '2m', target: 0 },       // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(99)<10000'],
    http_req_failed: ['rate<0.10'],
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
}
