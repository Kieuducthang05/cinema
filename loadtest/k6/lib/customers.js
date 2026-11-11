import http from 'k6/http';
import { fail } from 'k6';
import { SharedArray } from 'k6/data';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PASSWORD = 'loadtest-password';

// Login to pre-seeded Customers so setup avoids registration's extra BCrypt hash.
// first is the number in loadtest-{first}@loadtest.local, so scenarios can use disjoint Customers.
export function loginCustomers(count, first = 1) {
	const emails = Array.from({ length: count }, (_, i) => `loadtest-${first + i}@loadtest.local`);
	const logins = http.batch(emails.map((email) => ['POST', `${BASE_URL}/api/auth/login`,
		JSON.stringify({ email, password: PASSWORD }), { headers: { 'Content-Type': 'application/json' } }]));
	return logins.map((res, i) => {
		if (res.status !== 200) fail(`login ${emails[i]}: HTTP ${res.status} ${res.body}`);
		const token = res.json('accessToken');
		if (typeof token !== 'string' || token.length === 0) fail(`login ${emails[i]} returned no accessToken`);
		return token;
	});
}

// The tokens login.js saved, shared by all VUs instead of copied into each. Call from the init context.
// The default is loadtest/results/tokens.json, where a local login.js run writes it.
export function savedTokens() {
	return new SharedArray('tokens', () => {
		const tokens = JSON.parse(open(__ENV.TOKENS_FILE || import.meta.resolve('../../results/tokens.json')));
		if (!Array.isArray(tokens) || tokens.length === 0) throw new Error('no tokens saved; run k6/login.js first');
		return tokens;
	});
}
