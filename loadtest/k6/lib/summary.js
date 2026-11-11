// Shared end-of-test summary for every scenario.
// A scenario re-exports handleSummary, spreads summaryOptions into its options
// and passes LOAD as the params of every request it wants measured.
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

// k6 does not create this directory; whoever runs k6 must.
const RESULTS_DIR = __ENV.RESULTS_DIR || 'loadtest/results';

// One step of the capacity run: k6 starts RATE iterations per second for DURATION.
export const RATE = Number(__ENV.RATE || 50);
export const DURATION = __ENV.DURATION || '60s';
// Every VU the target rate may need, up to the Customers whose tokens login.js saved.
export const MAX_VUS = 2000;

// Tags the measured requests, so setup's requests stay out of the numbers.
export const LOAD = { tags: { phase: 'load' } };

// Constant arrival rate: throughput is set by the target, not by how fast responses come back.
// Preallocating about a second of iterations keeps k6 from dropping arrivals while it creates VUs.
export function arrivalRate(maxVUs = MAX_VUS) {
	return {
		executor: 'constant-arrival-rate',
		rate: RATE,
		timeUnit: '1s',
		duration: DURATION,
		preAllocatedVUs: Math.min(maxVUs, Math.max(50, RATE)),
		maxVUs,
	};
}

export const summaryOptions = {
	// k6's default stats stop at p(95); the report needs p(99).
	summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
	// Never fail k6 on load: report.py decides whether a step held (ADR 0007).
	// k6 needs thresholds to include these tagged sub-metrics in the summary.
	thresholds: {
		'http_reqs{phase:load}': ['count>=0'],
		'http_req_failed{phase:load}': ['rate>=0'],
		'http_req_duration{phase:load}': ['max>=0'],
	},
};

export function handleSummary(data) {
	// setup's return value (tokens, seat lists) has no place in a shared results file.
	delete data.setup_data;
	data.load = { rate: RATE, duration: DURATION };
	return {
		stdout: textSummary(data, { indent: ' ', enableColors: false }),
		[`${RESULTS_DIR}/summary.json`]: JSON.stringify(data, null, 2),
	};
}
