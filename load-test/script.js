import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

// Baseline: 10 VU, 1 min; Stress: 100 VU, 5 min; Spike: 0->500 VU in 10s
export const options = {
  stages: [
    { duration: '1m', target: 10 },
    { duration: '5m', target: 100 },
    { duration: '10s', target: 500 },
    { duration: '1m', target: 500 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const r = Math.random();
  let res;
  if (r < 0.3) {
    res = http.get(`${BASE}/health`);
    check(res, { 'health 200': (r) => r.status === 200 });
  } else if (r < 0.8) {
    res = http.get(`${BASE}/api/v1/tickers/AAPL/returns`);
    check(res, { 'returns 200': (r) => r.status === 200 });
  } else {
    res = http.get(`${BASE}/api/v1/alpha?target=AAPL&benchmark=SPY`);
    check(res, { 'alpha 200': (r) => r.status === 200 });
  }
  sleep(0.1);
}
