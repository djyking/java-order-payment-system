import http from 'k6/http';
import { check } from 'k6';

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    create_order: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 10),
      timeUnit: '1s',
      duration: __ENV.DURATION || '30s',
      preAllocatedVUs: Number(__ENV.PRE_ALLOCATED_VUS || 50),
      maxVUs: Number(__ENV.MAX_VUS || 300),
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

export default function () {
  if (!__ENV.ACCESS_TOKEN) throw new Error('Set ACCESS_TOKEN to a registered test user JWT');
  const skuId = String(__ENV.SKU_ID || '19999');
  const body = JSON.stringify({ items: [{ skuId, quantity: 1 }] });
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${__ENV.ACCESS_TOKEN}`,
      'X-Idempotency-Token': `${__VU}-${__ITER}-${Date.now()}`,
    },
    timeout: '15s',
  };
  const response = http.post(`${__ENV.BASE_URL || 'http://localhost:8080'}/api/orders`, body, params);
  check(response, { 'order created': (result) => result.status === 200 });
}
