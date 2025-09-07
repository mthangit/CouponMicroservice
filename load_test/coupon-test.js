import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = 'http://localhost:8080/api/v1/orders/process';
const AUTH_TOKEN = 'Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJqb2huIiwidXNlcklkIjoxLCJyb2xlIjoiVVNFUiIsImlhdCI6MTc1NjI3NTk2NywiZXhwIjoxNzU2MzYyMzY3fQ.aPQmEOkCGgJW0gItPTcFML6Iu2q9UEFGWnhpvELTXyrFhDtN-8NRR8XNOrFk1d7qK6Sm7a_gyh85rOnmCGLxyg';

export const options = {
  scenarios: {
    one_time_requests: {
      executor: 'shared-iterations',
      vus: 6,           // 6 virtual users
      iterations: 10,
      maxDuration: '30s' // timeout nếu cần
    },
  },
};

export default function () {
  const userId = 34;
  const requestId = "Test_te1";
  const orderAmount = 3000000;
  const orderDate = "2025-08-27T18:00:00";
  const couponCode = "WELCOME10";

  const payload = JSON.stringify({
    userId,
    orderAmount,
    couponCode,
    requestId,
    orderDate
  });

  const headers = {
    'Content-Type': 'application/json',
    'Authorization': AUTH_TOKEN,
  };

  const res = http.post(BASE_URL, payload, { headers });

  const isSuccess = check(res, {
    'status is 200': (r) => r.status === 200,
  });

  // Hiển thị kết quả chi tiết
  console.log(`VU: ${__VU} | Iter: ${__ITER} | Status: ${res.status} | Duration: ${res.timings.duration}ms`);

  if (res.status !== 200) {
    console.log(`VU: ${__VU} | Error: ${res.body}`);
  }

  if (res.body) {
    try {
      const responseBody = JSON.parse(res.body);
      console.log(`VU: ${__VU} | Response: ${JSON.stringify(responseBody)}`);
    } catch (e) {
      console.log(`VU: ${__VU} | Response: ${res.body}`);
    }
  }

  return res; // Trả về response để K6 có thể thu thập metrics
}
