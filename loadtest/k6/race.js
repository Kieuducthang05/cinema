// Race scenario: like contention, but every winner abandons its Booking at once,
// so attempts keep racing for free Seats instead of meeting held ones.
import { STEP_SHOWTIMES } from './lib/showtimes.js';
import { attempt, contentionOptions, contentionSetup, contentionSummary } from './lib/contention.js';

export const options = contentionOptions('race');
export const handleSummary = contentionSummary;

export function setup() {
	return contentionSetup(1 * STEP_SHOWTIMES);
}

export default function (data) {
	attempt(data, true);
}
