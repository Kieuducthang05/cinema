// Account scenario: every VU acts as its own Customer and reads its Account.
import http from 'k6/http';
import { check, fail } from 'k6';
import { LOAD, MAX_VUS, arrivalRate, handleSummary, summaryOptions } from './lib/summary.js';
import { savedTokens } from './lib/customers.js';

export { handleSummary };

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const tokens = savedTokens();

export const options = {
	...summaryOptions,
	scenarios: { account: arrivalRate(Math.min(tokens.length, MAX_VUS)) },
};

export default function () {
	const token = tokens[__VU - 1];
	if (!token) fail(`VU ${__VU} has no Customer`);
	const res = http.get(`${BASE_URL}/api/me`, { ...LOAD, headers: { Authorization: `Bearer ${token}` } });
	check(res, { 'status is 200': (r) => r.status === 200 });
}
