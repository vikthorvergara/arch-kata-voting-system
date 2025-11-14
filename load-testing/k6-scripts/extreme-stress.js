import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const voteErrors = new Counter('vote_errors');
const voteSuccessRate = new Rate('vote_success_rate');
const voteDuration = new Trend('vote_duration');

export const options = {
  stages: [
    { duration: '2m', target: 10000 },   // Ramp up to 10000
    { duration: '3m', target: 20000 },   // Ramp up to 20000
    { duration: '4m', target: 30000 },   // Ramp up to 30000
    { duration: '5m', target: 40000 },   // Ramp up to 40000
    { duration: '6m', target: 50000 },   // Ramp up to 50000
    { duration: '5m', target: 50000 },   // Hold at 50000
    { duration: '3m', target: 0 },       // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(99)<15000'],
    http_req_failed: ['rate<0.30'], // Allow up to 30% errors - we're pushing to the limit
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
    timeout: '60s', // Very high timeout for extreme load
  };

  const startTime = new Date().getTime();
  const response = http.post(`${SERVICE_URL}/api/vote`, payload, params);
  const endTime = new Date().getTime();

  voteDuration.add(endTime - startTime);

  const success = check(response, {
    'status is 200': (r) => r.status === 200,
    'response has success field': (r) => {
      try {
        return JSON.parse(r.body).success === true;
      } catch (e) {
        return false;
      }
    },
  });

  voteSuccessRate.add(success);

  if (!success) {
    voteErrors.add(1);
    // Only log first few errors to avoid overwhelming console
    if (voteErrors.value < 100) {
      console.log(`Error: Status ${response.status}, Body: ${response.body}`);
    }
  }

  // Sleep to prevent completely overwhelming the system
  sleep(0.15);
}
