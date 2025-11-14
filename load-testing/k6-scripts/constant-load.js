import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom metrics
const voteErrors = new Counter('vote_errors');
const voteSuccessRate = new Rate('vote_success_rate');
const voteDuration = new Trend('vote_duration');

export const options = {
  stages: [
    { duration: '30s', target: 1000 },   // Ramp up to 1000 VUs
    { duration: '2m', target: 1000 },    // Stay at 1000 VUs
    { duration: '30s', target: 0 },      // Ramp down to 0
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    http_req_failed: ['rate<0.01'],
    vote_success_rate: ['rate>0.99'],
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
    'response has success field': (r) => JSON.parse(r.body).success === true,
    'response has totalVotes': (r) => JSON.parse(r.body).totalVotes > 0,
  });

  voteSuccessRate.add(success);
  voteDuration.add(response.timings.duration);

  if (!success) {
    voteErrors.add(1);
  }

  sleep(0.1);
}
