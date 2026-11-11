// Not a scenario: logs in COUNT seeded Customers from FIRST once and saves their tokens,
// so every step of a scenario reuses them instead of paying 2,000 BCrypt checks again.
// A token stays valid for one hour.
import { loginCustomers } from './lib/customers.js';
import { MAX_VUS } from './lib/summary.js';

const COUNT = Number(__ENV.COUNT || MAX_VUS);
const FIRST = Number(__ENV.FIRST || 1);
const TOKENS_FILE = __ENV.TOKENS_FILE || 'loadtest/results/tokens.json';

export const options = { setupTimeout: '600s', vus: 1, iterations: 1 };

export function setup() {
	return { tokens: loginCustomers(COUNT, FIRST) };
}

export default function () {}

export function handleSummary(data) {
	return {
		stdout: `saved ${data.setup_data.tokens.length} tokens for Customers ${FIRST}..${FIRST + COUNT - 1}\n`,
		[TOKENS_FILE]: JSON.stringify(data.setup_data.tokens),
	};
}
